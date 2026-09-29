---
type: design
status: implementation-in-progress
updated: 2026-09-28
---

# Civilian defense and death

This slice stays on the pinned 42.20.4 server. The 42.21 migration is a separate
compatibility gate. Civilians prefer a known escape path. A blocked, nearby threat
permits a brief shove or one usable melee weapon, followed by another escape attempt.
Unknown path results do not mean trapped. A target is a bounded, generation-tagged
zombie observation; stale targets and player targets are not valid defense inputs.

## Moving encounter iteration — implementation checkpoint

The reusable private runtime now accepts typed civilian encounter definitions. The
new backend retains four warmed Actors across events; the socket exposes the existing
submit/status/cancel lifecycle, with an additive encounter payload and progress metrics.
The ordinary-client diagnostics distinguish stationary fixtures from active pursuit
and scope readiness to event and Actor generation. Stock client-owned hunters still
run native AI; the server uses the existing model, contact and injury adapters.

The default watched batch is six cases / fifteen assignments: open escape, incoming
injury, one defense/escape, then three waves of four. Trap scenes use installed stock
transparent doors, not modified shared sprites. Geometry is owned and removed explicitly.
Read [[../Runbooks/Watched Testing]] and [[../Runbooks/Operations]] for repeat commands.

The injury adapter interrupts movement/combat on a validated attack attempt and applies
a bounded one-second presentation recovery hold. Native floor/knockdown/grapple states
also prevent movement. The timing still requires watched acceptance; it is not proof of
animation-frame alignment. Misses are never converted to wounds. Five native misses
leave the injury gate unqualified. Death or uncertain cleanup stops the batch.

Validation so far: 260 project tests; Java runtime/compatibility fixtures; both new
skills validated. Native headless checkpoint
`artifacts/civilian-headless/20260928-170403-f714ac/ipc/native-report.json` passed thirteen
stationary defense/escape encounters and the existing lifecycle/pool checks (survival
p95 3.4 ms, p99 6.5 ms; total p95 3.7 ms, p99 8.8 ms). That checkpoint precedes the
final encounter profiling/seed additions and does not qualify the new moving backend.
Watched execution and acceptance remain pending until recorded below.

## Implemented boundaries

### Autonomous adapter checkpoint — 2026-09-28

`NativeResidentController` now runs the existing server Lua model against bounded
native perception, a local geometry capture/A* escape search, native traversal and
`NativeCivilianCombat`. No planner process is required for this immediate fallback.
The C++ observation/DEFEND contract is unchanged. Activation is explicit and limited
to disposable fixtures; this is not persistent town-population activation.

`CivilianCombat` owns wind-up, one contact attempt, recovery, cooldown and cancellation.
Actor tokens/action revisions and exact target references/network IDs are checked at
contact. Failed effect calls remain unresolved and are never retried. Native candidate
filtering runs **before damage** and keeps only the selected zombie; scoped world-object
hits are disabled and native scratch references cleared. The original native hit and
stock packet remain responsible for damage/replication. Ordinary Lua plays the native
swing/hit sounds once per server action, including repeated actions. Timing currently
uses a bounded combat-speed-scaled schedule; the single-client fixture was accepted,
while detailed contact-frame alignment and two-client comparison remain unmeasured.

`NativeCivilianInjuries` generalizes the contact adapter to bounded observed hunters:
confirmed native owner, fresh native update, correct target, same floor, reach and LOS,
one effect per observed attack entry. Owner changes during an attack consume that entry.
Native wound processing and stock injury/hit packets are used. Incoming contact from a
real owning client still requires a watched test; detached gate tests are not that proof.

`./dayone civilian-headless survival` exercises the existing native lifecycle suite,
then one resident and three waves of four in the same world. The fixture creates actual
locked native doors, supplies a stationary zombie, and opens the exits after a native
defense contact. Residents choose their own defense and escape through the real model.
Thirteen hammer contacts and escapes passed, with the same four engine bodies reused
and verified retired. The reduced capture uses radius eight, eight nodes per Actor per
tick, and 32 A* polls per Actor. It reduced batch work p95 from 5.1 to 3.4 ms; the release
performance target remains unmet. Evidence:
`artifacts/civilian-headless/20260928-162214-56760b/ipc/native-report.json`.

The subsequent mixed hammer/unarmed-shove run passed all 13 cases as well; p95 was
4.6 ms (`artifacts/civilian-headless/20260928-162551-451fee/ipc/native-report.json`).
Performance varies above the release target; the earlier improvement is not a cap
qualification. Capture now preserves incomplete adjacent geometry as unknown, so a
missing chunk cannot authorize defense by masquerading as a confirmed blocked route.
256 project tests and the Java unit/integration/build-guard suites pass. Audio tests
exercise repeated action IDs, stale observations and unrelated attackers/targets.

