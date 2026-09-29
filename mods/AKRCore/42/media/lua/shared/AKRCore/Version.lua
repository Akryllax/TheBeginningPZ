-- Semantic versions for module APIs and saved-state contracts. Pure Lua 5.1.
local V = {}

function V.parse(text)
    if type(text) ~= "string" then
        return nil
    end
    local a, b, c = string.match(text, "^(%d+)%.(%d+)%.(%d+)$")
    if not a then
        return nil
    end
    return { major = tonumber(a), minor = tonumber(b), patch = tonumber(c), text = text }
end

-- A provider satisfies a consumer when majors match and it is at least as new.
function V.satisfies(provided, major, minimum)
    local p = V.parse(provided)
    if not p then
        return false, "invalid_version"
    end
    if p.major ~= major then
        return false, "major_mismatch"
    end
    if minimum then
        local m = V.parse(minimum)
        if not m or m.major ~= major then
            return false, "invalid_minimum"
        end
        if p.minor < m.minor or (p.minor == m.minor and p.patch < m.patch) then
            return false, "too_old"
        end
    end
    return true
end

return V
