if isClient() then return end
local C=require "LofersScenario/Config"
local M=require "LofersScenario/Model"
local W=require "LofersScenario/World"
local N=require "LofersScenario/Native"
local Road=require "LofersScenario/RoadState"
local Traffic=require "LofersScenario/TrafficIncidents"
local hasIndex,mapIndex=pcall(require,"LofersScenario/MapIndex")
local R={players={},clients={},lastTick=0,lastDiscovery=0,lastPublish=0,lastReplica=0,lastProbe=0,lastRegional=0,
    lastTelemetry=0,timings={},leaseChanges=0,planRejections=0,dirty=false,
    cursor=1,spawnCursor=1,snapshotCursor=1,indexCursor=1,assignmentCursor=1,pending=nil,nextProbe=1,
    contacts={},contactOrder={},contactLimits={},lastPlans=-1,lastError="initializing",
    workerReady=false,workerReason="awaiting_planner_reply",workerRevision=-1,workerMinimum=1}
LofersScenarioServer=R
local function seconds() return getTimestampMs()/1000 end
local function worldHour() return getGameTime():getWorldAgeHours() end
local function enabled() return C.enabled() and R.state~=nil end
local function reply(player,command,args) sendServerCommand(player,"LofersScenario",command,args) end
local function broadcast(command,args) sendServerCommand("LofersScenario",command,args) end
local function admin(p) return p and string.lower(p:getAccessLevel() or "")=="admin" end
local function status()
    local s=R.state;if not s then return {status="disabled",last_error=R.lastError} end
    local p,n,v=M.count(s);local rows={}
    for _,id in ipairs(s.order) do local r=s.residents[id]
        if r.materialized and #rows<32 then rows[#rows+1]={id=id,name=r.name,role=r.role,
            action=r.current_action,owner=r.owner_id} end
    end
    return {world=s.world,world_id=s.world_id,epoch=R.epoch,status=s.status,phase=s.phase,
        elapsed_hours=s.elapsed_hours,revision=s.control_revision or 0,world_revision=s.revision,online=#R.players,residents=#s.order,
        materialized=n,pedestrians=p,vehicles=v,bridge_health=R.workerReady and "ready" or R.workerReason,
        bridge_reported_health=(R.root.bridgeIn or {}).health or "waiting",
        last_error=not R.workerReady and R.workerReason or R.lastError,resident_rows=rows,planning_paused=not R.workerReady,
        planner_reason=R.workerReason,planner_reply_age=R.workerAt and math.max(0,seconds()-R.workerAt) or nil}
end
R.status=status
local function initialize()
    if not C.enabled() then return end
    R.root=ModData.getOrCreate("LofersScenario")
    if not R.root.state then R.root.state=M.new(getServerName(),getRandomUUID(),worldHour()) end
    local migrated,why=M.migrate(R.root.state)
    if not migrated then R.lastError=why;return end
    R.state=R.root.state;R.epoch=R.state.world_id..":"..getRandomUUID();R.state.last_hour=worldHour()
    local trafficReady,trafficWhy=Traffic.bind(R.state,true)
    if not trafficReady then R.state=nil;R.lastError=trafficWhy;return end
    Road.bind(R.state,hasIndex and mapIndex.navigation_id or "")
    R.workerMinimum=R.state.revision+1;R.workerAt=nil;R.workerReady=false
    -- Restore invalidates pending transport replies and every previous executor lease.
    R.root.bridgeIn={health="waiting",guard_ready=false};R.root.bridgeOut=nil
    for _,id in ipairs(R.state.order) do local r=R.state.residents[id]
        r.owner_id=-1;r.lease_epoch=r.lease_epoch+1;r.lease_until=0;r.action_started=nil
        if r.lifecycle=="spawning" then r.lifecycle="unresolved" end
    end
    R.lastError="waiting_for_native_bridge"
    print("[LofersScenario] "..C.version.." restored "..R.state.world.." in "..R.state.status)
