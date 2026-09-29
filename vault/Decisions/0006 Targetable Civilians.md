---
type: decision
status: accepted-gates1-2-pass-one-client
updated: 2026-09-27
---

# Targetable civilians

## Context

The first server-controlled pedestrian was a Bandits-spawned `IsoZombie` dressed as a human.
Native locomotion, stock replication and cleanup passed, but the user observed zombie gait,
position skips, a zombie spawn flash, the nearby-zombie jumpscare sting and an anxiety-like
moodle ([[Experiments/Implementation Ledger]], 2026-09-27 entries). While the presentation
fixes were being scoped, the user set a requirement for the outbreak story:

> Zombies MUST target healthy NPCs.

Presentation work for disguised zombies was therefore stopped before any code changed.
Private source/bytecode inspection of the pinned build (B42.20.4 / b0bbce05d5) under
`artifacts/decompiled/pedestrian-gait-fear/` and `artifacts/decompiled/npc-player-feasibility/`
establishes why a disguised `IsoZombie` cannot satisfy that requirement natively:

- **Target acquisition is player-driven.** Zombies acquire targets when a player's LOS pass
  calls `IsoPlayer.TestZombieSpotPlayer` → `IsoZombie.spotted(player)`. On a dedicated server
  the server-side pass for a remote player is `ServerLOS.doServerZombieLOS` / `updateLOS`, and a
  client-owned zombie is redirected with `ZombieControlPacket`, whose target field is a
  `PlayerID`. No stock path offers another `IsoZombie` as a target.
- **Attack resolution casts to `IsoPlayer`.** `AttackState.triggerPlayerReaction` treats the
  target as an `IsoPlayer`; an `IsoZombie` target would fail there.
- **Bites are reported only for local victims.** `GameClient.sendZombieHit` sends
  `ZombieHitPlayer` only when the zombie is locally owned *and* the victim is the local player.
  The server applies bites to the victim's `BodyDamage` (`Bite.process`, and `AttackState`
  when the zombie is simulated on the server).
- Upstream Bandits works around this with client-side Lua zombie-vs-bandit simulation
  (`BanditUpdate.lua` "ZOMBIES VS BANDITS"), which contradicts server authority.
- **Fear is type-based.** `IsoPlayer.updateLOS` counts visible, very-close and newly spotted
  objects by `IsoZombie` type and plays `ZombieSurprisedPlayer` inside the same method.
  `BodyDamage.UpdatePanicState` raises panic from that count. The only per-actor gate,
  `isVisibleToPlayer`, is a Java field that Lua cannot set; ordinary Lua can only approximate
  exclusion after the fact.

Findings recorded for the retired path (still valid engine facts):

- Stock `NetworkVariables.WalkType` cannot encode `Walk`; `ZombiePacket` sends WT1 and the client
  resets `zombieWalkType` on every parse, so Bandits' `Bob_Walk` node never matches. The server
  moved about 19 tiles in about 5 s with the human gait while the client used a slower zombie
  gait; the stock remote-zombie parser teleports at more than 3 tiles of error. That mechanism
  explains the observed skips; it was not measured on the client.
- Remote zombie `speedMod` is parsed without the `/1000` used at creation (bytecode `i2f`).
  This affects every remote zombie in the stock game and was not changed.

## Decision

1. Disguised `IsoZombie` civilians are retired as the civilian entity. The one-NPC walker
   remains evidence that bounded **server-owned zombie simulation** works (scheduler admission,
   native pathing, stock stream appending, cleanup). That capability is kept for zombies that
   engage civilians. Its presentation fixes are not pursued.
2. The proposed civilian entity is a **server-hosted `IsoPlayer`** that has no network
   connection, subject to the spike gates below. Clients see an ordinary remote player through
   stock code. That covers human animation, clothing from the player-connected packet
   (descriptor, `HumanVisual`, `ItemVisuals`), no zombie fear classification, and native
   targeting by zombies. Server JVM injection remains the only non-Lua integration; clients
   stay ordinary Lua clients.
