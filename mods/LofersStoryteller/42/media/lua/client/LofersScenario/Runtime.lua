local C=require "LofersScenario/Config"
local M=require "LofersScenario/Model"
local G=require "LofersScenario/ClientGate"
local L={residents={},outfits={},actors={},runs={},epoch=nil,lastHello=0,offset=0,paused=false,planningPaused=true}
LofersScenarioClient=L
local function now() return getTimestampMs()/1000 end
local function wh() return getGameTime():getWorldAgeHours() end
local function player() return getSpecificPlayer(0) end
local function send(cmd,args)
    local p=player();if p then args.epoch=L.epoch;sendClientCommand(p,"LofersScenario",cmd,args) end
end
local function ready() return G.ready() end
local function actorId(z)
    local id=z:getPersistentOutfitID()
    return L.outfits[id] or (BanditUtils and L.outfits[BanditUtils.GetCharacterID(z)])
end
local function init(z,r)
    if not ready() then return false end
    local cluster=GetBanditClusterData(r.outfit_id);local brain=cluster and cluster[r.outfit_id]
    if not brain then return false end
    local md=z:getModData();local bound=md.LofersScenario
    if not bound or bound.generation~=r.generation then
        md.LofersScenario={id=r.id,generation=r.generation,lease_epoch=r.lease_epoch,owner_id=r.owner_id}
        BanditBrain.Update(z,brain);Bandit.ApplyVisuals(z,brain)
        -- ApplyVisuals restores profile health; initialization must preserve the
        -- authority's injury state, including a late-joining observer.
        z:setHealth((brain.health or 1)*math.max(0,math.min(100,r.health or 100))/100)
        md.LofersHealthRevision=r.health_revision or 0
        z:setNoTeeth(true);z:setVariable("Bandit",true);z:setVariable("NoLungeTarget",true)
        z:setVariable("ZombieHitReaction","Chainsaw")
        z:setVariable("LimpSpeed",0.8);z:setVariable("RunSpeed",0.68);z:setVariable("WalkSpeed",1.0)
        z:setVariable("BanditWalkType","Walk");z:setWalkType("Walk")
        z:setVariable("BanditPrimary","");z:setVariable("BanditSecondary","")
        z:getDescriptor():setVoicePrefix("Bandit");md.brainId=r.outfit_id
    else bound.lease_epoch=r.lease_epoch;bound.owner_id=r.owner_id end
    if (r.health_revision or 0)>(md.LofersHealthRevision or 0) then
        z:setHealth((brain.health or 1)*math.max(0,math.min(100,r.health or 100))/100)
        md.LofersHealthRevision=r.health_revision
    end
    return true
end
local function canAct(z,r)
    local p=player()
    local actual=z:getOwnerPlayer()
    return p and r.owner_id==p:getOnlineID() and not z:isRemoteZombie() and
        (not actual or actual:getOnlineID()==p:getOnlineID()) and
        now()+L.offset<r.lease_until and not L.paused and ready()
end
local function stop(z,r)
    if z then z:getPathFindBehavior2():cancel();z:setPath2(nil) end
end
local animations={WAIT="Idle",EAT="Eat",REST="SitGround",WORK="Loot",SHOP="Loot",
    SOCIALIZE="Talk",SEEK_HELP="PainTorso",TREAT="Bandage",SCAVENGE="Loot",SHELTER="SitGround",
    PARK="Idle",ENTER_VEHICLE="Idle",EXIT_VEHICLE="Idle"}
local moving={WALK=true,FLEE=true,PATROL=true}
local function present(z,r,run)
    local a=r.action;local label=a and a.kind or "WAIT"
    if a and label~="DRIVE" and label~="ENTER_VEHICLE" and label~="EXIT_VEHICLE" and label~="PARK" and
        M.distance({x=z:getX(),y=z:getY()},a.target)>1.2 then label=label=="FLEE" and "FLEE" or "WALK" end
    if run.visual~=label then
        run.visual=label
        z:setVariable("LofersAction",label)
        z:setVariable("BanditWalkType",(label=="FLEE" and "Run") or "Walk")
        if not moving[label] and label~="DRIVE" then z:setBumpType(animations[label] or "Idle") end
    end
    if (r.infection=="symptomatic" or r.infection=="severe") and now()-(run.symptom or 0)>18 then
        run.symptom=now();z:setBumpType("Cough")
    end
