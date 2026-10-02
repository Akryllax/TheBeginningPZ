---
type: task
status: planned
created: 2026-09-29
planned_for: 2026-09-30
title: Four-resident neighborhood milestone
---

# Four-resident neighborhood milestone

Accepted plan saved for tomorrow, **2026-09-30**. Do not interpret this task's presence
as evidence of implementation or permission to run an unattended watched test.

## Starting checkpoint

Read [Current State](vault/Experiments/Current%20State.md) before operating services.
The one-client stationary lifecycle passed in batch `20260928-223514-adb64ed6`:
native death → corpse/inventory → reanimation → same-Actor reassignment → visible
replacement walk → verified cleanup. One body, two assignments, no retained resources.
The user confirmed the replacement appeared and walked normally. The earlier reuse
timeout was a viewing/facing omission, not a missing native replica.

Latest accepted agent: `7a19134a247ecb9f1ac7b37b3bc24d6fecf1d2a51af56f4c84a54f15d57151fb`.
Last watched world: `AKR_DayOne_Test_20260928_223313_996072`;
epoch `621685ec-307a-46cc-a3ae-6909d55e0ead`. Check actual service state tomorrow;
do not assume this process or an old readiness response remains valid.

The independent fleeing/turning replication regression, four-resident performance,
full aftermath restoration and two-client qualification remain open.

## Summary and accepted choices

Build on the accepted one-client lifecycle test to prove repeatability, four independent
civilians, and a small inhabited neighborhood.

Use **real ground-floor homes**, short routes and game-time routines. Include
**controlled save/restart restoration for living residents only**. Explicitly defer crash
recovery, corpse/reanimation restoration, larger crowds and two-client qualification.
The user specifically asked that the crash-recovery deferral be recorded, not forgotten.

## 1. Close the movement regression

- Add a repeatable course covering straight WALK/RUN, diagonals, short alternating turns,
  an unlocked door, a blocked passage and interruption/resumption.
- Keep native collision and replication. Preserve the failure threshold of more than
  three tiles' divergence for 750 ms; do not relax it to obtain a pass.
- Record timestamped server/client positions, gait, prediction inputs and visibility
  independently. Distinguish delayed samples, hidden Actors and movement divergence.
- Verify the recent transition-speed and turn-lookahead corrections before advancing
  the watched batch.

## 2. Repeatable, independent civilian lifecycles

- Add three consecutive lifecycle cases in one server process, using the same single
  prewarmed Actor. Each cycle must complete death, corpse/inventory transfer, reanimation,
  replacement identity, walking and verified cleanup.
- Refactor the encounter harness so each civilian owns its controller, lifecycle stage,
  deadlines and aftermath records. Remove assumptions that only the first participant
  can die or reanimate.
- Add a four-civilian encounter with four ordinary native-owned hunters. Civilians may
  escape, defend, suffer injuries or die independently; do not require predetermined outcomes.
- Track hunter deaths and their corpses as well as civilian aftermath. A dead hunter
  must not become an untracked resource.
- Preserve bounded population accounting, generation checks and terminal receipts.
  Never construct replacement bodies during assignment or discard uncertain resources.
- Keep stationary-defense behavior exclusive to explicitly announced lifecycle fixtures.

## 3. Neighborhood routine

- Prepare a fixed Muldraugh scene manifest containing four named residents, distinct
  ground-floor homes and one nearby destination each. Validate approximately 20–80-tile
  routes using WALK and DOOR only; reject stairs, climbing and locked entrances.
- Extend the existing GOAP-lite routine and equivalent Lua fallback. Keep the server
  authoritative and the worker optional; do not introduce another planner or movement executor.
- Use two staggered work routines and two shorter outings. Residents leave home, walk to
  their destination, remain there, return home and repeat the following game day.
  Destination activity initially means idle presence, not simulated employment or shopping.
- Persist identity, appearance, possessions, health, home/destination assignments,
  schedule day, routine phase and unfinished progress. Completed actions must not replay
  after interruptions.
- Immediate danger suspends the routine and uses existing flee/defense behavior. After
  recovery, resume unfinished work only if its schedule remains relevant; otherwise
  select the current scheduled goal.
- Start calmly. Introduce one native threat through an explicit test command after the
  calm routine passes. Neighborhood deaths leave normal aftermath; fixture cleanup
  rules must not erase it automatically.
- Use the existing bounded native path-request adapter for routes between buildings,
  feeding locally validated execution windows. Retain one pending request per resident
  and bounded, fairly scheduled planning work.

## 4. Controlled restart of living residents

- Add an explicit checkpoint-and-stop operation: stop new routine admission, settle
  native actions, capture living snapshots and logical progress, verify retirement,
  then complete the world save.
- Write a versioned checkpoint manifest linking resident snapshots, logical records,
  world/build identity and checksums. Perform disk writes from detached data.
- On restart, validate the completed checkpoint, prewarm the pool, restore living
  residents and replan physical routes from restored positions. Native objects, paths
  and transient deadlines never persist.
