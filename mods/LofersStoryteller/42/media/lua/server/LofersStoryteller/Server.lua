if isClient() then
    return
end
local C = require("LofersStoryteller/Config")
local Core = require("LofersStoryteller/Core")
local Scanner = require("LofersStoryteller/Scanner")
local Bandits = require("LofersStoryteller/Bandits")
local R = {
    scanner = Scanner.new(),
    last_tick = -1,
    last_presence = -1,
    last_publish = -1,
    players = {},
    player_cursor = 1,
    tile_cursor = 0,
    object_cursor = 0,
    container_cursor = 0,
    square = nil,
    object = nil,
    timings = {},
    timing_cursor = 1,
    telemetry_cursor = 1,
    health = "observing",
}

local function clock()
    -- In the pinned dedicated-server build getServerTime() is System.nanoTime().
    -- Epoch time would let an NTP correction defeat the work deadline.
    if GameTime and GameTime.getServerTime then
        return GameTime.getServerTime() / 1000000
    end
    return getTimestampMs()
end
local function hour()
    return getGameTime():getWorldAgeHours()
end
local function initialize()
    R.root = ModData.getOrCreate("LofersStoryteller")
    if not R.root.state then
        R.root.state = Core.new(hour(), clock() % 2147483646 + 1)
    end
    if R.root.state.schema ~= C.schema then
        R.health = "unsupported_saved_schema"
        R.disabled = true
        print("[LofersStoryteller] Unsupported saved schema; disabled without changing state.")
        return
    end
    R.state = R.root.state
    -- Configuration owns activation; save data alone cannot turn encounters on.
    R.state.mode = C.mode
    print("[LofersStoryteller] " .. C.version .. " initialized in " .. C.mode .. " mode")
end

