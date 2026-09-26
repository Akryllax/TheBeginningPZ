#include "planner.hpp"
extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>

namespace npc {
uint64_t stable_hash(std::string_view text) {
    uint64_t hash = 14695981039346656037ULL;
    for (unsigned char c : text) { hash ^= c; hash *= 1099511628211ULL; }
    return hash;
}
std::string rules_hash(const std::filesystem::path& directory) {
    std::string bytes;
    for (const char* name : {"civilian.lua", "emergency.lua", "survival.lua"}) {
        std::ifstream input(directory / name, std::ios::binary);
        if (!input) throw std::runtime_error(std::string("missing rules: ") + name);
        bytes += name; bytes += '\0';
        bytes.append(std::istreambuf_iterator<char>(input), {});
        if (bytes.size() > 256 * 1024) throw std::runtime_error("rules source exceeds 256KiB");
    }
    std::ostringstream out; out << "fnv1a64:" << std::hex << std::setfill('0') << std::setw(16) << stable_hash(bytes);
    return out.str();
}
namespace {
void number(lua_State* L, const char* key, double value) { lua_pushnumber(L, value); lua_setfield(L, -2, key); }
void boolean(lua_State* L, const char* key, bool value) { lua_pushboolean(L, value); lua_setfield(L, -2, key); }
void string(lua_State* L, const char* key, const std::string& value) { lua_pushlstring(L, value.data(), value.size()); lua_setfield(L, -2, key); }
void point(lua_State* L, const char* key, const pb::Point& p) {
    lua_createtable(L, 0, 3); number(L, "x", p.x()); number(L, "y", p.y()); number(L, "z", p.z()); lua_setfield(L, -2, key);
}
double numfield(lua_State* L, int index, const char* key, double fallback = 0) {
    lua_getfield(L, index, key);
    if (lua_isnil(L, -1)) { lua_pop(L, 1); return fallback; }
    if (!lua_isnumber(L, -1)) { lua_pop(L, 1); throw std::runtime_error(std::string("rule field must be numeric: ") + key); }
    const double result = lua_tonumber(L, -1); lua_pop(L, 1);
    if (!std::isfinite(result)) throw std::runtime_error("nonfinite rule field");
    return result;
}
std::string strfield(lua_State* L, int index, const char* key) {
    lua_getfield(L, index, key);
    size_t size = 0; const char* ptr = lua_tolstring(L, -1, &size);
    if (size > 256) { lua_pop(L, 1); throw std::runtime_error("rule string too long"); }
    std::string value = ptr ? std::string(ptr, size) : ""; lua_pop(L, 1); return value;
}
uint32_t maskfield(lua_State* L, int index, const char* key) {
    const double d = numfield(L, index, key);
    if (d < 0 || d > 0x7fffffff || d != std::floor(d)) throw std::runtime_error("invalid GOAP mask");
    return static_cast<uint32_t>(d);
}
}
struct Rules::Impl {
    struct Budget { size_t used{}, limit{}; unsigned instructions{}, maximum{}; } budget;
    lua_State* L{};
    std::vector<int> modules;
    static void* allocate(void* ud, void* ptr, size_t old_size, size_t size) {
        auto& b = *static_cast<Budget*>(ud);
        if (!ptr) old_size = 0;
        if (size == 0) { std::free(ptr); b.used -= old_size; return nullptr; }
        if (size > b.limit || b.used - old_size > b.limit - size) return nullptr;
        void* result = std::realloc(ptr, size);
        if (result) b.used = b.used - old_size + size;
        return result;
    }
    static void hook(lua_State* L, lua_Debug*) {
        auto* b = *static_cast<Budget**>(lua_getextraspace(L));
        b->instructions += 1000;
        if (b->instructions >= b->maximum) luaL_error(L, "Lua instruction budget exhausted");
    }
    void call(int nargs, int nresults) {
        budget.instructions = 0;
        lua_sethook(L, hook, LUA_MASKCOUNT, 1000);
        const int rc = lua_pcall(L, nargs, nresults, 0);
        lua_sethook(L, nullptr, 0, 0);
        if (rc != LUA_OK) {
            const char* message = lua_tostring(L, -1);
            const std::string text = message ? message : "Lua memory budget exhausted";
            lua_settop(L, 0); lua_gc(L, LUA_GCCOLLECT);
            throw std::runtime_error(text);
        }
    }
    Impl(const std::filesystem::path& directory, size_t memory_limit, unsigned instruction_limit) {
        if (memory_limit < 128 * 1024 || instruction_limit < 1000) throw std::runtime_error("invalid Lua budgets");
        budget.limit = memory_limit; budget.maximum = instruction_limit;
        L = lua_newstate(allocate, &budget);
        if (!L) throw std::runtime_error("cannot initialize bounded Lua state");
        *static_cast<Budget**>(lua_getextraspace(L)) = &budget;
        try {
            luaL_requiref(L, "_G", luaopen_base, 1); lua_pop(L, 1);
            luaL_requiref(L, LUA_TABLIBNAME, luaopen_table, 1); lua_pop(L, 1);
            luaL_requiref(L, LUA_STRLIBNAME, luaopen_string, 1); lua_pop(L, 1);
            luaL_requiref(L, LUA_MATHLIBNAME, luaopen_math, 1); lua_pop(L, 1);
            // Removing pcall/xpcall prevents rule code from swallowing budget
            // errors and looping forever. No coroutine can bypass the hook.
            for (const char* name : {"dofile", "loadfile", "load", "collectgarbage", "print", "warn", "pcall", "xpcall"}) {
                lua_pushnil(L); lua_setglobal(L, name);
            }
            lua_getglobal(L, "math");
            lua_pushnil(L); lua_setfield(L, -2, "random");
            lua_pushnil(L); lua_setfield(L, -2, "randomseed"); lua_pop(L, 1);
            lua_getglobal(L, "string"); lua_pushnil(L); lua_setfield(L, -2, "dump"); lua_pop(L, 1);
            for (const char* name : {"civilian.lua", "emergency.lua", "survival.lua"}) {
                std::ifstream input(directory / name, std::ios::binary);
                if (!input) throw std::runtime_error(std::string("missing rules: ") + name);
                std::string code(std::istreambuf_iterator<char>(input), {});
                if (code.size() > 128 * 1024) throw std::runtime_error("rule module exceeds 128KiB");
                if (luaL_loadbufferx(L, code.data(), code.size(), name, "t") != LUA_OK) {
                    std::string error = lua_tostring(L, -1); throw std::runtime_error(error);
                }
                call(0, 1);
                if (!lua_isfunction(L, -1)) throw std::runtime_error("rule module must return a function");
                modules.push_back(luaL_ref(L, LUA_REGISTRYINDEX));
            }
        } catch (...) { lua_close(L); L = nullptr; throw; }
    }
    ~Impl() { if (L) lua_close(L); }
    void context(const pb::ObservationBatch& b, const pb::Resident& r) {
        lua_createtable(L, 0, 36);
        string(L, "id", r.id()); string(L, "name", r.name()); string(L, "role", r.role());
        string(L, "phase", b.phase()); string(L, "infection", r.infection()); string(L, "current_action", r.current_action());
        string(L, "vehicle_id", r.vehicle_id());
        number(L, "hunger", r.hunger()); number(L, "fatigue", r.fatigue()); number(L, "fear", r.fear()); number(L, "health", r.health());
        number(L, "world_hour", b.world_hour()); number(L, "scenario_hour", b.scenario_hour());
        number(L, "hour_of_day", std::fmod(b.world_hour(), 24.0));
        number(L, "variation", static_cast<double>((stable_hash(r.id()) ^ b.seed()) % 1000000));
        boolean(L, "has_food", r.has_food());
        boolean(L, "has_vehicle", r.has_vehicle());
        boolean(L, "vehicle_available", r.has_vehicle()&&r.has_vehicle_observation()&&
            r.vehicle_observation().available()&&r.vehicle_observation().id()==r.vehicle_id());
        boolean(L, "in_vehicle", r.in_vehicle());
        boolean(L, "home_safe", r.home_safe()); boolean(L, "work_available", r.work_available()); boolean(L, "threatened", r.threatened());
        point(L, "position", r.position());
        if (r.has_home()) point(L, "home", r.home());
        if (r.has_work()) point(L, "work", r.work());
        if (r.has_shop()) point(L, "shop", r.shop());
        if (r.has_clinic()) point(L, "clinic", r.clinic());
        bool shop_available = r.has_shop(), clinic_available = r.has_clinic();
        const pb::Place* safe_place = nullptr; double safe_distance = std::numeric_limits<double>::infinity();
        const pb::Place* incident = nullptr; double incident_distance = 100;
        for (const auto& place : b.places()) {
            auto same = [&](const pb::Point& p) { return std::hypot(p.x()-place.position().x(), p.y()-place.position().y()) < 4 && p.z() == place.position().z(); };
            if (r.has_shop() && same(r.shop())) shop_available = place.available();
            if (r.has_clinic() && same(r.clinic())) clinic_available = place.available();
            if (place.available() && (place.kind() == "shelter" || place.kind() == "clinic" || place.kind() == "police")) {
                const double d = std::hypot(r.position().x()-place.position().x(), r.position().y()-place.position().y());
                if (d < safe_distance || (d == safe_distance && (!safe_place || place.id() < safe_place->id()))) { safe_place = &place; safe_distance = d; }
            }
            if (place.available() && place.kind() == "incident" && place.position().z() == r.position().z()) {
                const double d = std::hypot(r.position().x()-place.position().x(), r.position().y()-place.position().y());
                if (d < incident_distance || (d == incident_distance && (!incident || place.id() < incident->id()))) { incident = &place; incident_distance = d; }
            }
        }
        boolean(L, "shop_available", shop_available); boolean(L, "clinic_available", clinic_available);
        if (safe_place) point(L, "safe_place", safe_place->position());
        const pb::Resident* patient = nullptr; double best = 100 * 100;
        for (const auto& other : b.residents()) {
            if (other.id() == r.id() || !other.materialized() || other.position().z() != r.position().z()) continue;
            if (other.infection() != "symptomatic" && other.infection() != "critical" && other.infection() != "severe" && !other.threatened()) continue;
            double dx = other.position().x() - r.position().x(), dy = other.position().y() - r.position().y(), d = dx*dx+dy*dy;
            if (d < best || (d == best && (!patient || other.id() < patient->id()))) { patient = &other; best = d; }
        }
        if (incident && incident_distance * incident_distance < best) {
            lua_createtable(L, 0, 2);
            std::string id = incident->id(); if (id.starts_with("incident:")) id.erase(0, 9);
            string(L, "id", id); point(L, "position", incident->position()); lua_setfield(L, -2, "patient");
        } else if (patient) { lua_createtable(L, 0, 2); string(L, "id", patient->id()); point(L, "position", patient->position()); lua_setfield(L, -2, "patient"); }
    }
    Domain domain(const pb::ObservationBatch& b, const pb::Resident& r) {
        lua_settop(L, 0);
        context(b, r); // stack: context, current domain
        for (size_t i = 0; i < modules.size(); ++i) {
            lua_rawgeti(L, LUA_REGISTRYINDEX, modules[i]); lua_pushvalue(L, 1);
            if (i != 0) lua_pushvalue(L, 2);
            call(i == 0 ? 1 : 2, 1);
            if (!lua_istable(L, -1)) throw std::runtime_error("rule function must return a domain table");
            if (i != 0) lua_remove(L, 2);
        }
        Domain result;
        result.initial = maskfield(L, 2, "initial"); result.goal_mask = maskfield(L, 2, "goal_mask"); result.goal_value = maskfield(L, 2, "goal_value");
        result.goal = strfield(L, 2, "goal"); result.reason = strfield(L, 2, "reason");
        if (result.goal.empty() || !result.goal_mask || (result.goal_value & ~result.goal_mask)) throw std::runtime_error("invalid rule goal");
        lua_getfield(L, 2, "actions");
        if (!lua_istable(L, -1) || lua_rawlen(L, -1) > 48) throw std::runtime_error("invalid/beyond budget rule actions");
        const int actions_index = lua_gettop(L);
        for (size_t i = 1; i <= lua_rawlen(L, actions_index); ++i) {
            lua_rawgeti(L, actions_index, i); const int index = lua_gettop(L);
            if (!lua_istable(L, index)) throw std::runtime_error("invalid rule action");
            Operator op; op.name = strfield(L, index, "name");
            op.require_mask = maskfield(L, index, "require_mask"); op.require_value = maskfield(L, index, "require_value");
            op.set_mask = maskfield(L, index, "set_mask"); op.clear_mask = maskfield(L, index, "clear_mask");
            op.cost = numfield(L, index, "cost", 1);
            if (op.name.empty() || op.cost <= 0 || op.cost > 100000 || (op.require_value & ~op.require_mask) || (op.set_mask & op.clear_mask)) throw std::runtime_error("invalid rule operator");
            pb::ActionKind kind;
            if (!pb::ActionKind_Parse(strfield(L, index, "kind"), &kind) || kind == pb::ACTION_UNSPECIFIED) throw std::runtime_error("invalid rule action kind");
            op.action.set_kind(kind); op.action.set_target_id(strfield(L, index, "target_id"));
            op.action.set_animation(strfield(L, index, "animation"));
            const double duration = numfield(L, index, "duration");
            if (duration < 0 || duration > 24) throw std::runtime_error("invalid action duration");
            op.action.set_duration_hours(duration);
            lua_getfield(L, index, "target");
            if (lua_istable(L, -1)) {
                const int target = lua_gettop(L);
                op.action.mutable_target()->set_x(numfield(L, target, "x"));
                op.action.mutable_target()->set_y(numfield(L, target, "y"));
                op.action.mutable_target()->set_z(static_cast<int>(numfield(L, target, "z")));
            }
            lua_pop(L, 1); lua_pop(L, 1); result.actions.push_back(std::move(op));
        }
        lua_settop(L, 0); lua_gc(L, LUA_GCCOLLECT);
        return result;
    }
};
Rules::Rules(const std::filesystem::path& dir, size_t memory, unsigned instructions) : impl_(std::make_unique<Impl>(dir, memory, instructions)) {}
Rules::~Rules() = default;
Domain Rules::domain(const pb::ObservationBatch& b, const pb::Resident& r) { return impl_->domain(b, r); }
}
