-- Current visibility only; an unloaded square in the viewport is unknown, never hidden.
local V = {}
local offsets = { { 0, 0 }, { -1, -1 }, { -1, 1 }, { 1, -1 }, { 1, 1 } }
function V.footprint(x, y, player)
    local loaded, hidden = true, true
    local core = getCore()
    local zoom = core:getZoom(player:getPlayerNum())
    for _, d in ipairs(offsets) do
        local tx, ty = x + d[1], y + d[2]
        local sq = getCell():getGridSquare(math.floor(tx), math.floor(ty), 0)
        local sx = (IsoUtils.XToScreen(tx, ty, 0, 0) - getCameraOffX()) / zoom
        local sy = (IsoUtils.YToScreen(tx, ty, 0, 0) - getCameraOffY()) / zoom
        local on = sx >= -160
            and sy >= -200
            and sx <= core:getScreenWidth() + 160
            and sy <= core:getScreenHeight() + 200
        loaded = loaded and sq ~= nil
        hidden = hidden and (not on or (sq ~= nil and not sq:isCanSee(player:getPlayerNum())))
    end
    return loaded, hidden
end
function V.body(body, player, id, unknown, crowd)
    local entry = {
        id = id,
        present = body ~= nil,
        on_screen = false,
        hidden = true,
        loaded = false,
        visible = false,
    }
    if unknown then
        entry.present = "unknown"
        entry.on_screen = "unknown"
        entry.hidden = false
    end
    if body then
        entry.on_screen = body:isOnScreen()
        entry.x = body:getX()
        entry.y = body:getY()
        if crowd then
            entry.loaded, entry.hidden = V.footprint(entry.x, entry.y, player)
            local sq = body:getSquare()
            entry.line_of_sight = sq ~= nil and sq:isCanSee(player:getPlayerNum())
            entry.visible = entry.on_screen and entry.line_of_sight
        end
    end
    return entry
end
return V
