---
type: design
status: target-not-measured
updated: 2026-09-26
---

# Performance budget

Optimization is a product requirement: the director must not introduce the stutter it is meant to make more interesting. The numbers below are proposed acceptance targets until the [[Experiments/Implementation Ledger]] links actual server measurements.

| Work | Initial target or bound |
| --- | --- |
| One incremental observation/update slice | 1 ms target plus a strict operation cap |
| Measured slice latency | p95 below 2 ms, p99 below 5 ms |
| Encounter NPCs | At most 8 per encounter, 24 globally |
| Major hostile event | At most 1 active |
| Telemetry queue | Latest detached sample only; bounded size |
| Spatial storage | Sparse cells with explicit cell/queue caps and eviction policy |
| Missing data | Retain age/confidence; avoid rescan storms |

Do not scan the world, every inventory, or every object on every tick. Work queues must resume after a cap and deduplicate repeated dirties. A large container must not defeat the budget through one unbounded nested traversal.

## Measurement protocol

Use identical game builds, mod manifests and saves when comparing runs. Record host load, whether the local client is running, JVM/container limits, player count and encounter size. Warm the test, then retain a sufficiently long sample window to include expensive maintenance, saves and region transitions.

Record sample count, p50/p95/p99/max update time, operations per slice, queue length/oldest age, observed cells, item visits, deferred work, GC behavior and process/container memory. Distinguish CPU time from elapsed time. A lower mean with an extreme maximum is not proof that stutter improved.

Scenarios should include idle/no players, one roaming player, a dense inhabited base, nested/transferred storage, two players far apart, a maximum encounter, a save/restart, and an unavailable Observer. Compare with the director disabled or observation-only to isolate event costs.

A failed target should first reduce/split work or lower sampling rates. Raising limits needs evidence. A timing test under mocked Lua verifies logic but does not establish in-game latency.

Use [[Templates/Experiment]] and link results from [[Experiments/Implementation Ledger]].