end
local function players()
    R.players={};local all=getOnlinePlayers()
    for i=0,all:size()-1 do local p=all:get(i)
        if p and not p:isDead() then R.players[#R.players+1]=p end
    end
end
local function plannerReady()
    local b=R.root.bridgeIn or {};local now=seconds();local ready=false;local reason
    local revision=b.observation_revision
    if b.server_epoch~=R.epoch or b.guard_ready~=true or not N.ready() then
        reason="native_bridge_not_ready"
    elseif b.health~="ready" then reason="planner_unavailable"
    elseif type(revision)=="number" and revision>=R.workerMinimum and revision>R.workerRevision and
        revision<=R.state.revision then
        -- Only an advancing completed observation proves worker progress. A
        -- repeated ready status/handshake cannot refresh an old reply forever.
        R.workerRevision=revision;R.workerAt=now;ready=true
    elseif R.workerAt and now-R.workerAt<=C.workerTimeoutSeconds and R.workerRevision>=R.workerMinimum then
        ready=true
    else reason=R.workerAt and "planner_reply_stale" or "awaiting_planner_reply" end
    if not ready then R.workerMinimum=math.max(R.workerMinimum,R.workerRevision+1) end
    if ready~=R.workerReady then
        R.workerReady=ready;R.dirty=true;R.pending=nil
        R.state.last_hour=worldHour()
        if not ready then
            -- Recovery must acknowledge an observation published after this
            -- hold, rather than a delayed in-flight reply from before it.
            R.workerMinimum=math.max(R.workerMinimum,R.state.revision+1)
            -- Uncommitted actions are abandoned, so late receipts and elapsed
            -- client timers cannot produce a burst of effects after recovery.
            for _,id in ipairs(R.state.order) do local r=R.state.residents[id]
                r.actions={};r.action_index=1;r.current_action="WAIT";r.action_started=nil
                r.action_deadline=nil;r.abstract_progress=0;r.revision=r.revision+1
            end
        end
        M.event(R.state,"planner",ready and "recovered_fresh_reply" or reason)
    end
    R.workerReason=ready and "ready" or reason
    return ready,R.workerReason
end
local function guarded()
    local ok,why=plannerReady();if not ok then return false,why end
    if #R.players>C.maxPlayers then return false,"observer_capacity" end
    for _,p in ipairs(R.players) do
        local c=R.clients[p:getOnlineID()]
        if not c or not c.helper or seconds()-c.at>30 then return false,"client_lua_gate_missing" end
    end
    return true
end
local function replica(r)
    local a=M.action(r)
    return {id=r.id,name=r.name,role=r.role,generation=r.generation,outfit_id=r.outfit_id or -1,
        position=M.point(r.position),lifecycle=r.lifecycle,infection=r.infection,health=r.health,health_revision=r.health_revision or 0,
        owner_id=r.owner_id,lease_epoch=r.lease_epoch,lease_until=r.lease_until,plan_revision=r.plan_revision,
        action=a,action_started=r.action_started or 0,has_vehicle=r.has_vehicle,vehicle_id=r.vehicle_id,in_vehicle=r.in_vehicle==true,
        current_action=r.current_action,epoch=R.epoch}
end
local function sendResidents(player)
    local out={};for _,id in ipairs(R.state.order) do local r=R.state.residents[id]
        if r.materialized and #out<C.maxPhysical then out[#out+1]=replica(r) end
    end
    local msg={epoch=R.epoch,world=R.state.world,revision=R.state.revision,server_seconds=seconds(),
        world_hour=worldHour(),phase=R.state.phase,paused=R.state.status=="paused",
        planning_paused=not R.workerReady,planner_reason=R.workerReason,residents=out}
    if player then reply(player,"residents",msg) else broadcast("residents",msg) end
end
local function observation(r)
    local out={}
    for _,key in ipairs({"id","revision","generation","name","role","hunger","fatigue","fear","infection",
        "health","materialized","has_vehicle","vehicle_id","threatened","home_safe","work_available",
        "has_food","current_action","plan_revision","lease_epoch","exposed_hour","in_vehicle"}) do out[key]=r[key] end
    for _,key in ipairs({"position","home","work","shop","clinic"}) do out[key]=M.point(r[key] or r.home) end
    return out
end
local function publish()
    local s=R.state;local rs={};local seen={}
    for _,id in ipairs(s.order) do local r=s.residents[id]
        if r.materialized and not M.action(r) and #rs<C.maxPlans then rs[#rs+1]=observation(r);seen[id]=true end
    end
    for _=1,math.min(#s.order,C.maxPlans) do
        R.snapshotCursor=(R.snapshotCursor-1)%math.max(1,#s.order)+1
        local id=s.order[R.snapshotCursor];R.snapshotCursor=R.snapshotCursor+1
        local r=s.residents[id]
        if r and r.lifecycle=="abstract" and not seen[id] and not M.action(r) and #rs<C.maxPlans then rs[#rs+1]=observation(r) end
    end
    local ps={};for _,id in ipairs(s.place_order) do ps[#ps+1]=s.places[id] end
    for _,id in ipairs(s.order) do local r=s.residents[id]
        if r.materialized and (r.infection=="symptomatic" or r.infection=="severe") and #ps<C.maxPlaces+16 then
            ps[#ps+1]={id="incident:"..id,kind="incident",position=M.point(r.position),available=true,revision=r.revision}
        end
    end
    local ns={};if not hasIndex then for _,id in ipairs(s.road_order) do ns[#ns+1]=s.roads[id] end end
    s.revision=s.revision+1
    R.root.bridgeOut={world=s.world,server_epoch=R.epoch,revision=s.revision,
        world_hour=math.floor(worldHour()/24)*24+getGameTime():getTimeOfDay(),
        scenario_hour=s.elapsed_hours,phase=s.phase,paused=s.status=="paused" or not R.workerReady,seed=s.schedule_seed,
        residents=rs,places=ps,road_nodes=ns,road_edges=hasIndex and {} or s.edges,online_players=#R.players,
        navigation_id=s.navigation_id or "",
        road_closures=Road.snapshot(s,math.floor(worldHour()/24)*24+getGameTime():getTimeOfDay())}
end
local function plans()
    if not R.workerReady then return end
    local b=R.root.bridgeIn or {};local batch=b
    if batch.observation_revision==R.workerRevision and batch.observation_revision~=R.lastPlans then
        R.lastPlans=batch.observation_revision
        for i,p in ipairs(batch.plans or {}) do
            if i>C.maxPlans then break end
            local ok,why=M.acceptPlan(R.state,p)
            if not ok then M.event(R.state,"plan_rejected",why,p.resident_id);R.planRejections=R.planRejections+1
            else R.dirty=true end
        end
    end
end
local function beginProbe(role,force)
    if R.pending or #R.players==0 then return false,"probe_busy_or_no_players" end
    local ok,why=guarded();if not ok then return false,why end
    local s=R.state;local ped,total,cars=M.count(s)
    if total>=C.maxPhysical then return false,"physical_capacity" end
    local resident,point,vehicle;local attempts=0
    for _=1,math.min(#s.order,32) do
        R.spawnCursor=(R.spawnCursor-1)%#s.order+1
        local r=s.residents[s.order[R.spawnCursor]];R.spawnCursor=R.spawnCursor+1
        local nearby=false
        for _,p in ipairs(R.players) do if M.distance(r.position,{x=p:getX(),y=p:getY()})<140 then nearby=true end end
        if r.lifecycle=="abstract" and not r.materialized and r.infection~="turning" and nearby and
            seconds()>(r.spawn_retry_at or 0) and not r.has_vehicle and
            (not role or role==r.role or (role=="medic" and r.role=="nurse")) then
            attempts=attempts+1;vehicle=false
            point=W.candidate(s,r,R.players,false)
            if point then resident=r;break end
            r.spawn_retry_at=seconds()+8
            if attempts>=4 then break end
        end
    end
    if not resident then return false,"no_discovered_household" end
    if not vehicle and ped>=C.maxPedestrians then return false,"pedestrian_capacity" end
    local probe={id=tostring(R.nextProbe),resident=resident.id,point=point,vehicle=vehicle,
        kind="spawn",expires=seconds()+C.visibilitySeconds,answers={},required={}}
    R.nextProbe=R.nextProbe+1
    for _,p in ipairs(R.players) do
        probe.required[p:getOnlineID()]=true
        reply(p,"visibility",{epoch=R.epoch,id=probe.id,point=point})
    end
    R.pending=probe;return true,"visibility_requested"
end
local function beginRetirement()
    if R.pending or #R.players==0 then return false end
    local ok=guarded();if not ok then return false end
    for _,id in ipairs(R.state.order) do local r=R.state.residents[id]
        if r.materialized and r.lifecycle=="active" and seconds()-(r.spawned_at or 0)>45 then
            local far=true
            for _,player in ipairs(R.players) do
                if M.distance(r.position,{x=player:getX(),y=player:getY()})<95 then far=false;break end
            end
            local z=N.actors[id];local car=z and z:getVehicle()
            if far and z and (not car or math.abs(car:getCurrentSpeedKmHour())<1) then
                local probe={id=tostring(R.nextProbe),resident=id,point=M.point(r.position),kind="retire",
                    generation=r.generation,expires=seconds()+C.visibilitySeconds,answers={},required={}}
                R.nextProbe=R.nextProbe+1
                for _,player in ipairs(R.players) do
                    probe.required[player:getOnlineID()]=true
                    reply(player,"visibility",{epoch=R.epoch,id=probe.id,point=probe.point})
                end
                R.pending=probe;return true
            end
        end
    end
    return false
end
local function finishProbe()
    local p=R.pending;if not p then return end
    if not plannerReady() then R.pending=nil;return end
    if seconds()>p.expires then R.pending=nil;R.lastError="visibility_timeout";return end
    for _,player in ipairs(R.players) do if not p.required[player:getOnlineID()] then R.pending=nil;return end end
    for id in pairs(p.required) do
        if p.answers[id]==false then R.pending=nil;R.lastError="visible_position_declined";return end
        if p.answers[id]~=true then return end
    end
    R.pending=nil
    if p.kind=="retire" then
        local r=R.state.residents[p.resident]
        if not r or r.generation~=p.generation or not r.materialized or
            M.distance(r.position,p.point)>0.75 then return end
        local old=replica(r)
        local ok,why=N.remove(R.state,r)
        if ok then old.lifecycle="abstract";broadcast("terminal",old);R.dirty=true
        else R.lastError=why end
        return
    end
    if not W.valid(p.point,R.players,p.vehicle) then return end
    local r=R.state.residents[p.resident]
    if not r or r.lifecycle~="abstract" then return end
    local ok,why=N.spawn(R.state,r,p.point,R.players[1],p.vehicle)
    if ok then r.spawned_at=seconds() end
    R.lastError=ok and "" or why;sendResidents()
end
local function advanceResident(r,dt,progress)
    if seconds()>(r.threat_until or 0) then r.threatened=false end
    local s=R.state
    if progress then
        if r.pending_contact_exposure then
            M.expose(s,r,"deferred_confirmed_zombie_contact");r.pending_contact_exposure=nil
        end
        M.advanceResident(s,r,dt)
    end
    if r.materialized then
        local z,state=N.reconcile(r)
        if state=="dead" then
            r.lifecycle="dead";r.materialized=false;r.owner_id=-1;r.lease_epoch=r.lease_epoch+1
            N.dead(r);r.actions={};r.current_action="WAIT"
            M.event(s,"death","native_dead",r.id);broadcast("terminal",replica(r));return
        elseif not z then
            -- Native unload is not despawn/death. Keep reservation until reconciliation.
            r.lifecycle="unresolved";return
        end
        r.lifecycle="active"
        if progress and r.infection=="turning" then
            if N.turn(s,r) then broadcast("terminal",replica(r)) end
            return
        end
        local owner=LofersNative.ownerId(z)
        local found=false;for _,p in ipairs(R.players) do if p:getOnlineID()==owner then found=true end end
        if not found and #R.players>0 then owner=R.players[1]:getOnlineID() end
        local oldOwner=r.owner_id
        M.lease(r,owner,seconds());LofersNative.lease(z,r.lease_epoch,owner)
        if owner~=oldOwner then R.dirty=true;R.leaseChanges=R.leaseChanges+1 end
        local a=M.action(r)
        if progress and a and not r.action_started then r.action_started=worldHour();r.action_deadline=seconds()+120;R.dirty=true end
    elseif progress and r.lifecycle=="abstract" then
        -- Coarse unseen routines conserve identity; physical members never use this path.
        local a=M.action(r)
        if a then
            r.abstract_progress=(r.abstract_progress or 0)+dt
            local travel=M.distance(r.position,a.target)/(a.kind=="DRIVE" and 5000 or 1200)
            if r.abstract_progress>=math.max(travel,a.duration_hours or 0.05) then
                r.position=M.point(a.target);r.abstract_progress=0
                M.receipt(s,r,{action_id=a.id,generation=r.generation,plan_revision=r.plan_revision,
                    lease_epoch=r.lease_epoch,state="completed"},r.owner_id)
            end
        end
    end
end
local function regional()
    local s=R.state;if not s.started_hour then return end
    local interval=math.max(1,8-s.elapsed_hours/24)
    if s.elapsed_hours-(s.last_regional_hour or 0)<interval then return end
    s.last_regional_hour=s.elapsed_hours
    -- Regional seeds complement observed contacts. Stored causes are available to admin inspection.
    for _=1,math.min(12,#s.order) do
        local r=s.residents[s.order[math.floor(M.random(s)*#s.order)+1]]
        if r.lifecycle~="dead" and r.lifecycle~="zombie" and M.expose(s,r,"regional_incident") then break end
    end
end
local function update()
    if not enabled() then return end
    local now=seconds();if now-R.lastTick<0.1 then return end;R.lastTick=now
    local nativeEpoch=(R.root.bridgeIn or {}).server_epoch
    if type(nativeEpoch)=="string" and nativeEpoch~="" and nativeEpoch~=R.epoch then
        R.epoch=nativeEpoch;R.lastPlans=-1;R.pending=nil
        R.contacts={};R.contactOrder={};R.contactLimits={}
        R.workerMinimum=R.state.revision+1;R.workerAt=nil;R.workerRevision=-1
        for _,id in ipairs(R.state.order) do local r=R.state.residents[id]
            r.owner_id=-1;r.lease_epoch=r.lease_epoch+1;r.action_started=nil
        end
    end
    players();local progress=plannerReady()
    local dt=M.clock(R.state,worldHour(),progress and #R.players or 0)
    if now-R.lastDiscovery>1 then
        local places=hasIndex and mapIndex.places or {}
        for _=1,8 do local p=places[R.indexCursor];if not p then break end
            local place=M.addPlace(R.state,p.id,p.kind,p.position)
            if place and place.kind=="home" and not place.household then
                place.household={}
                for _=1,2 do local r=M.resident(R.state,place);if r then place.household[#place.household+1]=r.id end end
            end
            R.indexCursor=R.indexCursor+1
        end
        W.discover(R.state,R.players,C.scanOperations,hasIndex)
        for _=1,math.min(4,#R.state.order) do
            R.assignmentCursor=(R.assignmentCursor-1)%#R.state.order+1
            M.assignPlaces(R.state,R.state.residents[R.state.order[R.assignmentCursor]])
            R.assignmentCursor=R.assignmentCursor+1
        end
        R.lastDiscovery=now
    end
    plans()
    if R.state.status~="paused" and #R.players>0 then
        -- At most eight residents per 100ms slice, with fair round-robin maintenance.
        local n=#R.state.order
        for _=1,math.min(8,n) do R.cursor=(R.cursor-1)%n+1
            advanceResident(R.state.residents[R.state.order[R.cursor]],dt*math.max(1,n/8),progress);R.cursor=R.cursor+1
        end
        if progress then regional();finishProbe() end
        if progress and now-R.lastProbe>2 then
            if not beginRetirement() then beginProbe() end
            R.lastProbe=now
        end
    end
    if R.dirty or now-R.lastReplica>2 then sendResidents();R.lastReplica=now;R.dirty=false end
    if now-R.lastPublish>1 then publish();R.lastPublish=now end
    if now-R.lastTelemetry>5 then
        local t=status();local sorted={};for _,v in ipairs(R.timings) do sorted[#sorted+1]=v end;table.sort(sorted)
        t.worker_compute_ms=(R.root.bridgeIn or {}).compute_ms;t.plan_rejections=R.planRejections;t.lease_changes=R.leaseChanges
        t.last_step_ms=R.lastStep
        if #sorted>0 then t.p95_step_ms=sorted[math.ceil(#sorted*0.95)];t.p99_step_ms=sorted[math.ceil(#sorted*0.99)] end
        R.root.telemetry=t;R.lastTelemetry=now
    end
end
local function receipt(player,args)
    if R.state.status=="paused" or not plannerReady() then return end
    local r=R.state.residents[args.resident_id]
    if not r or not r.materialized or args.epoch~=R.epoch or seconds()>r.lease_until then return end
    local z=N.actors[r.id];if not z or LofersNative.ownerId(z)~=player:getOnlineID() then return end
    local a=M.action(r);if not a then return end
    if args.state=="completed" then
        local distance=M.distance(r.position,a.target)
        if a.kind~="WAIT" and a.kind~="EAT" and a.kind~="REST" and distance>3.5 then return end
        if r.action_started and worldHour()-r.action_started+0.001<(a.duration_hours or 0) then return end
    end
    local ok,why=M.receipt(R.state,r,args,player:getOnlineID())
    reply(player,"receipt_ack",{resident_id=r.id,action_id=args.action_id,ok=ok,reason=why or "",epoch=R.epoch})
    if ok then
        if args.state=="completed" and a.kind=="TREAT" then
            local patient=R.state.residents[a.target_id]
            if patient and patient.materialized and patient.health>0 and
                M.distance(r.position,patient.position)<3.5 and N.health(patient,patient.health+10) then
                patient.revision=patient.revision+1;M.event(R.state,"treatment","confirmed_patient",patient.id)
            end
        end
        sendResidents()
    end
end
local function contact(player,args)
    if args.epoch~=R.epoch or R.state.status=="paused" then return end
    local pid=player:getOnlineID();local now=seconds()
    local limit=R.contactLimits[pid]
    if not limit or now-limit.at>=1 then limit={at=now,count=0};R.contactLimits[pid]=limit end
    if limit.count>=12 then return end;limit.count=limit.count+1
    local r=R.state.residents[args.resident_id]
    if not r or not r.materialized or args.generation~=r.generation or args.lease_epoch~=r.lease_epoch or
        type(args.attacker_id)~="number" or type(args.attacker_outfit)~="number" or
        type(args.sequence)~="number" or args.sequence<1 or args.sequence>2147483647 then return end
    local victim=N.actors[r.id];if not victim or victim:isDead() then return end
    local point={x=victim:getX(),y=victim:getY(),z=math.floor(victim:getZ())}
    local attacker=W.find(args.attacker_outfit,point)
    if not attacker or attacker==victim or attacker:isDead() or attacker:getOnlineID()~=args.attacker_id or
        attacker:getVariableBoolean("Bandit") then return end
    local owner=attacker:getOwnerPlayer()
    if not owner or owner:getOnlineID()~=pid then return end
    if M.distance(point,{x=attacker:getX(),y=attacker:getY()})>=0.9 or
        math.abs(attacker:getZ()-point.z)>0.3 or not attacker:isFacingObject(victim,0.3) then return end
    local a,b=attacker:getSquare(),victim:getSquare()
    if not a or not b or a:isSomethingTo(b) then return end
    local key=r.id..":"..r.generation..":"..args.attacker_id..":"..args.attacker_outfit
    local prior=R.contacts[key]
    if prior and (now-prior.at<2 or (prior.owner==pid and args.sequence<=prior.sequence)) then return end
    if not prior then
        if #R.contactOrder>=128 then R.contacts[table.remove(R.contactOrder,1)]=nil end
        R.contactOrder[#R.contactOrder+1]=key
    end
    R.contacts[key]={at=now,owner=pid,sequence=args.sequence}
    local health=victim:getHealth()*100/(r.max_native_health or 1)
    if N.health(r,health-6) then
        r.revision=r.revision+1;r.threatened=true;r.threat_until=now+8;r.fear=math.max(r.fear,0.8)
        r.actions={};r.action_index=1;r.current_action="WAIT"
        -- Immediate physical injury/defense remains authoritative during an
        -- outage; the paused outbreak cannot introduce new infection decisions.
        if plannerReady() then M.expose(R.state,r,"confirmed_zombie_contact")
        elseif R.state.started_hour and r.infection=="healthy" then r.pending_contact_exposure=true end
        M.event(R.state,"contact","native_owner_geometry_validated",r.id);R.dirty=true
    end
end
local function command(module,cmd,player,args)
    if module~="LofersScenario" or not enabled() then return end
    args=args or {}
    if cmd=="hello" then
        R.clients[player:getOnlineID()]={helper=args.helper==true and args.version==C.version and
            args.integration=="lua-pedestrian" and args.manifest==C.clientManifest and
            args.callback_ready==true and args.target_shield==true,
            version=tostring(args.version),at=seconds()}
        reply(player,"state",status());sendResidents(player);return
    end
    if cmd=="visibility" then
        local p=R.pending
        if p and args.epoch==R.epoch and args.id==p.id and p.required[player:getOnlineID()] then
            p.answers[player:getOnlineID()]=args.safe==true
        end;return
    end
    if cmd=="receipt" then receipt(player,args);return end
    if cmd=="seat" then
        if not C.vehicleExecution then return end
        local r=R.state.residents[args.resident_id]
        local z=r and N.actors[r.id]
        if not r or not z or args.epoch~=R.epoch or args.generation~=r.generation or
            args.lease_epoch~=r.lease_epoch or r.owner_id~=player:getOnlineID() or
            args.vehicle_id~=r.vehicle_id or LofersNative.ownerId(z)~=player:getOnlineID() then return end
        local v=getVehicleById(tonumber(r.vehicle_id));if not v then return end
        if M.distance(r.position,{x=v:getX(),y=v:getY()})>5 then return end
        if args.enter and v:getDriver() and v:getDriver()~=z then return end
        local ok=args.enter and LofersNative.enterVehicle(z,v,0) or
            (not args.enter and LofersNative.exitVehicle(z))
        if ok then
            r.in_vehicle=args.enter==true
            R.dirty=true
            broadcast("seat",{epoch=R.epoch,resident_id=r.id,generation=r.generation,
                lease_epoch=r.lease_epoch,vehicle_id=r.vehicle_id,enter=r.in_vehicle})
        end
        return
    end
    if cmd=="contact" then contact(player,args);return end
    if not admin(player) then reply(player,"result",{ok=false,reason="admin_required",request_id=args.request_id or ""});return end
    local ok,why=true,"ok";local s=R.state
    if args.expected_revision~=nil and args.expected_revision~=(s.control_revision or 0) and cmd~="state" and cmd~="inspect" then
        reply(player,"result",{ok=false,reason="stale_admin_revision",request_id=args.request_id or "",state=status()});return
    end
    if cmd=="state" then reply(player,"state",status());return
    elseif cmd=="start" then ok,why=guarded();if ok then ok,why=M.start(s,worldHour()) end
    elseif cmd=="pause" then s.paused_from=s.status;s.status="paused";R.pending=nil
    elseif cmd=="resume" then s.status=s.started_hour and "running" or "calm";s.last_hour=worldHour()
    elseif cmd=="step" then
        if not plannerReady() then ok=false;why=R.workerReason
        elseif s.status~="paused" then ok=false;why="pause_before_step"
        else local h=math.max(0,math.min(24,tonumber(args.hours) or 1));s.elapsed_hours=s.elapsed_hours+h;s.phase=M.phase(s) end
    elseif cmd=="advance" then
        local stages={initial_cases=0,first_cases=0,response=48,emergency=48,disruption=72,collapse=120,survival=168}
        local h=stages[args.phase]
        if not plannerReady() then ok=false;why=R.workerReason
        elseif not h or not s.started_hour then ok=false;why="start_and_choose_valid_phase"
        else s.elapsed_hours=math.max(s.elapsed_hours,h);s.phase=M.phase(s) end
    elseif cmd=="inspect" then
        local r=s.residents[args.id]
        if r then reply(player,"inspection",{resident=r,request_id=args.request_id or ""});return
        else ok=false;why="unknown_resident" end
    elseif cmd=="test_spawn" then ok,why=beginProbe(args.role,true)
    elseif cmd=="remove_owned" then
        local r=type(args.id)=="string" and s.residents[args.id]
        if r then ok,why=N.remove(s,r);if ok then broadcast("terminal",replica(r)) end
        else ok=false;why="resident_id_required" end
    else ok=false;why="unknown_command" end
    if ok then s.control_revision=(s.control_revision or 0)+1;R.dirty=true end
    M.event(s,"admin",cmd..":"..tostring(why));reply(player,"result",{ok=ok,reason=why or "ok",
        request_id=args.request_id or "",state=status()});reply(player,"state",status())
end
Events.OnInitGlobalModData.Add(initialize)
Events.OnTick.Add(function()
    local measure=GameTime and GameTime.getServerTime and function() return GameTime.getServerTime()/1000000 end or getTimestampMs
    local before=measure();local priorTick=R.lastTick
    local ok,err=pcall(update)
    if R.lastTick~=priorTick then
        R.lastStep=measure()-before
        if #R.timings>=128 then table.remove(R.timings,1) end
        R.timings[#R.timings+1]=R.lastStep
    end
    if not ok then
    R.lastError="scenario_update_failed";R.pending=nil
    if R.state then R.state.status="paused" end
    if seconds()-(R.lastWarning or 0)>30 then print("[LofersScenario] "..tostring(err));R.lastWarning=seconds() end
end end)
Events.OnClientCommand.Add(command)
return R
