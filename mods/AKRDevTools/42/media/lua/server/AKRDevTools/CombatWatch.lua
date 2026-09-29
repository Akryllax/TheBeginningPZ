local K = require("AKRCore/Core")
local last = 0
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRDevTools.combatConfig", function()
    if not AKRRuntime or not AKRCombatWatch or AKRCombatWatch.epoch ~= AKRRuntime.epoch then
        return
    end
    if not getServerName():match("^AKR_DayOne_Test_") then
        return
    end
    local now = getTimestampMs()
    if now - last < 500 then
        return
    end
    last = now
    sendServerCommand("AKRDevTools", "combatConfig", AKRCombatWatch)
end)
K.Dispatch.on(
    K.instance().dispatch,
    "OnClientCommand",
    "AKRDevTools.combatView",
    function(module, command, player, args)
        if
            module ~= "AKRDevTools"
            or command ~= "combatView"
            or not AKRCombatWatch
            or not AKRRuntime
        then
            return
        end
        if not getServerName():match("^AKR_DayOne_Test_") or player:getUsername() ~= "akr" then
            return
        end
        if type(args) ~= "table" or args.epoch ~= AKRCombatWatch.epoch then
            return
        end
        local now = getTimestampMs()
        if AKRCombatReport and now - AKRCombatReport.received_ms < 80 then
            return
        end
        for _, key in ipairs({
            "actor_present",
            "target_present",
            "attack_animation_flag_seen",
            "saw_reaction",
        }) do
            if type(args[key]) ~= "boolean" then
                return
            end
        end
        AKRCombatReport = {
            epoch = args.epoch,
            received_ms = now,
            actor_present = args.actor_present,
            target_present = args.target_present,
            attack_animation_flag_seen = args.attack_animation_flag_seen,
            saw_reaction = args.saw_reaction,
            actor_state = tostring(args.actor_state):sub(1, 80),
            target_state = tostring(args.target_state):sub(1, 80),
            target_health = tonumber(args.target_health),
        }
    end
)
