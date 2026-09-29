package net.akr.scenario.npc.core;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Game-independent physical ownership controller. All calls belong to one simulation thread. */
public final class CivilianPool<B> {
  public enum State {
    FREE,
    RESERVED,
    MATERIALIZING,
    ACTIVE,
    RETIRING,
    DYING,
    CORPSE,
    UNRESOLVED
  }

  public record Token(
      String epoch, int slot, long generation, String resident, long residentGeneration) {
    public Token {
      Objects.requireNonNull(epoch);
      Objects.requireNonNull(resident);
    }
  }

  /** Opaque stock serialization, not scalar health or a list of item types. */
  public record Snapshot(int codec, int worldVersion, String build, byte[] bytes, String digest) {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public Snapshot {
      if (codec != 1
          || worldVersion <= 0
          || build == null
          || build.isBlank()
          || bytes == null
          || bytes.length == 0
          || bytes.length > MAX_BYTES) throw new IllegalArgumentException("body_snapshot");
      bytes = bytes.clone();
      if (!hash(bytes).equals(digest)) throw new IllegalArgumentException("body_checksum");
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }

    public static Snapshot capture(int worldVersion, String build, byte[] bytes) {
      return new Snapshot(1, worldVersion, build, bytes, hash(bytes));
    }

    private static String hash(byte[] bytes) {
      try {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (java.security.NoSuchAlgorithmException error) {
        throw new AssertionError(error);
      }
    }
  }

  public record Removal(boolean worldGone, boolean networkGone) {
    public boolean confirmed() {
      return worldGone && networkGone;
    }
  }

  public interface Port<B> {
    /** Must call own immediately after construction, before any fallible setup. */
    void materialize(Token token, Snapshot saved, Consumer<B> own) throws Exception;

    boolean dead(B body) throws Exception;

    /** Missing visibility evidence returns true. */
    boolean visibleOrUnknown(B body) throws Exception;

    void stop(B body) throws Exception;

    Snapshot capture(B body) throws Exception;

    /** Idempotent; remove only this reference, never a replacement sharing its ID. */
    void retire(Token token, B body) throws Exception;

    Removal verify(Token token, B body) throws Exception;

    /** Native corpse exists before returning; null means its animation is still in progress. */
    default String handoffDeath(Token token, B body) throws Exception {
      throw new UnsupportedOperationException("death_handoff_unavailable");
    }

    /** Reclaim only after the terminal resident/corpse record has been persisted. */
    default boolean releaseDeath(Token token, B body, String corpseId) throws Exception {
      throw new UnsupportedOperationException("death_release_unavailable");
    }
  }

  /** Presentation admission can become stale before ownership. Retry without losing reservation. */
  public static final class AdmissionDeferred extends Exception {
    public AdmissionDeferred(String reason) {
      super(reason);
    }
  }

  public record View(Token token, State state, String reason, long actionRevision) {}

  public record Retired(Token token, Snapshot snapshot) {}

  public record Dead(Token token, String corpseId) {}

  private final class Slot {
    final int id;
    long generation, revision, quietUntil;
    State state = State.FREE;
    Token token;
    B body;
    Snapshot saved, pending;
    String reason = "";
    String corpseId;
    long diedAt;
    boolean retirementAttempted, failedMaterialization;

    Slot(int id) {
      this.id = id;
    }
  }

  private final String epoch;
  private final Port<B> port;
  private final List<Slot> slots = new ArrayList<>();
  private final ArrayDeque<Retired> retired = new ArrayDeque<>();
  private final ArrayDeque<Dead> dead = new ArrayDeque<>();
  private long lastTick = Long.MIN_VALUE;
  private long lastNowNanos;

  public CivilianPool(String epoch, Port<B> port) {
    this(epoch, port, 32);
  }

  public CivilianPool(String epoch, Port<B> port, int capacity) {
    if (capacity < 1 || capacity > 64) throw new IllegalArgumentException("actor_capacity");
    this.epoch = Objects.requireNonNull(epoch);
    this.port = Objects.requireNonNull(port);
    for (int i = 4096; i < 4096 + capacity; i++) slots.add(new Slot(i));
  }

