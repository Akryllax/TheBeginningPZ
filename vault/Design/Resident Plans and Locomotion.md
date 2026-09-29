---
type: design
status: native-functional-passed-live-pending
updated: 2026-09-28
---

# Resident plans and locomotion

Accepted scope: a small end-to-end C++ GOAP-lite slice, native immediate flee/defense,
continuous local navigation, owned reaction recovery, and explicit WALK/RUN. Ordinary
Lua clients and pinned 42.20.4 native replication remain required. No client JVM agent.

## Evidence and causes

Batch `20260928-173834-0694a61e` failed OPEN_ESCAPE after 5.789 tiles, nine path requests
and twelve damaging attacks. Identity and generation did not change. Controller code
cancelled navigation on each attack and on two-tile threat movement. Native damage sets
HitReaction; the ordinary postupdate cleanup is skipped for Actors. Both controller
interrupts and native reaction lifecycle must be traced; an engine reset is not proven.
The prior door fixture failure remains distinct from the open-area failure.

## Implementation contract

AKRResidents owns the durable goal, action cursor, completed milestones, wait remainder
and routine home/activity points. A worker proposes at most six actions using the existing
bounded transport. It cannot overwrite a committed action. Logical facts and generation
reject stale plans. Worker absence permits a local equivalent three-action routine;
flee/defense never waits for IPC. Actor retirement discards physical routes and transient
clocks while preserving the logical routine. Terminal residents cannot be rebound alive.

The implemented routine is visit activity → wait → return home. Immediate danger suspends
it; calm recovery resumes it. The narrow typed ExecutionContext and explicit Locomotion
fields are additive to the existing protobuf contract. No new external network channel.

Geometry caching is bounded to 512 tiles/controller, two-second TTL, and map revisions.
Threat costs are separate. Flee capture starts at radius three; failure expands to eight
before declaring a known trap. A committed route can continue during a candidate search.
Candidate routes join at a physically reached boundary of the committed route. Native
collision remains active. The execution window contains at most eight edges including
the active edge; refill begins at four remaining edges. One candidate can be staged.
Traversal consumes one movement-time allowance across at most eight edges per tick.

Native injuries pause traversal instead of destroying it. An owned, matching, nonterminal
reaction receives an explicit completion after its bounded presentation hold; native
knockdown, floor, grapple and unrelated movement locks remain blocking. Unexpected state
is retained rather than cleared indiscriminately. Exact visual reaction timing remains a
watched gate. Actor speed uses native movement modifiers, with WALK/RUN flags and separate
requested/achieved measurements. Sprinting is deferred.

## Verification

New detached tests cover route retention, unsafe threat changes, reaction pause, worker
plan validation, durable progress and stale generations. Java fixtures cover geometry
cache invalidation and bounded continuous traversal. Native `civilian-headless planner`
uses the real C++ worker and four prewarmed bodies for routine/run/reaction/reuse checks.
Existing lifecycle/defense tests remain regression coverage. Watched cases retain the
8/2/2-second hold/quiet/countdown policy and immediate failure on unrecoverable death.

Performance, narrow-door execution, true incoming pursuit, and two-client agreement need
separate evidence. Follow [[../Runbooks/Watched Testing]] and [[../Experiments/Current State]].


Native acceptance: `20260928-182359-85ed5b` passed eight assignments on four bodies,
including real worker proposals, complete routines, running, native reaction enter/exit
and pool parking. Work p95/p99 1.7/2.9 ms in this sequential fixture. Existing client-owned
pursuit and final watched animation/recovery validation are not implied by this result.


## Visual correction after the first two watched cases

The user reported walking presentation during RUN, small position jumps, and cardinal
paths. Only those two cases were observed; later cases have no human acceptance.
In the real-chase capture, 10 of 74 fresh samples with server gait RUN had client_running
false. This supports a gait consistency problem, but does not prove an animation fix.

The local graph previously enumerated four neighbours even though native classification
already validates diagonals. It now enumerates eight at Euclidean edge cost; all four
cardinal corner boundaries must remain walkable, plus the native diagonal collision line.
The first native routine uses a diagonal activity point and rejects a round trip of 13+
tiles (the four-direction path would require 16).

Traversal now has one end-of-frame publication boundary. Intermediate edge movement does
not publish a partially completed frame; native stationary heartbeats cannot replace an
active WALK/RUN intent. Final stop and reaction pause explicitly clear gait. This targets
the observed animation resets and short position corrections while keeping stock packets.
Replacement escape paths plan from the committed endpoint, wait while approaching it, then
join there rather than repeatedly rejecting a path planned from a departed origin.
Visual smoothness and running animation remain pending a subsequent watched test.

## Continuous movement and feedback implementation, 2026-09-28

The latest failed watched run recorded `clientWalkSpeed=0` throughout fresh RUN samples.
Inspection of the pinned engine explains a concrete missing input: `PlayerVariables.set`
sends only IdleSpeed while the authoritative Actor remains in its idle action state.
The stock running animation blends from walking at WalkSpeed zero to running at one.
Movement flags and physical translation alone therefore do not establish running
presentation. The adapter now explicitly fills stock WalkInjury/WalkSpeed fields while
moving, using cached reflection guarded by exact class hashes. Stock serialization and
ordinary Lua clients are retained; no installed game files or engine classes are replaced.
Headless checks serialize, parse and apply real stock fields for WALK/RUN and an injury
blend, then verify the stationary payload. Visible running is still unaccepted.

