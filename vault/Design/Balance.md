---
type: design
status: initial-baseline
updated: 2026-09-26
---

# Balance

The initial experiment preserves the live game's quality-of-life settings, then adds pressure through event pacing. Faster progress remains intentional. The new server uses fresh characters and a fresh world, not a migrated copy.

## Inherited baseline

| Setting | Baseline | Notes |
| --- | --- | --- |
| Global XP | 10× | Keep individual skill factors as inherited |
| Skill-book reading | `MinutesPerPage = 0.01` | Game minutes per page |
| Crop growth | `FarmingSpeedNew = 2` | Preserve inherited setting |
| Day length | `DayLength = 5` | Preserve live sandbox value |
| Other crafting, repair and salvage time | Existing mod/vanilla values | No general action-time multiplier added in v1 |
| Players | Up to 4 | Intended regular group: 2–4 |
| Empty server | Pause | Do not queue hostile catch-up events |

The copied sandbox is the operational source of truth. Compare it before edits, and record intentional differences rather than replacing the baseline wholesale with a campaign preset.

## Director defaults to tune

See [[Design/Storyteller]] for the initial grace, cooldown, recovery and NPC limits. Wealth should use category weighting, smoothing and caps; raw item count should not dominate. Recovery and online eligibility override pressure. Limit repetitive event families so high resources do not become constant raids.

Start with observation-only proposals. During testing, compare the proposal explanation with what the players actually own and are experiencing. Treat false base detection, unseen losses, exhausted groups and excessive downtime as separate tuning problems.

Record each change with before/after values, the hypothesis, exact build and a playtest result in [[Templates/Experiment]]. A setting that feels right in a brief solo run has not been validated for a 2–4 player session.
