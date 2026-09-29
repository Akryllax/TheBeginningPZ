---
type: runbook
status: watched-reuse-passed-two-client-pending
updated: 2026-09-28
---

# Civilian qualification batch

The user authorized native functional tests without a client, then a watched four-wave
reuse batch with the ordinary local client. The broader traversal/two-client sequence
remains deferred; neither batch substitutes for that qualification.

## Watched reuse batch (2026-09-28)

`./dayone civilian-watch create/start/join/status/stop` selects one action at a time.
Stop the preceding disposable server before `create`; keep the client closed for
`start`. The guarded `WatchedCivilianHarness` prewarms four engine Actors, assigns
four new residents per wave, and repeats the same four parallel 150-tile corridors
four times. No constructors are allowed during assignment. Each outgoing resident
gets a distinct saved body; a scratch inventory item checks for carryover on reuse.
Male/female profiles alternate and the client must report the incoming sex while
on-screen. The local client is ordinary Lua only, with bounded four-Actor telemetry.

Server chat announces preparation, movement, finish, verified retirement and the final
pass/failure. Between waves the observer is returned to the viewing point and admission
waits for clearance and fresh client reports. Retirement requires five seconds off-screen
and at least 72 tiles from every observer, followed by verified native removal and two
seconds of client absence. Refusals retain ownership. Reports expose slot states and
specific materialization errors, including failures before a body is assigned.

This is a purpose-built, opt-in harness in a fresh disposable world, not generic runtime
hot reload or ambient population activation. A passed lifecycle report means four complete
passes with verified reuse/removal; client/server position error, sound, interpolation,
combat and two-client agreement remain independent gates. User feedback from the first
two completed waves confirmed changing identities/reuse but reported **missing footsteps**.
That initial audio report was subsequently corrected by the user: footsteps were audible. The initial retry stalled before wave 3 and was stopped; its
partial report is retained under `artifacts/scenario-tests/20260928-093617-aa2b0b/`.

Successful announced rerun: `artifacts/scenario-tests/20260928-094756-a2fa59/`
(four waves, 16 assignments, four constructors, zero retained bodies). User confirmed
success. See [[../Experiments/Current State]] for timing and the failed position-error
qualification; position accuracy and two-client validation remain open.

## 32-Actor marching stress case

Use `./dayone civilian-watch create 32` after stopping the previous disposable batch.
This original case uses 32 slots; the default native pool remains four. The test uses
an 8-column × 4-row grid (one-tile lateral and 1.5-tile longitudinal spacing), 150 tiles
per member, four waves and 128 resident assignments. Preflight checks every member's
corridor incrementally. Warmup, assignment, snapshot retirement and parking are staggered;
all bodies must be parked and absent on the client before the next wave is admitted.

Client telemetry is negotiated through the guarded server configuration, capped at 64
ordered IDs and rejected on wrong count/order/epoch. It never authorizes gameplay effects.
`FrameSamples.lua` records fixed-size render-callback interval histograms every five
seconds, tagged by phase/wave; phase 2 supplies a short no-Actor baseline and phase 4
covers the march, including its off-screen approach/departure. Metrics include diagnostics,
not isolated GPU cost. Harness work now includes report I/O, so old four-Actor timing is
not an exact benchmark comparison. Container samples and native packet/heap counters
supplement the phase timings. Keep cold initialization spikes separate.

This exercises repeated initialized-body reuse and collision-checked straight movement.
It does not run 32 GOAP/FSM controllers, perception scans or concurrent native path jobs;
their existing budgets remain unchanged. Do not label the test release-capacity or soak
qualification. Ordinary local-client observation remains separate from two-client evidence.

Audio correction: the user subsequently confirmed footsteps were audible during the
successful four-Actor run. The earlier missing-audio report is superseded.

32-Actor batch passed: `artifacts/scenario-tests/20260928-100710-9f7bda/report.md`.
All four waves completed with 128 assignments on 32 bodies and full cleanup. See the report
for render intervals, server cost, memory and the still-failed position-accuracy target.

