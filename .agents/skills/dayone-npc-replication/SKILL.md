---
name: dayone-npc-replication
description: Implement or diagnose multiplayer NPC actions, replicas, ownership and vehicle handoff for LofersStoryteller on the pinned Zomboid build.
---

# Shared NPC state

Read `vault/Research/Bandits Compatibility.md` and `vault/Design/First Week.md`.
Reverify integration hashes after dependency updates. Never update hashes merely to bypass
a rejection. All modifications belong to original companion/bridge code, not redistributed
Bandits source.

The current civilian backend uses server-controlled pooled IsoPlayer Actors (Decision
0006); ordinary zombies retain native client ownership. The Bandits-specific guidance
below applies to that dependency's own callbacks, not a switch of Actor authority.
Read the latest Current State before treating historical blockers as current.
For watched iteration use dayone-watched-testing; for qualification use dayone-test-iteration.

Maintain one logical resident and physical generation across walking, driving, abstraction,
infection and death. Match the execution lease to confirmed native ownership. The native
zombie manager does more bookkeeping than setting an owner field directly.

Bandits generates and executes tasks on multiple clients. Its custom-program callback is
not the whole update: healing, combat and inventory effects can run outside that callback.
Gate the exact managed-NPC callbacks and effects; replicas run presentation only. Preserve
ordinary zombies and unrelated mods. Do not let nearest-player guesses compete with the
native owner or accept receipts from an expired owner.

Describe Java integration explicitly: a client `-javaagent` changes selected loaded game
methods in memory and requires a custom launch on each participating client. Calling it a
"helper" alone is insufficient. The user selected JVM injection on the controlled server
with ordinary Lua clients. Do not install or launch the superseded client Java prototype.
Never replace core game files or ship decompiled/replacement engine classes. Preserve the
playable world while server vehicle streaming and real multiplayer behavior are validated.

Replicate semantic action ID/type/target/stage alongside native movement. Late joiners need
a current snapshot. Never create a competing custom transform stream without resolving
native prediction. A driver and the pedestrian exiting the vehicle are the same resident.

At handoff, revoke old execution, confirm native ownership, and reconcile the current action
before resuming effects. Vehicle braking and immediate defense cannot wait on external AI.
Use idempotent accepted receipts; reconcile uncertain crash outcomes instead of promising
transactions across independent world/ModData saves.

Validate with two actual clients observing one NPC. Collect paired position/action samples,
declared network conditions and handoff evidence. Mock tests establish protocol invariants,
not visible multiplayer behavior. See the scenario-validation skill for release acceptance.
