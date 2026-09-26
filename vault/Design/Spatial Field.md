---
type: design
status: accepted-target
updated: 2026-09-26
---

# Spatial field and base inference

Infer likely bases from several signals using sparse **32×32 tile cells**, retaining floor. This supports a heatmap and useful diagnostics without repeatedly scanning the full map.

| Signal | Supports | Common false positive |
| --- | --- | --- |
| Dwell and repeated visits | Sustained player use | Looting one large building |
| Stored inventory categories/density | Investment and supplies | Untouched commercial stock |
| Useful infrastructure | Established operations | Existing map fixtures |
| Defenses and constructed changes | Player investment | An inherited fenced compound |
| Relevant vehicles | Mobility and storage capacity | A parked vehicle passed once |

A base candidate should combine repeated use with invested or confirmed useful resources. Explain contributing signals and confidence in local debug rather than presenting a score as objective ownership.

## Data shape

Use a spatial hash keyed by `(floor(x/32), floor(y/32), z)`. Floor division matters for negative coordinates. Each cell retains bounded summaries, update time, freshness/confidence, decay anchor and observation generation. Keep category values rather than a permanent list of every item in the world.

Distinguish **unknown**, **stale**, **observed empty** and **observed populated**. An unloaded area is unknown/stale, not empty. Removing stale samples must not create an artificial confirmed loss.

## Incremental observation

Observe only loaded relevant areas around online players or existing candidates. Maintain a deduplicated dirty queue and resumable cursor. Bound cells, queues, nested container depth and item work per slice, with both operation and wall-time caps. Lazy decay occurs when cells are accessed or during limited maintenance passes.

Nested inventories and transfers require identity/generation handling so a bag does not count both inside its container and as an independent stockpile. A snapshot that stops at a budget limit is incomplete; publish that fact and continue later. Unsupported item/container APIs lower confidence rather than inventing totals.

Changes can be significant only after comparable observations. Compare confirmed stock generations for loss, smooth transient transfers, and avoid treating a loaded-to-unloaded transition as theft.

## Debugging and validation

The local Observer debug surface should show field value by floor, contributing categories, update age, confidence and deferred work. Public map fog and normal inventory privacy remain intact. Use fixtures for a roadside stop, repeated looting, a small player base, a dense warehouse, nested storage and cross-cell transfers.

See [[Design/Performance Budget]] and [[Design/Telemetry]].
