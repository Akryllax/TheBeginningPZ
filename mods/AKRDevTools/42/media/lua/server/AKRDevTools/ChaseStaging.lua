-- Disposable native staging experiment. Module registration does not activate ambience.
local K=require 'AKRCore/Core'
local core=K.instance()
AKRChaseReports={}
local last=0
local function finite(n) return type(n)=='number' and n==n and math.abs(n)<100000 end
local function body(a)
    if type(a)~='table' or (a.present~=true and a.present~=false and a.present~='unknown') or type(a.hidden)~='boolean' then return nil end
    if a.present==true and (not finite(a.x) or not finite(a.y)) then return nil end
    return {present=a.present,hidden=a.hidden,loaded=a.loaded==true,visible=a.visible==true,x=a.x,y=a.y,
        ready=a.ready==true,local_owner=a.local_owner==true,targeted=a.targeted==true}
end
K.Dispatch.on(core.dispatch,'OnClientCommand','AKRDevTools.chaseView',function(module,command,player,a)
    local c=AKRChase
    if not c or not AKRRuntime or not getServerName():match('^AKR_DayOne_Test_') or module~='AKRDevTools' or command~='chaseView' then return end
    if type(a)~='table' or a.epoch~=c.epoch or a.revision~=c.revision or not finite(a.x) or not finite(a.y) then return end
    if type(a.candidates)~='table' or #a.candidates~=#c.candidates or #a.candidates>6 then return end
    local id=player:getOnlineID();local now=getTimestampMs();local old=AKRChaseReports[id]
    if old and now-old.received<150 then return end
    local cs={}
    for i,v in ipairs(a.candidates) do
        if type(v)~='table' or type(v.loaded)~='boolean' or type(v.hidden)~='boolean' then return end
        cs[i]={loaded=v.loaded,hidden=v.hidden}
    end
    local av,zv=body(a.actor),body(a.hunter);if not av or not zv then return end
    AKRChaseReports[id]={epoch=a.epoch,revision=a.revision,received=now,x=a.x,y=a.y,candidates=cs,actor=av,hunter=zv}
end)
K.Dispatch.on(core.dispatch,'OnTick','AKRDevTools.chasePublish',function()
    local c=AKRChase;if not c or not AKRRuntime or c.epoch~=AKRRuntime.epoch then return end
    local now=getTimestampMs();if now-last<250 then return end;last=now
    if c.registry_error then return end
    if not AKRChaseResident then
        local R=require 'AKRResidents/Residents'
        if type(R)~='table' then c.registry_error='resident_module_unavailable';return end
        local state=assert(R.open(ModData.getOrCreate('AKRCore')))
        local id='staged-civilian-1'
        -- A previous native binding is uncertain on restart; do not silently resurrect it.
        if state.residents[id] then c.registry_error='resident_restart_requires_reconciliation';return end
        local resident=assert(R.add(state,id,927,{x=10590.5,y=10060.5,z=0},{{x=10590.5,y=10140.5,z=0}}))
        resident.presentation={name='Jordan Hale',outfit='Generic01',female=false}
        AKRChaseResident=resident
    end
    sendServerCommand('AKRDevTools','chaseConfig',c)
end)
