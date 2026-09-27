# Live event runtime for testing and the Storyteller

## Summary

Build a persistent server runtime that can spawn vehicles, run events, reload behavior and execute test batches without restarting Zomboid for each iteration.

The test runner and future Storyteller will use the same event API. The server remains authoritative for entities, physics, replication and saved state. The C++ service continues to provide planning proposals.

The first deployment requires one test-server restart. Changes to native hooks, the runtime’s public interface or game-version compatibility may still require restarts; scenario parameters and compatible behavior modules will reload live.

## 1. Separate the runtime from individual tests

- Extract native-world ownership, terrain loading, entity registration, physics controls and cleanup from `ServerVehicleProbe` into a resident runtime.
- Maintain one owner of the native physics world and shared terrain regions. Individual events must never initialize or tear down competing physics worlds.
- Give every event an ownership record covering its vehicles, fixtures, terrain leases and reservations. Cleanup removes only resources that event owns.
- Initially allow one event at a time, supporting two moving vehicles and up to eight owned entities in total. This covers the pending opposing-car test while keeping resource usage bounded.
- Preserve stock vehicle capabilities, collisions, damage and replication. Intentional collisions exempt only the named participants from mutual avoidance; other actors and obstacles remain protected.
- Reconcile the existing unfinished opposing-car work during extraction. Preserve unrelated working-tree changes.

## 2. Provide a shared event API

Add a versioned event interface with these operations:

| Operation | Purpose |
|---|---|
| `submit` | Validate and queue an event; return its stable identifier |
| `status` | Report preparation, execution, cleanup and outcome |
| `cancel` | Request a controlled stop followed by cleanup |
| `outcomes` | Read completion receipts and measurements |
| `reload` | Stage and activate compatible behavior modules; operator-only |

Expose it through a private Unix socket for tooling and a server Lua adapter for the Storyteller. Both enter the same scheduler and lifecycle.

- Reuse the existing Protobuf tooling and framing, with a separate runtime protocol. Keep planner traffic separate.
- Include world identity, server epoch and request identifiers. Reject stale commands and deduplicate retries within the current epoch.
- Replace the current latest-command mailbox with a bounded FIFO. Return explicit busy responses when full; cancellation must remain available.
- Distinguish command acceptance from event completion. Completion requires successful cleanup, or an explicit retained-aftermath result.
- Use typed event definitions containing seed, parameters, location, participants and module version. Do not expose arbitrary Java/Lua evaluation.
- Start with route driving, obstacle avoidance, static impacts and opposing-car impacts. Reuse the existing validated setup and observation helpers.
- Keep teleportation, forced weather/time changes and test fault injection behind the disposable-world operator policy.
- Store Storyteller event identities and outcomes in server-owned state. After a restart, mark unfinished execution interrupted and reconcile tagged entities; never automatically replay a crash.
- Leave automatic Storyteller scheduling disabled in this milestone. The API and lifecycle will be ready for it.

## 3. Reload behavior safely

- Split the startup agent into a resident engine adapter and separately built behavior modules.
- Load each Java module version through a fresh classloader sharing a stable interface with the resident runtime.
- Modules receive detached observations and return bounded intents. They do not retain game objects, own native bodies, register unmanaged callbacks or create their own threads.
- Stage and validate candidates off the game thread. Activate them only after the current event and its cleanup finish, as selected.
- Pin each running event to its module version. Keep the previous known-good version available if candidate activation fails.
- Use a permanent Lua dispatcher with replaceable module implementations so reloads cannot accumulate event listeners. Keep persistent state outside replaceable closures.
- Continue using the existing worker’s restricted Lua environment for expensive planning rules; game-side Lua remains small.
- Treat modules as trusted project code. Reloading does not provide isolation from arbitrary Java failures, and reverting code cannot undo completed physical effects.

## 4. Add repeatable batches and performance controls

Extend `./dayone` with runtime commands for loading definitions, running events, inspecting status, cancelling, reloading and executing batches.

- Use JSON scenario and batch definitions with parameter variants and explicit seeds.
- **Unattended mode:** run sequentially on an empty disposable server.
- **Watched mode:** require observers in the reserved viewing area and pause between cases until the operator advances.
- Abort or safely stop when observer safety conditions change. Do not silently relocate newly joined players.
- Before advancing, verify owned entities, native bodies and reservations have returned to baseline. Failed cleanup stops the batch.
- Use generated fixtures for repeatable cases. Tests that alter existing world geometry run against a fresh disposable snapshot rather than claiming cleanup restores the map.
- Produce per-case JSON results and a readable report with versions, seed, timings, actual speeds, collision evidence, damage, cleanup and failure reasons.
- Keep parsing, module loading, planning and report writing off the game thread. Perform engine mutations only on the game thread.
- Share one incremental-work budget across events, rather than allocating a full budget per vehicle. Prioritize control, braking and cancellation.
- Measure preparation, spawning, native calls and cleanup separately. Use the existing performance targets—approximately 1 ms incremental work, added-work p95 below 2 ms and p99 below 5 ms—as acceptance targets, not guarantees that native calls can be interrupted.

## 5. Validation and rollout

Validate in this order:

1. Protocol tests: duplicate requests, stale epochs, malformed definitions, queue saturation and cancellation.
2. Lifecycle tests: partial preparation failure, module failure, disconnects, interrupted runs and cleanup recovery.
3. Reload tests: change parameters, Java behavior and Lua behavior while retaining the same server process and epoch; verify no duplicate callbacks or retained event resources.
4. Native tests: driving, stopping, avoidance, static impact, then low-speed two-car contact before faster opposing-car tests.
5. Soak test: at least 100 cases and 20 reloads, checking resource counts, retired module references, memory trends and tick latency.
6. Watched validation: movement, visibility, sound, damage and responsiveness. Record two-client replication as a separate required gate before normal campaign deployment.

Deploy first to the disposable server at **192.168.1.132:16281**, using a test-specific backup and one coordinated restart. Keep the normal `.132` game and `.160` production server unchanged.

Document module authoring, event definitions, batch operation and recovery. Routine iteration should then be: **build module → stage reload → run batch → inspect results**, with the game server staying online.
