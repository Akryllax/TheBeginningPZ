---
type: experiment-ledger
status: first-week-implementation-in-progress
updated: 2026-09-26
---

# Implementation ledger

This ledger separates requested design from measured implementation evidence. The deployed
0.1.0 prototype remains an observation world. The 0.2.0 First Week implementation is under
development; it is not a complete or multiplayer-validated release.

## Navigation and road-turn iteration — 2026-09-26

- Added [[Design/Navigation]]: original installed-map stacks, floor definitions and road
  geometry produce content-identified navigation chunks. Two bytes per tile record surface
  cost and conservative quarter-tile clearance. Asphalt beneath sidewalk overlays is blocked.
  Vehicle graph edges require a swept footprint and use a bounded clearance penalty; no
  diagonal shortcut across a sidewalk is accepted. The native worker still reads the
  existing prebuilt Protobuf graph; no worker wire-contract change was required.
- Initial Muldraugh coverage contains 1,081,600 tiles, 50,423 permitted asphalt tiles and
  24 chunks occupying 3,146,304 bytes. The conservative graph has 1,076 nodes and 2,426
  directed edges. This excludes uncertain/narrow surfaces and does not establish driving
  coverage of the whole map, lane discipline, traffic rules or collision avoidance.
  The final first bake including its prerequisite raster took 6.508 s; a validated cache
  open took 80.3 ms and cached graph generation 1.932 s. These are offline costs, not
  measured per-NPC A* latency. Exact reproduction commands are in the navigation page.
- The server probe now accepts bounded waypoint routes, retains bends, steers with native
  force/brake control, slows for turns and checks loaded road/actor/car hazards. The probe
  is still one empty car in an isolated world. Route configuration is serialized with
  start/stop/preparation and allowed only while stopped; previous private configuration
  and source-route provenance are preserved.
- The native physics guard now hashes the first library the pinned JVM would actually
  select, using its startup-captured library paths. Mutable Java properties cannot mask
  an incompatible startup path. Eight subprocess path cases, premain, the pzexe bootstrap,
  Java IPC and controller fixtures passed. Core game files remain read-only.
- **Actual server-native asphalt turn passed**, with zero clients connected, agent SHA256
  `f135a657cc1f4f119ffd9d18d8e35bcc72114567bc84844caf982d6716763c51`, epoch
  `70d34a75-51f1-4344-8962-b325fa6fd9ad`. The six-point planned route was 55.2117 tiles;
  physical travel was 53.8780 tiles, including corner rounding and arrival tolerance.
  Heading changed from 90 to 180 degrees, maximum speed was 4.0002 km/h, and final speed
  was 0.05275 km/h at `(10818.5,9839.1796875)`, 0.6797 tiles from the requested endpoint.
  All 307 planned road tiles passed loaded-world validation. Largest captured cross-track
  distance was 0.5933 tiles (sampled evidence, not an every-tick maximum). Native bodies
  returned from zero to one to zero, and all three owned terrain cells were removed.
  Final status was `complete`, reason `route_arrived`, with no error.
- The measured 650 active warm probe ticks had mean 0.1263 ms, histogram p95 upper bound
  0.4 ms, p99 upper bound 0.6 ms, and maximum 1.0824 ms; none exceeded 2 ms. These timings
  cover the hook's control/check work, not total game/Bullet/network frame time. Cold
  vehicle creation cost 35.45 ms and cleanup 12.36 ms. A single empty-server car is not
  evidence of production capacity. Raw private evidence is under
  `artifacts/scenario-agent/road-turn-70d34a75-51f1-4344-8962-b325fa6fd9ad/`.
- Final validation: **116 project tests passed**, alongside native core/real IPC and
  the Java/controller/bootstrap fixtures. Review found and fixed stale prerequisite road
  masks when source bytes changed without size/timestamp changes; a real binary-map
  regression reproduces that case. Full-source identities now namespace all navigation
  and route-evidence prerequisite masks. Geometry sampling rejects nonfinite parameters
  and caps the total route at 32,768 samples before doing work.
- The disposable server acknowledged quit, logged completed world saving and shutdown,
  exited with code zero, and closed its RCON port. Final navigation/graph identity is
  `f086062b762a6096d7d6123b33d43b36ad0d75061c5ef27f7aac36494f95b366`.

This is a prepared and exercised turn controller, not full civilian driving. No client
observed the turn. NPC driver presentation, normal-world collision/ownership integration,
late joins, two-client comparison and the accepted full scenario remain open gates.
The original playable prototype and the `.160` production deployment were not changed.

## First Week iteration — 2026-09-26

