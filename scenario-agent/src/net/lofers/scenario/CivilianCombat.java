package net.lofers.scenario;

import java.util.Objects;

/** One bounded, generation-scoped action. A failed native call is never retried. */
public final class CivilianCombat {
  public enum Phase {
    IDLE,
    WINDUP,
    RECOVERY,
    COMPLETE,
    CANCELLED,
    UNRESOLVED
  }

  public enum Style {
    MELEE,
    SHOVE
  }

  public record Key(CivilianPool.Token actor, long revision, String target, long targetGeneration) {
    public Key {
      Objects.requireNonNull(actor);
      if (revision < 1 || target == null || target.isBlank() || targetGeneration < 1)
        throw new IllegalArgumentException("combat_key");
    }
  }

  public record Timing(long windup, long recovery, long cooldown) {
    public Timing {
      if (windup < 1 || recovery < 1 || cooldown < windup + recovery || cooldown > 10_000_000_000L)
        throw new IllegalArgumentException("combat_timing");
    }
  }

  public interface Port {
    boolean current(Key key);

    boolean interrupted();

    boolean contactAllowed(Key key);

    Timing prepare(Key key, Style style) throws Exception;

    /** Return actual native contact count, not intended damage. */
    int contact(Key key, Style style) throws Exception;

    void finish(Key key) throws Exception;
  }

  private final Port port;
  private Key key;
  private Style style;
  private Timing timing;
  private Phase phase = Phase.IDLE;
  private long started, nextAllowed, lastNow = Long.MIN_VALUE;
  private String reason = "";
  private int contacts;
  private boolean attempted;
  private Throwable failure;

  public CivilianCombat(Port port) {
    this.port = Objects.requireNonNull(port);
  }

  public boolean begin(Key candidate, Style requested, long now) {
    if (now < lastNow) return false;
    lastNow = now;
    if (key != null
        && candidate.actor().equals(key.actor())
        && candidate.revision() <= key.revision()) return false;
    if (busy()
        || phase == Phase.UNRESOLVED
        || now < nextAllowed
        || !port.current(candidate)
        || port.interrupted()
        || !port.contactAllowed(candidate)) return false;
    key = candidate;
    style = Objects.requireNonNull(requested);
    started = now;
    contacts = 0;
    attempted = false;
    reason = "";
    failure = null;
    phase = Phase.WINDUP;
    try {
      timing = port.prepare(key, style);
      nextAllowed = now + timing.cooldown();
    } catch (Exception error) {
      failure = error;
      phase = Phase.UNRESOLVED;
      reason = "prepare:" + error.getClass().getSimpleName();
    }
    return phase == Phase.WINDUP;
  }

  public void tick(long now) {
    if (now < lastNow) return;
    lastNow = now;
    if (!busy()) return;
    if (!port.current(key) || port.interrupted()) {
      cancel("interrupted");
      return;
    }
    if (phase == Phase.WINDUP && now - started >= timing.windup()) {
      attempted = true;
      phase = Phase.RECOVERY; // commit before a native call that may throw after damage
      try {
        if (port.contactAllowed(key)) contacts = port.contact(key, style);
        else reason = "target_left_contact";
      } catch (Exception error) {
        failure = error;
        phase = Phase.UNRESOLVED;
        reason = "contact:" + error.getClass().getSimpleName();
        return;
      }
    }
    if (phase == Phase.RECOVERY && now - started >= timing.windup() + timing.recovery())
      finish(Phase.COMPLETE);
  }

  public void cancel(String reason) {
    if (!busy()) return;
    this.reason = reason;
    finish(Phase.CANCELLED);
  }

  private void finish(Phase result) {
    try {
      port.finish(key);
      phase = result;
    } catch (Exception error) {
      failure = error;
      phase = Phase.UNRESOLVED;
      reason = "finish:" + error.getClass().getSimpleName();
    }
  }

  public Throwable failure() {
    return failure;
  }

  public boolean busy() {
    return phase == Phase.WINDUP || phase == Phase.RECOVERY;
  }

  public Phase phase() {
    return phase;
  }

  public String reason() {
    return reason;
  }

  public int contacts() {
    return contacts;
  }

  public boolean attempted() {
    return attempted;
  }

  public Key key() {
    return key;
  }
}
