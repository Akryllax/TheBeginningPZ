package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;
import zombie.characters.IsoPlayer;

/** Two simultaneous 50-tile tracks through the same bounded native executor. */
final class NativeStrideComparison {
  private final CivilianTraversal[] paths = new CivilianTraversal[2];
  private final Tile[] starts = new Tile[2];
  private final CivilianPool.Token[] tokens;
  private final CivilianPool<IsoPlayer> pool;
  private final IsoPlayer[] bodies;
  private final long[] revisions = {1, 1};
  private long began;
  final double[] seconds = new double[2];

  NativeStrideComparison(
      CivilianPool<IsoPlayer> pool,
      NativeCivilianActors actors,
      CivilianPool.Token[] tokens,
      IsoPlayer[] bodies) {
    this.pool = pool;
    this.tokens = tokens;
    this.bodies = bodies;
    for (int i = 0; i < 2; i++) {
      starts[i] =
          new Tile((int) Math.floor(bodies[i].getX()), (int) Math.floor(bodies[i].getY()), 0);
      var port = new NativeCivilianTraversal(tokens[i], bodies[i], actors);
      port.speed(i == 0 ? 1.45 : 2.6);
      paths[i] = new CivilianTraversal(port);
      if (!pool.acceptIntent(tokens[i], 1)) throw new IllegalStateException("stride_identity");
      paths[i].start(route(i, 0, 8));
    }
  }

  private Result route(int actor, int from, int to) {
    var steps = new ArrayList<Step>();
    for (int i = from; i <= to; i++)
      steps.add(new Step(new Tile(starts[actor].x(), starts[actor].y() + i, 0), Action.WALK));
    return new Result(new Key(tokens[actor], revisions[actor], 1), Status.FOUND, steps, 0, 0);
  }

  boolean tick(long now) {
    if (began == 0) began = now;
    if (now - began > 60_000_000_000L) throw new IllegalStateException("stride_timeout");
    for (int i = 0; i < 2; i++) {
      if (seconds[i] > 0) continue;
      var path = paths[i];
      path.tick(path.view().key());
      if (path.view().phase() == CivilianTraversal.Phase.COMPLETE) {
        if (path.completedEdges() != 50 || Math.abs(bodies[i].getY() - starts[i].y() - 50.5) > .02)
          throw new IllegalStateException("stride_distance:" + i);
        seconds[i] = (now - began) / 1e9;
        continue;
      }
      if (path.view().phase() != CivilianTraversal.Phase.RUNNING)
        throw new IllegalStateException("stride_blocked:" + i + ":" + path.view().reason());
      int tail = path.route().route().getLast().at().y() - starts[i].y();
      if (!path.hasReplacement() && path.buffered() <= 4 && tail < 50) {
        revisions[i]++;
        if (!pool.acceptIntent(tokens[i], revisions[i])
            || !path.replaceAtBoundary(route(i, tail, Math.min(50, tail + 8))))
          throw new IllegalStateException("stride_refill");
      }
      if (path.buffered() > 8) throw new IllegalStateException("stride_window");
    }
    if (seconds[0] > 0 && seconds[1] > 0) {
      if (seconds[0] / seconds[1] < 1.7)
        throw new IllegalStateException("stride_run_not_faster:" + Arrays.toString(seconds));
      return true;
    }
    return false;
  }

  void cancel() {
    for (var path : paths) path.cancel();
  }
}
