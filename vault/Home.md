---
type: index
status: active
updated: 2026-09-30
---

# The Beginning

Latest maintenance assessment: [[Research/42.21 Upgrade Impact]] — isolated client/server
downloads verified; port recommended and pending. The 42.20.4 baseline remains intact.

Next task for **2026-09-30**: [Four-resident neighborhood](../TASK_Four_Resident_Neighborhood.md).
The accepted scope includes real ground-floor homes and controlled restart for living
residents; crash recovery and aftermath restoration remain explicit follow-ups.

A fresh multiplayer world on the workstation, with an adaptive storyteller and an independent Observer map. The existing `.160` game remains separate. Open this `vault/` directory as an Obsidian vault; plain Markdown readers also work.

Resume with [[Experiments/Current State]] and [[Design/NPC First Slice]] for the current
pedestrian feasibility milestone. Use [[Experiments/Implementation Ledger]] for what exists and what has actually been tested. The design below is a target; it is not evidence that every event or integration is live.

## Design

- [[Design/Civilian Pool and Navigation]] — pool, civilian FSM, bounded A*, partial headless evidence and traversal stop gate.
- [[Design/Civilian Defense and Death]] — flee/defend policy, native corpse reuse evidence, and the open attack/contact gate.

- [[Design/NPC First Slice]] — accepted experiment-first ordering, population ownership and crowd gates.

- [[Design/Live Event Runtime]] — persistent event execution, compatible module reload and batch tests.
- [[Design/Implementation Roadmap]] — accepted continuation, complete commute and rollout gates.
- [[Design/First Week]] — corrected civilian opening, C++/Lua service and replication contract.
- [[Design/Architecture]] — services, authority and data flow.
- [[Design/Service Segmentation]] — architecture diagrams and the reasoning for each service/module boundary.
- [[Design/Traffic Incidents]] — visible accidents, off-screen sound and persistent aftermath.
- [[Design/Navigation]] — precomputed terrain scores, vehicle clearance and live obstacle checks.
- [[Design/Storyteller]] — world phases, pressure, recovery and lifecycle.
- [[Design/Storyteller Implementation]] — source-level implementation and limitations.
- [[Design/Spatial Field]] — base inference from bounded observations.
- [[Design/Performance Budget]] — work limits and measurement protocol.
- [[Design/Event Catalog]] — candidate events and rollout status.
- [[Design/Balance]] — inherited settings and proposed storyteller defaults.
- [[Design/Telemetry]] — private protobuf ingestion and local diagnostics.

## Evidence and decisions

- [[Research/42.21 Upgrade Impact]] — verified downloads, Java/Lua breakages, native risks and port sequence.
- [[Research/Sources]] — source provenance and dependency IDs.
- [[Research/Bandits Compatibility]] — the integration audit and multiplayer evidence.
- [[Decisions/0001 Living World]] — chosen scope and campaign alternatives.
- [[Decisions/0002 Isolated Local Deployment]] — paths, ports and fresh state.
- [[Decisions/0003 Observation Before Encounters]] — rollout and validation gates.
- [[Decisions/0004 Server Runtime Extensions]] — server-only JVM injection, ordinary Lua clients and distribution boundaries.
- [[Decisions/0005 Modular AKR Mods]] — core plus self-sufficient feature mods, per-feature compatibility and the Lofers → AKR rename.
- [[Decisions/0006 Targetable Civilians]] — civilians must be native zombie targets; disguised zombies retired; off-engine simulation drives a pool of server-hosted player Actors.
- [[Reviews/2026-09-27 Project Review]] — whole-project assessment, risks and viability.

## Operations and experiments

- [[Runbooks/Civilian Qualification Batch]] — executable headless native batch and deferred visual/two-client sequence.

- [[Runbooks/Operations]] — project commands and service boundaries.
- [[Runbooks/Pedestrian Experiment]] — private controls and the first walking gate.
- [[Runbooks/Runtime Testing]] — layered offline suite, retained reports and pending native gates.
- [[Runbooks/Backup and Restore]] — consistent snapshots and isolated restore checks.
- [[Runbooks/Multiplayer Validation]] — two-client acceptance sequence.
- [[Runbooks/Private Mod Distribution]] — original companion ZIP installation.
- [[Runbooks/Java Inspection]] — project-local decompilation and bytecode evidence for the installed game.
- [[Experiments/Current State]] — deployment snapshot, unfinished source and next-session handoff.
- [[Experiments/Implementation Ledger]] — implementation evidence and open gates.
- [[Templates/Experiment]] and [[Templates/Decision]] — record new work without losing context.

The vault contains no credentials. Dependency source belongs in project `references/`, runtime data in `data/`, and generated evidence/builds in `artifacts/`.

- [[Design/Offscreen Chase Staging]] — proposed loaded-area pursuit admission and reveal; failed 64-case lessons.

- [[Design/Resident Plans and Locomotion]] — durable goals, worker routine, navigation and native WALK/RUN.

- Compatibility migration: `TASK_42_21_Compatibility_Migration.md` and `compatibility/README.md` at the source root.
