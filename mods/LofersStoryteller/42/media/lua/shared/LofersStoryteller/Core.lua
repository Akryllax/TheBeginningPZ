-- Pure Lua 5.1 domain model. Java references never enter persisted state.
local C = require "LofersStoryteller/Config"
local M = {}
local function clamp(v, lo, hi) return math.max(lo, math.min(hi, v)) end
local function decay(v, dt, half) return v * 2 ^ (-math.max(0, dt) / half) end
local categories = { food=1, medical=3, weapon=4, ammunition=0.4, tool=2, other=0.2 }
local caps = { food=120, medical=100, weapon=180, ammunition=100, tool=120, other=80 }

function M.new(now, seed)
    return {schema=C.schema, started_at_hour=now, seed=seed or 104729,
        cells={}, cell_order={}, cell_cursor=1, items={}, item_order={}, item_cursor=1,
        signals={}, signal_order={}, signal_cursor=1, players={}, decisions={},
        tick=0, dropped_cells=0, next_cell_id=1, next_id=1, budget=0, pressure=0,
        last_evaluation=now, next_decision=now, recovery_until=now,
        last_hostile=now-C.hostileCooldownHours, active_events={}, mode=C.mode}
end

function M.cellKey(x, y, z)
    return math.floor(x/C.cellSize)..":"..math.floor(y/C.cellSize)..":"..math.floor(z)
end

local function ageCell(cell, now)
    local dt=now-cell.decayed_at
    cell.dwell = decay(cell.dwell, dt, C.dwellHalfLifeHours)
    for category,value in pairs(cell.stock) do
        cell.stock[category]=decay(value,dt,C.evidenceHalfLifeHours)
    end
    cell.infrastructure=decay(cell.infrastructure,dt,C.evidenceHalfLifeHours)
    cell.defense=decay(cell.defense,dt,C.evidenceHalfLifeHours)
    cell.vehicles=decay(cell.vehicles,dt,C.evidenceHalfLifeHours)
    cell.decayed_at = now
end