- The accepted target is [[Design/First Week]]: calm civilians, manual outbreak, seven-day
  progression, real civilian/emergency driving and persistent survival behavior.
- The user selected [[Decisions/0004 Server Runtime Extensions]]: server JVM injection is
  allowed, clients use ordinary Lua, installed core game files remain unchanged. The client
  Java prototype was never installed or launched and is superseded.
- The C++20/Lua planner, bounded Protobuf IPC, server bridge, admin UI, map-index generator
  and initial resident/action model are implemented. The worker passes core and Unix-wire
  tests and an independent Java round trip. Real-map batches of 32 driving residents measured
  42.65 ms median / 44.90 ms maximum IPC roundtrip in the recorded synthetic benchmark.
- A six-step native planner → actual Lua `Model.acceptPlan`/`Server.receipt` fixture passed
  for road parking followed by walking to a building 25 tiles from the road. This checks
  acceptance semantics, not actual vehicle movement.
- The guarded JVM fixtures pass, including the real bundled pzexe launcher-discovery stage.
  The initial disposable boot exposed and led to fixing that startup-stage distinction.
- Added private First Week fields to Observer diagnostics. Five focused Python tests,
  frontend build and Java/Python fixtures for five exporter failure modes passed. New output
  is built locally; the playable world's mounted exporter has not been replaced.
- Installed pinned Vineflower 1.12.0 and added [[Runbooks/Java Inspection]], a focused skill,
  reusable decompile command, exact `javap` output and per-run provenance. Vehicle classes
  decompiled without warnings; game-derived output is ignored and excluded from distribution.
- Prepared and booted the disposable `LofersVehicleProbe_20260926_185224_d59d95` world.
  RCON responds with zero players. It has no game mods or client agent requirement, and
  core game files are mounted read-only. The first explicit attempt initialized the native
  library/world and registered a body, but the car fell below the terrain before driving.
  The height guard stopped the attempt and restored the native body count to zero.
  A second attempt explicitly activated the road's native chunk map and uploaded 20 chunks;
  it also fell below the terrain (physics Z −0.767), with no horizontal travel. Cleanup
  again restored zero native bodies. Native inspection then established that server physics
  reads a separate list of cells, each five game chunks wide, instead of client chunk maps.
  Creating the two required native server cells resolved the ground-contact failure.
- **The bounded server-only motion probe passed** with agent SHA-256
  `106c3f900cb80a89e755ab881a9f9a81ae61b0d6980cca1f79198c09d97599b3`:
  settled → drove → braked → stopped → cleaned up. The car traveled 10.0547 tiles along X,
  reached 4.8211 km/h, stopped at 0.05275 km/h and retained physical Z 0.13951.
  Native vehicle bodies returned from one to the zero baseline, and both owned native
  terrain cells were removed. The probe recorded 175 active physics frames and 176 dirty
  position publications; these are not packet measurements. It used the normal physics
  update path, with no extra simulation step, fake players or coordinate-driven movement.
  No client was connected. See `scenario-agent/VEHICLE_PROBE.md` for the exact pinned native
  library and evidence paths; normal-world integration, collision ownership and NPC seats
  are separate unpassed gates.
- **One ordinary client observed visible, smooth movement** in a subsequent run with one
  player on port 16281. The player identified the path as the sidewalk and initially
  described brief acceleration before disappearance. This was a fixed straight test strip,
  without road-following steering; removal after the short stopped hold was intentional.
  Server evidence recorded 10.09375 tiles, continuous Server/-1 ownership in captured live
  samples, no error, zero remaining native vehicle bodies and both native cells removed.
  Client-visible duration and interpolation latency were not measured, and no second
  client participated. The one-client peak probe tick was 170.6 ms, so no production
  performance claim follows from this test.
- Connected the workspace to `Akryllax/TheBeginningPZ`, retaining remote main commit
  `7b528a3` and its GPL-3.0 license. Read-only candidate audit found no actual credential
  matches or game/decompiled binary payloads. Raw live baseline configuration and generated
  map-index Lua are now ignored. Initial source commit `6cd5045` was pushed to
  `work/first-week-server-runtime`; remote main remains unchanged. The GitHub connector
  returned HTTP 403 when creating a draft PR, so no PR was created.
- Added project skills for native planning, NPC replication, scenario acceptance and Java
  inspection. Each new skill passed its structural validator.
- The ordinary-Lua pedestrian rewrite is implemented: verified callback capture, scoped
  target-effect isolation, native owner/lease checks, bounded contact reports and server
  validation. Client Runtime has no Java-helper calls. Native Kahlua compilation verified
  all ten scenario Lua files and the four callback signatures. Contact animation/armor
  behavior still needs real gameplay validation; vehicle actions are explicitly rejected
  pending the separate server physics experiment.
