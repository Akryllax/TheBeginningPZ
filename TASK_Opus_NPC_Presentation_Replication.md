# Handoff: server-side Actor pool, gate 3 (flee, trip, catch, bite)

> **Latest checkpoint, 2026-09-27:** the user requires cross-resident initialized reuse,
> not a resident cache. Four prewarmed Actors now serve 15 assignments without another
> constructor. Native headless dirty → fresh resident → restore original onto different
> body checks pass. See the top of `vault/Experiments/Current State.md` for exact evidence
> and performance limits. Integrated idle/roam/flee, runtime admission, stairs/climbing,
> visual/two-client reuse and reopened combat gate 3 remain pending. Use
> `vault/Runbooks/Civilian Qualification Batch.md`; older next-run instructions are historical.

Prepared 2026-09-27 (evening) at the user's request as a **full handoff**: the previous agent
stopped at its context limit. This file replaces the earlier disguised-zombie presentation
handoff, which the user superseded (see the appendix). Read it completely before touching the
live server. The user delegates manually; no child agent is running.

## 1. Where things stand

- The project abandoned disguised `IsoZombie` civilians. Its NPC body is now an **Actor**: a
  pooled, connectionless server `IsoPlayer`, driven by off-engine resident state and
  replicated with stock player packets
  ([Decision 0006](vault/Decisions/0006%20Targetable%20Civilians.md)).
- Gate 1 (spawn, replicate, reassign, release, repeat) passed with one client.
- Gate 2 (walking) passed with one client.
- **Gate 3 is reopened by user observation.**
  - Architecture: a stock zombie, owned by the nearest real client, chases the Actor. The
    Actor flees, trips and is bitten, and the server applies the damage.
  - An earlier run bit successfully (ledger: "Gate 3 passes with client-owned hunters").
  - Two later user-watched runs failed visually. The zombie was not a sprinter; then it
    reached the tripped Actor but stalled; then positions split during the chase.
- Root causes were found and fixes are **built and deployed but not yet observed live**.
  The next action is to run the long-chase test (section 6) and analyse it.

| Gate (Decision 0006) | Status |
| --- | --- |
| 1 Actor pool v0 | Pass, one client |
| 2 Walking | Pass, one client (p95 0.35 tiles, 0 skips at 1.45 tiles/s) |
| 3 Zombie engagement | Reopened. Fixes deployed and untested (section 5). Two clients and ownership handoff still pending |
| 4 Lifecycle (death, corpse, reanimation, persistence) | Not started |
| Resident state table (pool/ECS stats kept across swaps) | Agreed idea, planned after gate 3 |
| Four Actors, two real clients | Not started |

Nothing is committed. All work is uncommitted in the working tree on branch
`work/first-week-server-runtime`, alongside many unrelated changes. Do not commit unless the
user asks, and never reset, clean or broadly stage.

## 2. Agreed functionality and user decisions (chronological)

1. Zombies **must** natively target healthy NPCs, so the disguised-zombie gait and dressing
   work was stopped as wasted effort.
2. Direct replacement of game code is forbidden. Use only a plugin (Lua mod) or a Java mod
   (server `-javaagent` with scoped bytecode hooks). **No client Java.** Clients stay on
   ordinary Lua.
3. The user proposed the architecture:
   - mock "dumb headless player NPCs", owned by the server, kept in a pool of usable Actors;
   - the NPC simulation stays off-engine;
   - Actors act as a server-side replicator.
   This was recorded as Decision 0006 and gates 1–3 followed.
4. **Pool table / ECS resident state.** Per-NPC stats (wounds, reactions and so on) must
   survive Actor swaps, so player reactions carry over. Agreed; build it after gate 3.
5. **Test setup.**
   - Grant the user's account `akr` admin, invisible and godmode during tests, so it does
     not taint results.
   - The agent may kill and relaunch the game client with connect parameters and the local
     server password (developer-controlled LAN; passing it on the command line is fine).
   - Always **SIGTERM the client after a test the agent launched**.
   - Capture screenshots only when the game window is on the active Hyprland workspace
     (private windows were captured once and deleted).
