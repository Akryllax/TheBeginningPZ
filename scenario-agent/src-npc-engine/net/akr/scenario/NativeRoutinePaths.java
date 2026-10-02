package net.akr.scenario;

import java.util.HashMap;
import java.util.Map;
import net.akr.scenario.npc.core.CivilianNavigation.Key;
import net.akr.scenario.npc.core.CivilianNavigation.Tile;
import net.akr.scenario.npc.core.CivilianPathRequests;
import net.akr.scenario.npc.core.CivilianPool;
import zombie.characters.IsoPlayer;

/** Shared bounded native route jobs; movement and world validation remain on the game thread. */
final class NativeRoutinePaths {
  private static final Map<CivilianPool.Token, Key> current = new HashMap<>();
  private static final CivilianPathRequests<IsoPlayer> requests =
      new CivilianPathRequests<>(
          new NativeCivilianNavigation(), k -> k.equals(current.get(k.actor())));

  static boolean submit(Key key, IsoPlayer body, Tile from, Tile goal, long now) {
    GameHooks.ownThread();
    if (!current.containsKey(key.actor()) && current.size() >= 4) return false;
    current.put(key.actor(), key);
    return requests.submit(key, body, point(from), point(goal), now);
  }

  private static CivilianPathRequests.Point point(Tile tile) {
    return new CivilianPathRequests.Point(tile.x() + .5f, tile.y() + .5f, tile.z(), 0);
  }

  static CivilianPathRequests.Reply poll(CivilianPool.Token token, long now) {
    GameHooks.ownThread();
    requests.tick(now, now);
    return requests.poll(token);
  }

  static void cancel(CivilianPool.Token token) {
    GameHooks.ownThread();
    requests.cancel(token);
    current.remove(token);
  }

  private NativeRoutinePaths() {}
}
