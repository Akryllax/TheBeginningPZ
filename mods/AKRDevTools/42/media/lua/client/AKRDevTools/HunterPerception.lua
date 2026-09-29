-- Owner-side stock perception for zombies the server directed at an Actor (Decision 0006).
-- Stock multiplayer runs zombie AI on the owning client, whose LOS pass only offers its own
-- local player to spotted(). The server steers the target with the stock ZombieControl message,
-- which sets the target but never "sees" it, so the zombie idles. For zombies this client owns
-- and whose target is an Actor (reserved online IDs 4096..4159; four by default), run the stock spotted() the way
-- the LOS pass would: every frame, because stock spotted() adds one frame to targetSeenTime and
-- the stock bite animation waits (Zombie_Idle_Lunge grace) until it exceeds 0.5 s. Stock chance
-- model; never touches health, inventory or actions; bites are still decided on the server.
-- Bounded: pairs are found at 4 Hz (64 zombies / 8 pairs normally; 256 / 128 only in the opt-in crowd test) and spotted per frame.
local K = require("AKRCore/Core")
local core = K.instance()
local H = { last = 0, calls = 0, pairs = {} }
AKRHunterPerception = H
local FIRST = 4096

local function near(z, actor)
    return math.abs(z:getX() - actor:getX()) < 20 and math.abs(z:getY() - actor:getY()) < 20
end

local function discover()
    local encounter = AKREncounterClient
    if encounter and getTimestampMs() - encounter.received > 2500 then
        encounter = nil
    end
    local config = encounter or AKRChaseClient or AKRPoolWatchClient
    local count = config and config.count or 4
    local allowed = {}
    for _, id in ipairs(config and config.hunters or {}) do
        allowed[id] = true
    end
    local watched = config
        and config.epoch
        and config.hunters
        and (encounter ~= nil or #config.hunters > 0)
    local crowd = watched and config.crowd == true and count == 64
    local found = {}
    local list = getCell():getZombieList()
    for i = 0, math.min(list:size(), crowd and 256 or 64) - 1 do
        if #found >= (crowd and 128 or watched and 16 or 8) then
            break
        end
        local z = list:get(i)
        local target = z:getTarget()
        if target and not z:isRemoteZombie() and instanceof(target, "IsoPlayer") then
            local id = target:getOnlineID()
            if
                id >= FIRST
                and id < FIRST + count
                and getPlayerByOnlineID(id) == target
                and (not watched or allowed[z:getOnlineID()])
                and (not encounter or encounter.targets[z:getOnlineID()] == id)
                and near(z, target)
            then
                found[#found + 1] = { z, target }
            end
        end
    end
    H.pairs = found
end

K.Dispatch.on(core.dispatch, "OnTick", "AKRDevTools.hunterPerception", function()
    local stamp = getTimestampMs()
    if stamp - H.last >= 250 then
        H.last = stamp
        discover()
    end
    for _, pair in ipairs(H.pairs) do
        local z, actor = pair[1], pair[2]
        if z:getTarget() == actor and not z:isRemoteZombie() and near(z, actor) then
            z:spotted(actor, false)
            H.calls = H.calls + 1
        end
    end
end)
