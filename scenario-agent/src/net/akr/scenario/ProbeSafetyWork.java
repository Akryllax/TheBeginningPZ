package net.akr.scenario;

/** Hard operation bounds for the synchronous safety scan, independent of JVM pauses. */
final class ProbeSafetyWork {
  static final int MAX_TILES = 256, MAX_VEHICLES = 64;
  private int tiles, vehicles;
  private final int tileLimit;

  ProbeSafetyWork() {
    this(false);
  }

  ProbeSafetyWork(boolean extendedImpact) {
    tileLimit = extendedImpact ? 1024 : MAX_TILES;
  }

  void reset() {
    tiles = vehicles = 0;
  }

  boolean tile() {
    return ++tiles <= tileLimit;
  }

  boolean vehicle() {
    return ++vehicles <= MAX_VEHICLES;
  }

  int tiles() {
    return tiles;
  }

  int vehicles() {
    return vehicles;
  }
}