3. *Revised 2026-09-27 (user decision after gate-3 testing):* zombies that engage civilians
   keep **stock client ownership**; NPCs only matter in LOS and in prefetched border areas.
   The server steers the target with stock `ZombieControl`; the owner client runs stock
   `spotted()` for Actor targets (ordinary Lua); the server applies the stock bite from the
   owner's replicated attack and relays the reaction. Server-simulated zombies produced
   replica teleports and needed engine fidelity hooks, so this replaces the original plan
   below.
   Original plan: zombies that engage civilians are **server-owned and server-simulated** (bounded), because
   stock bite reporting from clients only covers local victims. Spotting uses the stock
   `TestZombieSpotPlayer` for zombies inside a small radius instead of a whole-object-list LOS
   scan per civilian. Attacks use stock `AttackState` on the server; wounds and infection land
   in the civilian's server `BodyDamage`.
4. Civilian locomotion is our own bounded server path follower. On a dedicated server, stock
   `IsoPlayer.update` always takes `updateRemotePlayer()` and forces position from
   `realx/realy`, so the server never simulates player locomotion; clients normally drive it.
   The follower supplies `realx/realy` and the network-AI movement state that the stock
   `PlayerPacket` prediction carries. B42's `AIComponent` NPC flag exists, but its `update()`
   is empty and it only feeds control variables to the local-player branch, which is
   asserted off on servers.

5. **Actor pool (accepted 2026-09-27).** Civilian state lives off-engine: identity, needs,
   routines, planner decisions, the infection model, persistence and unobserved summaries.
   The engine only receives a bounded pool of connectionless server `IsoPlayer` **Actors**
   that act as the server-side replicator. Actors are materialized only near real players.
   The simulation sends them intents (walk to, idle, flee, act); actors report what the
   engine actually did. While materialized, the engine stays authoritative for physical
   facts, and the simulation must not overwrite them: position and blocked paths, bites and
   other damage in `BodyDamage`, death and the resulting corpse, and items carried (a corpse
   can be looted). Dematerializing copies engine state back into the simulation;
   materializing applies it to the actor, so the same resident keeps the same body.
   `AKRPopulation` owns the pool, admission and leases; `AKRResidents` owns identities; the
   Java agent exposes acquire / assign / intent / report / release through the runtime
   socket and Lua bridge.
6. **Integration boundary.** Everything above is our server `-javaagent` (the project's Java
   mod: runtime instrumentation of selected methods, loaded at server launch) plus original
   Lua. No installed game file is replaced and no `zombie.*` class is distributed. Clients
   run stock code plus ordinary Lua mods ([[Decisions/0004 Server Runtime Extensions]]).

Pool identity facts (source, same build):

- The scarce resource is the reserved player online-ID range, not memory.
- A client ignores a player-connected packet for an online ID it already knows
  (`ConnectedPacket.parse` returns early). Name, descriptor and body type only travel with
  that packet. Reassigning an actor to a different resident therefore needs the stock
  removal (`PlayerTimeout`) and a fresh announce, done out of view. Clothing and hair may
  be updatable in place: `HumanVisualPacket`, `SyncClothingPacket` and `SyncVisualsPacket`
  exist but are not inspected yet.

Alternatives rejected on the evidence above:

| Alternative | Reason |
| --- | --- |
| Keep `IsoZombie` civilians, add Lua fear compensation | Zombies still cannot natively target or bite them; the jumpscare can only be blocked by also hiding real zombies' stings nearby |
| Bandits zombie-vs-bandit Lua | Client-side simulation of gameplay effects; violates server authority |
| B42 animals (`IsoAnimal`) | Zombies do not acquire animals as targets; `AttackState` drops animal targets |
| `FakeClientManager` bots | Standalone load-test clients; consume player slots and accounts, and only simplistically simulate owned zombies |
| Client-leased NPC players | Silent client authority fallback, explicitly excluded |

## Spike gates

Each gate runs in the disposable pedestrian world with one ordinary client first. Evidence
goes in [[Experiments/Implementation Ledger]].

