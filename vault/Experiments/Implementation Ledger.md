---
type: experiment-ledger
status: first-week-implementation-in-progress
updated: 2026-09-28
---

# Implementation ledger

## Lifecycle watched attempt interrupted before contact (2026-09-29)

World `AKR_DayOne_Test_20260928_215420_1d4250`, epoch
`4885aa10-16b0-4f45-87ff-217d05b2965f`. Batch `20260928-215914-aedfd06e`
started after explicit readiness. The user reported the hunter attacking a door and
requested a restart. No damaging contacts or death occurred. Cancelled and verified
cleanup: zero resources, one constructed Actor, one parked. Verbatim partial feedback
is archived independently; lifecycle acceptance remains pending.

The requested same-session retry `20260928-220032-b388c539` stayed WAIT_OBSERVER with
zero participants because the local client had closed. Cancelled that empty submission;
cleanup verified. Relaunching the pinned client and awaiting fresh user readiness before
another `run lifecycle`. Zombie sight/AI and fixture parameters remain unchanged.


## Fatal lifecycle implementation prepared; watched acceptance pending (2026-09-28)

Implemented the next death/aftermath/reuse slice in the private runtime. `LIFECYCLE=5`
is additive; a separate one-Actor session runs native incoming injury → death → durable
terminal receipt → original corpse inventory → native reanimation → same engine object
as a different resident → eight-tile walk. Added scoped reanimation-return observation,
terminal planner fences, bounded asynchronous fsync journal, population provenance,
independent scenario/cleanup outcomes and cleanup without a named observer when every
connection is confirmed absent. Unknown viewers/removal still retain exact resources.
The initial dying fixture has ~10 HP, ordinary clothing, nested inventory and no weapon.
Only this new disposable world uses transmission 1, mortality 7 and reanimation 5;
after the eight-second corpse hold only its native timer is shortened. No forced kill,
attack-acquisition override or client Java injection. No fatal encounter within 120 seconds
is NOT_EXERCISED, followed by cleanup without automatic retry.

Manual review caught and fixed a terminal-state bug: ordinary `Plan.detach` reset DEAD
residents to IDLE. Terminal state now survives detach; Lua/Java reject late planning.
Death during an incoming-injury tick stops later actions in the same update. Death before
native retirement starts enters DYING; death after uncertain removal remains unresolved.
Journal files from an earlier process block admission pending world-save reconciliation.
This is not proof of full world-save/ModData atomicity or restored aftermath fidelity.

Validation:
- Final Java unit/component, transformed-bytecode/build guards, native asset import and
  premain integration pass; **273 Python tests passed**; watched skill validates.
- Headless `20260928-214538-fa5766`, epoch `97ad51b9-4767-4b73-bd6f-e6f5afb53868`,
  passed early native reanimation, durable receipt, nested inventory/exact Actor reuse,
  doors and **13 strengthened survival encounters / 13 contacts**. Controller p95/p99
  **5.8/22.37 ms**: correctness passed; release performance targets remain unmet.
- The previous timeout reproduced in `20260928-214056-ff2336` at round 2, fourth Actor,
  X10762.5/Y9847.5, repeatedly replanning without native movement. Source showed escape
  search allowing unqualified climbs while execution supported only WALK/DOOR. Filtered
  escape edges by the Actor's executable capabilities; added a detached regression.
  Native rerun passed without weakening collision or raising deadlines. Original failed
  evidence remains preserved; the old trace did not record its exact rejected edge.
- Headless `20260928-214921-b2014f`, epoch `62ae52d9-9400-43ed-92cb-3f5c3e2faeff`,
  passed reanimation after Actor release, terminal receipt and nested inventory preservation,
  same-object reuse, door/snapshot/pool tests. Total p95/p99 **4.2/86.54 ms** includes cold
  prewarm (32-body capacity check); not a steady-state capacity claim.
- Final deployed agent `acf9f88709491236ed3b0551d24678ede201efa6c2656628caa7b50b56af99fa`.
  Later final changes are fixture validation/metadata, replacement resident registration,
  ordinary-client diagnostics and the offline/dispose death guard; final component/integration
  checks cover those changes. No human acceptance of this new lifecycle case yet.

Fresh watched world **AKR_DayOne_Test_20260928_215420_1d4250**, epoch
`4885aa10-16b0-4f45-87ff-217d05b2965f`, is **READY**, .132:16281.
All 55 installed ordinary-client mod file hashes match its receipt. Startup error signatures
match the previous watched server apart from the launcher stderr preload notice; runtime
reached READY with no submitted event. See its `lifecycle-preparation.json`. No event submitted,
no client launched for this iteration. Wait for readiness and use
`./dayone civilian-combat join`, then `./dayone civilian-combat run lifecycle` after
confirmed in-world readiness. Retain automatic placement/protection/daylight/pistol/chat.
The earlier uncertain watched world is preserved; production .160 and normal .132 untouched.

Remaining gates: actual client-owned fatal contact and death/reanimation presentation,
watched old-replica absence and reuse/cleanup, full world-reload aftermath fidelity,
two-client comparison and performance. Unexpected hunter death remains retained rather
than claiming its unproven corpse cleanup succeeds. The normal admission limit remains four.
See [[../Design/Civilian Defense and Death]] and [[../Runbooks/Operations]].


## Door/corner watched behavior accepted by user, 2026-09-28

Batch `20260928-211357-bfef6568`, epoch `7ae7eb1c-dd11-4004-aaa5-acf49dfbf79c`.
User verdict, verbatim: **“One of the zombie never attacked due to it's normal behavior.
Test accepted and passed”**. Separate passed human receipt is archived. Accept the
observed one-client door/corner/startup behavior; do not require every native zombie to
attack merely to force a deterministic demonstration. Three civilians defended/escaped;
one hunter retained a native target but did not attack. Reported peak replica gaps were
1.048/0.777/0.778/0.783 tiles, versus the previous rejected run's 6.34-tile peak.
No sustained divergence gate fired. Client geometry confirmation allowed exits normally.

The client disconnected before the fourth encounter completed. Machine reason became
`observer_disconnected`; cleanup remained blocked with four Actors, four hunters and
fixture geometry tracked, zero parked. Preserve the original machine failure/ownership:
human acceptance does not verify cleanup or four completed combat encounters. Operator
cancelled the pending case and stopped the disposable server gracefully, preserving its
world/evidence; do not reuse this uncertain pool as though retirement passed. No further
case is running. Two-client, strengthened headless survival timeout and sustained load
remain separate gates. Later watched tests should distinguish native non-acquisition
from presentation failure while still requiring exact cleanup accounting.

## Four-civilian visual rejection: doors/corners, 2026-09-28

The user rejected batch `20260928-205933-7da56482`: running civilians bumped into a
door and appeared to teleport through walls despite apparently valid server paths.
Verbatim failure feedback is archived separately. Its machine pass remains immutable;
**the encounter is not visually accepted**. Straight WALK/RUN comparison acceptance is
limited to its unobstructed track.

Position analysis found sampled client/server separation up to **6.34 tiles**. Native
client path-failure recovery can teleport remote players; the exact branch was not
captured. Fixture exit doors were removed without client confirmation. No single cause
is claimed proven. Candidate changes and limits are in [[../Design/Resident Plans and Locomotion]].

Added startup blend/ramp, WALK approaches to nearby turns/doors, prompt heading/gait
packets, downward prediction quantization, bounded client exit-geometry confirmation,
and a sustained replica-gap watched failure gate (3 tiles/750 ms, fresh reports only).
Java checks and 271 Python tests pass, including blocked/unloaded/cleared exit reports,
transition/reset behavior and divergence-gate tests. Native movement/reuse validation passed; evidence follows. New code/Lua have **not** been installed in the running watched session;
no further visual test has been started. Keep the separate headless survival timeout
and two-client gates open.

Native validation completed: world `20260928-210702-d85943`, epoch
`669a7813-2095-455f-803c-d868cec8fa56`, passed eight worker routine/run/reaction
assignments plus the two-Actor 50-tile comparison. WALK **23.0975 s**, RUN **12.0987 s**;
four constructed, ten assignments, four parked, zero occupied. Work p95/p99 **1.7/3.3 ms**.
Snapshot agent `2d00c35097c61de351399c506b9b8544bdcbcd083c385f111ada48ed7e56aa07`.
Final build (including watched drift gate and exit-hold refinement)
`bb6bf189dc7ce57d36ad6e3b2dde6391fe0bf358e5a4e1dadb4a34da3c615d6f` passes Java
integration/guards; Python 271 passed. Native movement changes were in the tested
snapshot; later changes affect the watched fixture only. Headless world stopped
automatically. Live watched session remains on its old build, idle. Deployment requires
server restart and a fresh client load for the updated diagnostic Lua. No visual check
was started. Client door API/replication timing and corner smoothness remain native-client
gates; detached report checks do not prove them.

## Four-civilian rerun after explicit readiness, 2026-09-28

Batch `20260928-205933-7da56482`, same epoch `819447fe-fb20-47cb-910f-dfe75549a435`,
started after the user said “I am in”. Four civilians/four shamblers completed:
one defensive contact each, incoming injuries 10/5/2/3, escape travel
16.46/13.98/17.53/18.35 tiles. Cleanup verified; four constructed, ten cumulative
assignments, four parked, zero event resources. Client ERROR/Exception count zero.
Machine pass; human feedback requested and pending. The prior successful watched
run was missed by the user and does not count as human acceptance. No automatic
next case. Separate strengthened headless survival timeout remains unresolved.

## Four-civilian watched defense/escape machine pass, 2026-09-28

Batch `20260928-205424-5e4b9d53` reused the comparison server and its four bodies
(epoch `819447fe-fb20-47cb-910f-dfe75549a435`). The client was closed when requested;
automatic reconnect completed before scene preparation. Four civilians and four native
shamblers: each civilian landed one defensive contact and escaped, travelling
23.88/17.06/19.14/17.52 tiles with 2/7/8/8 incoming injuries respectively.
The previously reproduced null MoodlesUI combat exception did not recur.

Cleanup verified: four constructed, six cumulative assignments, four parked, no event
resources. Case-scoped client ERROR/Exception count zero. Runtime work p95/p99/max
1.5/3.9/40.73 ms; controller p95/p99 1.0/2.7 ms. Short functional case only, not sustained
capacity qualification. Human visual/audio feedback requested and pending.
The stronger headless pain/interruption survival fixture remains separately failed;
this watched success does not erase its unexplained escape timeout. Two-client and
crowd gates remain pending. Server/client remain available, no automatic next case.

## Parallel WALK/RUN accepted in one client, 2026-09-28

Batch `20260928-205224-4a741075`, world `20260928_205047_e4dcd9`, epoch
`819447fe-fb20-47cb-910f-dfe75549a435`: two civilians, zero zombies, parallel 50-tile
tracks. WALK **23.00 s**, RUN **11.90 s**, approximately **1.93× faster**.
User verdict, verbatim: **“Runner clearly faster; both smooth”**. Separate human
receipt saved beside the immutable machine report. The test completed with verified
cleanup, four bodies parked, two assignments and zero remaining event resources.
Case-scoped client log: zero ERROR/Exception lines. Native animation warnings about
`turning180` appeared in server logs; no observed motion defect was reported.

This accepts the speed/presentation correction in one ordinary client. It does not
qualify two-client replication, crowds, terrain-specific animation profiles or the
outstanding strengthened four-civilian survival timeout. Server/client remain available;
no additional case is submitted automatically.

## Gait speed and combat diagnostics, 2026-09-28

The user confirms running animation now appears, but rejects equal-looking WALK/RUN
velocity and jumpy client motion. Exact feedback is preserved in the original watched
world's `human-feedback-verbatim-speed.json`; prior “human pending” entries are historical.

The server speed formula incorrectly treated WalkSpeed (a blend coordinate) as a linear
physical multiplier. The replacement caches the installed animation root-motion profiles
and uses the pinned native blend picker. Default healthy WALK/RUN commands are about
2.18/4.22 tiles/s. Native collision stays enabled. Stock prediction now follows verified
collinear buffered edges up to 0.6 seconds, bounded by turns, doors, obstacles and deferred
stop boundaries. No secondary protocol or client JVM hook was introduced. Mean clip
speed does not qualify every terrain/weapon/transition animation; smoothness remains a
human gate. The new `run compare` definition uses two matched civilians and two parallel
50-tile tracks, zero zombies, independent times and verified cleanup.

Diagnostic batch `20260928-203325-b2c5c39a`, world `20260928_203127_5ef695`, reproduced
the four-Actor exception with nested evidence: `CombatManager.attackCollisionCheck`
calls `MoodlesUI.wiggle()` on a dedicated server where that UI is null. The scoped hook
skips only this presentation call during managed native combat; damage calculations
remain native. The diagnostic failed as expected and later verified four parked bodies,
zero event resources. Server was then stopped; no client remains running.

Java unit/integration/hash guards pass (agent
`5130a56ffaad03f02442d9e6a3831f3a212fb30d7d94e67595618015fa5abbf1`), as do 271 Python tests.
Native survival `20260928-202716-2572f9` passed 25 checks and 13 contacts with zero owned
Actors. Survival p95/p99 6.9/39.21 ms still exceed budgets. A stronger pain/UI regression
in `20260928-203658-614669` exercised guarded painful attacks successfully, but failed
later: one of four civilians stopped after 4.05 tiles and exhausted its escape timeout
(57 path requests). Preserve this failed world; the full strengthened survival gate is
**not passed**, and its blockage is not yet diagnosed. No crowd admission change.

Native planner/comparison world `20260928-204341-c60c3b`, epoch
`9554d282-dc71-4532-8baf-2545c51c7bfa`, **passed**: eight worker-driven routine/reaction
assignments followed by two simultaneous 50-tile tracks. WALK **23.0003 s**, RUN
**11.8987 s** (1.933× faster; achieved 2.174/4.202 tiles/s). Four engine bodies,
ten assignments, four parked, zero occupied; added work p95/p99 **1.7/3.2 ms**.
Snapshot agent `61d1429c6475955f2398b9edfcc2e557d54d2a9f68c5f494726f527b99455e37`.
This confirms authoritative speed separation, continuous native execution and cleanup,
not client visual smoothness. The first comparison attempt `20260928-203111-62fba1`
correctly rejected an obstructed lane; the final fixture selects clear lanes with bounded
native preflight instead of disabling collision. Report includes exact elapsed times.
Disposable services are stopped. Latest watched comparison code is built but not deployed
or human-accepted; verify both full tracks and camera coverage on preparation.

## Watched movement progression and first multi-Actor failure, 2026-09-28

Ran batch `20260928-200957-b3846c22`, epoch
`6cfdd74e-41c8-4acd-a038-3a31122cbb7e`, after explicit observer readiness.
LOCOMOTION, OPEN_ESCAPE, INCOMING_INJURY and single DEFENSE_ESCAPE passed machine
checks and cleanup. Six fresh sampled RUN frames all carried client WalkSpeed about
0.65 (previous failed iteration had zero); five reported client_running. This is packet
evidence, not human animation acceptance. Injury and defense cases recorded real
damaging hits and subsequent movement. Captured client ERROR/Exception count: zero.

First four-person DEFENSE_ESCAPE failed in resident-1's controller after injury recovery
and re-entering SwipeStatePlayer. Only the wrapping resident_execution exception was
retained, so the nested cause remains undiagnosed. The operator stopped and did not run
the last two waves. Cleanup completed with no event resources, four bodies parked and
eight total assignments over four constructed bodies. Case five work p95/p99 1.4/2.6 ms;
controller p95/p99 0.6/1.2 ms. These short measurements do not qualify sustained load.

Reports, per-second samples and bounded client/server logs remain in the world's
encounter-batches directory. Human verdict requested separately. Next improve nested
failure evidence and reproduce the first multi-Actor case; do not erase the failed
report or treat cleanup success as scenario success. See [[Current State]].

## Continuous execution window and planner feedback, 2026-09-28

Implemented the accepted soft-cancellation/window plan in CivilianTraversal,
NativeResidentController and AKRResidents. The eight-edge window includes the active
edge, refills at four, stages one candidate and joins at a reached boundary without
resetting the native port clock. Unsafe execution still interrupts immediately. Added
terminal journal reservations/acknowledgments and active progress to the existing C++
transport. Goals/effects remain server-owned. Actual IPC tests drop an acknowledgment,
reconnect, verify the identical retry, then verify slot release only after acknowledgment.

Pinned movement investigation found that idle native Actor action state suppresses
WalkSpeed/WalkInjury in PlayerVariables.set, while the client run animation needs
WalkSpeed. Added guarded native-field population rather than changing action state or
the wire format. Headless serialization/application checks pass for WALK/RUN/injury and
idle. No client JVM change, game-file patch, visual retry or production deployment.

270 Python tests, Java unit/guard/integration checks and C++ core plus 16 process/wire
scenarios pass. C++ benchmark: 32 residents × 30 batches, round-trip p50/p95/max
13.77/15.64/16.49 ms. Initial native worker fixture
`20260928-195047-d48558` passed eight assignments over four bodies, uninterrupted RUN
continuations, reaction recovery and receipt draining; zero occupied/four parked,
work p95/p99 1.0/2.2 ms. Final repeat `20260928-195821-2301fc` passed all eight
assignments with native next-boundary cancellation as well: work p95/p99 1.0/3.0 ms,
zero occupied/four parked, terminal journal drained. Its immutable agent SHA256 is
`4e70b4298703812b5523ad54f9647a7e14986a9503faee8a931447988dbc55df`.
Final Java integration checks passed; all disposable containers stopped automatically.

Survival regression `20260928-195552-9f647c` passed 25 checks and all 13 contacts across
one then four Actors, with native lifecycle, doors and cleanup intact. Survival work
p95/p99 4.1/18.2869 ms; whole fixture 4.0/192.137079 ms including cold setup (cold p95
187.502343 ms). These exceed release budgets. Do not scale admission or infer visual,
client-owned chase or multiplayer qualification. Reports and immutable snapshots remain
under artifacts/civilian-headless. See [[Current State]] and
[[../Design/Resident Plans and Locomotion]].

