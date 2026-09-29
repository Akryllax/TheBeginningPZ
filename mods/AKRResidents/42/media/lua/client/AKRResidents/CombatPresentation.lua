-- Native hit callbacks provide presentation only; server combat owns all effects.
local K = require("AKRCore/Core")
local records = {}
K.Dispatch.on(
    K.instance().dispatch,
    "OnServerCommand",
    "AKRResidents.combatPresentation",
    function(module, command, args)
        if module ~= "AKRResidents" or command ~= "combatPresentation" or type(args) ~= "table" then
            return
        end
        if
            type(args.epoch) ~= "string"
            or #args.epoch > 128
            or type(args.rows) ~= "table"
            or #args.rows > 4
        then
            return
        end
        for _, row in ipairs(args.rows) do
            if
                type(row.actor) == "number"
                and row.actor >= 4096
                and row.actor <= 4099
                and row.actor == math.floor(row.actor)
                and type(row.target) == "number"
                and row.target >= 0
                and row.target <= 32767
                and type(row.action) == "string"
                and #row.action <= 200
            then
                local old = records[row.actor]
                if not old or old.action ~= row.action or old.epoch ~= args.epoch then
                    old = { action = row.action, epoch = args.epoch }
                    records[row.actor] = old
                end
                old.target = row.target
                old.at = getTimestampMs()
            end
        end
    end
)
local function record(a)
    if not a or a == getSpecificPlayer(0) then
        return
    end
    local r = records[a:getOnlineID()]
    if r and getTimestampMs() - r.at <= 3000 and a == getPlayerByOnlineID(a:getOnlineID()) then
        return r
    end
end
K.Dispatch.on(K.instance().dispatch, "OnWeaponSwing", "AKRResidents.swingAudio", function(a, weapon)
    local r = record(a)
    if not r or r.swing or not weapon then
        return
    end
    r.swing = true
    local sound = weapon:getSwingSound()
    if sound then
        a:playSoundLocal(sound)
    end
    a:playerVoiceSound("MeleeAttack")
end)
K.Dispatch.on(
    K.instance().dispatch,
    "OnWeaponHitCharacter",
    "AKRResidents.impactAudio",
    function(a, target, weapon)
        local r = record(a)
        if not r or r.hit or not target or not weapon or target:getOnlineID() ~= r.target then
            return
        end
        r.hit = true
        a:setMeleeHitSurface("Body")
        local sound = weapon:getZombieHitSound()
        if sound then
            a:playSoundLocal(sound)
        end
    end
)
