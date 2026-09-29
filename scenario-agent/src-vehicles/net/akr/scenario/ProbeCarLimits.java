package net.akr.scenario;

/**
 * Observed vehicle limits plus conservative driving preferences, not a replacement physics model.
 */
record ProbeCarLimits(
    double mass,
    double driveForce,
    double maxSpeed,
    double steeringAngle,
    double steeringRate,
    double brakingDeceleration,
    double lateralAcceleration) {
  ProbeCarLimits {
    if (!ProbeRoute.finite(
            mass,
            driveForce,
            maxSpeed,
            steeringAngle,
            steeringRate,
            brakingDeceleration,
            lateralAcceleration)
        || mass < 100
        || mass > 10000
        || driveForce <= 0
        || driveForce > 50000
        || maxSpeed < 1
        || maxSpeed > 300
        || steeringAngle <= 0
        || steeringAngle > 1
        || steeringRate <= 0
        || steeringRate > 3
        || brakingDeceleration < .1
        || brakingDeceleration > 3
        || lateralAcceleration < .1
        || lateralAcceleration > 3)
      throw new IllegalArgumentException("Invalid observed vehicle capabilities");
  }

  static ProbeCarLimits fixture() {
    return new ProbeCarLimits(1000, 1500, 70, .65, .9, 1.6, 2.5);
  }
}
