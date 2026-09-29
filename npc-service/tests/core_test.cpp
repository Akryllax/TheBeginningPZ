#include "planner.hpp"
#include <chrono>
#include <filesystem>
#include <fstream>
#include <future>
#include <iostream>
#include <stdexcept>
#include <unistd.h>

using namespace npc;
void require(bool value, const char *message) {
    if (!value)
        throw std::runtime_error(message);
}
npc::pb::Point at(double x, double y = 100) {
    npc::pb::Point p;
    p.set_x(x);
    p.set_y(y);
    return p;
}
npc::pb::ObservationBatch fixture() {
    npc::pb::ObservationBatch b;
    b.set_revision(12);
    b.set_world_hour(10);
    b.set_phase("calm");
    b.set_online_players(2);
    b.set_seed(7);
    auto *r = b.add_residents();
    r->set_id("resident-1");
    r->set_revision(8);
    r->set_generation(3);
    r->set_health(100);
    r->set_role("mechanic");
    r->set_home_safe(true);
    r->set_work_available(true);
    r->set_has_food(true);
    *r->mutable_position() = at(100);
    *r->mutable_home() = at(100);
    *r->mutable_work() = at(400);
    *r->mutable_shop() = at(130);
    *r->mutable_clinic() = at(160);
    for (unsigned i = 0; i < 4; ++i) {
        auto *n = b.add_road_nodes();
        n->set_id(i);
        *n->mutable_position() = at(100 + i * 100);
        if (i) {
            auto *e = b.add_road_edges();
            e->set_from(i - 1);
            e->set_to(i);
            e->set_cost(100);
        }
    }
    return b;
}
void car(npc::pb::ObservationBatch &b) {
    auto *r = b.mutable_residents(0);
    r->set_has_vehicle(true);
    r->set_vehicle_id("car-1");
    auto *v = r->mutable_vehicle_observation();
    v->set_id("car-1");
    v->set_profile("smallcar");
    v->set_available(true);
    *v->mutable_position() = at(100);
    *v->mutable_entry_point() = at(100);
}
void has(const npc::pb::Plan &plan, npc::pb::ActionKind kind) {
    for (const auto &action : plan.actions())
        if (action.kind() == kind)
            return;
    throw std::runtime_error("missing action " + npc::pb::ActionKind_Name(kind) + " in " +
                             plan.DebugString());
}
std::string normalized(npc::pb::Plan plan) {
    plan.clear_compute_ms();
    return plan.SerializeAsString();
}
int main(int argc, char **argv) {
    try {
        require(argc == 2, "rules directory required");
        std::filesystem::path rules = argv[1];
        Planner planner(rules);
        auto b = fixture();
        std::string reason;
        require(validate(b, reason), "fixture must validate");
        {
            auto routine = fixture();
            auto *r = routine.mutable_residents(0);
            auto *e = r->mutable_execution();
            e->set_routine_enabled(true);
            *e->mutable_activity() = at(110);
            e->set_goal("civilian_routine");
            for (unsigned phase = 0; phase < 3; ++phase) {
                e->set_routine_phase(phase);
                auto result = planner.plan(routine, *r);
                require(result.goal() == "civilian_routine", "durable routine goal");
                require(result.actions_size() == 3 - static_cast<int>(phase),
                        "routine continuation shape");
                require(result.actions(result.actions_size() - 1).kind() == npc::pb::WALK,
                        "routine returns home");
                require(result.actions(result.actions_size() - 1).locomotion() ==
                            npc::pb::WALK_GAIT,
                        "explicit gait");
                if (phase <= 1)
                    require(result.actions(1 - phase).kind() == npc::pb::WAIT,
                            "routine wait milestone");
            }
            for (const auto *status : {"executing", "waiting_path", "paused", "blocked",
                                       "completed", "cancelled", "failed"}) {
                e->set_status(status);
                e->set_action_id("committed-action");
                e->set_route_revision(9);
                e->set_completed_edges(100);
                e->set_buffered_edges(8);
                require(validate(routine, reason), "progress validates");
                require(planner.plan(routine, *r).actions_size() == 0,
                        "progress cannot replace committed action");
            }
            e->set_status("needs_plan");
            require(planner.plan(routine, *r).actions_size() == 1,
                    "explicit replan resumes remaining routine");
            e->set_buffered_edges(9);
            require(!validate(routine, reason), "oversized execution window rejected");
            e->set_buffered_edges(0);
            e->set_routine_phase(4);
            require(!validate(routine, reason), "invalid routine milestone rejected");
        }
        auto plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::WALK);
        has(plan, npc::pb::WORK);
        require(plan.actions_size() <= 6 && plan.expansions() <= 64, "bounded GOAP");
        b.mutable_residents(0)->set_hunger(0.9);
        b.mutable_residents(0)->set_has_food(false);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::WALK);
        has(plan, npc::pb::SHOP);
        has(plan, npc::pb::EAT);
        b = fixture();
        b.mutable_residents(0)->set_threatened(true);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::FLEE);
        b = fixture();
        {
            auto *r = b.mutable_residents(0);
            r->set_threatened(true);
            auto *combat = r->mutable_combat();
            combat->set_known(true);
            combat->set_endurance(.8);
            combat->set_weapon_id("item:14");
            combat->set_weapon_usable(true);
            auto *threat = combat->add_threats();
            threat->set_id("z:1:7");
            threat->set_generation(7);
            threat->set_visible(true);
            threat->set_zombie(true);
            threat->set_alive(true);
            *threat->mutable_position() = at(101);
            require(validate(b, reason), "bounded combat observation validates");
            plan = planner.plan(b, b.residents(0));
            has(plan, npc::pb::WAIT);
            require(plan.reason() == "escape_not_assessed", "unknown escape never means cornered");
            combat->set_escape_assessed(true);
            plan = planner.plan(b, b.residents(0));
            has(plan, npc::pb::DEFEND);
            require(plan.actions(0).target_id() == "z:1:7" &&
                        plan.actions(0).animation() == "melee",
                    "worker selects equipped melee against exact target");
            combat->set_path_pending(true);
            has(planner.plan(b, b.residents(0)), npc::pb::WAIT);
            combat->set_path_pending(false);
            combat->set_escape_reachable(true);
            *combat->mutable_escape_target() = at(105);
            has(planner.plan(b, b.residents(0)), npc::pb::FLEE);
            combat->set_escape_reachable(false);
            combat->clear_escape_target();
            combat->set_weapon_usable(false);
            plan = planner.plan(b, b.residents(0));
            require(plan.actions(0).animation() == "shove", "unarmed fallback shoves");
            threat->set_generation(0);
            require(!validate(b, reason) && reason == "invalid_combat_threat",
                    "unstable target identity refused");
        }
        b = fixture();
        b.set_phase("emergency");
        b.mutable_residents(0)->set_infection("symptomatic");
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::WALK);
        has(plan, npc::pb::SEEK_HELP);
        b = fixture();
        b.mutable_residents(0)->set_fatigue(0.8);
        has(planner.plan(b, b.residents(0)), npc::pb::REST);
        b = fixture();
        b.set_world_hour(23);
        has(planner.plan(b, b.residents(0)), npc::pb::REST);
        b = fixture();
        b.set_phase("survival");
        b.mutable_residents(0)->set_has_food(false);
        b.mutable_residents(0)->set_hunger(0.8);
        auto *place = b.add_places();
        place->set_id("shop");
        place->set_kind("shop");
        *place->mutable_position() = b.residents(0).shop();
        place->set_available(false);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::SCAVENGE);
        has(plan, npc::pb::EAT);
        b = fixture();
        car(b);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::ENTER_VEHICLE);
        has(plan, npc::pb::DRIVE);
        has(plan, npc::pb::PARK);
        has(plan, npc::pb::EXIT_VEHICLE);
        has(plan, npc::pb::WALK);
        has(plan, npc::pb::WORK);
        require(plan.actions_size() == 6, "drive, park, exit, walk, interact fits six actions");
        for (const auto &a : plan.actions())
            if (a.kind() == npc::pb::DRIVE)
                require(a.route_size() == 4, "A* route follows graph");
        b.mutable_residents(0)->mutable_work()->set_y(125);
        plan = planner.plan(b, b.residents(0));
        require(plan.actions_size() == 6, "off-road building commute remains six steps");
        require(plan.actions(1).target().y() == 100 && plan.actions(2).target().y() == 100 &&
                    plan.actions(3).target().y() == 100,
                "drive/park/exit target the actual road endpoint");
        require(plan.actions(4).target().y() == 125 && plan.actions(5).target().y() == 125,
                "final walk/work target the real building");
        b.mutable_residents(0)->set_in_vehicle(true);
        *b.mutable_residents(0)->mutable_position() = at(400);
        plan = planner.plan(b, b.residents(0));
        require(plan.actions_size() == 4 && plan.actions(0).kind() == npc::pb::PARK &&
                    plan.actions(1).kind() == npc::pb::EXIT_VEHICLE &&
                    plan.actions(2).kind() == npc::pb::WALK &&
                    plan.actions(3).kind() == npc::pb::WORK,
                "failed drive recovery parks and exits before walking");
        b = fixture();
        car(b);
        b.mutable_road_edges(1)->set_blocked(true);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::WALK);
        require(plan.reason().find("road_disconnected") != std::string::npos,
                "blocked road reason");
        b = fixture();
        auto route = road_route(b, b.residents(0).position(), b.residents(0).work());
        require(route.error.empty() && route.points.size() == 4, "directed road path");
        route = road_route(b, b.residents(0).work(), b.residents(0).position());
        require(route.error == "road_disconnected", "one-way roads respected");
        b = fixture();
        *b.mutable_road_nodes(0)->mutable_position() = at(100, 100);
        *b.mutable_road_nodes(1)->mutable_position() = at(120, 100);
        *b.mutable_road_nodes(2)->mutable_position() = at(120, 120);
        *b.mutable_road_nodes(3)->mutable_position() = at(140, 120);
        for (auto &edge : *b.mutable_road_edges())
            edge.set_cost(20);
        route = road_route(b, at(100, 100), at(140, 120));
        require(route.error.empty() && route.points.size() == 4 && route.cost == 60,
                "bent road retains all graph segments");
        require(route.points[1].x() == 120 && route.points[1].y() == 100 &&
                    route.points[2].x() == 120 && route.points[2].y() == 120,
                "A* must not shortcut intermediate road bends");
        b = fixture();
        b.set_navigation_id("test-graph");
        RoadGraphCache cache;
        auto shared = cache.get(b);
        require(cache.get(b) == shared && cache.builds() == 1, "unchanged graph reused");
        auto *closure = b.add_road_closures();
        closure->set_from(1);
        closure->set_to(2);
        closure->set_expires_world_hour(11);
        require(cache.get(b) == shared && cache.builds() == 1,
                "closures do not rebuild static adjacency");
        require(shared->route(at(100), at(400), b).error == "road_disconnected",
                "active closure blocks edge");
        b.set_world_hour(12);
        require(shared->route(at(100), at(400), b).error.empty(), "expired closure releases route");
        b.set_navigation_id("other-map");
        require(shared->route(at(100), at(400), b).error == "navigation_identity_mismatch",
                "wrong graph rejected");
        b.set_navigation_id("test-graph");
        b.mutable_road_edges(0)->set_cost(150);
        require(cache.get(b) != shared && cache.builds() == 2,
                "changed graph bytes invalidate despite same declared id");
        b = fixture();
        car(b);
        *b.mutable_residents(0)->mutable_position() = at(80);
        plan = planner.plan(b, b.residents(0));
        require(plan.goal() == "approach_vehicle" && plan.actions_size() == 1 &&
                    plan.actions(0).target().x() == 100,
                "walk to actual car before commute within six action budget");
        b.mutable_residents(0)->mutable_vehicle_observation()->set_available(false);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::WALK);
        for (const auto &a : plan.actions())
            require(a.kind() != npc::pb::DRIVE, "unavailable car cannot drive");
        b.mutable_residents(0)->set_in_vehicle(true);
        plan = planner.plan(b, b.residents(0));
        has(plan, npc::pb::PARK);
        has(plan, npc::pb::EXIT_VEHICLE);
        for (const auto &a : plan.actions())
            require(a.kind() != npc::pb::DRIVE,
                    "unavailable occupied car must recover without driving");
        b.mutable_residents(0)->mutable_vehicle_observation()->set_speed_kmh(
            std::numeric_limits<double>::quiet_NaN());
        require(!validate(b, reason) && reason == "invalid_vehicle_observation",
                "invalid car pose rejected");
        b = fixture();
        b.mutable_residents(0)->mutable_position()->set_x(std::numeric_limits<double>::quiet_NaN());
        require(!validate(b, reason), "NaN rejected");
        b = fixture();
        *b.add_residents() = b.residents(0);
        require(!validate(b, reason), "duplicate residents rejected");
        b = fixture();
        const auto expected = normalized(planner.plan(b, b.residents(0)));
        std::vector<std::future<std::string>> parallel;
        for (int i = 0; i < 2; ++i)
            parallel.push_back(std::async(std::launch::async, [&] {
                Planner p(rules);
                std::string out;
                for (int j = 0; j < 100; ++j) {
                    out = normalized(p.plan(b, b.residents(0)));
                    require(out == expected, "deterministic repeated plan");
                }
                return out;
            }));
        for (auto &f : parallel)
            require(f.get() == expected, "deterministic parallel workers");
        const auto temp =
            std::filesystem::temp_directory_path() / ("akr-lua-budget-" + std::to_string(getpid()));
        std::filesystem::create_directories(temp);
        for (const char *name : {"emergency.lua", "survival.lua"})
            std::filesystem::copy_file(rules / name, temp / name,
                                       std::filesystem::copy_options::overwrite_existing);
        auto check_error = [&](const std::string &code, const char *message) {
            std::ofstream(temp / "civilian.lua") << code;
            Rules bounded(temp);
            const auto start = std::chrono::steady_clock::now();
            bool failed = false;
            try {
                bounded.domain(b, b.residents(0));
            } catch (const std::exception &) {
                failed = true;
            }
            require(failed, message);
            require(std::chrono::steady_clock::now() - start < std::chrono::milliseconds(500),
                    "Lua failure completed promptly");
        };
        check_error("return function(c) while true do end end", "infinite Lua loop bounded");
        check_error(
            "return function(c) local x={} for i=1,999999 do x[i]=string.rep('x',4096) end end",
            "Lua allocations bounded");
        {
            std::ifstream source(rules / "civilian.lua");
            std::string code(std::istreambuf_iterator<char>(source), {});
            const auto where = code.find("return function(c)");
            require(where != std::string::npos, "civilian function exists");
            code.insert(where + 18, "\nassert(io==nil and os==nil and package==nil and debug==nil "
                                    "and load==nil and pcall==nil and math.random==nil)\n");
            std::ofstream(temp / "civilian.lua") << code;
            Rules sandbox(temp);
            require(!sandbox.domain(b, b.residents(0)).actions.empty(),
                    "sandbox assertions must actually pass");
        }
        b = fixture();
        b.mutable_residents(0)->set_hunger(0.9);
        plan = planner.plan(b, b.residents(0));
        require(plan.actions(0).has_target() && plan.actions(0).target().x() == 100,
                "at-position actions include an explicit target");
        std::filesystem::remove_all(temp);
        std::cout << "npc-core: civilian, food, refuge, medical, rest, survival, driving, A*, "
                     "validation, determinism, Lua budgets passed\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
