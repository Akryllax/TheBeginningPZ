local K = require("AKRCore/Core")
local last = 0
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRResidents.combatPresentation", function()
    if not AKRRuntime or not AKRCombatActions then
        return
    end
    local now = getTimestampMs()
    if now - last < 100 then
        return
    end
    last = now
    local rows = {}
    for slot = 4096, 4099 do
        local row = AKRCombatActions[slot]
        if row and now - row.at <= 3000 then
            rows[#rows + 1] = row
        end
    end
    if #rows > 0 then
        sendServerCommand(
            "AKRResidents",
            "combatPresentation",
            { epoch = AKRRuntime.epoch, rows = rows }
        )
    end
end)
