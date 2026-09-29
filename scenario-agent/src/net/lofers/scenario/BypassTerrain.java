package net.lofers.scenario;

import java.util.Set;

/** Surface preference only. Solid ground, walls, actors and loaded physics remain mandatory. */
final class BypassTerrain {
  private static final Set<String> SHOULDERS =
      Set.of(
          "Grass_Dark",
          "Grass_Medium",
          "Grass_Light",
          "Dirt",
          "Dirt_Grass",
          "Road_01",
          "Road_02",
          "Road_03",
          "Road_04",
          "Road_05",
          "Road_06",
          "Road_07");

  static boolean allows(String floorMaterial, boolean shoulder) {
    return floorMaterial != null
        && (shoulder ? SHOULDERS.contains(floorMaterial) : floorMaterial.equals("Road_06"));
  }

  static ProbeCarLimits cautious(ProbeCarLimits car) {
    return new ProbeCarLimits(
        car.mass(),
        car.driveForce(),
        car.maxSpeed(),
        car.steeringAngle(),
        car.steeringRate(),
        Math.max(.1, car.brakingDeceleration() * .6),
        Math.max(.1, car.lateralAcceleration() * .6));
  }
}
