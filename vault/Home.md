---
type: index
status: active
updated: 2026-09-26
---

# The Beginning

A fresh multiplayer world on the workstation, with an adaptive storyteller and an independent Observer map. The existing `.160` game remains separate. Open this `vault/` directory as an Obsidian vault; plain Markdown readers also work.

Start with [[Experiments/Implementation Ledger]] for what exists and what has actually been tested. The design below is a target; it is not evidence that every event or integration is live.

## Design

- [[Design/Implementation Roadmap]] — accepted continuation, complete commute and rollout gates.
- [[Design/First Week]] — corrected civilian opening, C++/Lua service and replication contract.
- [[Design/Architecture]] — services, authority and data flow.
- [[Design/Navigation]] — precomputed terrain scores, vehicle clearance and live obstacle checks.
- [[Design/Storyteller]] — world phases, pressure, recovery and lifecycle.
- [[Design/Storyteller Implementation]] — source-level implementation and limitations.
- [[Design/Spatial Field]] — base inference from bounded observations.
- [[Design/Performance Budget]] — work limits and measurement protocol.
- [[Design/Event Catalog]] — candidate events and rollout status.
- [[Design/Balance]] — inherited settings and proposed storyteller defaults.
- [[Design/Telemetry]] — private protobuf ingestion and local diagnostics.

## Evidence and decisions

- [[Research/Sources]] — source provenance and dependency IDs.
- [[Research/Bandits Compatibility]] — the integration audit and multiplayer evidence.
- [[Decisions/0001 Living World]] — chosen scope and campaign alternatives.
- [[Decisions/0002 Isolated Local Deployment]] — paths, ports and fresh state.
- [[Decisions/0003 Observation Before Encounters]] — rollout and validation gates.
- [[Decisions/0004 Server Runtime Extensions]] — server-only JVM injection, ordinary Lua clients and distribution boundaries.

## Operations and experiments

- [[Runbooks/Operations]] — project commands and service boundaries.
- [[Runbooks/Backup and Restore]] — consistent snapshots and isolated restore checks.
- [[Runbooks/Multiplayer Validation]] — two-client acceptance sequence.
- [[Runbooks/Private Mod Distribution]] — original companion ZIP installation.
- [[Runbooks/Java Inspection]] — project-local decompilation and bytecode evidence for the installed game.
- [[Experiments/Implementation Ledger]] — implementation evidence and open gates.
- [[Templates/Experiment]] and [[Templates/Decision]] — record new work without losing context.

The vault contains no credentials. Dependency source belongs in project `references/`, runtime data in `data/`, and generated evidence/builds in `artifacts/`.
