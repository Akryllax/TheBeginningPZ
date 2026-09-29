package net.lofers.scenario;

import java.util.*;

final class TrafficBypassFixture {
  static void check(boolean value, String why) {
    ScenarioFixture.check(value, why);
  }

  static ProbeRoute line() {
    return new ProbeRoute(
        new BezierPath(
            List.of(
                BezierPath.line(new ProbeRoute.Point(-18.5, 0), new ProbeRoute.Point(7, 0)),
                BezierPath.line(new ProbeRoute.Point(7, 0), new ProbeRoute.Point(32.5, 0)))));
  }

  static void run() {
    terrain();
    shoulderAtChunkBoundary();
    var a = new TrafficFootprint(1, 0, 0, Math.PI / 2, .69, 1.68);
    check(
        !TrafficFootprint.overlaps(a, a.at(0, 2.5, Math.PI / 2), .25),
        "Parallel cars incorrectly collide like circles");
    check(
        TrafficFootprint.overlaps(a, a.at(2, 0, Math.PI / 2), .25), "Longitudinal overlap missed");
    check(TrafficFootprint.overlaps(a, a.at(0, 1, 0), .25), "Crosswise overlap missed");
    var b = new TrafficFootprint(2, 8.45, .4, 0, .69, 1.68);
    var base = line();
    var job = new TrafficBypass.Job(1, 1, base, 18.5, a, b, List.of(b));
    var result = TrafficBypass.plan(job);
    check(
        !result.candidates().isEmpty(),
        "No candidate around isolated parked car: " + result.reason());
    for (var candidate : result.candidates()) {
      check(
          TrafficFootprint.pathClear(candidate.route(), 0, 60, a, b), "Candidate clips parked car");
      check(
          candidate.route().points.getLast().equals(base.points.getLast()),
          "Bypass loses original destination");
      simulate(candidate, a, b);
    }
    var wall = new TrafficFootprint(3, 8.45, -3.2, Math.PI / 2, 2, 5);
    var wall2 = new TrafficFootprint(4, 8.45, 3.2, Math.PI / 2, 2, 5);
    check(
        TrafficBypass.plan(new TrafficBypass.Job(1, 2, base, 18.5, a, b, List.of(b, wall, wall2)))
            .candidates()
            .isEmpty(),
        "Blocked detour fabricated a route");
    check(
        !TrafficBypass.straight(new ProbeRoute(BezierFixture.course())),
        "Curved junction accepted as passing road");
    check(
        TrafficBypass.plan(new TrafficBypass.Job(1, 3, base, 18.5, a, a.at(24, 0, 0), List.of(b)))
            .candidates()
            .isEmpty(),
        "Too distant blocker accepted");
    System.out.println(
        "Bypass fixtures passed: oriented clearance, bounded left/right proposals, blocked denial"
            + " and independent physical approach/rejoin simulation");
  }

  private static void shoulderAtChunkBoundary() {
    var start = new ProbeRoute.Point(10732.5, 9861.5);
    var mid = new ProbeRoute.Point(10754.5, 9861.5);
    var end = new ProbeRoute.Point(10776.5, 9861.5);
    var path = new BezierPath(List.of(BezierPath.line(start, mid), BezierPath.line(mid, end)));
    var narrow = new ProbeRoute(path);
    var retained = new ProbeRoute(path, 13);
    var ego = new TrafficFootprint(1, 10745.23828125, 9861.5, Math.PI / 2, .69, 1.68);
    var blocker = new TrafficFootprint(2, 10754, 9862, Math.PI / 2, .69, 1.68);
    var opposite = new TrafficFootprint(3, 10754, 9858, Math.PI / 2, .69, 1.68);
    var denial = new TrafficFootprint(4, 10758, 9864, Math.PI / 2, .69, 1.68);
    check(
        TrafficBypass.plan(
                new TrafficBypass.Job(
                    1, 1, narrow, 12.73828125, ego, blocker, List.of(blocker, opposite)))
            .candidates()
            .isEmpty(),
        "Unloaded shoulder candidate accepted");
    var clear =
        TrafficBypass.plan(
            new TrafficBypass.Job(
                1, 2, retained, 12.73828125, ego, blocker, List.of(blocker, opposite)));
    check(
        clear.candidates().size() == 1 && clear.candidates().getFirst().side().equals("right"),
        "Retained shoulder not proposed at chunk boundary");
    check(
        TrafficBypass.plan(
                new TrafficBypass.Job(
                    1, 3, retained, 12.73828125, ego, blocker, List.of(blocker, opposite, denial)))
            .candidates()
            .isEmpty(),
        "Occupied shoulder accepted");
    simulate(clear.candidates().getFirst(), ego, blocker);
  }

  private static void terrain() {
    for (String surface : List.of("Grass_Medium", "Grass_Dark", "Dirt", "Dirt_Grass", "Road_04")) {
      check(!BypassTerrain.allows(surface, false), "Road-only driver admitted shoulder");
      check(BypassTerrain.allows(surface, true), "Willing driver rejected supported shoulder");
    }
    for (String surface : List.of("Water", "Burnt", "Sand", "unknown"))
      check(!BypassTerrain.allows(surface, true), "Unsafe/unknown terrain admitted");
    check(!BypassTerrain.allows(null, true), "Missing terrain observation became drivable");
    var car = ProbeCarLimits.fixture();
    var cautious = BypassTerrain.cautious(car);
    check(
        cautious.driveForce() == car.driveForce()
            && cautious.brakingDeceleration() < car.brakingDeceleration()
            && cautious.lateralAcceleration() < car.lateralAcceleration(),
        "Offroad invents engine power or ignores reduced grip preference");
    for (double willingness : List.of(0.0, 1.0)) {
      var driver =
          new TrafficBlockage(3, new TrafficTemperament(12, .5, 4, .5, 1.5, 5, willingness));
      for (int i = 0; i < 300; i++)
        check(
            driver.step(7, true, .1).tryOffroad() == (willingness == 1),
            "Offroad choice changed mid-episode or ignored temperament");
    }
  }

  private static void simulate(
      TrafficBypass.Candidate candidate, TrafficFootprint ego, TrafficFootprint obstacle) {
    var route = candidate.route();
    var driver = new ProbeDriver(route, 15, 1.94);
    double x = ego.x(), y = ego.y(), yaw = ego.heading(), v = 0, dt = .025;
    boolean arrived = false;
    for (int i = 0; i < 4000; i++) {
      var c = driver.step(x, y, Math.sin(yaw), Math.cos(yaw), v * 3.6, dt, "");
      check(c.stopReason().isEmpty(), "Bypass controller stopped: " + c.stopReason());
      check(
          !TrafficFootprint.overlaps(ego.at(x, y, yaw), obstacle, .15),
          "Integrated car collided while passing");
      if (c.arrived()) {
        arrived = true;
        break;
      }
      v = Math.max(0, v + (c.engineForce() / 1000 - c.brake() / 100 * 3 - .03 * v) * dt);
      yaw += v / 1.94 * Math.tan(c.steering()) * dt;
      x += v * Math.sin(yaw) * dt;
      y += v * Math.cos(yaw) * dt;
    }
    check(arrived, "Bypass never rejoined/completed route");
  }
}
