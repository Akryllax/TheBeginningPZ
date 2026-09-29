# Lofers scenario gameplay bridge

This is a separate gameplay Java agent for **Project Zomboid 42.20.4 / b0bbce05d5,
Java 25**. Observer remains a read-only service and exporter. The agent changes
class definitions in memory and never writes a game JAR.

The active integration uses **server-only injection and ordinary clients**. The client-helper
and owner-client NPC-control sections below document retained prototype APIs; they are not
the current deployment direction. The validated vehicle probe explicitly owns server native
bodies and stock replication. Do not install a client Java agent.

Current priority: [live event runtime](../vault/Design/Live%20Event%20Runtime.md) and
[shared scheduler plan](../PLAN_SchedulerAPI.md). Read the
[current handoff](../vault/Experiments/Current%20State.md) before building. Resident world/terrain
ownership and scheduler integration are implemented in source but not deployed/native-validated.
The legacy opposing option rejects explicitly. Dynamic event submission and reload remain pending.

Build and run the independent fixtures:

```sh
python3 scripts/build_scenario_agent.py --test
```

For the combined runtime suite and retained reports, use `./dayone runtime-test offline`.
Selections `unit`, `integration` and `worker` run narrower layers. The Java builder also accepts
`--test --test-layer unit` or `--test --test-layer integration`; plain `--test` runs both.
See [Runtime Testing](../vault/Runbooks/Runtime%20Testing.md) for coverage and outstanding gates.

The project-local pinned JDK, protoc and protobuf runtime must already exist in
`.tooling/agent`. Output is `artifacts/scenario-agent/lofers-scenario-agent.jar`
and `manifest.json`. The manifest includes critical class hashes, the JAR hash
and the required upstream Bandits callback source hash. No game or Bandits code
is packaged in our JAR. Protobuf runtime license notices are retained.

## Launch configuration

Pass an absolute properties path through
`-javaagent:/path/lofers-scenario-agent.jar=/path/scenario.properties`.

```properties
side=server
scenario.enabled=true
world=AKR_DayOne
socket=/run/lofers/npc.sock
bandits_update_file=/pzserver/steamapps/workshop/content/108600/3268487204/mods/Bandits/42.20/media/lua/client/BanditUpdate.lua
```

Client helper properties use `side=client`, `scenario.enabled=true` and a path
to the client's matching `BanditUpdate.lua`. No IPC socket is used by clients.
Optional `server_epoch` overrides the generated JVM boot UUID; `registry_hash`
is passed in the native handshake. Use the configured server name as `world`.

With Observer, put **Observer's `-javaagent` first**. The existing Observer
transformer requires original RCON method bytes. This agent verifies original
class resources, then appends its own RCON update hook to the already transformed
method. Every gameplay hook is eagerly loaded and checked before world loading.
An unknown build or mismatched Bandits source fails scenario launch; it does not
quietly start an ordinary world with missing gameplay protections.

The native launcher first starts a separate `zombie.pzexe` JVM to discover its
JNI library. That process inherits `JAVA_TOOL_OPTIONS` but has no game classes.
Only its exact command, single `pzexe.jar` classpath and pinned helper class
hash are exempted. The subsequent actual game JVM still performs every guard.

`scenario.enabled=false` installs no hooks. A client launched with the helper
but connected to a world without `SandboxVars.LofersScenario.Enabled=true` has
no scenario gameplay effects. The availability table can still be inspected.

## Game-thread Lua API

Global `LofersNative` is installed after Lua initialization, with an event-entry
fallback. Calls use a dot (`LofersNative.bind(...)`), not a colon. The live game
thread is claimed when the first tick/action runs, not during threaded loading.

| Function | Contract |
| --- | --- |
| `versionReady()` | Verified native helper is loaded. This is not an NPC playtest result. |
| `spawn(factoryClosure, argsTable)` | Server only, paired scenario required. Runs the closure synchronously with a thread-local spawn permit and returns `ok, resultOrError`. A `finally` restores the permit even on Lua failure. At most 16 canonical zombie-factory creations per scope. |
| `bind(zombie, id, generation, leaseEpoch, ownerId)` | Tags a managed resident; older generations cannot overwrite newer ones. Does not perform Bandits visual/brain initialization. |
| `ownerId(zombie)` | Native owner player's online ID; on a client without the native player reference, returns the replicated lease owner. Server unknown ownership returns `-1`. |
| `canAct(zombie)` | For clients, requires a matching local player lease and native `isRemoteZombie()==false`. |
| `lease(zombie, leaseEpoch, ownerId)` | Rejects older leases. Server ownership uses `NetworkZombieManager.moveZombie`, including engine bookkeeping, and confirms the resulting owner. Vehicle authorization follows a seated driver's confirmed owner. |
| `enterVehicle(zombie, vehicle, seat)` | Authoritative operation. Requires a managed actor, installed empty seat, nearby stopped vehicle, and a known driver lease. Uses `enterRSync` to avoid player-only enter packets. Server requests engine start and assigns native vehicle authorization. |
| `exitVehicle(zombie)` | Authoritative operation; rejects a moving vehicle. Releases controls and native vehicle authorization if no driver remains. |
| `syncSeat(zombie, vehicle, seat, enter, generation, leaseEpoch)` | Client presentation operation for an already server-validated seat message. Checks the generation/lease, permits nonowner replicas, uses `enterRSync`/`exitRSync`, sends no packet or physics command. |
| `controlVehicle(vehicle, throttle, brake, steer)` | Owner client only. Inputs are clamped; a command expires after 400 real milliseconds. |
| `removeOwned(zombie)` | Queues native network deletion while the actor's online ID is valid, then removes the managed actor and flushes the update. Refuses unsafe moving-vehicle removal. Lua still owns the durable lifecycle receipt. |
| `populationMode(allowBackground)` | Server only. Explicitly permits or denies autonomous factory creation. Default is denied from JVM startup, before Lua activation. |

