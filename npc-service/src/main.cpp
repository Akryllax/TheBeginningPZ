#include "planner.hpp"
#include <iostream>

int main(int argc, char **argv) {
    std::filesystem::path socket, rules, map;
    std::string world;
    unsigned workers = 2;
    try {
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "--help") {
                std::cout << "akr-npc-service --socket PATH --world NAME --rules DIR [--workers 2] "
                             "[--map-index PATH]\n";
                return 0;
            }
            if (i + 1 == argc)
                throw std::runtime_error("missing argument: " + arg);
            if (arg == "--socket")
                socket = argv[++i];
            else if (arg == "--world")
                world = argv[++i];
            else if (arg == "--rules")
                rules = argv[++i];
            else if (arg == "--map-index")
                map = argv[++i];
            else if (arg == "--workers")
                workers = std::stoul(argv[++i]);
            else
                throw std::runtime_error("unknown argument: " + arg);
        }
        if (socket.empty() || world.empty() || rules.empty() || (workers != 1 && workers != 2))
            throw std::runtime_error("required --socket, --world, --rules; workers must be 1 or 2");
        return npc::serve(socket, world, rules, workers, map);
    } catch (const std::exception &error) {
        std::cerr << "npc-service: " << error.what() << '\n';
        return 1;
    }
}
