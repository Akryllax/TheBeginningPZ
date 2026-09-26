require "ZomboidObserver/Shared"
if not isServer() then return end
local O = ZomboidObserver
local session = tostring(O.now()) .. "-" .. tostring(ZombRand(1000000))
local sequence, slot, bytes, generation = 0, -1, 0, 0
local lastHeartbeat, lastWarning = 0, 0
local limits = {}
local initialized = false
local MAX_FILE = 16*1024*1024

local function warn(message)
    if O.now()-lastWarning > 10000 then
        print("[ZomboidObserver] " .. tostring(message)); lastWarning=O.now()
    end
end

local function rotate()
    slot = (slot+1)%4
    generation = generation+1
    local writer = getFileWriter("ZomboidObserver/observer-"..slot..".txt",true,false)
    if not writer then error("cannot create observer journal") end
    local header = O.json({format="zomboid-observer-journal-v1",created_at=O.now(),session=session,
                          generation=generation,sequence=sequence}) .. "\n"
    writer:write(header);writer:close();bytes=#header
end

local function emit(observer, kind, fields)
    sequence=sequence+1
    local packet={version=1,world=O.world,session=session,sequence=sequence,observed_at=O.now(),observer=observer,kind=kind}
    for key,value in pairs(fields or {}) do packet[key]=value end
    local line=O.json(packet).."\n"
    if #line>O.maxLine then error("journal record too large") end
    if slot<0 or bytes+#line>MAX_FILE then rotate() end
    local writer=getFileWriter("ZomboidObserver/observer-"..slot..".txt",true,true)
    if not writer then error("observer journal unavailable") end
    writer:write(line);writer:close();bytes=bytes+#line
end

local function metadata()
    local grid=getWorld():getMetaGrid()
    emit("server","metadata",{bounds={min_x=grid:getMinX(),min_y=grid:getMinY(),max_x=grid:getMaxX(),max_y=grid:getMaxY(),
         cell_size=256,unit_size=32,world_version=249}})
    initialized=true
    print("[ZomboidObserver] v0.1 exporter started; world="..O.world.." session="..session)
end

local function heartbeat()
    local all=getOnlinePlayers();local players={}
    for i=0,all:size()-1 do
        local player=all:get(i)
        if player and not player:isDead() then
            players[#players+1]={name=player:getUsername(),x=player:getX(),y=player:getY(),z=math.floor(player:getZ()),
                                heading=(O.call(player,"getDirectionAngle") or 0)%360,online=true}
        end
    end
    emit("server","heartbeat",{players=players})
end

local function tick()
    local now=O.now()
    if now-lastHeartbeat<1000 then return end
    lastHeartbeat=now
    local ok,err=pcall(function()
        if not initialized then metadata() end
        heartbeat()
    end)
    if not ok then warn(err) end
end

local function finite(v) return type(v)=="number" and v==v and math.abs(v)<200000 end
local function nearby(player, x,y,z)
    return finite(x) and finite(y) and finite(z) and math.abs(x-player:getX())<=90 and math.abs(y-player:getY())<=90 and math.abs(z-player:getZ())<=1
end

local function canonicalId(id)
    if type(id)~="string" then return id end
    local net=id:match("^vehicle%-net:(%d+)$")
    if net then
        local vehicle=getVehicleById(tonumber(net))
        if not vehicle then error("vehicle is no longer loaded") end
        local sql=vehicle:getSqlId()
        if sql and sql>=0 then return "vehicle:"..sql end
        return "vehicle:"..session..":"..net
    end
    if id:sub(1,6)=="actor:" then return "actor:"..session..":"..id:sub(7) end
    return id
end

local function receive(module,command,player,args)
    if module~=O.module or not player or type(args)~="table" then return end
    local username=player:getUsername()
    local now=O.now()
    local limit=limits[username]
    if not limit or now-limit.at>=1000 then limit={at=now,count=0,bytes=0};limits[username]=limit end
    local ok,encoded=pcall(O.json,args)
    if not ok or #encoded>O.maxPacket then warn("rejected oversized observation");return end
    limit.count=limit.count+1;limit.bytes=limit.bytes+#encoded
    if limit.count>24 or limit.bytes>240000 then return end
    local accepted={}
    if command=="pulse" then
        tick()
        return
    elseif command=="observe" then
        tick()
        if args.tiles and #args.tiles>64 then return end
        if args.seen and #args.seen>256 then return end
        if args.inspections and #args.inspections>4 then return end
        for _,tile in ipairs(args.tiles or {}) do
            if not nearby(player,tile.x,tile.y,tile.z) or type(tile.objects)~="table" or #tile.objects>128 then return end
            for _,obj in ipairs(tile.objects) do
                if not finite(obj.x) or not finite(obj.y) or math.floor(obj.x)~=tile.x or math.floor(obj.y)~=tile.y or obj.z~=tile.z then return end
                local valid,id=pcall(canonicalId,obj.id)
                if not valid then return end
                obj.id=id
            end
        end
        for _,tile in ipairs(args.seen or {}) do if not nearby(player,tile.x,tile.y,tile.z) then return end end
        for _,inspection in ipairs(args.inspections or {}) do
            local valid,id=pcall(canonicalId,inspection.id)
            if not valid then return end
            inspection.id=id
        end
        accepted.tiles=args.tiles;accepted.seen=args.seen;accepted.inspections=args.inspections;accepted.coverage=args.coverage
        ok,encoded=pcall(emit,username,"observation",accepted)
    elseif command=="markers" then
        if type(args.markers)~="table" or #args.markers>128 then return end
        for _,marker in ipairs(args.markers) do
            if marker.author~=username or marker.public~=true then return end
        end
        ok,encoded=pcall(emit,username,"markers",{markers=args.markers})
    else return end
    if not ok then warn(encoded) end
end

-- Dedicated servers do not dispatch the client OnTick event. Client pulses drive
-- wall-clock updates while connected; startup and game-minute events cover setup.
O.serverReady=function()
    initialized=false
    local ok,err=pcall(function() metadata();heartbeat() end)
    if not ok then warn(err) end
end
Events.OnServerStarted.Add(O.serverReady)
Events.EveryOneMinute.Add(tick)
Events.OnClientCommand.Add(receive)
