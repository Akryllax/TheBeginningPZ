-- Wandering Zombies exposes a global validity predicate used before steering.
-- Exclude Bandits without editing either dependency or replacing event handlers.
if not isClient() then
    return
end
local installed = false
local function install()
    if installed or type(wzIsValidZombie) ~= "function" then
        return
    end
    local original = wzIsValidZombie
    wzIsValidZombie = function(zombie)
        if zombie and zombie:getVariableBoolean("Bandit") then
            return false
        end
        return original(zombie)
    end
    installed = true
    print("[AKRStoryteller] Wandering Zombies excludes Bandit NPCs.")
end
Events.OnGameStart.Add(install)
