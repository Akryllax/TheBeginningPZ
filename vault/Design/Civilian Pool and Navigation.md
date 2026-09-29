---
type: design
status: headless-partially-qualified-native-traversal-gated
updated: 2026-09-27
---

# Civilian pool and navigation

The user requested abstract testing first and a later batch of native functional/visual
tests. The user subsequently authorized isolated headless native functional tests.
Baseline behavior is **idle and roam**. The selected navigation target is **full
native traversal**, including doors, stairs, windows, low fences and high walls. This
iteration does not silently reduce that target to ground-only movement.

Related: [[NPC First Slice]], [[../Decisions/0006 Targetable Civilians]],
[[../Runbooks/Civilian Qualification Batch]], [[../Experiments/Current State]].

## Implemented components and evidence

| Component | Responsibility / evidence boundary |
| --- | --- |
| `CivilianPool` | Four online-ID slots (4096–4099), one materialization per tick, exact body ownership, revision fencing, snapshot-before-retirement and verified removal; detached fault/soak tested |
| `AKRPopulation` | Plain ModData ownership ledger, API registration, stale-epoch rejection, restart reconciliation; Lua 5.1 tested |
| `AKRResidents` | Persistent identities, seeded idle/roam/flee/recover FSM, generation-aware controller and injected ports; Lua 5.1 tested |
| `CivilianNavigation` | Immutable layered graph, deterministic incremental A*, 4-job fair queue, unknown/blocked edges and no corner cutting |
| `CivilianGraphCapture` | Incremental local loaded-neighborhood capture; no whole-map scan |
| `CivilianEscape` | Safer endpoint candidates, danger-weighted A*, bounded retries of unreachable candidates, native route safety-envelope validation |
| `CivilianPathRequests` | One job per Actor, two native jobs outstanding, one new submit per tick, cooldown/timeout and stale callback rejection |
| `CivilianPerception` | Paged local-square cursor, at most 8 objects/page and 32 remembered threats; incomplete observations remain explicit |
| `CivilianTraversal` | Shared action sequencing, dynamic validation, cancellation, exactly-once begin contract and current action snapshot |
| `NativeCivilianNavigation` | Compiled path-only adapter to the installed native/Java solver; copies coordinates and flags before pooled request release |
| `NativeCivilianActors`, `NativeActorBodyCodec` | Compiled native pool/stock replication/serialization candidates; headless body restoration and three four-Actor reuse rounds passed; replication remains pending |
| `NativeCivilianGeometry`, `NativeCivilianGraphSource`, `NativeCivilianPerception` | Compiled loaded-world adjacency, native-path classification and LOS adapters; stair graph edges require a classified native route |
| `NativeCivilianTraversal` | Headless collision-constrained walking and native door opening passed; explicitly refuses unimplemented stair/climb execution |

The new mod bootstrap **only registers APIs**. It creates no NPCs and installs no update
loop. The legacy private runtime still submits its existing one-Actor experiment. The
new components are not yet wired to a live multi-Actor operator case or a Lua/Java session
driver. Do that during the native feasibility batch, sharing the scheduler's ownership
ledger rather than adding an independent spawn path. The interactive mod list and deployed JAR remain unchanged. A separate opt-in
`HeadlessCivilianHarness` directly exercises these components in a disposable zero-client
server; it is not the ambient gameplay/session bridge.

## Authority and persistence

The server owns resident identity, intent, RNG, physical pool and effects. Ordinary
clients render stock player replication. Engaging zombies keep the previously accepted
stock client ownership; this iteration does not change that decision or claim to fix
the reopened combat gate. Bandits is not a dependency of the new resident model/body.

Pool slots follow `FREE → RESERVED → MATERIALIZING → ACTIVE → RETIRING → FREE`.
Unknown outcomes enter `UNRESOLVED`, retaining capacity. Pool slots/IDs are reused.
The native adapter retains four initialized objects and reassigns them across residents
after verified retirement and an explicit reset/load boundary.
Every assignment carries epoch, slot generation,
resident identity/generation and an action revision. Old results cannot control a new body.

The adapter reports construction immediately before fallible setup. Removal checks the
owned reference, saved square, world lists and network maps. Native ID replacement blocks
the destructive timeout/remove path because that packet could delete someone else's
replica. Bodies stay owned through partial exceptions and are never released merely because
an ID vanished. No visible recycling; uncertain visibility holds the Actor.