## 64-Actor chase extension

`./dayone civilian-watch create 64 16` selects four 8×8 waves with sixteen native fast
shamblers per wave. The ordinary client runs stock zombie AI under native ownership;
server ZombieControl assigns Actor targets. Scoped perception only refreshes configured,
locally owned hunters with an exact managed Actor target. No zombie transform driver.

The observer must have the admin account role. The harness verifies god/invisible/ghost
and repositions the observer **before every wave**, then moves them away for cleanup.
Do not ask the user to navigate to coordinate-only waiting points. Zombie qualification
requires every hunter to acquire a civilian target, move at least five tiles and be seen.
Removal is staggered and requires off-screen clearance, native removal and client absence.
This is pursuit-only; contact, death or ambiguous cleanup fails and retains resources.
It does not qualify 64 behavior planners, combat, two clients or a release population cap.

## Headless native batch

```sh
./dayone civilian-headless run
./dayone civilian-headless combat-probe
./dayone civilian-headless status
./dayone civilian-headless stop
```

`run` builds the agent and runs its Java unit fixtures, prepares a fresh world and copied
agent/mods, starts `akr-civilian-headless`, waits for its native report, then sends RCON
`quit` and verifies exit. A failed native check produces a nonzero command exit and retains
evidence. Boot timeout is ten minutes; native phases have bounded deadlines. If interrupted
or graceful shutdown fails, inspect `status` and use `stop`; retained worlds are never
restored over other saves. This is one fixed functional batch per process, not generic
hot reload, parameterized runtime submission or a soak runner. Avoid concurrent invocations.

World prefix: `AKR_DayOne_Test_Headless_`; artifacts and separate `current.json`:
`artifacts/civilian-headless/`. Private copied JAR/manifest/config/mods/world and redacted
container log accompany `ipc/native-report.json`. Core game files are read-only. Only RCON
27045 is published, on loopback; game UDP 16291/16292 is unpublished. The world does not
pause when empty, has no copied player accounts and uses a 3 GiB heap / 5 GiB container cap.
The existing interactive test server and its receipt are not operated.

`combat-probe` uses another fresh zero-client world. It forces stock reanimation
before Actor release, verifies the same inventory container transfers, performs
one direct stock melee contact against that zombie, and retires the Actor. It is
an engine feasibility probe, not a timed action or multiplayer presentation test.
Passing evidence: `20260928-150158-4995a5`.

Latest zero-client death extension: `20260928-144023-c575ca` passed stock corpse
handoff with the same item ID, native reanimation, clean reuse of the exact Actor,
and 32 incremental off-world prewarms. Use the report's `pending_gates` alongside
[[../Design/Civilian Defense and Death]]: neither this fixture nor the earlier
walking cases prove visible combat, incoming client-owned zombie bites, saved-world
corpse reload, or two-client replication. Cold prewarm hitches are measured, so
complete it before gameplay admission.

Passed native run: `20260927-210440-841d18`, 15 checks with zero clients:

- Load a small map neighborhood using native server interest; create one connectionless Actor.
- Obtain a path from the existing solver, walk eight tiles with stock collision, cancel an
  in-flight request and release request capacity (cancellation is not synchronous solver termination).
- Save/remove/load a body; assert scratch, item IDs/condition, equipped hammer, nested bag/pen
  and clothing count. Other serialized fields and appearance are not exhaustively compared.
- Three rounds of four simultaneous Actors, fifth-slot refusal and verified world/network removal.
- Create a tagged native door fixture, stop at it closed, refuse it locked, open it with stock
  `ToggleDoor`, walk through, then remove the fixture and Actor. No arbitrary world door is edited.