  public Token reserve(String resident, long generation, boolean newResident, Snapshot saved) {
    if (resident == null || resident.isBlank() || generation < 1)
      throw new IllegalArgumentException("resident");
    if (!newResident && saved == null)
      throw new IllegalArgumentException("known_resident_requires_body");
    for (Slot s : slots)
      if (s.state != State.FREE && s.token.resident().equals(resident)) {
        if (s.token.residentGeneration() == generation) return s.token;
        throw new IllegalStateException("resident_already_bound");
      }
    for (Slot s : slots)
      if (s.state == State.FREE) {
        s.token = new Token(epoch, s.id, ++s.generation, resident, generation);
        s.state = State.RESERVED;
        s.saved = saved;
        s.pending = null;
        s.revision = 0;
        s.reason = "";
        s.retirementAttempted = false;
        s.failedMaterialization = false;
        s.corpseId = null;
        s.diedAt = 0;
        return s.token;
      }
    return null;
  }

  /** Monotonic tick identity prevents repeated calls from exceeding the spawn budget. */
  public void tick(long tick, long nowNanos) {
    if (tick <= lastTick) return;
    lastTick = tick;
    lastNowNanos = nowNanos;
    boolean spawned = false;
    for (Slot s : slots) {
      if (s.state == State.RESERVED && !spawned) {
        spawned = true;
        s.state = State.MATERIALIZING;
        try {
          port.materialize(
              s.token,
              s.saved,
              body -> {
                if (body == null || s.body != null)
                  throw new IllegalStateException("body_ownership");
                s.body = body;
              });
          if (s.body == null) throw new IllegalStateException("body_not_tracked");
          s.state = State.ACTIVE;
          s.reason = "";
        } catch (AdmissionDeferred deferred) {
          if (s.body == null) {
            s.state = State.RESERVED;
            s.reason = "admission_deferred";
          } else {
            s.failedMaterialization = true;
            unresolved(s, "deferred_after_ownership");
          }
        } catch (Exception failure) {
          s.failedMaterialization = true;
          String detail = Objects.toString(failure.getMessage(), "").replaceAll("[\\r\\n\\t]", " ");
          unresolved(
              s,
              "materialize:"
                  + failure.getClass().getSimpleName()
                  + ":"
                  + detail.substring(0, Math.min(256, detail.length())));
        }
      }
      if (s.state == State.RETIRING) advanceRetirement(s, nowNanos);
      if (s.state == State.ACTIVE && portDead(s)) {
        s.state = State.DYING;
        s.diedAt = nowNanos;
        s.reason = "awaiting_native_corpse";
      }
      if (s.state == State.DYING) advanceDeath(s, nowNanos);
    }
  }

  private boolean portDead(Slot s) {
    try {
      return port.dead(s.body);
    } catch (Exception error) {
      unresolved(s, "death_observation:" + error.getClass().getSimpleName());
      return false;
    }
  }

  private void advanceDeath(Slot s, long now) {
    if (now - s.diedAt < 500_000_000L || dead.size() >= slots.size()) return;
    try {
      String corpse = port.handoffDeath(s.token, s.body);
      if (corpse == null) return;
      if (corpse.isBlank()) throw new IllegalStateException("missing_corpse_identity");
      s.corpseId = corpse;
      s.state = State.CORPSE;
      s.quietUntil = now + 1_000_000_000L;
      s.reason = "awaiting_terminal_receipt";
      dead.addLast(new Dead(s.token, corpse));
    } catch (Exception error) {
      unresolved(s, "death_handoff:" + error.getClass().getSimpleName());
    }
  }

  public Dead pollDead() {
    Dead next = dead.peekFirst();
    Slot s = next == null ? null : find(next.token());
    return s != null && lastNowNanos >= s.quietUntil ? next : null;
  }

  public boolean acknowledgeDead(Token token, String corpseId) {
    Slot s = find(token);
    if (s == null
        || s.state != State.CORPSE
        || !Objects.equals(s.corpseId, corpseId)
        || dead.isEmpty()
        || !dead.peekFirst().token().equals(token)) return false;
    try {
      if (!port.releaseDeath(token, s.body, corpseId)) return false;
      dead.removeFirst();
      clear(s);
      return true;
    } catch (Exception error) {
      dead.removeFirst();
      unresolved(s, "death_release:" + error.getClass().getSimpleName());
      return false;
    }
  }

