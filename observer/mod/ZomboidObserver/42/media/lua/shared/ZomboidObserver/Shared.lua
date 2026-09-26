ZomboidObserver = ZomboidObserver or {}
local O = ZomboidObserver
O.version = 1
O.module = "ZomboidObserver"
O.world = "AKR_Exploratory"
O.maxPacket = 32000
O.maxLine = 262144

-- An explicit JSON encoder keeps the journal independent of optional JSON mods.
local function quote(value)
    return '"' .. tostring(value):gsub('[%z\1-\31\\"]', function(c)
        local escapes = {['"']='\\"', ['\\']='\\\\', ['\n']='\\n', ['\r']='\\r', ['\t']='\\t'}
        return escapes[c] or string.format('\\u%04x', string.byte(c))
    end) .. '"'
end

function O.json(value, depth)
    depth = depth or 0
    if depth > 14 then error("observation nesting limit") end
    local t = type(value)
    if value == nil then return "null" end
    if t == "boolean" then return value and "true" or "false" end
    if t == "string" then return quote(value) end
    if t == "number" then
        if value ~= value or value == math.huge or value == -math.huge then error("nonfinite observation") end
        return tostring(value)
    end
    if t ~= "table" then error("unsupported observation value") end
    local array = true
    for key in pairs(value) do
        if type(key) ~= "number" then array = false; break end
    end
    local out = {}
    if array then
        for i=1,#value do out[#out+1] = O.json(value[i], depth+1) end
        return "[" .. table.concat(out,",") .. "]"
    end
    local keys = {}
    for key in pairs(value) do keys[#keys+1] = key end
    table.sort(keys)
    for _,key in ipairs(keys) do out[#out+1] = quote(key) .. ':' .. O.json(value[key],depth+1) end
    return "{" .. table.concat(out,",") .. "}"
end

function O.call(object, method, ...)
    if not object then return nil end
    local args = {...}
    local ok,result = pcall(function()
        local fn = object[method]
        if not fn then return nil end
        return fn(object, unpack(args))
    end)
    if ok then return result end
    return nil
end

function O.now() return getTimestampMs() end
function O.text(value, maximum)
    if value == nil then return nil end
    local text = tostring(value)
    if #text > (maximum or 128) then text = string.sub(text,1,maximum or 128) end
    return text
end

function O.elementId(object, square, index)
    local x,y,z = square:getX(),square:getY(),square:getZ()
    -- Tile-local slots survive ordinary state updates; complete tile observations
    -- replace them after construction/removal. No IDs are written into game objects.
    return string.format("tile:%d:%d:%d:%d",x,y,z,index)
end

function O.classify(sprite, object)
    local name = string.lower(sprite or "")
    if instanceof(object,"IsoDoor") then return "door" end
    if instanceof(object,"IsoWindow") then return "window" end
    if instanceof(object,"IsoTree") then return "tree" end
    if instanceof(object,"IsoWorldInventoryObject") then return "item" end
    if O.call(object,"isDoor") then return "door" end
    if O.call(object,"getContainer") then return "container" end
    if name:find("roof") then return "roof" end
    if name:find("stairs") then return "stairs" end
    if name:find("fenc") or name:find("railing") then return "fence" end
    if name:find("wall") then return "wall" end
    if name:find("floor") or name:find("street") or name:find("blends") or name:find("water") then return "floor" end
    if name:find("vegetation") or name:find("grass") or name:find("bush") then return "vegetation" end
    if name:find("furniture") or name:find("fixtures") or name:find("appliances") then return "furniture" end
    return "unknown"
end

function O.orientation(object)
    local north = O.call(object,"getNorth")
    if north ~= nil then return north and 0 or 90 end
    local sprite = O.call(object,"getSprite")
    local props = O.call(sprite,"getProperties")
    local facing = O.call(props,"get","Facing") or O.call(props,"Val","Facing")
    if facing == "W" or facing == "E" then return 90 end
    return 0
end
