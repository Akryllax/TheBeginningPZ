---
name: dayone-spatial-profiling
description: Implement and profile bounded sparse spatial fields, inventory and infrastructure observations, and base inference for LofersStoryteller.
---

# Spatial inference and profiling

Read `vault/Design/Spatial Field.md` and `vault/Design/Performance Budget.md`. This is a sparse inference system, not a full world inventory index.

- Key 32×32 tile cells by world X/Y and floor. Use floor division for negative coordinates and preserve floor identity when aggregating a base.
- Combine repeated dwelling/revisits with storage, infrastructure, defense and vehicle signals. A brief visit, dropped bag or dense loot shop alone should not establish a player base.
- Observe loaded, relevant areas incrementally with dirty queues and resumable cursors. Apply both an operation cap and a time budget; bound cell, item and queue counts.
- Deduplicate nested inventory contents and transfers. Inspect container identities and observation generations before counting contents twice. Skip unsupported observations with a confidence reason.
- Track freshness and confidence separately from value. Missing or unloaded observations do not erase stock or trigger loss recovery. Decay lazily when a cell is read or maintained.
- Profile empty areas, a crowded base, nested storage, player travel and NPC encounters. Record sample count, p50/p95/p99/max, queue size, cache size and deferred work; report the host/build used.

The proposed slice budget is 1 ms with measured targets below 2 ms p95 and 5 ms p99. Treat these as targets until profiled on the actual server. Prefer smaller resumable work to increasing the budget. Use Observer's local debug view to explain scores and staleness without exposing hidden stock on the public map.