## Autonomous civilian defense/escape adapter, 2026-09-28

Connected the existing Lua model to bounded native perception, geometry/A*, traversal
and timed melee/shove execution. Added pre-damage target filtering, monotonic action
receipts, repeated-action audio and the bounded native incoming-bite adapter. Added
detached cooldown/miss/interruption/uncertain-effect and ownership-change tests.
The same headless session completed one then four civilian encounters over four waves:
13 native hammer contacts and escapes, no new engine bodies after prewarm, verified
retirement. The first run exposed graph capture exceeding the model timeout at 10 Hz;
bounded capture was corrected. A subsequent reduction lowered aggregate batch work
p95 from 5.1 to 3.4 ms, still above the release target. See the defense design and
Current State for exact evidence and unqualified live-contact/lifecycle gates.

Follow-up mixed hammer/shove run also passed 13 encounters, p95 4.6 ms; keep the
performance gate open. Fresh watched world `AKR_DayOne_Test_20260928_163023_07ce36`
was prepared with the ordinary local Lua modules and the same observer loadout.
Java guards/integration and 256 project tests passed. No live-chase, incoming-bite,
combat-to-death or two-client acceptance is inferred from these runs.

## Civilian defense policy and native death gate, 2026-09-28

Implemented a bounded C++ combat observation/`DEFEND` result and Lua fallback that
wait for a confirmed blocked escape. Raised the native pool class default to 32
without raising normal live admission, and added explicit native corpse ownership,
terminal receipt, same-engine-object reuse, and reanimation checks. The disposable
zero-client world passed these death and 32-body prewarm cases. A separate opt-in
headless probe passed fast native reanimation before Actor release, exact cargo
container preservation, direct stock melee damage, and Actor retirement.
Detached Java, C++ core/wire, project and Observer checks pass. Evidence and open engine attack gate:
[[../Design/Civilian Defense and Death]]. This is not a live combat or multiplayer
qualification. The unrelated 64/128 pursuit/contact failure remains open.

## Latest checkpoint — 32-Actor 8×4 stress batch passed, 2026-09-28

Four waves of 32 civilians completed the same 150-tile routes in one ordinary-client
session. **32 engine objects, 128 resident assignments, 128 saved outgoing bodies,
19,200 actor-tiles**, no hot constructors and zero retained native bodies after cleanup.
Client presence/appearance and absence-before-reuse passed for all 32 in every wave.
Captured screenshots show the formation. Native server chat announced each wave and
completion. The disposable server was left running; normal .132 and production .160
were untouched. This is an opt-in stress harness; default Actor capacity remains four.

Full evidence and analysis: `artifacts/scenario-tests/20260928-100710-9f7bda/report.md`.
Agent SHA-256 `81bc90bc357d1310be5a6ff201c8227db4728f5aa49230969d3cbc1e20cfd483`.
Per-wave harness work p95 **0.4–0.7 ms**, p99 **0.7–1.1 ms** (now includes report I/O).
Warm assignment max **2.026 ms**, p95 histogram upper bound 1.0 ms. Cold initialization
max **161.964 ms**, before clients joined. Assignment/retirement/parking are staggered.

Client render callbacks averaged **143.92 FPS marching / 143.23 FPS during admission**;
interval p95/p99 ≤10/≤11 ms, marching maximum 27 ms. These include diagnostics and the
off-screen approach/departure, not isolated GPU cost. Container delta CPU during movement
averaged 6.26% of one core (peak sample 16.33%); sampled memory peaked at **4.201 GB**
cgroup / **2.521 GB** Java heap. Cgroup includes game/cache, not just Actors. Transmit
traffic averaged 13.96 kB/s; 42,688 native Actor update packets over the run.

Zero client exceptions/new errors, zero per-frame >1-tile corrections, all 314 fear
samples zero. **Position accuracy remains a failed gate:** 29,416 same-host aligned pairs
measured p95 4.279 tiles, p99 4.467, max 5.579, without lag compensation. Smoothness and
pool viability are separate from correct contact/replication. This does not qualify 32
AI/perception/native-path jobs or two-client agreement. No normal population increase.
The user's audio correction stands: footsteps were audible; the earlier missing-audio
finding is superseded. No custom sound workaround was added.

Implementation adds bounded opt-in 32-slot native/pool capacity, tested 8×4 geometry,
server-configured 4/32 telemetry with count/order/epoch validation, fixed-size render
interval histograms and per-phase/container measurements. Unit/component checks passed
in `artifacts/runtime-tests/20260928-100615-0f6a7c34/` (241 Python tests plus Java fixtures),
engine integration in `artifacts/runtime-tests/20260928-100709-e1890f58/`. Added tests for
32-slot overflow, one assignment per tick, all 32 retirement receipts, formation distances
and malformed/stale client telemetry. Next: prediction timing, then representative native
behavior/path workloads and paired-client validation; do not infer full crowd capacity.

## Follow-up — audio confirmed; 32-Actor stress test authorized

The user corrected the earlier audio report: footsteps **were audible** in the successful
four-wave test. Treat missing footsteps below as superseded user feedback, not a confirmed
open defect. Position accuracy and two-client qualification remain open. The user now
authorizes 32 simultaneous civilians in an 8-column × 4-row formation, repeated over
four waves to measure costs and viability. This is an opt-in disposable stress experiment;
normal pool/controller budgets remain four and ambient population stays disabled.

## Latest checkpoint — four watched reuse waves passed, 2026-09-28

The user confirmed **"Test successful"** after four simultaneous NPCs ran the same
150-tile paths four times in one ordinary-client session. Exactly **four prewarmed
engine objects** served **16 different resident assignments**, with identity/profile
changes between waves, no assignment-time constructors, 16 outgoing body snapshots,
confirmed client absence before reuse, and zero active/parked/retained bodies after
cleanup. All four object identities are stable across the four waves. The user previously
confirmed changing identities and reuse but reported **missing local footstep audio**;
that remains open and was not fixed by the test changes.

Evidence: `artifacts/scenario-tests/20260928-094756-a2fa59/`, especially
`ipc/watched-report.json`, `ipc/watched-samples.jsonl`, `analysis.json`, source/client
hash receipts, copied agent manifest, redacted logs and wave screenshots. Agent SHA-256:
`a41ee04b02fb6e91e9618c8bd5134b6dfc182ecdf8661aacc5a58e4015567289`.
The final report's wave counter is 5 because it advances after completion; its completed
wave records are correctly 1–4. The disposable server was left running; no .160 or normal
.132 deployment was changed. Local AKRCore/AKRDevTools were backed up and installed for
this explicitly authorized test. There is no client Java agent.

Each movement pass took about **57.6 seconds** (2.6 tiles/s) and each full wave about
66.0–66.2 seconds including retirement. Warm assignment maximum **1.655 ms**, p95
histogram upper bound 1.7 ms. Per-wave harness work p95/p99 upper bounds were
0.3/0.4, 0.2/0.3, 0.2/0.2 and 0.1/0.2 ms. Cold prewarm maximum **106.4 ms** remains
visible; these are harness timings, not whole-server or client frame-time measurements.

This is a lifecycle/reuse pass, **not replication/performance release qualification**.
Same-host wall-clock interpolation of 3,692 client/server pairs, with no time-shift
compensation, measured position error p95 **4.455 tiles**, p99 4.664, max 7.199.
That fails the ≤1-tile target. Four per-frame >1-tile corrections occurred together near
wave 1's departure/finish; their on-screen visibility was not recorded, so do not label
them confirmed visible skips. No client ERROR/Exception lines occurred after the first
Actor sample. All 292 fear samples had zero visible zombies, panic and stress. Ordinary
startup asset warnings and server `turning180` animation warnings remain. Two-client
agreement, audio, complete traversal and combat are still open.

Implemented `./dayone civilian-watch create/start/join/status/stop` and the opt-in guarded
`WatchedCivilianHarness`, plus bounded four-Actor client visibility telemetry. Each wave,
finish, off-screen wait, confirmed retirement and final pass/failure now uses the game's
native server-alert chat API (inspection: `artifacts/decompiled/watched-messages/`).
Between waves the observer returns to the viewing point and a 12-second admission stage
rechecks fresh telemetry and clearance. `join` waits for warmup before launching the client.
Materialization failures now retain their specific reason; the harness inspects unresolved
slots before returning for a missing body, so it cannot silently wait until timeout.

Previous evidence is preserved: the overnight run `20260927-220044-d3a555` failed on
observer disconnect in wave 1; retry `20260928-093617-aa2b0b` completed two waves but
stalled before assigning wave 3. It was stopped for diagnosis. The old harness hid the
pre-body failure reason, so its exact original refusal remains unproven; do not retrospectively
claim a confirmed reset bug or blame observer movement. Improved admission/diagnostics
were followed by the complete four-wave pass above.

Validation: Python components (240 tests) and Java unit/vehicle regression fixtures passed
in `artifacts/runtime-tests/20260928-094725-9aee591d/`; exact-engine integration checks
passed in `artifacts/runtime-tests/20260928-094755-f93f1359/`. Added a regression case
for a materialization refusal before body ownership, retaining capacity and the refusal
reason. Next work: native remote-player sound/prediction audit, then measured paired-client
replication. Idle/roam/flee controller integration and full traversal remain separate gates.

## Latest checkpoint — cross-resident initialized pooling, 2026-09-27

The user reiterated that general Actor reuse was the point. **The resident-affine cache
is superseded.** The native adapter now prewarms exactly four initialized `IsoPlayer`
objects, keeps them in a reusable FIFO, and applies an explicit reset/load boundary when
assigning any new or saved resident. `materialize` has no constructor fallback. Four
constructors occurred during warmup; **15 subsequent assignments reused those objects**.
No normal gameplay session or client installation was changed.

`NativeActorReset` clears stock-load omissions (hands/equipment, ModData, appended history
collections and fitness state) and transient locomotion/action/path/network state before
loading the incoming snapshot. New residents start from a pristine per-body snapshot and
receive their own descriptor/outfit. Busy, dead, burning, vehicle, grapple/climb/attack,
downed or ragdoll bodies cannot be reset into another person. Failures remain owned.
Exact old descriptor pointers/registry entries and delayed emitter callbacks are detached.
Source inspection is retained under `artifacts/decompiled/actor-reset/`; newly inspected
reset dependencies have explicit BuildGuard hashes. See [[../Design/Civilian Pool and Navigation]].

Native report: `artifacts/civilian-headless/20260927-214700-fb96f8/ipc/native-report.json`.
Agent SHA-256 `75adb7f4052101e01d278d84b8b2de8252ed22680bf1e772d1515ea97a822571`.
World `AKR_DayOne_Test_Headless_20260927_214700_fb96f8`, no clients. The batch dirtied four
male residents, reassigned the same four objects to fresh female residents, then restored
the originals on **different** physical objects. Assertions passed for no leaked inventory,
hands, wounds, recipes/books/literature/fitness, ModData or posture; incoming sex, original
saved state, stale-token rejection, movement, busy-reset refusal and descriptor cleanup also
passed. Existing native path/cancel/body/door tests passed. Final retained/parked/active
counts were zero; the isolated server stopped gracefully.

Warm assignment max **5.895462 ms** (15 assignments), p95 upper bound 5.9 ms. Warmup cold
p95 upper bound 138.65 ms. Total harness p95/p99 upper bounds 2.4/145.17 ms include warmup;
walking was 0.3/2.3 ms. This meets the no-hot-constructor requirement, not the full performance
target. Reset/outfit/restoration still cost time; the former same-resident-cache timings do
not measure this stronger operation. Earlier incremental runs `20260927-214351-f2e7a4`
and `20260927-214545-89cd6c` passed before the final descriptor-cleanup assertions.

240 Python tests and Java unit/vehicle regressions passed:
`artifacts/runtime-tests/20260927-214743-40e88be1/report.md`. Engine compatibility/hook/premain
checks passed: `artifacts/runtime-tests/20260927-214812-2834cf16/report.md`.
Remaining work: native idle/roam/flee controller integration, runtime warmup/admission wiring,
full traversal, richer combat/lifecycle reset coverage, and visual/two-client reuse. This is
headless qualification of the implemented ambient Actor boundary, not every arbitrary player
or third-party mod state. The reopened combat gate remains separate.

## Superseded resident-affine cache experiment — 2026-09-27

The user clarified that pooling must retain initialized engine objects, not merely reuse
online IDs. Added a four-body resident-affine cache in `NativeCivilianActors`. It parks
verified detached bodies, checks identity/profile/snapshot and a serialized fingerprint
before reuse, replaces assignment/replication bindings, and evicts only verified parked
references when capacity is needed. Cross-resident reset is not implemented: inspected
stock load paths append some collections and leave some empty equipment references intact.
See [[../Design/Civilian Pool and Navigation]] for the restricted contract and provenance.

The extended headless batch passed **17 checks**, including eight warm reactivations of
the exact same four Java objects, retained item IDs, stale-token rejection and walking.
Existing stock save/load and door cases also passed. Final parked/active/owned counts were
zero and the isolated server stopped gracefully. No interactive/normal/production session
or client install was changed. Final report:
`artifacts/civilian-headless/20260927-213421-fd9522/ipc/native-report.json`.
Agent SHA-256 `0527111a72c87229a0c917f059cf9946681c5da6bca137b90a3c5ee5912bbd69`.
Private inspection: `artifacts/decompiled/actor-reuse/`. It also exposed the stock delayed
emitter cleanup; parking finishes that cleanup for the owned body so it cannot stop its
sounds after reuse. Existing IsoPlayer hash guards cover the reflection dependency.

Warm activation maximum **1.246405 ms** (8 samples), p95 histogram upper bound 1.3 ms;
cold p95 upper bound 130.64 ms. Seven constructors include the separate stock restoration
and door fixtures; the four-body reuse rounds constructed only four objects. Total harness
p95/p99 upper bounds 5.4/132.74 ms, walking 0.5/1.7 ms. Release performance still fails its
target; startup prewarming and cache-miss scheduling are not implemented. The earlier warm
run `20260927-213253-fdda0a` passed before the emitter cleanup fix, with warm max 1.67 ms.
Both are short functional runs, not statistical capacity/soak qualification.

Java unit/vehicle regressions ran before each native batch; pinned-engine integration passed
in `artifacts/runtime-tests/20260927-213438-68ee4148/report.md`. Visual/two-client reuse,
full traversal, integrated idle/roam/flee, reopened combat gate 3 and general resident-to-body
reset remain pending. Next priorities: explicit warmup/admission policy and reset feasibility,
then connect the existing controller to native pool/path/perception ports and exercise
roam → visible-threat flee → blocked/trapped → recovery headlessly before the watched batch.

## 2026-09-27 — native zero-client civilian harness

User authorized headless native testing after the detached foundation. Added
`./dayone civilian-headless [run|status|stop]` and an opt-in, world-prefix-guarded Java
harness. Each batch uses a fresh private cachedir, copied JAR/manifest/mods and unpublished
UDP ports; loopback RCON 27045 is separate from all existing sessions. Existing `.132`
interactive test and `.160` were not operated. No GUI client was launched.

The real server exposed two fixture problems before the final pass. Run
`20260927-205705-548831` timed out loading: `ServerMap.characterIn(int,int,int)` takes
8-tile chunk coordinates, not tile coordinates. Verified bytecode in private
`artifacts/decompiled/headless-server-map/` and corrected the call. Also supplied empty
AnimSets/actiongroups directories for the new API-only mods to satisfy B42 directory scans.
Run `20260927-205919-41e71f` then passed core path/walk/body/pool cases. The next run
`20260927-210142-51e726` failed to find a suitable unlocked map door; replaced discovery
with a precisely owned native door fixture using the game's public construction path.
All failures retained evidence and stopped gracefully; none counted as successful checks.

Final run `20260927-210440-841d18` passed 15 native checks with no clients and zero owned
Actors at completion. Report: `artifacts/civilian-headless/20260927-210440-841d18/ipc/native-report.json`.
Agent SHA-256 `cb5f43287b0bf4bf1a1dd05c1de3dc6304454af4eb8ba1d0666887c7a13375fb`;
world version 249. Covered native path callback, collision-constrained walk, in-flight
cancel, stock body round trip, three four-slot lifecycle rounds, closed/locked/open door
behavior and verified owned Actor/door cleanup. The server then exited after RCON quit.
Native body checks cover wounds/items/nested contents/hands/clothing count, not exhaustive
appearance or restart recovery. Pool rounds test lifecycle, not four moving pedestrians.

Regression validation: 240 Python tests and Java unit fixtures passed in
`artifacts/runtime-tests/20260927-210730-d2655876/`; engine integration/premain checks
passed in `artifacts/runtime-tests/20260927-210934-673fc2e8/`. Native case success is separate
from boot log health: existing engine asset/property diagnostics remain in retained logs.

The short batch does not pass performance qualification: walking histogram p95/p99 upper
bounds 0.6/1.5 ms, total work 4.8/156.86 ms, materialization p95 ≤149.81 ms (overflow bucket
reports the maximum). Follow up on Actor creation spikes before crowd scaling. Native
stairs/climbs, integrated FSM/flee, visual/two-client agreement, combat gate 3 and runtime
session integration remain open. See [[../Runbooks/Civilian Qualification Batch]].

## 2026-09-27 — pedestrian feasibility foundation

First watched submission failed before creating an NPC: the legacy spawn permit required
LofersStoryteller's sandbox flag, absent from the isolated module list. Fixed the Java
activation guard to recognize the disposable runtime and added a native no-spawn permit
preflight during Lua initialization. Clean pre-creation refusals now release their reservation;
exceptions remain unresolved. Full Java fixtures/premain passed after that change.