6. **Out of scope:** removing or reducing network optimistic execution (the owner client's
   locally predicted attack animation and hit reactions stay).
7. **Test scenario.**
   - The Actor flees from the zombie (baked server movement is fine), trips, and the zombie
     catches and attacks him.
   - The hunter must be a **deterministic sprinter**, not a random sandbox speed.
8. **Hunter authority.**
   - The user asked for behaviour "like a player moving to a point, tripped, and a zombie it
     owns catches him and attacks", and confirmed: "NPCs are only relevant in game while
     they are in LOS (and prefetch nearby areas, in the border of FOW)".
   - Result: **client-owned hunters.** Stock ownership gives the zombie to the nearest real
     client; the server directs its target with the stock `ZombieControl` message; the owner
     runs stock chase and attack; the server stays authoritative for Actor health.
9. Latest request: make the chase and the whole test **longer**, with time for everything to
   initialise before the chase. Implemented as `chase_delay_seconds`, a longer flee and a
   longer timeout and hold.

## 3. Boundaries (carried over and still binding)

- Workspace: `/var/home/akr/Documents/Projects/ZomboidDayOne`. Source repo
  `git@github.com:Akryllax/TheBeginningPZ.git` (GPL-3.0; preserve history).
- The normal `.132` game (UDP 16271/16272, RCON 27025) and production `.160` are untouched.
  Work only on the disposable pedestrian world. **Never use `./dayone rcon`**; use
  `./dayone scenario-test-rcon`.
- Server JVM injection is authorized; keep hooks scoped to our entities.
  - Never replace or patch installed core files.
  - Never distribute replacement `zombie.*` classes or decompiled code.
  - Decompiled research stays private under `artifacts/decompiled/`.
- Keep stock collision and replication semantics and the physics coordinate frame. Native
  ownership needs stock bookkeeping (`NetworkZombieManager`); setting an owner field alone is
  insufficient.
- The server owns gameplay, damage and persistence. The Observer is read-only.
- Measure game-thread work, keep explicit budgets, never scan the whole world.
- One ordinary client (`akr`) has participated. Don't call anything multiplayer-validated
  until two real clients have been tested.
- Use `./dayone`, project-local tools and the host Podman. Keep caches and research inside
  the project.
- Record decisions and evidence in the vault. Detailed history belongs in
  `vault/Experiments/Implementation Ledger.md`.

## 4. Architecture as implemented

### Actor pool (server, Java agent)

The core is [ServerActors.java](scenario-agent/src/net/lofers/scenario/ServerActors.java).

- **Identity.** Reserved online IDs from `ID_BASE=4096` (real IDs are `slot*4+index` ≤ 1019).
  The Actor is registered only in `GameServer.IDToPlayerMap`, never in `GameServer.Players`
  or any connection.
- **Replication.** Stock `sendPlayerConnected`, then `PlayerPacket` (prediction types 0/1),
  sent reliably or unreliably at fixed intervals. Release uses stock `PlayerTimeout`.
- **Puppet.** Stock purges unregistered players from the cell object list every frame. So the
  Actor is kept out of the object list and update scheduler, and placed on its square's
  moving objects (`setCurrentSquareFromPosition` + `setMovingSquareNow`).
  - A native-step sentinel fails the event if stock ever updates it.
  - Its stock update cost is zero.
- **Appearance.**
  - `dressInNamedOutfit` + `WornItems.setFromItemVisuals` + `addItemsToItemContainer`.
  - `setForceX/Y` drives position.
  - Reassignment swaps identity in the same pooled slot.
- **Walking (gate 2).** The server path follower advances `realx/realy` at the configured
  speed. The client walks remote players at animation speed with only 0.9–1.1× catch-up:
  walk ≈ 1.45 and run ≈ 2.6 tiles/s measured. Other speeds drift until the stock teleport.
