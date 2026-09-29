-- Registration only. Live activation waits for the native traversal/replication experiment gate.
if isServer and isServer() then
    require("AKRResidents/Residents").install()
    -- Server-local functions consumed by the bounded native adapter. No activation/spawn here.
    AKRResidentModel = require("AKRResidents/Model")
    Events.OnInitGlobalModData.Add(function()
        AKRResidentState =
            assert(require("AKRResidents/Residents").open(ModData.getOrCreate("AKRCore")))
    end)
end

-- Narrow server-only lifecycle bridge. No engine references enter ModData.
if isServer and isServer() then
    local P = require("AKRPopulation/Population")
    AKRPopulationLifecycle = {}
    function AKRPopulationLifecycle.bind(resident, token)
        local state = assert(P.open(ModData.getOrCreate("AKRCore"), token.epoch))
        local old = state.bindings[resident.id]
        if old then
            return P.equal(old.token, token) and old.state == "ACTIVE"
        end
        local reserved = P.reserve(state, {
            reserve = function()
                return token
            end,
        }, resident)
        return reserved ~= nil and P.observe(state, token, "ACTIVE")
    end
    function AKRPopulationLifecycle.deceased(resident, token, corpseId, inventory)
        local root = ModData.getOrCreate("AKRCore")
        local state = assert(P.open(root, token.epoch))
        local ok, why = P.deceased(state, resident, token, corpseId)
        if ok then
            state.bindings[resident.id].inventory = inventory
        end
        return ok, why
    end
    function AKRPopulationLifecycle.reanimated(resident, token, corpseId, zombieId)
        local state = assert(P.open(ModData.getOrCreate("AKRCore"), token.epoch))
        return P.reanimated(state, resident, token, corpseId, zombieId)
    end
end