Prepared a separate retry world `AKR_DayOne_Test_20260927_141446_320776`, retaining the failed
world and copying its test character database. An idle Lua adapter refresh passed in the
same new server epoch. Its Core dispatcher keeps keyed subscriptions, and the controller
calls the native pathfinder update explicitly. Two additional Lua regressions cover missing
activation and idle reload without duplicate subscriptions. Native walking is still pending.


Accepted the experiment-first revision in [[Design/NPC First Slice]]. Added typed pedestrian
cases and immutable parameters, bounded scheduler/socket and server Lua entry points,
resource retention on uncertain cleanup, server-only ownership and native stream hooks,
Core-backed disposable Lua spawning/presentation and first diagnostic samples. The existing
client-owner pedestrian implementation is not silently substituted or enabled by this path.

Offline suite passed: `artifacts/runtime-tests/20260927-140257-ea8a1bf2/`, 216 Python tests,
Java layers/premain, native planner core/wire. New fixtures cover request conflicts, invalid
coordinates/population, full-queue cancellation, blocked cleanup and a real private socket.
First actual boot exposed Kahlua's absent `next()`; replaced it and added a focused regression
(12 tests passed). Socket tooling corrections cover project Python and long socket paths.

Deployment: prior empty vehicle probe stopped gracefully with no owned native car. Fresh
pedestrian world prepared independently, ordinary original Core/DevTools installed locally,
private API handshake verified. The corrected boot registered the Lua adapter; a real
empty-server submission failed with `observer_required` and no retained resources, recorded
in `artifacts/scenario-tests/20260927-140310-3dd59f/empty-server-api-check.json`.
Native movement, effects, cleanup after death, two-client
agreement and stress gates remain pending. See [[Runbooks/Pedestrian Experiment]] for limits.


This ledger separates requested design from measured implementation evidence. The deployed
0.1.0 prototype remains an observation world. The 0.2.0 First Week implementation is under
development; it is not a complete or multiplayer-validated release.

## Layered offline runtime suite — 2026-09-27

Added `./dayone runtime-test [offline|unit|integration|worker]`, with per-step logs, JSON/Markdown
reports, source revision/dirty status, failure propagation, process-group timeout cleanup,
interruption receipts and a shared-output lock. Java detached fixtures now have a separate
entry point; the existing builder's `--generate-only` behavior is preserved.

Full offline run `artifacts/runtime-tests/20260927-125049-bb1ba0ea/` passed: 204 Python tests,
Java unit/integration/agent-launch checks, C++ core and 13 worker socket/protocol scenarios.
The new runner tests cover nonzero exits, timeouts, missing tools, overlapping runs, interrupted
reports and explicit separation from in-game acceptance. No game server was operated.

[[Runbooks/Runtime Testing]] maps regression coverage and remaining functional gates. Native
functional batches, live event protocol/reload and two-client validation remain pending.

## Resident lifecycle integration in source — 2026-09-27

Implemented a bounded detached scheduler/ownership ledger, then connected the existing probe
to it. `ScenarioAgent` now owns the extracted native-world/terrain adapter. Legacy commands
use FIFO with priority stop; execution records car, terrain and passing ownership. Cleanup
retains unresolved engine references, checks Java/native removal and blocks subsequent starts.
The missing opposing helper is replaced by explicit rejection; two-car execution remains absent.

Validation: full agent fixtures pass against pinned B42.20.4/Java 25, including JVM bytecode
guards and launcher discovery. New cases cover retry identity, capacity/cancellation, 128
detached scheduler lifecycles, and 100 mocked terrain leases sharing one initialization.
Partial native initialization/activation/deactivation failures retain ownership as appropriate.
These are detached/fault-injection tests, not native soak or multiplayer evidence. Logs:
`artifacts/runtime-resident-agent-tests.log`; prior Python suite: 191 passed in
`artifacts/runtime-foundation-python-tests.log`. Built JAR SHA256:
`178568c1dd65c56a6326446540a6155c07143e858e52d3b3bb7959dab77f676d`.

No restart, deployment or live scenario occurred. Routes and vehicle execution remain tied to
the legacy adapter; complete typed definitions, sockets/Lua, durable recovery, reload and batch
tooling are pending. Keep the planned installation restart until those iteration tools are ready.
See [[Current State]] and [[Design/Live Event Runtime]].

## Runtime framework priority and saved handoff — 2026-09-27

After the accepted stove collision, the user requested an opposing-car test, then redirected
work before implementation completed: first design a reliable way to inject events, reload
behavior and run batches without restarting the server for every iteration. The saved plan
is [PLAN_SchedulerAPI.md](../../PLAN_SchedulerAPI.md); [[Design/Live Event Runtime]] records
the architecture and evidence, and [[Current State]] records the exact deployment/source
boundary. The head-on test remains pending behind this framework work.

At 12:18 UTC the disposable server reported phase `complete`, no managed body, native count
zero, no error and zero online players. Its validated private JAR is unchanged. The incomplete
opposing-car source edits remain unbuilt/undeployed and include a missing helper reference;
a local diff/state/plan backup is under `artifacts/handoffs/20260927-live-event-runtime/`.
No restart, experiment or framework implementation occurred during this documentation task.
The existing unrelated CMake edit is preserved.

## One-client fast crash accepted; rock and opposing-car requests — 2026-09-27

The first client attempt was aborted when the observer moved inside the 12-tile route buffer;
all fixtures were cleaned. The next contact passed telemetry but the user could not see it
well at night. After setting the disposable clock to noon and clearing weather, the user
confirmed daylight and accepted the replay: “The car ate a wall. It looked right for this one.”
Evidence: `artifacts/scenario-agent/fast-sports-brake-impact-client-20260927_102200/`.
Peak 100 km/h; final pre-contact sample 92.64 km/h, 80% brake request / 17.28 native force.
Native impact severity 77.712524, one crash sound, no feedback failures or new client errors.
Hood 100->46, windshield 100->39, headlights 100->41; engine remained 100. Four other cars
retained coordinates. Tagged pole and managed body cleaned; native count zero. Warm mean
0.162 ms, p99 upper bound 0.6 ms. This is one-client acceptance, not two-client consistency.

Next requested sequence: retain a **rock / rollover attempt first**, with the obstacle
removed about three seconds after contact, then test **two cars moving in opposing
directions** as a subsequent experiment. Do not replace the rock trial with the car-car trial.
Neither a rollover nor managed two-car native collision is yet demonstrated. Ordinary parked
Java cars still do not provide native vehicle bodies; the opposing test requires explicit
registration, ownership, independent controls, contact feedback and cleanup for both cars.

## One-client stove impact accepted after reconnect — 2026-09-27

The first client attempt (`stove-impact-client-20260927_110926`) coincided with a reported
client freeze. It aborted with `invalid_observation` near 1 km/h; car and tagged fixture were
removed, native count zero. The cause of the freeze is not established.

After the user restarted and reconnected, the rerun completed:
`artifacts/scenario-agent/stove-impact-client-20260927_111158/`. The user confirmed:
“Saw and heard it; game stayed responsive.” Peak 103.35 km/h, final approach
sample 100.40 km/h, native crash severity 81.2255, one
stock crash sound, maximum tilt 4.46°. No rollover is demonstrated.
No new client error/exception entries were found in the captured log interval. Fixture
removal occurred 3.15 seconds after observed contact; managed-body cleanup
completed with native count zero and 5 unrelated cars unchanged.
This accepts the one-client collision/sound presentation, not two-client consistency or
NPC death. The separate opposing-car test remains next in the requested sequence.

## Alternative collider preparation — 2026-09-27

Stock boulder contacts did not roll the car: the first measured maximum tilt was 4.97°.
An original convex rock collider, scoped to the single tagged disposable fixture through
an injected post-calculation hook, also stopped the car without rolling it (3.68° maximum).
The latest isolated run completed and removed both fixture and managed native body:
`artifacts/scenario-agent/sloped-rock-impact-empty-20260927_104149/`.
The first mesh attempt failed before spawning because the game object list does not support
iteration; indexed access fixed that error. No rollover or client acceptance is claimed.

User subsequently requested another collider and more speed. The next reviewed object is
`appliances_cooking_01_16`, the antique wood stove, whose installed tile has `solidtrans` and
therefore uses the stock solid collider. It does not use the experimental rock hull. The
extended disposable impact speed ceiling is now 120 km/h, matching the installed stock
SportsCar script maximum; actual drivetrain, tyre, mass and brake capabilities still apply.
Normal driving ceilings are unchanged. The original rock scene remains available, and the
opposing-car test remains a separate subsequent experiment. Neither a stove contact nor
more speed guarantees a rollover. Fixture removal remains about three seconds after contact.

The first stove run reached 103.68 km/h and 44.56° maximum tilt, with a real crash severity
of 83.12038 and one stock crash sound. Hood fell to 34, windshield to 42, front lights to 37;
engine remained 100. Cleanup completed with native count zero. Evidence:
`artifacts/scenario-agent/stove-impact-empty-20260927_105355/`. This is empty-server evidence,
not a rollover or client observation. The requested 120 was limited by the planner's older
18-second preference. The revised extended-only cap now derives from the checked 320-tile
stopping preview (retaining a three-tile allowance), still bounded by the stock vehicle and
route-end stopping envelope. Ordinary-route preferences are unchanged.

The 480-tile replay still peaked at 103.32 km/h because the endpoint entered the braking
preview before contact (`stove-impact-empty-20260927_105707`). It completed and cleaned up.
The next course keeps the same start, target and observer location but extends the downstream
runout to 600 tiles total. Extended-only retained bounds are 1,900 swept tiles, 320 chunks,
20 loading anchors and native map width 81; per-tick validation and scan budgets are unchanged.

Final empty-server validation passed in
`artifacts/scenario-agent/stove-impact-empty-20260927_110054/`: peak **114.66 km/h**, final
pre-contact sample **110.23 km/h while applying 17.28 native brake force**, native crash
severity 91.871956 and one stock crash sound. Maximum tilt 11.54°: no rollover. The tagged
stove was removed 3.11 seconds after observed contact; the managed body was retained for the
viewing hold and then removed (native count zero). Five unrelated loaded cars retained their
coordinates. Warm mean 0.294 ms, p99 upper bound 1.6 ms; two ticks exceeded 2 ms, none exceeded
5 ms (maximum 3.219 ms). Final JAR SHA256:
`43c057c30ad71397f2fcf86461d141a7f5da751c10f6799aee64b2bb49711c8e`.
Java fixtures and 191 Python tests passed; logs are `artifacts/stove-impact-agent-tests.log`
and `artifacts/stove-impact-python-tests.log`. The disposable server is running with noon and
clear weather for the requested client check. This scene has no living NPC occupant; neither
NPC death nor total engine destruction is demonstrated. Client confirmation was pending at preparation; the later accepted reconnect is recorded above.

## Faster stock sports-car brake failure — 2026-09-27

User requested substantially more speed. Only explicit extended impact experiments now admit
`Base.SportsCar`, using its installed stock drivetrain and a wider/longer clearance footprint.
Normal routes retain their previous car and speed restrictions. The extended experiment has
480 tiles / 100 km/h bounds, at most 16 loading anchors, 256 retained chunks and native width
65. Scanned road tiles remain capped at 1024 per tick; forward stopping preview is bounded at
320 tiles with a larger deduplication bitmap. No stock power, velocity or damage override.

JAR `a1fbf71c19ca02d0d353e6b438b6ed24553c02f48688913b2daa3d55b1dbb76a`;
full Java fixtures and 191 Python tests passed. Evidence logs:
`artifacts/faster-brake-failure-agent-tests.log`, `artifacts/faster-brake-failure-python-tests.log`.
The course now runs X 10588.5, Y 9820.5 -> 10300.5; the marked pole and clear side-view
position remain at Y 10060.5. All 1455 swept asphalt tiles passed offline/live checks.

The first empty trial (`fast-sports-brake-impact-empty-20260927_101330`) reached 100 km/h,
but the operator brake-damage action was too late to establish a pre-impact braking attempt.
The corrected trigger at route progress 210 passed in
`artifacts/scenario-agent/fast-sports-brake-impact-empty-20260927_101453/`:
stock available braking force 108 -> 21.6, driver request 80%, native applied force 17.28.
Before contact, samples show 99.96 -> 95.53 -> **91.09 km/h while braking**; peak was 100.
One native impact (severity 77.10653), one `VehicleCrash` sound request. Hood, windshield and
headlights fell 100 -> 41; engine remained 100. This is stronger real damage, not a total wreck
or a living NPC death. The empty vehicle remained visible to the server for 20 seconds,
then tagged pole/body cleanup completed with native count zero.

Warm mean 0.176 ms, p99 upper bound 0.7 ms, maximum safety scan 0.591 ms. Unrelated loaded
vehicle coordinate comparison and cleanup are recorded in `verification.json`. Client
observation of this faster run is pending; test server remains ready on .132:16281.

## Brake failure on a longer clear-view course — 2026-09-27

Requested: a faster native crash on a different street, then damage the brakes while the
controller still attempts to stop. The isolated experiment now supports an explicitly marked,
straight extended impact course (maximum 320 tiles, 80 km/h ceiling). Ordinary routes retain
60 tiles / 50 km/h limits. Eleven or fewer 9-chunk loading anchors retain the corridor; native
map width is capped at 45, retained chunks at 192, road tiles at 1500. Forward safety work has
an explicit 1024-tile ceiling and 20-second forecast in this experiment only. No engine power,
velocity, impact damage, core files or client Java agent were modified.

Empty-server evidence: `artifacts/scenario-agent/brake-failure-impact-empty-20260927_100456/`.
JAR `e38ec046e2066ae2baaeadf5f8d8acfaca3729918836c561d79a0e513532e28e`.
Course: X 10588.5, Y 9900.5 to 10220.5; tagged pole at 10588,10060; observer
10605.5,10060.5. Original ground data and a bounded live inspection found no trees, walls or
fences across the side-view area. Real client visibility remains pending.

The car reached its observed stock 70 km/h maximum. At progress 129.56, speed 69.81 km/h,
all four brake part/item conditions were set to zero through stock APIs; available braking
force fell from 80 to 20. Zomboid retains residual braking at zero condition. The controller
requested 80% braking and native force was 16. Last pre-impact sample was 51.50 km/h;
this is a sampled approach speed, not an exact contact-speed measurement. One real crash
registered severity 40.43606 and one `VehicleCrash` sound request. Hood/windshield fell
100->72; headlights 100->69. Engine remained 100: **this was not a destroyed car or NPC death**.
There is still no living occupant in this fixture.

The damaged vehicle remained for 20 seconds. Tagged pole and native body were cleaned;
three other loaded vehicles retained their coordinates. Warm mean 0.219 ms, p99 upper bound
0.7 ms, maximum safety scan 0.759 ms; cold vehicle creation reached 17.01 ms. Full Java
fixtures and 191 Python tests passed; logs `artifacts/brake-failure-agent-tests.log` and
`artifacts/brake-failure-python-tests.log`. Private operator harness and receipts are under
`artifacts/`; brake degradation is an operator test, not an enabled ambient incident feature.
Ordinary-client observation of this particular run remains pending.

## Confirmed low-speed crash and faster impact preparation — 2026-09-27

The user confirmed the ordinary client saw and heard the 5 km/h pole collision:
“It did crash, with sound.” Evidence: `artifacts/scenario-agent/pole-client-contact-20260927_094426/`.
One native crash, one sound request, zero feedback errors; hood 100->96, windshield
100->97 and left front door/window damage. All 16 other loaded cars retained coordinates,
no new client error lines, tagged pole and managed body cleaned. This is one-client
collision/audio acceptance, not two-client consistency or car-to-car contact.

At the user's request, a faster scene now uses the normal drivetrain, a reviewed 60-tile
straight asphalt route and a 50 km/h ceiling. It explicitly exempts only one marked pole
at one configured tile from avoidance. Missing/wrong/duplicate targets, other obstacles,
bypasses and junction stops remain rejected. Every connected player must stay at least
12 tiles from the route; entering that envelope requests braking. A stock crash stops
driving immediately, retains physics/braking and holds the damaged car for 20 seconds.
No velocity, damage or part-condition injection is used. Normal route configuration clears
both impact target and token so the exemption cannot leak into another course.

Agent `21286219739b4f17157514815136239a02b4f99dac8bc3494c66370a7b81fe21` passed all Java
fixtures and 190 Python tests. Empty-server evidence:
`artifacts/scenario-agent/high-speed-impact-empty-20260927_094922/`. Actual peak 35.41 km/h,
one native impact with stock severity 27.367 and `VehicleCrash2`, hood 100->75,
windshield 100->84, headlights 100->79 each. The 50 km/h ceiling was not reached on
this short course. Normal braking/viewing hold completed, exact fixture removed and native
body count returned to zero.

The client retry at `artifacts/scenario-agent/high-speed-impact-client-20260927_095052/`
reached **38.81 km/h**, recorded one stock crash (severity 31.122), and broadcast
`VehicleCrash`. The user confirmed: “Collision, damage and sound look right.” No new
client error lines; all other loaded cars kept their coordinates. The 20-second viewing
hold and exact fixture/native cleanup completed. This accepts the one-client faster pole
impact. It does not validate native car-to-car impacts or two-client consistency.

## One-client pole-detour refusal — 2026-09-27

The ordinary client observed the corrected approach, wait and rejection of the pole-blocked
shoulder. User report: “Smooth stop; stays visible.” Evidence:
`artifacts/scenario-agent/pole-client-denial-20260927_093229/`, final agent
`36971f0a24ebb65e8c2d25eadcf442a7a74391d3cd7bcfb8ff1e0f3883bde157`.
The willing driver repeatedly rejected `candidate_physical_shape_obstacle`; it did not
attempt the shoulder. Both exact parked fixtures and the moving probe were cleaned up,
with native body count zero. This accepts visible avoidance refusal only. A separate
low-speed pole impact is prepared for client collision/sound validation; no client impact
acceptance is implied yet.

