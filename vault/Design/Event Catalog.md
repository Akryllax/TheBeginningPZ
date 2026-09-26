---
type: design
status: candidate-catalog
updated: 2026-09-26
---

# Event catalog

These are proposed event families, not a list of enabled features. Record executable adapters and their validation in [[Design/Storyteller Implementation]]. Every event needs eligibility, cost, placement, timeout, completion/failure and cleanup behavior before activation.

| Event family | Main phases | Eligibility and placement | Budget / lifecycle concerns | Initial rollout |
| --- | --- | --- | --- | --- |
| Distant conflict, screams, alarms | Outbreak, aftermath | Online group; believable distance; avoid repeated sound spam | Rate-limited ambient effect; expiry | Candidate after observation |
| Traffic accidents and road incidents | Early epidemic | Visible impact after unseen staging, or off-screen sound with discoverable aftermath; see [[Traffic Incidents]] | All-player exclusion, two-car cap, persistent sound/wreck receipts, shared fleet budget | Accepted; native collision tests pending |
| Radio progression | All | Dedicated channel/content; respect existing emergency broadcasts | Persist broadcast IDs; no repeated restart messages | Candidate |
| Fleeing groups and patrols | Outbreak | Normal Bandits spawn path; outside player sight/base interiors | NPC reservation, movement ownership and cleanup | Requires two-client gate |
| NPC–zombie skirmishes | Outbreak, aftermath | Valid separated participants; no forced spawn on player | Includes all spawned NPCs in global cap; resolve stale ownership | Requires two-client gate |
| Scavengers | Aftermath, survival | Confident relevant area; available budget | Bound stay/time and interactions | Requires two-client gate |
| Distress calls and supply leads | All | Discoverable but optional; no fabricated guaranteed loot | Persist lead/expiry; avoid unusable destinations | Candidate |
| Zombie congregations | Aftermath, survival | Valid off-screen placement and online target | Separate zombie workload accounting; bounded lifetime | Candidate; engine integration needed |
| Looters / limited raids | Aftermath, survival | Online target base, sufficient confidence; grace/cooldown/recovery satisfied | One major hostile event; theft only after validated stock/interaction support | Disabled until hostile gate passes |
| Limited sabotage | Aftermath, survival | Specific eligible object; online target; recovery rules apply | Strict effect budget, outcome evidence and cleanup | Disabled until separately validated |

## Shared placement rules

Do not spawn inside player sight or a base interior. Check loaded terrain, navigability and relevant floors using verified engine APIs. If a valid placement cannot be established within a bounded search, reject the proposal and explain why. Distance alone is not proof of invisibility or path access.

Do not use an upstream debug-spawn helper as an assumed substitute for the normal multiplayer spawn path. Normal spawn, ownership and persistence are covered in [[Runbooks/Multiplayer Validation]].

## Outcome contract

Persist a stable event ID, category, proposed/active/completed times, reserved resources, participants, reason codes and actual outcomes. Observation-only proposals must never consume real spawn capacity or pretend an encounter occurred. Confirmed losses may trigger recovery; missing samples do not.

There are no large-fire, nuclear or forced-loss events in scope. See [[Design/Storyteller]] and [[Design/Balance]].
