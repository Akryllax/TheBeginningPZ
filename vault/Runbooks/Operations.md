---
type: runbook
status: active
updated: 2026-09-27
---

# Operations

Operate from `/var/home/akr/Documents/Projects/ZomboidDayOne`. The normal launcher targets `AKR_DayOne` on `.132`. The existing game and Observer on `.160` are independent; upstream Observer examples are not deployment commands for this world.

The current architecture uses server runtime Java hooks and ordinary Lua clients. Do not install the experimental client Java helper or replace installed game classes. The vehicle probe below investigates server-owned physics and stock replication; a successful build or server boot does not establish working NPC driving or multiplayer safety.

## Command interface

`./dayone vehicle-probe-create driver-model` prepares the isolated original seated-driver
variant. It packages only our mesh, palette and vehicle script as `LofersDriverProbe`;
clients need that ordinary asset mod. The default probe still uses the stock empty car.
Use `vehicle-probe-route`, `vehicle-probe-start`, `vehicle-probe-control start` and
`vehicle-probe-stop` as before. A completed probe removes its disposable car; it is not
the future persistent parked-car runtime.

Run `./dayone --help` for the current command list. Arguments after the command are positional; there are no command-specific help subparsers.

| Command | Purpose |
| --- | --- |
| `./dayone bootstrap` | Prepare project-local language tools, dependencies and browser assets |
| `./dayone manifest` | Record installed source/dependency identity |
| `./dayone agent` | While the game is stopped, build the Observer server exporter; this is separate from the scenario agent |
| `./dayone install-mod` | While stopped, copy the current original mod into game data, record its hashes and apply validated Linux dependency path aliases |
| `./dayone check` | Run deployment/configuration sanity checks |
| `./dayone build [services]` | Build local container images |
| `./dayone start` | Require the game stopped, install the current companion source, then recreate/start this world's services |
| `./dayone status` | Inspect running services |
| `./dayone logs [service]` | Read service logs; omit service for the default scope |
| `./dayone rcon <command words>` | Issue an explicit administration command without putting the password in shell history |
| `./dayone stop` | Gracefully stop the normal game, then its Observer, gateway and NPC worker |
| `./dayone backup` | Gracefully stop, back up consistent state, retain seven backups; leaves services stopped |
| `./dayone restore-test <backup.tar.gz>` | Verify checksum, extract into an isolated path and check SQLite integrity |
| `./dayone test` | Run project/Observer automated checks and frontend build |
| `./dayone package` | Build the versioned original companion-mod ZIP |

`stop` and `backup` do not stop the independently launched scenario test or vehicle probe. `test` covers project/Observer checks and the frontend build; native-worker, Java fixtures, browser acceptance and in-game multiplayer validation have separate evidence. See [[Runbooks/Backup and Restore]] and [[Runbooks/Multiplayer Validation]].

## Updating the normal deployment

Use `bootstrap` when preparing dependencies. Before a game/mod/agent upgrade, make a consistent backup, update the applicable artifacts while stopped, run `check` and the relevant tests, build the affected images, then `start` and inspect `status` and logs. `start` only activates the scenario worker and scenario server hook when `game/scenario.json` enables that world; building scenario artifacts alone does not enable them.

Changing the game mod list or Java agent requires a game-server restart. `start` returns an error when the game is already running: use `stop`, update/build, then `start`. `agent` and `install-mod` also require the game stopped, preventing replacement of the loaded JAR or partial Lua updates during a session. Web-only Observer changes can be deployed separately using the supported service build controls; check the current CLI implementation before assuming a selective restart exists. Game binary updates are an explicit maintenance action, not a startup side effect. Workshop dependencies may have their own update behavior; record the installed manifest after startup.

Inspect game startup/version/agent logs, Observer health and source status, and gateway logs. An HTTP health response alone does not prove game positions or exploration are flowing.

## Scenario tooling and reset gate

| Command | Purpose |
| --- | --- |
| `./dayone scenario-build` | Build the native planner bundle, tested scenario Java agent, local map index and worker image |
| `./dayone scenario-test-create` | Prepare an isolated fresh scenario world and private account/config copies |
| `./dayone scenario-test-start` | Start that disposable scenario server and planner |
| `./dayone scenario-test-rcon <command words>` | Send an explicit command to its loopback RCON |
| `./dayone scenario-test-stop` | Gracefully stop the disposable scenario and its planner |
| `./dayone scenario-reset` | After the acceptance gate, back up/archive the normal world and Observer, configure a fresh scenario, retain accounts and leave services stopped |

The scenario test harness contains the earlier NPC experiment; it is not proof of the revised server-only vehicle architecture. Preserve `AKR_DayOne` until recorded acceptance covers at least two distinct clients and calm opening, routines, civilian/emergency driving, replication, handoff, restart and outbreak. `scenario-reset` checks `artifacts/scenario-tests/acceptance.json`; do not fabricate that receipt or bypass it by manually enabling the scenario.

For the planner's own tests and runtime bundle, use `python3 scripts/build_npc_service.py --test --bundle`. For the scenario agent fixtures, use `python3 scripts/build_scenario_agent.py --test`. These checks do not replace a real two-client playtest.

