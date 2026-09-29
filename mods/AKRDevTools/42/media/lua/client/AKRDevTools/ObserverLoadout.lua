local K = require("AKRCore/Core")
local applied, appliedPlayer = nil, nil
local function marked(inventory, key, itemType)
    local items = inventory:getItems()
    for i = 0, math.min(items:size(), 512) - 1 do
        local item = items:get(i)
        if item:getFullType() == itemType and item:getModData().AKRObserverLoadout == key then
            return item
        end
    end
    local item = inventory:AddItem(itemType)
    assert(item, "observer_loadout_item_missing:" .. itemType)
    item:getModData().AKRObserverLoadout = key
    return item
end
K.Dispatch.on(
    K.instance().dispatch,
    "OnServerCommand",
    "AKRDevTools.observerLoadout",
    function(module, command, args)
        if module ~= "AKRDevTools" or command ~= "observerLoadout" or type(args) ~= "table" then
            return
        end
        if
            type(args.epoch) ~= "string"
            or #args.epoch > 128
            or (args.event ~= nil and (type(args.event) ~= "string" or #args.event > 160))
        then
            return
        end
        local known = AKREncounterClient or AKRPedestrianClient
        if not known or args.epoch ~= known.epoch then
            return
        end
        local p = getSpecificPlayer(0)
        if not p or p:getUsername() ~= "akr" or p:getAccessLevel() ~= "admin" then
            return
        end
        local key = args.epoch .. ":" .. (args.event or "session")
        if applied ~= key or appliedPlayer ~= p then
            local inv = p:getInventory()
            local gun = marked(inv, "pistol", "Base.Pistol")
            gun:setContainsClip(true)
            gun:setCurrentAmmoCount(gun:getMaxAmmo())
            gun:setRoundChambered(true)
            gun:setJammed(false)
            local magazine = gun:getMagazineType()
            assert(magazine and magazine ~= "", "observer_magazine_type_missing")
            for i = 1, 2 do
                local clip = marked(inv, "magazine-" .. i, magazine)
                clip:setCurrentAmmoCount(clip:getMaxAmmo())
            end
            marked(inv, "ammo-box", "Base.Bullets9mmBox")
            p:setSecondaryHandItem(nil)
            p:setPrimaryHandItem(gun)
            applied = key
            appliedPlayer = p
            print(
                "[AKRObserverLoadout] loaded "
                    .. gun:getFullType()
                    .. " ammo="
                    .. tostring(gun:getCurrentAmmoCount())
                    .. " chambered="
                    .. tostring(gun:isRoundChambered())
                    .. "; two loaded spare magazines"
            )
        end
        sendClientCommand(
            p,
            "AKRDevTools",
            "observerLoadoutReady",
            { epoch = args.epoch, event = args.event or "session", loaded = true }
        )
    end
)
