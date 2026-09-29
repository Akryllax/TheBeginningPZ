-- Original ordinary-zombie pursuit. Upstream synthetic bites cannot select
-- scenario residents; the server validates and commits these contact reports.
local C = require("AKRScenario/Config")
local G = require("AKRScenario/ClientGate")
local A = { window = 0, used = 0, serial = 0, checks = 0, milliseconds = 0 }
local function update(attacker)
    local l = AKRScenarioClient
    if
        not C.enabled()
        or not l
        or not l.epoch
        or l.paused
        or not G.ready()
        or attacker:isDead()
        or attacker:isRemoteZombie()
        or attacker:getVariableBoolean("Bandit")
    then
        return
    end
    local p = getSpecificPlayer(0)
    if not p then
        return
    end
    local owner = attacker:getOwnerPlayer()
    if owner and owner:getOnlineID() ~= p:getOnlineID() then
        return
    end
    local now = getTimestampMs() / 1000
    local md = attacker:getModData()
    if now - (md.AKRContactCheck or 0) < 1 then
        return
    end
    if now - A.window >= 0.1 then
        A.window = now
        A.used = 0
    end
    if A.used >= 4 then
        return
    end
    A.used = A.used + 1
    md.AKRContactCheck = now
    local best, actor, distance
    local count = 0
    for id, r in pairs(l.residents) do
        count = count + 1
        if count > C.maxPhysical then
            break
        end
        local z = l.actors[id]
        if
            z
            and r.lifecycle == "active"
            and not z:isDead()
            and not z:getVehicle()
            and math.abs(z:getZ() - attacker:getZ()) < 0.3
        then
            local d = (z:getX() - attacker:getX()) ^ 2 + (z:getY() - attacker:getY()) ^ 2
            if d < 400 and (not distance or d < distance) and attacker:CanSee(z) then
                best = r
                actor = z
                distance = d
            end
        end
    end
    if not actor then
        return
    end
    local target = attacker:getTarget()
    if target and instanceof(target, "IsoPlayer") then
        local d = (target:getX() - attacker:getX()) ^ 2 + (target:getY() - attacker:getY()) ^ 2
        if d < distance then
            return
        end
    end
    attacker:setNoTeeth(true)
    attacker:setVariable("NoLungeAttack", true)
    attacker:setTarget(actor)
    attacker:pathToCharacter(actor)
    if
        distance < 0.64
        and now - (md.AKRLastContact or 0) > 2
        and attacker:isFacingObject(actor, 0.3)
    then
        local a, b = attacker:getSquare(), actor:getSquare()
        if not a or not b or a:isSomethingTo(b) then
            return
        end
        md.AKRLastContact = now
        A.serial = A.serial + 1
        attacker:setBumpType(actor:isProne() and "BiteLow" or "Bite")
        sendClientCommand(p, "AKRScenario", "contact", {
            epoch = l.epoch,
            resident_id = best.id,
            generation = best.generation,
            lease_epoch = best.lease_epoch,
            sequence = A.serial,
            attacker_id = attacker:getOnlineID(),
            attacker_outfit = attacker:getPersistentOutfitID(),
        })
    end
end
Events.OnZombieUpdate.Add(function(z)
    local before = getTimestampMs()
    local ok, err = pcall(update, z)
    A.checks = A.checks + 1
    A.milliseconds = A.milliseconds + (getTimestampMs() - before)
    if not ok and getTimestampMs() - (A.lastError or 0) > 30000 then
        A.lastError = getTimestampMs()
        print("[AKRScenario] contact adapter: " .. tostring(err))
    end
end)
return A
