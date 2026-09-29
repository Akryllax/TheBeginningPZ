local K = require("AKRCore/Core")
local core = K.instance()
local R = AKRPedestrianServer or { actors = {}, lastPublish = 0, receipts = {}, lastClient = {} }
AKRPedestrianServer = R
local cid = "akr-pedestrian-experiment-v1"
local state

local function factory(id, x, y)
    local players = getOnlinePlayers()
    if players:size() == 0 then
        error("observer_required")
    end
    state.pending[id] = { x = x, y = y, epoch = AKRRuntime.epoch, status = "spawning" }
    local clan = BanditCustom.ClanGet(cid) or BanditCustom.ClanCreate(cid)
    clan.general.name = "AKR experimental civilians"
    clan.spawn.spawnChance = 0
    clan.spawn.friendly = true
    local profile = BanditCustom.GetById(id) or BanditCustom.Create(id)
    profile.cid = cid
    profile.general.cid = cid
    profile.general.bid = id
    profile.general.name = "Civilian"
    profile.general.female = false
    profile.general.health = 5
    profile.general.skin = 1
    profile.general.hairType = 1
    profile.general.hairColor = 1
    profile.general.beardType = 1
    profile.weapons = { melee = "Base.BareHands" }
    profile.ammo = {}
    profile.bag = nil
    profile.clothing = {
        Shirt = "Base.Shirt_Denim",
        Pants = "Base.Trousers_JeanBaggy",
        Shoes = "Base.Shoes_Random",
    }
    local transmit = TransmitBanditCluster
    local outfit
    TransmitBanditCluster = function(key)
        local cluster = GetBanditClusterData(key)
        local brain = cluster and cluster[key]
        if brain and brain.bid == id then
            outfit = key
        end
        return transmit(key)
    end
    local refusal
    local ok, err = pcall(function()
        local allowed, why = AKRNative.spawn(function()
            BanditServer.Spawner.Individual(players:get(0), {
                bid = id,
                x = x,
                y = y,
                z = 0,
                program = "AKRRemoteCivilian",
                hostile = false,
                hostileP = false,
                fullname = "Civilian",
                permanent = false,
            })
        end, {})
        if not allowed then
            if why == "spawn_not_authorized" then
                refusal = why
            else
                error(why or "spawn_not_permitted")
            end
        end
    end)
    TransmitBanditCluster = transmit
    if refusal then
        state.pending[id] = nil
        return false
    end
    if not ok then
        error(err)
    end
    if not outfit then
        error("spawn_receipt_missing")
    end
    state.pending[id].outfit = outfit
    for dx = -1, 1 do
        for dy = -1, 1 do
            local square = getCell():getGridSquare(math.floor(x) + dx, math.floor(y) + dy, 0)
            if square then
                local objects = square:getMovingObjects()
                for i = 0, math.min(objects:size(), 32) - 1 do
                    local actor = objects:get(i)
                    if
                        instanceof(actor, "IsoZombie")
                        and BanditUtils.GetCharacterID(actor) == outfit
                    then
                        local cluster = GetBanditClusterData(outfit)
                        local brain = cluster and cluster[outfit]
                        actor:setHealth(brain and brain.health or 1)
                        actor:getModData().AKRPedestrian = { id = id, epoch = AKRRuntime.epoch }
                        if not AKRNative.bind(actor, id, 1, 1, -1) then
                            error("bind_failed")
                        end
                        R.actors[id] = actor
                        state.pending[id].status = "active"
                        return actor
                    end
                end
            end
        end
    end
    error("spawn_entity_unresolved")
end

local function retire(actor, id)
    -- Idempotent registry acknowledgment; Java independently checks native absence.
    if type(id) == "string" and not R.actors[id] and not state.pending[id] then
        return true
    end
    local tag = actor:getModData().AKRPedestrian
    if not tag or (id and tag.id ~= id) or R.actors[tag.id] ~= actor then
        return false
    end
    local entry = state.pending[tag.id]
    if not entry then
        return false
    end
    if not AKRNative.removeOwned(actor) then
        return false
    end
    local cluster = GetBanditClusterData(entry.outfit)
    if cluster then
        cluster[entry.outfit] = nil
        TransmitBanditCluster(entry.outfit)
    end
    R.actors[tag.id] = nil
    state.pending[tag.id] = nil
    return true
end

local function initialize()
    if not AKRRuntime or not AKRRuntime.world:match("^AKR_DayOne_Test_") then
        return
    end
    state = ModData.getOrCreate("AKRPedestrianExperiment")
    state.pending = state.pending or {}
    local unresolved = false
    for _ in pairs(state.pending) do
        unresolved = true
        break
    end
    if unresolved then
        print(
            "[AKRDevTools] restart_reconciliation_required; saved actors retained, spawning disabled"
        )
        return
    end
    local permitted, permitWhy = AKRNative.spawn(function()
        return true
    end, {})
    if not permitted then
        error(permitWhy or "spawn_permit_missing")
    end
    local ok, why = AKRRuntime.register(factory, retire)
    if not ok then
        error(why)
    end
    K.Capabilities.set(core.capabilities, "runtime.pedestrian-experiment", "0.1.0", "unvalidated")
    K.Registry.provide(core.registry, "AKRRuntime", "0.1.0", AKRRuntime)
    R.initialized = true
    print(
        "[AKRDevTools] pedestrian adapter registered; ordinary clients; native validation pending"
    )
