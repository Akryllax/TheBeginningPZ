# AKR server development distribution

Private experimental **Linux x86_64** operator kit. It contains the server runtime JAR,
its compiled compatibility manifest, the native worker's self-contained container root,
original Lua mods, matching source/build tools, and GPL-3.0/dependency licenses.
It is not a save backup or a preconfigured playable First Week release. Installing it
alone does not schedule civilians or activate the seven-day scenario.

## Contents

- `artifacts/scenario-agent/akr-scenario-agent.jar` and `manifest.json` — server JVM hook.
- `artifacts/npc-service-runtime/` — worker binary, rules, loader/libraries and notices.
  This is the existing **local/private** Linux bundle; portability/public redistribution
  of host libraries is not qualified.
- `mods/` — same original companion modules as the client kit.
- `scripts/`, `scenario-agent/`, `npc-service/`, `protocol/` and tests — corresponding
  authored source. Observer source and deployment files are included, but its optional
  exporter/UI must be built separately. Observer is not required for NPC gameplay.
- `references/pinned-client.json` and `references/workshop-files.json` — recorded
  compatibility/dependency hashes, not redistributed game/Workshop assets.

## Install into an isolated development server

1. Verify `SHA256SUMS` (`sha256sum -c SHA256SUMS` from this directory). Start in a new
   directory/world, never extract over a running server or an existing world.
2. Supply a legally obtained **42.20.4/b0bbce05d5 dedicated server** and its matching
   Java 25 runtime separately. Put it in `data/game-files/` for the project tooling,
   or configure the paths for your existing dedicated-server launcher. Do not allow
   SteamCMD to silently update it. Both engine-class and Linux physics-library hashes
   must match `artifacts/scenario-agent/manifest.json`; never bypass those guards.
3. Obtain upstream Bandits separately: Workshop **3268487204**, Mod ID **Bandits2**,
   with the recorded `BanditUpdate.lua` hash in the agent manifest. Install any other
   mods your chosen server profile needs separately. Workshop updates need revalidation.
4. Copy the included mod folders to the server's Zomboid user-data `mods/`. Distribute
   `AKR-client.zip` to every player. Use the exact scenario's mod list: the current
   pedestrian test harness uses `Bandits2;AKRCore;AKRDevTools`; a vehicle-only probe uses
   `AKRDriverProbe`. The campaign modules are not a substitute for test configuration.
5. Create a **new** private server INI/sandbox, accounts and passwords with the normal
   game tools. Live INI files, `secrets/`, account databases and saves are deliberately
   absent. Existing `compose.yaml` is the workstation deployment template: review its
   .132 bindings, UID/GID, ports, mounts and optional Observer services before use.
   The operation scripts expect project-local tooling and configuration; they are not
   a fresh-machine installer. `./dayone bootstrap` prepares tooling but does not create
   a playable world or pin/download the game for you.
6. Configure the Java hook with an operator-owned properties file. At minimum the
   existing scenario contract uses:

   ```properties
   side=server
   scenario.enabled=true
   world=YOUR_NEW_WORLD
   socket=/absolute/private/ipc/npc.sock
   bandits_update_file=/absolute/path/to/Bandits/42.20/media/lua/client/BanditUpdate.lua
   ```

   Add this JVM argument **only to the dedicated server**, using absolute paths:

   ```text
   -javaagent:/absolute/server/artifacts/scenario-agent/akr-scenario-agent.jar=/absolute/private/scenario.properties
   ```

   Watched NPC/vehicle tests need their additional harness-specific configuration.
   Use the included `civilian_combat.py`, `pedestrian_ops.py`, `civilian_watch.py` and
   `vehicle_probe_ops.py` through `./dayone`; do not guess flags or enable tests in a
   normal world. Runtime/debug sockets must stay private. Controls are not a public API.
7. For the planner, build its image with the existing `npc-service/Dockerfile` (context
   is this server directory), then run it with a private shared IPC mount and matching
   `--socket`, `--world` and `--rules /opt/akr/rules`. Use `--workers 2` for the current
   bounded setup. The included rootfs is for a container, not installation over `/`.
   Map indexes are game-derived and excluded; generate them from your local native map
   using `scripts/build_scenario_map.py` when the chosen scenario requires them. Do not
   share generated native-map or decompiler output as original assets.
8. Verify startup compatibility, worker/socket access and mod hashes in a disposable
   world before inviting players. Rebuilding/publishing the bundle is not a gameplay
   test. Current evidence is one-client experimental testing; two-client replication,
   generalized crowd capacity and a complete civilian opening remain separate gates.

## Rebuilding

In the source workspace with the pinned project-local toolchain and game dependencies:
`./dayone dist` rebuilds the scenario JAR and worker, then stages and checks both outputs.
`./dayone dist verify` checks the resulting inventory without rebuilding. It does not
start/stop services, install mods, copy credentials or publish anything. Use the project's
unit/headless/watched commands for qualification; a successful dist build is only a
build and packaging check.

The original repository is `git@github.com:Akryllax/TheBeginningPZ.git`. The bundle records
its revision and dirty state. Keep matching client/server manifests together for rollback.