local function presence(now)
    local all = getOnlinePlayers()
    local present = {}
    R.players = {}
    for i = 0, math.min(all:size(), C.maxOnlinePlayers) - 1 do
        local player = all:get(i)
        local id = tostring(player:getUsername())
        present[id] = true
        if not player:isDead() then
            R.players[#R.players + 1] = player
            local cell = Core.presence(
                R.state,
                id,
                player:getX(),
                player:getY(),
                player:getZ(),
                now,
                player:getBodyDamage():getOverallBodyHealth()
            )
            Scanner.enqueue(R.scanner, player:getInventory(), cell)
            local vehicle = player:getVehicle()
            if vehicle then
                Core.signal(R.state, "vehicle:" .. vehicle:getId(), cell, "vehicles", now)
            end
        elseif R.state.players[id] then
            Core.loss(R.state, now, "observed_player_death")
            R.state.players[id] = nil
        end
    end
    for id in pairs(R.state.players) do
        if not present[id] then
            R.state.players[id] = nil
        end
    end
end

-- Each invocation visits at most one square, object or container. The cursor
-- advances across a 25x25 area around one online player, on that player's floor.
local function worldStep(now)
    if #R.players == 0 then
        return
    end
    if R.object then
        if R.container_cursor < R.object:getContainerCount() then
            local inv = R.object:getContainerByIndex(R.container_cursor)
            R.container_cursor = R.container_cursor + 1
            local cell = Core.cell(R.state, R.square:getX(), R.square:getY(), R.square:getZ(), now)
            Scanner.enqueue(R.scanner, inv, cell)
        else
            R.object = nil
        end
        return
    end
    if R.square then
        local objects = R.square:getObjects()
        if R.object_cursor < objects:size() then
            local object = objects:get(R.object_cursor)
            R.object_cursor = R.object_cursor + 1
            local cell = Core.cell(R.state, R.square:getX(), R.square:getY(), R.square:getZ(), now)
            local kind = nil
            if instanceof(object, "IsoGenerator") then
                kind = "infrastructure"
            elseif instanceof(object, "IsoBarricade") then
                kind = "defense"
            end
            if kind then
                local id = R.square:getX()
                    .. ":"
                    .. R.square:getY()
                    .. ":"
                    .. R.square:getZ()
                    .. ":"
                    .. R.object_cursor
                    .. ":"
                    .. kind
                Core.signal(R.state, id, cell, kind, now)
            end
            R.object = object
            R.container_cursor = 0
        else
            R.square = nil
        end
        return
    end
    R.player_cursor = (R.player_cursor - 1) % #R.players + 1
    local player = R.players[R.player_cursor]
    local dx = R.tile_cursor % 25 - 12
    local dy = math.floor(R.tile_cursor / 25) - 12
    R.square = getCell():getGridSquare(
        math.floor(player:getX()) + dx,
        math.floor(player:getY()) + dy,
        math.floor(player:getZ())
    )
    R.object_cursor = 0
    R.tile_cursor = R.tile_cursor + 1
    if R.tile_cursor >= 625 then
        R.tile_cursor = 0
        R.player_cursor = R.player_cursor % #R.players + 1
    end
end

local function context(now)
    local wealth, health = 0, 0
    for _, player in ipairs(R.players) do
        local entry = R.state.players[tostring(player:getUsername())]
        local cell = entry and R.state.cells[entry.last_key]
        if cell then
            local score = Core.score(cell, now)
            wealth = math.max(wealth, score.wealth * score.confidence)
        end
        health = health + player:getBodyDamage():getOverallBodyHealth() / 100
    end
    local major = 0
    for _, event in pairs(R.state.active_events) do
        if event.hostile then
            major = major + 1
        end
    end
    return {
        online = #R.players,
        wealth = wealth,
        capability = math.min(1, health / 4),
        npcs = 0,
        npc_known = false,
        major = major,
        scan_queue = #R.scanner.queue,
        health = R.health,
    }
end

local function timingSummary()
    local sorted = {}
    local n = #R.timings
    for i, v in ipairs(R.timings) do
        sorted[i] = v
    end
    table.sort(sorted)
    return {
        last = R.last_duration or 0,
        p95 = sorted[math.max(1, math.ceil(n * 0.95))] or 0,
        p99 = sorted[math.max(1, math.ceil(n * 0.99))] or 0,
        max = sorted[n] or 0,
    }
end

local function update()
    if R.disabled then
        return
    end
    if not R.state then
        initialize()
    end
    if R.disabled then
        return
    end
    local started = clock()
    if started - R.last_tick < C.updateIntervalMs then
        return
    end
    R.last_tick = started
    local now = hour()
    if not Bandits.installed then
        Bandits.install()
    end
    if R.health ~= "scanner_error" then
        R.health = Bandits.status()
    end
    R.state.tick = R.state.tick + 1
    if started - R.last_presence >= C.presenceIntervalMs then
        presence(now)
        R.last_presence = started
    end
    local ctx = context(now)
    if not R.last_evaluation or now - R.last_evaluation >= 1 / 60 then
        Core.evaluate(R.state, now, ctx, {})
        R.last_evaluation = now
    end
    local operations = 0
    local deadline = started + C.updateMilliseconds
    -- Alternate world and inventory work: a huge inventory cannot starve mapping.
    while #R.players > 0 and operations < C.maxOperations and clock() < deadline do
        if operations % 2 == 0 then
            worldStep(now)
        else
            Scanner.drain(R.scanner, R.state, now, clock, deadline, function(item)
                return instanceof(item, "InventoryContainer")
            end, 1)
        end
        operations = operations + 1
    end
    if started - R.last_publish >= C.telemetryIntervalMs then
        R.root.telemetry = Core.snapshot(R.state, now, ctx, timingSummary(), R.telemetry_cursor)
        R.telemetry_cursor = R.telemetry_cursor + C.maxTelemetryCells
        R.last_publish = started
    end
    R.last_duration = math.max(0, clock() - started)
    R.timings[R.timing_cursor] = R.last_duration
    R.timing_cursor = R.timing_cursor % 256 + 1
end

local function safeUpdate()
    local ok, err = pcall(update)
    if not ok then
        R.health = "scanner_error"
        R.square = nil
        R.object = nil
        local now = clock()
        if not R.last_error or now - R.last_error > 60000 then
            print("[LofersStoryteller] Read-only update failed: " .. tostring(err))
            R.last_error = now
        end
    end
end

local function death(player)
    if R.state and player and R.state.players[tostring(player:getUsername())] then
        Core.loss(R.state, hour(), "player_death_event")
        R.state.players[tostring(player:getUsername())] = nil
    end
end

Events.OnInitGlobalModData.Add(initialize)
Events.OnServerStarted.Add(Bandits.install)
Events.OnTick.Add(safeUpdate)
Events.OnPlayerDeath.Add(death)
