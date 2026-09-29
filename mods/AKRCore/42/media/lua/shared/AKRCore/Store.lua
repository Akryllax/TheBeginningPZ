-- Per-module saved state with explicit, stepwise schema migrations.
-- The caller supplies the root table (ModData.getOrCreate("AKRCore") in game).
-- Java references and callbacks must never enter saved state.
local S={}

-- migrations[n] upgrades a state at schema n to schema n+1 and returns (ok, reason).
-- legacy, when given, is adopted once if the module has no state yet (renamed keys).
function S.open(root,module,schema,migrations,create,legacy)
    if type(root)~="table" then return nil,"root_required" end
    if type(schema)~="number" or schema<1 then return nil,"schema_required" end
    root.modules=root.modules or {}
    local slot=root.modules[module]
    if not slot and type(legacy)=="table" then
        slot={schema=legacy.schema or 1,state=legacy.state or legacy,adopted_legacy=true}
        root.modules[module]=slot
    end
    if not slot then
        if type(create)~="function" then return nil,"create_required" end
        slot={schema=schema,state=create()};root.modules[module]=slot
        return slot.state
    end
    if type(slot.schema)~="number" then return nil,"unknown_schema" end
    if slot.schema>schema then return nil,"newer_schema" end
    while slot.schema<schema do
        local step=migrations and migrations[slot.schema]
        if type(step)~="function" then return nil,"missing_migration_"..slot.schema end
        local ok,why=step(slot.state)
        if not ok then return nil,why or ("migration_failed_"..slot.schema) end
        slot.schema=slot.schema+1
    end
    return slot.state
end

return S
