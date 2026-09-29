-- Server-owned durable-intent model. No game API, spawning, sound or scheduling here.
-- A native adapter must durably checkpoint an intent before release(), and reconcile
-- interrupted materialization against tagged world objects. ModData assignment alone
-- is NOT a durable checkpoint. The live effect adapter is deliberately not installed.
local T = { limit = 64, cooldownHours = 1 }
local phases = {
    first_cases = true,
    concern = true,
    emergency = true,
    disruption = true,
    evacuation = true,
    collapse = true,
}
local function finite(v)
    return type(v) == "number" and v == v and math.abs(v) < math.huge
end
local function text(v)
    return type(v) == "string" and #v > 0 and #v <= 128
end
local function point(v)
    if
        type(v) ~= "table"
        or not finite(v.x)
        or not finite(v.y)
        or v.x < -20000
        or v.x > 60000
        or v.y < -20000
        or v.y > 60000
        or (v.z or 0) ~= 0
    then
        return nil
    end
    return { x = v.x, y = v.y, z = 0 }
end
local function bump(e)
    e.revision = e.revision + 1
end
local function active(e)
    return e.phase ~= "aftermath" and e.phase ~= "cancelled"
end
function T.bind(s, restarting)
    if not text(s.world_id) then
        return false, "invalid_world"
    end
    s.traffic = s.traffic
        or { schema = 1, world_id = s.world_id, next_id = 0, order = {}, events = {}, next_hour = 0 }
    local t = s.traffic
    if
        type(t) ~= "table"
        or t.schema ~= 1
        or t.world_id ~= s.world_id
        or type(t.order) ~= "table"
        or type(t.events) ~= "table"
        or #t.order > T.limit
        or not finite(t.next_id)
        or t.next_id < 0
        or t.next_id ~= math.floor(t.next_id)
        or not finite(t.next_hour)
    then
        return false, "invalid_incident_state"
    end
    for _, id in ipairs(t.order) do
        local e = t.events[id]
        if
            type(e) ~= "table"
            or e.id ~= id
            or e.world_id ~= s.world_id
            or not finite(e.revision)
            or not text(e.phase)
            or (e.pending ~= nil and type(e.pending) ~= "table")
        then
            return false, "invalid_incident_record"
        end
    end
    if restarting then
        for _, id in ipairs(t.order) do
            local e = t.events[id]
            -- Never infer whether independent world/save/audio effects completed.
            if e.pending then
                if e.pending.kind == "sound" then
                    e.sound = "uncertain_spent"
                    e.pending = nil
                else
                    e.phase = "unresolved"
                    e.pending.state = "uncertain"
                end
                bump(e)
            elseif active(e) then
                e.phase = "unresolved"
                bump(e)
            end
        end
    end
    return true
end
function T.reservations(s)
    local events, cars = 0, 0
    for _, id in ipairs((s.traffic or {}).order or {}) do
        if active(s.traffic.events[id]) then
            events = events + 1
            cars = cars + 2
        end
    end
    return events, cars
end
local function ready(s, c)
    if
        not c
        or s.status ~= "running"
        or not phases[s.phase]
        or not finite(s.elapsed_hours)
        or s.elapsed_hours < 0
    then
        return false, "scenario_not_running"
    end
    if
        not finite(c.online)
        or c.online < 1
        or c.online > 32
        or c.online ~= math.floor(c.online)
    then
        return false, "no_complete_audience"
    end
    if
        c.all_players_known ~= true
        or c.scene_clear ~= true
        or c.player_exclusion ~= true
        or c.unseen ~= true
        or not finite(c.age_seconds)
        or c.age_seconds < 0
        or c.age_seconds > 0.5
    then
        return false, "placement_not_revalidated"
    end
    return true
