package net.lofers.scenario;

/** Watched-test acceptance gate, not gameplay authority or a client transform controller. */
final class ReplicaDriftGate {
  private long since;
  double peak;

  boolean sample(long now, boolean fresh, double gap) {
    if (!fresh) {
      since = 0;
      return false;
    }
    if (!Double.isFinite(gap) || gap < 0) throw new IllegalArgumentException("replica_gap");
    peak = Math.max(peak, gap);
    if (gap <= 3) {
      since = 0;
      return false;
    }
    if (since == 0) since = now;
    return now - since >= 750_000_000L;
  }
}
