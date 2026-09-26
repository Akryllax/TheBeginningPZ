local C = {
    version = "0.2.0", schema = 1, mode = "observe",
    -- Enable only after the documented two-client encounter validation.
    npcIntegrationValidated = false,
    cellSize = 32, maxCells = 512, maxItems = 8192, maxSignals = 2048,
    maxTelemetryCells = 64, maxDecisions = 32, maxOnlinePlayers = 4,
    maxOperations = 32, updateMilliseconds = 1, updateIntervalMs = 100,
    presenceIntervalMs = 5000, telemetryIntervalMs = 5000,
    inventoryDepth = 8, maxInventoryItems = 4096, maxQueue = 16,
    dwellHalfLifeHours = 72, evidenceHalfLifeHours = 72,
    hostileGraceHours = 24, hostileCooldownHours = 6, recoveryHours = 24,
    maxNPCs = 24, maxEncounterNPCs = 8, maxMajorEvents = 1,
    budgetCap = 24, budgetPerHour = 1, decisionIntervalHours = 1,
    eventLifetimeHours = 2,
}
return C