- **Fear.** `IsoPlayer.updateLOS` counts `IsoZombie` only, so Actors cause no jumpscare or
  panic. Measured zero.

### Hunter (gate 3, client-owned), in `ServerActors.spawnHunter/hunt/bite/fleeAndTrip`

- **Spawn.** Stock `VirtualZombieManager.createRealZombieAlways` at the last route point,
  under `GameHooks.withSpawnPermit`. `hunter_speed` then calls `doSprinter`,
  `doFastShambler` or `doShambler`. It is **not** bound to server simulation.
- **Warm-up.** Stock ownership assigns the nearest real client. After
  `chase_delay_seconds`, the server sends stock `ZombieControl(zombie, actor)` to the owner:
  immediately when the target differs, and as a 1 s heartbeat.
- **Owner perception.** On the owner, `HunterPerception.lua` runs stock
  `zombie:spotted(actor,false)` **every frame** for owned zombies targeting an Actor. Stock
  ZombieControl sets the target but never "sees" it.
- **State mirror.** The server copy mirrors the owner: `NetworkZombiePacker.applyZombie`
  writes the owner's `realX/realY` into the copy's `x/y`, plus `realState` and target.
- **Bite.** When the copy's `realState` is `attack` and the target is the Actor, the server
  judges one bite per attack entry, at least 350 ms after it sees the entry.
  - Conditions: copy within 1.5 tiles, Actor not floored, `LosUtil` line not blocked.
  - Effect: `setAttackedBy` + stock `BodyDamage.AddRandomDamageFromZombie` + `Update`.
  - Relay: stock `ZombieHitPlayer` and `PlayerInjuries` packets to relevant connections.
  - Contact comes on the first damaging hit (`WOUNDED`) or after 5 reactions (`DEFENDED`).
    The event completes `hold_seconds` after contact.
- **Flee and trip** (baked on the server).
  - Once targeted, the Actor runs straight away from the copy position, along a clear
    corridor of length `min(30, max(12, ceil(flee*(trip+2))))`.
  - It trips when the hunter gap is ≤ 3.0 tiles after at least 1 s of running, or at
    `trip_after_seconds`, or on arrival.
  - Default trip: stock **forward stagger**, with no fall.
    - Setup: `BumpType=stagger`, `BumpFall=false`, `BumpFallType=pushedBehind`
      (`Bob_StaggerForward`).
    - The stock StatePacket `BumpedState` Enter is sent; `RECOVERED` + Exit follow at +1.6 s.
  - `trip_fall=true` keeps the older run bump → stumble → fall on front → get-up path.
    That path has lethal stock semantics: a floored victim is eaten, not bitten.
  - Because the puppet is a remote player on the server, `BumpedState.setParams` takes its
    remote branch. The code therefore writes `BUMP_TYPE`, `BUMP_FALL_TYPE` and `BUMP_FALL`
    directly with `player.set(...)`.
- **Cadence hook.**
  - Stock tells each client, in `ZombieSynchronization.hasNeighborPlayer`, whether another
    player is near. With a neighbor the owner reports zombies every 200 ms; without one,
    every 4000 ms.
  - The Actor is not in `GameServer.Players`, so stock answered "no neighbor".
  - The new `ScenarioTransformer` call-site hook in `NetworkZombiePacker.send` ORs
    `connection == hunterOwner` into `isNeighborPlayer()` (`ServerActors.neighborPlayer`).
  - It is scoped to the connection owning the live hunter and cleared on retirement.
- **Retire.** Stock `deleteZombie` + `removeFromWorld`/`removeFromSquare`, with absence
  verified.

### Inert earlier hooks

`ServerPedestrians` has server-simulated hunter hooks that were needed before the switch to
client-owned hunters:

- `isRemoteZombie → hunts`
- `isTargetVisible` override
- `AnimationPlayer.DoAngles` deferred-rotation fallback
- `updateAuth` bypass for `simulated`
- `bindHunter`/`unbindHunter`

