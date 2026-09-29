-- Durable logical goals. Engine routes, native references and wall-clock deadlines never persist here.
local P = require("AKRPopulation/Population")
local G = {}
function G.ensure(r)
    if not r.goalPlan then
        r.goalPlan = {
            goal = "civilian_routine",
            revision = 0,
            facts = 1,
            phase = 0,
            cursor = 1,
            actions = {},
            status = "needs_plan",
            home = P.copy(r.position),
            activity = P.copy(r.anchors[1]),
            waitRemaining = 2,
            source = "none",
        }
    end
    return r.goalPlan
end
function G.current(r)
    local g = G.ensure(r)
    return g.actions[g.cursor]
end
function G.accept(r, p)
    if r.life == "DEAD" or r.state == "TERMINAL" then
        return false, "terminal_resident"
    end
    local g = G.ensure(r)
    if
        p.resident_id ~= r.id
        or p.generation ~= r.generation
        or p.based_on_revision ~= g.facts
        or p.plan_revision <= g.revision
    then
        return false, "stale_plan"
    end
    if G.current(r) or g.phase >= 3 then
        return false, "committed_action"
    end
    if
        p.goal ~= "civilian_routine"
        or type(p.actions) ~= "table"
        or #p.actions < 1
        or #p.actions > 6
    then
        return false, "unsupported_plan"
    end
    local expected = ({
        [0] = { "WALK", "WAIT", "WALK" },
        [1] = { "WAIT", "WALK" },
        [2] = { "WALK" },
    })[g.phase]
    if #p.actions ~= #expected then
        return false, "routine_shape"
    end
    for i, a in ipairs(p.actions) do
        local kind = type(a.kind) == "number" and ({ [1] = "WAIT", [2] = "WALK" })[a.kind] or a.kind
        local target = expected[i] == "WAIT" and g.activity
            or (i == #expected and g.home or g.activity)
        if
            kind ~= expected[i]
            or type(a.id) ~= "string"
            or #a.id > 160
            or not a.target
            or a.target.x ~= target.x
            or a.target.y ~= target.y
            or a.target.z ~= target.z
        then
            return false, "routine_action"
        end
    end
    g.actions = P.copy(p.actions)
    for _, a in ipairs(g.actions) do
        if type(a.kind) == "number" then
            a.kind = ({ [1] = "WAIT", [2] = "WALK" })[a.kind]
        end
    end
    g.cursor = 1
    g.revision = p.plan_revision
    g.source = "worker"
    g.status = "active"
    return true
end
function G.fallback(r)
    if r.life == "DEAD" or r.state == "TERMINAL" then
        return
    end
    local g = G.ensure(r)
    if G.current(r) or g.phase >= 3 then
        return
    end
    g.revision = g.revision + 1
    g.actions = {}
    g.cursor = 1
    local function add(kind, target)
        g.actions[#g.actions + 1] = {
            id = r.id .. ":local:" .. g.revision .. ":" .. #g.actions,
            kind = kind,
            target = P.copy(target),
            locomotion = kind == "WALK" and "WALK" or "IDLE",
        }
    end
    if g.phase == 0 then
        add("WALK", g.activity)
    end
    if g.phase <= 1 then
        add("WAIT", g.activity)
    end
    add("WALK", g.home)
    g.status = "active"
    g.source = "fallback"
end
function G.complete(r, id)
    if r.life == "DEAD" or r.state == "TERMINAL" then
        return false
    end
    local g = G.ensure(r)
    local a = G.current(r)
    if not a or a.id ~= id then
        return false
    end
    G.outcome(r, a, "completed", "destination_or_wait_complete")
    g.phase = g.phase + 1
    g.cursor = g.cursor + 1
    g.facts = g.facts + 1
    g.status = g.phase >= 3 and "complete" or "active"
    return true
end
function G.outcome(r, a, state, reason)
    local g = G.ensure(r)
    g.outcomes = g.outcomes or {}
    assert(#g.outcomes < 6, "outcome_delivery_required")
    g.outcomes[#g.outcomes + 1] = {
        action = a.id,
        generation = r.generation,
        planRevision = g.revision,
        state = state,
        reason = reason,
    }
end
function G.cancelCurrent(r, state, reason)
    local g = G.ensure(r)
    local a = G.current(r)
    if a then
        G.outcome(r, a, state or "cancelled", reason or "execution_disposed")
    end
    g.actions = {}
    g.cursor = 1
    g.facts = g.facts + 1
    if g.phase < 3 then
        g.status = "needs_plan"
    end
    return r
end
function G.suspend(r, reason)
    local g = G.ensure(r)
    if g.status ~= "complete" then
        g.status = "suspended"
        g.reason = reason
    end
end
function G.resume(r)
    local g = G.ensure(r)
    if g.status == "suspended" then
        g.status = "active"
        g.reason = nil
    end
end
function G.detach(r)
    G.suspend(r, "dematerialized")
    r.moving = false
    r.requestPending = false
    r.plannedThreats = nil
    r.threats = nil
    r.nextDecision = 0
    r.nextRepath = 0
    r.lastNow = nil
    r.lastKnown = nil
    r.nextDefense = 0
    r.combatDeadline = 0
    r.pausedAt = nil
    r.requestAt = nil
    r.state = (r.life == "DEAD" or r.state == "TERMINAL") and "TERMINAL" or "IDLE"
    r.revision = r.revision + 1
    r.lastThreat = -1000000
    r.planWaitAt = nil
    r.planRequestedAt = nil
    r.deadline = 0
end
return G
