#include "planner.hpp"
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <map>
#include <limits>
#include <queue>
#include <set>
#include <sstream>
#include <tuple>

namespace npc {
bool validate(const pb::ObservationBatch& b, std::string& reason) {
    auto fail = [&](std::string text) { reason = std::move(text); return false; };
    if (b.residents_size() > static_cast<int>(kMaxResidents) || b.places_size() > 2048 || b.road_nodes_size() > static_cast<int>(kMaxRoadNodes) || b.road_edges_size() > static_cast<int>(kMaxRoadEdges)) return fail("observation_limit");
    if (!std::isfinite(b.world_hour()) || !std::isfinite(b.scenario_hour()) || b.world_hour() < 0 || b.scenario_hour() < 0 || b.phase().size() > 32) return fail("invalid_clock_or_phase");
    auto point_ok = [](const pb::Point& p) { return std::isfinite(p.x()) && std::isfinite(p.y()) && p.x() >= 0 && p.y() >= 0 && p.x() <= 100000 && p.y() <= 100000 && p.z() >= -32 && p.z() <= 32; };
    auto need_ok = [](double n) { return std::isfinite(n) && n >= 0 && n <= 1; };
    std::set<std::string> ids;
    for (const auto& r : b.residents()) {
        if (r.id().empty() || r.id().size() > 128 || !ids.insert(r.id()).second || r.name().size() > 128 || r.role().size() > 32 || r.infection().size() > 32 || r.vehicle_id().size() > 128 || r.current_action().size() > 64) return fail("resident_identity");
        if (!r.has_position() || !point_ok(r.position()) || (r.has_home() && !point_ok(r.home())) || (r.has_work() && !point_ok(r.work())) || (r.has_shop() && !point_ok(r.shop())) || (r.has_clinic() && !point_ok(r.clinic()))) return fail("resident_position");
        if (!need_ok(r.hunger()) || !need_ok(r.fatigue()) || !need_ok(r.fear()) || !std::isfinite(r.health()) || r.health() < 0 || r.health() > 100 || !std::isfinite(r.exposed_hour())) return fail("resident_needs");
    }
    ids.clear();
    for (const auto& p : b.places()) if (p.id().empty() || p.id().size() > 128 || !ids.insert(p.id()).second || p.kind().size() > 32 || !p.has_position() || !point_ok(p.position())) return fail("invalid_place");
    std::set<uint32_t> nodes;
    for (const auto& n : b.road_nodes()) if (!nodes.insert(n.id()).second || !n.has_position() || !point_ok(n.position()) || n.position().z() != 0) return fail("invalid_road_node");
    for (const auto& e : b.road_edges()) if (!nodes.contains(e.from()) || !nodes.contains(e.to()) || !std::isfinite(e.cost()) || e.cost() <= 0 || e.cost() > 100000) return fail("invalid_road_edge");
    return true;
}

pb::Plan Planner::plan(const pb::ObservationBatch& batch, const pb::Resident& resident) {
    const auto started = std::chrono::steady_clock::now();
    pb::Plan result;
    result.set_resident_id(resident.id()); result.set_based_on_revision(resident.revision()); result.set_generation(resident.generation());
    result.set_plan_revision(std::max(resident.plan_revision() + 1, batch.revision()));
    auto wait = [&](std::string reason) {
        result.clear_actions(); result.set_reason(std::move(reason));
        auto* action = result.add_actions(); action->set_kind(pb::WAIT); action->set_duration_hours(0.02);
    };
    try {
        if (batch.paused() || batch.online_players() == 0) {
            result.set_goal("paused"); wait("scenario_or_world_paused");
        } else if (resident.infection() == "dead" || resident.infection() == "reanimated" || resident.health() <= 0) {
            result.set_goal("inactive"); wait("resident_not_alive");
        } else {
            auto domain = rules_.domain(batch, resident);
            result.set_goal(domain.goal); result.set_reason(domain.reason);
            std::string route_failure;
            std::erase_if(domain.actions, [&](Operator& op) {
                if (op.action.kind() != pb::DRIVE) return false;
                auto route = road_route(batch, resident.position(), op.action.target());
                if (!route.error.empty()) { route_failure = route.error; return true; }
                const auto building = op.action.target();
                const auto endpoint = route.points.back();
                *op.action.mutable_target() = endpoint;
                const std::string destination = op.name.substr(std::string("drive_").size());
                for (auto& dependent : domain.actions) {
                    if (dependent.name == "park_" + destination) *dependent.action.mutable_target() = endpoint;
                    if (dependent.name == "walk_from_parked_" + destination)
                        dependent.cost = 1 + std::hypot(building.x()-endpoint.x(), building.y()-endpoint.y())/30;
                }
                for (const auto& p : route.points) *op.action.add_route() = p;
                return false;
            });
            // Admissible h-max delete-relaxation: estimate the cheapest way to
            // establish each required Boolean literal. This keeps the 64-node
            // budget useful for a long commute instead of spending it on cheap
            // but irrelevant social/eating/location combinations.
            std::map<uint32_t, double> heuristics;
            auto heuristic = [&](uint32_t facts) {
                if (auto existing = heuristics.find(facts); existing != heuristics.end()) return existing->second;
                std::array<double, 62> costs; costs.fill(std::numeric_limits<double>::infinity());
                for (unsigned bit = 0; bit < 31; ++bit) costs[bit*2 + ((facts>>bit)&1)] = 0;
                for (unsigned pass = 0; pass < 62; ++pass) {
                    bool changed = false;
                    for (const auto& op : domain.actions) {
                        double pre = 0;
                        for (unsigned bit = 0; bit < 31; ++bit) if ((op.require_mask>>bit)&1) pre = std::max(pre, costs[bit*2+((op.require_value>>bit)&1)]);
                        const double candidate = pre + op.cost;
                        for (unsigned bit = 0; bit < 31; ++bit) {
                            if (((op.set_mask|op.clear_mask)>>bit)&1) {
                                auto& cost = costs[bit*2+((op.set_mask>>bit)&1)];
                                if (candidate < cost) { cost = candidate; changed = true; }
                            }
                        }
                    }
                    if (!changed) break;
                }
                double estimate = 0;
                for (unsigned bit = 0; bit < 31; ++bit) if ((domain.goal_mask>>bit)&1) estimate = std::max(estimate, costs[bit*2+((domain.goal_value>>bit)&1)]);
                heuristics[facts] = estimate; return estimate;
            };
            struct Node { uint32_t facts; double cost, estimate; std::vector<size_t> path; uint64_t serial; };
            auto compare = [](const Node& a, const Node& b) { return std::tie(a.estimate, a.cost, a.serial) > std::tie(b.estimate, b.cost, b.serial); };
            std::priority_queue<Node, std::vector<Node>, decltype(compare)> open(compare);
            // Depth is part of the visited key: a cheaper six-step arrival
            // must not exclude a more expensive two-step route to the goal.
            std::map<std::pair<uint32_t, size_t>, double> best;
            uint64_t serial = 0; unsigned expansions = 0; bool found = false;
            open.push({domain.initial, 0, heuristic(domain.initial), {}, serial++}); best[{domain.initial, 0}] = 0;
            while (!open.empty() && expansions < kMaxExpansions) {
                Node node = open.top(); open.pop();
                if (node.cost > best[{node.facts, node.path.size()}]) continue;
                ++expansions;
                if ((node.facts & domain.goal_mask) == domain.goal_value) {
                    for (size_t op : node.path) *result.add_actions() = domain.actions[op].action;
                    found = true; break;
                }
                if (node.path.size() >= kMaxActions) continue;
                for (size_t i = 0; i < domain.actions.size(); ++i) {
                    const auto& op = domain.actions[i];
                    if ((node.facts & op.require_mask) != op.require_value) continue;
                    uint32_t next = (node.facts & ~op.clear_mask) | op.set_mask;
                    if (next == node.facts) continue;
                    auto key = std::pair(next, node.path.size() + 1);
                    const double cost = node.cost + op.cost;
                    auto prev = best.find(key);
                    if (prev != best.end() && prev->second <= cost) continue;
                    const double remaining = heuristic(next);
                    if (!std::isfinite(remaining)) continue;
                    best[key] = cost; auto path = node.path; path.push_back(i);
                    open.push({next, cost, cost+remaining, std::move(path), serial++});
                }
            }
            result.set_expansions(expansions);
            if (!found) wait(expansions >= kMaxExpansions ? "goap_search_budget" : "goal_unreachable");
            else if (result.actions().empty()) wait("goal_already_satisfied");
            if (!route_failure.empty()) result.set_reason(result.reason() + ";" + route_failure + ";walking_fallback");
        }
    } catch (const std::exception& error) {
        result.set_goal("planner_error"); wait(std::string("rules_error:") + error.what());
    }
    // IDs contain the authoritative input revision and generation, so two
    // workers produce identical proposals and the game can deduplicate effects.
    pb::Point execution_position = resident.position();
    for (int i = 0; i < result.actions_size(); ++i) {
        std::ostringstream id;
        id << resident.id() << ':' << resident.generation() << ':' << result.plan_revision() << ':' << resident.revision() << ':' << i;
        result.mutable_actions(i)->set_id(id.str());
        if (result.actions(i).has_target()) execution_position = result.actions(i).target();
        else *result.mutable_actions(i)->mutable_target() = execution_position;
    }
    result.set_compute_ms(std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now()-started).count());
    return result;
}
}
