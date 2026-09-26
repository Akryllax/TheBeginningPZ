require "ZomboidObserver/Shared"
if not isClient() then return end
local O=ZomboidObserver
local cache, trackedObjects, lastInspections = {},{},{}
local tiles, seen, inspections, coverage = {},{},{},{}
local queuedBytes, cacheCount, scanIndex = 0,0,0
local lastSend,lastMarkerPoll,lastInspectionPoll = 0,0,0
local lastPulse,lastCoverage=0,0
local markerDigest=nil
local warningAt=0
local retryAt=0
local radius=64
local scanCenter=nil

local function warn(message)
    if O.now()-warningAt>15000 then print("[ZomboidObserver] "..tostring(message));warningAt=O.now() end
end

local function property(sprite,name)
    local props=O.call(sprite,"getProperties")
    return O.call(props,"get",name) or O.call(props,"Val",name)
end

local function observeObject(object,square,index)
    local sprite=O.call(object,"getSprite")
    local name=O.text(O.call(sprite,"getName") or O.call(object,"getSpriteName") or "",160)
    local kind=O.classify(name,object)
    local label=property(sprite,"CustomName") or O.call(object,"getName") or kind
    local group=property(sprite,"GroupName")
    if group then label=tostring(group).." "..tostring(label) end
    if kind=="item" then
        local item=O.call(object,"getItem")
        label=O.call(item,"getDisplayName") or label
    end
    local id=O.elementId(object,square,index)
    local x,y,z=square:getX()+.5,square:getY()+.5,square:getZ()
    local orientation=O.orientation(object)
    if kind=="wall" or kind=="window" or kind=="door" or kind=="fence" then
        if orientation==90 then x=square:getX()+.05 else y=square:getY()+.05 end
    end
    local height=1
    if kind=="wall" or kind=="window" or kind=="door" or kind=="stairs" or kind=="roof" then height=2.8 end
    if kind=="tree" then height=3.6 end
    if kind=="fence" then height=1.2 end
    local result={id=id,kind=kind,label=O.text(label),sprite=name,x=x,y=y,z=z,
                  rotation=orientation,width=1,depth=1,height=height}
    local state={}
    local open=O.call(object,"IsOpen")
    if open==nil then open=O.call(object,"isOpen") end
    if type(open)=="boolean" then state.open=open end
    local broken=O.call(object,"isSmashed")
    if type(broken)=="boolean" then state.broken=broken end
    local material=property(sprite,"Material")
    if material then state.material=O.text(material,48) end
    if label then state.variant=O.text(label,80) end
    if next(state) then result.state=state end
    if kind=="floor" then
        if name:find("street") or name:find("asphalt") then result.color="#626c66"
        elseif name:find("water") then result.color="#5d9195"
        elseif name:find("interior") or name:find("wood") then result.color="#b9ad8d" end
    end
    trackedObjects[object]=id
    return result
end

local function vehicleSnapshot(vehicle)
    local net=O.call(vehicle,"getId")
    if not net or net<0 then return nil end
    local script=O.call(vehicle,"getScript")
    local extents=O.call(script,"getExtents")
    local width,depth,height=1.8,4.4,1.9
    if extents then
        width=tonumber(extents:x()) or width;depth=tonumber(extents:z()) or depth;height=tonumber(extents:y()) or height
    end
    local angle=O.call(vehicle,"getAngleY") or 0
    return {id="vehicle-net:"..net,kind="vehicle",label=O.text(O.call(script,"getName") or O.call(vehicle,"getScriptName") or "Vehicle"),
        sprite=O.text(O.call(vehicle,"getScriptName") or "",160),x=vehicle:getX(),y=vehicle:getY(),z=math.floor(vehicle:getZ()),
        rotation=math.deg(angle),width=math.max(.2,math.min(width,16)),depth=math.max(.2,math.min(depth,24)),height=math.max(.2,math.min(height,8))}
end

