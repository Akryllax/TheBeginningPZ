package net.akr.scenario;

import java.util.*;
import net.akr.scenario.npc.core.CivilianPool;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoWorld;

/** Same-pool real-house routine, closed-exit recovery and independent four-resident checks. */
final class NativeHouseRoutineBatch {
  private final String epoch;
  private NativeHouseScene scene;
  private NativeCivilianActors actors;
  private CivilianPool<IsoPlayer> pool;
  private final List<CivilianPool.Token> tokens = new ArrayList<>();
  private final List<IsoPlayer> bodies = new ArrayList<>();
  private final List<NativeResidentController> controllers = new ArrayList<>();
  private final List<NativeItemCollection> collections = new ArrayList<>();
  private final Set<Integer> outside = new HashSet<>();
  private int phase, round;
  private long tick, started, activeAt, nextReport;
  private boolean unblocked, independent;
  final ProbeTiming work = new ProbeTiming();
  int completed, blockedChecks;

  NativeHouseRoutineBatch(String epoch) {
    this.epoch = epoch;
  }

  NativeCivilianActors metricActors() {
    return actors;
  }

  int occupied() {
    return pool == null ? 0 : pool.occupied();
  }

  int constructed() {
    return actors == null ? 0 : actors.constructed;
  }

  int reused() {
    return actors == null ? 0 : actors.reused;
  }

  int parked() {
    return actors == null ? 0 : actors.parkedCount();
  }

  String summary() {
    return "round="
        + round
        + " phase="
        + phase
        + " completed="
        + completed
        + " blocked_exit_checks="
        + blockedChecks
        + " independent="
        + independent
        + " constructed="
        + constructed()
        + " occupied="
        + occupied()
        + " navigation="
        + controllers.stream().map(NativeResidentController::routineNavigationSummary).toList();
  }

  boolean tick(long now) throws Exception {
    long began = System.nanoTime();
    try {
      if (started == 0) started = now;
      if (now - started > 480_000_000_000L)
        throw new IllegalStateException(
            "house_timeout:"
                + summary()
                + controllers.stream()
                    .map(c -> c.logical().rawget("reason") + ":" + c.wireTrace())
                    .toList());
      zombie.network.ServerMap.instance.characterIn(
          Math.floorDiv(NativeHouseScene.ORIGIN.x(), 8),
          Math.floorDiv(NativeHouseScene.ORIGIN.y(), 8),
          7);
      if (scene == null) scene = new NativeHouseScene();
      if (phase == 0) {
        if (!scene.survey()) return false;
        System.out.println(
            "[HouseRoutine] surveyed homes="
                + scene.homes.subList(0, 4)
                + " destination="
                + scene.activity
                + " exits="
                + scene.exits.size());
        actors = new NativeCivilianActors(IsoWorld.getWorldVersion(), false, 4);
        pool = new CivilianPool<>(epoch + "-house", actors, 4);
        phase = 1;
      }
      pool.tick(++tick, now);
      if (phase == 1) {
        if (actors.constructed < 4) {
          var home = scene.homes.get(actors.constructed);
          actors.prewarmOne(home.x(), home.y(), home.z());
          return false;
        }
        for (int i = 0; i < (round == 2 ? 4 : 1); i++) {
          String id = "house-" + round + "-" + i;
          var h = scene.homes.get(i);
          actors.profile(
              id,
              new NativeCivilianActors.Profile(
                  id, "Generic01", false, h.x() + .5f, h.y() + .5f, 0));
          var token = pool.reserve(id, 1, true, null);
          if (token == null) throw new IllegalStateException("house_pool_reservation");
          tokens.add(token);
        }
        phase = 2;
      }
      if (phase == 2) {
        for (var token : tokens) if (pool.body(token) == null) return false;
        if (!scene.selectActivity(pool.body(tokens.getFirst()))) return false;
        for (var token : tokens) {
          var body = pool.body(token);
          bodies.add(body);
          var c = new NativeResidentController(pool, actors, token, scene.activity);
          c.nativeRoutineRouting();
          controllers.add(c);
          var collection = scene.collection(body, token.resident());
          collections.add(collection);
          c.collectAtActivity(collection);
        }
        scene.prepareDoors(bodies.getFirst());
        if (round == 1) scene.blockExits(true);
        if (round == 2) collections.getFirst().extraDelayNanos = 30_000_000_000L;
        activeAt = now;
        phase = 3;
      }
      if (phase == 3) {
        if (now >= nextReport) {
          System.out.println(
              "[HouseRoutine] progress "
                  + summary()
                  + controllers.stream()
                      .map(c -> c.logical().rawget("reason") + ":" + c.wireTrace())
                      .toList());
          nextReport = now + 10_000_000_000L;
        }
        if (round == 1 && !unblocked) {
          if (now - activeAt < 7_000_000_000L) {
            if (scene.outside(bodies.getFirst()))
              throw new IllegalStateException("house_barricaded_exit_crossed");
          } else {
            scene.blockExits(false);
            controllers.forEach(NativeResidentController::geometryChanged);
            unblocked = true;
            blockedChecks++;
          }
        }
        boolean all = true;
        for (int i = 0; i < controllers.size(); i++) {
          var c = controllers.get(i);
          var body = bodies.get(i);
          c.tick(now, true);
          if (c.unresolved()) throw new IllegalStateException("house_controller_unresolved");
          if (scene.outside(body)) outside.add(i);
          var g = (KahluaTable) c.logical().rawget("goalPlan");
          boolean done = "complete".equals(g.rawget("status"));
          all &= done;
          if (done
              && (!outside.contains(i)
                  || collections.get(i).transfers != 1
                  || body.getSquare().getBuilding()
                      != NativeHouseScene.square(scene.homes.get(i)).getBuilding()))
            throw new IllegalStateException("house_roundtrip_effect_unconfirmed");
          if (round == 2
              && i > 0
              && done
              && !"complete"
                  .equals(
                      ((KahluaTable) controllers.getFirst().logical().rawget("goalPlan"))
                          .rawget("status"))) independent = true;
        }
        scene.observe();
        if (!all) return false;
        if (scene.observedOpenings == 0)
          throw new IllegalStateException("house_door_not_exercised");
        if (round == 2 && !independent)
          throw new IllegalStateException("house_independence_not_exercised");
        completed += controllers.size();
        System.out.println("[HouseRoutine] round passed " + summary());
        scene.cleanup(bodies.getFirst());
        controllers.forEach(NativeResidentController::dispose);
        for (var token : tokens)
          if (!pool.retire(token)) throw new IllegalStateException("house_retire");
        phase = 4;
      }
      if (phase == 4) {
        CivilianPool.Retired retired;
        while ((retired = pool.pollRetired()) != null) {
          int i = tokens.indexOf(retired.token());
          if (i < 0) throw new IllegalStateException("house_retire_identity");
          actors.park(retired.token(), bodies.get(i), retired.snapshot());
        }
        if (pool.occupied() != 0) return false;
        if (actors.parkedCount() != 4 || actors.constructed != 4)
          throw new IllegalStateException("house_pool_leak");
        if (round == 2) return true;
        round++;
        phase = 1;
        tokens.clear();
        bodies.clear();
        controllers.clear();
        collections.clear();
        outside.clear();
      }
      return false;
    } finally {
      work.add(System.nanoTime() - began);
    }
  }
}
