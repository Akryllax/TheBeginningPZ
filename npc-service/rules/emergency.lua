return function(c, d)
    local H, C, FED, RESTED, HELPED, SAFE, CAR = 1, 8, 32, 64, 512, 1024, 4096
    local function add(name, kind, p, mask, value, set, clear, cost, duration, id, animation)
        d.actions[#d.actions + 1] = {
            name = name,
            kind = kind,
            target = p,
            require_mask = mask,
            require_value = value,
            set_mask = set,
            clear_mask = clear,
            cost = cost,
            duration = duration,
            target_id = id or "",
            animation = animation,
        }
    end
    if c.clinic and c.clinic_available then
        add(
            "request_medical_help",
            "SEEK_HELP",
            c.clinic,
            C | CAR | HELPED,
            C,
            HELPED,
            0,
            1,
            0.15,
            "clinic",
            "CallOut"
        )
    end
    if c.threatened or c.fear >= 0.7 then
        -- Local emergency navigation remains executor-controlled. The planner
        -- supplies a known refuge, never an invented unobserved safe point.
        local refuge = c.home_safe and c.home or (c.clinic_available and c.clinic or c.safe_place)
        if refuge then
            add("flee_to_refuge", "FLEE", refuge, CAR | SAFE, 0, SAFE, 0, 0.1, 0, "", "Run")
            d.goal = "escape_danger"
            d.goal_mask = SAFE
            d.goal_value = SAFE
            d.reason = "immediate_threat"
        end
        return d
    end
    if
        (c.infection == "symptomatic" or c.infection == "critical" or c.infection == "severe")
        and c.clinic_available
    then
        d.goal = "seek_treatment"
        d.goal_mask = HELPED
        d.goal_value = HELPED
        d.reason = "infection_symptoms"
        return d
    end
    if
        c.phase ~= "waiting"
        and c.phase ~= "calm"
        and c.patient
        and (c.role == "medic" or c.role == "doctor" or c.role == "nurse" or c.role == "firefighter" or c.role == "police")
        and c.hunger < 0.8
        and c.fatigue < 0.85
    then
        -- Navigation and treatment stay separate, so witnesses see attendance
        -- before any server-committed medical effect.
        local AT_PATIENT = 65536
        add(
            "attend_incident",
            "WALK",
            c.patient.position,
            CAR | AT_PATIENT,
            0,
            AT_PATIENT,
            15,
            1,
            0,
            c.patient.id,
            "Walk"
        )
        add(
            "help_civilian",
            c.role == "police" and "PATROL" or "TREAT",
            c.patient.position,
            CAR | AT_PATIENT | HELPED,
            AT_PATIENT,
            HELPED,
            0,
            1,
            0.12,
            c.patient.id,
            "Treat"
        )
        d.goal = "emergency_response"
        d.goal_mask = HELPED
        d.goal_value = HELPED
        d.reason = "observed_civilian_incident"
    end
    return d
end