The initialized-pool batch now prewarms exactly four engine Actors before any resident
assignment. It dirties four male residents, assigns four fresh female residents to the same
objects, and restores the original residents onto different objects. It asserts no extra
constructors, no leaked equipment/items/wounds/recipes/books/literature/fitness/ModData/posture,
correct incoming sex, stale-token rejection, native walking and exact descriptor cleanup.
Busy Actor reset is refused. The original save/load, native path and door cases remain.
Reports separate warmup/assignment timing and constructor/reuse/retained-body counts.
The preceding same-resident cache experiment is superseded, not the accepted pool design.

The batch does not exercise controller-driven flee/roam, barricades, moving vehicles, multi-Actor
movement, native stairs/climbing or client rendering/network agreement. Functional checks do
not certify performance: the successful run's walking p95/p99 upper bounds were 0.6/1.5 ms,
but total work was 4.8/156.86 ms and materialization p95 ≤149.81 ms. Histogram overflow uses
the maximum. Keep creation cost visible; it has not passed the release budget.

Detached suites remain separate and do not operate game services:

```sh
./dayone runtime-test unit
./dayone runtime-test integration
```

Read [[../Design/Civilian Pool and Navigation]] for source capabilities and blockers.

## Remaining interactive batch prerequisites

Use only the disposable `.132:16281/16282` world and loopback RCON 27035. First retain its
save/agent manifest, then wire an explicit civilian experiment to the existing submit,
status and cancel scheduler. Use `Residents.Controller` with the new pool/native ports;
share scheduler Actor reservations, admission, cancellation and cleanup evidence. Do not
activate an unrelated spawn loop or change the existing production/test deployments.

Complete the scoped stair/climb execution spike before marking the full-traversal slice
ready. New engine hooks require source/bytecode inspection, build guards, offline hook-site
tests and one prepared server restart. Parameterized cases thereafter reuse that process.

## Ordered cases

| Case | Actors | Required evidence |
| --- | --- | --- |
| Path-only callback | 1 | Existing native solver returns copied path; cancel and supersede discard old callbacks |
| Spawn/idle/retire/reuse | 1 | Human appearance, no fear sting, removal quiet interval, no visible identity swap or resource loss |
| Body round trip | 1 | Wounds, nested containers, item IDs, hands, clothing and appearance match before/after rematerialization |
| Wall detour | 1 | Native route and motion respect wall/corner clearance; no straight-line fallback |
| Dynamic blockage | 1 | Newly closed door/parked car stops motion and causes bounded replan |
| Doors | 1 | Unlocked passage works; locked/barricaded passage fails without bypass |
| Stairs | 1 | Ascend/descend; matching server/client floors and native placement |
| Window and low fence | 1 | Correct native state/animation, collision, interruption, endurance and injury effects exactly once |
| High wall | 1 | Server-computed success/failure, no local-player impersonation or endpoint teleport |
| Idle/roam | 1 | Varied pauses and reachable targets; no per-waypoint animation stop |
| Flee/occlusion/trap/recovery | 1 | Visible threat interrupts; occluded threat does not; route avoids walls; trapped Actor waits; recovery stable |
| Four intersecting civilians | 4 | Fair perception/path queues, no overlap through solid geometry, shared admission/cleanup |
| Two observers and late join | 1 then 4 | Position/action agreement, joining during traversal, disconnect/reconnect and relevance transitions |
| Repeated cancellation | 4 | No stale result controls a replacement, no unresolved resource silently freed |

Run in small groups and retain per-case seed, exact build/mod manifests, operation counts,
queue age, timings (including capture/diagnostics), paired samples, errors and cleanup
receipts. Stop the batch on unresolved ownership or unsupported traversal. Existing combat
gate 3 is still reopened; ambient checks cannot mark it passed.

Targets remain added steady game-thread work p95 <2 ms / p99 <5 ms, aligned position error
≤1 tile p95 and action agreement ≤500 ms p95 under the documented network conditions.
Report materialization/snapshot spikes separately as well as in total workload. Full
release-cap stress/soak follows a stable one/four/two-client slice, not before it.
