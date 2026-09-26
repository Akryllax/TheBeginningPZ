---
type: runbook
status: pending-real-clients
updated: 2026-09-26
---

# Multiplayer validation

The Bandits integration is not multiplayer-validated until two real clients complete the relevant checks on this dedicated server. A source audit, deterministic Lua test, debug spawn or successful boot is insufficient alone.

## Record the starting state

Create an experiment from [[Templates/Experiment]]. Record exact game/Java build, dependency hashes, custom mod ZIP checksum, enabled Mod IDs, world identity, server/agent versions, clients, mode, initial save and resource limits. Begin on the fresh test world with director hostile execution disabled.

## Baseline join and synchronization

1. Both clients install the same original companion ZIP and required upstream dependencies.
2. Join as separate accounts. The operator uses the new world's admin account for the controlled NPC trial; the second player uses a normal test account. Verify checksums, assets and no recurring Lua errors.
3. Walk separately and together; verify independent positions and exact explored areas on the new Observer.
4. Confirm normal map fog, pings/trips and Follow behavior. Check that private debug routes cannot be reached through the LAN/public gateway.

## NPC-engine integration

Start with the source-backed [[Research/Bandits Compatibility#Concrete next two-client trial|Clan Karate admin-UI trial]]. It is prepared for this release: built-in profiles are available, while the companion suppresses automatic spawn chances. The operator requests that clan once in a clear outdoor test area; the inspected profiles predict two friendly unarmed NPCs, which both clients must verify. Follow the linked procedure rather than substituting another clan.

This manual `Clan` trigger exercises the shared spawn/cluster/client-AI engine. It does not validate automatic event scheduling, off-screen placement or a storyteller receipt. Record the exact action, coordinates, time and experiment identity; there is no implemented storyteller encounter ID for this manual trial. Verify both clients see the same participants, equipment and behavior as they separate, approach from different areas, disconnect and reconnect.

Check NPC interactions against ordinary zombies with the inherited zombie mods enabled. Confirm no duplicate NPCs, frozen ownership, repeated global events or inconsistent inventories. Do not infer compatibility merely because one client sees an animated NPC.

## Persistence and cleanup

Save and gracefully restart during the controlled trial, then record what happens to the actual NPCs. Disappearance, duplication or changed ownership is a result to investigate. The current build has no validated physical cleanup operation; “Remove All Bandits” is not proof of entity despawn. Keep the pre-trial backup and follow the linked containment notes.

Before activating a future storyteller adapter, separately verify event receipts, persistence/reconciliation, completion/failure/expiry cleanup, reservation release and global counts. Test the no-player pause and reconnect path; no accumulated hostile burst should occur. A successful manual UI trial is only the first part of this gate.

## Inference and pacing

Inspect a temporary stop, repeated loot destination and established base. Compare field categories/confidence against the actual world, including nested storage and moved containers. Unload a base and verify it becomes stale rather than a confirmed loss. Confirm death/loss recovery, grace, cooldown and online eligibility using a disposable test scenario or validated control hook.

Collect performance evidence from [[Design/Performance Budget]]. Stop Observer temporarily and verify game/NPC behavior continues normally, then verify telemetry resumes without backlog flooding.

## Gate result

Mark each scenario passed, failed or not tested with evidence. Enable only event families whose effects and cleanup were actually validated. If real clients are unavailable, keep observation mode and state that this gate remains pending; continue independent source, automated and deployment work.
