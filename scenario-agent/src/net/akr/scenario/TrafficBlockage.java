package net.akr.scenario;

import java.util.SplittableRandom;

/** Local queue state; native motion/clearance and any bypass reservation are separate. */
final class TrafficBlockage {
  record Decision(
      boolean horn, boolean requestBypass, double waited, String state, boolean tryOffroad) {}

  private final TrafficTemperament temperament;
  private final long identity;
  private long episode;
  private int blocker = -1;
  private double waited, nextAttempt;
  private boolean honks, offroad;

  TrafficBlockage(long identity, TrafficTemperament temperament) {
    this.identity = identity;
    this.temperament = temperament;
  }

  Decision step(int observedBlocker, boolean stationary, double dt) {
    if (!Double.isFinite(dt) || dt <= 0 || dt > 1)
      throw new IllegalArgumentException("Invalid blockage clock");
    if (observedBlocker < 0) {
      blocker = -1;
      waited = 0;
      return new Decision(false, false, 0, "clear", false);
    }
    if (observedBlocker != blocker) {
      blocker = observedBlocker;
      waited = 0;
      nextAttempt = temperament.patienceSeconds();
      var random = new SplittableRandom(identity ^ Long.rotateLeft(++episode, 23) ^ blocker);
      honks = random.nextDouble() < temperament.hornChance();
      offroad = random.nextDouble() < temperament.offroadWillingness();
    }
    if (!stationary) return new Decision(false, false, waited, "approaching", offroad);
    waited += dt;
    boolean horn =
        honks
            && waited >= temperament.hornDelaySeconds()
            && waited < temperament.hornDelaySeconds() + temperament.hornSeconds();
    boolean attempt = waited >= nextAttempt;
    if (attempt) nextAttempt = waited + temperament.retrySeconds();
    return new Decision(horn, attempt, waited, attempt ? "seeking_bypass" : "waiting", offroad);
  }
}
