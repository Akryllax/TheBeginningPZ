-- Bounded observation only. Never moves Actors or supplies gameplay decisions.
local K=require "AKRCore/Core"
local core=K.instance()
local V=require "AKRDevTools/PoolVisibility"
local function finite(n) return type(n)=="number" and n==n and math.abs(n)<100000 end
local last=0
AKRPoolWatchClient={count=4,phase=0,wave=0}
K.Dispatch.on(core.dispatch,"OnServerCommand","AKRDevTools.poolConfig",function(module,command,args)
    if module~="AKRDevTools" or command~="poolConfig" or type(args)~="table" then return end
    if args.actor_count~=4 and args.actor_count~=32 and args.actor_count~=64 then return end
    if type(args.epoch)~="string" or #args.epoch>80 then return end
    local crowd=args.hunter_limit==128 and args.actor_count==64
    if args.hunter_limit==128 and not crowd then return end
    if crowd then
        if type(args.stage_points)~="table" or #args.stage_points~=192 then return end
        for _,p in ipairs(args.stage_points) do if type(p)~="table" or not finite(p.x) or not finite(p.y) then return end end
    end
    if args.hunters~=nil and (type(args.hunters)~="table" or #args.hunters>(crowd and 128 or 16)) then return end
    AKRPoolWatchClient={crowd=crowd,stage_points=crowd and args.stage_points or {},hunters=args.hunters or {},count=args.actor_count,phase=tonumber(args.phase) or 0,wave=tonumber(args.wave) or 0,epoch=args.epoch}
end)
K.Dispatch.on(core.dispatch,"OnTick","AKRDevTools.poolWatch",function()
    local now=getTimestampMs()
    if now-last<250 then return end
    local epoch=AKRPedestrianClient and AKRPedestrianClient.epoch
    local player=getSpecificPlayer(0)
    if not epoch or not player then return end
    last=now
    local entries={}
    local config=AKRPoolWatchClient;local crowd=config.crowd==true
    local stage={loaded=false,hidden=false}
    if crowd and (config.phase==2 or config.phase==3) then
        stage={loaded=true,hidden=true}
        for _,p in ipairs(config.stage_points) do local l,h=V.footprint(p.x,p.y,player);stage.loaded=stage.loaded and l;stage.hidden=stage.hidden and h end
    end
    local count=AKRPoolWatchClient.epoch==epoch and AKRPoolWatchClient.count or 4
    for id=4096,4095+count do
        local actor=getPlayerByOnlineID(id)
        local entry=V.body(actor,player,id,false,crowd)
        if actor then
            entry.on_screen=actor:isOnScreen()
            entry.x=actor:getX();entry.y=actor:getY();entry.female=actor:isFemale()
            entry.name=actor:getDisplayName();entry.clothes=actor:getItemVisuals():size()
        end
        entries[#entries+1]=entry
    end
    local hunts={};local found={};local ids=AKRPoolWatchClient.hunters or {}
    if #ids>0 then
        local wanted={};for _,id in ipairs(ids) do wanted[id]=true end
        local list=getCell():getZombieList()
        -- A capped scan with explicit unknown on overflow; missing is not guessed absent.
        for i=0,math.min(list:size(),256)-1 do
            local z=list:get(i);local id=z:getOnlineID()
            if wanted[id] then found[id]=V.body(z,player,id,false,crowd);found[id].local_owner=not z:isRemoteZombie() end
        end
        for i,id in ipairs(ids) do
            hunts[i]=found[id] or V.body(nil,player,id,list:size()>256,crowd)
        end
    end
    sendClientCommand(player,"AKRDevTools","poolView",{epoch=epoch,wave=config.wave,actors=entries,hunters=hunts,stage=stage,observer_x=player:getX(),observer_y=player:getY()})
end)
