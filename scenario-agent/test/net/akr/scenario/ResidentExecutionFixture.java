package net.akr.scenario;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

import java.util.*;
import net.akr.scenario.npc.core.CivilianGeometryCache;
import net.akr.scenario.npc.core.CivilianGraphCapture;
import net.akr.scenario.npc.core.CivilianPerception;
import net.akr.scenario.npc.core.CivilianPool;
import net.akr.scenario.npc.core.CivilianRouteJoin;
import net.akr.scenario.npc.core.CivilianTraversal;

final class ResidentExecutionFixture {
  static void run() {
    long[] clock = {0}, revision = {1};
    int[] reads = {0};
    Tile a = new Tile(1, 1, 0), b = new Tile(2, 1, 0);
    var source =
        new CivilianGraphCapture.Source() {
          public long revision() {
            return revision[0];
          }

          public List<Edge> edges(Tile t) {
            reads[0]++;
            return List.of(new Edge(b, Action.WALK, 1));
          }

          public double danger(Tile t) {
            return 100;
          }
        };
    var cache = new CivilianGeometryCache(source, () -> clock[0]);
    cache.edges(a);
    cache.edges(a);
    CivilianFixture.check(
        reads[0] == 1 && cache.danger(a) == 0, "cache geometry independent of moving danger");
    revision[0]++;
    cache.edges(a);
    CivilianFixture.check(reads[0] == 2, "map change invalidates geometry");
    clock[0] = 3_000_000_000L;
    cache.edges(a);
    CivilianFixture.check(reads[0] == 3, "expired geometry is observed again");
    int[] begins = {0}, frames = {0}, paused = {0}, ends = {0};
    var port =
        new CivilianTraversal.Port() {
          public void frame() {
            frames[0]++;
          }

          public void endFrame() {
            ends[0]++;
          }

          public void pause() {
            paused[0]++;
          }

          public boolean supported(Action a) {
            return true;
          }

          public boolean valid(Step a, Step b) {
            return true;
          }

          public void begin(CivilianTraversal.ActionKey k, Step a, Step b) {
            begins[0]++;
          }

          public CivilianTraversal.Outcome observe(CivilianTraversal.ActionKey k) {
            return CivilianTraversal.Outcome.COMPLETE;
          }

          public void stop(CivilianTraversal.ActionKey k) {}
        };
    Key key = new Key(new CivilianPool.Token("epoch", 4096, 1, "r", 1), 1, 1);
    var steps = new ArrayList<Step>();
    for (int i = 0; i < 20; i++) steps.add(new Step(new Tile(i, 1, 0), Action.WALK));
    var traversal = new CivilianTraversal(port);
    traversal.start(new Result(key, Status.FOUND, steps, 0, 0));
    traversal.pause();
    traversal.tick(key);
    CivilianFixture.check(
        paused[0] == 1
            && begins[0] == 8
            && frames[0] == 1
            && ends[0] == 1
            && traversal.view().phase() == CivilianTraversal.Phase.RUNNING,
        "carry movement across bounded steps; pause preserves route");
    traversal.tick(key);
    traversal.tick(key);
    CivilianFixture.check(
        traversal.view().phase() == CivilianTraversal.Phase.COMPLETE,
        "continuous route completion");
    CivilianFixture.check(
        ends[0] == frames[0], "one publication boundary after each movement frame");
    var oldRoute =
        new Result(
            key, Status.FOUND, List.of(new Step(a, Action.WALK), new Step(b, Action.WALK)), 0, 0);
    var candidate =
        new Result(
            key,
            Status.FOUND,
            List.of(new Step(b, Action.WALK), new Step(new Tile(3, 1, 0), Action.WALK)),
            0,
            0);
    CivilianFixture.check(
        CivilianRouteJoin.approaching(candidate, oldRoute, true, b, a),
        "ahead candidate waits without stopping committed route");
    CivilianFixture.check(
        !CivilianRouteJoin.approaching(candidate, oldRoute, true, b, b),
        "candidate joins when current body reaches anchor");
    CivilianFixture.check(
        !CivilianRouteJoin.approaching(candidate, oldRoute, false, b, a),
        "cancelled route cannot keep candidate waiting");
    var perception =
        new CivilianPerception(
            (tile, offset, maximum) -> new CivilianPerception.Page(true, List.of(), false));
    boolean observed = false;
    for (int i = 0; i < 25; i++)
      observed |= perception.sample(new Tile(i / 4, 0, 0), 32, i * 100_000_000L).known();
    CivilianFixture.check(observed, "moving resident finishes bounded perception sweep");
    System.out.println(
        "Resident execution fixtures passed: cache invalidation, bounded continuous steps, pause"
            + " preserves action");
  }
}
