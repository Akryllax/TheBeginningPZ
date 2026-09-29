package net.akr.scenario.npc.core;

/** Watched-test acceptance gate, not gameplay authority or a client transform controller. */
public final class ReplicaDriftGate {
  private long since;
  private double peak;

  /** Largest measured replica gap since this gate was created. */
  public double peak() {
    return peak;
  }

  /** Return true after a fresh replica has exceeded three tiles for 750 ms. */
  public boolean sample(long now, boolean fresh, double gap) {
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
