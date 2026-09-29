local K = require("AKRCore/Core")
local G = require("AKRDevTools/Gate")
local core = K.instance()
local C = {
    outfits = {},
    actors = {},
    epoch = nil,
    lastHello = 0,
    lastSample = 0,
    frames = {},
    frameCursor = 0,
    lastFrame = nil,
    scanCursor = 0,
    lastScan = 0,
    presented = 0,
    missingBrain = 0,
}
AKRPedestrianClient = C
local function now()
    return getTimestampMs()
end
local function send(command, args)
    local player = getSpecificPlayer(0)
    if player then
        sendClientCommand(player, "AKRDevTools", command, args)
    end
end
K.Dispatch.on(
    core.dispatch,
    "OnServerCommand",
    "AKRDevTools.snapshot",
    function(module, command, args)
        if module ~= "AKRDevTools" or command ~= "snapshot" then
            return
        end
        if type(args) ~= "table" then
            return
        end
        if type(args.actors) ~= "table" or #args.actors > 4 then
            return
        end
        if C.epoch ~= args.epoch then
            C.actors = {}
        end
        C.epoch = args.epoch
        C.serverEcho = args.server_ms
        C.outfits = {}
        for _, a in ipairs(args.actors) do
            local outfit = tonumber(a.outfit)
            if outfit and type(a.id) == "string" then
                C.outfits[outfit] = a
            end
        end
    end
)
local function present(actor)
    if not G.ready() then
        return
    end
    local key = BanditUtils.GetCharacterID(actor)
    local entry = C.outfits[key]
    if not entry or actor:getOnlineID() ~= entry.online_id then
        return
    end
    C.actors[entry.id] = actor
    local md = actor:getModData()
    local stamp = now()
    local refresh = md.AKRPresented ~= entry.id or md.AKRPresentedEpoch ~= C.epoch
    -- Outfit assets can finish dressing after our first callback. Reconcile the
    -- actual visual state at a bounded cadence, not just a one-time marker.
    if not refresh and stamp - (md.AKRVisualCheckAt or 0) >= 500 then
        md.AKRVisualCheckAt = stamp
        local visual = actor:getHumanVisual()
        refresh = visual
            and (
                visual:getSkinTexture() ~= md.AKRExpectedSkin
                or actor:getItemVisuals():size() ~= md.AKRExpectedClothes
            )
    end
    if refresh then
        local cluster = GetBanditClusterData(key)
        local brain = cluster and cluster[key]
        if not brain then
            C.missingBrain = C.missingBrain + 1
            return
        end
        -- Cosmetic initialization only: the upstream ApplyVisuals also heals and
        -- edits death loot, so use its body helper and our bounded visual wardrobe.
        if not actor:getHumanVisual() then
            return
        end
        actor:setVariable("Bandit", true)
        actor:setFemaleEtc(brain.female)
        local body = actor:getHumanVisual()
        body:removeBlood()
        body:removeDirt()
        body:getBodyVisuals():clear()
        local visuals = actor:getItemVisuals()
        visuals:clear()
        local count = 0
        for _, itemType in pairs(brain.clothing or {}) do
            if count >= 16 then
                break
            end
            if type(itemType) == "string" then
                local visual = ItemVisual.new()
                visual:setItemType(itemType)
                visual:setClothingItemName(itemType)
                visuals:add(visual)
                count = count + 1
            end
        end
        Bandit.ApplyBody(actor, brain)
        actor:resetModelNextFrame()
        md.AKRExpectedSkin = Bandit.GetSkinTexture(brain.female, brain.skin or 1)
        md.AKRExpectedClothes = count
        md.AKRVisualCheckAt = stamp
        md.AKRPresented = entry.id
        md.AKRPresentedEpoch = C.epoch
        md.AKRPedestrian = { id = entry.id, epoch = C.epoch }
        actor:setVariable("Bandit", true)
        actor:setVariable("BanditWalkType", "Walk")
        actor:setWalkType("Walk")
        C.presented = C.presented + 1
        print(
            "[AKRDevTools] civilian visual repair "
                .. entry.id
                .. " online="
                .. entry.online_id
                .. " skin="
                .. tostring(actor:getHumanVisual():getSkinTexture())
                .. " clothes="
                .. count
        )
    end
    Bandit.SurpressZombieSounds(actor)
    -- No path updates, health/inventory writes or action completion on replicas.
