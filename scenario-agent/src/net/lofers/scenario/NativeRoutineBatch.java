package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;

/** Zero-client end-to-end worker/routine and native gait/reaction fixture. */
final class NativeRoutineBatch {
  private final String epoch;
  private final Tile origin;
  private NativeCivilianActors actors;
  private CivilianPool<IsoPlayer> pool;
  private CivilianPool.Token token;
  private IsoPlayer body;
  private NativeResidentController controller;
  private CivilianTraversal run;
  private Key key;
  private int phase, round;
  private long tick, started, runAt, reactionAt;
  private long lastProgress;
  private boolean reactionStarted, planningReaction, continuation, softCancelled;
  double runSpeed;
  int workerPlans, assignments;
  final ProbeTiming work = new ProbeTiming();
  private CivilianPool.Token[] strideTokens;
  private IsoPlayer[] strideBodies;
  private NativeStrideComparison stride;
  private int strideCandidate, strideChecked;
  private Tile strideOrigin;

  String strideResult() {
    return stride == null
        ? "not_run"
        : "walk_seconds=" + stride.seconds[0] + " run_seconds=" + stride.seconds[1];
  }

  NativeRoutineBatch(String epoch, Tile origin) {
    this.epoch = epoch;
    this.origin = origin;
  }

  private void require(boolean ok, String reason) {
    if (!ok) throw new IllegalStateException("routine:" + reason);
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

  int occupied() {
    return pool == null ? 0 : pool.occupied();
  }

  boolean tick(long now) throws Exception {
    zombie.network.ServerMap.instance.characterIn(
        Math.floorDiv(origin.x(), 8), Math.floorDiv(origin.y() + 40, 8), 5);
    if (started == 0) started = now;
    if (now - started > 300_000_000_000L)
      throw new IllegalStateException(
          "routine_timeout:" + phase + ":" + (controller == null ? "" : controller.trace()));
    if (now - lastProgress > 5_000_000_000L) {
      lastProgress = now;
      System.out.println(
          "[NativeRoutine] phase="
              + phase
              + " round="
              + round
              + " state="
              + (controller == null ? "" : controller.state())
              + " xy="
              + (body == null ? "" : body.getX() + "," + body.getY())
              + " source="
              + (controller == null ? "" : controller.planSource())
              + " planner="
              + ResidentPlannerBridge.diagnostic
              + " trace="
              + (controller == null ? "" : controller.wireTrace()));
    }
    long began = System.nanoTime();
    try {
      if (actors == null) {
        actors = new NativeCivilianActors(zombie.iso.IsoWorld.getWorldVersion(), false, 4);
        pool = new CivilianPool<>(epoch + "-routine", actors, 4);
      }
      pool.tick(++tick, now);
      switch (phase) {
        case 0 -> {
          if (actors.constructed < 4) {
            actors.prewarmOne(origin.x(), origin.y(), origin.z());
            return false;
          }
          String id = "routine-" + round;
          actors.profile(
              id,
              new NativeCivilianActors.Profile(
                  id, "Generic01", round % 2 != 0, origin.x() + .5f, origin.y() + .5f, origin.z()));
          token = pool.reserve(id, 1, true, null);
          require(token != null, "reserve");
          assignments++;
          phase = 1;
        }
        case 1 -> {
          body = pool.body(token);
          if (body == null) return false;
          NativeMovementWireCheck.run(actors, body);
          controller =
              new NativeResidentController(
                  pool,
                  actors,
                  token,
                  new Tile(origin.x() + (round == 0 ? 4 : 0), origin.y() + 4, origin.z()));
          phase = 2;
        }
        case 2 -> {
          if (round == 0 && !planningReaction && controller.pathAgeMs(now) > 0) {
            body.setHitReaction("Bite");
            body.changeState(zombie.ai.states.PlayerHitReactionState.instance());
            controller.injuries.ownReaction(now);
            planningReaction = true;
          }
          controller.tick(now, true);
          require(!controller.unresolved(), "controller");
          if (!(controller.logical().rawget("goalPlan") instanceof KahluaTable g))
            throw new IllegalStateException("durable_state_not_registered");
          if (!"complete".equals(g.rawget("status"))) return false;
          require("worker".equals(g.rawget("source")), "worker_not_used:" + g.rawget("source"));
          workerPlans++;
          if (round == 0)
            require(controller.travelled < 13, "diagonal_route_not_used:" + controller.travelled);
          require(
              Math.hypot(body.getX() - origin.x() - .5, body.getY() - origin.y() - .5) < .2,
              "home_not_reached");
          controller.dispose();
          phase = 3;
          // Reuse the same physical Actor for the independent native RUN check.
          key =
              new Key(token, ((Number) controller.logical().rawget("revision")).longValue() + 1, 1);
          require(pool.acceptIntent(token, key.actionRevision()), "run_revision");
          var route = new ArrayList<Step>();
          for (int i = 0; i <= 4; i++)
            route.add(new Step(new Tile(origin.x(), origin.y() + i, origin.z()), Action.WALK));
          var port = new NativeCivilianTraversal(token, body, actors);
          port.speed(2.6);
          run = new CivilianTraversal(port);
          run.start(new Result(key, Status.FOUND, route, 0, 0));
          runAt = now;
          continuation = false;
        }
        case 3 -> {
          run.tick(key);
          key = run.view().key();
          require(
              run.view().phase() != CivilianTraversal.Phase.BLOCKED
                  && run.view().phase() != CivilianTraversal.Phase.UNRESOLVED,
              "native_run_blocked");
          if (!continuation && run.completedEdges() >= 1) {
            var next = new Key(token, key.actionRevision() + 1, 1);
            require(pool.acceptIntent(token, next.actionRevision()), "continuation_revision");
            var tail = new ArrayList<Step>();
            for (int i = 4; i <= 8; i++)
              tail.add(new Step(new Tile(origin.x(), origin.y() + i, origin.z()), Action.WALK));
            require(
                run.replaceAtBoundary(new Result(next, Status.FOUND, tail, 0, 0)),
                "native_continuation_join");
            continuation = true;
          }
          require(run.buffered() <= 8, "native_window_bound");
          if (run.view().phase() == CivilianTraversal.Phase.RUNNING && run.completedEdges() > 0)
            require(actors.gait(body).equals("RUN"), "handoff_emitted_idle");
          if (run.view().phase() != CivilianTraversal.Phase.COMPLETE) return false;
          require(run.completedEdges() == 8, "continuation_distance");
          runSpeed = 8 / ((now - runAt) / 1e9);
          require(runSpeed > 3 && runSpeed <= 5.5, "run_speed:" + runSpeed);
          key = new Key(token, key.actionRevision() + 1, 1);
          require(pool.acceptIntent(token, key.actionRevision()), "cancel_revision");
          var back = new ArrayList<Step>();
          for (int i = 8; i >= 4; i--)
            back.add(new Step(new Tile(origin.x(), origin.y() + i, origin.z()), Action.WALK));
          var port = new NativeCivilianTraversal(token, body, actors);
          port.speed(2.6);
          run = new CivilianTraversal(port);
          run.start(new Result(key, Status.FOUND, back, 0, 0));
          softCancelled = false;
          phase = 7;
        }
        case 7 -> {
          run.tick(key);
          if (!softCancelled && body.getY() < origin.y() + 8.45) {
            run.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);
            softCancelled = true;
          }
          if (run.view().phase() == CivilianTraversal.Phase.RUNNING) return false;
          require(
              softCancelled
                  && run.view().phase() == CivilianTraversal.Phase.CANCELLED
                  && run.completedEdges() == 1,
              "native_soft_cancel_boundary");
          require(
              Math.abs(body.getY() - (origin.y() + 7.5)) < .01 && actors.gait(body).equals("IDLE"),
              "native_soft_cancel_position_gait");
          body.setHitReaction("Bite");
          body.changeState(zombie.ai.states.PlayerHitReactionState.instance());
          controller.injuries.ownReaction(now);
          reactionAt = now;
          require(body.getIgnoreMovement(), "native_reaction_did_not_lock");
          phase = 4;
        }
        case 4 -> {
          boolean held = controller.injuries.reacting(now);
          if (now - reactionAt < 1_000_000_000L) {
            require(held, "premature_reaction_resume");
            return false;
          }
          require(
              !held && !body.getIgnoreMovement() && !body.hasHitReaction(),
              "reaction_not_released");
          require(pool.retire(token), "retire");
          phase = 5;
        }
        case 5 -> {
          var retired = pool.pollRetired();
          if (retired == null) return false;
          require(retired.token().equals(token), "retirement_identity");
          actors.park(token, body, retired.snapshot());
          require(
              pool.occupied() == 0 && actors.constructed == 4 && actors.parkedCount() == 4,
              "pool_leak");
          if (++round < 8) {
            phase = 0;
            controller = null;
            return false;
          }
          phase = 6;
          reactionAt = now;
        }
        case 6 -> {
          if (ResidentPlannerBridge.outcomes.size() == 0) {
            phase = 11;
            break;
          }
          require(
              now - reactionAt < 10_000_000_000L,
              "terminal_receipts_not_acknowledged:" + ResidentPlannerBridge.outcomes.size());
        }
        case 11 -> {
          // Read-only, bounded preflight: select two parallel clear lanes before assigning bodies.
          for (int budget = 0; budget < 8 && strideChecked < 100; budget++) {
            require(strideCandidate < 17, "stride_no_clear_parallel_lanes");
            int offset =
                strideCandidate == 0
                    ? 0
                    : ((strideCandidate + 1) / 2) * (strideCandidate % 2 == 0 ? -2 : 2);
            strideOrigin = new Tile(origin.x() + offset, origin.y(), 0);
            int lane = strideChecked / 50, edge = strideChecked % 50;
            var from = new Tile(strideOrigin.x() + lane * 3, strideOrigin.y() + edge, 0);
            var to = new Tile(from.x(), from.y() + 1, 0);
            var geometry = NativeCivilianGeometry.classify(body, from, to);
            if (!geometry.permitted() || geometry.action() != Action.WALK) {
              strideCandidate++;
              strideChecked = 0;
            } else strideChecked++;
          }
          if (strideChecked == 100) {
            strideTokens = new CivilianPool.Token[2];
            strideBodies = new IsoPlayer[2];
            for (int i = 0; i < 2; i++) {
              String id = "stride-" + i;
              actors.profile(
                  id,
                  new NativeCivilianActors.Profile(
                      id,
                      "Generic01",
                      false,
                      strideOrigin.x() + i * 3 + .5f,
                      strideOrigin.y() + .5f,
                      0));
              strideTokens[i] = pool.reserve(id, 1, true, null);
              require(strideTokens[i] != null, "stride_reserve");
            }
            phase = 8;
          }
        }
        case 8 -> {
          for (int i = 0; i < 2; i++) {
            strideBodies[i] = pool.body(strideTokens[i]);
            if (strideBodies[i] == null) return false;
          }
          stride = new NativeStrideComparison(pool, actors, strideTokens, strideBodies);
          phase = 9;
        }
        case 9 -> {
          if (!stride.tick(now)) return false;
          System.out.println("[NativeStride] " + strideResult());
          for (var t : strideTokens) require(pool.retire(t), "stride_retire");
          phase = 10;
        }
        case 10 -> {
          CivilianPool.Retired retired;
          while ((retired = pool.pollRetired()) != null) {
            int i = retired.token().equals(strideTokens[0]) ? 0 : 1;
            require(retired.token().equals(strideTokens[i]), "stride_retired_identity");
            actors.park(retired.token(), strideBodies[i], retired.snapshot());
          }
          if (pool.occupied() == 0) {
            require(actors.parkedCount() == 4 && actors.constructed == 4, "stride_pool_leak");
            return true;
          }
        }
      }
      return false;
    } finally {
      work.add(System.nanoTime() - began);
    }
  }
}
