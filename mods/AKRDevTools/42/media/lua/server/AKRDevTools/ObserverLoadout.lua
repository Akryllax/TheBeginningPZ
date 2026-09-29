-- Explicit user-requested test loadout, only akr on disposable worlds under AKRRuntime.
local K = require("AKRCore/Core")
local last = 0
local function event()
    return AKREncounterWatch
            and AKREncounterWatch.epoch == AKRRuntime.epoch
            and AKREncounterWatch.event
        or "session"
end
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRDevTools.observerLoadout", function()
    if not AKRRuntime or not getServerName():match("^AKR_DayOne_Test_") then
        return
    end
    local now = getTimestampMs()
    if now - last < 2000 then
        return
    end
    last = now
    local players = getOnlinePlayers()
    for i = 0, math.min(players:size(), 16) - 1 do
        local p = players:get(i)
        local ack = AKRObserverLoadoutReady
        if
            p:getUsername() == "akr"
            and p:getAccessLevel() == "admin"
            and (
                not ack
                or ack.player ~= p
                or ack.epoch ~= AKRRuntime.epoch
                or ack.event ~= event()
            )
        then
            sendServerCommand(
                p,
                "AKRDevTools",
                "observerLoadout",
                { epoch = AKRRuntime.epoch, event = event() }
            )
        end
    end
end)
K.Dispatch.on(
    K.instance().dispatch,
    "OnClientCommand",
    "AKRDevTools.observerLoadoutReady",
    function(module, command, player, args)
        if module ~= "AKRDevTools" or command ~= "observerLoadoutReady" or not AKRRuntime then
            return
        end
        if
            not getServerName():match("^AKR_DayOne_Test_")
            or player:getUsername() ~= "akr"
            or player:getAccessLevel() ~= "admin"
        then
            return
        end
        if
            type(args) ~= "table"
            or args.epoch ~= AKRRuntime.epoch
            or args.event ~= event()
            or args.loaded ~= true
        then
            return
        end
        AKRObserverLoadoutReady = { player = player, epoch = args.epoch, event = args.event }
        print(
            "[AKRObserverLoadout] akr confirmed loaded 9mm pistol and spare magazines for "
                .. args.event
        )
    end
)
