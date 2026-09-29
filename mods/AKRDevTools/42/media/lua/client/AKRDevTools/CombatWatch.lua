-- Disposable fixture control and bounded observations; native hit packets own damage/swing.
local K = require("AKRCore/Core")
local config, last, received, swing, reaction = nil, 0, 0, false, false

K.Dispatch.on(
    K.instance().dispatch,
    "OnServerCommand",
    "AKRDevTools.combatConfig",
    function(module, command, args)
        if module ~= "AKRDevTools" or command ~= "combatConfig" or type(args) ~= "table" then
            return
        end
        if type(args.epoch) ~= "string" or #args.epoch > 80 or args.actor ~= 4096 then
            return
        end
        if type(args.phase) ~= "number" or args.phase < 0 or args.phase > 9 then
            return
        end
        if
            args.target ~= nil
            and (type(args.target) ~= "number" or args.target < 0 or args.target > 32767)
        then
            return
        end
        if not config or config.epoch ~= args.epoch then
            swing = false
            reaction = false
        end
        config = args
        received = getTimestampMs()
    end
)
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRDevTools.combatWatch", function()
    local now = getTimestampMs()
    if not config or now - last < 100 or now - received > 2500 then
        return
    end
    local p = getSpecificPlayer(0)
    if not p then
        return
    end
    last = now
    local a = getPlayerByOnlineID(config.actor)
    local z = nil
    if config.target then
        local zombies = getCell():getZombieList()
        for i = 0, math.min(zombies:size(), 256) - 1 do
            local candidate = zombies:get(i)
            if candidate:getOnlineID() == config.target then
                z = candidate
                break
            end
        end
    end
    local ast = a and a:getCurrentStateName() or "absent"
    local zst = z and z:getCurrentStateName() or "absent"
    -- This is only an animation flag, not proof of visible motion. AnimationPlayer's
    -- track API is not exposed to ordinary Lua clients; human confirmation is required.
    if a and a:getVariableBoolean("AttackAnim") then
        swing = true
    end
    if z and (zst:lower():find("hit") or zst:lower():find("fall") or z:isKnockedDown()) then
        reaction = true
    end
    -- Stationary target is explicit fixture setup, never a civilian AI/health authority.
    if z and config.phase <= 5 and not AKREncounterClient and not z:isRemoteZombie() then
        z:setUseless(true)
        z:setTarget(nil)
    end
    sendClientCommand(p, "AKRDevTools", "combatView", {
        epoch = config.epoch,
        actor_present = a ~= nil,
        target_present = z ~= nil,
        attack_animation_flag_seen = swing,
        saw_reaction = reaction,
        actor_state = ast,
        target_state = zst,
        target_health = z and z:getHealth() or -1,
    })
end)

-- Presentation follows stock replicated action/hit callbacks. Never applies damage,
-- never emits a second network sound, and never touches ordinary player attacks.
local swingPlayed, impactPlayed = false, false
local soundEpoch = nil
local function watchedAttacker(a)
    if config and config.autonomous then
        return false
    end -- AKRResidents owns repeated-action presentation.
    if not config or getTimestampMs() - received > 2500 or not a then
        return false
    end
    if config.phase < 4 or config.phase > 6 or a:getOnlineID() ~= config.actor then
        return false
    end
    if a ~= getPlayerByOnlineID(config.actor) or a == getSpecificPlayer(0) then
        return false
    end
    if soundEpoch ~= config.epoch then
        soundEpoch = config.epoch
        swingPlayed = false
        impactPlayed = false
    end
    return true
end
K.Dispatch.on(
    K.instance().dispatch,
    "OnWeaponSwing",
    "AKRDevTools.combatSwingAudio",
    function(a, weapon)
        if not watchedAttacker(a) or swingPlayed or not weapon then
            return
        end
        swingPlayed = true
        local sound = weapon:getSwingSound()
        local handle = sound and a:playSoundLocal(sound) or 0
        a:playerVoiceSound("MeleeAttack")
        print("[AKRCombatAudio] swing=" .. tostring(sound) .. " handle=" .. tostring(handle))
    end
)
K.Dispatch.on(
    K.instance().dispatch,
    "OnWeaponHitCharacter",
    "AKRDevTools.combatImpactAudio",
    function(a, target, weapon, damage)
        if
            not watchedAttacker(a)
            or impactPlayed
            or not target
            or not weapon
            or target:getOnlineID() ~= config.target
        then
            return
        end
        impactPlayed = true
        a:setMeleeHitSurface("Body")
        local sound = weapon:getZombieHitSound()
        local handle = sound and a:playSoundLocal(sound) or 0
        print("[AKRCombatAudio] impact=" .. tostring(sound) .. " handle=" .. tostring(handle))
    end
)