- Final automated project run for this checkpoint: **61 project tests, 116 Observer tests,
  and the frontend build passed**. Java bridge/premain/control-file fixtures also passed.
  The probe exposed missing empty-world physics initialization and missing native-library
  initialization; fixes use the shipped `Bullet.init()` and `WorldSimulation.create()` on
  explicit probe start. No game file replacement or client injection was used.
- Follow-up worker-outage fix: an advancing completed observation must renew a five-second
  freshness window. Missing/stale replies hold scenario time, disease/regional decisions,
  unfinished routines and materialization. Recovery requires an observation published after
  the hold; paused empty observations continue, so recovery does not depend on pending work.
  Native reconciliation, leases, defensive reactions and validated physical damage continue.
  Confirmed contact exposure is retained until recovery at the frozen scenario time. Manual
  pause remains independent, and Observer's existing worker-health field reports the hold
  reason. **68 project tests passed**, including seven new regressions; peer review passed.
  This has not been deployed or exercised with real multiplayer clients.

Outstanding gates include real-world ordinary-Lua effect isolation, resident lifecycle,
treatment/contact correctness, infected corpses, complete regional/adaptive progression,
live worker-outage behavior, normal-world vehicle physics integration and collisions, NPC occupancy,
and two-client convergence. The private `artifacts/scenario-tests/integration-audit.md`
records specific implementation gaps. The prototype has not been reset or archived for release.

The following sections preserve evidence for the earlier observation-only deployment.

## Project bootstrap evidence

- Project created under `/var/home/akr/Documents/Projects/ZomboidDayOne`; isolated source, data, secrets, artifact and tooling paths established.
- Observer source copied independently; `references/observer-source.json` records file hashes. The original source repository had no HEAD commit.
- Live baseline config copied read-only; new world/port identity and credentials created separately. No old world save is used as this world's starting state.
- Root/scoped agent guidance, five focused skills and this Markdown vault created. Skill/link validation results are recorded below when run.
- All five `.agents/skills/*/SKILL.md` files passed the local skill-creator `quick_validate.py` validator on 2026-09-26. This validates frontmatter/naming/scaffold structure; it does not prove in-game behavior.
- Root README/SKILLS Markdown links resolve, and `./dayone --help` matches the documented command list. Final vault validation checked 22 Markdown files and 76 wikilinks: no missing/ambiguous targets, no missing local Markdown links and valid design-page frontmatter.
- Deployment work reports the baseline dedicated server reached RCON readiness and completed a graceful quit. The full companion mod subsequently initialized in observation mode on the dedicated server without Lua errors; Linux animation case aliases resolved the identified Bandits animation-node errors. This is startup evidence, not a real-client NPC test.

## Validation status

