---
type: design
status: implemented-first-native-qualification-in-progress
updated: 2026-09-28
---

# Offscreen chase staging

User direction: prewarm Actors, initialize residents from the civilian registry, and stage
native zombie pursuit in already-loaded areas outside current player sight, so an ongoing
chase enters view naturally. No spectator teleport is an acceptable substitute for this
ambient-event behavior. Observer repositioning remains a test-only convenience.

## Existing implementation versus remaining work

`NativeCivilianActors.prewarmOne` already initializes bounded reusable server bodies before
assignment; `profile` plus native snapshot restoration binds admitted residents. The whole
civilian registry must remain cheap logical records, not one live engine object per resident.
Prewarm after native world initialization, incrementally, preferably before admitting players.
Server prewarming does not prove client replica/appearance readiness. The current watched
harness uses generated test identities, not a complete ambient civilian-table integration.

Native zombie ownership can be assigned through `NetworkZombieManager.moveZombie`, which
updates ownership lists and native notifications. Never write just the owner field. Stock
`updateAuth` reconsiders ownership (normally after two seconds); any explicit event lease
needs a narrowly scoped, bounded integration and expiry/handoff, not repeated global forcing.
`NetworkZombieSimulator` cannot construct a replica where the client's grid square is absent.
A server-loaded square is not evidence that the client has loaded it.

## Proposed lifecycle

1. Select a bounded candidate corridor, outside every relevant observer's current visibility
   but inside at least one eligible client's loaded simulation area. Require walkable escape
   route and real line of sight at the intended reveal point. Historical explored-map fog
   is not current visibility; viewport overlap is not line of sight either.
2. Reserve a prewarmed Actor and initialize its civilian identity/snapshot before announcing
   the body. Stage the zombie through native construction/ownership. Require fresh matching
   client acknowledgements for loaded squares, both replicas, and local zombie simulation.
3. Prime pursuit out of sight. Native ZombieControl assigns a target; it does not itself
   perform spotting or start locomotion. Confirm pursuit state and measured movement before
   releasing the pair toward the reveal corridor. Choose spacing/speed that preserves a
   visible chase; do not let the civilian outrun perception during initialization.
4. Once bodies exist, a player turning or approaching must never cause visible teleport,
   removal or identity replacement. Let the encounter continue naturally, even if its reveal
   happens early. Before materialization, stale/visible/unknown candidates are deferred.
5. After the encounter, require confirmed off-screen native removal and client absence before
   reuse. Busy/dead/uncertain bodies remain owned. Persistent residents retain their state;
   no combat/death qualification is inferred from successful pursuit staging.

Client observations inform bounded presentation admission only. The server keeps event,
identity, budgets and outcomes authoritative. Do not weaken normal 64-tile conservative pool
admission merely to make staging work: add an explicit checked staging contract, scoped to
managed resources. Every relevant observer matters; the nearest eligible client owns zombie
simulation, other clients receive stock replication. No new protobuf transform stream or
client JVM agent is required by this design.

## Next gate

Prove one pair first: server/client preload readiness, offscreen pursuit, clear roadside
reveal without teleporting the observer, and verified cleanup. Test a player turning around,
leaving relevance and a second observer before claiming robustness. Then repeat four pairs,
and resume the requested 64-civilian/16-pursuer four-wave stress batch. Keep budgets explicit;
full town population and native path workload remain separate from Actor reuse capacity.

## Failed-test evidence motivating this design

`artifacts/scenario-tests/20260928-103315-ffb1ba/`: middle observer point left the initial
zombies outside client relevance; late ownership did not establish pursuit.
`artifacts/scenario-tests/20260928-103932-bb3eea/`: all 16 acquired targets and moved, but the
slowest traveled only 3.436 tiles (five required), so the batch failed before retirement.
The temporary closer viewer coordinate (10616.5,10005.5) was inside the walled school
playground. User feedback and formation-wave1.png confirm the obstructed view. `isOnScreen`
reported projection, not actual visibility: do not label this run visually passed.
That point is rejected for future tests. A replacement needs native LOS, open egress and
visual verification; floor/isFree checks alone are insufficient. No revised event staging
or replacement-viewpoint validation has run yet.

