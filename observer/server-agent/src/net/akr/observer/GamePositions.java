package net.akr.observer;

import java.lang.reflect.Method;
import java.util.*;

/** The only game adapter. Called exclusively on the game's main loop thread. */
final class GamePositions {
  private final Class<?> server;
  private final Method players, username, descriptor, fullName, x, y, z, dead;
  private final IdentityHashMap<Object, String> connections = new IdentityHashMap<>();
  private Thread gameThread;

  GamePositions(ClassLoader loader, String expectedWorld) throws ReflectiveOperationException {
    Class<?> core = Class.forName("zombie.core.Core", false, loader);
    Object instance = core.getMethod("getInstance").invoke(null);
    // getVersionNumber() on this build returns only 42.20. The full version
    // includes the .4 hotfix and commit, matching the dedicated startup log.
    if (!"42.20.4 b0bbce05d5".equals(core.getMethod("getVersion").invoke(instance))) {
      throw new IllegalStateException("Unsupported game version");
    }
    server = Class.forName("zombie.network.GameServer", false, loader);
    Class<?> player = Class.forName("zombie.characters.IsoPlayer", false, loader);
    Class<?> desc = Class.forName("zombie.characters.SurvivorDesc", false, loader);
    if (!(boolean) server.getField("server").get(null)
        || !expectedWorld.equals(server.getField("serverName").get(null))) {
      throw new IllegalStateException("Not the configured dedicated world");
    }
    players = server.getMethod("getPlayers");
    username = player.getMethod("getUsername");
    descriptor = player.getMethod("getDescriptor");
    fullName = desc.getMethod("getFullname");
    x = player.getMethod("getX");
    y = player.getMethod("getY");
    z = player.getMethod("getZ");
    dead = player.getMethod("isDead");
  }

  List<PositionAgent.Player> capture() throws ReflectiveOperationException {
    if (gameThread == null) gameThread = Thread.currentThread();
    if (gameThread != Thread.currentThread())
      throw new IllegalStateException("Wrong capture thread");
    List<?> online = (List<?>) players.invoke(null);
    if (online.size() > 128) throw new IllegalStateException("Too many players");
    Set<Object> present = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<String> names = new HashSet<>();
    List<PositionAgent.Player> result = new ArrayList<>(online.size());
    for (Object p : online) {
      String name = text(username.invoke(p), 128);
      String character = text(fullName.invoke(descriptor.invoke(p)), 128);
      if (!names.add(name)) throw new IllegalStateException("Duplicate username");
      double px = coordinate(x.invoke(p), -200000, 200000);
      double py = coordinate(y.invoke(p), -200000, 200000);
      double pz = coordinate(z.invoke(p), -32, 64);
      present.add(p);
      String connection = connections.computeIfAbsent(p, unused -> UUID.randomUUID().toString());
      result.add(
          new PositionAgent.Player(
              name, character, px, py, pz, (boolean) dead.invoke(p), connection));
    }
    connections.keySet().retainAll(present);
    return List.copyOf(result);
  }

  private static String text(Object value, int max) {
    if (!(value instanceof String s)
        || s.isBlank()
        || s.length() > max
        || s.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid text");
    return s;
  }

  private static double coordinate(Object value, double min, double max) {
    double n = ((Number) value).doubleValue();
    if (!Double.isFinite(n) || n < min || n > max)
      throw new IllegalArgumentException("Invalid position");
    return n;
  }
}
