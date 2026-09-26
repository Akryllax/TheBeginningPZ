---
type: decision
status: superseded-in-part
date: 2026-09-26
---

# 0001 — An ongoing living world

The opening/runtime scope below was corrected by [[Design/First Week]] and
[[Decisions/0004 Server Runtime Extensions]]. An observation-only ongoing world is not the
accepted substitute for the civilian opening and manual seven-day outbreak. The adaptive
living-world behavior remains a later phase of that scenario.

## Context

The original request was a fresh Day One-style world beside the existing game. Discussion selected a RimWorld-like coordinator that reacts to increasing comfort and lets the group recover after setbacks.

## Decision

Build the original companion `LofersStoryteller`, using Bandits NPC as the intended NPC engine. Use outbreak, aftermath and survival phases with adaptive, bounded encounters. Treat Week One/Day One implementations as research, not as the selected campaign runtime.

Retain current XP, reading, crop growth and day length settings. Permit raids/theft/limited sabotage once individually validated; exclude large fires, nuclear effects and forced character losses. Base inference combines spatial usage and resources, with optimization as a requirement.

## Consequences

The project needs its own scheduling, recovery, persistence, profiling and integration tests. Some client companion code may be required by the engine; the server-only Observer exporter does not establish that NPC gameplay can be entirely server-only. Distribution remains a private original-mod ZIP.

See [[Design/Storyteller]], [[Design/Event Catalog]] and [[Runbooks/Private Mod Distribution]].
