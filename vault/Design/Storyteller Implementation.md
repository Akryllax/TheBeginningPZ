---
type: design
status: implemented-observation-only
date: 2026-09-26
tags: [storyteller, spatial, telemetry, implementation]
---

# Storyteller implementation

`mods/LofersStoryteller` version **0.1.0** contains original Lua code for a server-owned observer and event-decision simulator. Its defaults do not create encounters, alter inventories, grant XP or damage the world. Native Bandits schedules are suppressed so observation mode is meaningful.

Related: [[Bandits Compatibility]], [[Home]], [[Operations]].

## Executable components

| File | Responsibility |
| --- | --- |
| `shared/LofersStoryteller/Config.lua` | Limits, timing, balancing defaults and explicit activation gate |
| `shared/LofersStoryteller/Core.lua` | Persistent sparse field, deduplication, scoring, deterministic decisions and event reservations |
| `server/LofersStoryteller/Scanner.lua` | Incremental nested-container traversal with per-update operation/deadline caps |
| `server/LofersStoryteller/Server.lua` | Dedicated-server hooks, local loaded-square cursor, health/presence observations and telemetry snapshots |
| `server/LofersStoryteller/Bandits.lua` | Bounded native-scheduler suppression through the current source-verified accessor |
| `client/LofersStoryteller/WanderingGuard.lua` | Excludes Bandit-flagged NPCs from Wandering Zombies' steering predicate |

There is no custom client-to-server command handler, HTTP call, file poll or per-tick world scan in the companion.

## Sparse field and evidence

Cell keys contain `floor(x/32):floor(y/32):floor(z)`. Negative coordinates and different floors stay distinct. The field holds at most **512 cells**, with bounded ring eviction and monotonically increasing cell generations so old item sightings cannot subtract a later reincarnation of a cell.

Every five real seconds, up to four online players contribute capped dwell, transitions/revisits and health observations. Offline gaps cannot create more than five game minutes of dwell on the next observation. Disconnected players leave the small runtime presence registry.

An incremental cursor samples loaded squares in a **25×25** area around one online player at a time, on that player's floor. It visits one square, object or object container per operation. Observed generators, barricades and occupied vehicles provide infrastructure/defense/vehicle evidence. This is a local sample, not a census of all player property. Objects seen in ordinary buildings are not automatically considered owned: base confidence also needs repeated player activity.

Inventory jobs traverse nested bags to depth **8**, at most **4096 item steps per job**, with **16 queued jobs**. An **8192-item** ledger deduplicates IDs across repeated, nested and transferred sightings. An item observed in a new cell is removed from its prior cell's contribution. Category values are capped and square-root compressed before contributing wealth. Signal identities have a separate **2048-entry** cap.

Missing, unloaded, truncated or failed scans do not imply an empty inventory or stolen property. All stock observations are expressly incomplete estimates. Stale item sightings can remain until transferred/replaced/evicted; each sighting's contribution ages independently. Seeing a fresh item or generator does not refresh a different missing item or generator. No automatic confirmed-loss inference is enabled from partial storage data. The generic `Core.loss` hook exists for future verified loss receipts and current death observations.

Dwell and observation freshness use a lazy **72 game-hour half-life**. Base confidence combines dwell, visits, infrastructure, defenses and stock evidence. Wealth pressure uses freshness and confidence rather than unqualified totals. Player capability currently uses online player count and body health; equipment/skill capability weighting remains future work.

## Decision model and safety state

The world phase uses elapsed hours since initialization: outbreak for days 0–7, aftermath for 8–30, survival from day 31. Weighted candidate categories are radio bulletin, distant fighting, fleeing group, scavenger patrol, looters and limited sabotage. These are **decision candidates**, not shipped active encounter implementations.

The server persists a Park–Miller random stream, increasing event IDs, pressure, budget, cooldown and recovery state. Pressure smooths wealth, online capability and world age. Budget accrues only while somebody is online; a large clock jump is capped to one game minute of accrual. Offline time advances the next decision time, preventing a reconnect burst.

Eligibility enforces a one-game-day hostile grace period, six-game-hour hostile cooldown, 24-hour recovery after observed death/large health loss, one major hostile encounter, eight NPCs per encounter and 24 overall. Unknown live NPC count blocks NPC candidates. Recovery and a low pressure score do not block harmless candidate previews.

