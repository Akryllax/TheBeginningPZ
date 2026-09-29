package net.akr.scenario;

/**
 * Missing observers are not missing resources. Unknown connections block the configured spectator
 * roster.
 */
final class EncounterCleanupGate {
  static boolean clearance(int connections, int players, boolean observerKnown, boolean allClear) {
    return clearance(connections, players, observerKnown, allClear, 1);
  }

  static boolean clearance(
      int connections, int players, boolean observerKnown, boolean allClear, int seats) {
    if (seats < 1 || seats > 4 || connections < 0 || players < 0) return false;
    if (connections == 0 && players == 0) return true;
    return connections == seats && players == seats && observerKnown && allClear;
  }

  static boolean absent(int connections, int players, boolean freshClientAbsence) {
    return absent(connections, players, freshClientAbsence, 1);
  }

  static boolean absent(int connections, int players, boolean freshClientAbsence, int seats) {
    return clearance(connections, players, true, true, seats)
        && (connections == 0 || freshClientAbsence);
  }
}