They are inert because hunters are no longer bound. Deciding whether to keep or remove them
is open (section 8).

### Runtime control

- `protocol/runtime_control.proto` `PedestrianCase`:

  | Field | Name |
  | --- | --- |
  | 6 | `entity` (`DISGUISED_ZOMBIE`, `ACTOR`) |
  | 7 | `reassign_after_seconds` |
  | 8 | `walk_tiles_per_second` |
  | 9 | `hunter_zombies` |
  | 10 | `flee_tiles_per_second` |
  | 11 | `trip_after_seconds` |
  | 12 | `hunter_speed` (`SANDBOX`, `SPRINTER`, `FAST_SHAMBLER`, `SHAMBLER`) |
  | 13 | `chase_delay_seconds` (new) |
  | 14 | `trip_fall` (new) |

  Existing fields are preserved.
- Validation lives in `PedestrianDefinition.java`:
  - one Actor per event;
  - hunter reach 3–20 tiles;
  - flee 1–6 tiles/s;
  - trip ≤ 20 s;
  - chase delay 0–30 s and hunter-only;
  - `trip_fall` requires a trip;
  - timeout ≤ 180 s, hold ≤ 60 s.
- `RuntimeSession` routes `ACTOR` events to `ServerActors` (`Routed` backend map).
  `EventResources` and `EventScheduler` have `ACTOR` and `ZOMBIE` kinds.

## 5. This session's evidence and fixes (not yet in the vault ledger)

The artifact root is `artifacts/scenario-tests/20260927-141446-320776`, abbreviated `$A`.

### Run `actor-gate3-owned-3` (epoch `3c07bbb6-…`, event `.3`)

Case: `SPRINTER`, flee 2.6, trip at 3 s, old `left`/fall trip.

- **Sprinter confirmed.** Walk type `sprint1`, about 3.3 tiles/s. On the client the zombie
  reached the stumbling Actor 250 ms after the bump. The user saw exactly this.
- **Stall for ~13 s in `attack`.** Stock `attack` plays `Zombie_Idle_Lunge` (grace) until
  `targetSeenTime > 0.5`, then `Zombie_Bite_Start`. Stock `spotted()` adds one frame of time
  per call. The 4 Hz Lua call accumulated about 16× too slowly.
- **Server bite missed.** It was judged only at attack entry: copy distance 1.26 against a
  1.2 limit. The first server bite came 37 s after the trip (health 91.2, 1 wound).
- **Position split.** The client drew the Actor about 1.2 tiles behind the server during the
  trip, then walked it 1.1 tiles after get-up, stranding the zombie. This was the "stood up,
  walked a few meters, both idled" the user described.
- Evidence: `$A/actor-gate3-owned-3.jsonl`, `$A/client-debuglog-gate3-owned3.txt`.

### Run `actor-long-chase-1` (epoch `55853ba9-…`, event `.1`)

Case: `$A/one-actor-long-chase-stagger.json`, run with the per-frame spotting fix.

- **Warm-up and spotting worked.**
  - The chase started at +8.0 s.
  - Attack → Actor `hitreaction-bite` → zombie `staggerback` cycled every ~2 s with no stall.
  - 0 skips for both entities.
- **Owner updates every 4 s** (10.55, 14.60, 18.62 s and so on). The server learned of the
  target 2.3 s late, so the Actor stood while the zombie sprinted at him.
- **Slow-factor split.** The sprinter caught the still-running Actor on the owner at 11.8 s.
  - Stock `AttackState` applies a sprinter's slow factor (up to 0.5) to its target. The owner
    applied it only to its local copy of the Actor.
  - The client ran the Actor at 1.3 tiles/s while the server ran it at 2.6. Positions split
    by up to ~5 tiles.
  - The stagger then rendered at x≈10777 on the client while the server was at 10771.6. The
    client bite came at 16.4 s; the Actor then shuffled to the server position under
    repeated attacks until +36 s.
