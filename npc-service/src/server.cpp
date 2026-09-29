#include "planner.hpp"
#include <algorithm>
#include <arpa/inet.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <fcntl.h>
#include <fstream>
#include <future>
#include <iostream>
#include <map>
#include <mutex>
#include <poll.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <thread>
#include <unistd.h>

namespace npc {
namespace {
using Clock = std::chrono::steady_clock;
volatile sig_atomic_t stopping = 0;
void stop_signal(int) {
    stopping = 1;
}
struct Result {
    pb::Plan plan;
    bool deferred{};
};
struct Job {
    std::shared_ptr<const pb::ObservationBatch> batch;
    std::shared_ptr<const RoadGraph> graph;
    size_t resident{};
    std::atomic_bool cancelled{false};
    std::promise<Result> promise;
};
class Pool {
    std::mutex mutex_;
    std::condition_variable available_;
    std::deque<std::shared_ptr<Job>> queue_;
    std::map<std::string, std::shared_ptr<Job>> latest_;
    std::vector<std::thread> workers_;
    bool stopping_{};
    size_t admitted_{};
    RoadGraphCache roads_;

  public:
    std::shared_ptr<const RoadGraph> roads(const pb::ObservationBatch &batch) {
        return roads_.get(batch);
    }
    Pool(unsigned count, const std::filesystem::path &directory) {
        // Validate all worker states before starting any thread. Bad rules fail
        // startup cleanly instead of leaving a partially initialized pool.
        std::vector<std::unique_ptr<Planner>> planners;
        for (unsigned i = 0; i < count; ++i)
            planners.push_back(std::make_unique<Planner>(directory));
        for (auto &planner : planners)
            workers_.emplace_back([this, planner = std::move(planner)] {
                while (true) {
                    std::shared_ptr<Job> job;
                    {
                        std::unique_lock lock(mutex_);
                        available_.wait(lock, [&] { return stopping_ || !queue_.empty(); });
                        if (stopping_)
                            return;
                        job = queue_.front();
                        queue_.pop_front();
                    }
                    Result result;
                    if (!job->cancelled)
                        result.plan = planner->plan(
                            *job->batch, job->batch->residents(job->resident), job->graph);
                    result.deferred = job->cancelled;
                    {
                        std::lock_guard lock(mutex_);
                        const auto &id = job->batch->residents(job->resident).id();
                        auto found = latest_.find(id);
                        if (found != latest_.end() && found->second == job)
                            latest_.erase(found);
                        --admitted_;
                    }
                    job->promise.set_value(std::move(result));
                }
            });
    }
    ~Pool() {
        {
            std::lock_guard lock(mutex_);
            stopping_ = true;
            for (auto &[_, job] : latest_)
                job->cancelled = true;
            for (auto &job : queue_)
                job->promise.set_value({{}, true});
            queue_.clear();
        }
        available_.notify_all();
        for (auto &worker : workers_)
            worker.join();
    }
    void cancel_all() {
        std::lock_guard lock(mutex_);
        for (auto &[_, job] : latest_)
            job->cancelled = true;
        for (auto &job : queue_) {
            job->promise.set_value({{}, true});
            --admitted_;
        }
        queue_.clear();
        latest_.clear();
    }
    std::future<Result> submit(std::shared_ptr<const pb::ObservationBatch> batch, size_t resident,
                               std::shared_ptr<const RoadGraph> graph) {
        auto job = std::make_shared<Job>();
        job->batch = std::move(batch);
        job->resident = resident;
        job->graph = std::move(graph);
        auto future = job->promise.get_future();
        std::lock_guard lock(mutex_);
        const auto &id = job->batch->residents(resident).id();
        if (auto previous = latest_.find(id); previous != latest_.end()) {
            previous->second->cancelled = true;
            auto queued = std::find(queue_.begin(), queue_.end(), previous->second);
            if (queued != queue_.end()) {
                (*queued)->promise.set_value({{}, true});
                queue_.erase(queued);
                --admitted_;
            }
        }
        if (admitted_ >= kMaxResidents) {
            job->promise.set_value({{}, true});
            return future;
        }
        latest_[id] = job;
        queue_.push_back(job);
        ++admitted_;
        available_.notify_one();
        return future;
    }
    size_t queue_size() {
        std::lock_guard lock(mutex_);
        return admitted_;
    }
};
struct Pending {
    uint64_t request{}, revision{};
    Clock::time_point start;
    std::vector<std::future<Result>> futures;
};
struct Session {
    int fd{-1};
    bool greeted{};
    std::string epoch, input;
    uint64_t last_request{}, last_revision{};
    std::deque<std::string> output;
    size_t output_offset{};
    std::deque<Pending> pending;
    Clock::time_point connected = Clock::now(), partial_started = Clock::now();
    ~Session() {
        if (fd >= 0)
            close(fd);
    }
};
pb::Envelope response(const std::string &world, const Session &session, uint64_t request) {
    pb::Envelope out;
    out.set_protocol_version(1);
    out.set_world(world);
    out.set_server_epoch(session.epoch);
    out.set_request_id(request);
    return out;
}
bool enqueue(Session &session, const pb::Envelope &envelope) {
    const size_t size = envelope.ByteSizeLong();
    if (size == 0 || size > kMaxFrame || session.output.size() >= 8)
        return false;
    uint32_t prefix = htonl(static_cast<uint32_t>(size));
    std::string data(reinterpret_cast<const char *>(&prefix), 4);
    envelope.AppendToString(&data);
    session.output.push_back(std::move(data));
    return true;
}
bool status(Session &s, const std::string &world, uint64_t request, const char *health,
            const std::string &detail, Pool &pool, unsigned workers) {
    auto envelope = response(world, s, request);
    envelope.mutable_status()->set_health(health);
    envelope.mutable_status()->set_detail(detail);
    envelope.mutable_status()->set_queue(pool.queue_size());
    envelope.mutable_status()->set_workers(workers);
    return enqueue(s, envelope);
}
bool process(Session &s, const pb::Envelope &input, const std::string &world, Pool &pool,
             unsigned workers, const pb::ObservationBatch &index, const std::string &hash) {
    if (input.protocol_version() != 1 || input.world() != world || input.server_epoch().empty() ||
        input.server_epoch().size() > 128)
        return false;
    if (!s.greeted) {
        if (!input.has_hello() || input.hello().max_frame_bytes() < 1024 ||
            input.hello().max_frame_bytes() > kMaxFrame)
            return false;
        s.epoch = input.server_epoch();
        s.last_request = input.request_id();
        s.greeted = true;
        auto out = response(world, s, input.request_id());
        out.mutable_hello()->set_build("akr-npc-service/0.2.0 cpp20 lua5.4.9 protobuf7.36.1");
        out.mutable_hello()->set_mod_version("0.2.0");
        out.mutable_hello()->set_registry_hash(hash);
        out.mutable_hello()->set_max_frame_bytes(kMaxFrame);
        out.mutable_hello()->set_max_residents(kMaxResidents);
        return enqueue(s, out);
    }
    if (input.server_epoch() != s.epoch || input.request_id() <= s.last_request ||
        input.has_hello())
        return false;
    s.last_request = input.request_id();
    if (input.has_status())
        return status(s, world, input.request_id(), "ready", "planner_connected", pool, workers);
    if (input.has_receipt()) {
        // Receipts are acknowledged for bridge delivery/reconciliation. They
        // cannot mutate authoritative state here; the next observation wins.
        const auto &receipt = input.receipt();
        if (receipt.resident_id().empty() || receipt.resident_id().size() > 128 ||
            receipt.action_id().size() > 256 || receipt.state().size() > 32 ||
            receipt.reason().size() > 512)
            return false;
        return status(s, world, input.request_id(), "ready", "receipt_observed", pool, workers);
    }
    if (!input.has_observations())
        return false;
    std::string reason;
    if (!validate(input.observations(), reason))
        return status(s, world, input.request_id(), "rejected", reason, pool, workers);
    if (input.observations().revision() <= s.last_revision)
        return status(s, world, input.request_id(), "stale", "observation_revision", pool, workers);
    if (s.pending.size() >= 4)
        return status(s, world, input.request_id(), "busy", "pending_batch_limit", pool, workers);
    s.last_revision = input.observations().revision();
    auto batch = std::make_shared<pb::ObservationBatch>(input.observations());
    if (batch->road_nodes().empty()) {
        if (!batch->navigation_id().empty() && batch->navigation_id() != index.navigation_id())
            return status(s, world, input.request_id(), "rejected", "navigation_identity_mismatch",
                          pool, workers);
        *batch->mutable_road_nodes() = index.road_nodes();
        *batch->mutable_road_edges() = index.road_edges();
        batch->set_navigation_id(index.navigation_id());
    }
    if (batch->places().empty())
        *batch->mutable_places() = index.places();
    Pending pending;
    pending.request = input.request_id();
    pending.revision = batch->revision();
    pending.start = Clock::now();
    auto graph = pool.roads(*batch);
    for (int i = 0; i < batch->residents_size(); ++i) {
        const auto &resident = batch->residents(i);
        if (resident.has_execution() && !resident.execution().status().empty() &&
            resident.execution().status() != "needs_plan")
            continue;
        pending.futures.push_back(pool.submit(batch, i, graph));
    }
    s.pending.push_back(std::move(pending));
    return true;
}
bool collect(Session &s, const std::string &world, const std::string &hash) {
    for (auto pending = s.pending.begin(); pending != s.pending.end();) {
        bool ready =
            std::all_of(pending->futures.begin(), pending->futures.end(), [](auto &future) {
                return future.wait_for(std::chrono::seconds(0)) == std::future_status::ready;
            });
        if (!ready) {
            ++pending;
            continue;
        }
        auto out = response(world, s, pending->request);
        auto *plans = out.mutable_plans();
        plans->set_observation_revision(pending->revision);
        plans->set_rules_hash(hash);
        plans->set_compute_ms(
            std::chrono::duration<double, std::milli>(Clock::now() - pending->start).count());
        for (auto &future : pending->futures) {
            auto result = future.get();
            if (result.deferred)
                plans->set_deferred(plans->deferred() + 1);
            else
                *plans->add_plans() = std::move(result.plan);
        }
        // Stable resident order comes from the input vector, not worker timing.
        if (!enqueue(s, out))
            return false;
        pending = s.pending.erase(pending);
    }
    return true;
}
} // namespace

int serve(const std::filesystem::path &path, const std::string &world,
          const std::filesystem::path &rules, unsigned workers,
          const std::filesystem::path &map_index) {
    pb::ObservationBatch index;
    if (!map_index.empty()) {
        if (!std::filesystem::exists(map_index) ||
            std::filesystem::file_size(map_index) > 4 * 1024 * 1024)
            throw std::runtime_error("invalid/missing map index");
        std::ifstream file(map_index, std::ios::binary);
        std::string bytes(std::istreambuf_iterator<char>(file), {}), reason;
        if (!index.ParseFromString(bytes) || !validate(index, reason) || !index.residents().empty())
            throw std::runtime_error("invalid map index: " + reason);
    }
    const std::string hash = rules_hash(rules);
    Pool pool(workers, rules);
    const std::string socket_path = path.string();
    if (socket_path.size() >= sizeof(sockaddr_un::sun_path))
        throw std::runtime_error("Unix socket path too long");
    std::filesystem::create_directories(path.parent_path());
    struct stat st{};
    if (lstat(socket_path.c_str(), &st) == 0) {
        if (!S_ISSOCK(st.st_mode) || st.st_uid != geteuid())
            throw std::runtime_error("refusing to remove non-owned socket path");
        const int probe = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        sockaddr_un address{};
        address.sun_family = AF_UNIX;
        std::strcpy(address.sun_path, socket_path.c_str());
        const int connected =
            connect(probe, reinterpret_cast<sockaddr *>(&address), sizeof(address));
        const int saved = errno;
        close(probe);
        if (connected == 0 || saved != ECONNREFUSED)
            throw std::runtime_error("socket is active or inaccessible");
        if (unlink(socket_path.c_str()) != 0)
            throw std::runtime_error("cannot remove stale socket");
    }
    const int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
    if (listener < 0)
        throw std::runtime_error("cannot create Unix socket");
    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    std::strcpy(address.sun_path, socket_path.c_str());
    if (bind(listener, reinterpret_cast<sockaddr *>(&address), sizeof(address)) != 0 ||
        chmod(socket_path.c_str(), 0660) != 0 || listen(listener, 2) != 0) {
        close(listener);
        throw std::runtime_error(std::string("cannot bind socket: ") + std::strerror(errno));
    }
    struct SocketCleanup {
        int fd;
        std::string path;
        ~SocketCleanup() {
            close(fd);
            unlink(path.c_str());
        }
    } cleanup{listener, socket_path};
    struct sigaction action{};
    action.sa_handler = stop_signal;
    sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, nullptr);
    sigaction(SIGINT, &action, nullptr);
    signal(SIGPIPE, SIG_IGN);
    std::unique_ptr<Session> session;
    std::cout << "npc-service ready world=" << world << " workers=" << workers << " rules=" << hash
              << " road_nodes=" << index.road_nodes_size() << '\n'
              << std::flush;
    while (!stopping) {
        pollfd descriptors[2]{
            {listener, POLLIN, 0},
            {session ? session->fd : -1,
             static_cast<short>(POLLIN | (session && !session->output.empty() ? POLLOUT : 0)), 0}};
        const int ready = poll(descriptors, 2, 10);
        if (ready < 0) {
            if (errno == EINTR)
                continue;
            throw std::runtime_error("poll failed");
        }
        if (descriptors[0].revents & POLLIN) {
            const int fd = accept4(listener, nullptr, nullptr, SOCK_NONBLOCK | SOCK_CLOEXEC);
            if (fd >= 0) {
                // This is a private single-game socket, not a public server.
                if (session)
                    close(fd);
                else {
                    pool.cancel_all();
                    session = std::make_unique<Session>();
                    session->fd = fd;
                }
            }
        }
        if (!session)
            continue;
        bool alive = !(descriptors[1].revents & (POLLERR | POLLNVAL));
        if (descriptors[1].revents & (POLLIN | POLLHUP)) {
            char buffer[8192];
            while (alive) {
                const ssize_t count = recv(session->fd, buffer, sizeof(buffer), 0);
                if (count == 0) {
                    alive = false;
                    break;
                }
                if (count < 0) {
                    if (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)
                        alive = false;
                    break;
                }
                if (session->input.empty())
                    session->partial_started = Clock::now();
                session->input.append(buffer, count);
                if (session->input.size() > kMaxFrame + 4 + sizeof(buffer)) {
                    alive = false;
                    break;
                }
                while (session->input.size() >= 4) {
                    uint32_t encoded;
                    std::memcpy(&encoded, session->input.data(), 4);
                    const size_t size = ntohl(encoded);
                    if (size == 0 || size > kMaxFrame) {
                        alive = false;
                        break;
                    }
                    if (session->input.size() < size + 4)
                        break;
                    pb::Envelope input;
                    if (!input.ParseFromArray(session->input.data() + 4, size) ||
                        !process(*session, input, world, pool, workers, index, hash)) {
                        alive = false;
                        break;
                    }
                    session->input.erase(0, size + 4);
                    session->partial_started = Clock::now();
                }
            }
        }
        if (alive)
            alive = collect(*session, world, hash);
        if (alive && !session->output.empty()) {
            auto &output = session->output.front();
            const ssize_t written = send(session->fd, output.data() + session->output_offset,
                                         output.size() - session->output_offset, MSG_NOSIGNAL);
            if (written > 0) {
                session->output_offset += written;
                if (session->output_offset == output.size()) {
                    session->output.pop_front();
                    session->output_offset = 0;
                }
            } else if (written < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)
                alive = false;
        }
        const auto now = Clock::now();
        if ((!session->greeted && now - session->connected > std::chrono::seconds(5)) ||
            (!session->input.empty() && now - session->partial_started > std::chrono::seconds(2)))
            alive = false;
        if (!alive) {
            pool.cancel_all();
            session.reset();
        }
    }
    pool.cancel_all();
    return 0;
}
} // namespace npc
