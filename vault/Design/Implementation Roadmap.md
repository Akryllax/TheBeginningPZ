---
type: design
status: accepted-implementation-in-progress
updated: 2026-09-27
---

# Implementation roadmap

## Next task — planned for 2026-09-30

Follow [Four-resident neighborhood](../../TASK_Four_Resident_Neighborhood.md):
close the independent flee/turn replication regression; repeat the accepted lifecycle
three times in one process; exercise four independent civilians; add real ground-floor
homes and game-time routines; restore living residents after a controlled save/restart.
This is the accepted next plan, not implemented behavior. Larger crowds, vehicle work
and migration stay separate; two-client qualification remains pending.

**Deferred milestone: crash recovery and aftermath reconciliation.** Retain explicit
work for incomplete-checkpoint fault injection, world/ModData/terminal-receipt reconciliation,
corpse and reanimated-state restoration, terminal precedence, duplicate prevention and
operator-visible quarantine. Controlled restart does not qualify these behaviors.

## Civilian survival checkpoint — 2026-09-28

The native server adapter now consumes the existing Lua civilian policy and executes
bounded escape navigation and timed melee/shove actions. One then four civilians
passed 13 stationary-threat defense/escape cases with original body reuse. Continue
with watched action timing/presentation, live owner-confirmed incoming bites, then
integrated combat-to-death and two-client tests. Batch work p95 remains above the
release target (latest 4.6 ms); keep larger combat crowds gated. Details and evidence:
[[Civilian Defense and Death]]. Vehicle work and the 42.21 migration remain separate.

## Accepted priority revision — 2026-09-27

Follow [[Design/NPC First Slice]]: minimum reusable controls and measurements, then the one/four
server-pedestrian feasibility gate **before** full reload/soak. Bandits2 remains the
initial spawn/presentation dependency. Shared accounting belongs to AKRPopulation;
AKRResidents contains both resident and cheap crowd controllers. Moving traffic and
commute/driving acceptance below are deferred beyond the first pedestrian slice.
Server simulation is not yet validated. Planner absence will use server Lua fallback.
The prior detailed roadmap below is retained as background, subordinate to this revision.


This is the accepted continuation after source checkpoint `0599298`. It supplements
[[First Week]] and [[Navigation]]; completion evidence belongs in
[[Experiments/Implementation Ledger]]. A controlled asphalt turn passed on the server;
integrated resident driving and two-client turning remain unvalidated.

## Immediate priority: persistent event runtime and batch testing

The 2026-09-27 user direction supersedes immediately running another individual crash:
first remove the recurring restart requirement from routine iteration. Follow
[[Live Event Runtime]] and [PLAN_SchedulerAPI.md](../../PLAN_SchedulerAPI.md). The framework
is planned, not deployed; [[Experiments/Current State]] identifies the running build and
incomplete opposing-car edits.

Extract one resident native-world/entity/terrain owner, add the shared operator/Storyteller
event API with a bounded FIFO, then stage compatible behavior reload between cleaned-up
events. Add unattended and watched batches with explicit receipts and cleanup gates. Keep
normal-world automatic scheduling disabled. Validate same-process parameter changes,
Java/Lua reload, fault recovery and a 100-case/20-reload soak before claiming routine live
iteration. Return to low-speed two-body validation, then the requested faster opposing-car
experiment. A rollover remains unproven and is a separate retained experiment.

## Following milestone: one complete commute

Demonstrate the same resident walking from home to a car, boarding, driving to work,
parking, walking to the workplace, and returning home. Preserve identity, appearance
profile, possessions, health, infection and history. Use the user's selected simple
original seated model while onboard, ordinary Lua clients and server-only JVM injection.

1. Reconcile current audit findings; migrate persistent fields explicitly. Separate
   schedule randomness from event randomness and correct population accounting.
2. Cache immutable road adjacency/spatial lookup across worker threads. Bind graphs
   to navigation identities and vehicle profiles; keep expiring obstructions separate.
   Validate actual vehicle-to-road connectors and swept turns. Use successive plans
   when approaching a car would exceed the existing six-action limit.
   The latest one-client turn exposed missing lane discipline and stop handling. Validate
   a right-hand lane course, stop-line dwell and faster bounded cruise before adding traffic.
   The corrected Bézier course passed a one-client smooth-turn check with a two-second
   stop and correct exit lane. The user then requested a roughly 50 km/h street limit and
   vehicle-specific engine, brake and steering constraints. Validate the faster capability-
   aware controls and sustained cruise on a sufficiently long road; the short course cannot
   establish 50 km/h cruise. One client now accepts the faster turn (24.1 km/h actual peak,
   about 13 km/h through the bend). Record parked/moving blocker results separately.
   Parked approach/wait now passes one-client visual acceptance: the car remained visible
   during a 66-second queue, and the server resumed the route after fixture removal.
   A subsequent one-client straight-road trial confirmed a smooth parked-car pass,
   return to lane and audible horn. Moving blockers remain a separate gate.
