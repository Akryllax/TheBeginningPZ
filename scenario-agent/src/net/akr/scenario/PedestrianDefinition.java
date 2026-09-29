package net.akr.scenario;

import java.util.List;
import net.akr.scenario.protocol.RuntimeControl;

/** Deliberately small feasibility envelope; not an ambient population configuration. */
record PedestrianDefinition(
    int actors,
    List<Point> route,
    int timeoutSeconds,
    int holdSeconds,
    Entity entity,
    int reassignAfterSeconds,
    double walkTilesPerSecond,
    int hunterZombies,
    double fleeTilesPerSecond,
    int tripAfterSeconds,
    HunterSpeed hunterSpeed,
    int chaseDelaySeconds,
    boolean tripFall) {
  enum Entity {
    DISGUISED_ZOMBIE,
    ACTOR
  }

  enum HunterSpeed {
    SANDBOX,
    SPRINTER,
    FAST_SHAMBLER,
    SHAMBLER
  }

  record Point(double x, double y, int z) {
    Point {
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || x < 0
          || y < 0
          || x > 30000
          || y > 30000
          || z != 0) throw new IllegalArgumentException("ground_level_point_required");
    }
  }

  PedestrianDefinition(int actors, List<Point> route, int timeoutSeconds, int holdSeconds) {
    this(
        actors,
        route,
        timeoutSeconds,
        holdSeconds,
        Entity.DISGUISED_ZOMBIE,
        0,
        0,
        0,
        0,
        0,
        HunterSpeed.SANDBOX,
        0,
        false);
  }

  PedestrianDefinition {
    route = List.copyOf(route);
    if ((actors != 1 && actors != 4)
        || route.size() < 2
        || route.size() > 8
        || timeoutSeconds < 10
        || timeoutSeconds > 180
        || holdSeconds < 0
        || holdSeconds > 60) throw new IllegalArgumentException("pedestrian_limits");
    java.util.Objects.requireNonNull(entity);
    // Reassignment exercises the pooled Actor slot; it must happen inside the hold.
    if (reassignAfterSeconds != 0
        && (entity != Entity.ACTOR || reassignAfterSeconds >= holdSeconds))
      throw new IllegalArgumentException("reassign_limits");
    if (!Double.isFinite(walkTilesPerSecond)
        || (walkTilesPerSecond != 0
            && (entity != Entity.ACTOR || walkTilesPerSecond < 0.5 || walkTilesPerSecond > 6)))
      throw new IllegalArgumentException("walk_speed_limits");
    if (walkTilesPerSecond != 0 && reassignAfterSeconds != 0)
      throw new IllegalArgumentException("reassign_requires_standing");
    if (hunterZombies != 0) {
      double reach =
          Math.hypot(
              route.getLast().x - route.getFirst().x, route.getLast().y - route.getFirst().y);
      if (hunterZombies != 1
          || entity != Entity.ACTOR
          || walkTilesPerSecond != 0
          || reassignAfterSeconds != 0
          || reach < 3
          || reach > 20) throw new IllegalArgumentException("hunter_limits");
    }
    if (!Double.isFinite(fleeTilesPerSecond)
        || (fleeTilesPerSecond != 0
            && (hunterZombies == 0 || fleeTilesPerSecond < 1 || fleeTilesPerSecond > 6))
        || tripAfterSeconds < 0
        || tripAfterSeconds > 20
        || (tripAfterSeconds != 0 && fleeTilesPerSecond == 0))
      throw new IllegalArgumentException("flee_limits");
    java.util.Objects.requireNonNull(hunterSpeed);
    if (hunterSpeed != HunterSpeed.SANDBOX && hunterZombies == 0)
      throw new IllegalArgumentException("hunter_speed_requires_hunter");
    if (chaseDelaySeconds < 0
        || chaseDelaySeconds > 30
        || (chaseDelaySeconds != 0 && hunterZombies == 0))
      throw new IllegalArgumentException("chase_delay_limits");
    if (tripFall && tripAfterSeconds == 0)
      throw new IllegalArgumentException("trip_fall_requires_trip");
    // One Actor per event until the four-Actor gate.
    if (entity == Entity.ACTOR && actors != 1)
      throw new IllegalArgumentException("actor_gate1_single");
    Point origin = route.getFirst();
    for (Point point : route)
      if (Math.hypot(point.x - origin.x, point.y - origin.y) > 64)
        throw new IllegalArgumentException("route_radius");
    for (int i = 1; i < route.size(); i++)
      if (Math.hypot(route.get(i).x - route.get(i - 1).x, route.get(i).y - route.get(i - 1).y) < 1)
        throw new IllegalArgumentException("route_segment_too_short");
  }

  static PedestrianDefinition from(RuntimeControl.PedestrianCase value) {
    Entity entity =
        switch (value.getEntity()) {
          case DISGUISED_ZOMBIE -> Entity.DISGUISED_ZOMBIE;
          case ACTOR -> Entity.ACTOR;
          default -> throw new IllegalArgumentException("entity");
        };
    return new PedestrianDefinition(
        value.getActors(),
        value.getRouteList().stream().map(p -> new Point(p.getX(), p.getY(), p.getZ())).toList(),
        value.getTimeoutSeconds(),
        value.getHoldSeconds(),
        entity,
        value.getReassignAfterSeconds(),
        value.getWalkTilesPerSecond(),
        value.getHunterZombies(),
        value.getFleeTilesPerSecond(),
        value.getTripAfterSeconds(),
        switch (value.getHunterSpeed()) {
          case SANDBOX -> HunterSpeed.SANDBOX;
          case SPRINTER -> HunterSpeed.SPRINTER;
          case FAST_SHAMBLER -> HunterSpeed.FAST_SHAMBLER;
          case SHAMBLER -> HunterSpeed.SHAMBLER;
          default -> throw new IllegalArgumentException("hunter_speed");
        },
        value.getChaseDelaySeconds(),
        value.getTripFall());
  }
}
