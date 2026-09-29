package net.lofers.scenario;

/** Bounded geometric stop planning independent of the car's current speed. */
final class ParkedObstacle {
  static final double SWEEP_SPEED = 5;

  static double stopProgress(
      ProbeRoute route,
      double progress,
      double x,
      double y,
      double radius,
      CollisionForecast.Obstacle obstacle,
      double gap) {
    if (!ProbeRoute.finite(gap)
        || gap < .5
        || gap > 4
        || Math.hypot(obstacle.vx(), obstacle.vy()) > .03)
      throw new IllegalArgumentException("Invalid parked obstacle");
    // Up to sixty remaining tiles at five tiles/s. The speed is only an
    // arc-length parameter: it is not a prediction of the driven speed.
    double horizon = Math.min(12, Math.max(.2, (route.length - progress) / SWEEP_SPEED + .2));
    var sweep = CollisionForecast.following(route, progress, x, y, SWEEP_SPEED, horizon);
    double contact = CollisionForecast.firstContact(sweep, radius, obstacle);
    return Double.isFinite(contact)
        ? progress + contact * SWEEP_SPEED - gap
        : Double.POSITIVE_INFINITY;
  }
}