| Gate | Status | Evidence / limit |
| --- | --- | --- |
| Project skills | Passed structural validation | All five skill-creator validators passed, 2026-09-26 |
| Vault links/frontmatter | Passed | 22 Markdown files, 76 resolved unambiguous wikilinks, all local Markdown links present |
| Local tool bootstrap | Prepared | Project-local Python 3.12.12, pinned Node/uv archives, dependencies and browser assets; tool/image manifests under `references/` |
| Configuration and container build | Built and restarted | Final game/Observer/gateway recreation completed; game had zero restarts/OOM events and RCON reported zero players |
| Fresh baseline server boot | Passed startup/RCON/shutdown | No real client evidence |
| Companion dedicated-server initialization | Passed startup observation | Final companion initialized in observation mode without storyteller Lua errors; real clients not tested. Upstream warnings remain, listed below |
| Observer automated tests | Passed | 114 Python tests, two browser tests and Java/Python cross-language validation; fixture covered five failure modes |
| Observer debug browser | Passed | Actual loopback debug page loaded without JavaScript errors; screenshot and fixture log under `observer/artifacts/` |
| Project/Lua automated tests | Passed | 19 Lua 5.1 tests via `lupa.lua51`, plus Ruff checks; see implementation page for coverage |
| Operations automated tests | Passed | Four checks: Observer SQLite backup/restore round trip, interrupted archive write, retention of completed backups only, and failed graceful stop preventing backup |
| Bandits source/API audit | Completed for observation release | Current V2 and old Week One MP interfaces differ; no active spawn/cleanup adapter claimed. See [[Research/Bandits Compatibility]] |
| Storyteller Lua implementation | Observation release implemented | Field/scanner, inference, persistent state, decision previews, recovery/limits, native schedule suppression and telemetry; see [[Design/Storyteller Implementation]] |
| Actual telemetry delivery | Passed with empty server | Real Lua → Java → protobuf → Observer reached tick 185 after the high-resolution-clock build, mode `observe`, phase `outbreak`, zero players, health `observing_native_spawns_disabled`. `PauseEmpty` was briefly disabled for each check and restored to true |
| Gateway boundary | Passed local/LAN checks | Observer LAN returned HTTP 200 from `.160`; loopback debug returned 200, LAN debug returned 404 |
| HTTPS certificate/routing on LAN | Passed | `map.lofers.net:8453` resolved explicitly to `.132` returned HTTP 200 with a valid certificate; this does not test WAN forwarding |
| Two-client NPC ownership/persistence | **Not tested** | Requires [[Runbooks/Multiplayer Validation]] |
| Empty-server timing | Measured, limited | High-resolution sample at tick 185: Lua last 0.042 ms, p95 0.377 ms, p99/max 1.125 ms; Java capture 0.194 ms. No online-player/storage/NPC load in this sample |
| Java bounded-capture fixture | Measured, synthetic | 200 warmed samples in each of five modes with 70 cells/40 decisions: p95 0.083–0.115 ms, p99 0.298–0.434 ms, max 0.404–0.769 ms. Combined-hook cold initialization reached 12–13 ms |
| Field inference and latency under player load | **Not measured** | Empty-server timing does not validate [[Design/Performance Budget]] scenarios |
| Consistent backup / automated restore | Passed | Final archive checksum and isolated SQLite checks passed; exact artifacts below |
| Playable isolated restore | **Not tested** | Archive/SQLite validation is a separate lesser check |
| WAN/router connectivity | **Not tested** | Requires direct forwarding and an external connection |
| Private companion package | Built and checksummed | Original `LofersStoryteller` 0.1.0 ZIP; checksum below |

Observer test procedure, fixture limits and evidence paths are documented in [the telemetry validation note](../../observer/docs/storyteller-debug.md). The fixture log is `observer/artifacts/storyteller-agent-test.txt`; the live debug screenshot is `observer/artifacts/storyteller-live.png`. These checks do not establish NPC multiplayer behavior or loaded-base scan performance.

## Deployment artifacts

- Runtime feed evidence: `artifacts/runtime-storyteller.json`.
- Final profile-enabled guard check: `artifacts/final-guard-smoke.json`; fresh mode `observe`, health `observing_native_spawns_disabled`, `PauseEmpty=true` restored, followed by an acknowledged RCON world save.
- Deployment state, test results and remaining gates: `artifacts/deployment-status.json`.
- Endpoint boundary evidence: `artifacts/endpoint-boundaries.json`.
- Installed original-mod hashes: `artifacts/installed-mod.json`.
- Verified private backup: `backups/AKR_DayOne-20260926-145311-827862265.tar.gz` plus its SHA256 sidecar. It includes secrets and must remain private; it precedes the final built-in-clan availability adjustment below.
- Isolated restored files: `artifacts/restore-tests/20260926-145333/`.
- Client package: `artifacts/LofersStoryteller-0.1.0.zip`, SHA256 `dbb456278426e186c6bf00ff2c48a4c4880124939f8218596ec8785031545f00`.

## Startup warnings retained for review

The final startup still reports upstream FluidLargeBucket/FuelPump sanitization, missing fence `ThumpSound`, mannequin-zone, duplicate basement ID and `map_meta` invalid-room skip warnings. These were not resolved by this deployment and should not be described as a completely clean log. The identified Bandits animation-parser errors and this project's missing-directory errors are gone. No storyteller Lua errors were seen during the empty-server smoke check; real-client behavior remains untested.

## Known rollout limits

The original companion must be installed on clients as well as the server; the Java Observer exporter is server-only. Hostile storyteller events remain gated on real integration evidence, and native Bandits scheduling is disabled during observation. The plan includes candidate event families beyond the initial executable implementation; consult implementation notes for actual support. Observer debug is read-only and local. Travel replay remains outside this task.

For the manual two-client trial, `General_OriginalBandits=true` makes built-in clan profiles available in the admin UI. The companion still sets their native scheduling chances to zero; this flag does not enable automatic encounters. See [[Research/Bandits Compatibility#Concrete next two-client trial|the prepared Clan Karate trial]]. The configuration adjustment was applied by a graceful restart; RCON reported zero players and the companion initialized successfully again.

For new evidence, add the command/scenario, date, exact build, result, artifact path and remaining limits here or link an experiment created from [[Templates/Experiment]].