function M.cell(s, x, y, z, now)
    local key = M.cellKey(x,y,z)
    local cell = s.cells[key]
    if not cell then
        if #s.cell_order >= C.maxCells then
            local old = s.cell_order[s.cell_cursor]
            s.cells[old] = nil
            s.cell_order[s.cell_cursor] = key
            s.cell_cursor = s.cell_cursor % C.maxCells + 1
            s.dropped_cells = s.dropped_cells + 1
        else s.cell_order[#s.cell_order+1] = key end
        cell = {key=key, x=math.floor(x/C.cellSize)*C.cellSize,
            y=math.floor(y/C.cellSize)*C.cellSize, z=math.floor(z),
            generation=s.next_cell_id, dwell=0, visits=0, stock={}, infrastructure=0,
            defense=0, vehicles=0, decayed_at=now, last_seen=now,
            stock_seen=-1, stock_complete=false}
        s.cells[key] = cell
        s.next_cell_id=s.next_cell_id+1
    end
    ageCell(cell,now)
    return cell
end

function M.presence(s, id, x, y, z, now, health)
    local cell = M.cell(s,x,y,z,now)
    local player = s.players[id]
    if not player then
        player={last_hour=now, last_key=cell.key, health=health or 100}
        s.players[id]=player
        cell.visits=math.min(100,cell.visits+1)
    end
    local dt=clamp(now-player.last_hour,0,1/12) -- no offline/catch-up dwell
    cell.dwell=math.min(72,cell.dwell+dt)
    if player.last_key~=cell.key then cell.visits=math.min(100,cell.visits+1) end
    if health and player.health and (player.health-health>=20 or (health<60 and player.health>=60)) then
        s.recovery_until=math.max(s.recovery_until,now+C.recoveryHours)
    end
    player.health=health or player.health
    player.last_hour=now
    player.last_key=cell.key
    cell.last_seen=now
    return cell
end

local function removeStock(s, entry, now)
    local cell=s.cells[entry.key]
    if cell and cell.generation==entry.generation then
        ageCell(cell,now)
        local value=decay(entry.value,now-(entry.last_seen or now),C.evidenceHalfLifeHours)
        local remaining=math.max(0,(cell.stock[entry.category] or 0)-value)
        cell.stock[entry.category]=remaining<0.000000001 and 0 or remaining
    end
end

function M.item(s, id, cell, category, now)
    id=tostring(id)
    category=categories[category] and category or "other"
    local previous=s.items[id]
    if previous then removeStock(s,previous,now)
    elseif #s.item_order>=C.maxItems then
        local old=s.item_order[s.item_cursor]
        if s.items[old] then removeStock(s,s.items[old],now) end
        s.items[old]=nil
        s.item_order[s.item_cursor]=id
        s.item_cursor=s.item_cursor%C.maxItems+1
    else s.item_order[#s.item_order+1]=id end
    local value=categories[category]
    ageCell(cell,now)
    s.items[id]={key=cell.key,generation=cell.generation,category=category,value=value,last_seen=now}
    cell.stock[category]=(cell.stock[category] or 0)+value
    cell.stock_seen=now
    -- A partial estimate with individually aging sightings, never a census.
    cell.stock_complete=false
end

function M.signal(s, id, cell, kind, now)
    if kind~="infrastructure" and kind~="defense" and kind~="vehicles" then return end
    id=tostring(id)
    local previous=s.signals[id]
    if previous then
        local old=s.cells[previous.key]
        if old and old.generation==previous.generation then
            ageCell(old,now)
            old[previous.kind]=math.max(0,old[previous.kind]-decay(1,now-previous.last_seen,C.evidenceHalfLifeHours))
        end
    elseif #s.signal_order>=C.maxSignals then
        local key=s.signal_order[s.signal_cursor]
        local entry=s.signals[key]
        local old=entry and s.cells[entry.key]
        if old and old.generation==entry.generation then
            ageCell(old,now)
            old[entry.kind]=math.max(0,old[entry.kind]-decay(1,now-entry.last_seen,C.evidenceHalfLifeHours))
        end
        s.signals[key]=nil
        s.signal_order[s.signal_cursor]=id
        s.signal_cursor=s.signal_cursor%C.maxSignals+1
    else s.signal_order[#s.signal_order+1]=id end
    ageCell(cell,now)
    cell[kind]=cell[kind]+1
    s.signals[id]={key=cell.key,generation=cell.generation,kind=kind,last_seen=now}
end

function M.score(cell, now)
    local dwell=decay(cell.dwell,now-cell.decayed_at,C.dwellHalfLifeHours)
    local fresh=decay(1,now-cell.last_seen,C.evidenceHalfLifeHours)
    local evidenceFresh=decay(1,now-cell.decayed_at,C.evidenceHalfLifeHours)
    local wealth=0
    for category,value in pairs(cell.stock) do wealth=wealth+math.min(caps[category],value*evidenceFresh) end
    wealth=math.sqrt(wealth/700)
    local stockEvidence=cell.stock_seen>=0 and 0.15*decay(1,now-cell.stock_seen,C.evidenceHalfLifeHours) or 0
    local confidence=clamp((math.min(1,dwell/4)*0.45+math.min(1,cell.visits/8)*0.2+
        math.min(1,cell.infrastructure*evidenceFresh/3)*0.12+
        math.min(1,cell.defense*evidenceFresh/8)*0.05+
        math.min(1,cell.vehicles*evidenceFresh/2)*0.03+stockEvidence)*fresh,0,1)
    return {wealth=clamp(wealth,0,1),confidence=confidence,dwell=dwell,
        age_hours=math.max(0,now-cell.last_seen)}
end

function M.phase(s, now)
    local days=math.max(0,now-s.started_at_hour)/24
    if days<8 then return "outbreak" elseif days<31 then return "aftermath" end
    return "survival"
end

function M.random(s)
    s.seed=(s.seed*16807)%2147483647
    return s.seed/2147483647
end

function M.record(s, event, outcome, reason, now, id)
    local decision={id=id or ("story-"..s.next_id),event=event,outcome=outcome,reason=reason,at_hour=now}
    if not id then s.next_id=s.next_id+1 end
    if #s.decisions>=C.maxDecisions then table.remove(s.decisions,1) end
    s.decisions[#s.decisions+1]=decision
    return decision
end

function M.loss(s, now, reason)
    s.recovery_until=math.max(s.recovery_until,now+C.recoveryHours)
    M.record(s,"recovery","started",reason or "confirmed_loss",now)
end

-- Each event has eligibility, cost, a bounded lifetime and phase weights.
M.events={
    {name="radio_bulletin",cost=0,npcs=0,hostile=false,weights={outbreak=8,aftermath=5,survival=2}},
    {name="distant_fighting",cost=2,npcs=0,hostile=false,weights={outbreak=8,aftermath=2,survival=1}},
    {name="fleeing_group",cost=3,npcs=3,hostile=false,weights={outbreak=6,aftermath=2,survival=1}},
    {name="scavenger_patrol",cost=4,npcs=3,hostile=true,weights={outbreak=2,aftermath=5,survival=3}},
    {name="looters",cost=6,npcs=4,hostile=true,weights={outbreak=1,aftermath=4,survival=4}},
    {name="limited_sabotage",cost=8,npcs=2,hostile=true,weights={outbreak=0,aftermath=1,survival=2}},
}

function M.eligibility(s, event, now, context)
    if context.online<=0 then return false,"no_online_players" end
    local pending=0
    for _ in pairs(s.active_events) do pending=pending+1 end
    if pending>=8 then return false,"unresolved_event_cap" end
    if event.hostile then
        if now-s.started_at_hour<C.hostileGraceHours then return false,"new_world_grace" end
        if now<s.recovery_until then return false,"recovery" end
        if now-s.last_hostile<C.hostileCooldownHours then return false,"hostile_cooldown" end
        if context.major>=C.maxMajorEvents then return false,"major_event_active" end
        if s.pressure<0.2 then return false,"insufficient_pressure" end
    end
    if event.npcs>0 then
        if not context.npc_known then return false,"npc_count_unknown" end
        if event.npcs>C.maxEncounterNPCs or context.npcs+event.npcs>C.maxNPCs then return false,"npc_cap" end
    end
    if s.budget<event.cost then return false,"budget" end
    return true,"eligible"
end

function M.evaluate(s, now, context, adapters)
    local dt=clamp(now-s.last_evaluation,0,1/60)
    s.last_evaluation=now
    -- No online => no budget accrual, elapsed-time burst, or event progression.
    if context.online<=0 then s.next_decision=math.max(s.next_decision,now+C.decisionIntervalHours);return end
    local capability=clamp(context.capability or 0,0,1)
    local age=clamp((now-s.started_at_hour)/(24*60),0,1)
    local desired=clamp((context.wealth or 0)*0.45+capability*0.35+age*0.2,0,1)
    if now<s.recovery_until then desired=desired*0.2 end
    s.pressure=s.pressure+(desired-s.pressure)*math.min(1,dt/0.5)
    s.budget=math.min(C.budgetCap,s.budget+dt*C.budgetPerHour*(0.5+s.pressure))
    if now<s.next_decision then return end
    s.next_decision=now+C.decisionIntervalHours
    local phase=M.phase(s,now)
    local total=0
    for _,event in ipairs(M.events) do total=total+event.weights[phase] end
    local pick=M.random(s)*total
    local selected=M.events[1]
    for _,event in ipairs(M.events) do
        pick=pick-event.weights[phase]
        if pick<=0 then selected=event;break end
    end
    local ok,reason=M.eligibility(s,selected,now,context)
    if not ok then return M.record(s,selected.name,"blocked",reason,now) end
    if s.mode~="active" then return M.record(s,selected.name,"preview","observation_only",now) end
    local adapter=adapters and adapters[selected.name]
    if not adapter then return M.record(s,selected.name,"blocked","adapter_not_validated",now) end
    if selected.npcs>0 and not C.npcIntegrationValidated then
        return M.record(s,selected.name,"blocked","multiplayer_validation_required",now)
    end
    local id="story-"..s.next_id
    s.next_id=s.next_id+1
    -- Persist a reservation before invoking an external API. Never retry on uncertainty.
    local active={id=id,event=selected.name,npcs=selected.npcs,hostile=selected.hostile,
        started=now,expires=now+C.eventLifetimeHours,status="reserved"}
    s.active_events[id]=active
    local ran,result=pcall(adapter,active,context)
    if not ran or result~=true then
        active.status="uncertain"
        if ran then s.active_events[id]=nil end
        return M.record(s,selected.name,"blocked",ran and "placement_or_adapter_declined" or "adapter_error",now,id)
    end
    active.status="active"
    s.budget=s.budget-selected.cost
    if selected.hostile then s.last_hostile=now end
    return M.record(s,selected.name,"started","adapter_accepted",now,id)
end

-- An integration must explicitly confirm cleanup. Expiry alone never subtracts live NPCs.
function M.complete(s, id, now, outcome)
    local event=s.active_events[id]
    if not event then return false end
    s.active_events[id]=nil
    M.record(s,event.event,"completed",outcome or "confirmed_cleanup",now,id)
    return true
end

function M.snapshot(s, now, context, timings, start)
    local cells={}
    local n=#s.cell_order
    start=n>0 and ((start or 1)-1)%n+1 or 1
    for i=0,math.min(n,C.maxTelemetryCells)-1 do
        local cell=s.cells[s.cell_order[(start+i-1)%n+1]]
        local score=M.score(cell,now)
        cells[#cells+1]={x=cell.x,y=cell.y,z=cell.z,dwell=score.dwell,
            wealth=score.wealth,confidence=score.confidence,age_hours=score.age_hours}
    end
    local decisions={}
    for _,d in ipairs(s.decisions) do
        decisions[#decisions+1]={id=d.id,event=d.event,outcome=d.outcome,reason=d.reason,at_hour=d.at_hour}
    end
    return {schema=C.schema,tick=s.tick,world_age_hours=now,phase=M.phase(s,now),
        pressure=s.pressure,budget=s.budget,mode=s.mode,online_players=context.online,
        npc_count=context.npc_known and context.npcs or -1,timings_ms=timings,
        cells=cells,decisions=decisions,cell_count=n,scan_queue=context.scan_queue or 0,
        dropped_cells=s.dropped_cells,health=context.health or "observing"}
end

return M
