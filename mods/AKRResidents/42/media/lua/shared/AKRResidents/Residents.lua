local K = require("AKRCore/Core")
local P = require("AKRPopulation/Population")
local Model = require("AKRResidents/Model")
local R = { version = "0.1.0", Model = Model }
function R.open(root)
    return K.Store.open(root, "AKRResidents", 1, nil, function()
        return { residents = {} }
    end)
end
function R.add(state, id, seed, position, anchors)
    if state.residents[id] then
        return nil, "resident_exists"
    end
    local r = Model.new(id, seed, position, anchors)
    state.residents[id] = r
    return r
end
-- The caller supplies no more than four admitted residents. No town-wide update loop.
function R.tick(state, ids, now, ports)
    assert(#ids <= 4, "physical_budget")
    local outputs = {}
    for _, id in ipairs(ids) do
        local resident = assert(state.residents[id], "unknown_resident")
        local observation = ports.observe(id)
        local intents = Model.tick(resident, now, observation)
        for _, intent in ipairs(intents) do
            ports.intent(P.copy(intent))
        end
        outputs[id] = intents
    end
    return outputs
end
function R.install()
    P.install()
    local core = K.instance()
    K.Registry.provide(core.registry, "AKRResidents", R.version, R)
    return R
end
return R
