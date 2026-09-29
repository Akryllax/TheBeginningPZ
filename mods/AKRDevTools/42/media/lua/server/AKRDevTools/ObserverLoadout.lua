-- Test loadout for the explicit spectator roster, on disposable worlds only.
local K = require("AKRCore/Core")
local last = 0
local function event()
    return AKREncounterWatch
            and AKREncounterWatch.epoch == AKRRuntime.epoch
            and AKREncounterWatch.event
        or "session"
end
local function authorized(p)
    local roster = AKREncounterWatch and AKREncounterWatch.viewers
    if roster then
        return roster[p:getUsername()] ~= nil
    end
    return p:getUsername() == "akr" and p:getAccessLevel() == "admin"
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
        AKRObserverLoadoutReports = AKRObserverLoadoutReports or {}
        local ack = AKRObserverLoadoutReports[p:getUsername()]
        if
            authorized(p)
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
        if not getServerName():match("^AKR_DayOne_Test_") or not authorized(player) then
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
        AKRObserverLoadoutReports = AKRObserverLoadoutReports or {}
        AKRObserverLoadoutReports[player:getUsername()] =
            { player = player, epoch = args.epoch, event = args.event }
        if player:getUsername() == "akr" then
            AKRObserverLoadoutReady = AKRObserverLoadoutReports.akr
        end
        print(
            "[AKRObserverLoadout] "
                .. player:getUsername()
                .. " confirmed loaded 9mm pistol and spare magazines for "
                .. args.event
        )
    end
)