end

local function publish(player)
    if not AKRRuntime or not state then
        return
    end
    local actors = {}
    for id, actor in pairs(R.actors) do
        local event = id:match("^(.*)%.npc%d+$")
        local status = event and AKRRuntime.status(event)
        local action = "PREPARING"
        for _, sample in ipairs(status and status.actors or {}) do
            if sample.id == id then
                action = sample.action
            end
        end
        actors[#actors + 1] = {
            id = id,
            outfit = state.pending[id].outfit,
            online_id = actor:getOnlineID(),
            action = action,
        }
    end
    local data = { epoch = AKRRuntime.epoch, server_ms = getTimestampMs(), actors = actors }
    if player then
        sendServerCommand(player, "AKRDevTools", "snapshot", data)
    else
        sendServerCommand("AKRDevTools", "snapshot", data)
    end
end

K.Dispatch.on(core.dispatch, "OnServerStarted", "AKRDevTools.init", initialize)
-- Stock native state machine now advances pathfinding once per engine update.
K.Dispatch.on(core.dispatch, "OnTick", "AKRDevTools.publish", function()
    if getTimestampMs() - R.lastPublish < 200 then
        return
    end
    R.lastPublish = getTimestampMs()
    publish()
end)
K.Dispatch.on(
    core.dispatch,
    "OnClientCommand",
    "AKRDevTools.measure",
    function(module, command, player, args)
        if module ~= "AKRDevTools" or not AKRRuntime or not state then
            return
        end
        local id = player:getOnlineID()
        local now = getTimestampMs()
        if now - (R.lastClient[id] or 0) < 500 then
            return
        end
        R.lastClient[id] = now
        if command == "hello" then
            publish(player)
        elseif command == "sample" and type(args) == "table" and args.epoch == AKRRuntime.epoch then
            -- Diagnostic only. These reports cannot alter NPC movement, health or outcomes.
            local samples = {}
            for i = 1, math.min(type(args.actors) == "table" and #args.actors or 0, 4) do
                local a = args.actors[i]
                if
                    type(a) == "table"
                    and R.actors[a.id]
                    and type(a.x) == "number"
                    and type(a.y) == "number"
                then
                    samples[#samples + 1] = {
                        id = a.id,
                        x = a.x,
                        y = a.y,
                        action = tostring(a.action):sub(1, 32),
                        skin = tostring(a.skin):sub(1, 80),
                        clothes = tonumber(a.clothes),
                    }
                end
            end
            R.receipts[id] = {
                received_ms = now,
                client_ms = args.client_ms,
                server_echo_ms = args.server_echo_ms,
                frame_p95_ms = args.frame_p95_ms,
                actors = samples,
                gate = tostring(args.gate):sub(1, 100),
                presented = tonumber(args.presented),
                missing_brain = tonumber(args.missing_brain),
            }
            ModData.getOrCreate("AKRPedestrianMeasurements").latest = R.receipts
        end
    end
)

-- The companion's permit is authoritative; upstream random encounter candidates stay disabled.
R.clanGetAll = R.clanGetAll or BanditCustom.ClanGetAll
local original = R.clanGetAll
BanditCustom.ClanGetAll = function(...)
    local clans = original(...)
    local n = 0
    for _, clan in pairs(clans) do
        n = n + 1
        if n > 256 then
            return {}
        end
        if clan.spawn then
            clan.spawn.spawnChance = 0
        end
    end
    return clans
end
-- A manual reload between empty events replaces keyed handlers and rebinds the factory.
-- It does not provide generic Java reload or migrate active actors.
if R.initialized then
    initialize()
end

-- Original visibility fixture, limited to the opt-in disposable pedestrian runtime.
local K = require("AKRCore/Core")
local nextCheck = 0
local confirmed = false
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRDevTools.visibility", function()
    if not isServer() or not AKRRuntime or not getServerName():match("^AKR_DayOne_Test_") then
        return
    end
    local now = getTimestampMs()
    if now < nextCheck then
        return
    end
    nextCheck = now + 5000
    local cm = getClimateManager()
    if not cm then
        return
    end
    local fog = cm:getClimateFloat(5)
    fog:setAdminValue(0)
    fog:setEnableAdmin(true)
    if not R.visibilityWeatherStopped then
        cm:transmitServerStopWeather()
        R.visibilityWeatherStopped = true
    end
    if not confirmed and cm:getFogIntensity() == 0 then
        confirmed = true
        print("[AKRDevTools] disposable visibility: server fog=0; weather stopped")
    end
end)
