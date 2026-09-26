---
name: dayone-multiplayer-compatibility
description: Validate ZomboidDayOne build and mod compatibility, especially Bandits NPC spawning, ownership, persistence and cleanup across two clients.
---

# Multiplayer compatibility

Read `vault/Runbooks/Multiplayer Validation.md` and the current `vault/Research/Bandits Compatibility.md` from the project root. Preserve the distinction between source inspection, automated simulations, dedicated-server startup and real two-client evidence.

- Record the exact game build, Java version, dependency versions/hashes and enabled Mod IDs. Workshop item IDs and Mod IDs are different identifiers.
- Inspect the installed Bandits normal spawn path and its ownership/synchronization model before adding an adapter. A working debug spawn is insufficient evidence for ordinary encounters.
- Keep Bandits as a separate dependency. Package only original companion files; do not silently patch or redistribute upstream source.
- Check interactions with the baseline zombie mods. StarvingZombies has a Bandit exclusion in the researched build; WanderingZombies needs explicit verification. Verify current files instead of assuming those findings remain true after an update.
- Exercise spawn, transfer between client areas, reconnect, save/restart and cleanup with two real clients before calling the integration multiplayer-validated. A server boot cannot prove these behaviors.
- Keep hostile event execution disabled while that gate is pending. Do not disable Lua checksum validation based solely on an older Week One guide.

Write observed results, logs and remaining gaps into a vault experiment, including exact reproduction steps. A compatibility failure should produce a narrow adapter change or a clearly reported limitation, not an unrequested change to the live `.160` deployment.