The watched fixture is `./dayone civilian-combat survival-create`, then `start`
and `join`. It retains automatic observer placement/protection, daylight, chat, viewing
hold, loaded pistol and cleanup. This is the stationary-threat defense/escape gate.
On 2026-09-28 the user confirmed “Test succeeded” for world
`AKR_DayOne_Test_20260928_163023_07ce36`. The automated harness completed one native
contact, escape over seven tiles and cleanup; separate human acceptance is stored
in that world's `survival-human-acceptance.json`. This closes the single-client
stationary-threat watched gate only.
The desired **moving pursuit → trapped defense → escape**, owner handoff, injury/death
during that encounter, and two-client replication remain separate acceptance gates.
Do not describe the stationary fixture as a completed autonomous zombie chase.

### Earlier policy and lifecycle work

- `npc_control.proto` carries bounded combat observations and a `DEFEND` action.
  `npc-service` validates at most 32 threats, a finite observation, target generation,
and known escape coordinates. `escape_assessed` distinguishes a confirmed blocked
route from missing path data. The planner prefers `FLEE`, waits for an unresolved
path or current action, and issues `DEFEND` only for a close visible zombie after
escape has been assessed as blocked.
- The Lua fallback in `AKRResidents` applies the same flee-before-defend rule and
  emits a short defend intent through `Controller`'s Actor port. The native adapter
  above now consumes the same model; the earlier detached port remains available.
- The native Actor and pool defaults are 32 slots. Actual construction is explicit
  and incremental through `prewarmOne`; assignment never creates an engine body.
  The headless fixture prewarmed and cleaned 32 engine objects with no network entries.
  No ambient module was auto-activated by this change.
- The pool now holds a dead Actor in `DYING`/`CORPSE` until stock death creates a
  verified corpse and the terminal resident record is acknowledged. The corpse
  keeps its real inventory and identity. A successful release detaches only the
  obsolete Actor references and returns that *same* engine object to the pool.
  Reanimation remains the stock corpse-to-zombie operation. A fast reanimation
  before the terminal receipt is safe only after verifying the corpse's original
  container is now the native zombie's container; this race is handled and tested.

## Engine and qualification gate

The direct-hit discussion below records the initial probe; the timed adapter and
ordinary-client one-strike acceptance above supersede its missing-executor status.
They do not remove the remaining live owner/contact and two-client gates.

The pinned `SwipeStatePlayer` attack-collision animation callback only calls
`CombatManager` for a local player. A connectionless server Actor does not pass
that guard. A gated zero-client probe set the Actor's animation-facing vector,
entered the stock swipe state, and called `CombatManager.attackCollisionCheck`
directly. The zombie's native health fell from 1.896 to 1.788 and the Actor then
retired cleanly. The combat manager's client hit-packet path is still guarded;
the pooled Actor has no stock server `IsoPlayer.update` animation pass. This
headless direct-hit proof does not show a correctly timed swing or replicated
injury. Do not ship a scalar-health shortcut or a global local-player spoof.
Implement and test a scoped server Actor attack executor, including observer
packets, before enabling defense in a live scenario. Generalize the existing one-hunter bite bridge with
one-hit-per-attack-entry and owner/target/range checks before allowing contact at
crowd scale.

The detached planner and Lua tests, Java pool tests, and zero-client native death
fixture pass. The headless fixture exercised infected death, stock corpse inventory,
native reanimation before and after Actor release, reuse of the same Actor,
incremental 32-body prewarm, and one direct native melee hit.
It does **not** establish timed combat animation, client injury display, two-client
replication, or 32 active fighting Actors. Perform 1/4/32 native contact batches,
then ordinary-client and two-client checks before the slice is accepted. Real
player safety, target freshness, failure quarantine, and bounded work remain
release gates.

Evidence: `artifacts/civilian-combat-headless-final-2.log`,
`artifacts/civilian-headless/20260928-144023-c575ca/ipc/native-report.json`,
`artifacts/civilian-combat-probe-7.log`,
`artifacts/civilian-headless/20260928-150158-4995a5/ipc/native-report.json`,
`artifacts/scenario-agent-combat-final-build.log`, and
`artifacts/npc-service-combat-test-2.log` (core and protobuf wire tests passed).
Project checks: `artifacts/dayone-combat-tests-final.log` (249 project and 116
Observer tests passed). The final short native run measured cold construction
p95 147.1 ms, harness work p99 150.1 ms, and warm materialization p95 4.6 ms
while warming 32 bodies. Prewarm before admitting gameplay; these figures are
not a release-cap soak result. An intervening
repeat exposed a fixture error: corpse location was assumed to be its starting
tile. The fixed test follows the exact corpse returned by native handoff and
checks it is attached to the world with the original inventory item.

