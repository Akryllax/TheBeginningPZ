local C = {
    schema = 2,
    version = "0.2.0",
    maxResidents = 256,
    maxPlaces = 384,
    maxRoads = 1024,
    maxPedestrians = 24,
    maxVehicles = 4,
    maxPhysical = 32,
    maxPlans = 32,
    maxActions = 6,
    maxReceipts = 128,
    maxEvents = 128,
    scanOperations = 24,
    workMs = 1,
    leaseSeconds = 8,
    visibilitySeconds = 2,
    visibilityMargin = 160,
    workerTimeoutSeconds = 5,
    maxPlayers = 4,
    scenarioHours = 168,
    vehicleExecution = false,
    clientManifest = "Bandits@319258424172526049:Update@2067,Hit@2289,Dead@2400,Body@2590:shield2",
}
C.actions = {
    "WAIT",
    "WALK",
    "EAT",
    "REST",
    "WORK",
    "SHOP",
    "SOCIALIZE",
    "SEEK_HELP",
    "FLEE",
    "DRIVE",
    "PARK",
    "ENTER_VEHICLE",
    "EXIT_VEHICLE",
    "TREAT",
    "PATROL",
    "SCAVENGE",
    "SHELTER",
}
function C.enabled()
    return SandboxVars and SandboxVars.AKRScenario and SandboxVars.AKRScenario.Enabled == true
end
function C.action(k)
    if type(k) == "number" then
        return C.actions[k]
    end
    for _, name in ipairs(C.actions) do
        if name == k then
            return k
        end
    end
end
return C