end
local function complete(r,run,state,reason)
    if now()-(run.sent or 0)<1 then return end
    run.sent=now();run.pending=true
    send("receipt",{resident_id=r.id,action_id=r.action.id,generation=r.generation,
        plan_revision=r.plan_revision,lease_epoch=r.lease_epoch,state=state or "completed",reason=reason or ""})
end
local function walk(z,target,run)
    local dist=M.distance({x=z:getX(),y=z:getY()},target)
    if dist<0.9 and math.abs(z:getZ()-(target.z or 0))<0.5 then
        z:getPathFindBehavior2():cancel();z:setPath2(nil);return "arrived"
    end
    if not run.pathAt or now()-run.pathAt>8 then
        z:getPathFindBehavior2():pathToLocation(math.floor(target.x),math.floor(target.y),target.z or 0)
        run.pathAt=now();run.pathAttempts=(run.pathAttempts or 0)+1
    end
    z:setUseless(false);z:setTarget(nil)
    local result=z:getPathFindBehavior2():update()
    if result==BehaviorResult.Failed then
        run.pathAt=nil
        if (run.pathAttempts or 0)>4 then return "failed" end
    end
    return "moving"
end
local function contacts(z,r,run)
    if now()-(run.contactAt or 0)<1 then return end;run.contactAt=now()
    local cell=getCell();local nearest,dist
    for dx=-1,1 do for dy=-1,1 do
        local sq=cell:getGridSquare(math.floor(z:getX())+dx,math.floor(z:getY())+dy,math.floor(z:getZ()))
        if sq then local all=sq:getMovingObjects()
            for i=0,math.min(all:size(),8)-1 do local other=all:get(i)
                if other~=z and instanceof(other,"IsoZombie") and not other:isDead() and
                    not other:getVariableBoolean("Bandit") then
                    local d=(other:getX()-z:getX())^2+(other:getY()-z:getY())^2
                    if not dist or d<dist then nearest=other;dist=d end
                end
            end
        end
    end end
    if nearest and not z:getVehicle() then
        -- Immediate owner-only avoidance remains available without a planner roundtrip.
        local dx=z:getX()-nearest:getX();local dy=z:getY()-nearest:getY()
        local len=math.max(0.1,math.sqrt(dx*dx+dy*dy))
        run.escape={x=z:getX()+dx/len*5,y=z:getY()+dy/len*5,z=math.floor(z:getZ())}
        run.escapeUntil=now()+2;run.pathAt=nil
    end
end
local function updateActor(z)
    if not C.enabled() or not L.epoch then return end
    local id=actorId(z);local r=id and L.residents[id]
    if not r or r.lifecycle~="active" then return end
    L.actors[id]=z
    local run=L.runs[id] or {};L.runs[id]=run
    if now()-(run.last or 0)<0.1 then return end;run.last=now()
    if not init(z,r) then return end
    present(z,r,run)
    if not canAct(z,r) then
        if run.owned then stop(z,r);run.owned=false end
        return
    end
    run.owned=true
    contacts(z,r,run)
    if run.escapeUntil and now()<run.escapeUntil then walk(z,run.escape,run);return end
    if run.escapeUntil then run.escapeUntil=nil;run.pathAt=nil;run.pathAttempts=0 end
    if L.planningPaused then stop(z,r);return end
    local a=r.action
    if not a then stop(z,r);return end
    if a.kind=="DRIVE" or a.kind=="PARK" or a.kind=="ENTER_VEHICLE" or a.kind=="EXIT_VEHICLE" then
        stop(z,r);complete(r,run,"failed","vehicle_execution_unverified");return
    end
    if run.actionId~=a.id or run.lease~=r.lease_epoch then
        stop(z,r);run={last=now(),owned=true,actionId=a.id,lease=r.lease_epoch,start=wh(),startReal=now()};L.runs[id]=run
    end
    local limit=150+(a.duration_hours or 0)*getGameTime():getMinutesPerDay()*60/24
    if now()-run.startReal>limit then stop(z,r);complete(r,run,"failed","execution_timeout");return end
    if run.pending then complete(r,run,run.outcome,run.reason);return end
    local result=walk(z,a.target,run)
    local why
    if result=="failed" then stop(z,r);run.outcome="failed";run.reason=why or "path_failed";complete(r,run,run.outcome,run.reason)
    elseif result=="arrived" then
        run.arrivedHour=run.arrivedHour or wh()
        if not moving[a.kind] then present(z,r,run) end
        if wh()-run.arrivedHour>=(a.duration_hours or 0) then complete(r,run) end
    end
