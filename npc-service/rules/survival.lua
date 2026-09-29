return function(c, d)
    if
        c.phase ~= "collapse"
        and c.phase ~= "survival"
        and c.phase ~= "aftermath"
        and c.phase ~= "shortages"
        and c.phase ~= "disruption"
        and c.phase ~= "evacuation"
    then
        return d
    end
    if
        d.reason == "immediate_threat"
        or d.reason == "infection_symptoms"
        or d.goal == "emergency_response"
    then
        return d
    end
    local H, S, FOOD, FED, RESTED, SAFE, SUPPLIES, CAR = 1, 4, 16, 32, 64, 1024, 2048, 4096
    if c.shop then
        d.actions[#d.actions + 1] = {
            name = "scavenge_supplies",
            kind = "SCAVENGE",
            target = c.shop,
            require_mask = CAR | S | FOOD,
            require_value = S,
            set_mask = FOOD | SUPPLIES,
            clear_mask = 0,
            cost = 1.5,
            duration = 0.2,
            target_id = "supplies",
            animation = "Loot",
        }
        if not c.shop_available then
            d.actions[#d.actions + 1] = {
                name = "walk_abandoned_shop",
                kind = "WALK",
                target = c.shop,
                require_mask = CAR | S,
                require_value = 0,
                set_mask = S,
                clear_mask = 11,
                cost = 2
                    + math.sqrt((c.position.x - c.shop.x) ^ 2 + (c.position.y - c.shop.y) ^ 2) / 30,
                duration = 0,
                target_id = "",
                animation = "Walk",
            }
        end
    end
    if c.home and c.home_safe then
        d.actions[#d.actions + 1] = {
            name = "shelter_at_home",
            kind = "SHELTER",
            target = c.home,
            require_mask = CAR | H | SAFE,
            require_value = H,
            set_mask = SAFE,
            clear_mask = 0,
            cost = 0.5,
            duration = 0.2,
            target_id = "home",
            animation = "Guard",
        }
    end
    d.reason = "survival_priorities"
    if c.hunger >= 0.5 then
        -- Lower hunger threshold after shortages; force a meal goal even if
        -- the civilian threshold initially marked the resident as fed.
        d.initial = d.initial & ~FED
        d.goal = "find_food"
        d.goal_mask = FED
        d.goal_value = FED
    elseif c.fatigue >= 0.5 then
        d.initial = d.initial & ~RESTED
        d.goal = "rest_safely"
        d.goal_mask = H | RESTED
        d.goal_value = H | RESTED
    elseif not c.has_food then
        d.goal = "gather_supplies"
        d.goal_mask = FOOD
        d.goal_value = FOOD
    elseif c.home_safe and c.home then
        d.goal = "shelter"
        d.goal_mask = H | CAR
        d.goal_value = H
    else
        d.goal = "find_refuge"
        d.goal_mask = SAFE
        d.goal_value = SAFE
    end
    return d
end
