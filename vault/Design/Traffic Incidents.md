---
type: design
status: accepted-implementation-in-progress
updated: 2026-09-27
---

# Traffic incidents

Accepted user addition: the beginning of the epidemic should include crashes happening
in the world, not only pre-placed wrecks. Some impacts are visible at a distance; others
are heard off-screen and leave discoverable aftermath at the sound's location. Detailed
seated-driver rendering is secondary. This adds to [[Implementation Roadmap]], not a
replacement for resident continuity or ordinary collision avoidance.

## Ordinary traffic and deliberate incidents

Normal drivers detect static road blockers, moving actors and vehicles along their
predicted motion/stopping corridor. Slow, wait or brake locally; request a replan only
when stopped and a bounded closure observation is available. A Bézier path provides
geometry, tangent and curvature; it does not establish clearance or right of way.

### Blockages, personality and passing

Accepted 2026-09-27: a blocked driver waits, may honk after several seconds, then looks
for a safe way around. Drivers differ, and avoidance must coexist with imperfect physical
driving and occasional crashes. Do not make every car honk together, pick a new personality
each frame, or repeatedly alternate between the same conflicting paths.

Use a stable resident seed for patience, horn tendency/delay/duration, stopped spacing and
replan interval. An obstruction episode chooses whether to honk once. Count patience while
actually waiting, not while approaching. Retain the car and its route while blocked; removal
of the obstruction resumes the original trip. Horns use the stock server vehicle sound
state, including its normal world-sound consequences and native replication.

Sequence: `approaching -> waiting -> seeking_bypass -> yielding/reserved -> passing ->
rejoining`. The high-level service can choose a different road when a persistent closure
makes a local maneuver impossible. The server must retain immediate braking without IPC.
For a local pass, consider at most two bounded candidates on known drivable road, including
a short use of the opposite lane only when the full maneuver, oncoming gap and rejoin are
freshly verified. No sidewalk/grass shortcut, blind junction entry or reversing near a
player. Check the swept oriented vehicle footprint, steering/turn radius and braking
capability; the current conservative circular forecast is insufficient to approve
side-by-side passing in adjacent lanes. A route proposal alone cannot execute a pass.

Reserve the maneuver's complete inflated swept corridor, including entry and rejoin, on
the authoritative server. Opposing/overlapping requests are decided in one bounded batch:
oldest waiter first, stable resident ID for ties. One winner proceeds; the other waits
outside the shared corridor. Keep the winner until it clears rather than swapping priority
each tick. Replans use each driver's staggered interval. Admission and execution both
recheck native ownership, actors and live oncoming traffic; a reservation is not a promise
that the space will stay clear.

An unused reservation can expire. An entered reservation whose heartbeat expires remains
occupied and commands a stop until fresh ownership/clearance permits reconciliation or
confirmed physical exit releases it. Never grant the same gap just because its previous
occupant became stale. Bound this table by the four-moving-car fleet cap. A genuine
stand-off requests a different road or stays queued; it must not oscillate or teleport.

Personality affects ordinary timing and preferred gaps within the vehicle's physical
limits. Add bounded perception/reaction and tracking error only after the baseline passes
moving-traffic tests. Keep continuous native physics, so sudden hazards and imperfect
execution can still produce contact; do not add ghosting, invulnerability or position
snaps. Higher-risk staged mistakes remain named incident actions under the all-player
exclusion rules below. Do not randomly disable the safety scan to manufacture crashes.

Current step: gradual parked-car approach, persistent wait/resume and temperament-based
honking are connected to the disposable server probe. Bounded bypass requests are emitted
after patience expires. `TrafficPassReservations` has detached conflict, stale-occupancy,
priority and generation tests; it is **not yet connected to a managed multi-car executor**.
Live bypass execution stays disabled until a candidate's full road footprint and dynamic
clearance are verified. Required follow-up evidence: stationary bypass, opposing contenders,
new obstacle during a pass, blocked rejoin, stale-owner recovery, native contact and two
ordinary clients. None of those are established by a single-car stopping test.

An incident is a separately authorized server-owned action with named participants,
reserved route/impact/aftermath areas and a stable event ID. Never switch off collision
avoidance globally. Only the selected scenario-owned pair may collide intentionally;
players, player vehicles/property, unrelated NPCs and unknown occupants remain blockers.
The first physical experiment is a low-energy two-car contact in an empty disposable
world. Native contact, braking, damage, sound, replication and cleanup need distinct
receipts; a proximity calculation is not proof of an actual crash.

## Placement and player exclusion

Stage participants outside every player's sight, then let existing cars enter the
visible scene. The intended impact may be visible, while creation may not. Verify real
visibility and loaded terrain; exploration fog and distance are insufficient. Prefer
roads already populated by the simulation. Do not create cars directly in view.

