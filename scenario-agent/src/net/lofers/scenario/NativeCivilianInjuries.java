package net.lofers.scenario;

import java.util.*;
import zombie.characters.*;
import zombie.iso.LosUtil;
import zombie.network.*;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.hit.ZombieHitPlayerPacket;

/** Native bite processing for bounded, continuously observed client-owned hunters. */
public final class NativeCivilianInjuries {
  private static final Map<NativeCivilianInjuries, Set<zombie.core.raknet.UdpConnection>> watchers =
      new IdentityHashMap<>();

  static boolean neighborPlayer(zombie.core.raknet.UdpConnection owner) {
    return watchers.values().stream().anyMatch(s -> s.contains(owner));
  }

  private record Entry(short id, CivilianBite bite) {}

  private final IdentityHashMap<IsoZombie, Entry> entries = new IdentityHashMap<>();
  private final CivilianPool<IsoPlayer> pool;
  private final CivilianPool.Token token;
  private final IsoPlayer actor;
  public int attempts, damagingHits, packets;

  public record Contact(
      CivilianPool.Token token,
      int sequence,
      short hunter,
      float before,
      float after,
      boolean fatal) {}

  public Contact lastDamage;
  private long reactionUntil;
  private String ownedReaction = "";
  public int resumed;

  public boolean reacting(long now) {
    if (now >= reactionUntil
        && !ownedReaction.isEmpty()
        && !actor.isDead()
        && !actor.isOnFloor()
        && !actor.isKnockedDown()
        && !actor.isGrappling()) {
      // Match the native hit-reaction exit only for the reaction this adapter created.
      // Stock player postupdate is deliberately skipped for controlled Actors.
      if (ownedReaction.equals(actor.getHitReaction()) && !"EndDeath".equals(ownedReaction)) {
        actor.setHitReaction("");
        if (actor.isCurrentState(zombie.ai.states.PlayerHitReactionState.instance()))
          actor.setDefaultState();
        resumed++;
      }
      ownedReaction = "";
    }
    return now < reactionUntil
        || actor.isOnFloor()
        || actor.isKnockedDown()
        || actor.isGrappling()
        || actor.getIgnoreMovement();
  }

  void ownReaction(long now) {
    reactionUntil = now + 1_000_000_000L;
    ownedReaction = Objects.toString(actor.getHitReaction(), "");
  }

  public String reaction() {
    return actor.getHitReaction();
  }

  NativeCivilianInjuries(CivilianPool<IsoPlayer> pool, CivilianPool.Token token) {
    this.pool = pool;
    this.token = token;
    actor = Objects.requireNonNull(pool.body(token));
  }

  public void tick(List<IsoZombie> nearby, long now) {
    GameHooks.ownThread();
    if (nearby.size() > 32) throw new IllegalArgumentException("bite_observation_budget");
    if (pool.body(token) != actor || actor.isDead()) {
      dispose();
      return;
    }
    var owners =
        Collections.newSetFromMap(new IdentityHashMap<zombie.core.raknet.UdpConnection, Boolean>());
    watchers.put(this, owners);
    entries
        .keySet()
        .removeIf(
            z ->
                !nearby.contains(z)
                    || z.isDead()
                    || ServerMap.instance.zombieMap.get(z.getOnlineID()) != z);
    for (IsoZombie z : nearby) {
      if (actor.isDead()) break;
      if (z.isDead()
          || z.getOnlineID() < 0
          || ServerMap.instance.zombieMap.get(z.getOnlineID()) != z) continue;
      var entry = entries.get(z);
      if (entry == null || entry.id() != z.getOnlineID()) {
        entry = new Entry(z.getOnlineID(), new CivilianBite());
        entries.put(z, entry);
      }
      var owner = z.getOwner();
      boolean fresh =
          owner != null
              && owner.isFullyConnected()
              && z.lastRemoteUpdate >= 0
              && z.lastRemoteUpdate <= 600;
      if (owner != null) owners.add(owner);
      boolean attack = "attack".equalsIgnoreCase(z.getRealState());
      if (entry.bite().contact(now, owner, attack, fresh, z.getTarget() == actor && reachable(z))) {
        apply(z);
        ownReaction(now);
      }
    }
    actor.getBodyDamage().Update();
  }

  public void dispose() {
    watchers.remove(this);
    entries.clear();
  }

  private boolean reachable(IsoZombie z) {
    if (z.getSquare() == null
        || actor.getSquare() == null
        || actor.isOnFloor()
        || Math.abs(actor.getZ() - z.getZ()) > .1
        || Math.hypot(actor.getX() - z.getX(), actor.getY() - z.getY()) > 1.15) return false;
    var sight =
        LosUtil.lineClear(
            actor.getCell(),
            (int) Math.floor(actor.getX()),
            (int) Math.floor(actor.getY()),
            (int) actor.getZ(),
            (int) Math.floor(z.getX()),
            (int) Math.floor(z.getY()),
            (int) z.getZ(),
            false);
    return sight == LosUtil.TestResults.Clear;
  }

  private void apply(IsoZombie z) {
    int wounds = wounds();
    float health = actor.getBodyDamage().getHealth();
    attempts++;
    actor.setAttackedBy(z);
    actor.getBodyDamage().AddRandomDamageFromZombie(z, null, -1);
    actor.getBodyDamage().Update();
    boolean damaged = wounds() > wounds || actor.getBodyDamage().getHealth() < health - .01f;
    if (damaged) {
      damagingHits++;
      lastDamage =
          new Contact(
              token,
              damagingHits,
              z.getOnlineID(),
              health,
              actor.getBodyDamage().getHealth(),
              actor.isDead());
    }
    int part = -1;
    var parts = actor.getBodyDamage().getBodyParts();
    for (int i = 0; i < parts.size(); i++)
      if (parts.get(i).bitten() || parts.get(i).scratched() || parts.get(i).isCut()) {
        part = i;
        break;
      }
    var packet = new ZombieHitPlayerPacket();
    packet.set(z, actor, damaged, actor.getHitReaction(), part);
    for (var connection : GameServer.udpEngine.connections) {
      if (connection == null
          || !connection.isFullyConnected()
          || !connection.isRelevantTo(actor.getX(), actor.getY())) continue;
      var writer = connection.startPacket();
      PacketTypes.PacketType.ZombieHitPlayer.doPacket(writer);
      packet.write(writer);
      PacketTypes.PacketType.ZombieHitPlayer.send(connection);
      INetworkPacket.send(connection, PacketTypes.PacketType.PlayerInjuries, actor);
      packets++;
    }
  }

  private int wounds() {
    int n = 0;
    for (var part : actor.getBodyDamage().getBodyParts())
      if (part.bitten() || part.scratched() || part.isCut() || part.deepWounded()) n++;
    return n;
  }
}
