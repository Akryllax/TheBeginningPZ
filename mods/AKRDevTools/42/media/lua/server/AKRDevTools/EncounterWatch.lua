local K=require "AKRCore/Core"
local sent=0
K.Dispatch.on(K.instance().dispatch,"OnTick","AKRDevTools.encounterConfig",function()
    local c=AKREncounterWatch
    if not AKRRuntime or not c or c.epoch~=AKRRuntime.epoch or not getServerName():match("^AKR_DayOne_Test_") then return end
    local now=getTimestampMs();if now-sent<250 then return end;sent=now
    sendServerCommand("AKRDevTools","encounterConfig",c)
end)
K.Dispatch.on(K.instance().dispatch,"OnClientCommand","AKRDevTools.encounterView",function(module,command,p,args)
    local c=AKREncounterWatch
    if module~="AKRDevTools" or command~="encounterView" or not c or not AKRRuntime then return end
    if not getServerName():match("^AKR_DayOne_Test_") or p:getUsername()~="akr" then return end
    if type(args)~="table" or args.epoch~=c.epoch or args.event~=c.event or c.epoch~=AKRRuntime.epoch
        or args.stage~=c.stage or type(args.pairs)~="table" or #args.pairs~=#c.pairs or #args.pairs>4 then return end
    local now=getTimestampMs()
    if AKREncounterReport and AKREncounterReport.event==c.event and now-AKREncounterReport.received_ms<80 then return end
    local rows={}
    for i,r in ipairs(args.pairs) do
        local expected=c.pairs[i]
        if type(r)~="table" or r.actor~=expected.actor or r.hunter~=expected.hunter or r.generation~=expected.generation then return end
        if expected.corpse and (type(r.corpse_present)~="boolean" or type(r.reanimated_present)~="boolean") then return end
        local row={corpse_present=r.corpse_present==true,reanimated_present=r.reanimated_present==true,exit_clear=r.exit_clear==true,actor=r.actor,hunter=r.hunter,generation=r.generation}
        for _,key in ipairs({"actor_present","hunter_present","actor_visible","hunter_visible","local_owner","reanimated_visible"}) do
            if type(r[key])~="boolean" then return end;row[key]=r[key]
        end
        for _,key in ipairs({"actor_x","actor_y","hunter_x","hunter_y"}) do
            if type(r[key])~="number" or r[key]~=r[key] or math.abs(r[key])>100000 then return end;row[key]=r[key]
        end
        row.actor_state=tostring(r.actor_state):sub(1,80);row.hunter_state=tostring(r.hunter_state):sub(1,80)
        for _,key in ipairs({"actor_running","actor_locked"}) do if type(r[key])=="boolean" then row[key]=r[key] end end
        for _,key in ipairs({"actor_walk_speed","actor_run_speed"}) do
            if type(r[key])=="number" and r[key]==r[key] and math.abs(r[key])<=100 then row[key]=r[key] end
        end
        row.actor_visibility=tostring(r.actor_visibility or "unavailable"):sub(1,200)
        row.actor_action=tostring(r.actor_action or ""):sub(1,80)
        row.actor_reaction=tostring(r.actor_reaction or ""):sub(1,80)
        row.hunter_diagnostic=tostring(r.hunter_diagnostic or ""):sub(1,200)
        rows[i]=row
    end
    AKREncounterReport={player=p,epoch=c.epoch,event=c.event,stage=c.stage,received_ms=now,pairs=rows}
end)
