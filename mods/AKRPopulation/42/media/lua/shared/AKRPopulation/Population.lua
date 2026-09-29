-- Persisted ownership ledger. The injected port owns native slots; this table contains no Java objects.
local K = require("AKRCore/Core")
local P = { version = "0.1.0" }
local function copy(value, depth)
    depth = depth or 0
    if depth > 20 then
        error("record_depth")
    end
    if type(value) == "table" then
        local out = {}
        for k, v in pairs(value) do
            if type(k) ~= "string" and type(k) ~= "number" then
                error("record_key")
            end
            out[k] = copy(v, depth + 1)
        end
        return out
    end
    if type(value) == "number" then
        assert(value == value and math.abs(value) < math.huge, "finite_record")
    end
    assert(
        value == nil
            or type(value) == "string"
            or type(value) == "number"
            or type(value) == "boolean",
        "plain_record"
    )
    return value
end
P.copy = copy
function P.open(root, epoch)
    local state, why = K.Store.open(root, "AKRPopulation", 1, nil, function()
        return { epoch = epoch, bindings = {} }
    end)
    if not state then
        return nil, why
    end
    if state.epoch ~= epoch then
        for _, b in pairs(state.bindings) do
            if b.state ~= "ABSTRACT" and b.state ~= "CORPSE" and b.state ~= "REANIMATED" then
                b.state = "UNRESOLVED"
                b.reason = "restart_reconciliation"
            end
        end
        state.epoch = epoch
    end
    return state
end
function P.reserve(state, port, resident)
    local b = state.bindings[resident.id]
    if b and b.state ~= "ABSTRACT" then
        return nil, "resident_owned"
    end
    local token, why =
        port.reserve(resident.id, resident.generation, resident.newBody, resident.body)
    if not token then
        return nil, why or "capacity"
    end
    state.bindings[resident.id] = { token = copy(token), state = "RESERVED" }
    return copy(token)
end
local function equal(a, b)
    if type(a) ~= type(b) then
        return false
    end
    if type(a) ~= "table" then
        return a == b
    end
    for k, v in pairs(a) do
        if not equal(v, b[k]) then
            return false
        end
    end
    for k in pairs(b) do
        if a[k] == nil then
            return false
        end
    end
    return true
end
P.equal = equal
function P.observe(state, token, status, reason)
    if token.epoch ~= state.epoch then
        return false, "stale_epoch"
    end
    local b = state.bindings[token.resident]
    if not b or not equal(b.token, token) or b.state == "ABSTRACT" then
        return false, "stale"
    end
    if status ~= "ACTIVE" and status ~= "RETIRING" and status ~= "UNRESOLVED" then
        return false, "status"
    end
    if status == "ACTIVE" and b.state ~= "RESERVED" and b.state ~= "ACTIVE" then
        return false, "transition"
    end
    b.state = status
    b.reason = reason
    return true
end
function P.retired(state, resident, token, body)
    if token.epoch ~= state.epoch then
        return false, "stale_epoch"
    end
    local b = state.bindings[resident.id]
    if not b or not equal(b.token, token) or b.state == "ABSTRACT" then
        return false, "stale"
    end
    if body == nil then
        b.state = "UNRESOLVED"
        b.reason = "missing_body_snapshot"
        return false, b.reason
    end
    -- Both records belong to the same ModData root; this is not a transaction with the world save.
    resident.body = copy(body)
    resident.newBody = false
    resident.generation = resident.generation + 1
    b.state = "ABSTRACT"
    b.token = nil
    b.reason = nil
    return true
end
function P.deceased(state, resident, token, corpseId)
    if type(corpseId) ~= "string" or #corpseId == 0 or #corpseId > 128 then
        return false, "corpse_id"
    end
    local b = state.bindings[resident.id]
    if not b or not equal(b.token, token) then
        return false, "stale"
    end
    if
        (b.state == "CORPSE" or b.state == "REANIMATED")
        and b.corpseId == corpseId
        and resident.life == "DEAD"
        and resident.state == "TERMINAL"
    then
        return true
    end
    if token.epoch ~= state.epoch or (b.state ~= "ACTIVE" and b.state ~= "UNRESOLVED") then
        return false, "transition"
    end
    resident.life = "DEAD"
    resident.state = "TERMINAL"
    resident.body = nil
    resident.newBody = false
    b.state = "CORPSE"
    b.corpseId = corpseId
    b.reason = nil
    return true
end
function P.reanimated(state, resident, token, corpseId, zombieId)
    local b = state.bindings[resident.id]
    if
        token.epoch ~= state.epoch
        or not b
        or not equal(b.token, token)
        or b.corpseId ~= corpseId
        or resident.life ~= "DEAD"
        or resident.state ~= "TERMINAL"
    then
        return false, "stale"
    end
    if b.state == "REANIMATED" then
        return b.zombieId == zombieId, "duplicate"
    end
    if b.state ~= "CORPSE" or type(zombieId) ~= "number" or zombieId < 0 then
        return false, "transition"
    end
    b.state = "REANIMATED"
    b.zombieId = zombieId
    return true
end
function P.install()
    local core = K.instance()
    K.Registry.provide(core.registry, "AKRPopulation", P.version, P)
    return P
end
return P
