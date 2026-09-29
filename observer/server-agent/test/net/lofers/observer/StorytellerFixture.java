package net.lofers.observer;

import java.lang.reflect.*;
import java.util.*;

/** Real GlobalModData and Kahlua interface, with controlled primitive table values. */
final class StorytellerFixture {
  static Map<String, Object> state;
  static Map<String, Object> cell;

  private static Object table(Map<?, ?> values) throws Exception {
    Class<?> type = Class.forName("se.krka.kahlua.vm.KahluaTable");
    return Proxy.newProxyInstance(
        type.getClassLoader(),
        new Class<?>[] {type},
        (proxy, method, args) -> {
          if (method.getName().equals("rawget")) return values.get(args[0]);
          throw new AssertionError("Unexpected table access: " + method.getName());
        });
  }

  static void setup(boolean broken) throws Exception {
    cell =
        new HashMap<>(
            Map.of(
                "x",
                10624.0,
                "y",
                9888.0,
                "z",
                0.0,
                "dwell",
                2.0,
                "wealth",
                15.0,
                "confidence",
                .25,
                "age_hours",
                1.0));
    state =
        new HashMap<>(
            Map.of(
                "schema",
                1.0,
                "tick",
                1.0,
                "world_age_hours",
                2.0,
                "phase",
                "outbreak",
                "mode",
                "observe",
                "pressure",
                5.0,
                "budget",
                3.0,
                "online_players",
                1.0,
                "npc_count",
                -1.0));
    state.put("cells", table(Map.of(1, table(cell))));
    state.put(
        "decisions",
        table(
            Map.of(
                1,
                table(
                    Map.of(
                        "id",
                        "fixture-1",
                        "event",
                        "patrol",
                        "outcome",
                        "deferred",
                        "reason",
                        "Grace period",
                        "at_hour",
                        2.0)))));
    state.put("timings_ms", table(Map.of("last", .1, "p95", .2, "p99", .3, "max", .4)));
    state.put("health", "observing");
    state.put("cell_count", 1.0);
    Class<?> data = Class.forName("zombie.world.moddata.GlobalModData");
    Object instance = data.getField("instance").get(null);
    data.getMethod("add", String.class, Class.forName("se.krka.kahlua.vm.KahluaTable"))
        .invoke(instance, "LofersStoryteller", table(Map.of("telemetry", table(state))));
    var reader = new GameStoryteller(StorytellerFixture.class.getClassLoader());
    GameStoryteller.Snapshot snapshot = null;
    for (int i = 0; i < 10; i++) {
      state.put("tick", (double) i + 1);
      snapshot = reader.capture();
      if (!snapshot.cells().isEmpty()) break;
    }
    if (snapshot.cells().isEmpty())
      throw new AssertionError("No storyteller cells captured after warmup");
    cell.put("wealth", 99.0);
    if (snapshot.cells().getFirst().wealth() != 15)
      throw new AssertionError("Live table alias escaped");
    if (reader.capture() != null) throw new AssertionError("Unchanged publication marked fresh");
    boolean[] rejected = {false};
    Thread wrong =
        new Thread(
            () -> {
              try {
                reader.capture();
              } catch (IllegalStateException expected) {
                rejected[0] = true;
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
            });
    wrong.start();
    wrong.join();
    if (!rejected[0]) throw new AssertionError("Storyteller allowed off game thread");
    ClassLoader bad =
        new ClassLoader(StorytellerFixture.class.getClassLoader()) {
          @Override
          public java.io.InputStream getResourceAsStream(String name) {
            return name.endsWith("GlobalModData.class")
                ? new java.io.ByteArrayInputStream(new byte[] {1, 2, 3})
                : super.getResourceAsStream(name);
          }
        };
    try {
      new GameStoryteller(bad);
      throw new AssertionError("Unknown storyteller class accepted");
    } catch (IllegalStateException expected) {
    }
    benchmark();
    if (broken) state.put("schema", 2.0);
  }

  private static void benchmark() throws Exception {
    Object originalCells = state.get("cells"), originalDecisions = state.get("decisions");
    Map<Integer, Object> cells = new HashMap<>(), decisions = new HashMap<>();
    for (int i = 1; i <= 70; i++)
      cells.put(
          i,
          table(
              Map.of(
                  "x",
                  i * 32.0,
                  "y",
                  9888.0,
                  "z",
                  0.0,
                  "dwell",
                  2.0,
                  "wealth",
                  15.0,
                  "confidence",
                  .25,
                  "age_hours",
                  1.0)));
    for (int i = 1; i <= 40; i++)
      decisions.put(
          i,
          table(
              Map.of(
                  "id",
                  "fixture-" + i,
                  "event",
                  "patrol",
                  "outcome",
                  "deferred",
                  "reason",
                  "Grace period",
                  "at_hour",
                  2.0)));
    state.put("cells", table(cells));
    state.put("decisions", table(decisions));
    GameStoryteller reader = new GameStoryteller(StorytellerFixture.class.getClassLoader());
    List<Double> times = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      state.put("tick", 1000.0 + i);
      var sample = reader.capture();
      if (sample.cells().size() > 64 || sample.decisions().size() > 32 || !sample.truncated())
        throw new AssertionError("Storyteller preview caps failed");
      if (i >= 50) times.add(sample.captureMs());
    }
    Collections.sort(times);
    System.out.printf(
        Locale.ROOT,
        "storyteller capture p95=%.3fms p99=%.3fms max=%.3fms (200 warmed bounded samples)%n",
        times.get(190),
        times.get(198),
        times.getLast());
    state.put("cells", originalCells);
    state.put("decisions", originalDecisions);
  }
}