end
function T.reserve(s, plan, c)
    local ok, why = T.bind(s)
    if not ok then
        return nil, why
    end
    ok, why = ready(s, c)
    if not ok then
        return nil, why
    end
    local t = s.traffic
    local events, cars = T.reservations(s)
    if
        events >= 1
        or not finite(c.moving_vehicles)
        or c.moving_vehicles < 0
        or c.moving_vehicles ~= math.floor(c.moving_vehicles)
        or c.moving_vehicles + cars + 2 > 4
    then
        return nil, "fleet_capacity"
    end
    if #t.order >= T.limit then
        return nil, "incident_capacity"
    end
    if s.elapsed_hours < t.next_hour then
        return nil, "incident_cooldown"
    end
    if
        type(plan) ~= "table"
        or (plan.kind ~= "offscreen" and plan.kind ~= "visible")
        or not finite(plan.seed)
        or plan.seed < 1
        or plan.seed > 2147483646
        or plan.seed ~= math.floor(plan.seed)
    then
        return nil, "invalid_incident"
    end
    local site = point(plan.site)
    if not site then
        return nil, "invalid_site"
    end
    if type(plan.wrecks) ~= "table" or #plan.wrecks ~= 2 then
        return nil, "two_owned_participants_required"
    end
    local wrecks = {}
    for _, w in ipairs(plan.wrecks) do
        if type(w) ~= "table" then
            return nil, "invalid_aftermath"
        end
        local p = point(w.position)
        if
            not p
            or math.abs(p.x - site.x) > 12
            or math.abs(p.y - site.y) > 12
            or not text(w.script)
            or not finite(w.yaw)
            or w.yaw < 0
            or w.yaw >= 360
            or type(w.parts) ~= "table"
            or type(w.loot) ~= "table"
            or #w.loot > 32
        then
            return nil, "invalid_aftermath"
        end
        local parts = {}
        local count = 0
        for name, value in pairs(w.parts) do
            count = count + 1
            if
                count > 32
                or not text(name)
                or not finite(value)
                or value < 0
                or value > 100
                or value ~= math.floor(value)
            then
                return nil, "invalid_part_condition"
            end
            parts[name] = value
        end
        local loot = {}
        local total = 0
        for _, item in ipairs(w.loot) do
            if
                type(item) ~= "table"
                or not text(item.type)
                or not finite(item.count)
                or item.count < 1
                or item.count > 32
                or item.count ~= math.floor(item.count)
            then
                return nil, "invalid_loot"
            end
            total = total + item.count
            if total > 64 then
                return nil, "loot_capacity"
            end
            loot[#loot + 1] = { type = item.type, count = item.count }
        end
        wrecks[#wrecks + 1] =
            { position = p, yaw = w.yaw, script = w.script, parts = parts, loot = loot }
    end
    t.next_id = t.next_id + 1
    local id = s.world_id .. ":traffic:" .. t.next_id
    for i, w in ipairs(wrecks) do
        w.id = id .. ":car:" .. i
    end
    local e = {
        id = id,
        world_id = s.world_id,
        revision = 1,
        phase = "reserved",
        kind = plan.kind,
        site = site,
        seed = plan.seed,
        created_hour = s.elapsed_hours,
        wrecks = wrecks,
        sound = "unclaimed",
        effect_serial = 0,
    }
    t.events[id] = e
    t.order[#t.order + 1] = id
    t.next_hour = s.elapsed_hours + T.cooldownHours
    return e
end
function T.prepare(s, id, kind, c)
    local e = s.traffic and s.traffic.events[id]
    if not e or e.pending then
        return nil, "incident_pending_or_missing"
    end
    local ok, why = ready(s, c)
    if not ok then
        return nil, why
    end
    if kind == "materialize" then
        if e.phase ~= "reserved" then
            return nil, "materialization_not_reserved"
        end
    elseif kind == "sound" then
        -- Sound-only resolution first creates the actual hidden aftermath. An
        -- investigation can therefore never outrun a deferred materializer.
        if
            e.kind ~= "offscreen"
            or e.phase ~= "aftermath"
            or e.sound ~= "unclaimed"
            or not e.world_receipt
        then
            return nil, "aftermath_not_ready"
        end
    else
        return nil, "unsupported_effect"
    end
    bump(e)
    e.effect_serial = e.effect_serial + 1
    e.pending = {
        kind = kind,
        token = e.id .. ":" .. kind .. ":" .. e.effect_serial,
        revision = e.revision,
        state = "prepared",
    }
    return e.pending.token, e.revision
end
function T.release(s, id, token, checkpointRevision, c)
    local e = s.traffic and s.traffic.events[id]
    local p = e and e.pending
    if
        not p
        or p.token ~= token
        or p.state ~= "prepared"
        or p.revision ~= checkpointRevision
        or e.revision ~= checkpointRevision
    then
        return false, "durable_intent_required"
    end
    local ok, why = ready(s, c)
    if not ok then
        return false, why
    end
    -- Only a trusted adapter may provide this exact durable revision; clients
    -- never call this API. Restoration does not grant this token a second time.
    p.state = "released"
    if p.kind == "sound" then
        e.sound = "claimed"
    end
    return true
end
function T.receipt(s, id, token, receipt)
    local e = s.traffic and s.traffic.events[id]
    local p = e and e.pending
    if not p or p.token ~= token or p.state ~= "released" then
        return false, "no_released_effect"
    end
    if type(receipt) ~= "table" or receipt.world_id ~= s.world_id then
        return false, "wrong_world_receipt"
    end
    if p.kind == "materialize" then
        if
            type(receipt.wreck_ids) ~= "table"
            or #receipt.wreck_ids ~= 2
            or receipt.wreck_ids[1] ~= e.wrecks[1].id
            or receipt.wreck_ids[2] ~= e.wrecks[2].id
            or not text(receipt.saved_world_revision)
        then
            return false, "saved_aftermath_receipt_required"
        end
        e.world_receipt = receipt.saved_world_revision
        e.phase = e.kind == "offscreen" and "aftermath" or "staged"
    else
        e.sound = "sent"
    end
    e.pending = nil
    bump(e)
    return true
end
function T.cancel(s, id)
    local e = s.traffic and s.traffic.events[id]
    if not e or e.phase ~= "reserved" or e.pending then
        return false, "reconciliation_required"
    end
    e.phase = "cancelled"
    bump(e)
    return true
end
return T
