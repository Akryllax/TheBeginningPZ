package net.lofers.scenario;

/** Connected observers may temporarily lack a square while their teleport loads chunks. */
final class EncounterObserverGate {
  enum State {
    WAITING,
    READY,
    DISCONNECTED,
    TIMED_OUT
  }

  static State evaluate(
      boolean connected, boolean loaded, boolean started, long elapsed, long timeout) {
    if (!connected) return started ? State.DISCONNECTED : State.WAITING;
    if (started && elapsed > timeout) return State.TIMED_OUT;
    return loaded ? State.READY : State.WAITING;
  }
}
