-- Narrow compatibility guard, written against Bandits 42.20. No upstream code
-- is copied or replaced. Leave engine registration and normal NPC AI untouched.
local B = { locked = false, reason = "bandits_not_loaded", maxClans = 256 }

function B.suppressNativeSchedule(clans)
    if type(clans) ~= "table" then
        B.reason = "invalid_clan_registry"
        return {}
    end
    local count = 0
    for _, clan in pairs(clans) do
        count = count + 1
        if count > B.maxClans then
            B.reason = "clan_limit_exceeded"
            return {} -- neither native scheduler sees a partially guarded registry
        end
        if type(clan.spawn) == "table" then
            clan.spawn.spawnChance = 0
        end
    end
    B.locked = true
    B.reason = "native_schedules_suppressed"
    return clans
end

function B.install()
    if B.installed then
        return true
    end
    if not BanditCustom or type(BanditCustom.ClanGetAll) ~= "function" then
        B.reason = "clan_api_missing"
        return false
    end
    local original = BanditCustom.ClanGetAll
    -- Both regular encounters and the abstract wanderer orchestrator read this
    -- exact accessor each time. Also handles a clan edit/reload after startup.
    BanditCustom.ClanGetAll = function()
        return B.suppressNativeSchedule(original())
    end
    B.installed = true
    B.suppressNativeSchedule(original())
    return true
end

function B.status()
    return B.locked and "observing_native_spawns_disabled" or B.reason
end

return B
