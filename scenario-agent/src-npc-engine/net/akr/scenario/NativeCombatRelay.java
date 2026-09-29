package net.akr.scenario;

import java.util.List;
import zombie.characters.*;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoObject;
import zombie.network.*;
import zombie.network.fields.hit.TracerInfo;
import zombie.network.packets.hit.PlayerHitZombiePacket;

/** Scoped forwarding of the exact stock contact result, never a second server damage call. */
public final class NativeCombatRelay {
  private static IsoPlayer attacker;
  private static IsoZombie target;
  static int packets, contacts;
  static float damage;
  static int skippedUi;

  static void begin(IsoPlayer a, IsoZombie z) {
    GameHooks.ownThread();
    if (attacker != null || !GameServer.server || !NativeCivilianActors.owns(a))
      throw new IllegalStateException("combat_relay_scope");
    attacker = a;
    target = z;
    packets = contacts = skippedUi = 0;
    damage = 0;
  }

  static void end() {
    GameHooks.ownThread();
    attacker = null;
    target = null;
  }

  /** Preserve native pain/fatigue damage modifiers; only the absent client UI is omitted. */
  public static void wiggle(zombie.ui.MoodlesUI ui, zombie.scripting.objects.MoodleType moodle) {
    if (attacker != null && GameServer.server) {
      GameHooks.ownThread();
      skippedUi++;
      return;
    }
    ui.wiggle(moodle);
  }

  public static boolean scoped(IsoGameCharacter a) {
    return attacker != null && a == attacker && GameServer.server;
  }

  /** Runs after native candidate selection but before any hit/weapon/property effects. */
  public static void filterTargets(IsoGameCharacter a) {
    if (!scoped(a)) return;
    GameHooks.ownThread();
    var hits = a.getHitInfoList();
    boolean kept = false;
    for (int i = hits.size() - 1; i >= 0; i--) {
      var hit = hits.get(i);
      if (hit.getObject() == target && !kept) {
        kept = true;
        continue;
      }
      hits.remove(i);
      zombie.CombatManager.getInstance().hitInfoPool.release(hit);
    }
  }

  public static void sendPlayerHit(
      IsoGameCharacter a,
      IsoObject t,
      HandWeapon w,
      float d,
      boolean ignore,
      float range,
      boolean critical,
      List<TracerInfo> tracers,
      boolean helmet,
      boolean head,
      boolean legs,
      boolean knife) {
    if (attacker == null || a != attacker || !GameServer.server) {
      GameClient.sendPlayerHit(
          a, t, w, d, ignore, range, critical, tracers, helmet, head, legs, knife);
      return;
    }
    GameHooks.ownThread();
    if (t == null)
      return; // Stock maintenance/miss notice; contact packet already carries the swing.
    if (t != target) throw new IllegalStateException("combat_probe_unexpected_contact");
    if (contacts != 0) throw new IllegalStateException("duplicate_native_contact");
    contacts++;
    damage = d;
    var packet = new PlayerHitZombiePacket();
    packet.set(attacker, w, ignore, critical, tracers, target, d, range, helmet, head, legs, knife);
    for (var c : GameServer.udpEngine.connections) {
      if (c == null || !c.isFullyConnected() || !c.isRelevantTo(a.getX(), a.getY())) continue;
      var writer = c.startPacket();
      PacketTypes.PacketType.PlayerHitZombie.doPacket(writer);
      packet.write(writer);
      PacketTypes.PacketType.PlayerHitZombie.send(c);
      packets++;
    }
  }
}
