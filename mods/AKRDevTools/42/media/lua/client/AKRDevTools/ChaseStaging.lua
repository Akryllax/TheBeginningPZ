-- Observation/ordinary native zombie AI only. No transforms, health or world edits.
local K=require 'AKRCore/Core'
local core=K.instance()
local config,last=nil,0
local function finite(n) return type(n)=='number' and n==n and math.abs(n)<100000 end
K.Dispatch.on(core.dispatch,'OnServerCommand','AKRDevTools.chaseConfig',function(module,command,a)
    if module~='AKRDevTools' or command~='chaseConfig' or type(a)~='table' then return end
    if type(a.epoch)~='string' or #a.epoch>80 or type(a.revision)~='number' or type(a.candidates)~='table' or #a.candidates>6 then return end
    for _,p in ipairs(a.candidates) do
        if not finite(p.x) or not finite(p.y) or (p.dir~=1 and p.dir~=-1) then return end
    end
    config=a
    AKRChaseClient={epoch=a.epoch,revision=a.revision,count=4,hunters=a.hunter_id and a.hunter_id>=0 and {a.hunter_id} or {}}
end)
-- Pixel margin protects sprite extent and small camera pans. Unknown loaded tiles fail closed.
local function tile(x,y,player)
    local sq=getCell():getGridSquare(math.floor(x),math.floor(y),0)
    local zoom=getCore():getZoom(player:getPlayerNum())
    local sx=(IsoUtils.XToScreen(x,y,0,0)-getCameraOffX())/zoom
    local sy=(IsoUtils.YToScreen(x,y,0,0)-getCameraOffY())/zoom
    local on=sx>=-160 and sy>=-200 and sx<=getCore():getScreenWidth()+160 and sy<=getCore():getScreenHeight()+200
    return sq~=nil,not on or (sq~=nil and not sq:isCanSee(player:getPlayerNum()))
end
local function footprint(x,y,player)
    local loaded,hidden=true,true
    for _,d in ipairs({{0,0},{-1,-1},{-1,1},{1,-1},{1,1}}) do
        local l,h=tile(x+d[1],y+d[2],player);loaded=loaded and l;hidden=hidden and h
    end
    return loaded,hidden
end
local function bodyView(body,player,unknown)
    if not body then
        if unknown then return {present='unknown',hidden=false} end
        return {present=false,hidden=true}
    end
    local l,h=footprint(body:getX(),body:getY(),player)
    local sq=body:getSquare()
    return {present=true,hidden=h,loaded=l,visible=body:isOnScreen() and sq~=nil and sq:isCanSee(player:getPlayerNum()),
        x=body:getX(),y=body:getY()}
end
K.Dispatch.on(core.dispatch,'OnTick','AKRDevTools.chaseObserve',function()
    local now=getTimestampMs();if not config or now-last<250 then return end
    local player=getSpecificPlayer(0);if not player then return end;last=now
    local candidates={}
    for i,p in ipairs(config.candidates) do
        local loaded,hidden=true,true
        -- Birth footprints plus the short approach needed to establish actual pursuit.
        for _,offset in ipairs({-8,0,4,8}) do
            local l,h=footprint(p.x,p.y+offset*p.dir,player);loaded=loaded and l;hidden=hidden and h
        end
        candidates[i]={loaded=loaded,hidden=hidden}
    end
    local actor=config.actor_id and getPlayerByOnlineID(config.actor_id)
    local av=bodyView(actor,player,false)
    av.ready=actor~=nil and actor:getHumanVisual()~=nil and config.actor_clothes~=nil and config.actor_clothes>0 and actor:getItemVisuals():size()==config.actor_clothes
    local z,list=nil,getCell():getZombieList()
    for i=0,math.min(list:size(),64)-1 do local b=list:get(i);if b:getOnlineID()==config.hunter_id then z=b;break end end
    local zv=bodyView(z,player,not z and list:size()>64)
    zv.local_owner=z~=nil and not z:isRemoteZombie()
    zv.targeted=z~=nil and actor~=nil and z:getTarget()==actor
    sendClientCommand(player,'AKRDevTools','chaseView',{epoch=config.epoch,revision=config.revision,
        x=player:getX(),y=player:getY(),candidates=candidates,actor=av,hunter=zv})
end)
