---
type: task
status: planned
created: 2026-10-02
title: Execute native path traversal actions safely
---

# Native path action executors

The house-routine work now asks the pinned 42.21 engine for routes. A returned route
is a proposal, not proof that a connectionless Actor can execute every transition.
The user explicitly requires unsupported routes to remain rejected until the missing
functionality is implemented and validated.

## Current boundary

`NativeCivilianGeometry.executableActions()` permits WALK and DOOR only. Keep that
allowlist. A rejected native route may trigger a bounded walk/door search; it must
never silently turn a climbing edge into walking or teleport an Actor across it.
The real-house probe found a native WALK/DOOR/LOW_FENCE route to a nearby log container.
That route remains rejected. Fixture selection may choose a different, reachable
container access square; this does not qualify fence traversal.

## Pending executors

| Native classification | Required execution and evidence |
| --- | --- |
| LOW_FENCE | Native approach, hop animation, collision transition and landing; interrupt/damage while hopping; ordinary-client replication. |
| WINDOW | Open/unlock or safe climb-through eligibility; intact/broken glass policy; sill crossing, animation and landing; barricade rejection. Do not silently smash glass. |
| HIGH_WALL | Verify actual climbability rather than treating every wall as climbable; native climb state, endurance/failure, landing and replication. |
| STAIRS | Preserve native floor transitions and z values; ascending/descending animation, collision, endpoint and streamed-floor validity. Local graph capture must not invent stair edges. |

For each: inspect the exact pinned engine methods and state transitions, implement a
small executor behind the existing Actor movement port, and add deterministic contract
and cancellation tests. Then run a native fixture, one ordinary-client watched case,
reuse after interruption, and eventually two-client comparison. Change the allowlist
only after the relevant executor is proven; headless success is not visual acceptance.

## Shared requirements

- Keep the server-owned goal and generation-bound action receipts. Engine state owns
  the actual movement constraints and safe interruption boundaries.
- Preserve collision and native networking; no core-file patches, client Java agents,
  position snapping across barriers, or emulated successful completion.
- Measure bounded game-thread work, queue latency, stuck states and route revisions.
- Record rejected action kinds and reasons even when a walk/door alternative succeeds.
- Test closed/locked/barricaded doors and blocked landings, moving obstacles, lost
  streaming, damage during transition, death and reuse without stale callbacks.
- Ambiguous native state stays owned and blocks reuse. Do not mark a route complete
  merely because its destination is nearby.

Start with LOW_FENCE because the current house probe provides a concrete native route.
Window and stair fixtures follow. High walls require an explicit climbability audit.
This task does not block the current ground-floor walk/door/collection routine.
