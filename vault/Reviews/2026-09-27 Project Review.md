---
type: review
status: current
updated: 2026-09-27
---

# Project review — 2026-09-27

An outside-view assessment of the whole project: what is strong, what is at risk, how viable
the targets are and what to do next. The proposed restructuring is recorded separately in
[[Decisions/0005 Modular AKR Mods]]. Evidence for individual features stays in
[[Experiments/Implementation Ledger]]; this page does not add in-game evidence.

## Verdict

The engineering is careful, and the server-owned vehicle work is a genuine finding. But the
target is several times larger than what has been demonstrated, and most recent effort went
into the least essential feature (moving traffic). As a **private experiment with a smaller
first target**, the project is viable. The full [[Design/First Week]] specification — living
town, real traffic, emergency services and two-client agreement — is months of focused work
at best, and every game update will reopen part of it.

## What was reviewed

- Design, decisions, runbooks and handoff pages in this vault; `AGENTS.md`;
  `PLAN_SchedulerAPI.md`.
- Source: the server Java agent (`scenario-agent/`, ~2.8k dense lines, 39 classes), the
  companion Lua mod (~2.6k lines), the C++ planner (~0.9k lines plus three Lua rule files),
  operations scripts and tests.
- Tests run on the working tree at review time: 204 Python tests passed; the Java build,
  fixtures and pinned-launcher check passed. These are offline checks only.

## Strengths

- **Constraints are enforced in code.** Build guards hash 32 engine classes and refuse to
  start on an unknown build, so a mismatch never quietly becomes a vanilla zombie world.
  Nothing replaces installed game files and no engine code is redistributed, which keeps
  future distribution clean.
- **Server-owned vehicle physics with ordinary clients.** The server runs Bullet for managed
  cars and stock vehicle replication carries the result; clients need only Lua. That removes
  the biggest distribution obstacle for NPC mods. One client has accepted driving, turning,
  stop/wait, a parked-car bypass with horn, and static collisions with stock damage and sound.
  Added server work for one car averaged about 0.2 ms, with a maximum just over 1 ms.
- **Honest evidence.** Pages consistently separate "fixture passed" from "native run" from
  "one client saw it" from "two clients agree". This is rare and valuable.
- **Performance discipline.** Explicit tick budgets, incremental cursors, bounded queues, no
  whole-world scans, and a clear authority split (game server decides; Observer only reads).
- **Small, bounded planner.** The C++ service is compact, deterministic and already wired
  to the Lua mod, with hard limits on plan length, expansions and admitted jobs.

## Problems and risks

### 1. Scope far ahead of evidence

Nothing a player would recognise as the scenario has run in front of a player: no resident
routine, no contagion progression, no two-client session. What exists in play is one
scripted car on a reviewed route in a disposable world, watched by one person. The
multiplayer agreement targets (500 ms action agreement, one-tile position agreement) have
never been measured.

### 2. Effort is going into the least essential piece

The recent work is almost entirely vehicle behaviour: lanes, stop signs, bypasses, crashes at
100+ km/h, rollover attempts. The outbreak story does not require moving traffic. It requires
a calm opening, residents who visibly live, infection that spreads and two players seeing the
same thing. Staged crashes already work and deliver most of the drama; multi-car traffic is
the riskiest remaining feature and should not be on the critical path.

### 3. One global compatibility gate

Every feature depends on game build 42.20.4 and a pinned Bandits source. The guard is a
single all-or-nothing set of whole-class hashes, so any game update — even one that touches
an unrelated method in `IsoChunk` — disables everything, including features whose hooks did
not change. Build 42 is an unstable branch that updates often. The guard makes breakage loud
rather than silent, but the rework cost is paid for the whole project every time.

### 4. Monolithic packaging

One Lua mod (`LofersStoryteller`) contains two namespaces (storyteller and scenario), the
Bandits integration, the admin UI, traffic state and a driver model. One Java agent contains
launch guards, population hooks, NPC leases, vehicle physics, traffic, crash feedback and the
event scheduler. `ServerVehicleProbe.java` is a 55 KB class with lines up to 371 characters.
There is no way to ship, test or disable one feature without the others.

### 5. Process overhead

About 2,400 lines of design notes, including a 933-line ledger, for roughly 10k lines of
source. `AGENTS.md` has become a running status log, and handoff pages must explain which of
their own paragraphs are out of date. The discipline is good; the volume now slows reading
and invites contradictions. Status belongs in [[Experiments/Current State]]; `AGENTS.md`
should hold stable rules only.

### 6. Many moving parts for one maintainer

Java agent, Lua mod, C++ service with its own Lua, Protobuf IPC, Observer, Caddy, containers
and Python operations. Each is individually reasonable; together every change crosses
several of them. The planner's code is cheap — its real cost is the extra service,
container and failure handling — so it should stay optional, with a Lua fallback.

## Viability

| Target | Assessment |
| --- | --- |
| Private friend-group server with a reduced first target | **Viable** |
| Full First Week specification | **Months of work at best; exposed to every game update** |
| Public release | Narrow audience: hosts must run a server Java agent. Clients need only Lua mods, which helps. |

## Recommendations

1. **Ship a thin vertical slice first:** calm opening → manual outbreak start → scripted
   seven-day progression → walking residents on simple routines (Lua and Bandits, planner
   optional) → cars only as parked vehicles and staged crashes. Then run a real two-client
   session measuring agreement and server cost.
2. **Split into modules** with one core and self-sufficient feature mods, so traffic becomes
   optional rather than blocking. See [[Decisions/0005 Modular AKR Mods]].
3. **Make compatibility per feature.** Guard each hook plug-in separately, fingerprint the
   hooked methods instead of whole classes, and publish capabilities so dependent Lua modules
   refuse to enable with a visible reason instead of failing the whole server.
4. **Keep the live event runtime**, but point it at the slice: it should run outbreak and
   resident events as readily as crash experiments.
5. **Trim process text.** Move status out of `AGENTS.md`; keep the ledger for evidence and
   Current State for the handoff.
6. **Rename Lofers → AKR** as part of the module carve-out, not as a separate sweep, with
   migrations for saved identifiers. Details are in the decision.

## Follow-up

Phase 1 of the split (the `AKRCore` contracts mod and its tests) was added with this review;
see the decision for status and the remaining phases.
