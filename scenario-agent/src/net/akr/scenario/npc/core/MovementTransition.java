package net.akr.scenario.npc.core;

/**
 * Native animation blend-in has duration; authoritative movement must not start at cruise speed.
 */
public final class MovementTransition {
  private long began;
  private boolean active;
  private double from;

  /** Forget the previous movement blend after body reassignment or a hard stop. */
  public void reset() {
    active = false;
    from = 0;
    began = 0;
  }

  /** Start a new blend at monotonic time {@code now} from the previous tile speed. */
  public void begin(long now, double previous) {
    active = true;
    began = now;
    from = Math.max(0, previous);
  }

  /** Return a bounded animation transition fraction for the requested gait. */
  public double progress(long now, boolean running) {
    return active ? Math.clamp((now - began) / (running ? 350_000_000.0 : 200_000_000.0), 0, 1) : 0;
  }

  /** Cap authoritative movement to the speed the visual blend can represent. */
  public double speed(long now, boolean running, double target) {
    double t =
        progress(
            now, running); // A WALK packet immediately selects the walking clip. Never retain RUN
    // translation
    // above that clip's native rate while the visual blend settles.
    return Math.min(target, from + (target - from) * t);
  }
}