Private pinned-build inspection: `artifacts/decompiled/watched-chase/`,
`artifacts/decompiled/offscreen-chase/`, `artifacts/decompiled/offscreen-los/`.
Keep proprietary reconstructed source out of distribution. These are source findings,
not multiplayer qualification.

## Implementation — 2026-09-28

`./dayone civilian-chase create/start/join/status/stop` operates the opt-in one-pair
experiment. It prewarms four reusable Actors before client launch, creates one logical
civilian using `AKRResidents.add` and binds that record's generation/presentation to a
warm Actor. No ambient controller is activated. A found dependency-manifest separator
bug in AKRResidents was fixed (`require` uses commas), and now has a regression check.

`OffscreenAdmission` requires fresh matching proposal revisions from every online player,
rejects observer displacement >1 tile and reports older than 750 ms, and chooses the
nearest eligible client that reports the staging footprints loaded. The native adapter's
conservative default stays intact; only this event supplies a scoped admission/retirement
policy. Candidate footprints include both births and an eight-tile priming segment.

Ordinary Lua observers check a 160/200-pixel expanded viewport and current square visibility,
not explored-map data. At most six corridors × four five-point footprints are sampled per
250 ms. Replica observations distinguish missing from overflow/unknown and confirm human
clothing plus the native zombie owner. Server telemetry checks epochs, proposal revisions,
counts, numeric bounds, booleans and rate limits. Reports inform presentation admission;
they do not authorize health, damage or arbitrary game positions.

The experiment lets native ownership operate normally inside its relevance bounds and can
request assignment with the engine manager only when a matching client reports the square
loaded and is natively eligible. It does not globally override ownership or extend streaming.
A handoff waits for the new owner's body acknowledgements. Pursuit requires actual target
acquisition and movement; the controller then adjusts civilian speed to retain separation.
Early observation does not despawn or swap bodies. Retirement requires five seconds of
fresh hidden reports from all observers, a twelve-tile safety distance, native removal and
two seconds of acknowledged client absence. Native resident state is saved before reuse.

The observer is positioned once on the original open-road viewpoint, before admission.
Native LOS and walkable egress to the road are mandatory in preflight. No teleport is used
for priming, reveal or cleanup. A moved observer or early reveal prevents a qualification
pass even when resources complete normal cleanup. Timeout/contact/disconnect retains owned
resources for diagnosis. Ordinary clients; no new engine patches or custom transform stream.

Initial unit/component and engine integration checks passed. The first native start exposed
the dependency metadata issue before any assignment; the corrected native run is
`artifacts/scenario-tests/20260928-110457-25ff6f/`. Visual, handoff and two-client results
are pending. The current route is a preflighted straight-road qualification corridor, not
arbitrary ambient placement or full flee/path controller activation. Timing includes
instrumentation; cold prewarm is separate from active encounter cost.

## Authorized full-scale batch and admission deferral

The user superseded the 64/16 target with four 64/128 waves, original southbound direction.
A one-pair reverse cleanup repeat confused the user; do not substitute further small cases.
`WatchedCivilianHarness` now stages the entire cohort behind the road viewpoint, checks
all observers, and uses `OffscreenZombieLease` only for admitted scene zombies in loaded
client squares. The max128 lease map uses native manager bookkeeping, a <=600ms lifetime,
fresh client reports, and disconnect/movement/distance expiry. Stock ownership remains
unchanged for all other zombies. This is a pinned server JVM hook, not update-proof.

At least 116/128 hunters must acquire a civilian and move >=5 tiles. Native spotting uses
its probability model (`spotted(actor,false)`); there is no forced-awareness call. Nonfollowers
remain owned and must complete normal offscreen removal. This does not qualify contact/death.

The first full-cohort staging run `20260928-113326-67e8d9` failed after 11 assignments when
visibility admission became unavailable. No chase occurred and no hunters were spawned.
The adapter now signals explicit `AdmissionDeferred` before ownership; the pool keeps the
reservation and retries without constructing or consuming another body. A deferral after
ownership is still unresolved. Fault-injection tests cover both paths. This preserves the
unknown-visibility veto while allowing normal loading/observation gaps during crowd setup.