end
K.Dispatch.on(core.dispatch, "OnZombieUpdate", "AKRDevTools.present", present)
-- Remote network actors may skip OnZombieUpdate; discover them incrementally.
-- Scan at most 32 loaded actors per 100ms, never a whole population per tick.
K.Dispatch.on(core.dispatch, "OnTick", "AKRDevTools.discover", function()
    local stamp = now()
    if not C.epoch or stamp - C.lastScan < 100 then
        return
    end
    C.lastScan = stamp
    if not G.ready() then
        return
    end
    local active = false
    for _ in pairs(C.outfits) do
        active = true
        break
    end
    if not active then
        return
    end
    local cell = getCell()
    local list = cell and cell:getZombieList()
    if not list or list:size() == 0 then
        return
    end
    for i = 1, math.min(32, list:size()) do
        C.scanCursor = C.scanCursor % list:size()
        present(list:get(C.scanCursor))
        C.scanCursor = C.scanCursor + 1
    end
end)
K.Dispatch.on(core.dispatch, "OnPostRender", "AKRDevTools.frames", function()
    local stamp = now()
    if C.lastFrame then
        C.frameCursor = C.frameCursor % 256 + 1
        C.frames[C.frameCursor] = stamp - C.lastFrame
    end
    C.lastFrame = stamp
end)
K.Dispatch.on(core.dispatch, "OnTick", "AKRDevTools.samples", function()
    local stamp = now()
    if stamp - (C.lastDiagnostic or 0) > 5000 then
        C.lastDiagnostic = stamp
        local diagnostic = G.status()
            .. ";epoch="
            .. tostring(C.epoch)
            .. ";presented="
            .. C.presented
            .. ";missingBrain="
            .. C.missingBrain
        if C.diagnostic ~= diagnostic then
            print("[AKRDevTools] presentation " .. diagnostic)
            C.diagnostic = diagnostic
        end
    end
    if not C.epoch then
        if stamp - C.lastHello > 2000 then
            C.lastHello = stamp
            send("hello", {})
        end
        return
    end
    if stamp - C.lastSample < 1000 then
        return
    end
    C.lastSample = stamp
    local samples = {}
    local live = {}
    for _, entry in pairs(C.outfits) do
        live[entry.id] = true
        local actor = C.actors[entry.id]
        if actor then
            local visual = actor:getHumanVisual()
            local skin = visual and visual:getSkinTexture() or "missing"
            local clothes = actor:getItemVisuals():size()
            samples[#samples + 1] = {
                id = entry.id,
                x = actor:getX(),
                y = actor:getY(),
                action = entry.action,
                skin = skin,
                clothes = clothes,
            }
            local diagnostic = tostring(skin) .. ":" .. clothes
            if actor:getModData().AKRVisualDiagnostic ~= diagnostic then
                actor:getModData().AKRVisualDiagnostic = diagnostic
                print(
                    "[AKRDevTools] observed replica " .. entry.id .. " skin:clothes=" .. diagnostic
                )
            end
        end
    end
    for id in pairs(C.actors) do
        if not live[id] then
            C.actors[id] = nil
        end
    end
    local frames = {}
    for i, v in ipairs(C.frames) do
        frames[i] = v
    end
    table.sort(frames)
    send(
        "sample",
        {
            epoch = C.epoch,
            client_ms = stamp,
            server_echo_ms = C.serverEcho,
            actors = samples,
            gate = G.status(),
            presented = C.presented,
            missing_brain = C.missingBrain,
            frame_p95_ms = frames[math.max(1, math.ceil(#frames * 0.95))] or 0,
        }
    )
end)
ZombiePrograms.AKRRemoteCivilian = {}
ZombiePrograms.AKRRemoteCivilian.Prepare = function()
    return { status = true, next = "Main", tasks = {} }
end
ZombiePrograms.AKRRemoteCivilian.Main = function()
    return { status = true, next = "Main", tasks = {} }
end
