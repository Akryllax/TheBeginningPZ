#pragma once
#include "npc_control.pb.h"
#include <cstdint>
#include <filesystem>
#include <memory>
#include <string>
#include <vector>

namespace npc {
namespace pb = lofers::npc::v1;
constexpr size_t kMaxFrame = 256 * 1024;
constexpr size_t kMaxResidents = 64;
constexpr size_t kMaxRoadNodes = 4096;
constexpr size_t kMaxRoadEdges = 16384;
constexpr unsigned kMaxExpansions = 64;
constexpr unsigned kMaxActions = 6;
constexpr unsigned kMaxRoadExpansions = 2048;

struct Operator {
    std::string name;
    uint32_t require_mask{}, require_value{}, set_mask{}, clear_mask{};
    double cost{1};
    pb::Action action;
};
struct Domain {
    std::string goal, reason;
    uint32_t initial{}, goal_mask{}, goal_value{};
    std::vector<Operator> actions;
};
struct Route {
    std::vector<pb::Point> points;
    unsigned expansions{};
    double cost{};
    std::string error;
};
uint64_t stable_hash(std::string_view text);
std::string rules_hash(const std::filesystem::path& directory);
bool validate(const pb::ObservationBatch&, std::string& reason);
Route road_route(const pb::ObservationBatch&, const pb::Point&, const pb::Point&);

class Rules {
public:
    explicit Rules(const std::filesystem::path&, size_t memory_limit = 2 * 1024 * 1024,
                   unsigned instruction_limit = 100000);
    ~Rules();
    Rules(const Rules&) = delete;
    Rules& operator=(const Rules&) = delete;
    Domain domain(const pb::ObservationBatch&, const pb::Resident&);
private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

class Planner {
public:
    explicit Planner(const std::filesystem::path& dir) : rules_(dir) {}
    pb::Plan plan(const pb::ObservationBatch&, const pb::Resident&);
private:
    Rules rules_;
};

int serve(const std::filesystem::path& socket, const std::string& world,
          const std::filesystem::path& rules, unsigned workers,
          const std::filesystem::path& map_index = {});
}
