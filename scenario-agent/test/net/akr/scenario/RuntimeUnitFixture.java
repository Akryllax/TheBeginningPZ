package net.akr.scenario;

/** Detached unit/component checks. No game server, native simulation or player session. */
public final class RuntimeUnitFixture {
  public static void main(String[] args) throws Exception {
    TerminalJournalFixture.run();
    EventSchedulerFixture.run();
    PedestrianRuntimeFixture.run();
    EncounterRuntimeFixture.run();
    CivilianFixture.run();
    ResidentExecutionFixture.run();
    ContinuousMovementFixture.run();
    NativeGaitSpeedFixture.run();
    PlannerTransportFixture.run();
    CivilianCombatFixture.run();
    OffscreenAdmissionFixture.run();
    ResidentPhysicsFixture.run();
    ProbeDriverFixture.run();
    TrafficTileObstacleFixture.run();
    ProbeImpactFixture.run();
    System.out.println(
        "Runtime unit/component layer passed; in-game functional validation not performed");
  }
}