## Offline navigation preprocessing

```bash
.tooling/venv/bin/python scripts/build_scenario_map.py --bake-navigation
.tooling/venv/bin/python scripts/build_scenario_map.py
.tooling/venv/bin/python scripts/build_scenario_map.py --benchmark-navigation artifacts/scenario-map/vehicle-probe-route.json
```

The first command builds or verifies scored navigation chunks; the second regenerates the
Protobuf road graph and server place index. Source hashes invalidate stale cache entries.
The optional benchmark needs the prepared private route artifact and measures offline
preprocessing, not per-NPC or game-thread latency. These commands prepare local artifacts;
they do not restart or deploy services. Keep generated data under ignored
`artifacts/scenario-map/`. Current coverage is the configured Muldraugh rectangle, with
conservative asphalt and vehicle-clearance checks; live obstacles and turning feasibility
still require runtime validation. See [[Design/Navigation]] for format, bounds, evidence
and the separate route-artifact preparation command.

## Disposable server vehicle probe

The probe has its own fresh world under `artifacts/vehicle-probe/`, a private copy of the built agent JAR and read-only core game files. The default has no loaded game mods; `vehicle-probe-create driver-model` adds only the original `LofersDriverProbe` assets and disposable-server visibility setup. It uses an unoccupied vehicle, no NPCs, no fake players and no planner. Clients use the ordinary game, plus that asset mod when selected. The probe deliberately does not pause when empty; whether native physics advances without a real player is a test result to record.

Both disposable harnesses use `.132` UDP **16281/16282** and loopback TCP **27035**. Stop the existing scenario test with `scenario-test-stop` before starting the probe; use `vehicle-probe-stop` before starting the other harness. They never share the normal world's save or Observer state.

```bash
python3 scripts/build_scenario_agent.py --test
./dayone vehicle-probe-create
# Optional road mode, after preparing/reviewing the private route artifact:
./dayone vehicle-probe-route artifacts/scenario-map/vehicle-probe-route.json
./dayone vehicle-probe-start
./dayone vehicle-probe-status
```

Once status reports the current server epoch, `./dayone vehicle-probe-control start` requests the driving experiment; `./dayone vehicle-probe-control stop` requests braking/stopping that experiment. `./dayone vehicle-probe-control inspect` retains the bounded route area for up to two minutes without creating a car or initializing physics, for read-only scene inspection. These controls do not start or shut down the server container. Use `./dayone vehicle-probe-stop` for graceful server shutdown. Control requests are bound to the current server epoch. A missing status file means initialization has not yet produced evidence.

Road probes hold the car stationary for 20 seconds after route completion or a terminal
safety stop. A known static parked obstacle now creates a planned stopping point and
`waiting_obstacle`: the car stays present until it clears, the operator stops the experiment,
or five minutes of total queue waiting elapse. Queue time is excluded from the driving
deadline. It may honk based on its test identity's temperament, then requests bounded bypass
planning. Passing is opt-in for a reviewed straight lane course without junction stops.
Prepare the separate artifact with `.tooling/venv/bin/python scripts/scenario_lanes.py --bypass`,
then configure `artifacts/scenario-map/vehicle-bypass-course.json` while the probe is stopped.
The artifact's `bypass` flag enables detached left/right candidate generation, live clearance,
a reserved corridor and a 15 km/h passing ceiling. Its optional `shoulder` flag permits a
temperament-dependent dirt/grass or paved-edge fallback after road options fail; that path
uses a lower speed and conservative planning grip preferences. It still rejects obstacles,
unsupported ground and uncertain observations. An empty-server shoulder refusal/pass now
executes, but the client observed pole ghosting despite successful road-obstacle avoidance.
The corrected planner rejects the original pole detour. Empty-server native pole contact
now works; client presentation/audio remains a separate check. Its artifact is prepared with
`.tooling/venv/bin/python scripts/scenario_lanes.py --shoulder`. It records road-parallel
fixture tiles/headings, not equivalent raw `/addvehicle` commands with their default rotation.
This course explicitly hashes the installed erosion tile definitions; unknown or conflicting
definitions still reject the surface. Passing courses retain the detours' loading margins.
Omitting the flags resets both features to disabled when configuring a different route.
Current scope is one parked obstacle on a short straight block; nearby moving traffic
still stops the experiment. This does not enable bypasses on the earlier junction course.
The probe's native-body, world, registry and chunk-membership fields distinguish server
presence from a client visibility report. These are diagnostics, not measured packets.
Moving/unknown hazards may still terminate with `predicted_vehicle_contact`; assess the
trace and actual client report separately. Remove only exact, recorded parked fixtures
after each obstacle trial. This disposable harness is not the persistent traffic lifecycle.
Remove recorded fixtures during the terminal viewing hold while their chunks remain loaded.
Before restarting, wait for `body_registered=false` and terminal probe status so the managed
test car has completed native/world cleanup. The shoulder validation world preserves the
server's existing physics coordinate frame; do not restore the earlier late global rebase.
The probe owns one bounded Bullet collision map, with Java dedicated-server authority
unchanged. Native headless ServerCells did not activate obstacle bodies. Missing custom mesh
registrations fail chunk preparation. Remove managed bodies before deactivating the map.
Do not infer native collision support for ordinary parked Java cars: registration/lifetime
for those bodies is still a separate gate.