## Pole collision activation, planning exclusion and crash feedback — 2026-09-27

The original map and live square at (10753,9864,0) both identify the pole as
`appliances_com_01_94`, with `PhysicsShape=Tree`, `StopCar` and `HitByCar`; the live
square has no `CarSlowFactor`. The planning bug was ignoring vehicle-collision metadata
on otherwise walkable tiles. Live and offline checks now reject those declarations and
legacy column sprites, while retaining the exact Floor exemption. The approved asphalt
approach remains valid; the original shoulder path is blocked by the pole.

Native inspection independently found that headless ServerCells store uploaded shapes but
never activate their obstacle bodies. Their separate flat ground supported driving and
masked the missing obstacles. The native server collision mask also excluded vehicle-to-
vehicle contact. The isolated probe now initializes its exclusively owned Bullet world
with the ordinary collision mode and one bounded ChunkMap; Java remains a dedicated server,
using ServerMap and normal authoritative replication. Existing global offsets are preserved.
No native/core files or client JVM flags changed. Missing custom mesh registrations fail
bounded chunk preparation; loading arbitrary mesh assets is not implemented here.

Actual stock crash calls already apply server damage. Their SoundManager path was silent
on a dedicated server. An identity-, thread- and authority-scoped hook now broadcasts the
matching existing sound only when the managed body's stock crash method executes. It does
not inject collision, change speed/pose, or apply duplicate damage. Exact class hashes and
bytecode/scope fixtures guard this integration. The `inspect` control loads the reviewed
area without creating a body, for bounded read-only scene inspection.

Native runtime evidence:
- `artifacts/scenario-agent/pole-native-baseline/`, agent `7b1201959ca35b5b2f731dec63df8202f5ab689fde98674b4ea197363d397030`:
  ordinary 44-tile route arrived, peak 33.34 km/h, no crashes, native cleanup zero;
  measured warm hook maximum 1.74 ms. This is a short-course peak, not sustained cruise.
- `artifacts/scenario-agent/pole-contact-proof/`, same agent: an empty-server private
  low-speed experiment inserted one marked temporary pole after initial road validation.
  At 5 km/h the car made real native contact and stopped at X=10738.82, before the pole
  at X=10740.5. Stock crash count 1, severity 1.487, hood 100->99, windshield 100->96;
  one `VehicleCrash1` broadcast requested, feedback errors 0. The exact tagged pole and
  managed car were removed, native bodies returned to zero. No sound was heard/validated
  by a client during this empty-server run.
- `artifacts/scenario-agent/pole-bypass-denial/`, agent `c31d20bea002e106ba6060c9d894b478bb8f7b2fafe3b38464bab41398a0c15b`:
  willing driver approached two road obstructions, waited, then rejected the shoulder
  with `candidate_physical_shape_obstacle`. No pass or crash occurred. All three preloaded
  cars retained exact coordinates through native initialization and the test; all fixtures
  cleaned. One native map created/removed, native bodies zero. Warm hook p95 <=1.1 ms,
  p99 <=1.8 ms, maximum 11.54 ms; this does not measure all engine physics work.

Automated checks: 190 Python tests and the complete Java fixture/build suite passed,
including missing-mesh rejection outside the planned route and crash-hook scope/bytecode
checks. Logs: `artifacts/pole-project-tests.log`, `artifacts/pole-agent-tests.log`.
Final agent SHA256: `36971f0a24ebb65e8c2d25eadcf442a7a74391d3cd7bcfb8ff1e0f3883bde157`.
Final-build runtime smoke: `artifacts/scenario-agent/pole-native-final/`; route arrived,
no crashes/errors, native bodies/maps cleaned to zero. Warm hook p95 <=0.4 ms,
p99 <=0.5 ms, maximum 1.79 ms. The later container shutdown occurred after completion
and logged save completion; it is separate from the successful drive receipt.
Private native and bytecode evidence is retained under `artifacts/`; no proprietary code
belongs in the source commit. Ordinary parked Java vehicles still need separately owned
native bodies, so physical car-to-car impact and fleet cleanup remain pending. Client
presentation/audio and two-client consistency are separate, unperformed checks.

## Client correction: road avoidance works, pole collision fails — 2026-09-27

The client clarified the outcome of `shoulder-denial-pass-20260927_000818`: the moving
car successfully avoided the road obstacles, but phased through a pole on its avoidance
path. Preserve this distinction: the maneuver worked; physical collision acceptance failed.
Do not infer collision safety from route arrival, no client errors, or geometric fixtures.
Further shoulder trials are on hold pending collider investigation; this is an operational
hold, not evidence that the runtime shoulder option has been removed or disabled.

Read-only inspection shows chunk upload handles built-in `PhysicsShape` values separately
from custom `PhysicsMesh` values. Custom mesh upload depends on a registered Bullet mesh
index; a missing index skips that mesh. The probe calls chunk upload, but does not explicitly
initialize the mesh registry. This is a candidate cause, not a confirmed diagnosis of this
particular pole. Private research: `artifacts/decompiled/collision-shapes/`.

The same client-present run independently preserved all 17 preloaded vehicle coordinates
(maximum displacement 0.0). The saved control car and all test fixtures were removed.
A single warm hook outlier reached 145.906 ms (p95 <=0.4 ms, p99 <=0.8 ms); its cause is
unresolved. Collision shapes, native contact response and impact feedback must be verified
before further shoulder acceptance or managed multi-car trials.

## Shoulder refusal/pass and server coordinate frame — 2026-09-27

The next native shoulder test exposed two limitations in the original fixed detours.
First, the original route retained a nine-tile observation margin, while a shoulder detour
needed another chunk row. Passing routes now retain a bounded thirteen-tile margin up front;
candidate geometry still must fit entirely within that retained coverage. Second, the
RCON-created parked cars faced across the road. The inflated footprint correctly rejected
the narrow gap beside one of those cars. The revised fixture uses the game's debug factory
and sets a road-parallel heading before its first update; clearance was not weakened.
Evidence of the failed trial and exact fixture recovery is retained at
`artifacts/scenario-agent/shoulder-denial-pass-20260926_235516/`. Two fixtures had unloaded
before cleanup; their exact saved locations were reloaded and checked before native removal.
The successful harness removes fixtures during its terminal viewing hold, before unloading.

An independent inspection found unrelated parked vehicles with invalid coordinates in the
older disposable world. Its private historical database snapshots show repeated 64,000-tile
shifts. The late `WorldSimulation.create()` call changes global offsets after headless
vehicle transforms already exist. The probe now initializes its own native server world
in the existing coordinate frame, without changing global offsets or any vehicle pose.
It rejects a preexisting unowned native world. Source/bytecode evidence is under
`artifacts/decompiled/physics-world-offset/`; historical rows are recorded in
`artifacts/scenario-agent/frame-offset-investigation.json`. The old disposable world is
preserved, not repaired or reused for this proof. No playable or production world was touched.

Fresh world: `LofersVehicleProbe_20260926_235220_a91875`. Final tested agent SHA256:
`b13b83912a67839c1c96d01e7e42c86faa4c3734eafa634ade45f4407fd7a090`.
The new shoulder course at X=10732.5–10776.5, Y=9861.5 uses explicitly supplied installed
erosion definitions to resolve decorative road cracks. Text and binary inputs are hashed;
conflicting duplicate tile definitions remain unknown and fail surface validation.
Automated checks: **170 project tests** plus all Java fixtures, including the actual
chunk-boundary regression and blocked-shoulder geometry. Logs:
`artifacts/shoulder-project-tests.log`, `artifacts/shoulder-agent-tests.log`.

Empty-server evidence: `artifacts/scenario-agent/shoulder-denial-pass-20260927_000257/`,
epoch `a20db534-c1f3-47df-9d12-cdd7cbac5184`. A deliberately selected willing test personality
first refused the blocked shoulder. After two unsuccessful requests, only the shoulder
obstruction was removed. Both road fixtures remained. The next request passed along the
right shoulder, rejoined the lane and reached the destination. Shoulder peak was 7.43 km/h,
maximum Y=9864.6182; full-course approach peak was 17.55 km/h. Both retained parked fixtures
kept identical positions. All sampled coordinate offsets stayed zero. Warm hook p95 <=0.4 ms,
p99 <=0.6 ms, max 2.975 ms. Native body count returned to zero; all three obstruction
fixtures were removed. One separately recorded stationary control car was saved at
(10735,9855) for a restart/client-present initialization check. This is not client visual
acceptance or a complete preloaded-vehicle preservation proof; those checks are pending.

Two-car execution still requires a shared native-world/cell lifetime and one batched
reservation coordinator. Do not instantiate two independent copies of the single-car probe:
its body-count and terrain-ownership assumptions deliberately exclude that use. Moving
managed-car clearance, changes during a pass and opposing-driver fairness remain live gates.

## Parked-car passing, audible horn and optional shoulders — 2026-09-27

The user requested temperament-dependent passing, then clarified that willing drivers may
attempt off-road avoidance while respecting physical obstacles. The opt-in straight-road
probe now proposes two bounded cubic detours on the private worker thread. It validates
loaded surfaces incrementally, rejects stale observations, checks oriented vehicle
footprints and reserves the entry/pass/rejoin corridor before applying native controls.
Release requires the actual padded footprint to clear the reserved corridor. The static
obstacle remains in place for the whole maneuver; the original destination is preserved.
Road passing is capped at 15 km/h and retains vehicle capability constraints. Geometry work
is separate from the game thread. The probe seeds personality with its command identity,
avoiding reuse of a recycled native vehicle ID; residents still need durable resident seeds.

The separate 44-tile course runs east along Y=9861.5 from X=10666.5 to 10710.5. The offline
reader checked both lanes across 294 tiles. An earlier site had unknown decorative crack
definitions and was rejected without weakening the reader. The parked fixture spawns at
command (10689,9862,0), actual centre approximately (10688,9861.8984). The observer stood at
(10688.5,9864.5); the first artifact's 9866.5 suggestion was behind a fence and has been
corrected in the baker. Original evidence artifacts retain their original hashes/metadata.

Automated checks passed: **168 project tests** plus Java compatibility, premain, protocol,
native-asset, route/controller, reservation and new bypass fixtures. New coverage includes
oriented clearance, denial when both sides are blocked, independent physical approach/rejoin
simulation, terrain rejection, stable off-road choices and invalid route flag rejection.
Private logs: `artifacts/traffic-bypass-project-tests.log` and
`artifacts/traffic-bypass-tests.log`.

Empty-server evidence: `artifacts/scenario-agent/native-parked-bypass-20260926_232324/`
passed with agent `6f91bc76051a77ab00565610235974b404ad9e0dab7cdbf3e03ae07292c4e08f`:
27.90 seconds waiting, left pass, lane return and arrival. The following run,
`native-parked-bypass-20260926_233003/`, used the final deployed agent
`7969caf6661ea2e458dcb878b5aba476bcd19178d3834241fd8076e8c9dbe545` and epoch
`61e2a134-7e55-42b2-8c52-b8c6f562a1ed`. It waited 18.50 seconds, passed on asphalt,
rejoined and arrived; peak 18.33 km/h. Detached geometry took 10.89 ms. Warm hook
p95 <=0.6 ms, p99 <=1.3 ms, max 3.716 ms. Native bodies returned to zero and the exact
parked fixture was removed. These runs had no clients.

One-client evidence: `artifacts/scenario-agent/client-parked-bypass-20260926_233245/`,
same final agent and epoch, akr connected. The user confirmed **smooth pass and lane return;
heard a honk**. The car waited 20.60 seconds at (10679.2422,9861.5), passed on the left
to a minimum Y=9858.3984, rejoined, and arrived at (10709.8359,9861.5). Peak 16.56 km/h
on the complete course. All 83 waiting and 37 passing snapshots retained body, world,
registry and chunk membership. Three sampled snapshots had native horn state enabled;
audibility is established by the separate user report. There were **zero new client error
lines** in the captured console interval. Detached geometry took 15.40 ms; warm hook
p95 <=0.4 ms, p99 <=0.9 ms, max 4.485 ms. Cold hook max was 14.491 ms, dominated by
vehicle creation; these are added probe hook measurements, not total server tick timings.
One bypass completed, its reservation cleared and native body count returned to zero.
The exact parked fixture (native 245, persistent row 20) was removed through the game's
method with identity/rest/occupancy guards and verified absent from the database. The
temporary server Lua extension was restored byte-for-byte. No playable or production world
was changed. The disposable server remains available, with no active test car.

Shoulder fallback is implemented and fixture-tested but **not natively demonstrated**.
Road candidates are preferred; one stable episode roll can admit known dirt/grass/paved
edges at 6–8 km/h with 60% planning braking/lateral preferences. These are conservative
preferences, not measured terrain friction. Walls, actors, vehicles, supported ground and
a lane rejoin remain mandatory. Next gates are native shoulder success/denial, managed
multi-car arbitration, changing obstacles/rejoin blockage, native impact/damage, resident
commutes and two-client agreement. The C++ planner still does not drive this probe.

## Planned stopping, queue persistence and traffic personality — 2026-09-27

The longer-held obstacle trial still failed the user's visual acceptance: the car accelerated
about two tiles, stopped abruptly with a distant untouched obstacle, then appeared to disappear
after about five seconds. Private evidence `client-parked-visible-20260926_224219/` recorded
20 seconds of native-body presence but did not establish the client's view or world/registry
membership. Do not reinterpret that trace as a passed visual test. The specific parked fixture
was removed through the game's method and verified absent from the database.

Cause of premature braking: every finite predicted contact triggered full emergency braking,
even a parked car several seconds away. `ParkedObstacle` now computes a bounded geometric
stopping point; `ProbeDriver` uses the existing capability-aware speed/braking envelope,
waits without a stall failure and resumes its original route when the obstacle clears.
Scheduled stops are preserved. Moving, occupied, client-owned or unknown hazards retain
the stricter response. Queue time is excluded from the route deadline and bounded to five
minutes in this disposable harness. World, native registry and chunk membership are checked
and published separately from native-body existence. These fields are not packet receipts.

`TrafficTemperament` and `TrafficBlockage` provide stable preferences, optional one-episode
honking, patience and staggered bounded bypass requests. Stock server horn start/stop calls
are used; no client patch or replacement game class is involved. The probe uses native car
identity as its test seed; integrated residents will require their durable resident seed.
`TrafficPassReservations` has a bounded four-car arbitration model: oldest waiter wins an
overlapping corridor, stable ID breaks ties, native generations/token matches prevent stale
release, unused leases expire, and entered stale leases retain occupancy while stopping
permission. Fresh matching observations may reconcile the owner; confirmed exit releases
the corridor. It is not yet wired to a native multi-car executor. No bypass is executed by
this iteration. [[Design/Traffic Incidents]] records the accepted wait/honk/pass behavior,
safe opposing-lane use, conflict resolution and bounded imperfect-driving requirements.

Automated: compatibility/premain/protocol/native-asset fixtures and existing turn simulations
pass, plus independent parked-approach simulations for normal and weaker/heavier cars. They
wait 35 seconds without stalling, preserve the stop sign, then finish after obstacle removal.
Tests cover horn probability/delay, deterministic traits, bypass retry pacing and competing
reservations including stale occupied corridors and generation recovery. Evidence:
`artifacts/parked-approach-reservation-tests.log`. This is not multi-car physics validation.

Empty-server native evidence: `artifacts/scenario-agent/native-parked-queue-20260926_225816/`,
deployed agent SHA256 `6979d5977be560fe18274c16e2600418119d6bf4fc798c4f840147f194e54e7f`,
epoch `02c56483-d54d-454f-9eab-416ee4918588`. The car traveled 23.16 tiles and stopped at
(10806.6641, 9861.5), roughly 6.35 tiles from the parked fixture centre. It waited 40.69
seconds, retained every sampled body/world/registry/chunk check, then resumed and completed
the original route and stop after exact fixture removal. Peak 22.25 km/h; native body count
returned to zero. Warm hook p95 <=0.5 ms, p99 <=2.4 ms, max 4.192 ms. No client was connected.

One-client evidence: `artifacts/scenario-agent/client-parked-queue-20260926_230154/`, same
deployed agent and epoch. The user confirmed **smooth stopping and continued visibility**,
with no horn heard. The car stopped at (10806.5625, 9861.5), waited 66.34 seconds, then the
server recorded resumption, one served stop sign and `route_arrived`. Peak 24.43 km/h;
native bodies returned to zero. Warm hook p95 <=0.2 ms, p99 <=0.4 ms, max 3.529 ms. This
run produced no new client error lines. All 262 sampled waiting snapshots retained native
body, world, registry and chunk membership. This
driver's episode roll was 0.8770 against a 0.4913 horn chance, so silence was selected rather
than a failed sound invocation. Audible horn replication still needs a selected-horn trial.
The user accepted the approach and wait; post-removal driving is server trace evidence.
Both fixtures were removed through exact-ID/script/position/rest/occupancy-checked native
removal; the one-use test Lua extension was restored byte-for-byte. Production and playable
worlds were not changed. Passing, moving traffic, intentional native crashes, integrated
resident trips and two-client agreement remain separate pending gates.

The final source/build additionally contains the detached reservation arbiter and reserves
enough road deadline for the full five-second brake timeout plus 20-second viewing hold;
legacy straight probes keep their original eight-second reserve. The running test epoch
retains the explicitly recorded earlier JAR. Final source will load on its next restart;
do not attribute the native/client receipts above to an unrun binary hash.

## Observed smooth turn and vehicle-capability iteration — 2026-09-27

The first observed Bézier/stop run reached its goal but **failed visible smoothness**.
The user heard/saw braking during the turn. The trace showed a second, abrupt 5 km/h
curvature threshold and two full-brake commands caused only by cooperative scan timeouts;
speed fell to 1.31 km/h. Evidence: `artifacts/scenario-agent/client-suite-20260926_215614/`.

