package net.lofers.scenario;

import java.util.*;
import zombie.characters.*;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.popman.NetworkZombieManager;

/**
 * Bounded, short-lived scene ownership. Admission must first verify client-loaded footprints.
 * Expiry returns control to stock ownership; native manager bookkeeping is always used.
 */
public final class OffscreenZombieLease {
  private record Lease(IsoPlayer viewer, UdpConnection connection, float x, float y, long until) {}

  private static final IdentityHashMap<IsoZombie, Lease> leases = new IdentityHashMap<>();

  static void renew(IsoZombie z, IsoPlayer viewer, long freshForMillis) {
    GameHooks.ownThread();
    var c = GameServer.getConnectionFromPlayer(viewer);
    if (freshForMillis <= 0
        || z == null
        || z.isDead()
        || c == null
        || !c.isFullyConnected()
        || GameServer.isDelayedDisconnect(c)) return;
    if (!leases.containsKey(z) && leases.size() >= 128)
      throw new IllegalStateException("scene_owner_budget");
    if (Math.hypot(z.getX() - viewer.getX(), z.getY() - viewer.getY()) > 96) return;
    leases.put(
        z,
        new Lease(
            viewer,
            c,
            viewer.getX(),
            viewer.getY(),
            System.nanoTime() + Math.min(600, freshForMillis) * 1_000_000L));
  }

  static void release(IsoZombie z) {
    leases.remove(z);
  }

  public static boolean maintain(IsoZombie z) {
    Lease l = leases.get(z);
    if (l == null) return false;
    if (!GameServer.server
        || System.nanoTime() > l.until
        || z.isDead()
        || z.getSquare() == null
        || !l.connection.isFullyConnected()
        || GameServer.isDelayedDisconnect(l.connection)
        || !GameServer.Players.contains(l.viewer)
        || Math.hypot(l.viewer.getX() - l.x, l.viewer.getY() - l.y) > 1
        || Math.hypot(z.getX() - l.viewer.getX(), z.getY() - l.viewer.getY()) > 96) {
      leases.remove(z);
      return false;
    }
    NetworkZombieManager.getInstance().moveZombie(z, l.connection, l.viewer);
    return true;
  }
}