Reserve the complete approach, collision and possible run-out envelope, with an extra
buffer. Check all players, their vehicles, nearby protected property and uncertain actor
ownership. Include current movement and observation age in a conservative reachable
area. Recheck before activation and continuously while approaching impact. A player who
could enter the envelope before the cars can stop cancels the collision and causes local
braking. New arrivals, reconnects, stale observations, ownership changes, unexpected
blockers or insufficient work budget also cancel it. Never claim absolute protection
from arbitrary teleports or unbounded speed/network error; do not enable the event until
physical separation and interruption tests meet the accepted player-exclusion requirement.

After impact, retain the guard until participants and debris are settled. No explosions,
large fires, launched vehicles, high-speed pile-ups or induced damage to player property.
Initial scope is two scenario cars. Event participants count against the same fleet/NPC
limits as other traffic; they are not extra unbudgeted entities.

## Visible and off-screen execution

Visible: real native vehicle bodies follow their approach, make confirmed contact and
settle. A staged controller error is permitted only for the named participants and only
while exclusion remains valid. Replicate native transforms; never teleport between
pre-crash and wreck poses while observed. A persistent resident remains the same resident
through injury, exit, infection and subsequent response.

Off-screen: reserve one plausible road location and persist the incident before emitting
the spatial sound once to eligible nearby listeners. Resolve a bounded abstract outcome
when full physics is unnecessary. Materialize its reserved aftermath only while the
location is unobserved, before players can arrive; if that cannot be guaranteed, postpone
the sound/event. Store wreck IDs, poses, condition, occupants/outcomes and generated loot
once. Reconnect, chunk loading and re-observation cannot replay audio or duplicate loot.
No sound from an arbitrary point followed by unrelated randomly placed wrecks.

The first implementation will materialize and save the hidden aftermath **before** releasing
its sound. Players discover it later, but it is already present if they investigate at once.
Deferred creation in unloaded chunks is outside that initial adapter. Sound delivery is
at-most-once: after an interrupted dispatch, suppress replay even if that means an unheard
sound. Do not claim exactly-once delivery across a process crash.

## Lifecycle, pacing and recovery

`proposed -> reserved -> staged -> active -> impact_confirmed/abstract_resolved -> settling
-> aftermath` records contain event/world IDs, phase/seed, location, participant IDs,
source observations, sound receipt, actual outcome and materialization revision. Each
transition is idempotent. Cancellation before impact preserves or parks the owned cars;
uncertain contact/save outcomes remain unresolved until reconciliation. Never delete a
wreck, move a car or overwrite a container being observed or used by a player.

Use sparse, cause-driven early-epidemic incidents (panic, illness, blocked roads), with
shared per-group/area cooldowns and a cap of one active staged crash. Pause scheduling
when empty; no reconnect backlog. Later sirens, responders, road closures and residents'
memories may follow the same incident, within shared budgets. Collisions are world-building
events rather than attacks chosen to punish a nearby player.

## Evidence gates

1. Braking-distance and moving-blocker forecasts, including stale/unknown observations.
2. Native two-car contact and settled damage/audio in an empty disposable world.
3. Cancellation for an approaching player or unrelated car, before and during staging.
4. One-client distant visible crash; sound attenuation, occlusion and believable aftermath.
5. Two clients see the same outcome; disconnect, restart and early approach cannot duplicate
   sounds, wrecks, occupants or loot. Profile the combined physics/control/replication cost.

## Current implementation boundary

`shared/LofersScenario/TrafficIncidents.lua` implements a bounded server state model under
the scenario's ModData state: stable event/wreck IDs, copied positions/part conditions/loot,
one active incident, two-car reservations against the shared four-moving-car budget,
one scenario-hour cooldown and 64 retained incident records. Scheduling eligibility requires
an active early-outbreak phase, online observers and fresh placement/exclusion evidence.
It is initialized on restore, but has no automatic proposal scheduler or native effect adapter.

Materialization and sound use single-use intent tokens. An adapter must durably checkpoint
the exact intent revision before release, revalidate placement, then return a matching world
receipt. Assigning a Lua table does not supply that durable checkpoint. Saved-aftermath
receipts gate sound; stale/duplicate/wrong-world receipts fail. Interrupted creation stays
unresolved and holds capacity; interrupted audio is marked spent. Never retry unknown world
effects or regenerate loot blindly. The durable checkpoint writer, native tagged-object
reconciliation and physical creation/audio adapters remain to be implemented and verified.

`TrafficIncidentSafety` supplies detached all-player reachable-area admission checks, and
`CollisionForecast` provides continuous contact prediction. These tests are not native
crashes or visibility proofs. Corrected Bézier driving has passed a one-client smooth-turn
check at the initial low speed and a subsequent faster capability-aware run. Native traces
confirmed a conservative parked-car stop, but the user rejected both the early abrupt stop
and disappearance even with the 20-second hold. Revised planned stopping and wait/resume
have separate receipts; visual braking remains pending alongside moving blockers.
No physical crash director/event is enabled. Record new receipts in
[[Experiments/Implementation Ledger]].