Removed the duplicate speed threshold, made service-brake corrections continuous, and
replaced live scan timeout braking with hard operation limits plus duration telemetry.
An empty-server run then held 5.870–5.904 km/h through the measured bend with zero braking.
Evidence: `artifacts/scenario-agent/continuous-turn-2e28eb76-82f4-483e-bd9e-b2241346006a/`.
The next one-client run held 5.869–5.902 km/h with zero bend braking; the user explicitly
confirmed an excellent, smooth turn, but rejected the low speed. It completed the configured
stop and used the correct exit lane, then stopped for `actor_in_vehicle_path` near the goal;
do not report a clean `route_arrived` result for this run. No new client errors were recorded.
Warm hook p95 <=0.2 ms, p99 <=0.3 ms, max 0.415 ms; maximum safety scan 0.358 ms.
Evidence: `artifacts/scenario-agent/client-continuous-turn-20260926_221020/`, agent SHA256
`3e155fed42dc09c6e04baab0e8e5313d3f20b3ed38dfb5152cf87e1bff77b783`.

The parked-car trial exposed a real adapter bug: `getOwnVehiclePhysics` throws
`Vehicle not found` for a normal parked car without a server native body. The probe failed
before moving and removed its own body. This was **not successful blocker braking**.
Evidence: `artifacts/scenario-agent/client-parked-obstacle-20260926_221214/`.
The parked fixture was spawned only in this disposable world, at approximately
(10803.0, 9861.8984); its SQLite persistent ID is 19, absent from the before-test snapshot.
Replacing the lookup with bounded native snapshot enumeration distinguishes body absence
from invalid/incomplete observations. Unknown moving or client-owned cars still stop the probe.

The user requested faster driving with a roughly 50 km/h street limit, while explicitly
respecting each car's engine power, rates and properties. The revised adapter uses the
installed drivetrain/gear/RPM and service-brake routines, caps requests by their outputs,
applies loaded mass to Bullet and respects script steering limits. Curvature and braking
preferences now use observed vehicle capabilities; details and limits are in
[[Design/Navigation]]. The test course remains too short to prove sustained 50 km/h travel.
The earlier smoothness pass applies only to the 6 km/h bend; faster results require separate
evidence. Raw source reconstruction remains private under
`artifacts/decompiled/vehicle-capabilities/`; no engine implementation is redistributed.

Automated checks: 163 project tests and 116 Observer tests/frontend build passed before
the capability changes. Native C++ core and 13 actual socket scenarios also passed, including
32-resident/30-batch round trips at p50 14.741 ms, p95 18.688 ms and max 20.723 ms. These
detached worker tests do not connect its plans to the current test car. The capability
iteration passes all **164 project tests** (including 31 targeted Python checks), Java compatibility/protocol/premain/native-asset
fixtures and ten independent bicycle simulations, including a weaker, heavier vehicle.
Private logs: `artifacts/all-test-project-observer.log`, `artifacts/all-test-native-worker.log`,
`artifacts/vehicle-capabilities-tests.log`. Native/client capability calibration is separate.

The first native-snapshot calibration rejected the JNI header's fractional integer encoding
(observed ID 253.1 / wheel count 4.1). The parser now follows the pinned engine's truncating
read, with finite/range/duplicate/completeness checks; a regression fixture includes this
encoding. The subsequent native parked-car test successfully detected `predicted_vehicle_contact`
and stopped at (10785.65625, 9861.5), about 17.35 tiles from the stationary fixture centre.
Peak speed 11.733 km/h, cleanup native body count zero; no client was connected. Warm hook
p95 <=0.5 ms, p99 <=2.0 ms, max 1.972 ms. Evidence:
`artifacts/scenario-agent/native-parked-fixed-20260926_222813/`.
This proves conservative detection/braking for one static obstacle, not contact/damage or
moving-traffic avoidance. A one-use native Lua removal attempt did not remove the fixture;
the original test Lua file was restored. After graceful shutdown, the recorded fixture row
alone was removed from a backed-up disposable vehicles database; all other blobs were
unchanged and SQLite integrity passed. No running save or player vehicle was edited.

The clear-road native capability run then completed with agent SHA256
`6b2e7f6664f80d6b48bb89410ab6b9d2b66b2b7500cae53bd455907a444fee5b`, epoch
`9b12adff-2a7d-4dcc-b22c-978f60f21dfb`. The street ceiling was 50 km/h; actual peak was
22.873 km/h on this short stop-sign course. Six 250 ms samples in progress 33–39 measured
11.950–11.996 km/h and **zero applied braking** through the bend. The repaired SmallCar's
loaded mass was 934 kg, top-speed property 70 km/h and steering slew limit 0.9 rad/s;
its observed tyre grip reduced the lateral preference to 1.875 m/s². Sampled engine/brake
forces never exceeded the installed drivetrain/service-brake outputs. These are sampled
limits, not an exhaustive proof for all vehicle states. One configured stop completed,
the car finished in the correct lane at (10820.5, 9839.1171875), and native bodies returned
to zero. Warm hook p95 <=0.4 ms, p99 <=1.9 ms, max 2.033 ms; cold creation 22.547 ms.
Evidence: `artifacts/scenario-agent/native-faster-turn-20260926_223200/`.
The subsequent one-client run completed and the user confirmed **“Speed and turn feel good.”**
Peak speed was 24.105 km/h; six measured bend samples held 12.910–12.945 km/h with no applied
braking. It served the stop, used the correct lane, cleaned up its native body and generated
no new client errors. Sampled engine/brake output stayed within native available force.
Warm hook p95 <=0.3 ms, p99 <=0.4 ms, max 0.733 ms. Evidence:
`artifacts/scenario-agent/client-faster-turn-20260926_223439/`, same agent/epoch. Sustained
50 km/h travel and two-client agreement remain untested.

The repeated client-present obstacle trial detected the parked car and stopped after
2.66 tiles, roughly 17 tiles short of it, with zero new client errors. The user saw the
moving car briefly accelerate and disappear, rather than clearly observing it stationary.
This is **inconclusive visual braking evidence**, despite the recorded safety stop.
Evidence: `artifacts/scenario-agent/client-parked-fixed-20260926_223605/`. Its exact parked
fixture was removed through the stock game method and confirmed absent from the database;
the one-use original Lua file change was restored. To make the next visual test useful,
road probes now remain in `stopped_visible` for 20 seconds instead of three before cleanup.
The legacy straight probe retains its shorter timeout. The next obstacle position will
allow a longer visible approach; this harness change does not change the accepted controller.

No physical crash director, incident sound adapter or durable aftermath materializer was
enabled by these tests. Two-client consistency and integrated resident commutes remain pending.

## Bézier lanes, stop handling and traffic-incident foundations — 2026-09-26

Implemented bounded cubic trajectories with arc-length lookup, analytic heading/curvature,
signed deviation, velocity-scaled steering lookahead, longer speed preview and a one-second
bicycle prediction. Added a source-verified Muldraugh lane course: eastbound Y=9861.5,
northbound X=10820.5, 15 km/h cruise, and a two-second stop before the west-facing sign.
Its 192-tile oriented swept footprint passes both source and loaded-world validation.
The route is a reviewed artifact, not automatic map-wide lane/sign extraction.

Early native attempts stopped on the 1 ms cooperative road-scan budget:
`lane-stop-619eda16-3288-4131-a5d0-da69e8109fa2` and
`bezier-56a9b965-670d-4f41-8b69-8a9a5d90ebf0` under `artifacts/scenario-agent/`.
Deduplicated tile checks and replaced timeout failure with immediate braking until a new
complete scan succeeds; persistent starvation still fails. The scan budget was not raised.
Actual-body checks and the curved stopping corridor remain authoritative over static data.

Two corrected empty-server native runs completed on the pinned B42.20.4 build. The final
run includes the conservative moving-vehicle forecast adapter and uses agent SHA256
`2dcc881de532d4cd9d95739756efd644041213049ef1183ed8417117b99847c4`:

| Measurement | Final native run |
| --- | --- |
| Epoch | `0b40cd92-c5d2-4295-a10c-57ff6f2845b6` |
| Finish | `complete`, `route_arrived`, one configured stop completed |
| Maximum speed | 15.000 km/h |
| Final centre | (10820.5, 9839.171875), correct northbound lane |
| Largest sampled signed deviation magnitude | 0.3795 tiles; status sampled every 0.5 s |
| Warm hook samples / p95 / p99 / max | 400 / <=0.7 ms / <=3.0 ms / 4.254 ms |
| Cooperative scan-budget brake ticks | 10; acceleration resumed only after complete scans |
| Cold vehicle creation / cleanup | 30.579 ms / 8.593 ms |
| Native bodies after cleanup | 0 |

Private evidence: `artifacts/scenario-agent/bezier-0b40cd92-c5d2-4295-a10c-57ff6f2845b6/`.
The preceding successful run is `bezier-2bddac02-6508-4401-bdbe-a900228b9d9c/`.
These timings measure the added Java hook, not total native physics/server frame cost.
There were no clients or other moving cars in either run. Visible smoothness, moving-blocker
behavior and two-client agreement remain untested; ten brief brake interventions may be
visible and should be assessed in the next client trial.

Continuous swept-circle fixtures cover crossing blockers between samples, stale data and
invalid observations. The native adapter queries server-owned vehicle physics; unknown or
client-owned nearby motion stops the probe. It does not yet support normal lane passing,
moving pedestrian forecasts or general streamed traffic. Detached incident-admission tests
also cover every supplied player's reachable area, incomplete audiences and protected scenes.

Added the accepted [[Design/Traffic Incidents]] requirement: off-screen sound must have
discoverable aftermath at the same persistent location. The Lua model stores IDs, poses,
part conditions and fixed loot; accounts for reservations in the shared fleet; requires
a durable intent revision and saved-world receipt before sound; and retains interrupted
creation for reconciliation while suppressing uncertain audio replay. It is bound to the
scenario ModData restore path. **No durable checkpoint writer, native aftermath/sound adapter,
automatic crash scheduler or visible crash execution is enabled.** Table assignment and
mock receipts do not establish crash-safe game persistence. The first adapter must save
hidden wrecks before releasing sound. Native two-car contact/damage/audio is still pending.

Validation: **163 project tests**, Java compatibility/premain/protocol/asset fixtures,
analytic Bézier checks and nine detached stop-and-drive simulations passed. Evidence logs:
`artifacts/traffic-project-tests.log`, `artifacts/traffic-agent-tests.log`.
Lua tests exercise wrong/stale receipts, single-use dispatch, interrupted effects,
unchanged loot on restore, cooldown/capacity and shared fleet accounting. These are model
tests, not game-save restore or multiplayer tests.

Control responsibility remains split: C++ provides high-level planning/road routes; the
server Java agent executes the current probe's steering/braking through native physics;
Lua manages scenario/resident state. The probe does not consume the C++ worker's routes yet.
Only the disposable `.132:16281` probe was restarted. The playable `.132:16271` world and
production `.160` were not changed; the new Lua incident foundation is source-only.

## First client driver-model run — 2026-09-26

The single-client run was stopped after the user reported Error 13 and obscuring fog.
The earliest relevant client error was failure to open the vehicle script: Linux path
resolution requested `~/Zomboid/mods/home/akr/zomboid/mods/lofersdriverprobe/42/media/scripts/lofers_driver.txt`.
Subsequent vehicle-sound null-script and vehicle-update byte-count errors were recorded;
no successful visible driver replication is claimed. Private client log and server samples
are in `artifacts/scenario-agent/client-driver-20260926_205618/`.

Installed a narrow local directory symlink from that requested mod path to the original
`LofersDriverProbe` directory; no installed game files were changed. Client restart and
confirmation that the script loads remain required. The generic `stopweather` command
was insufficient to clear the reported fog. Added a disposable-world-only server Lua
fog override to the test package. After graceful restart, epoch
`31591e9e-25f0-4287-9a6b-18df379dcf95` logged actual fog intensity zero and the probe armed.
Fourteen targeted packaging/operations/visibility tests pass.

After the client rejoined, the next run completed with one client connected and no new
client log errors during the monitored course. Travelled 53.888 tiles; final position
(10818.5, 9839.1875), finish `route_arrived`. Evidence:
`artifacts/scenario-agent/client-driver-20260926_210235/`. Separate startup checksum
(`absPath:null`) and chunk CRC warnings remain recorded; no clean-startup claim is made.
The user confirmed a visible turn and cleared fog, but reported excessively slow driving,
a skipped stop sign and the wrong exit lane. Detailed seated-driver visibility is now low
priority by user choice. This is a partial one-client result, not traffic or two-client
acceptance; the next iteration addresses those specific failures.

## Commute foundations and original driver — 2026-09-26

- Added [[Design/Implementation Roadmap]] as the accepted continuation. Persistent
  schedule seed now has an explicit schema-1 to schema-2 migration; event RNG advances
  cannot shift residents' routines. Population accounting counts walking car owners as
  pedestrians and excludes parked cars from the moving-car budget.
- Both native planning threads share immutable road adjacency and spatial lookup. Graph
  content changes invalidate the cache even when a caller repeats its claimed identity.
  Expiring directed closures remain separate from baked terrain. Added Protobuf navigation
  identity, route node IDs, observed vehicle pose/entry and occupancy revision fields.
  Absent nested Protobuf messages now preserve absence through the Java codec.
- Planning uses the observed car position. A separate approach plan walks to its entry
  before the six-action commute. Unavailable cars cannot start driving; occupied cars can
  still plan parking/exit recovery. These are planner and protocol changes: the gameplay
  adapter does not yet publish verified car observations or execute integrated commutes.
- Created an original 204-triangle seated civilian and palette with a reproducible generator.
  The installed native Assimp importer accepted its normals/UVs/static geometry. Added an
  original vehicle extension referencing installed art and an asset-only disposable probe
  package. No game art, decompiled code, or replacement engine classes are distributed.
- Dedicated test world `LofersVehicleProbe_20260926_204822_20bc4e`, epoch
  `de51b9ac-a9c0-4800-8b0d-6ffbc23bcb7f`, completed the six-point asphalt course with
  `Base.LofersSmallCar` and its driver part enabled. Travelled 53.866 tiles; stopped at
  (10818.5, 9839.1953125), approximately 0.695 tiles from the goal. No probe error;
  native bodies returned to zero. Warm hook p95 <=0.4 ms, p99 <=0.7 ms; cold creation
  8.515 ms. These are hook timings, not whole engine/physics frame costs.
  Evidence: `artifacts/scenario-agent/driver-model-de51b9ac-a9c0-4800-8b0d-6ffbc23bcb7f/`.
  The first isolated attempt found an unsupported line comment in the vehicle script;
  fixed before this successful run. Existing upstream startup warnings remain.
- Validation: 122 project tests; native core and actual Unix IPC checks including closure
  expiry and route IDs; Java compatibility/premain/protocol fixtures and native asset import.
  Regenerated the private Muldraugh map index with its navigation identity. Worker load
  fixture (32 residents, 30 batches) measured p50 14.28 ms, p95 15.73 ms, maximum 23.11 ms;
  this is external round-trip latency, not added game-thread work.
- **Still pending:** client view of the driver (scale, position, visibility and replication),
  vehicle-profile connectors/turns, managed persistent vehicles, inventory-preserving
  boarding/exit, complete commute, human takeover and two-client validation. The original
  `LofersDriverProbe` assets are installed in the local client's mods directory for the
  prepared `.132:16281` test. No client Java agent is used. Normal vehicle execution remains
  disabled; playable prototype and production `.160` were not changed.

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

### 2026-09-27 — First pedestrian: user-observed partial success

`watched-walk-clear-1`, disposable world `AKR_DayOne_Test_20260927_141446_320776`:
one ordinary client saw the spawned actor standing idle and could push it. Actor
looked and sounded like a zombie, without zombie behavior. Server found a path but
route timed out; recorded deferred movement was zero. Recorded displacement cannot
be credited to walking because the player could push the actor. Cleanup reported no
remaining reservations. Spawn/visibility/contact passed this one-client observation;
autonomous movement and civilian presentation did not. Four-actor testing remains
gated; inspect locomotion separately from appearance/sound suppression before retry.

### 2026-09-27 — Pedestrian follow-up candidate (not native-validated)

Implemented bound-actor animation-variable delegation and original callback setup on
the dedicated server; removed duplicate manual Lua path stepping. Added incremental
remote client discovery, persistent callback capture, cosmetic-only body/clothes,
voice suppression and explicit presentation diagnostics. Exact engine and dependency
hashes remain guarded. Java unit/integration and launch fixtures pass; 31 focused
Python/Lua checks pass. Deployed to the same disposable world with a fresh epoch;
local companion updated. Actual walking, appearance and sound still need the next
one-client observation. See Current State for the artifact hash and evidence paths.

### 2026-09-27 — Scheduler exclusion identified after second failed walk

User observed quiet but naked/zombie-looking and idle: only voice suppression passed.
No movement over 30 seconds, even though client presentation callback ran. The actual
pinned `MovingObjectUpdateScheduler.startFrame` excludes all dedicated-server zombies
from both update and postupdate buckets; it only supports an optional GUI animation
path. Previous variable registration alone could not start their animation/state loop.
Private source/bytecode evidence: `artifacts/decompiled/pedestrian-scheduler`.

New candidate admits only adapter-bound live server-owned actors (maximum four) to
FULL simulation after stock bucket construction. Uses stock preupdate/frameStep/update/
postupdate; no global server mode toggle or synthetic locomotion. Scoped stock sight,
sound and wander entry guards prevent bound civilians from acquiring zombie goals;
WAIT stops movement and disables idle wandering. Native update/postupdate timings are
captured separately in actor path-state diagnostics (not whole-server measurements).