- Missing, incomplete or conflicting checkpoints remain quarantined. Terminal records
  must prevent an older living snapshot from resurrecting a deceased resident.
- Do not claim recovery after process crashes or restoration of corpse/reanimation state.

## Interfaces and compatibility

- Extend the existing runtime submit/status/cancel and `./dayone` workflows with movement
  regression, repeated lifecycle, four-civilian and neighborhood cases.
- Add neighborhood checkpoint/restore operations and per-resident lifecycle/routine
  status. Preserve existing scenario commands.
- Extend Protobuf fields additively for schedule progress and diagnostics; explicitly
  migrate resident saved-state schemas. Existing simple routine fixtures retain their behavior.
- Keep pinned 42.20.4, server-only Java hooks, ordinary Lua clients and existing private IPC.
  Observer remains read-only. Follow [AGENTS.md](AGENTS.md) and the test-iteration,
  watched-testing, native-worker and NPC-replication skills as applicable.

## Validation and acceptance checklist

- [ ] Automated tests: schedule rollover, interrupted waits, stale plans, worker outage
  and fallback, independent deaths, duplicate receipts, inventory isolation, checkpoint
  mismatches and refusal to restore terminal residents.
- [ ] Headless native: ground-floor routes and doors, four-controller budgeting,
  repeated pool reuse, controlled restart with matching identities, inventory and progress.
- [ ] Watched movement regression accepted.
- [ ] Three lifecycle repetitions accepted in one process with the original initialized body.
- [ ] Four-civilian encounter accepted with independent outcomes and exact cleanup.
- [ ] Calm neighborhood routine accepted, followed by the explicit threat interruption.
- [ ] Watched procedure retains automatic placement/facing, protection, daylight, loaded
  9mm, exact announcements and separate human feedback. Stop advancement on failure or
  uncertain cleanup; native non-engagement remains “not exercised.”
- [ ] Test-only schedule clock demonstrates routine phases without accelerating physics.
- [ ] Measure four-resident steady operation for ten minutes: total/component p95, p99
  and maximum work, path queue age and resource counts. Target added work below
  2 ms p95 / 5 ms p99; failures block expansion. This does not replace later
  population-capacity or two-client qualification.
- [ ] Update design, roadmap, Current State and runbooks with actual evidence and remaining gates.

## Explicit follow-up: crash recovery and aftermath reconciliation

**Deferred, not completed or silently dropped.** Add a later milestone covering:

- Incomplete-checkpoint and interrupted-write fault injection, including process termination.
- Reconciliation between world saves, logical resident snapshots and terminal receipts.
- Terminal-record precedence and prevention of duplicate bodies/items or resurrected residents.
- Restoration of corpse inventory and reanimated identity after saved-world reload.
- Operator-visible quarantine and an explicit reconciliation path for ambiguous ownership.

Keep this follow-up linked from the roadmap and operating runbook. Controlled restart
success in this task must never be reported as crash recovery.

## Deployment boundary

All deployment remains in disposable `.132` worlds. Normal `.132`, production `.160`,
vehicle development, the 42.21 migration and crowd expansion are outside this milestone.
Prepare independent code/tests before requesting watched feedback; use fresh readiness
and stop on failures. Preserve prior evidence and unrelated working-tree work.

## 2026-10-02 — Richer watched routine acceptance

The user found the simple out/wait/back test insufficient after the 42.21 regression.
Prepare one civilian in a real ground-floor house before expanding to four:

1. Begin visibly inside its assigned home, with an unlocked exterior door closed.
2. Choose an outdoor destination from the current goal, request an actual route and
   execute it through the existing native adapter. Open/cross the door and respect walls
   and furniture; do not substitute a coordinate tween or hand-authored teleport route.
3. Perform one genuine world interaction at the destination, with measurable effects.
   Proposed first interaction: retrieve one tagged test item from a known container;
   validate engine support/animation before promising it. Merely waiting or displaying an
   animation does not satisfy this requirement. Preserve original contents and item ownership.
4. Return through the doorway and finish at an interior home position. Confirm actual
   position, inventory/effect, plan progress, animation/audio and clean action completion.
5. Repeat with a blocked preferred route and a valid alternative. Observe replanning
   and collision-respecting execution. A locked sole exit must yield a bounded blocked
   result, not clip through or unlock without a key. Resume correctly after obstruction clears.
6. Repeat with four independently progressing residents, including one delayed/blocked
   resident; others must continue. Keep this separate from the initial one-resident gate.

Use existing GOAP-lite goals, per-resident progress and bounded execution windows. Do
not create a second planner or run unsafe engine mutations on worker threads. Include
one continuous traversal observation or automatic spectator repositioning with explicit
announcements so the user can see the interior, doorway and destination stages.
The real interaction extends the earlier idle-presence-only activity scope. Controlled
restart, long schedules and stress remain later gates. First reconcile retained resources
from the interrupted watched run; do not restart into a fresh world to hide that failure.
