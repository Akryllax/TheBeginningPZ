-- Wires the real model to pool/navigation/Actor ports. No client or Observer owns decisions.
local P = require("AKRPopulation/Population")
local R = require("AKRResidents/Residents")
local C = {}
function C.new(root, epoch, ports)
    return {
        population = assert(P.open(root, epoch)),
        residents = assert(R.open(root)),
        ports = ports,
    }
end
function C.materialize(c, id)
    local r = assert(c.residents.residents[id], "unknown_resident")
    return P.reserve(c.population, c.ports.pool, r)
end
local function command(c, r, token, intent)
    local key = { token = P.copy(token), revision = intent.revision }
    if intent.kind == "stop" then
        c.ports.navigation.cancel(token)
        c.ports.actor.stop(key)
    elseif intent.kind == "navigate" then
        c.ports.navigation.request(key, P.copy(intent))
    elseif intent.kind == "follow" then
        c.ports.actor.follow(key, P.copy(intent.route), intent.mode)
    elseif intent.kind == "defend" then
        c.ports.navigation.cancel(token)
        c.ports.actor.defend(key, intent.target, intent.style, intent.targetGeneration)
    else
        error("unknown_intent")
    end
end
function C.tick(c, ids, now)
    assert(#ids <= 4, "actor_budget")
    c.ports.pool.tick()
    for _, id in ipairs(ids) do
        local r = assert(c.residents.residents[id], "unknown_resident")
        local binding = c.population.bindings[id]
        if
            binding
            and binding.token
            and binding.token.epoch == c.population.epoch
            and (binding.state == "ACTIVE" or binding.state == "RESERVED")
        then
            local token = binding.token
            local ok, why = pcall(function()
                local observed = c.ports.actor.observe(token)
                if observed.state == "ACTIVE" then
                    P.observe(c.population, token, "ACTIVE")
                    local result = c.ports.navigation.poll(token)
                    if result and P.equal(result.key.token, token) then
                        observed.path = {
                            revision = result.key.revision,
                            generation = token.residentGeneration,
                            status = result.status,
                            route = result.route,
                        }
                    end
                    for _, intent in ipairs(R.Model.tick(r, now, observed)) do
                        command(c, r, token, intent)
                    end
                elseif observed.state == "UNRESOLVED" then
                    P.observe(c.population, token, "UNRESOLVED", observed.reason)
                    r.state = "UNRESOLVED"
                end
            end)
            if not ok then
                -- A failed action might have run partially. Keep ownership and stop other commands for this resident.
                P.observe(c.population, token, "UNRESOLVED", tostring(why))
                r.state = "UNRESOLVED"
                pcall(c.ports.navigation.cancel, token)
                pcall(c.ports.actor.stop, { token = P.copy(token), revision = r.revision + 1 })
            end
        end
    end
    for _ = 1, 4 do
        local receipt = c.ports.pool.pollRetired()
        if not receipt then
            break
        end
        local r = c.residents.residents[receipt.token.resident]
        if r then
            local ok = P.retired(c.population, r, receipt.token, receipt.body)
            if ok then
                c.ports.pool.acknowledgeRetired(receipt.token)
            end
        end
    end
    if c.ports.pool.pollDead then
        for _ = 1, 4 do
            local receipt = c.ports.pool.pollDead()
            if not receipt then
                break
            end
            local r = c.residents.residents[receipt.token.resident]
            if not r or not P.deceased(c.population, r, receipt.token, receipt.corpseId) then
                break
            end
            if not c.ports.pool.acknowledgeDead(receipt.token, receipt.corpseId) then
                break
            end
        end
    end
end
function C.retire(c, id)
    local b = c.population.bindings[id]
    if not b or not b.token then
        return false, "not_materialized"
    end
    c.ports.navigation.cancel(b.token)
    local ok, why = c.ports.pool.retire(b.token)
    if ok then
        P.observe(c.population, b.token, "RETIRING")
    end
    return ok, why
end
return C