1. **Actor pool v0 (mannequin).** Create one connectionless `IsoPlayer` Actor, standing still, with a reserved online
   ID in `GameServer.IDToPlayerMap` but not in `GameServer.Players` or any connection. The
   server authors the stock player-connected packet and a periodic `PlayerPacket`, and removes
   the civilian with stock `PlayerTimeout`. Pass when the client sees a clothed human with no
   zombie flash, jumpscare or fear moodle, with no new errors, and the civilian can be
   repeated without a restart. The same pooled Actor must also be reassigned to a different
   resident appearance within the process. Measure per-civilian server update cost.
   *Status 2026-09-27:* server/replication pass with one client (see ledger). The Actor stays
   out of the stock update loop (the server purges unregistered players each frame), so its
   stock update cost is zero and creation costs 1.7–7.6 ms per materialization. The jumpscare
   and moodle observation is pending from the user.
2. **Walking.** The server path follower drives `realx/realy` and movement state. Pass when the
   client shows human gait, has no correction jumps (client skip counter) and aligned
   difference is at most one tile p95.
   *Status 2026-09-27:* passed with one client. At 1.45 tiles/s (the client's measured walk
   speed) aligned difference was p95 0.35 tiles with 0 skips across two turns and a stop.
   Fear counts stayed at zero with the Actor in view. Speeds outside the client's 0.9–1.1×
   catch-up band drift until the stock 7-tile teleport.
3. **Zombie engagement.** One server-owned zombie spots the civilian through stock spotting,
   chases it and bites with stock `AttackState`. Wounds appear in the server `BodyDamage`.
   The observer sees stock animations, and fear comes only from the real zombie. The zombie
   hands ownership back to a client when it retargets a real player.
   *Status 2026-09-27:* passed with client-owned hunters and one (owner) client: no teleports,
   visible trip, server bite, relayed reaction. The earlier server-simulated run also passed on
   damage, but with replica desync. Status of that run: A stock sprinter bit a fleeing, tripped
   Actor through stock AttackState; wounds are in the server `BodyDamage`; the hit and trip
   were relayed with stock packets. This needed four scoped hunter fidelity hooks (see the
   ledger). The ownership handoff to clients and two-client checks are still pending.
   *Reopened later on 2026-09-27:* user-watched runs stalled and desynced. Causes and
   untested fixes are in the ledger and the root handoff; re-pass needs a new observed run.
4. **Lifecycle.** Death, corpse, reanimation, infection progression, and persistence outside
   `players.db`.

Then four civilians and two real clients, as in [[Design/NPC First Slice]].

## Consequences and risks

- Bandits2 is no longer needed for civilian spawn or appearance if the spike passes. It may
  remain for hostile bandit encounters. [[Decisions/0005 Modular AKR Mods]] lists
  `AKRResidents` as a Bandits adapter; revisit that after gate 1.
- **Connection assumptions.** Server paths may assume a player has a connection: safehouse,
  chat, anti-cheat, sleep and fast-forward, population loading around players, saves and
  admin lists. The civilian must stay out of `GameServer.Players`, and each assumption must
  be tested, not assumed.
- **Online IDs.** Player IDs come from per-connection slots (`UdpConnection.playerIds`,
  four per connection). The login-path allocator was not located yet; the reserved range must
  be proven collision-free before gate 1.
- **Client liveness.** Remote players with stale network data are removed after a timeout, so
  the server must keep sending relevance-filtered updates.
- **Other mods.** Client Lua that iterates online players (including Bandits) will see
  civilians as players.
- **Cost.** `IsoPlayer` updates are heavier than zombies' (stats, nutrition, fitness, safety).
  The 32-actor target needs measurement at gate 1.
- **Zombie ownership.** Handoffs between server and client ownership need the existing
  native bookkeeping (`NetworkZombieManager.moveZombie`), not owner-field writes.
- Zombies near civilians but far from every real player stay unowned and unsimulated. That
  is consistent with off-screen summaries, but off-screen civilian deaths must be modeled.
