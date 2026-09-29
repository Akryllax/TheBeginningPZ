-- Versioned module APIs. Modules talk to each other only through provided tables,
-- never through each other's globals or saved state.
local V=require "AKRCore/Version"
local R={}

function R.new() return {apis={},order={}} end

-- Re-providing the same major (a Lua reload) replaces the table in place of adding one.
function R.provide(registry,name,version,api)
    local v=V.parse(version)
    if type(name)~="string" or name=="" then return false,"name_required" end
    if not v then return false,"invalid_version" end
    if type(api)~="table" then return false,"api_table_required" end
    local key=name.."@"..v.major
    if not registry.apis[key] then registry.order[#registry.order+1]=key end
    registry.apis[key]={name=name,version=version,major=v.major,api=api}
    return true
end

function R.require(registry,name,major,minimum)
    local entry=registry.apis[name.."@"..tostring(major)]
    if not entry then
        for _,key in ipairs(registry.order) do
            if registry.apis[key].name==name then return nil,"major_mismatch" end
        end
        return nil,"missing"
    end
    local ok,why=V.satisfies(entry.version,major,minimum)
    if not ok then return nil,why end
    return entry.api
end

function R.list(registry)
    local out={}
    for _,key in ipairs(registry.order) do
        local e=registry.apis[key];out[#out+1]={name=e.name,version=e.version}
    end
    return out
end

return R
