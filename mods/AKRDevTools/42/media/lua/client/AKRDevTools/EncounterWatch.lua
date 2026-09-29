-- Bounded presentation diagnostics; stock AI owns pursuit, server owns damage.
local K=require "AKRCore/Core"
local V=require "AKRDevTools/PoolVisibility"
local config,received,last=nil,0,0
local function integer(v,lo,hi) return type(v)=="number" and v==math.floor(v) and v>=lo and v<=hi end
K.Dispatch.on(K.instance().dispatch,"OnServerCommand","AKRDevTools.encounterConfig",function(module,command,args)
    if module~="AKRDevTools" or command~="encounterConfig" or type(args)~="table" then return end
    if type(args.epoch)~="string" or #args.epoch>128 or type(args.event)~="string" or #args.event>160 then return end
    if (args.count~=1 and args.count~=4 and not (args.count==2 and args.actor_only==true)) or type(args.pairs)~="table" or #args.pairs>args.count then return end
    if type(args.stage)~="string" or #args.stage>32 then return end
    local ids={};local hunters={}
    for _,row in ipairs(args.pairs) do
        if type(row)~="table" or not integer(row.actor,4096,4099) or not (integer(row.hunter,0,32767) or (args.actor_only==true and row.hunter==-1 and row.active==false))
            or not integer(row.generation,1,9007199254740991) or type(row.active)~="boolean"
            or ids[row.actor] or (row.hunter>=0 and hunters[row.hunter]) then return end
        if row.exit_x~=nil and (not integer(row.exit_x,0,100000) or not integer(row.exit_y,0,100000)) then return end
        ids[row.actor]=true;if row.hunter>=0 then hunters[row.hunter]=true end
    end
    config=args;received=getTimestampMs()
    local active={};local targets={}
    for _,row in ipairs(args.pairs) do if row.active then active[#active+1]=row.hunter;targets[row.hunter]=row.actor end end
    AKREncounterClient={epoch=args.epoch,event=args.event,count=args.count,hunters=active,targets=targets,received=received,stage=args.stage}
end)
K.Dispatch.on(K.instance().dispatch,"OnTick","AKRDevTools.encounterWatch",function()
    local now=getTimestampMs()
    if not config or now-received>2500 or now-last<100 then return end
    local player=getSpecificPlayer(0);if not player then return end
    if config.stage=="COUNTDOWN" then getGameTime():setTimeOfDay(12) end
    last=now
    local wanted={};for _,r in ipairs(config.pairs) do wanted[r.hunter]=true;if r.reanimated then wanted[r.reanimated]=true end end
    local found={};local zombies=getCell():getZombieList()
    for i=0,math.min(zombies:size(),256)-1 do local z=zombies:get(i);if wanted[z:getOnlineID()] then found[z:getOnlineID()]=z end end
    local rows={}
    for _,r in ipairs(config.pairs) do
        local actor=getPlayerByOnlineID(r.actor);local hunter=found[r.hunter]
        local corpsePresent=false
        if r.corpse and type(r.corpse_x)=="number" and type(r.corpse_y)=="number" then
            local sq=getCell():getGridSquare(math.floor(r.corpse_x),math.floor(r.corpse_y),0)
            if sq then
                local bodies=sq:getStaticMovingObjects()
                for i=0,math.min(bodies:size(),64)-1 do
                    local body=bodies:get(i)
                    if instanceof(body,"IsoDeadBody") and tostring(body:getObjectIDAsLong())==r.corpse then corpsePresent=true end
                end
                if bodies:size()>64 then corpsePresent=true end -- unknown means retain
            end
        end
        if hunter and not hunter:isRemoteZombie() then
            -- Explicit fixture hold only. RUNNING hunters use native simulation.
            hunter:setUseless(not r.active)
            if not r.active then hunter:setTarget(nil) end
        end
        if actor and (config.stage=="POSITIONING" or config.stage=="REUSE_POSITION" or config.stage=="REUSE_WALK") then player:faceLocationF(actor:getX(),actor:getY()) end
        local target=hunter and hunter:getTarget()
        local diagnostic=string.format("target=%s useless=%s remote=%s seen=%s ghost=%s calls=%s",
            tostring(target and target:getOnlineID()),tostring(hunter and hunter:isUseless()),
            tostring(hunter and hunter:isRemoteZombie()),tostring(hunter and hunter:getTargetSeenTime()),
            tostring(actor and actor:isGhostMode()),
            tostring(AKRHunterPerception and AKRHunterPerception.calls))
        local exitClear=false
        if r.exit_x then
            exitClear=true
            for n=0,8 do
                local sq=getCell():getGridSquare(r.exit_x,r.exit_y+n,0)
                if not sq or sq:getDoor(false) then exitClear=false;break end
            end
        end
        local rv=V.body(found[r.reanimated],player,r.reanimated,false,true)
        local av=V.body(actor,player,r.actor,false,true);local hv=V.body(hunter,player,r.hunter,false,true)
        rows[#rows+1]={exit_clear=exitClear,actor=r.actor,generation=r.generation,hunter=r.hunter,
            corpse_present=corpsePresent,reanimated_visible=rv.visible,reanimated_present=r.reanimated~=nil and (found[r.reanimated]~=nil or zombies:size()>256),
            actor_visibility=string.format("present=%s on_screen=%s loaded=%s los=%s visible=%s",tostring(actor~=nil),tostring(av.on_screen),tostring(av.loaded),tostring(av.line_of_sight),tostring(av.visible)),
            actor_present=actor~=nil,hunter_present=hunter~=nil,actor_visible=av.visible,hunter_visible=hv.visible,
            actor_x=actor and actor:getX() or 0,actor_y=actor and actor:getY() or 0,
            hunter_x=hunter and hunter:getX() or 0,hunter_y=hunter and hunter:getY() or 0,
            local_owner=hunter~=nil and not hunter:isRemoteZombie(),
            hunter_diagnostic=diagnostic,
            actor_state=actor and actor:getCurrentStateName() or "absent",
            actor_action=actor and actor:getActionStateName() or "absent",
            actor_running=actor~=nil and actor:isRunning(),
            actor_reaction=actor and actor:getHitReaction() or "",
            actor_locked=actor~=nil and actor:getIgnoreMovement(),
            actor_walk_speed=actor and actor:getVariableFloat("WalkSpeed",0) or 0,
            actor_run_speed=actor and actor:getVariableFloat("RunSpeed",0) or 0,
            hunter_state=hunter and hunter:getCurrentStateName() or "absent"}
    end
    sendClientCommand(player,"AKRDevTools","encounterView",{epoch=config.epoch,event=config.event,stage=config.stage,pairs=rows})
end)