Observation mode records a preview or a specific blocked reason without invoking an adapter or spending an encounter budget. In active mode an absent adapter remains blocked; setting a string to `active` does **not** create a working NPC integration. NPC adapters additionally require `npcIntegrationValidated=true`.

A future adapter is invoked only after a persistent event reservation exists. Errors leave an uncertain reservation, preventing blind retry or duplicate creation. Returning a clean refusal cancels the reservation. Expiry alone does not free the slot; a verified completion/cleanup receipt must call `Core.complete`. At most eight unresolved reservations can exist.

## Runtime budget and telemetry contract

The update loop runs at most once per **100ms**, aims for **1ms** of work and caps the scan at **32 operations**. `GameTime.getServerTime()` is monotonic `System.nanoTime()` in this dedicated-server build (verified with `javap`); timing uses its value in milliseconds. World and inventory operations alternate to avoid a large bag monopolizing scanning. Presence, bounded context scoring and snapshot publication add small bounded work around that scan; the deadline is a cooperative limit, not a real-time guarantee against a slow Java API call or garbage collection.

The last 256 update durations are measured in milliseconds. Snapshot publication reports last, p95, p99 and maximum. The design targets remain **p95 <2ms** and **p99 <5ms** on the real server under representative two-client load; pure Lua tests do not establish that result.

Persistent state lives in `ModData.getOrCreate("LofersStoryteller").state`. A separate detached primitive table under `.telemetry` is replaced every five real seconds. No telemetry transmission is performed by Lua. The optional Java agent reads the snapshot and handles asynchronous protobuf delivery.

```text
schema = 1
tick, world_age_hours, phase, pressure, budget, mode, online_players
npc_count = -1 when live ownership/lifecycle is unverified
timings_ms = {last, p95, p99, max}
cells[1..64] = {x, y, z, dwell, wealth, confidence, age_hours}
decisions[1..32] = {id, event, outcome, reason, at_hour}
cell_count, scan_queue, dropped_cells, health
```

`x`/`y` are world tile coordinates of the 32-tile cell origin. The bounded cell window rotates through stored cells. A received window is **not** a complete field census, so a receiver must not interpret omitted cells as deleted or zero. Decision records and cell summaries are copied; Java/HTTP cannot mutate domain state. The snapshot contains no usernames or raw item lists.

## Validation evidence

`tests/test_storyteller.py` runs using **lupa.lua51**, matching the supported Lua language dialect. Execution on 2026-09-26: **19 tests passed**. Coverage includes:

- Negative coordinates, per-floor cell separation and eviction generations.
- Repeat/nested/transferred item deduplication and bounded ledgers.
- Decay without invented loss, and no offline budget/dwell burst.
- Grace, recovery, cooldown, active encounter and NPC capacity decisions.
- Deterministic random stream continuation and phase boundaries.
- No adapter execution in observation mode; uncertain reservations are not retried.
- Detached bounded telemetry, rotating cell window and unknown NPC count.
- Inventory operation/deadline limits and unloaded-container errors.
- Native schedule suppression across a clan reload and Bandit-only steering exclusion.
- First client join through mocked server hooks: player inventory, world storage, generator and vehicle evidence reach telemetry.
- Gradual serious injury and death/disconnect recovery, with stale stock retained as incomplete.
- Independent decay prevents new sightings from refreshing missing inventory or infrastructure.

`javap` checks against the deployed `java/projectzomboid.jar` verified the called player, inventory, square, object, vehicle and clock signatures. The first actual dedicated-server observation reached the Java agent and Observer with zero online players; this establishes the empty-world integration path only. The deployment ledger records that run's timing and payload evidence.

Run with `./dayone test` or `.tooling/venv/bin/python -m pytest tests/test_storyteller.py -q`. Dedicated-server deployment/real-client evidence belongs in the playtest ledger; do not describe these source-level checks as multiplayer validation.

## Remaining implementation after the compatibility trial

No validated encounter spawn/cleanup adapter, radio channel, physical supply cache, theft or sabotage action is enabled in this release. Storage completeness/confirmed loss, equipment/skill capability, stronger vehicle ownership evidence and exact visibility placement need additional source/runtime work. Those are distinct from the implemented observer, bounded field, simulator and debug feed. Complete the [[Bandits Compatibility]] activation gate before using the field to create adversarial events.
