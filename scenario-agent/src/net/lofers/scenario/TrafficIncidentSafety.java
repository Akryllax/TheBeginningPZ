package net.lofers.scenario;

import java.util.*;

/** Pure admission/revalidation policy. It cannot spawn, damage or exempt an actor. */
final class TrafficIncidentSafety {
  record Envelope(double minX, double minY, double maxX, double maxY) {}

  record Player(double x, double y, double maxApproachSpeed, double ageSeconds, double radius) {}

  record Check(boolean allowed, String reason) {}

  static Check check(
      Envelope hazard,
      List<Player> players,
      double timeToSafeStop,
      boolean allPlayersKnown,
      boolean sceneClear,
      boolean onlyScenarioParticipants,
      boolean stagingUnseen) {
    if (players.isEmpty() || players.size() > 32 || !allPlayersKnown)
      return new Check(false, "player_observations_incomplete");
    if (!ProbeRoute.finite(hazard.minX, hazard.minY, hazard.maxX, hazard.maxY, timeToSafeStop)
        || hazard.maxX < hazard.minX
        || hazard.maxY < hazard.minY
        || hazard.maxX - hazard.minX > 160
        || hazard.maxY - hazard.minY > 160
        || timeToSafeStop < 0
        || timeToSafeStop > 8) return new Check(false, "invalid_hazard_envelope");
    if (!sceneClear || !onlyScenarioParticipants)
      return new Check(false, "unreserved_actor_or_property");
    if (!stagingUnseen) return new Check(false, "visible_materialization");
    for (Player p : players) {
      if (!ProbeRoute.finite(p.x, p.y, p.maxApproachSpeed, p.ageSeconds, p.radius)
          || p.ageSeconds < 0
          || p.ageSeconds > .5
          || p.maxApproachSpeed < 3
          || p.maxApproachSpeed > 60
          || p.radius < .3
          || p.radius > 4) return new Check(false, "player_observation_stale_or_invalid");
      // Reachability disk covers any change of direction, not only current velocity.
      double reach = 5 + p.radius + p.maxApproachSpeed * (timeToSafeStop + p.ageSeconds + .25);
      double dx = Math.max(Math.max(hazard.minX - p.x, 0), p.x - hazard.maxX);
      double dy = Math.max(Math.max(hazard.minY - p.y, 0), p.y - hazard.maxY);
      if (Math.hypot(dx, dy) <= reach) return new Check(false, "player_can_reach_hazard");
    }
    return new Check(true, "detached_checks_passed_native_revalidation_required");
  }
}
