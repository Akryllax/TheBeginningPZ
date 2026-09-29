package net.lofers.scenario;

import java.util.*;

/** Physics-independent regression for approach, long wait, clearance and resumption. */
final class ParkedObstacleFixture {
  static void check(boolean value, String why) {
    ScenarioFixture.check(value, why);
  }

  static void run() throws Exception {
    var route = new ProbeRoute(BezierFixture.course());
    var obstacle = new CollisionForecast.Obstacle(29.5, .4, 0, 0, 1.8, 0);
    double target = ParkedObstacle.stopProgress(route, 0, 0, 0, 2.45, obstacle, 1.5);
    check(target > 23 && target < 24, "Parked stopping point not near physical blocker");
    for (double progress = 0; progress < 23; progress += .25)
      check(
          Math.abs(
                  ParkedObstacle.stopProgress(route, progress, progress, 0, 2.45, obstacle, 1.5)
                      - target)
              < .001,
          "Static stop moves during approach");
    check(
        Double.isInfinite(
            ParkedObstacle.stopProgress(
                route, 0, 0, 0, 2.45, new CollisionForecast.Obstacle(20, 12, 0, 0, 1.8, 0), 1.5)),
        "Off-road parked car stops traffic");
    ScenarioFixture.rejects(
        () ->
            ParkedObstacle.stopProgress(
                route, 0, 0, 0, 2.45, new CollisionForecast.Obstacle(20, 0, 1, 0, 1.8, 0), 1.5),
        "Moving car treated as parked");
    for (ProbeCarLimits limits :
        List.of(ProbeCarLimits.fixture(), new ProbeCarLimits(2000, 1200, 12, .6, .6, .7, 1.5)))
      simulate(route, obstacle, limits);
    temperament();
    System.out.println(
        "Parked obstacle fixtures passed: stable stopping point, capability-aware approach,"
            + " 35-second wait without stall, stop-sign preservation and resume; personality/horn"
            + " timing deterministic");
  }

  private static void simulate(
      ProbeRoute route, CollisionForecast.Obstacle obstacle, ProbeCarLimits limits) {
    var driver = new ProbeDriver(route, 50, 1.94, List.of(new ProbeDriver.Stop(24.75, 2)));
    driver.limits(limits);
    double x = 0, y = 0, yaw = Math.PI / 2, v = 0, dt = .05, waited = 0, maxSpeed = 0;
    boolean removed = false, arrived = false;
    for (int i = 0; i < 5000; i++) {
      double progress = route.trajectory.project(x, y, 0, route.length).progress();
      double stop =
          removed
              ? Double.POSITIVE_INFINITY
              : ParkedObstacle.stopProgress(route, progress, x, y, 2.45, obstacle, 1.5);
      var command = driver.step(x, y, Math.sin(yaw), Math.cos(yaw), v * 3.6, dt, "", stop);
      check(command.stopReason().isEmpty(), "Parked approach failed: " + command.stopReason());
      if (!removed) {
        check(
            Math.hypot(x - obstacle.x(), y - obstacle.y()) > 5,
            "Car reached parked collision envelope");
        if (driver.waitingForObstacle()) {
          check(x > 22 && x < 24, "Car stopped prematurely or too late");
          check(driver.completedStops() == 0, "Queue consumed a stop sign before reaching it");
          waited += dt;
          if (waited > 35) removed = true;
        }
      }
      if (command.arrived()) {
        arrived = true;
        break;
      }
      maxSpeed = Math.max(maxSpeed, v * 3.6);
      v =
          Math.max(
              0,
              v
                  + (command.engineForce() / limits.mass()
                          - command.brake() / 100 * 3 * (1000 / limits.mass())
                          - .03 * v)
                      * dt);
      yaw += v / 1.94 * Math.tan(command.steering()) * dt;
      x += v * Math.sin(yaw) * dt;
      y += v * Math.cos(yaw) * dt;
    }
    check(
        arrived && removed && driver.completedStops() == 1 && maxSpeed > 7,
        "Queue did not resume and complete its original route");
  }

  private static void temperament() {
    check(
        TrafficTemperament.forResident(42).equals(TrafficTemperament.forResident(42)),
        "Personality changed on replay");
    check(
        !TrafficTemperament.forResident(42).equals(TrafficTemperament.forResident(43)),
        "Every resident has same personality");
    for (double chance : List.of(0.0, 1.0)) {
      var queue = new TrafficBlockage(42, new TrafficTemperament(12, chance, 4, .5, 1.5, 5));
      for (int i = 0; i < 100; i++)
        check(!queue.step(7, false, .1).horn(), "Horn emitted while approaching");
      int hornTicks = 0, attempts = 0;
      double lastAttempt = -100;
      for (int i = 0; i < 300; i++) {
        var decision = queue.step(7, true, .1);
        if (decision.horn()) {
          check(
              decision.waited() >= 4 && decision.waited() < 4.5,
              "Horn outside personality interval");
          hornTicks++;
        }
        if (decision.requestBypass()) {
          check(
              decision.waited() >= 12 && decision.waited() - lastAttempt >= 4.99,
              "Unbounded or premature replan");
          attempts++;
          lastAttempt = decision.waited();
        }
      }
      check(
          chance == 0 ? hornTicks == 0 : hornTicks >= 4 && hornTicks <= 6,
          "Honk probability does not control episode");
      check(attempts == 4, "Expected bounded repeated bypass requests");
      var clear = queue.step(-1, true, .1);
      check(
          !clear.horn() && !clear.requestBypass() && clear.waited() == 0,
          "Cleared blockage retained effects");
      check(queue.step(8, true, .1).waited() == .1, "New obstruction inherited old impatience");
    }
  }
}
