# Game mods

Use the project storyteller and multiplayer-compatibility skills for companion-mod changes.
Keep `LofersStoryteller` original source separate from downloaded Workshop dependencies.
Do not make client synchronization or API availability claims based on Lua mocks alone.

Scheduling, persistent IDs and budgets are server-owned. Bound work on every event hook,
including failure paths. Track missing/stale observations explicitly. Implement and exercise
the accepted civilian scenario in isolated worlds; multiplayer validation is a release gate,
not a reason to deliver an observation-only substitute. Never describe mock results as a
two-client demonstration. Enable new scenario behavior only in explicitly configured worlds.

For First Week work also read the NPC replication and scenario validation skills in
`.agents/skills/`. The native worker has its own Lua runtime: never call game APIs there.
