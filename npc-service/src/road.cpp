#include "planner.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <queue>
#include <unordered_map>

namespace npc {
namespace {
double distance(const pb::Point& a, const pb::Point& b) {
    return std::hypot(a.x() - b.x(), a.y() - b.y());
}
}
Route road_route(const pb::ObservationBatch& batch, const pb::Point& from, const pb::Point& to) {
    Route result;
    if (from.z() != 0 || to.z() != 0) { result.error = "road_requires_ground_floor"; return result; }
    if (batch.road_nodes().empty()) { result.error = "road_graph_unavailable"; return result; }
    std::unordered_map<uint32_t, size_t> index;
    std::vector<const pb::RoadNode*> nodes;
    for (const auto& node : batch.road_nodes()) {
        index.emplace(node.id(), nodes.size()); nodes.push_back(&node);
    }
    auto nearest = [&](const pb::Point& point) {
        size_t best = 0; double cost = std::numeric_limits<double>::infinity();
        for (size_t i = 0; i < nodes.size(); ++i) {
            double d = distance(point, nodes[i]->position());
            if (d < cost || (d == cost && nodes[i]->id() < nodes[best]->id())) { best = i; cost = d; }
        }
        return std::pair(best, cost);
    };
    auto [start, start_distance] = nearest(from);
    auto [goal, goal_distance] = nearest(to);
    // Never fabricate a straight-line drive over unmapped terrain to reach a road.
    if (start_distance > 40 || goal_distance > 40) { result.error = "road_endpoint_too_far"; return result; }
    std::vector<std::vector<std::pair<size_t, double>>> edges(nodes.size());
    double admissible_scale = 1;
    for (const auto& edge : batch.road_edges()) {
        if (edge.blocked()) continue;
        auto fi = index.find(edge.from()), ti = index.find(edge.to());
        if (fi == index.end() || ti == index.end()) continue;
        double geometric = distance(nodes[fi->second]->position(), nodes[ti->second]->position());
        if (geometric > 0) admissible_scale = std::min(admissible_scale, edge.cost() / geometric);
        edges[fi->second].emplace_back(ti->second, edge.cost());
    }
    for (auto& list : edges) std::sort(list.begin(), list.end(), [&](auto a, auto b) { return nodes[a.first]->id() < nodes[b.first]->id(); });
    struct Entry { double f, g; size_t at; uint32_t id; };
    auto cmp = [](const Entry& a, const Entry& b) {
        if (a.f != b.f) return a.f > b.f;
        if (a.g != b.g) return a.g > b.g;
        return a.id > b.id;
    };
    std::priority_queue<Entry, std::vector<Entry>, decltype(cmp)> open(cmp);
    std::vector<double> scores(nodes.size(), std::numeric_limits<double>::infinity());
    std::vector<size_t> previous(nodes.size(), nodes.size());
    scores[start] = 0;
    open.push({distance(nodes[start]->position(), nodes[goal]->position()) * admissible_scale, 0, start, nodes[start]->id()});
    while (!open.empty() && result.expansions < kMaxRoadExpansions) {
        const auto current = open.top(); open.pop();
        if (current.g != scores[current.at]) continue;
        ++result.expansions;
        if (current.at == goal) {
            std::vector<size_t> path;
            for (size_t at = goal; at != nodes.size(); at = previous[at]) path.push_back(at);
            if (path.size() > 128) { result.error = "road_route_too_long"; return result; }
            for (auto it = path.rbegin(); it != path.rend(); ++it) result.points.push_back(nodes[*it]->position());
            result.cost = current.g;
            return result;
        }
        for (auto [next, weight] : edges[current.at]) {
            const double g = current.g + weight;
            if (g >= scores[next]) continue;
            scores[next] = g; previous[next] = current.at;
            open.push({g + distance(nodes[next]->position(), nodes[goal]->position()) * admissible_scale, g, next, nodes[next]->id()});
        }
    }
    result.error = open.empty() ? "road_disconnected" : "road_search_budget";
    return result;
}
}
