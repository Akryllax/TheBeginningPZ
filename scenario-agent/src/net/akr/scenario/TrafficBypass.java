package net.akr.scenario;

import java.util.*;

/**
 * Two detached candidates on a reviewed straight road. Game thread must validate live terrain and
 * traffic.
 */
final class TrafficBypass {
  record Job(
      long command,
      long revision,
      ProbeRoute original,
      double progress,
      TrafficFootprint ego,
      TrafficFootprint blocker,
      List<TrafficFootprint> parked) {
    Job {
      parked = List.copyOf(parked);
      if (parked.size() > 64) throw new IllegalArgumentException("Too many bypass obstacles");
    }
  }

  record Candidate(
      ProbeRoute route, double rejoinProgress, Set<ProbeRoute.Tile> corridor, String side) {}

  record Result(Job job, List<Candidate> candidates, String reason) {}

  static boolean straight(ProbeRoute route) {
    if (route.trajectory == null) return false;
    double heading = route.heading();
    for (var curve : route.trajectory.curves)
      for (var p : List.of(curve.p1(), curve.p2(), curve.p3())) {
        double dx = p.x() - route.points.getFirst().x(), dy = p.y() - route.points.getFirst().y();
        if (Math.abs(dx * Math.cos(heading) - dy * Math.sin(heading)) > .001) return false;
      }
    return true;
  }

  static Result plan(Job job) {
    if (!straight(job.original)
        || job.progress < 0
        || job.progress > job.original.length
        || Math.abs(ProbeRoute.wrap(job.ego.heading() - job.original.heading()))
            > Math.toRadians(3))
      return new Result(job, List.of(), "requires_aligned_straight_road");
    var candidates = new ArrayList<Candidate>();
    double heading = job.original.heading(),
        fx = Math.sin(heading),
        fy = Math.cos(heading),
        rx = fy,
        ry = -fx;
    double ahead = (job.blocker.x() - job.ego.x()) * fx + (job.blocker.y() - job.ego.y()) * fy;
    double rejoin = ahead + job.blocker.halfLength() + 10;
    double remaining =
        (job.original.points.getLast().x() - job.ego.x()) * fx
            + (job.original.points.getLast().y() - job.ego.y()) * fy;
    if (ahead < 5 || ahead > 10.5 || rejoin + 3 > remaining)
      return new Result(job, List.of(), "insufficient_entry_or_rejoin_space");
    for (double offset : List.of(3.2, -3.2)) {
      try {
        var a = new ProbeRoute.Point(job.ego.x(), job.ego.y());
        var b = point(job.ego, fx, fy, rx, ry, 8, offset);
        var c = point(job.ego, fx, fy, rx, ry, rejoin - 8, offset);
        var d = point(job.ego, fx, fy, rx, ry, rejoin, 0);
        var end = job.original.points.getLast();
        if (rejoin - 16 < .5) continue;
        var curves =
            List.of(
                shift(a, b, fx, fy),
                BezierPath.line(b, c),
                shift(c, d, fx, fy),
                BezierPath.line(d, end));
        var route = new ProbeRoute(new BezierPath(curves));
        if (route.tiles.size() > 256 || !job.original.chunks.containsAll(route.chunks)) continue;
        boolean clear = true;
        for (var obstacle : job.parked)
          if (!TrafficFootprint.pathClear(route, 0, route.length, job.ego, obstacle)) {
            clear = false;
            break;
          }
        if (clear)
          candidates.add(
              new Candidate(
                  route,
                  route.trajectory.ends[3],
                  Set.copyOf(ProbeFootprint.swept(new BezierPath(curves.subList(0, 3)))),
                  offset > 0 ? "left" : "right"));
      } catch (IllegalArgumentException ignored) {
        /* Geometrically impossible candidate stays rejected. */
      }
    }
    return new Result(
        job,
        List.copyOf(candidates),
        candidates.isEmpty() ? "no_geometric_bypass" : "awaiting_live_clearance");
  }

  private static ProbeRoute.Point point(
      TrafficFootprint origin,
      double fx,
      double fy,
      double rx,
      double ry,
      double forward,
      double side) {
    return new ProbeRoute.Point(
        origin.x() + fx * forward + rx * side, origin.y() + fy * forward + ry * side);
  }

  private static BezierPath.Curve shift(
      ProbeRoute.Point a, ProbeRoute.Point b, double fx, double fy) {
    double third = ((b.x() - a.x()) * fx + (b.y() - a.y()) * fy) / 3;
    return new BezierPath.Curve(
        a,
        new ProbeRoute.Point(a.x() + fx * third, a.y() + fy * third),
        new ProbeRoute.Point(b.x() - fx * third, b.y() - fy * third),
        b);
  }
}