Appearance now rechecks actual skin and visual-item count at 500ms intervals and
repairs deferred outfit resets. It logs actual skin/clothes observations. The
initial appearance failure is not yet conclusively attributed to deferred dressing;
the next real observation must verify it. No replica health or gameplay inventory edits.
32 focused Python/Lua checks and full Java unit/integration/bytecode/premain checks
pass. Candidate JAR SHA256:
`4990f6b1be0cdae77128fecad44b8c4a7beac9b33978b0c3ac171b5042ab4d94`.
Native walking, collision and human appearance are still unvalidated.

### 2026-09-27 — Scheduler candidate: observed human, idle, cleanup blocked

One client confirmed a transient zombie flash followed by stable human presentation.
This is the first observed human-appearance pass; no walking. Actual position unchanged
for 30 seconds despite native update/postupdate running. Event timed out and remained
CLEANUP_BLOCKED: network ID cleared, server Lua actor registry/pending entries empty,
but Java reservation retained. Preserve that unresolved result and fix retirement
acknowledgment/absence verification before another test. Native per-method p99≤0.4ms,
max4.25ms; not a population performance result. Evidence and current epoch in Current State.

### 2026-09-27 — Native condition reproduced and targeted fix deployed

Native `CharacterVariableCondition.Factory` integration fixture reproduces the exact
isFalse(bPathfind) distinction: missing fails, explicit false passes, true fails.
`CanUsePathfindState()` previously refused the server and left the variable absent.
A scoped permission hook now lets stock pathToAux initialize it for bound NPCs only.
No synthetic motion or alternative transform channel. Cleanup now verifies actual
square/list memberships, waits for delayed removal, and remembers its acknowledgment;
retiring actors are excluded from simulation/replication. Lua registry acknowledgment
is idempotent while native absence remains independently required.

33 focused Python/Lua tests and full Java unit/integration/launch fixtures pass,
including the real engine condition regression. JAR:
`82b947f8155a5bb39d5d2bf34a52280964356b4500b77d328c252fb2247b0fbb`.
Deployed to existing disposable world, epoch
`6d9c5a22-e65e-43ed-884c-631561fb13a6`; adapter registered, no players online.
Actual autonomous walking and repeated successful cleanup still require live validation.
The user was asked to reconnect for diagnostics (no client restart needed).
Also corrected test port preflight to ignore closed RCON TIME_WAIT sockets while still
rejecting active listeners. Normal game and production deployments untouched.

### 2026-09-27 — First native walking completion; repeat cleanup passed

Epoch `6d9c5a22-e65e-43ed-884c-631561fb13a6`, events `.1` and `.2`, requests
`watched-walk-path-permission-1` and `watched-walk-path-permission-2`, both reached
COMPLETED / route_complete with no retained resources. Second submission succeeded
in the same process after first cleanup; no restart or authority fallback.
First status capture began after completion, so it establishes the terminal receipt
only. Second trace has 18 samples: server-owned actor moved from
(10756.5166,9856.7998) to (10775.7344,9856.5078), 19.22 tiles, entered native
walktoward with nonzero deferred movement, then WAIT/idle with zero movement before
retirement. This is the first measured server locomotion pass.

Second actor's native update/postupdate measured maximum 0.203006ms. Runtime-wide
work maximum was 98.722194ms, p99 2.8ms at completion; this includes earlier work and
is not a population performance acceptance result. First-use spawn/work latency
requires attribution before scaling. Client logs show both replicas with human skin
and three clothing visuals, two initial repairs each, and no new ERROR/exception
entries during these runs (existing startup errors and unrelated object warnings
remain). Human visual confirmation of this walking revision is still pending.
Do not describe smooth client walking or multiplayer validation as passed yet.

Evidence in `artifacts/scenario-tests/20260927-141446-320776/`:
`watched-walk-path-permission-1.jsonl`, `watched-walk-path-permission-2.jsonl`,
`watched-walk-path-permission-2-submit.json`,
`watched-walk-path-permission-server.log`, `watched-walk-path-permission-client.log`.
Next: collect user observation, tune any presentation/speed issue, then four-actor
feasibility test. Two actual clients remain required for multiplayer acceptance.

### 2026-09-27 — Walking observed; presentation failed; Opus handoff

User confirmed walking with human appearance/clothing, but zombie gait, position skips,
and a brief zombie spawn presentation that triggers jumpscare sound and an anxiety-like
moodle. Thus native locomotion/lifecycle passed; smooth civilian presentation and fear
classification failed. No four-actor advancement. No fixes for this latest feedback
were deployed before the user requested delegation to Opus.

Full task, deployment, evidence, constraints and next gates are recorded in
[TASK_Opus_NPC_Presentation_Replication.md](../../TASK_Opus_NPC_Presentation_Replication.md).
New private inspection identifies stock WalkType.fromString("Walk") falling back to WT1;
remote native packets overwrite the client walk type. IsoPlayer.updateLOS counts
IsoZombie instances without a Bandit exclusion and triggers fear/sound itself. These
are concrete source findings; resolving visible jitter and civilian fear under ordinary
Lua clients still requires implementation/validation. Read the handoff before changes.

At handoff the server remains running, epoch6d9c5a22-e65e-43ed-884c-631561fb13a6,
one player akr connected, no new event submitted and no server/client modifications.

### 2026-09-27 — Civilians must be zombie targets; disguised-zombie path retired

Read-only investigation only; no code, deployment or live event. The server stayed on
epoch `6d9c5a22-e65e-43ed-884c-631561fb13a6` with `akr` connected. The user required that
zombies natively target healthy NPCs, so gait and dressing fixes for disguised `IsoZombie`
civilians were stopped before implementation.

Source findings (private artifacts `artifacts/decompiled/pedestrian-gait-fear/` and
`npc-player-feasibility/`): zombies acquire targets only through a player's LOS pass
(`TestZombieSpotPlayer`, server `ServerLOS`, `ZombieControlPacket` carries a `PlayerID`).
`AttackState` casts the victim to `IsoPlayer`. Clients report bites only for their local
player. Fear counting and the jumpscare are type-based inside `IsoPlayer.updateLOS`, and the
visibility gate is not Lua-settable. The observed skips have a concrete mechanism: `Walk`
cannot be encoded in the stock walk-type enum, so the client ran a slower zombie gait
(about 19 tiles in about 5 s on the server) and hit the 3-tile teleport threshold. That was
not measured on the client. Remote `speedMod` parse lacks `/1000` in stock bytecode
(unchanged).

Proposed replacement: server-hosted connectionless `IsoPlayer` civilians. Server-owned,
server-simulated zombies engage them, and a server path follower drives their locomotion.
Four spike gates are defined. See [[Decisions/0006 Targetable Civilians]]. The existing
server-owned zombie walker remains validated evidence for bounded server zombie simulation.
Nothing about the new entity is validated yet.

### 2026-09-27 — Actor-pool design accepted; gate 1 started

The user accepted the design: civilian state stays off-engine, and a bounded pool of
connectionless server `IsoPlayer` Actors replicates it through stock player packets.
Integration stays inside the server Java agent plus original Lua; no core file replacement.
A client ignores a repeated player-connected packet for a known ID, so reassigning an Actor
to another resident needs stock removal plus re-announce. Details in
[[Decisions/0006 Targetable Civilians]]. Nothing is validated yet.

### 2026-09-27 — Actor pool v0 implemented and deployed (not yet observed)

New `ServerActors` runtime backend (agent JAR SHA256
`240345bf3c8cf4a3f1e529f9e4f59268df690cd10211e0e5e934e81bfffa53cd`):

- One connectionless server `IsoPlayer` per event, reserved online ID 4096 (real IDs are
  `slot*4+index` ≤ 1019). It is registered only in `GameServer.IDToPlayerMap`, not in
  `Players` or any connection.
- It is dressed from a stock outfit converted into real worn items, and placed in loaded
  free squares only.
- It is announced with stock `sendPlayerConnected`. Stock `PlayerUpdateReliable` packets go
  out at 4 Hz to relevant connections, and the network-staleness timer is kept fresh.
- It is reassigned once to another identity on the same slot (stock `PlayerTimeout`, a 1 s
  quiet gap, then a re-announce). Release verifies removal from the world and ID maps.
- Stock `IsoPlayer.update/postupdate` timing is sampled for Actors only; an unmatched update
  (the stock update threw) fails the event.

`PedestrianCase` gained compatible fields `entity` (default `DISGUISED_ZOMBIE`) and
`reassign_after_seconds`; the zombie walker path is unchanged. The session routes each event
to its adapter. The Actor limit is one per event at gate 1.

Validation: 33 focused Python tests and the full Java unit/integration/premain suite pass,
including actor routing, limits, capacity and release-requires-network-absence, and JVM
verification of the new `IsoPlayer` hooks against the pinned jar (log
`artifacts/actor-gate1-build.log`). Deployed to the disposable world by a graceful restart
with no players online. The pre-restart log is kept as
`artifacts/scenario-tests/20260927-141446-320776/server-console-before-actor-gate1.txt`.
In-game behaviour is not yet observed.

### 2026-09-27 — Actor pool v0: gate 1 server/replication pass, one client

Disposable world `AKR_DayOne_Test_20260927_141446_320776`. Evidence lives in
`artifacts/scenario-tests/20260927-141446-320776/` (`actor-*.jsonl`, saved server/client
consoles).

- **Refusals.** With no player online the Actor refused with `spawn_square_unavailable` and
  retained nothing, so it never loads chunks.
- **First watched run failed closed** (`actor_left_world`). Two stock behaviours: every frame
  the server purges any `IsoPlayer` not in `GameServer.Players` from the cell object list,
  and the character constructor queues itself on that list.
- **Fix: the Actor is a puppet.** It is removed from the cell object/add lists and the update
  scheduler at creation, and placed only on its square's moving objects, as the stock
  remote-player update would place it. A stock `IsoPlayer.update` running for an Actor now
  fails the event.
- **Saves.** The server player DB saves connection players only; `players.db` holds only
  `akr` rows after saves with Actors present.
- **Final agent** `0312d8592d9a1dd9fb815f8184f947cc2511e1f7b0b635a094028c3fa887ed53`, epoch
  `54d9ed5e-3444-46ac-987a-af268bdc5c1a`. Events `.1` and `.2` both COMPLETED in the same
  process, with no restart:
  - Slot 4096 announced once to the client, reassigned at 20 s to a new identity (stock
    `PlayerTimeout`, then a re-announce after 1 s), then released at 40 s.
  - 131 `PlayerUpdateReliable` packets per event; stock updates of the Actor: 0.
  - No server purge line and no new server or client errors (client ERROR lines are all
    startup lines before connect).
  - Identities: Barrett Layton (Student, 3 worn) → Anastasia Rowan (Young, 4 worn);
    Patti Schneider (Young, 6) → Harry McClendon (Classy, 6).
  - Materialization cost in one server frame is 1.7–7.6 ms (first/cold highest). Runtime
    work p99 is 0.3–0.7 ms and max 7.8 ms, including creation. Budget one materialization per
    tick, off-screen, before scaling.
- **Visuals.** Game-window screenshots at 3 s and 24 s show a clothed human with its own
  gait-free stance, name tag `akr-actor-4096`. The reassigned identity shows a visibly
  different person; at 45 s the Actor is gone. No moodle is visible in those frames.
- **Not yet verified.** The jumpscare sound is audio and was not verified by the agent; the
  user's observation is pending. Only one ordinary client; not multiplayer-validated.
  Standing only (walking is gate 2). Zombies do not engage Actors yet (gate 3). The name tag
  shows the username, not the display name.

Operator tooling: `./dayone pedestrian-test-join` relaunches the local Steam client into the
disposable world. It needs the developer-only original `AKRDevConnect` client mod, which
reuses the stock favorites connect with the account already saved in `ServerListSteam.db`;
the stock `+connect` path waits on a Steam query and cannot match the saved account. The
command sends one synthetic click through xdotool to pass the stock "Click to Start" screen,
which accepts only real input. Install receipt: `artifacts/scenario-agent/dev-connect-install.json`
(main-menu `default.txt` backed up alongside).

### 2026-09-27 — Actor pool gate 2: walking passes with one client

Agent `cd7a5ae7bc21692767f7c0c118c0702e407f92ed8217eb1b669bc72a7ea09ec0`, epoch
`640c9ef4-44f4-44bd-9257-7c78f7c2aa96`. The server path follower walks straight route
segments between tile centres. Each segment is refused up front if stock
`PolygonalMap2.lineClearCollide` reports a collision. Replication uses stock
`PlayerUpdateUnreliable` type-1 predictions at 10 Hz (lookahead capped at the segment end),
plus reliable packets on start, turn and stop and every 1 s, with the stock moving flag and
never the running flags. New compatible field: `PedestrianCase.walk_tiles_per_second`
(0 = stand).

New client diagnostic `AKRDevTools/ActorSamples.lua` logs:

- the rendered Actor position and action state at 4 Hz (wall clock, same host as the server);
- frame-to-frame jumps over 1 tile;
- the local player's visible/very-close zombie counts, panic and stress at 1 Hz.

Server samples carry `wallMs`. Alignment and evidence:
`actor-gate2-*.jsonl` and `client-debuglog-gate2-*.txt` under the artifact root.

- **Calibration** (event `.1`, 4 tiles/s): the client fell behind at a steady ~1.6 tiles/s.
  That is the stock remote catch-up ceiling (1.1×) on its walk animation, so the natural
  walk is about 1.45 tiles/s. The error grew to 7.0 tiles, and the stock remote-player
  correction teleported the Actor once. The server speed must stay inside the client's
  0.9–1.1× band.
- **Nominal walk** (event `.4`, 1.45 tiles/s, 26-tile U route with two turns and a stop):
  - 88 aligned samples; difference p50 0.11, p95 0.35, max 0.52 tiles.
  - 0 skips; human `movement` action state throughout; `idle` 0.09 tiles from the server
    stop point.
  - 126 packets; stock updates of the Actor: 0.
  - Server runtime work p95 0.2 ms and p99 2.3 ms; materialization 2.1 ms (10.3 ms cold
    after restart, the session maximum of 17.9 ms).
- **Fear inputs:** all samples while Actors were present and for 10 s after show
  visibleZombies=0, veryClose=0, panic 0, stress 0. The stock jumpscare needs
  `numVisibleZombies>0`, so Actors cannot trigger it.
- Event `.2`/`.3` failures (`spawn_square_unavailable`) happened while the client had quit
  normally (clean `GameThread exited`, no crash). They confirm fail-closed refusal with no
  retained resources.

Limits: one ordinary client on LAN. The 1.45 tiles/s value is empirical for the stock healthy
walk animation of this build. There is no pathfinding around obstacles and no dynamic
avoidance of players, zombies or vehicles; blocked routes are refused, not rerouted. Zombies
do not engage Actors yet (gate 3). Two real clients are still required for multiplayer
validation.

### 2026-09-27 — Actor pool gate 3: perception and chase pass; server attack fidelity blocks damage

Disposable world, one ordinary client (`akr`), admin + `invisibleplayer` (which also sets
ghost mode, the flag stock spotting and attack checks exclude) + god mode, so the hunter
could only engage the Actor. Evidence: `actor-gate3-hunter-*.jsonl` and the saved
consoles under the artifact root. Final agent `702474f431dc453ae135d2c45f3cdd3ff31358cd5bc2f20bd8e88f41b4dd6e97`.

Implemented:

- `hunter_zombies` (0/1) definition field.
- Stock `createRealZombieAlways` under a scoped Java spawn permit.
- Hunter bindings reuse the server-simulation hooks (native ownership, FULL scheduler bucket,
  stock stream, variable and path permission) without the civilian goal gating.
- Stock `spotted(actor)` is called per tick.
- The first wound disengages the hunter; stock removal is verified.

Findings (source plus runs):

1. **Stock server spotting ignores server-owned zombies.** `NetworkZombieComponent.isRemote()`
   returns `authOwner == null`, so a server-owned zombie is "remote" on the server.
   `NetworkZombieManager.canSpotted` then refuses every sighting, and aggro is not recorded.
   Run 1: 1,200 `spotted` calls, never targeted. A scoped hook makes `isRemoteZombie()` false
   for hunters only, on the server; the server-side `!isRemoteZombie()` branches (aggro,
   tripping, traps) are exactly the server-simulator semantics. After the hook, the hunter
   targeted the Actor within 0.3 s and walked, lunged and entered `AttackState`
   (runs 2–5).
2. **The puppet had no body.** Its `nextX/nextY` stayed at the constructor tile corner, and
   it never ran the stock `separate()`. The zombie walked into the Actor (d=0.00); stock
   `LungeState` then threw `Forward Direction cannot be zero length vector`. Fixed with
   `setForceX/Y` (position, next and last together) and the Actor's own stock `separate()`
   each tick, with the engine-resolved push applied and replicated. Contact now holds at
   0.3–0.55 tiles.
3. **Attacks abort (open blocker).** Every `AttackState` exits within about 200 ms of the
   `start` outcome. The zombie then sits in `face-target` for 10–14 s: `isFacingTarget`
   uses the animation player's angle (`getLookAngleRadians`), which does not turn toward
   `faceThisObject` on the dedicated server. So `SetAttackOutcome` and
   `AttackCollisionCheck` never fire and no damage is rolled; health stayed 100 with 0
   wounds.
4. **Client view.** The client replica plays the network attack animation and blood, so the
   user saw full attacks while the server applied nothing. The user also saw the replica
   walk about 1 m, snap about 3 m and then reach the Actor. The server-simulated zombie
   moves faster than the client replica (the earlier walker measured about 3.8 tiles/s on
   the server), which hits the stock 3-tile remote-zombie teleport.
5. **Late placement failures** (`actor_unplaced`) happened with a real player 2.6 tiles
   away. The cause is not yet explained; the event fails closed.

Fear counts, damage replication to observers (hit reaction, wounds) and the ownership
handoff to a client were not exercised. Next: server animation-player angle and time-step
fidelity for server-simulated zombies, so facing, attack outcome and walk speed match
clients. Then gate 3 damage; then author the victim-side stock packets so observers see the
Actor's hit reaction. The user proposed an off-engine resident table (pool/ECS-like) that
keeps stats across Actor swaps; that matches Decision 0006 and is planned after gate 3.

