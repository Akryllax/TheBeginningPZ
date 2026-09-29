---
type: design
status: accepted-pedestrian-feasibility-first
updated: 2026-09-27
---

# NPC first slice

## Latest iteration — offline preparation

The user deferred native functional/visual checks and selected idle/roam behavior with
full native traversal as the goal. Follow [[Civilian Pool and Navigation]] for the
implemented pool, Lua FSM, bounded A*, native adapters and explicit stair/climb blockers.
The connectionless `IsoPlayer` Actor and stock client-owned hunter decision supersedes
the older Bandits/server-zombie passages below. Gate 3 remains reopened. No offline
result requalifies it or the native four-Actor/two-client gates.

The user accepted this ordering after the [[Reviews/2026-09-27 Project Review]]:
minimum reusable controls and measurements → one/four server-controlled pedestrians →
complete runtime and modular split → living neighborhood/outbreak → crowd qualification.
This supersedes completing hot reload before the pedestrian experiment. Server pedestrian
simulation is a hypothesis until native and client evidence establishes it.

## Requirement revision — 2026-09-27

Zombies must natively target healthy civilians. Disguised `IsoZombie` civilians cannot be
targeted or bitten through stock engine paths, and they trigger type-based player fear.
They are retired as the civilian entity. The proposed replacement and its spike gates are in
[[Decisions/0006 Targetable Civilians]]: server-hosted connectionless `IsoPlayer` civilians,
engaged by bounded server-simulated zombies. Bandits-based spawning and presentation below
applies only until gate 1 settles the entity. Measurement targets and one/four/two-client
gates are unchanged.

## Experiment and stop gate

Use typed submit/status/cancel, bounded FIFO, world/epoch/request identities, deduplicated
submissions and explicit resources. Repeated cases and parameter changes use the same
process; changing engine hooks can still require a restart. Adopt AKRCore's permanent
dispatcher. Unknown cleanup retains capacity and stops subsequent execution.

Test one civilian walking, turning, stopping and respecting a blocked passage, then four
with intersecting routes. Check human presentation, semantic actions, injury/death,
cancel/repeat, reconnect and streaming transitions. Compare two ordinary Lua clients.
Use guarded server character simulation and native zombie replication, never vehicle
packets, teleport-driven walking or a client Java agent. If movement, authority, collision,
replication or cleanup cannot be established without replacing substantial simulation,
stop and document the blocker before building routines/crowds. No silent client-owner fallback.

Bandits2 remains an upstream dependency for spawning, appearance and animation. Gate all
managed-actor autonomous behavior and gameplay effects, including effects on a managed
target from another actor's callback. Reliable isolation is an acceptance requirement.

## Population and behavior

AKRCore owns contracts only. AKRPopulation owns shared admission, physical bindings,
merged interest areas and unresolved ownership. AKRResidents contains persistent residents
and a separate cheap crowd controller; both use Population. Rich planning is optional:
server Lua fallback preserves server authority when the C++ worker is absent.

Persistent locals and meaningful contacts retain identities/history. Transient extras
walk, watch, panic, flee, suffer injury and die. Unobserved regions use summaries, not
invisible live actors. Identity retention and behavior complexity are independent.
Promotion keeps the same body/identity; never delete visible actors, erase visible corpses
or duplicate inventory during transitions. Residents, extras and managed scene zombies
share admission. Initially admit at most eight rich planners, separately from physical actors.

After feasibility, complete [[Live Event Runtime]]: durable interruption/reconciliation,
operator and Lua API, Java/Lua behavior reload between cleaned events, and repeatable batches.
Keep car behavior as regression coverage. Opposing cars and further traffic research are
deferred, not prerequisites for the pedestrian slice.

## Measurements and qualification

Build server timing, spawn/cleanup measurements and path queue age/depth into the experiment.
Add bounded ordinary-client position/action samples, clock alignment, network conditions
and frame-interval measurements (not GPU time). Measure instrumentation overhead.
Targets remain added work p95 <2 ms / p99 <5 ms; aligned walking difference ≤1 tile p95
and action agreement ≤500 ms p95 at RTT ≤150 ms and loss ≤1%. Unmeasured is pending.

Run early one/four-actor functional checks, including two-client correctness. Screen
8/16/32 with short automated runs; attempt 48/64 only after lower levels pass. Qualify
the selected release cap with warm-up, ≥10 minutes steady measurements, clients together
and apart, and a one-hour soak including a save. The initial target is 32, not a guarantee.
Keep runtime's separate 100-case/20-reload qualification after reload is implemented.
Exercise panic bursts, narrow exits, mixed zombies, deaths, reconnects and worker outages.
Reject sustained new stutter, growing queues, ownership errors and resources retained
after supposedly successful cleanup. Admission stops before visible simulation degrades.

## Story and deployment

Calm neighborhood → manual 168-game-hour outbreak → regional delayed spread → survivors.
Empty worlds pause progression. Persist regional onset/outcomes independently of visitation
eviction. Stage arrivals outside every player's view; interactions interrupt scenes under
ordinary world rules. Fleeing buys time; background summaries reduce later civilian arrivals.
Later representative encounters are hostile confrontation, assistance and neutral scavenging.
Full factions, recruitment and dialogue systems are deferred.

Use only disposable `.132:16281/16282` with loopback RCON `27035`. Normal `.132` and `.160`
remain unchanged. Evidence belongs in [[Experiments/Implementation Ledger]], current source
and deployment status in [[Experiments/Current State]]. This design is not gameplay evidence.