The body codec uses stock `IsoPlayer.save/load(ByteBuffer,...)`, with a 2 MiB cap,
build/world-version tag and SHA-256 envelope. It preserves serialized body/inventory data
rather than regenerating items from types or restoring health percentages. The headless native round trip preserved a scratch, item IDs and condition, equipped
hammer, nested bag/pen and clothing count. Other fields, visual fidelity, process-restart
restoration and migration still need qualification. Known residents require compatible snapshots.
Death/corpses and interrupted saves require reconciliation, not fresh-body respawning.
ModData and the world save are not a cross-file transaction.

## Reusable initialized Actor pool (2026-09-27)

The user rejected resident-affine caching: the goal is to reuse initialized Actors for
**different residents**. That cache is superseded. `NativeCivilianActors.prewarmOne` creates
one Actor per call during explicit warmup (one call per harness tick), before admission.
The pool retains four initialized `IsoPlayer` objects, constructor-owned callbacks and
engine components. `materialize` consumes an available object and **never constructs a
replacement**. Exhaustion is an explicit admission failure. The normal controller must
check capacity before reserving; the headless batch warms all four before starting cases.

After the caller persists a verified retirement snapshot and cancels its action/path jobs,
`park` returns the detached object to a FIFO. No resident affinity or eviction/reconstruction
policy remains. Each activation has a new assignment/replication binding and restores the
incoming resident onto the available body. A new resident uses the body's pristine stock
snapshot and then receives a new descriptor/appearance/outfit. An existing resident uses its
saved stock snapshot. Descriptor instance pointers and exact world-registry entries are
removed on parking/replacement, preventing an old identity retaining the reused body.

`NativeActorReset` clears empty-slot/append-only state that stock load does not replace:
hands, inventory, worn/attached equipment, ModData, book/recipe/literature/media histories,
mechanics history, fitness collections and recent perception. It resets body damage before
loading the incoming state and clears movement/hit/posture, action events, path and network
state. The stock loader supplies the incoming stats, XP/traits, wounds, inventory and visual
state. This is not reflective cloning of arbitrary engine objects or replacement of callbacks.

Never reset a corpse, occupied vehicle, burning, grappling, climbing, attacking, downed or
busy Actor into a new person. Those resources stay owned pending their lifecycle resolution.
Reset/restore failures after ownership transfer quarantine that exact body. Parked bodies
have no world/network registration but stay in the Actor update guard until reuse/shutdown.
Active plus parked bodies remain capped at four. `clearParked` releases only verified detached
references. A resident's durable state exists separately from its physical Actor.

Source provenance: `artifacts/decompiled/actor-reuse/` and `artifacts/decompiled/actor-reset/`.
Inspected classes are pinned in BuildGuard. Stock `IsoPlayer.load` alone is insufficient:
it appends collections, preserves absent hand references, and does not wipe absent ModData.
Native tests dirty a resident, assign a fresh resident onto the same object, then restore the
original resident onto a **different** physical object. Checks cover items/hands, wounds,
recipes/books/literature/fitness, ModData, posture, sex, stale assignments, walking and cleanup.

Stock removal also queues a delayed emitter stop. Parking finishes that owned cleanup and
removes only its exact reference from pinned `IsoPlayer.RecentlyRemoved`, so it cannot stop
the next resident's sounds. No client JVM injection or installed game-file edits are involved.

This boundary is exercised by the isolated headless harness, not enabled as ambient gameplay.
Two-client appearance/replication across reassignment, richer combat/climb transient states,
third-party Actor mutations and sustained capacity remain qualification gates. The reset is
for the implemented ambient Actor contract, not a claim that every arbitrary mod/player state
is safely reset. Normal runtime warmup/admission wiring remains part of controller integration.

## Behavior and budgets

- Pure Lua 5.1 FSM: `IDLE → WALK`, threat interruption to `FLEE`, then `RECOVER`; `BLOCKED`
  retries with backoff. Terminal/unresolved states emit no new movement requests.
- Injected simulation clock and deterministic Park–Miller RNG. No ambient random/clock
  calls in the domain model. Empty-world pauses shift deadlines without replaying a backlog.
- Decisions at most 5 Hz. LOS-visible zombies within 12 tiles trigger escape; 3 seconds
  of threat memory and 3 seconds of recovery reduce oscillation. Stable threats do not
  restart an already accepted escape every second.
- Loaded neighborhood radius 16 tiles, up to 4096 graph nodes, 16 edges/node, 128 route
  nodes, 2048 search work units/job. The four-job queue shares 128 expansions per tick.