### 2026-09-27 — Gate 3: a stock server zombie bites a fleeing, tripped Actor

Agent `bb5d5e38fe5241dfe9dc4722da953bb1b888d598df84884a53a43e02381a60f6`, epoch
`a385d259-fc69-4df4-838f-cc88e4a1a520`, event `.1`. Definition
`one-actor-flee-trip-sprinter.json`; capture `actor-gate3-sprint-3.jsonl` plus the saved
client and server logs under the artifact root. One ordinary client (`akr`): admin,
invisible, ghost and god mode.

Result: COMPLETED with verified cleanup.

- The sprinter hunter targeted the Actor at 0.4 s.
- The Actor ran away (baked run, stock running flag) and tripped at 3.4 s with the stock
  `BumpedState` stumble, sent to the client with stock `StatePacket` enter and exit.
- The hunter lunged and attacked: stock `AttackState` outcome `start` then `success`.
- Stock `triggerPlayerReaction` → `AddRandomDamageFromZombie` ran on the server: health
  100 → 99.05, one scratched part.
- The server relayed the stock `ZombieHitPlayer` and `PlayerInjuries` packets to the client
  for the Actor's hit reaction. The hunter then disengaged.

Server-simulation fidelity fixes, all scoped to hunters (server-simulated zombies engaging
Actors):

1. `isRemoteZombie()` returns false on the server, so stock `canSpotted` and aggro accept
   sightings (the stock server treats unowned zombies as remote).
2. Stock `spotted(actor)` is called every tick within 20 tiles, like the stock server LOS
   pass. Calling it only while untargeted left `vectorToTarget` stale, so `bAttack` fired late
   and the zombie walked onto the Actor.
3. `AnimationPlayer.DoAngles` clears the deferred-rotation weight for hunters. The dedicated
   server's non-visual animation update computes root translation but never root rotation,
   so turn-in-place animations froze the facing angle (measured: weight 1.00, delta 0.0000
   while the angle stayed at −2.735 with the target at +0.45). The stock procedural rotation
   branch now turns them.
4. `IsoZombie.isTargetVisible` for hunters targeting an Actor uses the stock
   `LosUtil.lineClear` within 20 tiles. Stock server visibility asks `ServerLOS` for the
   target player's LOS, which a connectionless Actor does not have, so every attack exited to
   idle (`bCanSeeTarget` false).
5. Deterministic stock speed setup via the `hunter_speed` field (`doSprinter` etc.) instead of
   the sandbox's random pick, per the user.

The Actor puppet also gained stock body contact: `setForceX/Y` positions and its own stock
`separate()` each tick, except while floored.

Known issues:

- A stock `ZombieSound` packet sent by the server for the server-simulated hunter
  underflowed on the client (`GameClient.receiveZombieSound`). This is a stock server path
  for server-owned zombies; it is recorded, not yet fixed.
- The trip is a stumble without a fall: stock attacks never damage a floored victim (they
  eat it; seen in an earlier run as 95 s of `eatbody` on the living Actor).
- The late `actor_unplaced` failures coincided with the user shoving the Actor or zombie; not
  yet explained.
- The observer's hit reaction and wound display were not visually verified by the agent.
- Handing the hunter back to a client when it retargets a real player is not implemented.
- Two clients have not been exercised.

### 2026-09-27 — Trip and bite reaction render on the client; zombie teleport measured

Agent `442d6f9b46631ed1e7060f2f6a8d50bd722b360422fdff148e88ce19364e1d3e`, epoch
`21301e6a-d375-4266-b91b-1274673d722a`, `one-actor-flee-trip-fast.json` (fast shambler,
flee 2.6 tiles/s, trip at 3 s). Evidence: `actor-gate3-fast-2.jsonl` and
`client-debuglog-gate3-fast2.txt`. COMPLETED with verified cleanup; server bite
health 100 → 93.24, one wound.

- **Trip fix.** The puppet is `remote`, so stock `BumpedState.setParams` took its remote
  branch, read the (empty) bump from the params and sent an empty bump type. The client's
  `bumped` variable stayed false, so earlier runs showed no trip (as the user observed).
  Writing the stock params (`bump_type=left`, `bump_fall=true`, `bump_fall_type=pushedFront`)
  before the stock `StatePacket`, and sending the stock exit on recovery, made the client
  render `run → bumped → getup → movement`.
- **Hit reaction.** After the server bite, the stock `ZombieHitPlayer` relay made the client
  render `hitreaction-bite`.
- **Zombie teleport (unresolved, now measured with client replica sampling).**
  - The client replica walked at about 1.8 tiles/s while the server zombie moved at about
    2.0–2.4 tiles/s, so the gap grew to about 1.9 tiles.
  - When the server zombie entered `lunge`, the replica stayed in `walktoward-network` and
    nearly stopped (0.74, then 0.15 tiles/s); the gap reached 2.55 tiles.
  - The replica then snapped 3.6 tiles in one frame to exactly (10778.00, 9856.00). That is
    below the stock 3-tile parse correction, so the snap is inside the client's network
    walk/lunge handling of a server-owned zombie.
  - Next: time the server animation clock against wall time for hunters (speed ratio), and
    trace the snap in the client's WalkTowardNetworkState/lunge prediction path.

### 2026-09-27 — Gate 3 passes with client-owned hunters: no teleports, trip, bite, reaction

User decision: zombies engaging Actors use stock ownership (the nearest real client runs their
AI), since NPCs only matter in LOS and in prefetched border areas. The Actor stays
server-owned.

Agent `4d53bb8b96401008d6519da3ae95685a3e94fffafb384bab6fca1d36ab429253`, epoch
`3c07bbb6-1905-45f3-b2e7-1391f93adf56`, event `.2`, `one-actor-flee-trip-fast.json`.
Evidence: `actor-gate3-owned-2.jsonl` and `client-debuglog-gate3-owned2.txt`. COMPLETED
with verified cleanup.

- **Ownership.** The hunter is spawned with stock `createRealZombieAlways` (scoped Java
  permit) and stock speed setup, with no server-simulation binding; stock `updateAuth`
  gave it to the user's client.
- **Targeting.** The server sends the owner the stock `ZombieControl` (target = Actor),
  repeated each second while engaged. `ZombieControl` only sets the target, so the owner's
  zombie idled (run 1). The new client file `AKRDevTools/HunterPerception.lua` runs stock
  `zombie:spotted(actor,false)` at 4 Hz for zombies the client owns whose target is an Actor.
  That is the owner-side LOS pass stock runs only for the local player (bounded: 4 Actors,
  8 zombies).
- **Bite.** The server copy mirrors the owner's replicated target and `realState`. On each
  entry into `attack` against the Actor (≤1.2 tiles, clear stock `LosUtil` line), the server
  applies the stock `AddRandomDamageFromZombie`, which is what stock `Bite.process` applies
  for an owner-reported hit (clients only report hits on their own player). It then relays
  the stock `ZombieHitPlayer` and `PlayerInjuries` packets.
- **Owner client timeline.** Zombie idle → walktoward → lunge → attack; **0 zombie and
  0 Actor position skips**. Actor run → `bumped` (trip) → getup → movement → idle →
  `hitreaction-bite`. Server bite: health 100 → 99.64, one wound. No new client errors.

The earlier server-simulated hunter hooks (`isRemoteZombie`, deferred-rotation fallback,
`isTargetVisible`) are now unused on this path, because hunters are not bound. They remain in
source, inert, pending a keep-or-remove decision.

Known issues:

- `actor_unplaced` is a chunk unload after the last nearby player leaves. It should become
  a graceful dematerialize, not a failure.
- The disengage after the first bite only stops server damage rolls; the owner's zombie may
  keep attacking visually until cleanup.
- Only one client has been exercised, and it was the owner. A second observer client, and
  ownership changes mid-chase, are untested.

### 2026-09-27 — Gate 3 reopened: attack stall, 4 s owner cadence, owner-only slow factor

The user watched two more runs and reported failures, so gate 3 is reopened. Full detail is in
the root handoff `TASK_Opus_NPC_Presentation_Replication.md` (sections 5, 6 and 8).

- **Run `actor-gate3-owned-3`** (sprinter confirmed). The zombie reached the stumbling Actor,
  then stood in `attack` for ~13 s.
  - Cause: stock `Zombie_Bite_Start` needs `targetSeenTime > 0.5`, and stock `spotted()` adds
    one frame per call; the 4 Hz Lua spotting was about 16× too slow.
  - The server bite missed its 1.2-tile entry check (copy distance 1.26).
- **Run `actor-long-chase-1`** (8 s warm-up, stagger trip, per-frame spotting). The attack
  loop worked, but:
  - the owner reported the zombie only every 4 s (stock `hasNeighborPlayer=false` gives 4000 ms);
  - the sprinter's stock slow factor was applied only to the owner's copy of the running
    Actor, splitting positions up to ~5 tiles;
  - the server check read the unset copy `realx` field and made 0 bites;
  - the client closed normally, so the run ended `actor_unplaced`.
- **Fixes, built and deployed but unobserved:**
  - per-frame owner spotting;
  - bite judged over the attack window on the copy position (reach 1.5, delay 350 ms);
  - trip at hunter gap ≤ 3 tiles;
  - default stock forward-stagger trip (`trip_fall` keeps the fall);
  - `chase_delay_seconds`;
  - a scoped `NetworkZombiePacker.send` neighbor hook for the hunter owner (200 ms cadence).
- Deployment: epoch `35d43721-87b2-4000-beb6-821b56e91eb3`, agent `3338398134db…`.


## 2026-09-27 — Offline civilian pool, navigation and behavior foundation

User direction: abstract tests first; batch native functional/visual checks later. Baseline
idle/roam, full native traversal retained as the target. No server/client operations or deployment.

Implemented original source:
- Four-slot `CivilianPool` with exact-reference ownership, construction/retirement failure
  retention, generations/revisions, immutable checked body envelopes and conservative corpse handling.
- `AKRPopulation` and `AKRResidents`: plain persisted identity/ownership records, restart
  reconciliation, Lua 5.1 idle/walk/flee/recover/blocked FSM and controller with injected ports.
- Layered deterministic A*, fair four-job work queue, incremental local capture, bounded
  escape destination attempts and native-route safety checks; no straight-line failure fallback.
- Native asynchronous path adapter with synchronous pooled-result copying, request admission,
  cooldown/timeout and stale-result rejection. Native/Java backend selection follows the engine.
- Paged perception cursor, loaded-square LOS adapter, action sequencer/current snapshot and
  compiled native Actor/stock-body-codec/geometry/level-walk/door adapters.
- Legacy Actor cleanup tracks bodies before fallible setup/removal, holds corpses and changed
  IDs, checks doors and current route clearance; runtime timing includes sample production.
- Guarded IsoPlayer/AnimationPlayer/path contracts; actual Actor sentinel hook-site assertions.

Validation:
- `./dayone runtime-test unit`: 237 Python tests (13 new civilian tests), Java fixtures,
  2,000 deterministic pool reuse cycles and accepted vehicle regressions passed.
  Evidence: `artifacts/runtime-tests/20260927-204454-8de22b1b/report.md`.
- `./dayone runtime-test integration`: passed pinned-build/JVM/hook-site checks, real native
  pooled Path copying, original driver import, premain and launch-discovery fixtures.
  Evidence: `artifacts/runtime-tests/20260927-204517-ec601c1b/report.md`.
- Built, not deployed: `a2cded325e185840b19ac675938e77b9ea180a04567e249f190a7c2ea25cfb4a`.

Native limitations are explicit in [[../Design/Civilian Pool and Navigation]]. Full stair/
window/fence/wall state execution is not implemented or validated: the connectionless Actor
skips stock updates and high-wall outcome code assumes a local player. Native body round-trip,
live multi-Actor session-driver wiring, four-Actor and two-client checks remain pending.
The new modules register APIs only; the old socket experiment stays unchanged. Gate 3 combat
remains reopened; no abstract result changes its status. Prepared the later batch in
[[../Runbooks/Civilian Qualification Batch]].

## 2026-09-28 — Client rollback and watched native melee contact

- Steam public update changed the local client to 42.21 just before the test.
  Restored the user's exact prior Linux depot 108603 / 6267392422221692966
  (42.20.4, build 24909800) into the project. Game JAR SHA matches the pinned server.
- Added a hash-guarded ordinary-client launcher and application menu entry. Watched
  joins use this archived client through the installed Steam Linux runtime.
  The Steam-managed latest client remains separate; saves/mods were preserved.
- Fixed stale readiness reports surviving test-server restarts.
- Added opt-in native-contact relay and a one-hammer/one-zombie watched harness:
  admin/god/invisible/ghost observer, clear noon, teleport, chat countdown, stock
  PlayerHitZombie message, client observations and off-screen owned cleanup.
- Guarded transformer/engine fixtures passed; 24 Lua/tooling tests passed.
- Ordinary client joined and automated contact/cleanup completed. Both swing and
  reaction were reported by the client. The user saw no swing, only stance and zombie reaction: presentation failed;
  timed autonomous combat and multiplayer comparison remain separate gates.

- Repeat after stock attack-type/combat-speed initialization: user confirmed one
  visible swing. Contact and cleanup passed. A new Lua track sampler failed because
  AnimationPlayer's track API is not exposed, causing error-counter spam. Removed
  it; supported `AttackAnim` flag is explicitly not a visual claim. Clean repeat
  follows with no client Java changes.

- Clean repeat epoch `57c77582-8d81-4062-81b3-a50376f6540c` completed one native
  contact and cleanup with zero new client errors across 166 Actor samples. Health
  0.79 -> 0.6692302; scoped work p95 <=0.1 ms. Coarse attack flag true, reaction
  flag false; do not substitute these for visual/sound confirmation. The user
  confirmed the swing in the prior run. Latest artifacts remain under
  `artifacts/scenario-tests/20260928-152217-49a655/`.

- Final audio/loadout repeat: user confirmed correct animations, audible swing
  and impact, then “Success!”. Both native sound requests returned nonzero client
  handles. Loaded pistol (15+1), two spare magazines and ammo box confirmed on both
  sides. Zero new client errors during the observed test; 25 Lua/tooling tests pass.
- User exited during the final hold, before cleanup. Automated report records
  observer_disconnected, preserved separately from human acceptance. Empty server
  stopped gracefully and world retained. Do not describe this last run as cleanup
  qualified; the preceding run separately completed cleanup. Next test needs a
  fresh disposable world. Autonomous combat and two-client gates remain open.

## 2026-09-28 — Watched autonomous defense and escape accepted

- Launched the pinned ordinary client as akr; automatic protected placement,
  clear daylight, loaded 9mm loadout and announcements ran.
- World `AKR_DayOne_Test_20260928_163023_07ce36`, epoch
  `54689636-7cb3-43b0-95a0-f9091fa0da72`: one native defense contact,
  escape over seven tiles, 20-second viewing hold and verified cleanup completed.
- User confirmed “Test succeeded”. Human acceptance is recorded separately from
  the unchanged `passed_visual_pending` machine report in
  `artifacts/scenario-tests/20260928-163023-07ce36/survival-human-acceptance.json`.
  Client console is archived in the same directory.
- This is a stationary-threat locked-door fixture, not moving pursuit or incoming
  bite qualification. Two-client and integrated combat-to-death gates remain open.
- Post-completion observer relocation loaded MOWoodenWalFrame errors about missing
  IsoThumpable objects on distant floor-one squares. No global clean-log claim.

## 2026-09-28 — Reusable encounter runtime and testing skills

- Added opt-in typed encounter backend using existing scheduler/socket; additive request
  and progress fields preserve pedestrian contracts. Four initialized Actor bodies persist
  between cases. Legacy actor submissions are rejected in this exclusive encounter session.
- Added open escape, incoming injury and one/four defense cases, real generated collision
  fixtures, exact resource ownership, bounded deadlines and cleanup-gated repeats.
- Ordinary-client reports now carry event/generation identities; stationary target freezing
  is separated from pursuit. Native injury interrupts action/movement. Per-component timings,
  source hashes, log boundaries, separate human feedback and failed-case reconciliation
  support the operator workflow.
- Added and validated project skills dayone-watched-testing and dayone-test-iteration,
  their runbook and skill index; corrected stale combat guidance.
- 260 project tests and guarded Java suites passed. Native checkpoint
  20260928-170403-f714ac passed thirteen stationary encounters and native lifecycle/pooling;
  survival p95 3.4 ms / p99 6.5 ms remains above the release target.
- Prepared fresh disposable encounter world 20260928-170922-c7b03e. Host protobuf mismatch
  was fixed by routing combat commands through the project venv. Watched qualification
  is pending; existing production deployments are untouched.


## 2026-09-28 — Resident plans, continuous movement and owned reactions

Implemented the narrow worker routine and durable AKRResidents goal cursor; generation/fact
checks prevent stale plans replacing committed work. WALK/RUN uses native modifiers and
an owned native endurance call. Reaction pause retains navigation; only a matching owned
nonterminal native reaction is completed. Geometry is cached separately from threat costs,
candidate routes join at the Actor's actual tile, and movement carries frame time across
bounded edges. Perception now finishes a rolling sweep instead of restarting on every
short movement. Added bounded server traces and ordinary-client gait/reaction observations.

First native acceptance run `artifacts/civilian-headless/20260928-181718-f4d7e9/` passed
four worker routines and native run/reaction/retirement checks with four initialized bodies.
Work p95/p99 2.1/5.8 ms, not a release performance pass. Its generic top-level pool counts
were zero because the new fixture owned a separate pool; fixed reporting uses that pool's
actual counters in subsequent runs. The longer eight-assignment run is recorded separately.

