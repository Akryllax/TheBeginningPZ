package net.lofers.observer;

import java.nio.file.*;
import java.util.*;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;

/** Uses the REAL installed RCONServer.class, with controlled player fixtures. */
public final class FixtureMain {
  public static void main(String[] args) throws Exception {
    var update = Class.forName("zombie.network.RCONServer").getMethod("update");
    IsoPlayer player = new IsoPlayer();
    GameServer.online.add(player);
    StorytellerFixture.setup(args.length > 0 && args[0].equals("broken_storyteller"));
    // Use the actual installed exploration class and its private dictionary.
    // Its constructor only allocates the dictionary; never call load/save.
    Class<?> visited = Class.forName("zombie.worldMap.WorldMapVisitedServer");
    Object state = visited.getConstructor().newInstance();
    var instance = visited.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, state);
    var dictionary = visited.getDeclaredField("dictionary");
    dictionary.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, byte[]> masks = (Map<String, byte[]>) dictionary.get(state);
    byte[] flags = new byte[501 * 501 * 16];
    flags[0] = 3;
    masks.put(player.getUsername(), flags);
    GameExploration reader = new GameExploration(FixtureMain.class.getClassLoader());
    var copy = reader.capture(player.getUsername());
    copy.flags()[0] = 1;
    if (flags[0] != 3) throw new AssertionError("Exploration array was aliased");
    boolean[] rejected = {false};
    Thread wrong =
        new Thread(
            () -> {
              try {
                reader.capture(player.getUsername());
              } catch (IllegalStateException expected) {
                rejected[0] = true;
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
            });
    wrong.start();
    wrong.join();
    if (!rejected[0]) throw new AssertionError("Exploration read allowed off game thread");
    ClassLoader bad =
        new ClassLoader(FixtureMain.class.getClassLoader()) {
          @Override
          public java.io.InputStream getResourceAsStream(String name) {
            return name.endsWith("WorldMapVisitedServer.class")
                ? new java.io.ByteArrayInputStream(new byte[] {1, 2, 3})
                : super.getResourceAsStream(name);
          }
        };
    try {
      new GameExploration(bad);
      throw new AssertionError("Unknown exploration class accepted");
    } catch (IllegalStateException expected) {
      /* Known game version required. */
    }
    List<Long> costs = new ArrayList<>();
    long start = System.nanoTime();
    for (int i = 0; i < 650; i++) {
      StorytellerFixture.state.put("tick", 100.0 + i);
      player.x += .1f;
      if (i == 100) flags[1] = 3;
      if (i == 400) player.dead = true;
      if (i == 500) GameServer.online.clear();
      long before = System.nanoTime();
      update.invoke(null);
      costs.add(System.nanoTime() - before);
      Thread.sleep(10);
    }
    Collections.sort(costs);
    System.out.printf(
        Locale.ROOT,
        "fixture p99=%.3fms max=%.3fms elapsed=%.3fs%n",
        costs.get((int) (costs.size() * .99)) / 1e6,
        costs.getLast() / 1e6,
        (System.nanoTime() - start) / 1e9);
    // Unknown bytes must be ignored, without throwing or installing a hook.
    byte[] invalid =
        new PositionTransformer()
            .transform(
                FixtureMain.class.getClassLoader(),
                PositionTransformer.TARGET,
                null,
                null,
                new byte[] {1, 2, 3});
    if (invalid != null) throw new AssertionError("Unsupported class was modified");
    if (flags[0] != 3 || flags[1] != 3 || flags[2] != 0)
      throw new AssertionError("Game exploration was changed by the exporter");
  }
}
