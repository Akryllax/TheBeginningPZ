package net.akr.observer;

import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** Reads an existing server mask on the game thread; never calls update/save/reveal. */
final class GameExploration {
  record Mask(String username, int minX, int minY, int maxX, int maxY, int version, byte[] flags) {}

  private final Field visitedInstance, dictionary, worldInstance;
  private final Method metaGrid, worldVersion;
  private final Field minX, minY, maxX, maxY;
  private Thread owner;

  GameExploration(ClassLoader loader) throws Exception {
    verify(
        loader,
        "zombie/worldMap/WorldMapVisitedServer.class",
        BuildProfile.CLASS_HASHES.get("zombie/worldMap/WorldMapVisitedServer"));
    verify(
        loader,
        "zombie/worldMap/WorldMapVisited.class",
        BuildProfile.CLASS_HASHES.get("zombie/worldMap/WorldMapVisited"));
    Class<?> visited = Class.forName("zombie.worldMap.WorldMapVisitedServer", false, loader);
    visitedInstance = visited.getDeclaredField("instance");
    dictionary = visited.getDeclaredField("dictionary");
    visitedInstance.setAccessible(true);
    dictionary.setAccessible(true);
    Class<?> world = Class.forName("zombie.iso.IsoWorld", false, loader);
    worldInstance = world.getField("instance");
    metaGrid = world.getMethod("getMetaGrid");
    worldVersion = world.getMethod("getWorldVersion");
    Class<?> grid = Class.forName("zombie.iso.IsoMetaGrid", false, loader);
    minX = grid.getField("minX");
    minY = grid.getField("minY");
    maxX = grid.getField("maxX");
    maxY = grid.getField("maxY");
  }

  Mask capture(String username) throws Exception {
    if (owner == null) owner = Thread.currentThread();
    if (owner != Thread.currentThread())
      throw new IllegalStateException("Wrong exploration thread");
    Object visited = visitedInstance.get(null);
    Object world = worldInstance.get(null);
    if (visited == null || world == null) return null;
    Object value = ((Map<?, ?>) dictionary.get(visited)).get(username);
    if (value == null) return null; // A connecting player may not have loaded their mask yet.
    if (!(value instanceof byte[] flags))
      throw new IllegalStateException("Unexpected exploration state");
    Object grid = metaGrid.invoke(world);
    int x0 = minX.getInt(grid),
        y0 = minY.getInt(grid),
        x1 = maxX.getInt(grid),
        y1 = maxY.getInt(grid);
    long expected = (x1 - (long) x0 + 1) * (y1 - (long) y0 + 1) * 16;
    int version = (int) worldVersion.invoke(null);
    if (x1 < x0
        || y1 < y0
        || expected <= 0
        || expected > 32 * 1024 * 1024 - 4
        || expected != flags.length
        || version != 249) {
      throw new IllegalStateException("Unsupported exploration dimensions or version");
    }
    // Detach before compression/network work. The game retains sole ownership
    // of its arrays; the worker receives no game objects or mutable aliases.
    return new Mask(username, x0, y0, x1, y1, version, flags.clone());
  }

  private static void verify(ClassLoader loader, String resource, String expected)
      throws Exception {
    try (var stream = loader.getResourceAsStream(resource)) {
      if (stream == null
          || !expected.equals(
              HexFormat.of()
                  .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())))) {
        throw new IllegalStateException("Unsupported exploration class");
      }
    }
  }
}
