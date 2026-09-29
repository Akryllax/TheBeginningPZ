package net.akr.scenario;

/** Conservative planning evidence, not a replacement for native collision geometry. */
final class TrafficTileObstacle {
  private TrafficTileObstacle() {}

  /** Null means absent; an empty/unknown declaration remains an obstacle. */
  static String problem(
      String shape,
      String mesh,
      String sprite,
      String moveType,
      boolean stopCar,
      boolean hitByCar) {
    if (mesh != null) return "physical_mesh_obstacle";
    if (shape != null && !"Floor".equals(shape)) return "physical_shape_obstacle";
    if (stopCar || hitByCar) return "physical_car_obstacle";
    // Some installed freestanding fixtures have native column collisions
    // without ordinary walkability flags or an explicit shape declaration.
    if (shape == null
        && sprite != null
        && !"WallObject".equals(moveType)
        && (sprite.contains("lighting_outdoor_")
            || sprite.equals("recreational_sports_01_19")
            || sprite.equals("recreational_sports_01_21")
            || sprite.equals("recreational_sports_01_32"))) return "physical_column_obstacle";
    return "";
  }
}
