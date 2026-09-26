---
type: research
status: source-audited-multiplayer-unverified
date: 2026-09-26
tags: [bandits, compatibility, multiplayer]
---

# Bandits compatibility

The first release observes the fresh world and previews decisions. It does **not** spawn encounters. Source inspection found real differences between current Bandits V2 and the older Week One multiplayer integration, and cleanup cannot be inferred from a successful spawn call.

Related: [[Storyteller Implementation]], [[Multiplayer Validation]], [[Home]].

## Sources and provenance

Workshop dependencies were downloaded privately into `data/game-files/steamapps/workshop/content/108600/`. Nothing from them is included in the companion ZIP.

| Dependency | Workshop source | Inspected branch | Result |
| --- | --- | --- | --- |
| Bandits NPC | [3268487204](https://steamcommunity.com/sharedfiles/filedetails/?id=3268487204) | `mods/Bandits/42.20` | Current source declares B42.20+ SP/MP, Mod ID `Bandits2` |
| Week One SP | [3403180543](https://steamcommunity.com/sharedfiles/filedetails/?id=3403180543) | `mods/BanditsWeekOne/42.20` | Reference only; not enabled |
| Week One MP | [3631385356](https://steamcommunity.com/sharedfiles/filedetails/?id=3631385356) | `mods/BanditsWeekOneMP/42.13` | Reference only; uses older queue interface |
| Wandering Zombies | [2983905789](https://steamcommunity.com/sharedfiles/filedetails/?id=2983905789) | `42.18` | Native steering predicate does not exclude Bandits |
| Starving Zombies | [3396867685](https://steamcommunity.com/sharedfiles/filedetails/?id=3396867685) | `42.15` | Native update explicitly excludes the `Bandit` flag |

Selected SHA-256 fingerprints, relative to the Bandits `42.20/media/lua/` directory:

| File | SHA-256 |
| --- | --- |
| `server/BanditServerSpawner.lua` | `6c732bbeadcb8dbf06457d1f497bcac73df441508305b6a663ce289d73dd656b` |
| `server/BanditServerWanderers.lua` | `edf62faf74cd9b95af79e0881f0da72a2f7984a57d1aa70f24e76efda48a77c9` |
| `shared/BanditCustom.lua` | `a3fbfae8bde80c197c9f22dd7338dcad2b4f91c1092d49961045596f274d8b45` |
| `shared/BanditGMD.lua` | `a6b530f81e0737cc425685f52c0f8bf31efd5a424d7a4eff119daf1eb2abeab2` |
| `client/BanditPermanent.lua` | `a54ec705a9d5c21878d8499ba02488d18f76614a26ad00c2b13d37b240e410ac` |

The Wandering Zombies validity helper `shared/RYUKU_WanderingZombies_Utility.lua` SHA-256 is `48658da7c37a2c7987266a5a90a6d14c768ddb2519b8746e1456643e4ad8ec7c`. Recheck these integration points after any Workshop update.

## Normal server spawn path

`BanditServerSpawner.lua` registers `checkEvent` on `Events.EveryTenMinutes`. It chooses an online player, reads clans through `BanditCustom.ClanGetAll()`, evaluates `spawnChance`, and calls its local `spawnType`. That function selects clan size and behavior, generates locations, calls local `spawnGroup`, then publishes the brain cluster. `BanditServer.Spawner.Type(player, args)` exposes this path for external mods. `BanditServer.Spawner.Clan(player, args)` exposes a size/program/spawn-points variant and is also called by the normal wanderer system.

Important limits for our adapter:

- `Type` and `Clan` do not return a spawned count or entity IDs. A call returning normally is not proof that any NPC exists.
- `spawnGroup` uses the clan's available distinct character profiles; requested size can exceed actual size.
- `generateSpawnPointUniform` tests safehouse occupancy, square occupancy and player distance, but does not establish actual visibility. Its proximity guard is bypassed for debug/admin state. Our no-visible-spawn rule needs its own validated placement check.
- `Clan` with direct coordinates only checks that the square exists. It must not be called with arbitrary user/current-player coordinates.
- `brain.key` is a vehicle key identifier and is used with `InventoryItem:setKeyId` on death. **Do not put a string event ID in it.** No verified dedicated event correlation field was found.
- NPC creation consumes the engine's random stream. The storyteller's selection stream is deterministic, but NPC equipment/placement is not covered by that guarantee.

## Synchronization, ownership and persistence

The server records brains by persistent outfit ID in one of 32 `BanditC0`–`BanditC31` GlobalModData tables and calls `TransmitBanditCluster`. Clients request and update those tables through `BanditGMD.lua`.

`client/BanditUpdate.lua` returns immediately on a dedicated server. Clients turn matching zombies into Bandits based on the brain cluster. Therefore successful dedicated-server startup cannot establish animation, AI or ownership handoff correctness. Two real clients must inspect the same normal encounter, leave/re-enter range and reconnect.

Cluster persistence is not the same as live NPC persistence. `client/BanditPermanent.lua` currently returns immediately at the start of its restore check. `BanditRemove` deletes the brain and transmits the cluster; the client can then convert that zombie back to a normal zombie. It is **not** a verified physical despawn/cleanup receipt. Report live NPC count as unknown (`-1`) until a bounded verified lifecycle tracker exists. Never release encounter capacity merely because an expiry timer elapsed.

The older Week One MP `server/BWOPopControl.lua` reads `gmd.Queue[id]`. The inspected V2 GMD initializer creates cluster tables and does not initialize that Queue. This is concrete source evidence against enabling the old campaign unchanged with this V2 build; no failure was deliberately induced in a playable world.

## Suppressing independent encounters

The native spawn multiplier has a declared minimum of **0.25**, so setting it to zero is not a reliable configuration switch. `General_OriginalBandits=false` suppresses built-in profiles when the config loader considers the game active, but its load-state condition makes it insufficient as the only guard.

Our server companion wraps only `BanditCustom.ClanGetAll` and sets each returned clan's `spawn.spawnChance` to zero in memory. Both the encounter scheduler and wanderer orchestrator call that accessor before rolling. This also covers a subsequent clan reload/edit. The guard bounds its work at 256 clans and returns an empty registry to these callers if exceeded. It does not edit Workshop source, delete brains, or hide existing NPCs.

This guard suppresses automatic scheduling; it is not an authorization system for Bandits' own custom/debug network commands. The new world uses a private join password and has not been treated as an untrusted public NPC API.

## Wandering/Starving Zombies

Starving Zombies checks `isoZombie:getVariableBoolean("Bandit")` before modifying a zombie. Wandering Zombies queues locally controlled zombies and later checks its global `wzIsValidZombie` function, without a Bandit exclusion. That creates a potential competing pathing controller.

The original companion client script wraps the exposed validity predicate on `OnGameStart`: flagged Bandits return false; ordinary zombies retain the original predicate. Existing Wandering Zombies cleanup removes rejected entries. No upstream file or event callback is replaced. This behavior has a Lua unit test; actual queued NPC behavior and the short interval before the Bandit flag arrives still require the two-client test.

## Linux deployment finding

The initial dedicated-server log showed animation paths being normalized to lower case while the Workshop directories used `Bandits` / `AnimSets`. The deployment work added local filesystem aliases where required; upstream Lua was not rewritten. Recheck startup logs after Workshop updates, because an alias/workaround can become stale when the packaging changes.

## Activation gate

Keep `mode="observe"` and `npcIntegrationValidated=false`. Before implementing and enabling any encounter adapter, record:

1. Two clients see the same small encounter created through the normal server path, with no duplicate spawns.
2. Ownership changes and reconnects preserve health/equipment/AI without errors.
3. A game restart and chunk unload/reload have understood, verified NPC behavior.
4. A receipt associates actual NPC identities with the reserved event; a verified cleanup operation reports what remains.
5. Placement is outside every player's visibility and base interior; capacities count existing NPCs too.
6. Wandering Zombies does not override NPC steering; the server and both clients remain within the profiling target.

This is a technical validation gate, not a requirement to ask repeatedly for general deployment permission.

## Concrete next two-client trial

The installed Bandits UI can exercise the shared server spawn engine without enabling its automatic encounter schedules. `client/BanditMenu.lua:206` implements `SpawnClan`; the world context menu at line 271 exposes it when `isDebugEnabled()` **or** `isAdmin()` is true. Debug launch mode is therefore unnecessary for the first trial.

The source-backed path is:

```text
Admin right-click on an outdoor square
  → Spawn Bandit Clan
  → Clan Karate
  → sendClientCommand(player, "Spawner", "Clan", args)
  → BanditServer.Spawner.Clan
  → generateSpawnPointHere
  → spawnGroup
  → banditize + TransmitBanditCluster
```

`Clan` is also used by the normal wanderer system when an abstract group becomes loaded NPCs. The menu is a manual trigger into that shared engine, **not** a test of the normal automatic encounter scheduler or the `Type` placement function.

### Preparation on the server

Keep the storyteller in observation mode and its native-scheduler guard installed. For this controlled trial, enable `Bandits.General_OriginalBandits=true` and leave `General_SpawnMultiplier=1.0`, then gracefully restart and verify telemetry health is `observing_native_spawns_disabled`. The original profiles must be loaded for the client menu and server clan lookup. The guard suppresses automatic chances without removing those profiles or blocking a deliberate `Clan` invocation.

Before players invest in the test world, retain a consistent pre-trial backup with an identifiable timestamp. Do not restore that snapshot later without accounting for gameplay since the snapshot. Automatic cleanup is one of the things under investigation.

Both players install the same companion ZIP and subscribe to the required Workshop dependencies. The operator joins `192.168.1.132:16271` using the separate new-world **admin** credentials from the local credential file; Eric joins with his normal test account. These are unrelated to credentials or characters on `.160`. First confirm both join normally, the map receives positions/exploration, and there are no recurring Lua errors.

### First encounter, one click

1. Meet in a loaded outdoor clearing away from player buildings, vehicles and stockpiles. Record coordinates and both clients' initial error counters. Confirm no Bandit encounter is already there.
2. The admin right-clicks a clear ground square and chooses **Spawn Bandit Clan → Clan Karate**, once. If the submenu is empty or Karate is missing, stop at the profile/load issue; do not choose a different armed or hostile clan.
3. The menu requests `size=6`, `program="Bandit"` and the clicked coordinates. In the pinned profile files, Karate has exactly **two distinct, friendly, unarmed profiles**. `spawnGroup` limits actual participants to the available profiles, so the expected result is **two NPCs**. Treat their appearance and count as an observation to confirm, not a guarantee from the menu label. Do not click repeatedly if nothing appears.
4. Both clients record whether those same two NPCs have matching appearance, movement, equipment and interaction state. Observe their response to ordinary nearby zombies and check that Wandering Zombies is not steering them independently. Avoid provoking a larger battle during this first check.
5. Eric leaves streaming range and returns while the admin stays; then reverse who stays nearby. Disconnect and reconnect each client separately. Record disappearance, duplication, freezing and error counters.
6. With the encounter still present, the operator captures the relevant logs and performs the planned save/graceful restart. Both clients rejoin and report what remains. Source inspection predicts uncertainty here; a lost or changed NPC is a useful failed result, not grounds for guessing that it was cleaned up.
7. Record how many actual entities remain. **Do not use “Remove All Bandits” as a cleanup guarantee**: in this build its `BanditFlush` handler clears visited-building/post/base data and does not implement a verified entity despawn. Trial restoration into the isolated test world is a known containment option; normal encounter cleanup still needs implementation and testing.

Karate source identity: clan `0dfc13d3-4ce6-4af8-aac6-326eb7514c36`, profiles `c3c606c3-1aba-4baf-923f-a762977e543e` and `3af549b6-bf5d-48e1-bfe3-86b06a1e723c`. Both profiles specify `Base.BareHands`. Reverify after a Workshop update.

Source fingerprints for this procedure:

| Source | SHA-256 |
| --- | --- |
| `42.20/media/lua/client/BanditMenu.lua` | `5506d1445459861a2985d745aeefe35aacadbbcc96db2799735b39f69b41c6da` |
| `common/bandits/clans.txt` | `2d1bf7e87c4c171b62f58b42451b4eac502407bd74faa299746cc8223c1c97ab` |
| `common/bandits/bandits.txt` | `a0a5c09d60fc39d8700e436afd7e76f87423cf2b9a09f6dc734ca9ddbbc3b574` |

### What this trial does and does not establish

It tests the real server spawn/cluster/client-AI path, ordinary two-client visibility, handoff, reconnect behavior, source-compatible unarmed profiles and baseline zombie-mod interactions. It deliberately creates a visible manually placed test group. It does not establish off-screen placement, automatic event selection, hostile effects, event receipts, cleanup accounting, or that restarting/unloading preserves NPCs correctly.

The ordinary automatic encounter path is `checkEvent → spawnType`. The public server `Spawner.Type` entry calls that same `spawnType` function, but the built-in admin menu exposes **Clan only**. A later controlled Type trial can use the existing client Lua console/network API or a separately reviewed one-shot test harness; neither is currently a storyteller control surface.

For source reference, the Type command shape is:

```lua
-- Research invocation, not an automatically executed or client-validated step.
sendClientCommand(getSpecificPlayer(0), "Spawner", "Type", {
    cid = "0dfc13d3-4ce6-4af8-aac6-326eb7514c36",
    dist = 70,
})
```

The pinned game contains a Lua console: `shared/keyBinding.lua` declares **Toggle Lua Console**, default grave/backtick key, and `UIDebugConsole.ProcessCommand` compiles and executes Lua (verified through `javap`). Access to that console in an actual connected client has not been tested here. Even once invoked, `Type` uses upstream random placement and bypasses the native scheduling roll. An admin/debug caller can bypass the upstream distance guard; this invocation therefore cannot certify the storyteller's no-visible-spawn rule.

Before production encounters can be enabled, we still need a bounded placement/visibility checker, reliable spawned-identity receipts, live-NPC accounting that includes loaded and unloaded ownership, and verified cleanup/reconciliation. Those do not block the concrete first UI trial above; they prevent treating a successful trial as the entire activation gate.