end
local function visible(point)
    for i=0,getNumActivePlayers()-1 do
        local p=getSpecificPlayer(i)
        if p then
            local sq=getCell():getGridSquare(math.floor(point.x),math.floor(point.y),point.z or 0)
            if sq and (sq:isCanSee(i) or sq:isCouldSee(i)) then return true end
            -- Also veto points inside the current camera, including panning and zoom.
            local sx=IsoUtils.XToScreen(point.x,point.y,point.z or 0,0)-IsoCamera.getOffX(i)
            local sy=IsoUtils.YToScreen(point.x,point.y,point.z or 0,0)-IsoCamera.getOffY(i)
            local zoom=getCore():getZoom(i);sx=sx/zoom;sy=sy/zoom
            if sx>=-C.visibilityMargin and sy>=-C.visibilityMargin and
                sx<=getPlayerScreenWidth(i)+C.visibilityMargin and sy<=getPlayerScreenHeight(i)+C.visibilityMargin then return true end
        end
    end
    return false
end
local function terminal(r)
    local prior=L.residents[r.id]
    local z=L.actors[r.id]
    if z then
        stop(z,r);z:setNoTeeth(false);z:setVariable("Bandit",false);z:setUseless(false)
        z:setVariable("BanditWalkType","");z:setWalkType("2")
        z:getModData().LofersScenario=nil;z:getModData().brain=nil;z:getModData().brainId=nil
    end
    L.residents[r.id]=nil
    if r.outfit_id then L.outfits[r.outfit_id]=nil end
    if prior and prior.outfit_id then L.outfits[prior.outfit_id]=nil end
    L.runs[r.id]=nil;L.actors[r.id]=nil
end
local function command(module,cmd,args)
    if module~="LofersScenario" or not C.enabled() then return end
    if cmd=="residents" then
        if L.epoch and L.epoch~=args.epoch then
            for id,z in pairs(L.actors) do stop(z,L.residents[id] or {}) end
            L.residents={};L.outfits={};L.runs={};L.actors={}
        end
        L.epoch=args.epoch;L.offset=args.server_seconds-now();L.paused=args.paused==true
        L.planningPaused=args.planning_paused==true
        local included={};for _,r in ipairs(args.residents or {}) do included[r.id]=true end
        local removed={};for id,r in pairs(L.residents) do if not included[id] then removed[#removed+1]=r end end
        for _,r in ipairs(removed) do terminal(r) end
        for _,r in ipairs(args.residents or {}) do
            if r.lifecycle=="dead" or r.lifecycle=="zombie" then terminal(r)
            else L.residents[r.id]=r;L.outfits[r.outfit_id]=r.id end
        end
    elseif cmd=="visibility" then
        local ok,result=pcall(visible,args.point)
        send("visibility",{id=args.id,safe=ok and not result})
    elseif cmd=="terminal" then terminal(args)
    elseif cmd=="seat" then return -- Native NPC seats are not enabled on ordinary clients.
    elseif cmd=="receipt_ack" then
        local run=L.runs[args.resident_id]
        if run and args.action_id==run.actionId then run.ack=args.ok end
    end
end
Events.OnServerCommand.Add(command)
Events.OnZombieUpdate.Add(function(z)
    local ok,err=pcall(updateActor,z)
    if not ok and now()-(L.lastError or 0)>30 then
        L.lastError=now();print("[LofersScenario] client executor: "..tostring(err))
    end
end)
Events.OnTick.Add(function()
    if C.enabled() and player() and now()-L.lastHello>10 then
        L.lastHello=now();send("hello",{helper=ready()==true,version=C.version,
            integration="lua-pedestrian",manifest=G.manifest,callback_ready=ready()==true,
            target_shield=true,vehicle_execution=false})
    end
end)
-- A safe no-task program exists on every client before any brain arrives.
ZombiePrograms=ZombiePrograms or {};ZombiePrograms.LofersCivilian={}
ZombiePrograms.LofersCivilian.Prepare=function() return {status=true,next="Main",tasks={}} end
ZombiePrograms.LofersCivilian.Main=function() return {status=true,next="Main",tasks={}} end
return L
