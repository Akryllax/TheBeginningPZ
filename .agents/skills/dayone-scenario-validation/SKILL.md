---
name: dayone-scenario-validation
description: Validate First Week civilian openings, outbreak progression and multiplayer continuity in disposable Zomboid worlds, then prepare the isolated .132 rollout.
---

# Scenario acceptance

Read `vault/Design/First Week.md` and `vault/Runbooks/Backup and Restore.md`.
Existing implementation authorization includes preparation and disposable-world tests; do
not ask again for routine work. It does not authorize pretending unperformed client tests
passed. If clients are needed, finish independently testable work and provide one concrete
reproduction session.

Use isolated cachedirs, world identities and unused game/RCON ports. Keep test telemetry
away from the playable Observer and preserve prototype saves until archived. No `.160`
changes are implied. Restore tests must never overwrite the running save.

Check calm state before activating the outbreak: outdoors, first-visited buildings,
basements, story zones and save/restart. Native population zero alone does not suppress all
indoor/story spawns; disabling all zombie creation also disables Bandits civilians.

Demonstrate one persistent routine and one real civilian/emergency vehicle trip before
scaling population. Test two observers, native-owner changes, reconnect, chunk transitions,
human vehicle entry, infection/conversion and restarts. Missing is not dead or cleaned up.

The seven-day clock starts once when the admin activates it, pauses when empty, and survives
restart. Player Knox immunity is independent of NPC contagion. Out-of-view batching considers
every player and cannot silently damage player property or consume their storage.

Record automated, dedicated-server, one-client, two-client and WAN evidence separately.
Failed compatibility is a concrete unresolved gate, not permission to replace the accepted
opening with observation-only code. Keep the implementation ledger accurate.