  public boolean acceptIntent(Token token, long revision) {
    Slot s = find(token);
    if (s == null || s.state != State.ACTIVE || revision <= s.revision) return false;
    s.revision = revision;
    return true;
  }

  public boolean current(Token token, long revision) {
    Slot s = find(token);
    return s != null && s.state == State.ACTIVE && s.revision == revision;
  }

  public boolean retire(Token token) {
    Slot s = find(token);
    if (s == null) return false;
    if (s.state == State.RETIRING) return true;
    if (s.state == State.RESERVED) {
      clear(s);
      return true;
    }
    if (s.body == null || s.state == State.MATERIALIZING) return false;
    try {
      if (port.dead(s.body)) {
        s.state = State.DYING;
        s.diedAt = lastNowNanos;
        s.reason = "awaiting_native_corpse";
        return false;
      }
      if (port.visibleOrUnknown(s.body)) {
        s.reason = "visible_or_unknown";
        return false;
      }
      port.stop(s.body);
      if (!s.retirementAttempted && !s.failedMaterialization) s.pending = port.capture(s.body);
      if (!s.failedMaterialization && s.pending == null)
        throw new IllegalStateException("snapshot_missing");
      s.state = State.RETIRING;
      s.reason = "";
      return true;
    } catch (Exception failure) {
      unresolved(s, "capture:" + failure.getClass().getSimpleName());
      return false;
    }
  }

  private void advanceRetirement(Slot s, long now) {
    try {
      if (port.dead(s.body)) {
        if (s.retirementAttempted) {
          unresolved(s, "death_during_native_removal");
          return;
        }
        s.state = State.DYING;
        s.diedAt = now;
        s.pending = null;
        s.reason = "death_during_retirement";
        return;
      }
      if (!s.retirementAttempted) {
        // Record ownership before either network or world removal can throw.
        s.retirementAttempted = true;
        s.quietUntil = now + 1_000_000_000L;
        port.retire(s.token, s.body);
      }
      if (!port.verify(s.token, s.body).confirmed()) {
        s.reason = "removal_unconfirmed";
        return;
      }
      if (now < s.quietUntil || retired.size() >= slots.size()) return;
      retired.add(new Retired(s.token, s.failedMaterialization ? s.saved : s.pending));
      clear(s);
    } catch (Exception failure) {
      unresolved(s, "retire:" + failure.getClass().getSimpleName());
    }
  }

  /** Explicit retry for an owned reference; no missing-body success assumption. */
  public boolean reconcile(Token token) {
    Slot s = find(token);
    if (s == null || s.state != State.UNRESOLVED || s.body == null) return false;
    if (s.retirementAttempted) {
      try {
        if (port.dead(s.body)) {
          unresolved(s, "corpse_owned");
          return false;
        }
        port.retire(s.token, s.body);
        s.state = State.RETIRING;
        return true;
      } catch (Exception error) {
        unresolved(s, "retry_retire:" + error.getClass().getSimpleName());
        return false;
      }
    }
    return retire(token);
  }

  public B body(Token token) {
    Slot s = find(token);
    return s == null ? null : s.body;
  }

  public Retired pollRetired() {
    return retired.poll();
  }

  public List<View> views() {
    return slots.stream().map(s -> new View(s.token, s.state, s.reason, s.revision)).toList();
  }

  public int occupied() {
    return (int) slots.stream().filter(s -> s.state != State.FREE).count();
  }

  private Slot find(Token token) {
    if (token == null) return null;
    for (Slot s : slots) if (token.equals(s.token)) return s;
    return null;
  }

  private void clear(Slot s) {
    s.state = State.FREE;
    s.body = null;
    s.token = null;
    s.saved = null;
    s.pending = null;
    s.corpseId = null;
    s.diedAt = 0;
  }

  private void unresolved(Slot s, String why) {
    s.state = State.UNRESOLVED;
    s.reason = why;
  }
}
