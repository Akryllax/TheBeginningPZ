local C={schema=1,version="0.2.0",maxResidents=256,maxPlaces=384,maxRoads=1024,
    maxPedestrians=24,maxVehicles=4,maxPhysical=32,maxPlans=32,maxActions=8,
    maxReceipts=128,maxEvents=128,scanOperations=24,workMs=1,
    leaseSeconds=8,visibilitySeconds=2,visibilityMargin=160,workerTimeoutSeconds=5,
    maxPlayers=4,scenarioHours=168,vehicleExecution=false,
    clientManifest="Bandits42.20:Update@2062,Hit@2328,Dead@2395,Body@2601:shield2"}
C.actions={"WAIT","WALK","EAT","REST","WORK","SHOP","SOCIALIZE","SEEK_HELP",
    "FLEE","DRIVE","PARK","ENTER_VEHICLE","EXIT_VEHICLE","TREAT","PATROL","SCAVENGE","SHELTER"}
function C.enabled() return SandboxVars and SandboxVars.LofersScenario and SandboxVars.LofersScenario.Enabled==true end
function C.action(k)
    if type(k)=="number" then return C.actions[k] end
    for _,name in ipairs(C.actions) do if name==k then return k end end
end
return C
