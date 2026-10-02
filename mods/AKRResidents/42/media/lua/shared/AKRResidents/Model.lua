-- Pure Lua 5.1 domain logic. Inputs are detached, server observations; outputs are intents.
local P = require("AKRPopulation/Population")
local G = require("AKRResidents/Plan")
local M = { version = "0.2.0" }
local function random(r, n)
    r.random = (r.random * 48271) % 2147483647
    return (r.random % n) + 1
end
local function point(p)
    assert(type(p) == "table", "position")
    for _, k in ipairs({ "x", "y", "z" }) do
        assert(type(p[k]) == "number" and p[k] == p[k] and math.abs(p[k]) < math.huge, "position")
    end
    return { x = p.x, y = p.y, z = p.z }
end
local function distance(a, b)
    return math.sqrt((a.x - b.x) ^ 2 + (a.y - b.y) ^ 2 + (a.z - b.z) ^ 2)
end
function M.new(id, seed, position, anchors)
    assert(
        type(id) == "string" and #id > 0 and type(seed) == "number" and seed == math.floor(seed),
        "identity"
    )
    assert(#anchors > 0 and #anchors <= 16, "anchor_limit")
    local points = {}
    for i, p in ipairs(anchors) do
        points[i] = point(p)
    end
    return {
        id = id,
        generation = 1,
        newBody = true,
        position = point(position),
        anchors = points,
        random = (seed % 2147483646) + 1,
        state = "IDLE",
        revision = 0,
        nextDecision = 0,
        deadline = 0,
        nextRepath = 0,
        lastThreat = -1000000,
        requestPending = false,
        moving = false,
        bodyRevision = 0,
        life = "ALIVE",
        combatDeadline = 0,
        nextDefense = 0,
    }
end
local function emit(r, out, kind, extra)
    local command = extra or {}
    command.kind = kind
    command.resident = r.id
    command.generation = r.generation
    command.revision = r.revision
    out[#out + 1] = command
end
local function interrupt(r, out, state, policy)
    r.revision = r.revision + 1
    r.state = state
    r.requestPending = false
    r.moving = false
    emit(r, out, "stop", { cancellation = policy or "INTERRUPT_NOW" })
end
local function navigate(r, out, now, escape)
    r.revision = r.revision + 1
    r.requestPending = true
    r.requestAt = now
    r.pathProgressAt = now
    r.pathProgress = 0
    r.nextRepath = now + 1
    if escape then
        -- Geometry and danger-aware reachability are the NavigationPort's responsibility.
        emit(r, out, "navigate", {
            mode = "flee",
            origin = P.copy(r.position),
            threats = P.copy(r.threats or {}),
            radius = 16,
            allowClimb = true,
        })
        r.plannedThreats = P.copy(r.threats or {})
    else
        local action = r.planEnabled and G.current(r)
        local target = action and action.target or r.anchors[random(r, #r.anchors)]
        emit(r, out, "navigate", {
            mode = "roam",
            origin = P.copy(r.position),
            target = P.copy(target),
            allowClimb = false,
        })
    end
end
local function dangerMoved(r)
    local previous = r.plannedThreats or {}
    if #previous ~= #(r.threats or {}) then
        return true
    end
    for i, t in ipairs(r.threats or {}) do
        if t.id ~= previous[i].id or distance(t.position, previous[i].position) >= 2 then
            return true
        end
    end
    return false
end
function M.tick(r, now, observation)
    assert(type(now) == "number" and now == now and math.abs(now) < math.huge, "clock")
    local out = {}
    local o = observation
    if r.lastNow and now < r.lastNow then
        return out
    end
    r.lastNow = now
    if not o.online then
        r.pausedAt = r.pausedAt or now
        return out
    end
    if r.pausedAt then
        local shift = now - r.pausedAt
        for _, k in ipairs({
            "deadline",
            "nextDecision",
            "nextRepath",
            "lastThreat",
            "requestAt",
            "pathProgressAt",
            "combatDeadline",
            "nextDefense",
            "planWaitAt",
        }) do
            if r[k] then
                r[k] = r[k] + shift
            end
        end
        r.pausedAt = nil
    end
    if r.state == "TERMINAL" or r.state == "UNRESOLVED" then
        return out
    end
    if o.reacting then
        r.planWaitAt = nil
        if r.planEnabled then
            G.suspend(r, "reaction")
        end
        return out
    end
    if o.dead then
        interrupt(r, out, "TERMINAL")
        r.life = "DEAD"
        if r.planEnabled then
            G.cancelCurrent(r, "failed", "resident_dead")
        end
        return out
    end
    if o.uncertain then
        interrupt(r, out, "UNRESOLVED")
        return out
    end
    if now < r.nextDecision then
        return out
    end
    r.nextDecision = now + 0.2
    if not o.known then
        -- Keep only already committed, locally collision-checked movement during a
        -- bounded rolling perception sweep. Unknown never authorizes a new action.
        if r.moving and o.routeSafe and r.lastKnown and now - r.lastKnown <= 3 then
            return out
        end
        if r.state ~= "BLOCKED" then
            r.resumeState = r.state
            interrupt(r, out, "BLOCKED")
        end
        r.reason = "observation_missing"
        return out
    end
    r.lastKnown = now
    r.position = point(o.position)
    local threats = {}
    -- The observation producer must use a rotating local-square cursor; this is a bounded frame.
    assert(#(o.threats or {}) <= 32, "threat_observation_budget")
    for _, t in ipairs(o.threats or {}) do
        if t.visible and t.zombie and not t.dead and distance(r.position, t.position) <= 12 then
            threats[#threats + 1] =
                { id = t.id, generation = t.generation, position = point(t.position) }
        end
    end
    table.sort(threats, function(a, b)
        return tostring(a.id) < tostring(b.id)
    end)
    if #threats > 0 then
        r.lastThreat = now
        r.threats = threats
        if r.planEnabled then
            G.suspend(r, "danger")
        end
        if
            r.state ~= "FLEE"
            and r.state ~= "DEFEND"
            and not (r.state == "BLOCKED" and r.resumeState == "FLEE")
        then
            interrupt(r, out, "FLEE", o.routeSafe and "DEFER_TO_BOUNDARY" or "INTERRUPT_NOW")
            r.nextRepath = now
        end
    elseif r.state == "FLEE" and now - r.lastThreat >= 3 then
        interrupt(r, out, "RECOVER", o.routeSafe and "DEFER_TO_BOUNDARY" or "INTERRUPT_NOW")
        r.deadline = now + 3
    end
    local reply = o.path
    if
        reply
        and reply.revision == r.revision
        and reply.generation == r.generation
        and r.requestPending
    then
        r.requestPending = false
        if reply.status == "found" then
            r.moving = true
            r.followRevision = reply.revision
            emit(r, out, "follow", {
                route = P.copy(reply.route),
                mode = r.state,
                gait = r.state == "FLEE" and "RUN" or "WALK",
            })
        elseif r.moving and o.routeSafe == true then
            r.nextRepath = now + 0.5
        else
            r.resumeState = r.state
            interrupt(r, out, "BLOCKED")
            r.reason = reply.status
            r.nextRepath = now + 1
        end
    end
    if
        r.requestPending
        and type(o.pathProgress) == "number"
        and o.pathProgress > (r.pathProgress or 0)
    then
        r.pathProgress = o.pathProgress
        r.pathProgressAt = now
    end
    if
        r.requestPending
        and (now - (r.pathProgressAt or r.requestAt) >= 5 or now - r.requestAt >= 20)
    then
        r.resumeState = r.state
        interrupt(r, out, "BLOCKED")
        r.reason = "path_timeout"
        r.nextRepath = now + 1
    end
    if o.blocked and (r.moving or r.requestPending) then
        r.resumeState = r.state
        interrupt(r, out, "BLOCKED")
        r.nextRepath = now + 1
        r.reason = "changed_obstacle"
    end
    -- The server's immediately available observations take precedence over worker latency.
    -- A path still being solved is not evidence that escape has failed.
    local combat = o.combat
    local target = nil
    local nearest = math.huge
    if combat and combat.known and #threats > 0 then
        for _, t in ipairs(threats) do
            local d = distance(r.position, t.position)
            if
                d < nearest or (d == nearest and target and tostring(t.id) < tostring(target.id))
            then
                target = t
                nearest = d
            end
        end
    end
    if r.state == "DEFEND" then
        if combat and combat.attacking and not combat.knockedDown then
            return out
        end
        if
            not target
            or combat.escapeReachable
            or combat.knockedDown
            or now >= r.combatDeadline
        then
            interrupt(r, out, "FLEE")
            r.nextRepath = now
        else
            return out
        end
    end
    if
        target
        and nearest <= 2
        and now >= r.nextDefense
        and combat.escapeAssessed == true
        and not combat.escapeReachable
        and not combat.pathPending
        and not combat.knockedDown
        and not combat.attacking
        and r.state ~= "DEFEND"
    then
        interrupt(r, out, "DEFEND")
        r.combatDeadline = now + 0.8
        r.nextDefense = now + 1.5
        emit(r, out, "defend", {
            target = tostring(target.id),
            targetGeneration = target.generation,
            style = combat.weaponUsable
                    and combat.endurance
                    and combat.endurance >= 0.2
                    and "melee"
                or "shove",
        })
        return out
    end
    if (o.arrivedRevision ~= nil and o.arrivedRevision == r.followRevision) and r.moving then
        r.moving = false
        if r.state == "WALK" then
            if o.goalReached == false then
                r.state = "WALK"
                navigate(r, out, now, false)
            else
                if r.planEnabled then
                    local a = G.current(r)
                    if a then
                        G.complete(r, a.id)
                    end
                end
                r.state = "IDLE"
                r.deadline = r.planEnabled and now or now + 2 + random(r, 4)
            end
        end
    end
    if r.state == "BLOCKED" and now >= r.nextRepath then
        r.state = (now - r.lastThreat < 3) and "FLEE" or "IDLE"
        r.deadline = now
        r.reason = nil
    end
    if r.state == "RECOVER" and now >= r.deadline then
        r.state = "IDLE"
        r.deadline = now + 2
    end
    if r.planEnabled and (r.state == "IDLE" or r.state == "WALK") and #threats == 0 then
        G.resume(r)
        local a = G.current(r)
        if not a then
            if G.ensure(r).phase >= 3 then
                return out
            end
            r.planRequestedAt = r.planRequestedAt or now
            if
                now - r.planRequestedAt < 1 or (o.plannerPending and now - r.planRequestedAt < 3)
            then
                return out
            end
            G.fallback(r)
            a = G.current(r)
        end
        if
            o.routineAdmission == false or (o.admittedAction ~= nil and o.admittedAction ~= a.id)
        then
            return out
        end
        if a.kind == "COLLECT" then
            r.planWaitAt = nil
            if o.interactionComplete == a.id then
                G.complete(r, a.id)
            end
            return out
        end
        if a.kind == "WAIT" then
            if r.planWaitAt then
                G.ensure(r).waitRemaining =
                    math.max(0, G.ensure(r).waitRemaining - (now - r.planWaitAt))
            end
            r.planWaitAt = now
            if G.ensure(r).waitRemaining <= 0 then
                G.complete(r, a.id)
                r.planWaitAt = nil
            end
            return out
        end
        r.planWaitAt = nil
    else
        r.planWaitAt = nil
    end
    if
        r.planEnabled
        and (r.state == "IDLE" or r.state == "WALK")
        and o.routineAdmission == false
    then
        return out
    end
    if
        r.state == "WALK"
        and r.moving
        and not r.requestPending
        and o.routeRefresh
        and o.goalReached == false
        and now >= r.nextRepath
    then
        navigate(r, out, now, false)
    end
    if r.state == "IDLE" and now >= r.deadline then
        r.state = "WALK"
        navigate(r, out, now, false)
    elseif
        r.state == "FLEE"
        and not r.requestPending
        and now >= r.nextRepath
        and (not r.moving or o.routeRefresh or (dangerMoved(r) and o.routeSafe ~= true))
    then
        if r.moving and o.routeSafe == false then
            interrupt(r, out, "FLEE")
        end
        navigate(r, out, now, true)
    end
    return out
end
function M.bind(state, id, seed, position, anchors, generation)
    state.residents = state.residents or {}
    local r = state.residents[id]
    if not r then
        r = M.new(id, seed, position, anchors)
        state.residents[id] = r
    else
        assert(r.life ~= "DEAD" and r.state ~= "TERMINAL", "terminal_resident")
        G.detach(r)
    end
    r.position = point(position)
    r.generation = generation
    r.planEnabled = true
    G.ensure(r)
    return r
end
function M.acceptPlan(r, p)
    local ok, why = G.accept(r, p)
    return { accepted = ok, reason = why }
end
function M.cancelAction(r, state, reason)
    return G.cancelCurrent(r, state, reason)
end
function M.detach(r)
    G.detach(r)
    return r
end
return M
