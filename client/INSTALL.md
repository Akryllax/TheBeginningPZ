# First Week client helper

**Superseded experimental prototype. The selected architecture uses ordinary Lua clients
and server-only JVM injection. Do not install or launch this helper for that architecture.**

This launcher loads a Java agent using `-javaagent`. The agent rewrites selected game methods
in JVM memory during startup. It gates managed Bandits callbacks and overrides the vehicle
controller path for managed NPC drivers. This is deeper integration than an ordinary Lua
mod and every participating client would need the matching launcher and agent. It is tied
to exact game and Bandits versions; compatibility checks do not establish multiplayer correctness.

The installed game JAR is not replaced. The C++ planner runs beside the server, not on clients.
Bandits remains a separate Workshop dependency. Python 3 is required for the launcher.

1. Close Project Zomboid and extract the private helper package to a writable directory.
2. Run `python3 launch.py --check --install-mod` on Linux, or
   `py -3 launch.py --check --install-mod` on Windows. Use `--game-dir PATH` for a nondefault Steam library.
3. Start `launch-first-week.sh` on Linux or `launch-first-week.cmd` on Windows while Steam is running.
4. Join the address provided for the playable or disposable First Week world. The C++ service
   runs beside the server; no new client network port is required.

A compatibility rejection identifies an unsupported dependency/game build. Update the helper
for that exact build instead of disabling checks. The launcher preserves earlier local mod
versions under `Zomboid/mods-backups/`. To remove the helper, launch normally from Steam and
restore/remove the original companion folder as appropriate. No persistent Steam launch
options or global Java environment settings are installed.

The helper is active only with the matching scenario's enabled Lua settings. Normal worlds
retain their normal behavior. Keep this directory writable for its generated client properties.