- **Server bites: 0.** Bug: the check used `zombie.realx`, which is 0 on the server copy.
  Fixed: it now uses `getX/getY`.
- **Result: FAILED `actor_unplaced`.** The client shut down normally at 21:39:06
  ("removing all player data", no crash; probably closed by the user, unconfirmed). The last
  player left, the chunk unloaded, and the Actor lost its square.
- Evidence: `$A/actor-long-chase-1.jsonl`, `$A/client-debuglog-long-chase-1.txt`.

### Fixes built and deployed (fixtures pass; live behaviour unobserved)

1. `HunterPerception.lua`: finds pairs at 4 Hz but calls stock `spotted()` every frame
   (≤ 8 pairs).
2. Warm-up gate, a longer flee corridor, and the stagger trip (default) or fall trip
   (`trip_fall`).
3. Bite judged throughout the attack window on the copy position, with reach 1.5 and a delay
   of 350 ms; `hBiteMisses` counted.
4. Trip triggers on hunter gap ≤ 3.0 tiles (after ≥ 1 s of running), recorded as `tripGap`.
   The zombie now reaches during the stumble instead of attacking a runner, which also
   avoids the owner-only slow factor.
5. Neighbor-player cadence hook, so the owner reports every 200 ms.
6. New diagnostics in `path_state`: `hNeighbor`, `tripGap`, `hBiteMisses`; `hDist` now uses
   the copy position.

## 6. Immediate next step: run and judge the long chase

The server is **running** with the latest build:

- epoch `35d43721-87b2-4000-beb6-821b56e91eb3`
- agent SHA256 `3338398134dbef83b81caa10ddf86acec785b1b0d4ed81ac05c7772e712d5f55`

No client is connected and no event is live. Recheck before acting:
`./dayone runtime hello $A/ipc/runtime.sock` and `./dayone scenario-test-rcon players`.

```bash
cd /var/home/akr/Documents/Projects/ZomboidDayOne
A=artifacts/scenario-tests/20260927-141446-320776
timeout 580 ./dayone pedestrian-test-join     # kills/relaunches client, waits "fully connected"
for c in 'setaccesslevel "akr" admin' 'invisibleplayer "akr" -true' 'godmodeplayer "akr" -true'; do
  ./dayone scenario-test-rcon "$c"; done
sleep 3
ACTOR_POLL=0.2 timeout 280 .tooling/venv/bin/python $A/capture_event.py . $A/ipc/runtime.sock \
  $A/one-actor-long-chase-stagger.json actor-long-chase-2 $A/actor-long-chase-2.jsonl
sleep 3; cp "$(ls -t ~/Zomboid/Logs/*DebugLog*.txt | head -1)" $A/client-debuglog-long-chase-2.txt
pid=$(pgrep -f '^./ProjectZomboid64'); [ -n "$pid" ] && kill -TERM $pid   # client only; server is /pzserver/...
```

The case file `$A/one-actor-long-chase-stagger.json`:

```json
{"actors":1,"route":[{"x":10784,"y":9856},{"x":10794,"y":9856}],"timeout_seconds":180,
 "hold_seconds":10,"seed":12,"entity":"ACTOR","hunter_zombies":1,"flee_tiles_per_second":2.6,
 "trip_after_seconds":5,"hunter_speed":"SPRINTER","chase_delay_seconds":8,"trip_fall":false}
```

The user joins at about (10779, 9859). The hunter spawns 10 tiles east of the Actor, and the
Actor flees west.

### How to analyse

