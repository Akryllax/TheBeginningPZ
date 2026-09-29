package net.akr.observer;

import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** Reads only an already-published primitive table. No game writes or inventory reads. */
final class GameStoryteller {
  record Cell(int x, int y, int z, double dwell, double wealth, double confidence, double age) {}

  record Decision(String id, String event, String outcome, String reason, double hour) {}

  record Timings(double last, double p95, double p99, double max) {}

  record Scenario(
      String status,
      String phase,
      double elapsed,
      int residents,
      int materialized,
      int pedestrians,
      int vehicles,
      String workerHealth,
      double computeMs,
      int rejected,
      int leases,
      double last,
      double p95,
      double p99,
      int queue) {}

  record Snapshot(
      long tick,
      double age,
      String phase,
      String mode,
      double pressure,
      double budget,
      int players,
      int npcs,
      Timings timings,
      List<Cell> cells,
      List<Decision> decisions,
      int cellCount,
      int queue,
      int dropped,
      String health,
      boolean truncated,
      double captureMs,
      Scenario scenario) {}

  private final Field instance;
  private final Method get, rawget, rawindex;
  private final Class<?> tableType;
  private Thread owner;
  private long previousTick = -1;

  GameStoryteller(ClassLoader loader) throws Exception {
    verify(
        loader,
        "zombie/world/moddata/GlobalModData.class",
        "61b27632d28a63f92667a727d40614f6ec1e22640a64a65a93508427bcf4d47d");
    verify(
        loader,
        "se/krka/kahlua/vm/KahluaTable.class",
        "398e2f6df2108991fdbf3290e2725236eb71e4d070039a114a3cc1cf5a14af18");
    Class<?> data = Class.forName("zombie.world.moddata.GlobalModData", false, loader);
    tableType = Class.forName("se.krka.kahlua.vm.KahluaTable", false, loader);
    instance = data.getField("instance");
    get = data.getMethod("get", String.class); // Never getOrCreate, transmit or save.
    rawget = tableType.getMethod("rawget", Object.class);
    rawindex = tableType.getMethod("rawget", int.class);
  }

  Snapshot capture() throws Exception {
    if (owner == null) owner = Thread.currentThread();
    if (owner != Thread.currentThread())
      throw new IllegalStateException("Wrong storyteller thread");
    long before = System.nanoTime(), deadline = before + 1_000_000L;
    Object data = instance.get(null);
    if (data == null) return null;
    Object root = get.invoke(data, "AKRStoryteller");
    Object table = value(root, "telemetry");
    if (table == null) return null;
    if (number(table, "schema", 0) != 1)
      throw new IllegalStateException("Unsupported storyteller schema");
    long tick = (long) number(table, "tick", 0);
    if (tick == previousTick)
      return null; // Do not turn a stale mod publication into fresh telemetry.
    Scenario scenario = null;
    Object scenarioRoot = get.invoke(data, "AKRScenario");
    Object st = value(scenarioRoot, "telemetry");
    if (st != null)
      scenario =
          new Scenario(
              text(st, "status", 32),
              text(st, "phase", 32),
              number(st, "elapsed_hours", 0),
              (int) number(st, "residents", 0),
              (int) number(st, "materialized", 0),
              (int) number(st, "pedestrians", 0),
              (int) number(st, "vehicles", 0),
              text(st, "bridge_health", 128),
              number(st, "worker_compute_ms", 0),
              (int) number(st, "plan_rejections", 0),
              (int) number(st, "lease_changes", 0),
              number(st, "last_step_ms", 0),
              number(st, "p95_step_ms", 0),
              number(st, "p99_step_ms", 0),
              (int) number(st, "worker_queue", -1));
    double age = number(table, "world_age_hours", 0),
        pressure = number(table, "pressure", 0),
        budget = number(table, "budget", 0);
    String phase = text(table, "phase", 32),
        mode = text(table, "mode", 32),
        health = text(table, "health", 256);
    int players = (int) number(table, "online_players", 0),
        npcs = (int) number(table, "npc_count", -1),
        cellCount = (int) number(table, "cell_count", 0),
        queue = (int) number(table, "scan_queue", 0),
        dropped = (int) number(table, "dropped_cells", 0);
    Object t = value(table, "timings_ms");
    Timings timings =
        new Timings(
            number(t, "last", 0), number(t, "p95", 0), number(t, "p99", 0), number(t, "max", 0));
    ArrayList<Cell> cells = new ArrayList<>();
    ArrayList<Decision> decisions = new ArrayList<>();
    boolean truncated = false;
    Object source = value(table, "cells");
    if (source != null)
      for (int i = 1; i <= 65; i++) {
        Object c = index(source, i);
        if (c == null) break;
        if (i > 64 || System.nanoTime() > deadline) {
          truncated = true;
          break;
        }
        cells.add(
            new Cell(
                (int) number(c, "x", 0),
                (int) number(c, "y", 0),
                (int) number(c, "z", 0),
                number(c, "dwell", 0),
                number(c, "wealth", 0),
                number(c, "confidence", 0),
                number(c, "age_hours", 0)));
      }
    source = value(table, "decisions");
    if (source != null)
      for (int i = 1; i <= 33; i++) {
        Object d = index(source, i);
        if (d == null) break;
        if (i > 32 || System.nanoTime() > deadline) {
          truncated = true;
          break;
        }
        decisions.add(
            new Decision(
                text(d, "id", 128),
                text(d, "event", 64),
                text(d, "outcome", 64),
                text(d, "reason", 256),
                number(d, "at_hour", 0)));
      }
    previousTick = tick;
    return new Snapshot(
        tick,
        age,
        phase,
        mode,
        pressure,
        budget,
        players,
        npcs,
        timings,
        List.copyOf(cells),
        List.copyOf(decisions),
        cellCount,
        queue,
        dropped,
        health,
        truncated,
        (System.nanoTime() - before) / 1e6,
        scenario);
  }

  private Object value(Object table, String key) throws Exception {
    if (table == null) return null;
    if (!tableType.isInstance(table))
      throw new IllegalStateException("Expected primitive storyteller table");
    return rawget.invoke(table, key);
  }

  private Object index(Object table, int key) throws Exception {
    if (!tableType.isInstance(table)) throw new IllegalStateException("Expected storyteller array");
    return rawindex.invoke(table, key);
  }

  private double number(Object table, String key, double fallback) throws Exception {
    Object v = value(table, key);
    if (v == null) return fallback;
    if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue()))
      throw new IllegalStateException("Invalid storyteller number");
    return n.doubleValue();
  }

  private String text(Object table, String key, int limit) throws Exception {
    Object v = value(table, key);
    if (v == null) return "";
    if (!(v instanceof String s)) throw new IllegalStateException("Invalid storyteller text");
    return s.substring(0, Math.min(limit, s.length()));
  }

  private static void verify(ClassLoader loader, String name, String expected) throws Exception {
    try (var stream = loader.getResourceAsStream(name)) {
      if (stream == null
          || !expected.equals(
              HexFormat.of()
                  .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()))))
        throw new IllegalStateException("Unsupported storyteller game class");
    }
  }
}
