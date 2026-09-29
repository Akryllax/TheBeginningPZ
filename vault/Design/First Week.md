---
type: design
status: accepted-implementation-in-progress
updated: 2026-09-26
---

# First Week

## Accepted priority revision — 2026-09-27

Follow [[Design/NPC First Slice]]: minimum reusable controls and measurements, then the one/four
server-pedestrian feasibility gate **before** full reload/soak. Bandits2 remains the
initial spawn/presentation dependency. Shared accounting belongs to AKRPopulation;
AKRResidents contains both resident and cheap crowd controllers. Moving traffic and
commute/driving acceptance below are deferred beyond the first pedestrian slice.
Server simulation is not yet validated. Planner absence will use server Lua fallback.
The prior detailed roadmap below is retained as background, subordinate to this revision.


Civilian entity revision (2026-09-27): zombies must natively target healthy civilians, so
Bandits-spawned disguised zombies are no longer the planned civilian entity. See
[[Decisions/0006 Targetable Civilians]] for the proposed server-hosted player civilians.

Implementation note: the user selected **server-only JVM injection with ordinary Lua clients**.
The initial client-agent prototype is superseded; it was never installed or launched in the
local game. Pedestrian execution has been adapted to Lua, with gameplay validation pending.
A separate server-only vehicle probe passed bounded straight-line native movement and
cleanup; one ordinary client reported smooth movement. A later server-only asphalt turn
also passed. Integrated NPC driving and two-client comparison remain unvalidated.
The next implementation sequence is [[Implementation Roadmap]]. The accepted initial
driver presentation is a simple original seated model attached to the native car, with
the same persistent resident restored on exit; exact seated clothing/animation is deferred.
The playable reset still requires actual scenario and multiplayer acceptance.

The user explicitly requires behavior injection without modifying installed core game files,
so future distribution contains original Lua/JAR code and notices only. Class-replacement
overlay JARs and redistributed decompiled engine classes are excluded. A newly identified
research route uses server-owned vehicle simulation and the existing vehicle stream with
ordinary Lua clients. Physics registration/cleanup have been demonstrated in an isolated
empty-car test. Normal-world integration, collision ownership and NPC occupant representation
remain unproven; this is not yet a complete driving implementation.

The group starts together in a living Muldraugh. Residents have persistent homes, occupations,
needs, possessions and histories. They walk or drive to work/shops, eat, rest and return home.
Police and medical responders travel to incidents. Dialogue, trade and recruitment menus
are excluded. The initial release includes real civilian and emergency vehicle trips.

The calm world runs until `akr` starts the scenario through an in-game admin panel. A separate
persistent clock then progresses over seven game days: initial cases on day 1, transmission
and emergency response on days 2–3, shortages/evacuation/service failures on days 4–5, and
collapse on days 6–7. Afterwards residents use survival goals and the adaptive director.
Player Knox immunity, 10× XP and rapid reading remain. Empty servers pause progression.

The current vanilla-like prototype is archived before a fresh world/character reset;
accounts and credentials remain. `akr` receives full admin access. `.160` stays separate.

## Implementation contract

A C++20 service with Lua behavior packages computes bounded GOAP-lite plans, road routes and
coarse town outcomes. Initial pool: two computation workers, two CPU capacity, 512 MiB.
Use private Lua states with instruction/allocation limits and deterministic request state.
Exchange typed Protobuf frames over a private Unix socket; the game remains the sole durable
authority and applies effects on its game thread. Observer is independent/read-only.

Initial planner bounds are six actions, 64 expansions, 64 admitted jobs and one pending
request per resident. Physical population caps are 24 pedestrians, four moving vehicles and
32 residents including occupants, shared across player areas. Abstract residents retain
identity and history. Observed entities move continuously; nearby work is staggered; unseen
town outcomes may be batched. Visibility considers all clients, not exploration fog.
No fabricated event silently consumes player storage or destroys player property.

Use a separate guarded server Java bridge and version-checked ordinary Lua client integration.
All native autonomous spawns must respect the calm opening while explicit civilian creation
remains available. Build guards must reject incompatible integrations instead of silently
turning the intended opening into a vanilla zombie world.

## Replication requirement

One accepted resident identity/state, one native-owner-confirmed execution lease, and a
replicated semantic action. Other clients present native replicated motion and the shared
action without independently performing inventory, damage or infection effects. Gate the
managed Bandits callback/effect boundary, not merely the custom program. Vehicles use one
native physics authority and the same persistent resident when entering/exiting.

Targets for compatible clients at RTT ≤150 ms and loss ≤1%: action agreement within 500 ms
p95 and walking position agreement within one tile p95 using aligned samples. Vehicle drift
is assessed against speed/interpolation; routes, turns and occupancy must agree. Handoff,
reconnect and packet loss must converge without repeated effects. These are acceptance
targets, not current measured guarantees. Exact gait frames are not required.

## Admin and evidence

The panel exposes start/pause/resume, phase/day, service/replication health, resident inspection
and explicit debug controls for stepping, phase changes and owned test entities. All
administration is authenticated server-side and logged. IPC outage preserves immediate
reactions/braking and stops new decisions/materializations until reconciliation.

Acceptance includes a calm opening in new chunks/buildings/basements, one complete home/work
routine, real civilian/emergency driving, two-client shared actions/effects, owner changes,
unload/reload, infection/conversion, save/restart and worker failure. Profile server and both
clients at the configured caps; game-thread target p95 <2 ms and p99 <5 ms. A working daemon
or server boot alone does not complete the scenario.

See [[Experiments/Implementation Ledger]], [[Research/Bandits Compatibility]],
[[Runbooks/Multiplayer Validation]] and [[Design/Performance Budget]].
