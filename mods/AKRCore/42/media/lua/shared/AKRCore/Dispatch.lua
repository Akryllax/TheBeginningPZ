-- Permanent game-event dispatcher. Each game event is subscribed once; modules register
-- keyed handlers, so reloading a module replaces its handler instead of stacking another.
-- A failing handler is isolated and counted; other modules keep running.
local D = {}

function D.new(events, log)
    return {
        events = events,
        log = log or print,
        subscribed = {},
        handlers = {},
        order = {},
        failures = {},
        warnedAt = {},
    }
end

local function run(dispatch, name, ...)
    local order = dispatch.order[name]
    for i = 1, #order do
        local key = order[i]
        local fn = dispatch.handlers[name][key]
        if fn then
            local ok, err = pcall(fn, ...)
            if not ok then
                local id = name .. ":" .. key
                dispatch.failures[id] = (dispatch.failures[id] or 0) + 1
                if dispatch.failures[id] == 1 or dispatch.failures[id] % 100 == 0 then
                    dispatch.log(
                        "[AKRCore] "
                            .. id
                            .. " failed ("
                            .. dispatch.failures[id]
                            .. "): "
                            .. tostring(err)
                    )
                end
            end
        end
    end
end

function D.on(dispatch, name, key, fn)
    if type(key) ~= "string" or key == "" then
        return false, "key_required"
    end
    if type(fn) ~= "function" then
        return false, "function_required"
    end
    if not dispatch.subscribed[name] then
        local event = dispatch.events and dispatch.events[name]
        if not event or type(event.Add) ~= "function" then
            return false, "unknown_event"
        end
        dispatch.handlers[name] = {}
        dispatch.order[name] = {}
        event.Add(function(...)
            run(dispatch, name, ...)
        end)
        dispatch.subscribed[name] = true
    end
    if not dispatch.handlers[name][key] then
        local order = dispatch.order[name]
        order[#order + 1] = key
    end
    dispatch.handlers[name][key] = fn
    return true
end

function D.off(dispatch, name, key)
    local handlers = dispatch.handlers[name]
    if not handlers or not handlers[key] then
        return false
    end
    handlers[key] = nil
    local order = dispatch.order[name]
    for i = #order, 1, -1 do
        if order[i] == key then
            table.remove(order, i)
        end
    end
    return true
end

return D