- Server samples: each status reply's `actors[0].path_state` is `;`-separated `k=v`.
  - Keys: `wallMs`, `hState`, `hOutcome` (the copy's `realState`), `hTarget`, `hOwner`,
    `hDist`, `hAttackEntries`, `hReactions`, `hDamaging`, `hBiteMisses`, `hNeighbor`,
    `tripGap`, `health`, `wounds`, `hWalkType`, `aOnFloor`.
  - The Actor's `action` is `STAND`, `RUN`, `TRIPPED`, `RECOVERED` (or `FALLEN`/`STOOD_UP`),
    `WOUNDED` or `DEFENDED`.
- Client samples come from `[AKRActorSample]`, `[AKRZombieSample]`, `[AKRActorSkip]` and
  `[AKRZombieSkip]` lines (4 Hz, `t` = epoch ms).
- Align both streams by wall-clock ms and compare at each client sample:
  - the client Actor and zombie positions and states;
  - the server Actor position and copy distance.

### Accept when all of these hold

- The copy updates about every 200 ms (`hNeighbor=true`), and the flee starts within about
  0.5 s of the client zombie's `walktoward`.
- The zombie does **not** attack while the Actor runs, and the trip fires on `tripGap ≤ 3`.
- The client shows the Actor `bumped` (stagger), then the zombie `attack`, then Actor
  `hitreaction-bite` while standing.
- The server shows `hReactions ≥ 1`, with `WOUNDED` (health < 100, wounds ≥ 1) or `DEFENDED`.
- After the stagger, the client and server Actor positions differ by ≤ 1 tile. There is no
  post-stagger "walk to catch up".
- 0 skips, no new client or server errors, and the event `COMPLETED` after the hold.
- Report to the user in plain terms. They watch the run live and their observation outranks
  the metrics.

If positions still split after the stagger, the likely cause is client lag (~0.5–1 tile) plus
stagger root motion. Two options:

- advance the server Actor through the stagger by the measured root-motion distance; or
- place the server at the rendered estimate before `RECOVERED`.

Measure first. If the flee must resume after a bite, mirror stock's sprinter slow on the
server: slow factor +0.03 per tick to 0.5, decayed by slow timer, flee speed scaled, and
stock `GameServer.sendSlowFactor` to all clients.

## 7. Operations reference

| Item | Value |
| --- | --- |
| Client endpoint | `192.168.1.132:16281` (UDP 16281/16282, loopback RCON 27035) |
| World | `AKR_DayOne_Test_20260927_141446_320776` |
| Artifact root | `artifacts/scenario-tests/20260927-141446-320776` (`current.json` points here) |
| Runtime socket | `$A/ipc/runtime.sock` (container `/run/lofers/runtime.sock`) |
| Server mods | `Bandits2;AKRCore;AKRDevTools` (Bandits unused by the Actor path) |
| Game | B42.20.4, Java 25; server game files `data/game-files` (read-only mount) |
| Stock anim/action XML | `data/game-files/media/AnimSets`, `data/game-files/media/actiongroups` |

- **Build.** `./dayone scenario-build`: builds the npc-service bundle and runs
  `scripts/build_scenario_agent.py --test`. Java fixtures include
  `PedestrianRuntimeFixture.actors()` for routing and limits.
- **Deploy.** `./dayone pedestrian-test-stop && ./dayone pedestrian-test-start`. This copies
  the jar and the server mods into the disposable deployment; native hook changes need this
  restart. Then poll `./dayone runtime hello $A/ipc/runtime.sock` until it returns an epoch.
- **Client Lua** is not installed by `pedestrian-test-start`.
  - Copy it into `~/Zomboid/mods/AKRDevTools/...` only after checking the installed hash
    against `artifacts/scenario-agent/pedestrian-client-install.json`.
  - Back up to `artifacts/scenario-agent/client-before-<tag>/` and update the receipt.
  - The latest backup is `client-before-perframe-20260927-213547`.
  - Client Lua changes need a client relaunch; `pedestrian-test-join` does that.
- **Join.** `./dayone pedestrian-test-join`
  (`scripts/pedestrian_ops.py join`):
  - SIGTERMs the client and launches `steam -applaunch 108600 +connect … +password …`.
  - The dev-only `mods/AKRDevConnect` (enabled locally) completes the saved-account connect.
  - It clicks the window centre after "loading time was" and waits for "fully connected".
- **Capture.**
  - `$A/capture_event.py` submits one definition and polls status until terminal
    (`ACTOR_POLL` seconds; 240 s cap).
  - `$A/align.py` was the gate-2 alignment tool.
  - Both were copied from the old session scratchpad.
- **Client samplers.** In `mods/AKRDevTools/42/media/lua/client/AKRDevTools/`:
  `ActorSamples.lua` (4 Hz) and `HunterPerception.lua`. `Presentation.lua` belongs to the
  old disguised path.
- **Memory** (`~/.claude/projects/.../memory`): close the launched client; unattended join is
  allowed.
- **Decompile.** `python3 scripts/decompile_java.py CLASS… --output artifacts/decompiled/NAME`.
  Relevant roots:
  - `npc-player-feasibility` (AttackState, ZombieControlPacket, ZombieHitPlayerPacket)
  - `pedestrian-locomotion` (IsoZombie)
  - `server-resident-replication-review` (NetworkZombieAI, NetworkZombiePacker, ZombiePacket)
  - `pedestrian-walk-wire` (NetworkZombieSimulator)
  - `pedestrian-feasibility` (ZombieSynchronizationPacket)
  - `pedestrian-presentation` (IsoPlayer)
  - `npc-actor-flee` (BumpedState)
  - `pedestrian-world-update` (IsoCell)

## 8. Stock engine facts established this session (private inspection)

- `AttackState`:
  - `triggerPlayerReaction` damages only a standing target within 1.0 tile with line of
    sight; a **floored target becomes an eat-body target** (lethal path, gate 4);
  - a sprinter (`speedType==1`) raises the target's slow factor to 0.5;
  - exit to idle happens on `ZombieBiteDone` or `!bCanSeeTarget`.
- Zombie attack anims:
  - `gracePeriod` (`Zombie_Idle_Lunge`) needs `targetSeenTime < 0.5`;
  - `start` (`Zombie_Bite_Start`) needs `> 0.5`;
  - `success` fires `AttackCollisionCheck` at 20%.
  - The attack range for `bAttack` is 0.72 (`vectorToTarget`).
- `IsoZombie.spotted` adds one frame of time to `targetSeenTime` per call and resets it only
  when the target changes. The owner's `preupdate` increments it only for remote zombies.
- `isTargetVisible` on a client is `currentSquare.isCouldSee(player.getIndex())`: the local
  view of the zombie's square. Out-of-view hunters lose sight (consistent with the
  LOS-relevance rule).