## Accepted next slice: native death → aftermath → Actor reuse (2026-09-28)

Keep vehicles, migration and crowds separate. A dedicated `LIFECYCLE` runtime session
warms **one** Actor and owns one ordinary client-owned hunter. Its controlled disposable
fixture now starts the civilian at full health (revised after the 2026-09-29 watched
feedback), without added doors. Its hunter uses Superhuman strength, Pinpoint hearing and
Eagle sight in this disposable world. The next explicitly announced stationary-defense
fixture holds escape execution through the existing controller fixture boundary, while
native injuries, reactions and defensive decisions remain active. No fake geometry or
forced damage is used. This isolates terminal lifecycle behavior; it does not qualify
autonomous survival or resolve the separately observed flee-replication gap.
The civilian has infection eligible for native
reanimation but infection mortality disabled. The disposable world uses a 48–72-game-hour
initial reanimation delay to preserve the viewing hold; only the selected corpse timer
is then shortened. An actual validated native damaging attack
must precede death. No synthetic damage or forced kill is substituted in the watched case.
After 120 seconds without a qualifying encounter, report **NOT_EXERCISED**, clean up and
stop. This differs from both a scenario pass and cleanup failure.

The controller fences death before and after incoming-injury processing, unregisters from
the planner and preserves DEAD/TERMINAL across detach. Lua rejects late plans, fallback
plans and completion callbacks for a deceased resident. Pool generation fences remain.
A death during retirement before native removal enters DYING; death after uncertain
removal remains owned and unresolved.

`NativeResidentLifecycle` retains the exact corpse independently of the Actor. The pinned
`IsoDeadBody.reanimate()` return hook records the **native** resulting zombie and leaves
its simulation, ownership and networking unchanged. Stock container transfer, item IDs,
nested contents and deceased identity are checked. The fixture also checks clothing count
and skin transfer and rejects any shared inventory item ID in the replacement resident.

Before acknowledging pool release, a plain-data terminal receipt must be written by a
single background writer (at most four outstanding records), fsynced, atomically renamed
and directory-fsynced. Persistence failures retain the Actor. The Lua population record
links corpse/reanimation provenance to the terminal resident. Existing receipt files on
restart block encounter admission for reconciliation; this is deliberately **not** a
transaction with world/ModData saves and never permission to reconstruct an uncertain body.
Full world-reload aftermath fidelity remains an explicit qualification gate.

The corpse has an eight-second viewing hold. Only that corpse's native timer is shortened;
its native update performs reanimation. An additional eight continuous seconds of fresh
client-confirmed reanimated visibility precedes relocation. Each later stage has a 30-second deadline. Before
reassignment, move the observer away, confirm server removal and a fresh old-player replica
absence, then reuse the same object for a new name/outfit and an eight-tile walk. There is
no replacement construction on assignment. A second observer is outside this fixture's
qualification and blocks removal/reuse rather than borrowing the first observer's report.

The event reserves an aftermath resource before activation, allowing a death discovered
during cancellation to remain accounted for. Only fixture-owned corpses/zombies are removed.
A connected observer needs fresh absence; a confirmed zero-connection, zero-player server
can instead finish using native world/network removal checks. Unknown connections retain
ownership. Unexpected hunter death or uncertain native removal remains a blocked cleanup,
not a cleanup success. Normal gameplay aftermath is never removed by this test helper.

Headless fixture death is still artificial and is labelled separately from client-owned
incoming combat. Detached tests cover journal conflicts/failure/capacity/restart quarantine,
terminal Lua state, late plans, death during cancellation and disconnect clearance policy.
Native tests cover both early reanimation and reanimation after Actor release, real inventory
transfer and exact-object reuse. Watched fatal contact, presentation and two-client comparison
remain separate gates. See the latest [[../Experiments/Current State]] for actual results.

## Watched stationary lifecycle acceptance — 2026-09-29

Batch `20260928-223514-adb64ed6` passed native death, corpse/inventory/reanimation,
durable receipt, same-Actor reuse, replacement walk and exact cleanup. User confirmed
replacement appeared and walked normally. One constructed body, two assignments, one
parked; one ordinary client only. See Current State for evidence and independent remaining
gates (repetition, unrestricted pursuit, saved-world reload, crowd performance, two clients).