Routine/flee replanning stages a continuation from the reachable endpoint without
destroying the active movement port, frame clock or gait. Combined committed and staged
edges remain bounded to eight. A delayed or invalid candidate cannot restart an expired
route. Soft cancellation discards obsolete candidates and finishes the current safe edge;
it does not force an extra tile after reaching its boundary. A replacement can join there
without publishing an intermediate idle state. Collision/unsafe routes, unload, death
and explicit hard cancellation stop immediately. Native hit reactions pause movement
and reset its time allowance while retaining the logical goal. Never force movement
through native locks merely to satisfy a planner request.

The existing C++ channel now receives active execution snapshots once per second per
admitted resident, including action/status, route revision, completed edges and buffered
edges. Completed edges are cumulative for that controller assignment, not per action.
Explicit active/paused/blocked/completed snapshots produce no replacement action list;
`needs_plan` permits a new proposal. Legacy empty execution status keeps its old behavior.
The game owns goal progress and physical effects; C++ does not become a second executor.

Terminal completed/cancelled/failed outcomes reserve capacity before routine execution.
A 64-entry session journal retains them until the existing receipt acknowledgment arrives;
reconnect retries are harmless. At most four receipts precede a waiting progress sample.
Backpressure pauses new routine admission, never local flee/defense or physical cleanup.
Lua keeps pending outcomes until the bridge can take them. This is reconnect delivery
within a server session, not a crash-durable transaction log; after process restart,
server-owned resident facts remain the source of truth. Physical ownership is independent
of delivery acknowledgment. No worker outage may free an uncertain Actor.

Diagnostics now expose packet fields, server walk modifier, client action state,
perception age/rejection and execution-window progress. These diagnose the remaining
visual/perception questions; they do not change native threat visibility rules.

## WALK/RUN speed calibration (2026-09-28)

Running animation is now human-confirmed, but the latest watched result rejected speed
and smoothness. The old server formula treated a blend coordinate as a linear speed
modifier. NativeGaitSpeeds now caches rates from the installed default WALK/RUN root
translation clips and animation node/MotionScale settings, using the pinned native blend
picker. Healthy default commanded rates are approximately 2.18 and 4.22 tiles/second.
No proprietary animation assets are packaged. Injury blending remains native; weapon,
terrain-specific and transition animation profiles still need separate qualification.

Stock prediction already transports speed in tiles/second. The fix aligns its speed
with authoritative movement and extends prediction along verified collinear buffered
edges (maximum 0.6 seconds), stopping before turns, doors, obstacles and a deferred
cancellation boundary. It adds no secondary network channel. Visual smoothness remains
pending despite numerical/native tests.

The dedicated two-Actor STRIDE_COMPARE case runs parallel 50-tile tracks with matched
healthy profiles and no zombies, records independent finish times and requires a RUN/WALK
speed ratio of at least 1.7. Native collision and ownership cleanup remain mandatory.

## Obstacle replication and startup follow-up (2026-09-28)

Straight 50-tile movement was accepted in one client. The subsequent four-civilian
encounter was **visually rejected**: door bumps followed by apparent wall teleportation.
Fresh captured positions diverged by up to 6.34 tiles (not latency-adjusted). A successful
server path and cleanup do not establish faithful client traversal.

Pinned native inspection confirms remote players can teleport to their authoritative
position after a failed native path request. This identifies a possible correction
mechanism, not proof that this branch executed in the reported frame. The artificial
exit removed nine doors without waiting for client geometry; delayed removal and corner
prediction are hypotheses, not a confirmed sole cause.

Candidate corrections: movement blends in over the installed default WALK/RUN transition
durations (0.20/0.35 seconds); RUN's WalkSpeed blend progresses from the native walking
clip to cruise. Stops/reactions reset the envelope. Nearby corners/door actions request
WALK. Gait/heading changes bypass the ordinary packet throttle; prediction distance is
quantized downward so encoding cannot extend a requested segment. Native collision is
unchanged; no client agent, forced transform stream or teleport workaround is introduced.

The private watched fixture holds movement during exit removal, requires fresh client
confirmation that all nine exit squares are loaded and contain no west-edge doors, and
fails after five seconds without that confirmation. This is fixture synchronization,
not client gameplay authority. It is bounded to four Actors/nine squares each. A separate
watched acceptance gate rejects a reported gap over three tiles for 750 ms; samples older
than 500 ms or absent Actors reset that check rather than treating missing data as zero.
Peak gap is recorded additively in the runtime protobuf. Thresholds are conservative LAN
test checks, not latency-compensated multiplayer qualification.

Do not mark these corrections visually accepted until a new installed-build watched
run passes both telemetry and the user's observation. The old live test remains on the
previous build while native/component validation runs.
