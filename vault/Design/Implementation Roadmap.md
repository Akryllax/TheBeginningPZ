---
type: design
status: accepted-implementation-in-progress
updated: 2026-09-27
---

# Implementation roadmap

This is the accepted continuation after source checkpoint `0599298`. It supplements
[[First Week]] and [[Navigation]]; completion evidence belongs in
[[Experiments/Implementation Ledger]]. A controlled asphalt turn passed on the server;
integrated resident driving and two-client turning remain unvalidated.

## Next milestone: one complete commute

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
   Optional horn timing is connected; the observed driver selected silence, so sound
   playback is still unverified. Moving blockers remain a separate gate.
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
reservations when two cars want the same gap. The detached reservation arbiter exists;
native candidate clearance, passing/rejoin control and managed multi-car wiring are next.
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