Failed preliminary runs exposed an IPC permission failure across host/container security
contexts; the original bundled worker now runs inside the disposable game's container,
without changing SELinux or adding public ports. Other fixes: incompatible arrival
thresholds, completed old-route receipts applied to a newer candidate, and one-step paths
that did not recenter an off-center Actor. Regression coverage was added for each pure
controller behavior. Exact native reaction animation timing, narrow-door navigation,
incoming pursuit and two-client agreement remain live gates. No production deployment changed.


### Eight native assignments passed

`artifacts/civilian-headless/20260928-182359-85ed5b/ipc/native-report.json`:
eight accepted worker routines, eight RUN/reaction checks, four constructed bodies,
eight assignments reported by the pool reuse counter, four parked and zero occupied.
End-to-end work p95/p99 1.7/2.9 ms; final measured RUN 1.60 tiles/second with native
modifiers (requested baseline 2.6). Sequential functional evidence only. Server stopped
gracefully. Latest watched-only additions export client observations and reject shooter
interference during the diagnostic gait case too; native movement source is unchanged.


### Survival regression passed; performance remains blocked

`artifacts/civilian-headless/20260928-182814-e612da/ipc/native-report.json` passed all
25 checks, including thirteen survival encounters and native contacts. Survival work
p95 6.0 ms / p99 25.218667 ms exceeds release targets; no crowd expansion is qualified.
Worker routine measurements must not be substituted for survival measurements. The
fixture stopped gracefully. Latest Lua additionally pauses routine wait time while
offline; its dedicated regression passes (27 focused tests total).


### Watched iteration and pending-reply recovery

First watched batch `20260928-183432-eb3551c3` on world
`AKR_DayOne_Test_20260928_182917_2fa6d3`: LOCOMOTION completed and cleaned; OPEN_ESCAPE
failed its active timeout because the owned hunter travelled zero tiles. The civilian
moved 103.27 tiles, alternated routine/escape behavior, and retained 100 health. This is
not pursuit acceptance. Cleanup verified four parked bodies and no retained resources.

Review also found the adapter consumed path replies while Lua returned early for a hit
reaction or incomplete observation. Replies now remain pending until the model can consume
them. Native run `20260928-184215-387006` passed eight worker routine/run/reaction assignments
on four bodies, including reaction injection while planning; p95/p99 0.7/1.9 ms, zero occupied.

Added spectator facing during POSITIONING. A subsequent diagnostic attempted to index
NetworkPlayerAI from Lua; that Java return object is opaque to Kahlua, so the diagnostic
failed before countdown. Removed that unsupported access; the test stub now deliberately
rejects it. This failure is diagnostic-only, not a pursuit result. The disposable copy
helper now remaps matching networkPlayers.world rows, fixing repeated character creation;
source preservation and unrelated-world rows are regression-tested. All 268 component tests
pass. Latest watched world and outcome are tracked in Current State.


### User feedback: walking animation, mini-teleports, no diagonals

User observed only the first two cases and accepted basic navigation/flee functionality,
but rejected running presentation/smoothness and four-direction path shape. Verbatim
feedback is archived under watched batch `20260928-185010-9b395844`. No later case was run
or visually accepted. In this batch, RUN server samples disagreed with client_running in
10 of 74 fresh observations. The chase also produced three native damaging attacks and
three resumes before cancellation; that is recovery evidence, not complete escape acceptance.

Latest source adds eight-neighbour graph edges using Euclidean cost and existing native
corner/line collision guards. Movement packet construction is deferred until the whole
traversal frame finishes; idle heartbeats cannot overwrite active gait. Candidate escape
routes start at the committed endpoint and join there. Java fixtures cover the publication
boundary and ahead-route join. The native diagonal/routine check
`20260928-185937-3ef7e4` passed eight assignments on four bodies, diagonal-distance bound,
native RUN/reaction, and clean reuse (p95/p99 1.8/3.6 ms). This does not establish visual
running or smoothness. Watched server/client stopped while the user is AFK; no automatic
watched relaunch. Survival/corner regression result follows separately.


### Final diagonal/corner survival regression

`artifacts/civilian-headless/20260928-190354-252326/ipc/native-report.json` passed all 25
checks, thirteen autonomous encounters/native contacts, native lifecycle and ownership
cleanup. Locked fixtures still required defense before escape, so diagonal edges did
not bypass the tested blocked corners. Survival work p95/p99 8.7/36.042234 ms exceeds
targets and regressed against the earlier cardinal run; profile geometry and fixture
costs before increasing population. This is functional evidence, not performance acceptance.
The headless server stopped gracefully. No watched server/client remains running, and
no visual acceptance of the final packet/diagonal changes has been collected.

## Watched retry — running animation still failed, 2026-09-28

World `AKR_DayOne_Test_20260928_191738_658aa9`, epoch
`ed0b3121-3c21-483a-b32e-32945403e19c`, batch `20260928-191922-d57501d1`.
LOCOMOTION completed with verified cleanup; this is a machine result only.
OPEN_ESCAPE moved the civilian 33.23 tiles and hunter 35.21 tiles before the user
closed the client. The user explicitly reports no running animation, while bush and
attack reaction animations work. Human verdict is **failed**, archived verbatim in the
batch. No later case ran; smoothness/diagonal visual acceptance is not established.
Before disconnect, the controller also recorded `candidate_origin_stale` after a
RECOVER transition; this remains a separate navigation issue to diagnose.
Disconnect left CLEANUP_BLOCKED with exact Actor, hunter and terrain ownership retained
(three bodies parked, fourth retained). Preserve this world; do not claim cleanup passed.
Disposable server stopped gracefully; no project containers remain running. No further watched retry until a substantive running
presentation fix has independent checks. Next inspect stock remote IsoPlayer running
animation inputs and packet decoding against the pinned engine, alongside our Actor gait
publication; do not infer animation success from RUN flags or translation speed.

## Fatal watched retry failed; visibility/loadout corrections prepared (2026-09-29)

Batch `20260928-220300-e7815a7c`, event
`4885aa10-16b0-4f45-87ff-217d05b2965f.3`, recorded four damaging incoming attacks,
two defensive contacts, native death/corpse/reanimation, durable terminal receipt and
same-Java-object reassignment. It failed at `REUSE_POSITION`; cleanup remained blocked
with Actor, hunter, aftermath and geometry reservations. The disposable server was
stopped gracefully and the complete failed world `20260928-215420-1d4250` preserved.
Do not classify its resources as cleaned or the lifecycle as accepted.

Human feedback (also archived verbatim): death looked sudden, reanimation was not visible,
and the pistol was missing. User requested stronger hearing/strength and removal of side
doors. Original fixture started at ~10 HP. Source review found immediate teleport after
server reanimation, lifecycle early returns bypassing Actor replication while waiting
for visibility, and a corpse cleanup predicate requiring a null square reference even
though stock removal retains that reference. These explain concrete harness defects;
corrected native cleanup and visible reuse still require execution evidence.

Prepared revision `e1d4996d218218705285edb56a5933687633721fe1f5672788432ca4f8a05b83`:
- Full-health civilian; no fixture doors. Only native damage may cause death. Escape is
  permitted; lack of a fatal encounter stays NOT_EXERCISED, with no automatic retry.
- Dedicated world ZombieLore: Strength=1 (Superhuman), Hearing=1 (Pinpoint), Sight=1 (Eagle).
- Eight continuous seconds of client-confirmed visible reanimation before teleport/reuse.
- Lifecycle replication/config publishing runs even while waiting; corpse cleanup checks
  actual memberships instead of a retained square pointer.
- Loadout acknowledgment is scoped to current player object, epoch and event. Every new
  case supplies/refills the marked pistol and two spare magazines; repeated requests within
  a case do not replenish spent ammunition. Countdown waits for that case's confirmation.

Java unit/integration, pinned build guards and premain passed; **274 Python tests passed**. Focused Lua/Python regressions
cover stale-event/reconnect loadout acknowledgments and diagnostic identity fencing.
The fresh prepared world is `AKR_DayOne_Test_20260928_221306_50f2a7`, .132:16281.
Client process was confirmed absent before installation. No new watched case submitted.
Previous failure, full native cleanup, actual reanimation visibility and two-client
qualification remain separate gates; no production or normal .132 changes.


## Door-free lifecycle attempt: escape, replication failure, cleanup verified (2026-09-29)

After explicit user readiness, ran batch `20260928-221607-385fbdb0`, event
`1cf19cf2-1c9f-4f67-9079-cb9f0d7cd170.1` in world
`AKR_DayOne_Test_20260928_221306_50f2a7` with revision
`e1d4996d218218705285edb56a5933687633721fe1f5672788432ca4f8a05b83`.
The current-case loaded-pistol acknowledgment arrived before countdown. The full-health
civilian fled 26.49 tiles; zero injury attempts, injuries or defensive contacts. It
remained at 100 HP. User observed: “Hahahaahaha, the NPC just fucked off.” Separate partial
human feedback is archived; this does not qualify death, reanimation or post-death reuse.

The test failed `client_replica_divergence:0:3.23` before the encounter timeout, rather
than reaching NOT_EXERCISED. Cleanup subsequently verified: resources empty, one
constructed/reused body parked. Machine report retains original failure; case client
log slice has zero error lines. Controller p95/p99 3.2/5.4 ms; total 3.5/65.79 ms including
initialization. Performance targets not qualified. Server remains ready without a new
submission. No automatic retry. Next: diagnose divergent flee replication and design a
visible native fatal-contact fixture that does not depend on a shambler catching a healthy
runner or on view-blocking doors. Preserve escape behavior and do not force death.


## Flee drift investigation and stationary lifecycle fixture (2026-09-29)

The `20260928-221607-385fbdb0` trace stays below ~0.93-tile peak during its first
straight RUN, then grows during short WALK/turn segments after resuming movement.
There is a concrete wire/translation mismatch: switching to WALK immediately sends
walking flags/blend, while MovementTransition previously retained RUN speed during
its 200ms deceleration. Cap transitional translation at the current native clip rate;
retain the existing acceleration warm-up. Classify forecast/caution on each new edge
before its first movement, rather than inheriting the previous edge's classification.
The unchanged >3 tiles / 750ms drift gate remains. This is a candidate correction,
not proof the whole observed gap is resolved; native turning/prediction remains a
watched regression gate. No client transforms, collision bypass or threshold relaxation.

Per user authorization, prepare an explicitly announced stationary-defense lifecycle
fixture on the open ground with no doors. It uses the existing controller movement-hold
boundary and an explicit fixture-only unavailable-escape observation. Navigation
requests are suppressed only for that controller fixture; normal civilians are unchanged.
Native defense, injury attempts, damage/reactions and death remain active. It starts at
100 HP against one Superhuman-strength, Pinpoint-hearing, Eagle-sight fast shambler.
No lethal contact in 120 seconds stays NOT_EXERCISED. This isolates terminal lifecycle;
it is not autonomous-survival acceptance. Gun acknowledgment, visibility hold, exact
cleanup and separate human feedback remain required.

Java unit/integration and 274 Python tests pass. Native headless survival validation is
passed in `20260928-222158-842d0e` (epoch
`242066c0-d97d-4d6b-a0cf-1db57e31565f`): 13 encounters/13 contacts,
native corpse/terminal receipt/reanimation/exact-body reuse and cleanup. Walking p95/p99
1.2/2.4 ms; survival controller 8.3/30.82 ms; total 7.6/146.09 ms includes cold prewarm.
Release performance gates remain unmet. Agent built/deployed SHA
`37e84914692be49f4095aeec69ee8c0033ee5f6f422b1fb299c44cbdbb43eb05`.
All 55 installed client files match; fresh client launch requested, awaiting readiness.
New watched world prepared:
`AKR_DayOne_Test_20260928_222229_cda5f4`, no case submitted. Prior cleaned watched server
was stopped gracefully. Production/normal .132 untouched.


## Stationary lifecycle watched: reanimation visible gate passed; reuse replica missing (2026-09-29)

User confirmed in-world readiness. Batch `20260928-222547-01539f19`, epoch/event
`f6bfdc35-f0aa-478d-ad03-d85825803501.1`, world
`AKR_DayOne_Test_20260928_222229_cda5f4`, revision
`37e84914692be49f4095aeec69ee8c0033ee5f6f422b1fb299c44cbdbb43eb05`.
Stationary-defense fixture recorded 31 damaging injuries and 11 defensive contacts before
native death. Native corpse/reanimation completed and the fresh client reanimated-visible
hold passed before relocation. Durable receipt and same-object server reassignment passed.
However replacement Actor presentation again timed out at REUSE_POSITION (client reported
absent). Thus prior heartbeat fix was insufficient; inspect native dead-player/online-ID
replica bookkeeping rather than claiming post-death reuse validated.

Final phase FAILED; exact cleanup VERIFIED, resources empty, one Actor parked. No automatic
retry. Human confirmed “Saw all three; gun present”: defense, death and reanimation presentation
accepted for this stationary one-client fixture. Recorded as partial feedback because
replacement replica presentation failed; full lifecycle remains unqualified. A pre-countdown PlayerHitSquare inconsistent-packet warning exists; do not claim
an entirely clean network log. No validated observer hit was recorded by interference gate.
The server remains running without another submission. The flee replication regression
and two-client acceptance are still pending independently.


## Correction: reused replica exists; observer-facing omission fixed (2026-09-29)

Detailed audit supersedes the preceding post-death replication diagnosis. All **30 fresh
REUSE_POSITION samples** in `20260928-222547-01539f19` report the replacement at
(10589.5,10080.5), transitioning GenericDefaultState → IdleState. Client actor logs
independently agree. The absent report was from CLEANING/COMPLETE and was wrongly
attributed to reuse. Native client replication did occur; visible presentation gate failed.
Evidence: batch-local `reuse-visibility-audit.json`. Do not change online IDs or clear
native death bookkeeping based on the disproven missing-replica hypothesis.

EncounterWatch only oriented the observer in POSITIONING, omitting REUSE_POSITION.
It now faces the actual Actor in both reuse positioning and reuse walk. Visibility still
requires stock screen/LOS checks; no fog bypass. Separate presence/on-screen/loaded/LOS/
visible diagnostics are bounded to 200 characters and exported in additive EncounterPair
field 43 (`client_visibility`). A Lua regression verifies facing for all viewing stages
and no forced facing during ordinary combat. Native inspection of pinned GameClient,
ConnectedPacket, PlayerPacket and PlayerTimeout confirms no engine changes were needed.
Exact visual cause remains pending confirmation by the repeat; no native reuse failure
is inferred from the old visibility timeout.

All 274 Python tests and Java unit/integration/build guards/premain pass. Agent SHA:
`7a19134a247ecb9f1ac7b37b3bc24d6fecf1d2a51af56f4c84a54f15d57151fb`.
No additional headless run: only observer-facing/diagnostics changed; previous native
lifecycle/survival evidence remains applicable within its documented limits. Previous
cleaned server stopped; new watched world `AKR_DayOne_Test_20260928_223313_996072`
prepared. Test client confirmed closed before installation. Await fresh readiness for
the same stationary-defense lifecycle sequence. Prior visual acceptance of defense,
death and reanimation remains; replacement walk and two-client acceptance remain pending.


## Stationary fatal lifecycle accepted with one ordinary client (2026-09-29)

Batch `20260928-223514-adb64ed6`, world `AKR_DayOne_Test_20260928_223313_996072`,
epoch/event `621685ec-307a-46cc-a3ae-6909d55e0ead.1`, agent
`7a19134a247ecb9f1ac7b37b3bc24d6fecf1d2a51af56f4c84a54f15d57151fb`.
Machine outcome PASSED / COMPLETED, cleanup VERIFIED, zero retained resources.
Native attacks inflicted 19 damaging injuries; civilian made eight defensive contacts
before death. Native corpse/inventory/reanimation, durable terminal receipt, client-visible
reanimation hold, exact-object reassignment and replacement eight-tile walk all completed.
One Actor constructed, two assignments, one parked after cleanup. No new client error
lines in the scoped log. Controller p95/p99 0.8/2.8 ms; runtime 1.0/3.8 ms for this short
one-Actor fixture, not a population-capacity qualification.

Human confirmation: “Yes—replacement appeared and walked normally.” Separate passed
feedback archived. Previous human acceptance covers defense, death, reanimation and gun;
this completes the watched stationary lifecycle slice. The observer-facing correction
resolved this repeat's visibility timeout without altering native identity/replication.

Server remains running with no active case or retained resources. Next gates remain:
repeated fatal lifecycle in one session, four-Actor correctness, independent flee/turn
replication regression, full save/reload aftermath fidelity, performance at scale and
actual two-client qualification. Stationary fixture acceptance does not certify an
unrestricted pursuit/escape/death scenario or general multiplayer replication.

## Source checkpoint and queued neighborhood task (2026-09-29)

User requested saving the accepted plan for tomorrow and committing accumulated WIP.
Created [TASK_Four_Resident_Neighborhood.md](../../TASK_Four_Resident_Neighborhood.md),
planned for **2026-09-30**, and linked it from AGENTS, the vault index, roadmap, operations
and Current State. Scope: movement regression, repeatable lifecycle, four independent
civilians, real ground-floor homes/routines and controlled living-resident restart.
Crash recovery and aftermath restoration are explicitly deferred follow-ups.

Fresh source-checkpoint verification: `./dayone runtime-test offline` passed all three
layers in `artifacts/runtime-tests/20260928-230404-2b11c513/`: 274 Python tests; Java
unit/component, compatibility guards, transformed bytecode and premain integration;
C++ core fixtures and Protobuf/socket integration. Build output remains local and was
not deployed. Existing dependency/JVM deprecation warnings remain; no test failed.

Reviewed source candidates for unexpected binaries, embedded credentials, downloaded
dependencies and replacement engine classes. The checkpoint includes original project
source, test harnesses, editor configuration, skills, design and evidence records;
generated output, upstream/game files, secrets and saves remain ignored. This is a source
checkpoint, not a release or a claim that pending native/multiplayer gates have passed.
No server operation or new watched test was performed for this checkpoint.