local function capture(square,player)
    if not square:isCanSee(player:getPlayerNum()) then return end
    local key=square:getX()..":"..square:getY()..":"..square:getZ()
    local record={x=square:getX(),y=square:getY(),z=square:getZ(),objects={}}
    local objects=square:getObjects()
    if objects:size()>120 then warn("tile too dense; retaining its previous observation");return end
    for i=0,objects:size()-1 do
        record.objects[#record.objects+1]=observeObject(objects:get(i),square,i)
    end
    local moving=square:getMovingObjects()
    for i=0,moving:size()-1 do
        local object=moving:get(i)
        if instanceof(object,"BaseVehicle") then
            local vehicle=vehicleSnapshot(object)
            if vehicle and math.floor(vehicle.x)==record.x and math.floor(vehicle.y)==record.y and vehicle.z==record.z then
                record.objects[#record.objects+1]=vehicle
            end
        elseif instanceof(object,"IsoZombie") or instanceof(object,"IsoAnimal") then
            local net=O.call(object,"getOnlineID")
            local alpha=O.call(object,"getAlpha",player:getPlayerNum())
            if net and net>=0 and (alpha==nil or alpha>.05) then
                local kind=instanceof(object,"IsoZombie") and "zombie" or "animal"
                record.objects[#record.objects+1]={id="actor:"..kind..":"..net,kind=kind,label=kind,
                    x=object:getX(),y=object:getY(),z=math.floor(object:getZ()),width=.6,depth=.6,height=kind=="zombie" and 1.7 or 1}
            end
        end
    end
    if #record.objects>128 then return end
    local serialized=O.json(record)
    local previous=cache[key]
    local now=O.now()
    if not previous or previous.value~=serialized then
        if #serialized>O.maxPacket-2000 then warn("tile exceeds packet budget; skipped");return end
        if queuedBytes+#serialized>O.maxPacket-3000 or #tiles>=48 then return end
        tiles[#tiles+1]=record;queuedBytes=queuedBytes+#serialized
        if not previous then cacheCount=cacheCount+1 end
        cache[key]={value=serialized,at=now}
    elseif now-previous.at>=4000 and #seen<160 then
        seen[#seen+1]={x=record.x,y=record.y,z=record.z};previous.at=now
    end
end

local function flush(player)
    if #tiles==0 and #seen==0 and #inspections==0 and #coverage==0 then return end
    local args={tiles=tiles,seen=seen,inspections=inspections,coverage=coverage}
    if #O.json(args)>O.maxPacket then
        -- Preserve tile completeness; never send a truncated tile as if objects disappeared.
        warn("observation batch over budget; requesting fresh capture")
        cache={};cacheCount=0
    else sendClientCommand(player,O.module,"observe",args) end
    tiles={};seen={};inspections={};coverage={};queuedBytes=0
end

local function containerId(container)
    local parent=O.call(container,"getParent")
    if not parent then return nil end
    if instanceof(parent,"IsoGameCharacter") or instanceof(parent,"InventoryItem") then return nil end
    if instanceof(parent,"BaseVehicle") then
        local net=O.call(parent,"getId")
        if net then return "vehicle-net:"..net end
    end
    return trackedObjects[parent]
end

local function inspectContainers(player)
    local page=getPlayerLoot(player:getPlayerNum())
    if not page or not page:isVisible() or not page.inventoryPane then return end
    local pane=page.inventoryPane
    if not pane:isVisible() then return end
    local container=pane.inventory
    if not container or O.call(container,"getType")=="floor" then return end
    local id=containerId(container)
    if not id then return end
    -- Only the selected root container: do not enumerate its children or nearby containers.
    local items=container:getItems()
    if items:size()>1500 then warn("selected container too large to record safely");return end
    local part=O.call(container,"getVehiclePart")
    local slot=O.text(O.call(part,"getId") or O.call(container,"getType") or "main")
    local record={id=id,category="container",slot=slot,items={}}
    local counts={}
    for i=0,items:size()-1 do
        local item=items:get(i)
        local fullType=O.text(item:getFullType())
        local condition=O.call(item,"getCondition")
        local key=fullType..":"..tostring(condition)
        if not counts[key] then
            counts[key]={type=fullType,name=O.text(item:getDisplayName()),count=0,condition=condition}
            record.items[#record.items+1]=counts[key]
        end
        counts[key].count=counts[key].count+math.max(1,O.call(item,"getCount") or 1)
    end
    local encoded=O.json(record)
    if #encoded>12000 then warn("selected container inspection exceeds export budget");return end
    local key=id..":"..slot
    local previous=lastInspections[key]
    if (not previous or previous.value~=encoded or O.now()-previous.at>5000) and #inspections<3 then
        inspections[#inspections+1]=record;lastInspections[key]={value=encoded,at=O.now()}
    end
end

local function inspectMechanics(player)
    local ui=getPlayerMechanicsUI and getPlayerMechanicsUI(player:getPlayerNum())
    if not ui or not ui:isVisible() or not ui.vehicle then return end
    local vehicle=ui.vehicle
    if math.abs(vehicle:getX()-player:getX())>8 or math.abs(vehicle:getY()-player:getY())>8 then return end
    local record={id="vehicle-net:"..vehicle:getId(),category="mechanics",parts={}}
    for i=0,vehicle:getPartCount()-1 do
        local part=vehicle:getPartByIndex(i)
        -- Match the mechanics UI: unavailable/uninstalled components have no observed condition.
        if part:getInventoryItem() then record.parts[#record.parts+1]={name=O.text(part:getId()),condition=part:getCondition()} end
    end
    local encoded=O.json(record)
    local previous=lastInspections[record.id..":mechanics"]
    if (not previous or previous.value~=encoded or O.now()-previous.at>5000) and #inspections<3 then
        inspections[#inspections+1]=record;lastInspections[record.id..":mechanics"]={value=encoded,at=O.now()}
    end
end

local function publicMarkers(player)
    local map=ISWorldMap_instance
    if not map or not map.mapAPI then return end
    local api=map.mapAPI:getSymbolsAPIv2()
    local records={}
    for i=0,api:getSymbolCount()-1 do
        local symbol=api:getSymbolByIndex(i)
        if symbol:isShared() and symbol:isVisibleToEveryone() and symbol:getAuthor()==player:getUsername() then
            local label=O.call(symbol,"getUntranslatedText") or O.call(symbol,"getSymbolID") or "Marker"
            records[#records+1]={id="live-marker:"..player:getUsername()..":"..i,author=player:getUsername(),label=O.text(label),
                x=symbol:getWorldX(),y=symbol:getWorldY(),z=0,public=true}
        end
    end
    if #records>128 then return end
    local digest=O.json(records)
    if digest~=markerDigest then sendClientCommand(player,O.module,"markers",{markers=records});markerDigest=digest end
end

local function tick()
    local player=getPlayer()
    if not player then return end
    local start=O.now()
    if start-lastPulse>=1000 then sendClientCommand(player,O.module,"pulse",{});lastPulse=start end
    if player:isDead() or player:isAsleep() then return end
    if start-lastSend>=100 then flush(player);lastSend=start end
    if start-lastInspectionPoll>=1000 then inspectContainers(player);inspectMechanics(player);lastInspectionPoll=start end
    if start-lastMarkerPoll>=2000 then publicMarkers(player);lastMarkerPoll=start end
    if start-lastCoverage>=2000 and WorldMapVisited then
        local visited=WorldMapVisited.getInstance()
        local bx,by=math.floor(player:getX()/32)*32,math.floor(player:getY()/32)*32
        for dx=-64,64,32 do for dy=-64,64,32 do
            local x,y=bx+dx,by+dy
            local flags=(visited:isVisited(x+16,y+16) and 1 or 0)+(visited:isKnown(x+16,y+16) and 2 or 0)
            if flags>0 then coverage[#coverage+1]={x=x,y=y,flags=flags} end
        end end
        lastCoverage=start
    end
    if cacheCount>24000 then cache={};trackedObjects={};cacheCount=0 end
    local size=radius*2+1
    if not scanCenter or scanIndex==0 or math.abs(scanCenter.x-player:getX())>8 or math.abs(scanCenter.y-player:getY())>8 or scanCenter.z~=math.floor(player:getZ()) then
        scanCenter={x=math.floor(player:getX()),y=math.floor(player:getY()),z=math.floor(player:getZ())};scanIndex=0
    end
    -- Limit BOTH elapsed time and work count. A full pass covers the local visibility
    -- window; only isCanSee squares are recorded, never all loaded squares.
    for _=1,256 do
        if O.now()-start>=2 or queuedBytes>O.maxPacket-6000 then break end
        local dx=scanIndex%size-radius
        local dy=math.floor(scanIndex/size)-radius
        scanIndex=(scanIndex+1)%(size*size)
        local square=getCell():getGridSquare(scanCenter.x+dx,scanCenter.y+dy,scanCenter.z)
        if square then capture(square,player) end
    end
end

Events.OnTick.Add(function()
    if O.now()<retryAt then return end
    local ok,err=pcall(tick)
    if not ok then retryAt=O.now()+15000;warn(err) end
end)
Events.OnGameStart.Add(function()
    cache={};trackedObjects={};lastInspections={};cacheCount=0;scanIndex=0;scanCenter=nil;markerDigest=nil
    print("[ZomboidObserver] observation capture active; selected world-container contents and online positions are public")
end)
