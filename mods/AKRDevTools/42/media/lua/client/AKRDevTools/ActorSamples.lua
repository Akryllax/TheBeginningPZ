-- Diagnostic only (Decision 0006): how this client renders pooled server Actors, reserved
-- online IDs 4096..4159 (four by default). Wall-clock stamps align with server status captures on the same
-- host. Never writes game state. Bounded: up to 64 Actors, 4 Hz samples, 1 Hz local fear stats.
local K = require("AKRCore/Core")
local core = K.instance()
local S = { last = {}, skips = {}, lastSample = 0, lastFear = 0, seen = false }
AKRActorSamples = S
local FIRST = 4096
K.Dispatch.on(core.dispatch, "OnTick", "AKRDevTools.actorSamples", function()
    local LAST = 4095 + (AKRPoolWatchClient and AKRPoolWatchClient.count or 4)
    local stamp = getTimestampMs()
    local any = false
    for id = FIRST, LAST do
        local actor = getPlayerByOnlineID(id)
        if actor then
            any = true
            local x, y = actor:getX(), actor:getY()
            local last = S.last[id]
            -- A rendered jump over one tile between frames is a visible correction.
            if last and ((x - last.x) ^ 2 + (y - last.y) ^ 2) > 1.0 then
                S.skips[id] = (S.skips[id] or 0) + 1
                print(
                    string.format(
                        "[AKRActorSkip] t=%d id=%d from=%.2f,%.2f to=%.2f,%.2f n=%d",
                        stamp,
                        id,
                        last.x,
                        last.y,
                        x,
                        y,
                        S.skips[id]
                    )
                )
            end
            S.last[id] = { x = x, y = y }
        else
            S.last[id] = nil
        end
    end
    if any and stamp - S.lastSample >= 250 then
        S.lastSample = stamp
        S.sampled = true
        for id = FIRST, LAST do
            local actor = getPlayerByOnlineID(id)
            if actor then
                print(
                    string.format(
                        "[AKRActorSample] t=%d id=%d x=%.3f y=%.3f moving=%s run=%s state=%s skips=%d",
                        stamp,
                        id,
                        actor:getX(),
                        actor:getY(),
                        tostring(actor:isPlayerMoving()),
                        tostring(actor:isRunning()),
                        tostring(actor:getActionStateName()),
                        S.skips[id] or 0
                    )
                )
            end
        end
    end
    -- Zombie replicas near an Actor (hunters): position/state at 4 Hz and per-frame jumps.
    -- Bounded: at most the first 8 zombies within 20 tiles of the first Actor.
    if any then
        local anchor = nil
        for id = FIRST, LAST do
            anchor = anchor or getPlayerByOnlineID(id)
        end
        local list = getCell():getZombieList()
        local seen = 0
        S.zlast = S.zlast or {}
        for i = 0, math.min(list:size(), 64) - 1 do
            local z = list:get(i)
            if seen >= 8 then
                break
            end
            if
                anchor
                and math.abs(z:getX() - anchor:getX()) < 20
                and math.abs(z:getY() - anchor:getY()) < 20
            then
                seen = seen + 1
                local id = z:getOnlineID()
                local zx, zy = z:getX(), z:getY()
                local last = S.zlast[id]
                if last and ((zx - last.x) ^ 2 + (zy - last.y) ^ 2) > 1.0 then
                    print(
                        string.format(
                            "[AKRZombieSkip] t=%d id=%d from=%.2f,%.2f to=%.2f,%.2f",
                            stamp,
                            id,
                            last.x,
                            last.y,
                            zx,
                            zy
                        )
                    )
                end
                S.zlast[id] = { x = zx, y = zy }
                if S.sampled then
                    print(
                        string.format(
                            "[AKRZombieSample] t=%d id=%d x=%.3f y=%.3f state=%s",
                            stamp,
                            id,
                            zx,
                            zy,
                            tostring(z:getActionStateName())
                        )
                    )
                end
            end
        end
    end
    S.sampled = false
    -- Local fear inputs while any Actor is present (and 10 s after), for exclusion evidence.
    if any then
        S.seen = stamp
    end
    if S.seen and stamp - S.seen < 10000 and stamp - S.lastFear >= 1000 then
        S.lastFear = stamp
        local player = getSpecificPlayer(0)
        if player then
            local stats = player:getStats()
            print(
                string.format(
                    "[AKRActorFear] t=%d visibleZombies=%d veryClose=%d panic=%.3f stress=%.3f",
                    stamp,
                    stats:getNumVisibleZombies(),
                    stats:getNumVeryCloseZombies(),
                    stats:get(CharacterStat.PANIC),
                    stats:get(CharacterStat.STRESS)
                )
            )
        end
    end
end)
