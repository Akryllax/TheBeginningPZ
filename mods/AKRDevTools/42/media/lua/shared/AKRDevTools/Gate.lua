-- Requiring an auto-loaded shared file must reuse its captured callback registry.
if AKRPedestrianGate then
    return AKRPedestrianGate
end
-- Shared scripts load before client scripts. Capture private upstream closures
-- at registration without copying their source or changing installed files.
local C = {
    maxPhysical = 4,
    enabled = function()
        return true
    end,
}
local G =
    { callbacks = {}, wrappers = {}, members = {}, depth = 0, error = nil, shield_calls = 0, shield_ms = 0 }
AKRPedestrianGate = G
local expected =
    { OnZombieUpdate = 2062, OnHitZombie = 2328, OnZombieDead = 2395, OnDeadBodySpawn = 2601 }
G.manifest = "Bandits42.20:Update@2062,Hit@2328,Dead@2395,Body@2601:shield2"
local function outfit(z)
    if not z or not instanceof(z, "IsoZombie") then
        return nil
    end
    if BanditUtils and BanditUtils.GetZombieID then
        return BanditUtils.GetZombieID(z)
    end
    return z:getPersistentOutfitID()
end
function G.managed(z)
    if not z or not (instanceof(z, "IsoZombie") or instanceof(z, "IsoDeadBody")) then
        return false
    end
    local id = outfit(z)
    local md = z:getModData()
    if md.AKRPedestrian then
        if id then
            G.members[id] = true
        end
        return true
    end
    if not id then
        return false
    end
    local cluster = GetBanditClusterData and GetBanditClusterData(id)
    local brain = cluster and cluster[id]
    if brain and brain.cid == "akr-pedestrian-experiment-v1" then
        G.members[id] = true
        return true
    end
    local client = AKRPedestrianClient
    if client and client.outfits[id] then
        G.members[id] = true
        return true
    end
    return false
end
function G.status()
    if G.error then
        return G.error
    end
    for event in pairs(expected) do
        if not G.callbacks[event] then
            return "missing:" .. event
        end
    end
    return "ready"
end
function G.ready()
    if G.error then
        return false
    end
    for event in pairs(expected) do
        if not G.callbacks[event] then
            return false
        end
    end
    return true
end
local function prune()
    local count = 0
    for id in pairs(G.members) do
        local cluster = GetBanditClusterData and GetBanditClusterData(id)
        local brain = cluster and cluster[id]
        local client = AKRPedestrianClient
        if
            not (brain and brain.cid == "akr-pedestrian-experiment-v1")
            and not (client and client.outfits[id])
        then
            G.members[id] = nil
        else
            count = count + 1
        end
    end
    if count > C.maxPhysical + 8 then
        G.error = "managed_identity_capacity"
    end
end
local function shield(fn, ...)
    -- Sparse, scoped removal of at most 40 identities. Never clone or scan the
    -- complete zombie cache per callback. Nested calls share the outer shield.
    if G.depth > 0 then
        return fn(...)
    end
    G.depth = 1
    local hidden = {}
    local b = BanditZombie
    if b then
        for _, name in ipairs({ "CacheLightB", "CacheLight", "CacheLightZ" }) do
            local cache = b[name]
            if cache then
                local entries = {}
                for id in pairs(G.members) do
                    if cache[id] ~= nil then
                        entries[id] = cache[id]
                        cache[id] = nil
                    end
                end
                hidden[#hidden + 1] = { cache = cache, entries = entries }
            end
        end
    end
    local before = getTimestampMs()
    local ok, a, b, c = pcall(fn, ...)
    for _, h in ipairs(hidden) do
        for id, value in pairs(h.entries) do
            h.cache[id] = value
        end
    end
    G.depth = 0
    G.shield_calls = G.shield_calls + 1
    G.shield_ms = G.shield_ms + (getTimestampMs() - before)
    if not ok then
        error(a)
    end
    return a, b, c
end
G.shield = shield
if not isServer() then
    for event, line in pairs(expected) do
        local eventName, firstLine = event, line
        local slot = Events[eventName]
        local add, remove = slot.Add, slot.Remove
        slot.Add = function(fn)
            local ok, filename = pcall(getFilenameOfClosure, fn)
            local source = ok and type(filename) == "string" and filename:gsub("\\", "/") or ""
            if source ~= "BanditUpdate.lua" and not source:match("/BanditUpdate%.lua$") then
                return add(fn)
            end
            local lineOk, actual = pcall(getFirstLineOfClosure, fn)
            if not lineOk or actual ~= firstLine then
                G.error = "callback_manifest_mismatch:" .. eventName
            end
            local wrapped = G.wrappers[fn]
            if not wrapped then
                wrapped = function(actor, ...)
                    if not C.enabled() then
                        return fn(actor, ...)
                    end
                    if G.managed(actor) then
                        return
                    end
                    if eventName == "OnZombieUpdate" then
                        local client = AKRPedestrianClient
                        if client then
                            for id in pairs(client.outfits) do
                                G.members[id] = true
                            end
                        end
                        if getTimestampMs() - (G.lastPrune or 0) > 1000 then
                            prune()
                            G.lastPrune = getTimestampMs()
                        end
                        return shield(fn, actor, ...)
                    end
                    return fn(actor, ...)
                end
                G.wrappers[fn] = wrapped
            end
            if G.callbacks[eventName] and G.callbacks[eventName] ~= wrapped then
                remove(G.callbacks[eventName])
            end
            G.callbacks[eventName] = wrapped
            return add(wrapped)
        end
        slot.Remove = function(fn)
            local wrapped = G.wrappers[fn]
            if wrapped and G.callbacks[eventName] == wrapped then
                G.callbacks[eventName] = nil
            end
            return remove(wrapped or fn)
        end
    end
end
return G
