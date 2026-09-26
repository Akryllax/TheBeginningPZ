---
name: dayone-storyteller
description: Develop LofersStoryteller event scheduling, server-owned state, adaptive pressure, recovery and encounter lifecycle for the DayOne test world.
---

# Storyteller development

Read `vault/Design/Storyteller.md`, `vault/Design/Event Catalog.md` and the current `vault/Design/Storyteller Implementation.md`. These distinguish the accepted design from executable events.

Keep event choice, random state, event IDs, budgets and persistent outcomes authoritative on the server. Clients provide presentation and only the NPC-engine interactions demonstrated necessary by source inspection and multiplayer tests. Observer receives observations; it never chooses or starts encounters.

For an event, make eligibility, placement, resource reservation, activation, completion, failure and cleanup explicit. Persist enough state to avoid duplicates after a restart. Release reservations on failure and retain evidence of why a proposal was rejected. Keep unknown NPC ownership and uncertain cleanup as unresolved state rather than pretending the encounter ended.

Use game time for pacing and wall-clock time for work measurement. Pause scheduling when nobody is online; resuming must not emit a backlog of missed hostile events. Recovery after death or confirmed substantial loss takes precedence over pressure. Do not treat an unloaded base or missing inventory sample as a loss.

The First Week limits are 24 pedestrians, four moving NPC vehicles and 32 materialized residents including occupants, shared globally. Drivers are residents, not additional duplicate characters. Later hostile encounters share the population budget; retain one major event, eight encounter participants, six game hours between major hostile events and 24 hours of recovery. No large fires, nuclear events or forced character loss.

Test lifecycle invariants with a deterministic clock, seeded randomness and mocked adapters. Real Bandits ownership, effects and persistence still require the two-client gate. Implement and test executable civilian behavior in isolated worlds before rollout; observation mode is a diagnostic mode, not the accepted deliverable. Read `vault/Design/First Week.md` for the manual outbreak clock and civilian-to-survival transition.
