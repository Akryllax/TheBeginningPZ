local C=require "LofersScenario/Config"
local M=require "LofersScenario/Model"
local W={roomCursor=0,tileCursor=0,playerCursor=1}
local homes={bedroom=true,livingroom=true,kitchen=true}
local shops={grocery=true,conveniencestore=true,store=true,shop=true,pharmacy=true}
local clinics={medical=true,hospitalroom=true,medicaloffice=true,clinic=true}
local function kind(name)
    name=string.lower(name or "")
    if homes[name] then return "home" end
    if clinics[name] or name:find("medical",1,true) then return "clinic" end
    if shops[name] or name:find("store",1,true) then return "shop" end
    if name:find("police",1,true) then return "police" end
    if name:find("fire",1,true) then return "firestation" end
    return "work"
end
function W.discover(s,players,operations,staticHomes)
    if #players==0 then return end
    local cell=getCell();local rooms=cell:getRoomList()
    for _=1,math.min(operations,rooms:size()) do
        W.roomCursor=W.roomCursor%rooms:size()
        local room=rooms:get(W.roomCursor);W.roomCursor=W.roomCursor+1
        local def=room and room:getRoomDef()
        if def then
            local sq=def:getFreeSquare()
            if sq then
                local k=kind(room:getName())
                local building=room:getBuilding();local bd=building and building:getDef()
                -- One household anchor per building, unique room anchors for work.
                local id=(k=="home" and bd) and ("home:"..bd:getX()..":"..bd:getY()) or
                    (k..":"..tostring(def:getIDString()))
                local place=M.addPlace(s,id,k,{x=sq:getX()+0.5,y=sq:getY()+0.5,z=sq:getZ()})
                if not staticHomes and place and k=="home" and not place.household and #s.order<C.maxResidents then
                    place.household={}
                    for _=1,2 do local r=M.resident(s,place);if r then table.insert(place.household,r.id) end end
                end
            end
        end
    end
    for _=1,operations do
        W.playerCursor=(W.playerCursor-1)%#players+1
        local p=players[W.playerCursor]
        local dx=W.tileCursor%41-20;local dy=math.floor(W.tileCursor/41)-20
        local x=math.floor(p:getX()/4)*4+dx*4;local y=math.floor(p:getY()/4)*4+dy*4
        W.tileCursor=W.tileCursor+1
        if W.tileCursor>=1681 then W.tileCursor=0;W.playerCursor=W.playerCursor%#players+1 end
        local sq=cell:getGridSquare(x,y,0)
        local zone=sq and sq:getZone()
        if sq and zone and zone:getType()=="Nav" and sq:isFree(false) and #s.road_order<C.maxRoads then
            local key=x..":"..y
            if not s.roads[key] then
                local n={id=#s.road_order+1,position={x=x+0.5,y=y+0.5,z=0},key=key}
                s.roads[key]=n;s.road_order[#s.road_order+1]=key
                for _,d in ipairs({{4,0},{-4,0},{0,4},{0,-4}}) do
                    local other=s.roads[(x+d[1])..":"..(y+d[2])]
                    if other then
                        s.edges[#s.edges+1]={from=n.id,to=other.id,cost=4,blocked=false}
                        s.edges[#s.edges+1]={from=other.id,to=n.id,cost=4,blocked=false}
                    end
                end
            end
        end
    end
end
function W.valid(point,players,vehicle)
    local sq=getCell():getGridSquare(math.floor(point.x),math.floor(point.y),point.z or 0)
    if not sq or not sq:isOutside() or sq:getRoom() or not sq:isFree(false) or sq:getVehicleContainer() then return false end
    if sq:getMovingObjects():size()>0 then return false end
    if vehicle then local zone=sq:getZone();if not zone or zone:getType()~="Nav" then return false end end
    for _,p in ipairs(players) do
        if (p:getX()-point.x)^2+(p:getY()-point.y)^2<32*32 then return false end
    end
    return true
end
function W.candidate(s,r,players,vehicle)
    if #players==0 then return nil end
    if r.has_vehicle then
        local car=getVehicleById(tonumber(r.vehicle_id))
        if not car or car:getDriver() or math.abs(car:getCurrentSpeedKmHour())>1 then return nil end
        local position={x=car:getX()+2,y=car:getY(),z=math.floor(car:getZ())}
        if W.valid(position,players,false) then return position end
        return nil
    end
    -- Bounded candidates; all clients still veto visible points before spawn.
    if vehicle then
        for i=1,math.min(32,#s.road_order) do
            local n=s.roads[s.road_order[math.floor(M.random(s)*#s.road_order)+1]]
            if M.distance(n.position,r.position)<40 and W.valid(n.position,players,true) then return M.point(n.position) end
        end
    else
        for _=1,16 do
            local angle=M.random(s)*math.pi*2;local radius=4+M.random(s)*14
            local point={x=math.floor(r.position.x+math.cos(angle)*radius)+0.5,
                y=math.floor(r.position.y+math.sin(angle)*radius)+0.5,z=0}
            if W.valid(point,players,false) then return point end
        end
    end
end
function W.find(outfit,point)
    local cell=getCell()
    for dx=-1,1 do for dy=-1,1 do
        local sq=cell:getGridSquare(math.floor(point.x)+dx,math.floor(point.y)+dy,point.z or 0)
        if sq then local all=sq:getMovingObjects()
            for i=0,math.min(all:size(),32)-1 do
                local obj=all:get(i)
                if instanceof(obj,"IsoZombie") and (obj:getPersistentOutfitID()==outfit or
                    (BanditUtils and BanditUtils.GetCharacterID(obj)==outfit)) then return obj end
            end
        end
    end end
end
return W
