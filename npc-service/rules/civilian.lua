-- Original rules. No engine objects, I/O, wall clock, or ambient randomness.
-- A worker evaluates this pure domain function for one detached observation.
return function(c)
    local H, W, S, C, FOOD, FED, RESTED, WORKED, SOCIAL, HELPED, SAFE, SUPPLIES, CAR, ARRIVED, PARKED, PATROL =
        1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768
    local location = H | W | S | C
    local road_locations = location << 17
    local function distance(a, b)
        return math.sqrt((a.x - b.x) ^ 2 + (a.y - b.y) ^ 2) + math.abs(a.z - b.z) * 20
    end
    local function near(p)
        return p and distance(c.position, p) < 4
    end
    local initial = 0
    if near(c.home) then
        initial = initial | H
    end
    if near(c.work) then
        initial = initial | W
    end
    if near(c.shop) then
        initial = initial | S
    end
    if near(c.clinic) then
        initial = initial | C
    end
    if c.has_food then
        initial = initial | FOOD
    end
    if c.hunger < 0.6 then
        initial = initial | FED
    end
    if c.fatigue < 0.65 then
        initial = initial | RESTED
    end
    if not c.threatened and c.fear < 0.7 then
        initial = initial | SAFE
    end
    if c.in_vehicle then
        initial = initial | CAR
    end
    local d = {
        initial = initial,
        goal = "idle",
        goal_mask = SOCIAL,
        goal_value = SOCIAL,
        reason = "civilian_schedule",
        actions = {},
    }
    local function add(name, kind, target, mask, value, set, clear, cost, duration, id, animation)
        d.actions[#d.actions + 1] = {
            name = name,
            kind = kind,
            target = target,
            require_mask = mask,
            require_value = value,
            set_mask = set,
            clear_mask = clear,
            cost = cost,
            duration = duration,
            target_id = id or "",
            animation = animation or "",
        }
    end
    if c.routine_enabled then
        -- Three durable milestones, not a fresh location goal on each observation.
        local VISITED, WAITED, HOME = 1, 2, 4
        local phase = c.routine_phase or 0
        d.initial = phase == 0 and 0
            or (phase == 1 and VISITED or (phase == 2 and (VISITED | WAITED) or 7))
        d.goal = "civilian_routine"
        d.goal_mask = 7
        d.goal_value = 7
        d.reason = "committed_routine"
        add("visit_activity", "WALK", c.activity, VISITED, 0, VISITED, 0, 1, 0, "", "Walk")
        add(
            c.collect_item and "collect_activity" or "wait_activity",
            c.collect_item and "COLLECT" or "WAIT",
            c.activity,
            VISITED | WAITED,
            VISITED,
            WAITED,
            0,
            1,
            0,
            "",
            "Idle"
        )
        add(
            "return_home",
            "WALK",
            c.home,
            VISITED | WAITED | HOME,
            VISITED | WAITED,
            HOME,
            0,
            1,
            0,
            "",
            "Walk"
        )
        return d
    end
    -- Location facts are exclusive after movement. Enter/drive/park/exit is a
    -- real four-step sequence; destination interactions require being on foot.
    local function travel(label, point, bit, available)
        if not point or not available then
            return
        end
        local dist = distance(c.position, point)
        add(
            "walk_" .. label,
            "WALK",
            point,
            CAR | bit,
            0,
            bit,
            location & ~bit,
            1 + dist / 30,
            0,
            "",
            "Walk"
        )
        if
            c.vehicle_available
            and c.vehicle_id ~= ""
            and dist > 90
            and point.z == 0
            and c.position.z == 0
        then
            local road_bit = bit << 17
            add(
                "drive_" .. label,
                "DRIVE",
                point,
                CAR | bit | road_bit,
                CAR,
                road_bit | ARRIVED,
                location | (road_locations & ~road_bit) | PARKED,
                1 + dist / 180,
                0,
                c.vehicle_id,
                "Drive"
            )
            add(
                "park_" .. label,
                "PARK",
                point,
                CAR | ARRIVED | road_bit,
                CAR | ARRIVED | road_bit,
                PARKED,
                ARRIVED,
                0.2,
                0.01,
                c.vehicle_id,
                "Park"
            )
            -- The road endpoint is distinct from the building. Native planning
            -- replaces this cost using the actual A* endpoint, and rewrites
            -- DRIVE/PARK targets to that endpoint before returning the plan.
            add(
                "walk_from_parked_" .. label,
                "WALK",
                point,
                CAR | road_bit | bit,
                road_bit,
                bit,
                (location & ~bit) | road_locations,
                1,
                0,
                "",
                "Walk"
            )
        end
    end
    travel("home", c.home, H, true)
    travel("work", c.work, W, c.work_available)
    travel("shop", c.shop, S, c.shop_available)
    travel("clinic", c.clinic, C, c.clinic_available)
    if c.has_vehicle and c.vehicle_id ~= "" then
        if c.vehicle_available then
            add(
                "enter_vehicle",
                "ENTER_VEHICLE",
                c.position,
                CAR,
                0,
                CAR,
                PARKED | ARRIVED,
                0.3,
                0.002,
                c.vehicle_id,
                "EnterVehicle"
            )
        end
        if c.in_vehicle then
            add(
                "park_for_recovery",
                "PARK",
                c.position,
                CAR | PARKED,
                CAR,
                PARKED,
                ARRIVED,
                0.2,
                0.01,
                c.vehicle_id,
                "Park"
            )
        end
        add(
            "exit_vehicle",
            "EXIT_VEHICLE",
            nil,
            CAR | PARKED,
            CAR | PARKED,
            0,
            CAR | PARKED,
            0.3,
            0.002,
            c.vehicle_id,
            "ExitVehicle"
        )
    end
    add("eat_carried_food", "EAT", nil, CAR | FOOD | FED, FOOD, FED, FOOD, 0.5, 0.08, "", "Eat")
    add("rest_at_home", "REST", c.home, CAR | H | RESTED, H, RESTED, 0, 1, 1.5, "", "Sleep")
    if c.shop and c.shop_available then
        add("buy_food", "SHOP", c.shop, CAR | S | FOOD, S, FOOD | SUPPLIES, 0, 1, 0.12, "", "Loot")
    end
    if c.work and c.work_available then
        add("work_shift", "WORK", c.work, CAR | W | WORKED, W, WORKED, 0, 1, 0.5, c.role, "Work")
    end
    add("socialize", "SOCIALIZE", nil, CAR | SOCIAL, 0, SOCIAL, 0, 1, 0.08, "", "Talk")
    local hour = c.hour_of_day
    -- A stable small offset prevents every resident commuting at once.
    local start = 8 + (c.variation % 90) / 60
    if c.hunger >= 0.6 then
        d.goal = "eat"
        d.goal_mask = FED
        d.goal_value = FED
    elseif c.fatigue >= 0.65 or hour >= 22.5 or hour < 6 then
        if hour >= 22.5 or hour < 6 then
            d.initial = d.initial & ~RESTED
        end
        d.goal = "sleep"
        d.goal_mask = H | RESTED
        d.goal_value = H | RESTED
    elseif hour >= start and hour < 17 and c.work_available and c.work then
        d.goal = "work"
        d.goal_mask = WORKED
        d.goal_value = WORKED
    elseif hour >= 17 and hour < 19 and not c.has_food and c.shop_available and c.shop then
        d.goal = "shop"
        d.goal_mask = FOOD
        d.goal_value = FOOD
    elseif not near(c.home) and c.home then
        d.goal = "return_home"
        d.goal_mask = H | CAR
        d.goal_value = H
    end
    return d
end