A disposable impact scene can additionally declare `vehicle_probe.impact_target=x,y`
and a private `vehicle_probe.impact_token=impact-<32 hex digits>`. Only a matching tagged
`appliances_com_01_94` pole on the centerline of a straight Bézier course is exempted;
approach and runout must each be at least 15 tiles. Bypasses/stops cannot coexist with it.
Players within 12 tiles of the route stop the experiment. An actual stock crash enters
braking and the normal 20-second viewing hold. Native physics and stock damage stay enabled.
This is operator test tooling, not a general traffic-incident trigger. Configuring an ordinary
route revokes the target/token automatically. `inspect` can load the scene before creating
its exact tagged fixture; absent or mismatched fixtures must be reconciled before retry.

Route configuration requires the probe server to be stopped. It validates bounded numeric
waypoints, keeps the previous private configuration and source artifact, and shares an
operation lock with preparation/start/stop. The Java runtime separately validates route
geometry, loaded road surfaces and live hazards. Omit route configuration only when
deliberately reproducing the older straight-line experiment.

Record agent/game hashes, observed movement, authority changes and results from separate ordinary clients in [[Templates/Experiment]]. Inspect the private probe status/logs before claiming physics or replication works. This probe does not demonstrate NPC seats, behavior or combat ownership.

## Java inspection

From the project root, `python3 scripts/decompile_java.py zombie.vehicles.BaseVehicle` produces private source reconstruction and bytecode evidence using the pinned project-local decompiler. See [[Runbooks/Java Inspection]]. Inspection does not alter installed game files; source evidence and runtime proof remain separate. Decompiled game code is excluded from source sharing and mod packages.

## Addresses and boundaries

| Target | Address |
| --- | --- |
| Game | `192.168.1.132:16271`, additional UDP 16272 |
| LAN map | `http://192.168.1.132:8099` |
| HTTPS map | `https://map.lofers.net:8453` |
| Direct local app | `http://127.0.0.1:8098` |
| Storyteller debug page | `http://127.0.0.1:8098/debug/storyteller` |
| RCON | `127.0.0.1:27025` |

Forward router UDP 16271/16272 and TCP 8453 to `.132` for WAN use. DNS must point to the public address. Test WAN from outside the home network; a LAN test does not establish router forwarding or NAT loopback behavior. The gateway performs DNS certificate validation using protected Porkbun credentials; DNS record updates are not implied by a container restart.

The public and LAN gateway listeners must return 404 for `/internal`, `/internal/*`, `/debug` and `/debug/*`. Never forward the direct app/debug or RCON port. The debug view is intentionally local and read-only.

## Files and logs

Game config lives in `data/Zomboid/Server/AKR_DayOne.ini` and `AKR_DayOne_SandboxVars.lua`; world saves in `data/Zomboid/Saves/Multiplayer/AKR_DayOne/`; logs in `data/Zomboid/Logs/`. Observer state is `data/observer/`. The copied baseline files under `references/` are references, not live config.

Credentials are in protected `secrets/` files. Do not paste them into logs, issue descriptions, vault pages, screenshots or shared mod packages. All new tools, caches and image layers use `.tooling/`; `scripts/podman-local` supplies project-local Podman storage. Runtime sockets can use `/run/user`.

When troubleshooting, compare current source/build identity with the last passing [[Experiments/Implementation Ledger|ledger]] entry. A Java-agent guard rejection means inspect the build mismatch before changing hashes; bypassing a guard is not compatibility validation. Source history belongs to `Akryllax/TheBeginningPZ` with its existing GPL-3.0 license; source commits do not imply a tested binary release or Workshop publication.

### Extended brake-failure impact experiment

Only the disposable marked-pole scene can set `vehicle_probe.extended_impact=true`.
Its reviewed route artifact must include `extended_impact: true`, a tile `impact_target`,
and `impact_token` matching `impact-` plus 32 lowercase hex characters. Configuration rejects
curves away from a straight line, bypass, scheduled stops, missing target identity, courses
over 480 tiles, or speed ceilings above 100 km/h. The stock vehicle limits still apply.
Ordinary route configuration clears the extended mode and target/token together.
Only this marked extended scene additionally accepts `vehicle_script: "Base.SportsCar"`;
it uses the installed stock script and a larger validated footprint. No custom engine power
or maximum-speed edits are applied.

Private brake-failure trials damage only the newly created managed car's four brake part
and inventory-item conditions, recalculate stock part stats, and transmit the updates. Zero
condition retains residual stock braking: record available force, driver request, actual
applied force, approach speed and post-impact part conditions. Do not equate a 0% brake part
with zero force, peak speed with contact speed, or a decorative driver with a living occupant.
Require terminal phase, no native body and exact tagged-fixture cleanup before a rerun.
