-- What this server can actually do: native bridge features, optional dependencies and
-- sidecar services. Modules check a capability before enabling, and refuse to enable
-- (with a visible reason) instead of silently running a degraded scenario.
local V = require("AKRCore/Version")
local C = {}

function C.new()
    return { entries = {} }
end

function C.set(caps, name, version, detail)
    if not V.parse(version) then
        return false, "invalid_version"
    end
    caps.entries[name] = { version = version, detail = detail }
    return true
end

function C.clear(caps, name)
    caps.entries[name] = nil
end

function C.has(caps, name, major, minimum)
    local e = caps.entries[name]
    if not e then
        return false, "missing"
    end
    return V.satisfies(e.version, major, minimum)
end

-- Server Java bridges publish capabilities() -> {name=version}. The pre-split bridge only
-- reports versionReady(); it is exposed as native.legacy 0.1.0 until it is replaced.
function C.readNative(caps, bridge)
    for name in pairs(caps.entries) do
        if string.sub(name, 1, 7) == "native." then
            caps.entries[name] = nil
        end
    end
    if type(bridge) ~= "table" then
        return 0
    end
    local count = 0
    if type(bridge.capabilities) == "function" then
        local ok, list = pcall(bridge.capabilities)
        if ok and type(list) == "table" then
            for name, version in pairs(list) do
                if type(name) == "string" and C.set(caps, "native." .. name, tostring(version)) then
                    count = count + 1
                end
            end
        end
    elseif type(bridge.versionReady) == "function" then
        local ok, ready = pcall(bridge.versionReady)
        if ok and ready == true then
            C.set(caps, "native.legacy", "0.1.0")
            count = 1
        end
    end
    return count
end

function C.list(caps)
    local out = {}
    for name, e in pairs(caps.entries) do
        out[#out + 1] = { name = name, version = e.version, detail = e.detail }
    end
    table.sort(out, function(a, b)
        return a.name < b.name
    end)
    return out
end

return C