Actor metadata is a primitive table at `zombie:getModData().LofersScenario` with
`id`, `generation`, `lease_epoch` and `owner_id`. Only the Lua scenario executor
owns domain decisions and validates received client action receipts.

## Population and callback guards

The startup guard covers `IsoWorld.getZombiesDisabled()` and the canonical
`VirtualZombieManager.createRealZombieAlways(...)` entry. This blocks the room
and story paths which consult the former, plus factory calls that bypass it.
An explicit spawn permit temporarily changes the answer on the calling thread;
it does not toggle global sandbox settings or enable a native spawn window.

Configure fresh-world population/respawn at zero and disable random building,
road and zone stories before generation. The guard does not cleanse an existing
apocalypse save. It does not promise all possible mod-specific direct actor
constructors are covered. Existing corpses and loaded actors require separate
handling. The live two-client population audit remains mandatory.

The `Event.trigger` hook intercepts callbacks sourced from the pinned
`BanditUpdate.lua` only when their arguments include a managed resident or its
tagged corpse. It suppresses upstream planning/hit/death side effects for owner
and nonowner copies. The companion supplies explicit original initialization and
execution. Before semantic binding, the guard also recognizes the replicated
outfit registry and the scenario clan in Bandits' cluster publication, avoiding
an initial autonomous callback when the cluster arrives before our replica.
Unrelated callbacks and ordinary Bandits are unchanged.

## NPC vehicles

The vanilla client controller's engine-start branch casts its driver to
`IsoPlayer`. Managed NPC drivers therefore use a narrow replacement for that
controller step. Only the native vehicle owner applies `Bullet.controlVehicle`
forces. Other clients interpolate. It does not teleport the car.

The replacement limits force to 2000, steering to 0.4, cuts throttle above
35 km/h, brakes above 40 km/h, and brakes when the 400 ms command expires. It
requires a running engine for throttle. The Lua executor owns obstacle checks,
routes, safe stopping and authoritative seat replication. Engine simulation,
collision and synchronization still run through the game. This implementation
is compiled and source-checked; two real clients must verify fuel use, collision,
seat display, ownership transfer and reconnect before calling driving validated.

## Primitive IPC boundary

The server publishes a detached table at
`ModData.getOrCreate("LofersScenario").bridgeOut`. Its fields match
`ObservationBatch` in `protocol/npc_control.proto`, using snake_case names and
Lua arrays. It additionally supplies `world` and the Java-published
`server_epoch`. Lua must adopt the epoch from `bridgeIn` before publishing.

Java copies primitives cooperatively, at most 256 iteration operations per tick
and a one-millisecond deadline. Cycles, game objects, nonfinite values, excessive
depth, strings and arrays are rejected. These are soft work deadlines; a single
engine table operation can exceed them. The service thread receives only the
detached Java primitive maps, performs protobuf serialization, and writes to a
local Unix socket. No game objects or Lua tables cross onto that worker thread.

Frames use a four-byte unsigned big-endian length and a 256 KiB maximum.
Handshake and every reply validate protocol, world, server epoch and request ID;
plan replies also match the observation revision. One request is in flight and
one newer publication can replace the pending publication. Old samples are
dropped; connection/read/write deadlines are bounded. Reconnect never replays a
queue of past actions.

`bridgeIn` is a **flat PlanBatch table** (`observation_revision`, `plans`,
`deferred`, `compute_ms`, `rules_hash`) plus `health`, `guard_ready`,
`server_epoch` and `request_id`. Java writes incoming tables cooperatively, with
a 256-operation cap and a half-millisecond slice. It replaces the publication
only when complete. These plans are proposals; Lua validates generation,
resident revision and current state before committing actions.

## Evidence and remaining tests

2026-09-26 automated checks passed:

- Pinned build resources, deliberate unknown-build rejection and JVM verification
  of all six transformed classes.
- Actual JVM premain loaded every guarded class before a world was opened.
- Bounded primitive copy, cycle/game-object rejection, numeric range checks,
  sparse array rejection, and generated protobuf encode/decode.
- Framed Unix IPC handshake/round trip, incorrect world/epoch/request rejection
  and oversized-frame rejection.
- An independent generated-Java client connected to the real C++ planner with a
  resident observation and received an `eat` action with the correct revision.

No two-client vehicle, NPC death, calm-building or ownership playtest is implied
by these fixtures. The deployment owner records isolated game startup and actual
playtest evidence separately.


## Pedestrian feasibility runtime

The experimental runtime adds a separate typed Protobuf Unix socket and matching server Lua
submit/status/cancel API. It is opt-in only in `AKR_DayOne_Test_*` worlds and mutually exclusive
with the vehicle probe. Narrow ownership/stock-zombie-stream hooks affect only registered
experiment actors. No synthetic locomotion or client Java injection is used.
See [the runbook](../vault/Runbooks/Pedestrian%20Experiment.md) for controls, evidence and limits.
Native walking and two-client consistency remain pending; do not equate the advertised
`pedestrian-experiment-unvalidated` capability with campaign support.
