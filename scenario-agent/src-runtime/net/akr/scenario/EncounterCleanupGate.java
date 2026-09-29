package net.akr.scenario;

/**
 * Missing observers are not missing resources. Unknown connections block the one-observer fixture.
 */
final class EncounterCleanupGate {
  static boolean clearance(int connections, int players, boolean observerKnown, boolean allClear) {
    if (connections < 0 || players < 0) return false;
    if (connections == 0 && players == 0) return true;
    return connections == 1 && players == 1 && observerKnown && allClear;
  }

  static boolean absent(int connections, int players, boolean freshClientAbsence) {
    return clearance(connections, players, true, true) && (connections == 0 || freshClientAbsence);
  }
}
