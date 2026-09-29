#include "planner.hpp"
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <limits>
#include <map>
#include <queue>
#include <set>
#include <sstream>
#include <tuple>

namespace npc {
bool validate(const pb::ObservationBatch &b, std::string &reason) {
    auto fail = [&](std::string text) {
        reason = std::move(text);
        return false;
    };
    if (b.residents_size() > static_cast<int>(kMaxResidents) || b.places_size() > 2048 ||
        b.road_nodes_size() > static_cast<int>(kMaxRoadNodes) ||
        b.road_edges_size() > static_cast<int>(kMaxRoadEdges))
        return fail("observation_limit");
    if (!std::isfinite(b.world_hour()) || !std::isfinite(b.scenario_hour()) || b.world_hour() < 0 ||
        b.scenario_hour() < 0 || b.phase().size() > 32)
        return fail("invalid_clock_or_phase");
    if (b.navigation_id().size() > 128 || b.road_closures_size() > 128 ||
        (b.road_closures_size() && b.navigation_id().empty()))
        return fail("navigation_limits");
    for (const auto &c : b.road_closures())
        if (!std::isfinite(c.expires_world_hour()) || c.expires_world_hour() < 0 ||
            c.expires_world_hour() > b.world_hour() + 24 || c.reason().size() > 128)
            return fail("invalid_road_closure");
    auto point_ok = [](const pb::Point &p) {
        return std::isfinite(p.x()) && std::isfinite(p.y()) && p.x() >= 0 && p.y() >= 0 &&
               p.x() <= 100000 && p.y() <= 100000 && p.z() >= -32 && p.z() <= 32;
    };
    auto need_ok = [](double n) { return std::isfinite(n) && n >= 0 && n <= 1; };
    std::set<std::string> ids;
    for (const auto &r : b.residents()) {
        if (r.id().empty() || r.id().size() > 128 || !ids.insert(r.id()).second ||
            r.name().size() > 128 || r.role().size() > 32 || r.infection().size() > 32 ||
            r.vehicle_id().size() > 128 || r.current_action().size() > 64)
            return fail("resident_identity");
        if (!r.has_position() || !point_ok(r.position()) || (r.has_home() && !point_ok(r.home())) ||
            (r.has_work() && !point_ok(r.work())) || (r.has_shop() && !point_ok(r.shop())) ||
            (r.has_clinic() && !point_ok(r.clinic())))
            return fail("resident_position");
        if (!need_ok(r.hunger()) || !need_ok(r.fatigue()) || !need_ok(r.fear()) ||
            !std::isfinite(r.health()) || r.health() < 0 || r.health() > 100 ||
            !std::isfinite(r.exposed_hour()))
            return fail("resident_needs");
        if (r.has_vehicle_observation()) {
            const auto &v = r.vehicle_observation();
            if (v.id() != r.vehicle_id() || v.id().empty() || v.id().size() > 128 ||
                v.profile().size() > 128 || !v.has_position() || !point_ok(v.position()) ||
                v.position().z() != 0 || !v.has_entry_point() || !point_ok(v.entry_point()) ||
                v.entry_point().z() != 0 || !std::isfinite(v.heading_degrees()) ||
                std::abs(v.heading_degrees()) > 360 || !std::isfinite(v.speed_kmh()) ||
                v.speed_kmh() < 0 || v.speed_kmh() > 200)
                return fail("invalid_vehicle_observation");
        }
        if (r.has_combat()) {
            const auto &c = r.combat();
            if (!std::isfinite(c.endurance()) || c.endurance() < 0 || c.endurance() > 1 ||
                c.weapon_id().size() > 128 || c.threats_size() > 32 ||
                (c.escape_reachable() && (!c.has_escape_target() || !point_ok(c.escape_target()))))
                return fail("invalid_combat_observation");
            std::set<std::string> threats;
            for (const auto &t : c.threats())
                if (t.id().empty() || t.id().size() > 128 || t.generation() == 0 ||
                    !threats.insert(t.id()).second || !t.has_position() || !point_ok(t.position()))
                    return fail("invalid_combat_threat");
        }
    }
    ids.clear();
    for (const auto &p : b.places())
        if (p.id().empty() || p.id().size() > 128 || !ids.insert(p.id()).second ||
            p.kind().size() > 32 || !p.has_position() || !point_ok(p.position()))
            return fail("invalid_place");
    for (const auto &r : b.residents())
        if (r.has_execution()) {
            const auto &e = r.execution();
            if (e.goal().size() > 64 || e.action_id().size() > 160 || e.status().size() > 32 ||
                e.interrupt_reason().size() > 128 || e.routine_phase() > 3 ||
                e.buffered_edges() > 8 ||
                (e.routine_enabled() && (!e.has_activity() || !point_ok(e.activity()) ||
                                         !r.has_home() || !point_ok(r.home()))))
                return fail("invalid_execution_context");
        }
    std::set<uint32_t> nodes;
    for (const auto &n : b.road_nodes())
        if (!nodes.insert(n.id()).second || !n.has_position() || !point_ok(n.position()) ||
            n.position().z() != 0)
            return fail("invalid_road_node");
    for (const auto &e : b.road_edges())
        if (!nodes.contains(e.from()) || !nodes.contains(e.to()) || !std::isfinite(e.cost()) ||
            e.cost() <= 0 || e.cost() > 100000)
            return fail("invalid_road_edge");
    return true;
}

pb::Plan Planner::plan(const pb::ObservationBatch &batch, const pb::Resident &resident,
                       std::shared_ptr<const RoadGraph> graph) {
    const auto started = std::chrono::steady_clock::now();
    pb::Plan result;
    result.set_resident_id(resident.id());
    result.set_based_on_revision(resident.revision());
    result.set_generation(resident.generation());
    result.set_plan_revision(std::max(resident.plan_revision() + 1, batch.revision()));
    auto wait = [&](std::string reason) {
        result.clear_actions();
        result.set_reason(std::move(reason));
        auto *action = result.add_actions();
        action->set_kind(pb::WAIT);
        action->set_duration_hours(0.02);
    };
    try {
        if (resident.has_execution() && !resident.execution().status().empty() &&
            resident.execution().status() != "needs_plan") {
            result.set_goal(resident.execution().goal());
            result.set_reason("execution_observed");
            return result;
        }
        if (batch.paused() || batch.online_players() == 0) {
            result.set_goal("paused");
            wait("scenario_or_world_paused");
        } else if (resident.infection() == "dead" || resident.infection() == "reanimated" ||
                   resident.health() <= 0) {
            result.set_goal("inactive");
            wait("resident_not_alive");
        } else if (resident.has_combat() && resident.combat().known() && resident.threatened()) {
            const auto &c = resident.combat();
            const pb::CombatThreat *nearest = nullptr;
            double distance = std::numeric_limits<double>::infinity();
            for (const auto &threat : c.threats()) {
                if (!threat.visible() || !threat.zombie() || !threat.alive() ||
                    threat.position().z() != resident.position().z())
                    continue;
                const double d = std::hypot(threat.position().x() - resident.position().x(),
                                            threat.position().y() - resident.position().y());
                if (d < distance || (d == distance && nearest && threat.id() < nearest->id())) {
                    nearest = &threat;
                    distance = d;
                }
            }
            if (c.knocked_down() || c.attacking()) {
                result.set_goal("contact_busy");
                wait("native_action_in_progress");
            } else if (c.escape_reachable() && c.has_escape_target()) {
                result.set_goal("escape_danger");
                result.set_reason("reachable_escape");
                auto *action = result.add_actions();
                action->set_kind(pb::FLEE);
                *action->mutable_target() = c.escape_target();
            } else if (c.path_pending()) {
                result.set_goal("escape_pending");
                wait("path_result_pending");
            } else if (!c.escape_assessed()) {
                result.set_goal("escape_unknown");
                wait("escape_not_assessed");
            } else if (nearest && distance <= 2.0) {
                result.set_goal("defend_to_escape");
                result.set_reason("escape_blocked");
                auto *action = result.add_actions();
                action->set_kind(pb::DEFEND);
                action->set_target_id(nearest->id());
                *action->mutable_target() = nearest->position();
                action->set_animation(
                    c.weapon_usable() && c.endurance() >= 0.2 && !c.weapon_id().empty() ? "melee"
                                                                                        : "shove");
            } else {
                result.set_goal("escape_blocked");
                wait("no_contact_target");
            }
        } else {
            if (!graph)
                graph = roads_.get(batch);
            auto domain = rules_.domain(batch, resident);
            result.set_goal(domain.goal);
            result.set_reason(domain.reason);
            std::string route_failure;
            std::erase_if(domain.actions, [&](Operator &op) {
                if (op.action.kind() != pb::DRIVE)
                    return false;
                const auto &origin = resident.has_vehicle_observation()
                                         ? resident.vehicle_observation().position()
                                         : resident.position();
                auto route = graph->route(origin, op.action.target(), batch);
                if (!route.error.empty()) {
                    route_failure = route.error;
                    return true;
                }
                const auto building = op.action.target();
                const auto endpoint = route.points.back();
                *op.action.mutable_target() = endpoint;
                const std::string destination = op.name.substr(std::string("drive_").size());
                for (auto &dependent : domain.actions) {
                    if (dependent.name == "park_" + destination)
                        *dependent.action.mutable_target() = endpoint;
                    if (dependent.name == "walk_from_parked_" + destination)
                        dependent.cost = 1 + std::hypot(building.x() - endpoint.x(),
                                                        building.y() - endpoint.y()) /
                                                 30;
                }
                for (const auto &p : route.points)
                    *op.action.add_route() = p;
                op.action.set_navigation_id(graph->identity());
                for (auto id : route.node_ids)
                    op.action.add_road_node_ids(id);
                return false;
            });
            // Admissible h-max delete-relaxation: estimate the cheapest way to
            // establish each required Boolean literal. This keeps the 64-node
            // budget useful for a long commute instead of spending it on cheap
            // but irrelevant social/eating/location combinations.
            std::map<uint32_t, double> heuristics;
            auto heuristic = [&](uint32_t facts) {
                if (auto existing = heuristics.find(facts); existing != heuristics.end())
                    return existing->second;
                std::array<double, 62> costs;
                costs.fill(std::numeric_limits<double>::infinity());
                for (unsigned bit = 0; bit < 31; ++bit)
                    costs[bit * 2 + ((facts >> bit) & 1)] = 0;
                for (unsigned pass = 0; pass < 62; ++pass) {
                    bool changed = false;
                    for (const auto &op : domain.actions) {
                        double pre = 0;
                        for (unsigned bit = 0; bit < 31; ++bit)
                            if ((op.require_mask >> bit) & 1)
                                pre =
                                    std::max(pre, costs[bit * 2 + ((op.require_value >> bit) & 1)]);
                        const double candidate = pre + op.cost;
                        for (unsigned bit = 0; bit < 31; ++bit) {
                            if (((op.set_mask | op.clear_mask) >> bit) & 1) {
                                auto &cost = costs[bit * 2 + ((op.set_mask >> bit) & 1)];
                                if (candidate < cost) {
                                    cost = candidate;
                                    changed = true;
                                }
                            }
                        }
                    }
                    if (!changed)
                        break;
                }
                double estimate = 0;
                for (unsigned bit = 0; bit < 31; ++bit)
                    if ((domain.goal_mask >> bit) & 1)
                        estimate =
                            std::max(estimate, costs[bit * 2 + ((domain.goal_value >> bit) & 1)]);
                heuristics[facts] = estimate;
                return estimate;
            };
            struct Node {
                uint32_t facts;
                double cost, estimate;
                std::vector<size_t> path;
                uint64_t serial;
            };
            auto compare = [](const Node &a, const Node &b) {
                return std::tie(a.estimate, a.cost, a.serial) >
                       std::tie(b.estimate, b.cost, b.serial);
            };
            std::priority_queue<Node, std::vector<Node>, decltype(compare)> open(compare);
            // Depth is part of the visited key: a cheaper six-step arrival
            // must not exclude a more expensive two-step route to the goal.
            std::map<std::pair<uint32_t, size_t>, double> best;
            uint64_t serial = 0;
            unsigned expansions = 0;
            bool found = false;
            open.push({domain.initial, 0, heuristic(domain.initial), {}, serial++});
            best[{domain.initial, 0}] = 0;
            while (!open.empty() && expansions < kMaxExpansions) {
                Node node = open.top();
                open.pop();
                if (node.cost > best[{node.facts, node.path.size()}])
                    continue;
                ++expansions;
                if ((node.facts & domain.goal_mask) == domain.goal_value) {
                    for (size_t op : node.path)
                        *result.add_actions() = domain.actions[op].action;
                    found = true;
                    break;
                }
                if (node.path.size() >= kMaxActions)
                    continue;
                for (size_t i = 0; i < domain.actions.size(); ++i) {
                    const auto &op = domain.actions[i];
                    if ((node.facts & op.require_mask) != op.require_value)
                        continue;
                    uint32_t next = (node.facts & ~op.clear_mask) | op.set_mask;
                    if (next == node.facts)
                        continue;
                    auto key = std::pair(next, node.path.size() + 1);
                    const double cost = node.cost + op.cost;
                    auto prev = best.find(key);
                    if (prev != best.end() && prev->second <= cost)
                        continue;
                    const double remaining = heuristic(next);
                    if (!std::isfinite(remaining))
                        continue;
                    best[key] = cost;
                    auto path = node.path;
                    path.push_back(i);
                    open.push({next, cost, cost + remaining, std::move(path), serial++});
                }
            }
            result.set_expansions(expansions);
            if (!found)
                wait(expansions >= kMaxExpansions ? "goap_search_budget" : "goal_unreachable");
            else if (result.actions().empty())
                wait("goal_already_satisfied");
            if (!route_failure.empty())
                result.set_reason(result.reason() + ";" + route_failure + ";walking_fallback");
        }
    } catch (const std::exception &error) {
        result.set_goal("planner_error");
        wait(std::string("rules_error:") + error.what());
    }
    // IDs contain the authoritative input revision and generation, so two
    // workers produce identical proposals and the game can deduplicate effects.
    pb::Point execution_position = resident.position();
    bool driving = false;
    for (const auto &a : result.actions())
        if (a.kind() == pb::DRIVE)
            driving = true;
    if (driving && !resident.in_vehicle() && resident.has_vehicle_observation()) {
        const auto &entry = resident.vehicle_observation().entry_point();
        if (std::hypot(resident.position().x() - entry.x(), resident.position().y() - entry.y()) >
                1.5 ||
            resident.position().z() != entry.z()) {
            result.clear_actions();
            result.set_goal("approach_vehicle");
            result.set_reason("walk_to_observed_vehicle_before_commute");
            auto *action = result.add_actions();
            action->set_kind(pb::WALK);
            *action->mutable_target() = entry;
            action->set_target_id(resident.vehicle_id());
        }
    }
    for (int i = 0; i < result.actions_size(); ++i) {
        std::ostringstream id;
        auto &action = *result.mutable_actions(i);
        if (action.kind() == pb::WALK)
            action.set_locomotion(pb::WALK_GAIT);
        else if (action.kind() == pb::FLEE)
            action.set_locomotion(pb::RUN);
        else
            action.set_locomotion(pb::IDLE);
        id << resident.id() << ':' << resident.generation() << ':' << result.plan_revision() << ':'
           << resident.revision() << ':' << i;
        result.mutable_actions(i)->set_id(id.str());
        if (result.actions(i).has_target())
            execution_position = result.actions(i).target();
        else
            *result.mutable_actions(i)->mutable_target() = execution_position;
    }
    result.set_compute_ms(
        std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started)
            .count());
    return result;
}
} // namespace npc
