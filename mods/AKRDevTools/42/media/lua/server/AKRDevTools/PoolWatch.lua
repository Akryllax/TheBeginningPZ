-- Disposable diagnostic input: may delay recycling, never authorize damage or movement.
local K = require("AKRCore/Core")
AKRPoolWatch = {}
local function finite(n)
    return type(n) == "number" and n == n and math.abs(n) < 100000
end
local function observation(v)
    return type(v) == "boolean" or v == "unknown"
end
K.Dispatch.on(
    K.instance().dispatch,
    "OnClientCommand",
    "AKRDevTools.poolWatch",
    function(module, command, player, args)
        if module ~= "AKRDevTools" or command ~= "poolView" or not AKRRuntime then
            return
        end
        if
            not getServerName():match("^AKR_DayOne_Test_")
            or type(args) ~= "table"
            or args.epoch ~= AKRRuntime.epoch
        then
            return
        end
        local count = AKRWatched and AKRWatched.actor_count or 4
        if count ~= 4 and count ~= 32 and count ~= 64 then
            return
        end
        if type(args.actors) ~= "table" or #args.actors ~= count then
            return
        end
        local id = player:getOnlineID()
        local now = getTimestampMs()
        local old = AKRPoolWatch[id]
        if old and now - old.received_ms < 150 then
            return
        end
        local crowd = AKRWatched and AKRWatched.hunter_limit == 128 and count == 64
        if crowd then
            if
                args.wave ~= AKRWatched.wave
                or not finite(args.observer_x)
                or not finite(args.observer_y)
            then
                return
            end
            if
                type(args.stage) ~= "table"
                or type(args.stage.loaded) ~= "boolean"
                or type(args.stage.hidden) ~= "boolean"
            then
                return
            end
        end
        local actors = {}
        for i = 1, count do
            local a = args.actors[i]
            if
                type(a) ~= "table"
                or a.id ~= 4095 + i
                or type(a.present) ~= "boolean"
                or type(a.on_screen) ~= "boolean"
            then
                return
            end
            if
                crowd
                and (
                    type(a.hidden) ~= "boolean"
                    or type(a.loaded) ~= "boolean"
                    or type(a.visible) ~= "boolean"
                )
            then
                return
            end
            actors[a.id] = {
                present = a.present,
                on_screen = a.on_screen,
                hidden = a.hidden,
                loaded = a.loaded,
                visible = a.visible,
                female = a.female == true,
                x = tonumber(a.x),
                y = tonumber(a.y),
                name = tostring(a.name):sub(1, 80),
                clothes = tonumber(a.clothes),
            }
        end
        local hunterIds = AKRWatched and AKRWatched.hunters or {}
        local hunts = {}
        if #hunterIds > 0 then
            if
                type(args.hunters) ~= "table"
                or #args.hunters ~= #hunterIds
                or #hunterIds > (crowd and 128 or 16)
            then
                return
            end
            for i, hid in ipairs(hunterIds) do
                local h = args.hunters[i]
                if type(h) ~= "table" or h.id ~= hid then
                    return
                end
                if not observation(h.present) or not observation(h.on_screen) then
                    return
                end
                if
                    crowd
                    and (
                        type(h.hidden) ~= "boolean"
                        or type(h.loaded) ~= "boolean"
                        or type(h.visible) ~= "boolean"
                    )
                then
                    return
                end
                hunts[hid] = {
                    present = h.present,
                    on_screen = h.on_screen,
                    hidden = h.hidden,
                    loaded = h.loaded,
                    visible = h.visible,
                    local_owner = h.local_owner == true,
                }
            end
        end
        AKRPoolWatch[id] = {
            epoch = AKRRuntime.epoch,
            received_ms = now,
            actors = actors,
            hunters = hunts,
            observer_x = args.observer_x,
            observer_y = args.observer_y,
            stage = crowd and { loaded = args.stage.loaded, hidden = args.stage.hidden } or nil,
        }
    end
)

local lastConfig = 0
K.Dispatch.on(K.instance().dispatch, "OnTick", "AKRDevTools.poolConfig", function()
    if not AKRWatched or not AKRRuntime or AKRWatched.epoch ~= AKRRuntime.epoch then
        return
    end
    if not getServerName():match("^AKR_DayOne_Test_") then
        return
    end
    local now = getTimestampMs()
    if now - lastConfig < 1000 then
        return
    end
    lastConfig = now
    sendServerCommand("AKRDevTools", "poolConfig", AKRWatched)
end)