3. Extract managed native vehicle control from the disposable probe. Own only registered
   scenario bodies and reference-counted terrain; retain parked cars after completion.
   Use bounded loading ahead, local emergency braking and road/vehicle speed envelopes.
4. Detailed seated-driver presentation is now low priority by user choice after the
   first visible turn. Preserve the original model as an optional representation. Add explicit
   boarding/onboard/exiting/unresolved states, semantic driver-seat reservations, snapshot
   reconciliation and confirmed walking-actor retirement/recreation. Advance execution
   generations without replacing the logical resident identity.
5. Validate human takeover, crash/damage/death, late joins and two-client state agreement
   before enabling normal ambient traffic. Uncertain transitions remain reserved and
   stopped. Then validate two cars, emergency trips and the four-moving-car limit.

## Blockers and traffic incidents

Add [[Traffic Incidents]] after the lane/stop controller. Normal driving retains local
blocker forecasting and braking. Extend persistent queues with temperament-dependent
honking and patience, bounded attempts to find a drivable bypass, and authoritative
reservations when two cars want the same gap. The single-car straight-road bypass now uses
detached candidate generation, live oriented clearance and an entry/pass/rejoin reservation;
two empty-server runs and one ordinary-client observation passed. The first shoulder run avoided road
obstacles but phased through a pole. The corrected planner rejects that detour; native
terrain activation now uses an owned collision map and a low-speed pole contact has produced
real stopping, stock damage and one sound broadcast on an empty server. Validate this with
one client, then register managed/parked collision bodies and test real two-car contact.
Extract one shared native-world/map lifetime and batch all reservation offers before
allowing two moving cars; independent single-car probes cannot share their current cleanup.
Keep the existing global coordinate frame when initializing headless physics, and verify
preloaded parked vehicles across startup. Temperament may admit short off-road attempts after
road options fail, with slower motion and the same physical obstacle checks.
Allow bounded imperfect driving through normal physics, without disabling actor checks
or manufacturing ordinary crashes by random emergency-brake failures. Validate native
two-car contacts in an empty world before
staging a visible crash. Incidents reserve the complete hazard envelope and exclude every
player and player-owned object, including likely approaches. Off-screen crashes emit one
spatial sound and retain discoverable aftermath at that same recorded location. Preserve
resident/wreck identity and bounded fleet resources across visibility and restart.

Current control split: the C++ worker implements high-level GOAP-lite and road routing;
Lua owns residents/scenario state; the server Java agent executes steering, speed and
emergency braking through native Bullet physics. The current probe loads a reviewed
route artifact directly and is not driven by the C++ worker. Connect worker routes to
the managed executor after vehicle ownership and lifecycle extraction. Immediate collision
avoidance must continue during worker/IPC outages.

## Living town and seven-day outbreak

Connect verified homes/jobs to staggered routines, supplies, rest and threat responses.
Replace arbitrary regional exposure with persistent incidents and bounded contacts:
household cases, clinic attendance, emergency response, closures and evacuation. Preserve
the manual 168-hour clock, empty-server/worker-outage holds, identity through conversion,
and transition into survival goals and individually validated adaptive events.

## Interfaces and acceptance

Extend Protobuf additively for navigation identity, vehicle observations and road closures.
The server owns durable bindings, occupancy revisions and action effects; Observer remains
read-only. Persistent vehicle identity is separate from its current native ID. No client
Java agent, core-file replacement or redistributed game assets are permitted.

Run automated lifecycle/protocol/navigation tests, dedicated-server commutes and fault
injection, then one-client and two-client walkthroughs. Measure aggregate added work against
p95 <2 ms / p99 <5 ms, separately reporting cold creation and total engine costs. Record
unperformed checks as pending. Test the calm opening in new buildings and basements, save
at transitions, interrupt the worker, and verify population/resource accounting.

Preserve the playable prototype until scenario acceptance and a verified archive permit
the already-authorized fresh-world rollout. Retain accounts, XP10, rapid reading and player
Knox immunity. Production `.160` remains separate. All tooling and evidence stay local to
this project; publish only original source on the existing development branch.