- `ZombieControlPacket.processClient` resets `targetSeenTime` only when the target changes, so
  the 1 s heartbeat is harmless.
- `ZombieSynchronizationPacket.parse` sets the owner send period to 200 ms if
  `hasNeighborPlayer`, else 4000 ms. The server computes the flag from
  `UdpConnection.isNeighborPlayer()` in `NetworkZombiePacker.send`.
- `NetworkZombiePacker.applyZombie` (server) writes the owner's `realX/Y` into the copy's
  `x/y`; the copy's `realx` field stays unset.
- Stock sprint trip (`IsoPlayer` drunk code) uses `BumpType=trippingFromSprint`, fall type
  `pushedBehind` 80% (`Bob_WalkStumble`) or `pushedFront`.
  - `left` + `BumpFall` plays `Bob_Run_BumpL` then `Bob_RunStumble`, falling on the front.
  - `stagger` + no fall + `pushedBehind` plays `Bob_StaggerForward`.
- Remote-puppet trips need the `BumpedState` params written directly, then the stock
  StatePacket Enter and Exit.
- The Actor chunk unloads when the last real player leaves or the Actor is shoved out of its
  square. That surfaces as `actor_unplaced` (event FAILED).

## 9. Open issues and backlog (in priority order)

1. Run and judge the long chase (section 6); then tell the user what to watch and get their
   observation.