- Native request cooldown 1 second, timeout 5 seconds. Callbacks are nonthrowing, copy
  pooled results immediately, and validate generation/revision. Cancel is not synchronous
  solver termination. Actor requests have player priority: do not flood the stock queue.
- Normal roaming uses walk/door/stair policy; fleeing can request all traversal types.
  Unknown squares, locked/barricaded doors, unsafe glass and destructive entry are blocked.
- Local perception pages through square occupants instead of repeatedly examining only
  the first 64 world zombies. The caller must share page budgets across four residents.
- Budgets are explicit operation caps. Full graph finalization/scoring and snapshot
  serialization still need native timing measurement; no claim that the 0.5 ms planning
  soft budget or overall p95 <2 ms / p99 <5 ms targets have passed.

`Residents.Controller` connects plain port contracts: pool reserve/tick/retire/receipt,
Actor observe/stop/follow, and navigation request/poll/cancel. Native callbacks do not
enter saved Lua tables. The controller rejects results from another assignment and holds
ownership on command failure. The optional C++ planner and Observer are not dependencies.

## Native pathfinding findings and stop gate

Inspection artifacts: `artifacts/decompiled/npc-actor-path-adapter-review/` (private).
Pinned game JAR SHA-256:
`80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44`.

Both existing solvers accept `IPathfinder` plus an `IsoPlayer` mover. Their existing
worker/main-thread callback mechanism is reusable without running the player's full update.
Do not initialize another solver, switch the global backend or retain pooled path nodes.
The Java fallback uses A*; the JNI search implementation itself was not inspected.

`PathFindBehavior2.update()` is not a path-only interface: it consumes animation movement
and runs door/climb transitions. Our connectionless Actors currently receive no stock
updates, so merely calling a climb method cannot advance the entire action. Whole
`IsoPlayer.update()` is unsuitable because the dedicated-server remote branch restores
network positions. High-wall outcome calculation also has `isLocalPlayer()` branches.

Therefore stair/window/fence/wall **execution is not implemented or qualified**. The
shared controllers and route types cover them, but the native executor reports a specific
blocker. Required next experiment: narrowly advance Actor state/animation/placement and
compute stock server outcomes once, preserving ordinary Lua clients and stock replication.
Do not globally spoof local-player status, duplicate state-enter effects or teleport endpoints.

The existing `ServerActors` prototype also received exact-reference cleanup tracking,
closed-door/per-step route checks and corpse holds. Runtime timing now includes diagnostic
sampling. IsoPlayer, AnimationPlayer and path classes are pinned in BuildGuard; the
integration suite checks Actor sentinel hook sites and native pooled-path copying.

## Remaining gates

Offline success proves contracts and algorithms, not physics, appearance, body persistence,
network agreement or full traversal. The live session-driver wiring, traversal feasibility,
four-Actor movement/replication and two-client checks remain open. The zero-client
native batch passed body round trip, one walking Actor, three four-Actor lifecycle rounds
and closed/locked/open door checks. These are partial native functional results, not
a complete civilian slice. Creation spikes exceeded timing targets (see Current State). Gate 3 combat
freshness/contact validation, corpse/reanimation lifecycle, crowds, GOAP and household
routines remain separate work. Keep the accepted car suite as regressions.


## Watched reuse findings (2026-09-28)

The user authorized four simultaneous 150-tile passes, repeated four times in one ordinary
client session with changing identities on the same four prewarmed engine Actors. The
opt-in `civilian-watch` harness and evidence live in [[../Runbooks/Civilian Qualification Batch]]
and [[../Experiments/Current State]]. This does not activate the ambient resident controller.

Open presentation defects: the user could see identity changes/reuse but **could not hear
footsteps**. Same-host aligned samples also show several-tile client/server position error;
render-frame skips near relevance/finish transitions need separate classification. These
are not erased by a successful pool lifecycle test. Next replication work should inspect
stock remote-player animation/sound and prediction timing, preserving native replication
and ordinary Lua clients; do not add a second transform stream or play duplicate footsteps
as a shortcut. Recheck audibility with the observer facing nearby moving residents, then
measure position/action agreement and the two-client gate independently.

Audio correction, 2026-09-28: the user subsequently confirmed that the footsteps were
audible. The earlier missing-audio report above is superseded. The authorized 32-Actor
8×4 test is scoped to pooling/straight-corridor movement, not 32 concurrent native path
requests or behavior planners. Their existing budgets are unchanged.
