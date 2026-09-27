# Project skills

Codex discovers these workflows under `.agents/skills/`. Read the applicable skill when the task benefits from its project-specific constraints. The root [AGENTS.md](AGENTS.md) applies to all work.

| Skill | Use for |
| --- | --- |
| [dayone-multiplayer-compatibility](.agents/skills/dayone-multiplayer-compatibility/SKILL.md) | Checking build/mod compatibility, Bandits integration and real two-client behavior |
| [dayone-storyteller](.agents/skills/dayone-storyteller/SKILL.md) | Implementing event scheduling, lifecycle, persistence, budgets and recovery |
| [dayone-spatial-profiling](.agents/skills/dayone-spatial-profiling/SKILL.md) | Base inference, inventory signals, sparse fields and performance measurement |
| [dayone-observer-telemetry](.agents/skills/dayone-observer-telemetry/SKILL.md) | Protobuf exporter changes and local read-only debug views |
| [dayone-local-deployment](.agents/skills/dayone-local-deployment/SKILL.md) | Operating, packaging, backing up and validating this isolated workstation deployment |
| [dayone-native-worker](.agents/skills/dayone-native-worker/SKILL.md) | C++/Lua planning, bounded IPC, deterministic rules and native service builds |
| [dayone-npc-replication](.agents/skills/dayone-npc-replication/SKILL.md) | NPC ownership, semantic actions, replica presentation and native vehicle handoff |
| [dayone-scenario-validation](.agents/skills/dayone-scenario-validation/SKILL.md) | Calm-opening and two-client acceptance in disposable worlds, then fresh-world rollout |
| [dayone-java-inspection](.agents/skills/dayone-java-inspection/SKILL.md) | Inspecting pinned game Java classes, bytecode, ownership paths and compatibility evidence |

For the current iteration milestone, start with [the scheduler plan](PLAN_SchedulerAPI.md),
[the runtime design](vault/Design/Live%20Event%20Runtime.md) and
[the current handoff](vault/Experiments/Current%20State.md). Use the existing Java inspection,
scenario validation and native-worker workflows as applicable. Planned reload/batch commands
are not available yet; the handoff distinguishes validated runtime artifacts from incomplete
source edits.

Keep skills focused on decisions that are easy to get wrong in this project. Put research evidence and evolving design in the vault; avoid copying long design documents into each skill.