2. Vault follow-up.
   - Done: a short ledger entry ("Gate 3 reopened …") and a "reopened" note on Decision 0006
     gate 3.
   - Still to do: update `vault/Experiments/Current State.md`, and document
     `chase_delay_seconds`/`trip_fall` in `vault/Runbooks/Pedestrian Experiment.md`.
   - Add the next run's evidence to the ledger.
3. **Graceful dematerialize** when the Actor's chunk unloads or it loses relevance, instead
   of `actor_unplaced` FAILED. This fits the agreed "relevant only in LOS/FOW border" rule.
4. **After contact**, the owner's zombie keeps attacking visually (predicted reactions) while
   the server stops biting. Decide on a disengage (for example, the Actor escapes or the
   hunter retargets) or accept it for the test.
5. **Resident state table** (pool/ECS): persist wounds, health, reactions and identity across
   Actor swaps. The user explicitly wants this after gate 3.
6. Decide the fate of the inert server-simulated hunter hooks in `ServerPedestrians`/
   `ScenarioTransformer`. Remove them or keep them documented as dormant.
7. The stock `ZombieSound` packet underflows on the client for server-simulated zombies.
   Likely moot with client-owned hunters; verify before closing.
8. The name tag shows `akr-actor-4096`; give Actors resident names.
9. Test with a second real client (observer ≠ owner): hit relay, the owner-only slow factor,
   ownership handoff when the hunter retargets a real player.
10. Gate 4 lifecycle, then four Actors, then the civilian opening
    (`vault/Design/First Week.md`, `vault/Design/NPC First Slice.md`).

## 10. Files touched for the Actor work (all uncommitted)

**New:**

- `scenario-agent/src/net/lofers/scenario/`: `ServerActors.java`, `EventResources.java`,
  `EventScheduler.java`, `GamePhysicsBackend.java`, `PedestrianDefinition.java`,
  `ProbeCommands.java`, `ResidentPhysics.java`, `RuntimeLua.java`, `RuntimeSession.java`,
  `RuntimeSocket.java`
- `scenario-agent/test/.../PedestrianRuntimeFixture.java`
- `protocol/runtime_control.proto`
- `mods/AKRCore/`, `mods/AKRDevTools/` (including `HunterPerception.lua` and
  `ActorSamples.lua`), `mods/AKRDevConnect/`
- `scripts/pedestrian_ops.py`

**Modified:**

- `ScenarioTransformer.java` (IsoPlayer / AnimationPlayer targets, hunter hooks, the new
  NetworkZombiePacker neighbor hook)
- `ServerPedestrians.java`, `GameHooks.java` (`withSpawnPermit`), `ScenarioAgent.java`
  (actors backend), `BuildGuard.java`
- `scripts/manage.py` (`pedestrian-test-join`)
- vault documents: Decision 0006, ledger, Current State, runbooks, Home, First Week,
  NPC First Slice

Unrelated modified files in `git status` belong to other tasks; leave them alone.

## Appendix: superseded disguised-zombie handoff (engine facts still valid)

The previous version of this file handed off fixing gait, skips and fear for Bandits-based
disguised `IsoZombie` pedestrians (`ServerPedestrians`, `Presentation.lua`, `Gate.lua`).
The user stopped it: zombies must target healthy NPCs. Facts worth keeping:

- The stock zombie walk-type wire enum has only `1–5`, `sprint1–5` and `slow1–3`; a human
  `Walk` is serialized as `1`.
- `IsoPlayer.updateLOS` counts every `IsoZombie` for jumpscare and panic, with no Bandit
  exclusion.
- Remote zombies teleport when the position error exceeds 3 tiles
  (`NetworkZombieAI.parse`).
- Dedicated-server zombie fixes remain in place for the pedestrian path:
  - skipped animation and variable registrations;
  - the `setVariable` guards;
  - scheduler exclusion;
  - `CanUsePathfindState` false giving an absent `bPathfind`;
  - cleanup membership checks.
  Don't regress them.

The old file was never committed, so its full text is not in git history. Its evidence is in
the ledger entries dated 2026-09-27 before "Actor-pool design accepted".
