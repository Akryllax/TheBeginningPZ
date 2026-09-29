package net.akr.scenario;

import java.util.*;

/**
 * Bounded, engine-independent lifecycle core. IO threads submit/cancel/read detached data; only the
 * bound game thread advances execution or resource ownership. No engine callbacks or IO run under
 * this monitor. Durable persistence and the transport are separate adapters.
 */
final class EventScheduler {
  enum Kind {
    ROUTE,
    AVOIDANCE,
    STATIC_IMPACT,
    OPPOSING_IMPACT,
    PEDESTRIAN,
    ACTOR,
    ENCOUNTER
  }

  enum Phase {
    QUEUED,
    PREPARING,
    RUNNING,
    STOPPING,
    CLEANING,
    CLEANUP_BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  record Definition(
      Kind kind,
      long seed,
      String moduleVersion,
      String scenarioVersion,
      PedestrianDefinition pedestrian,
      EncounterDefinition encounter) {
    Definition(
        Kind kind,
        long seed,
        String moduleVersion,
        String scenarioVersion,
        PedestrianDefinition pedestrian) {
      this(kind, seed, moduleVersion, scenarioVersion, pedestrian, null);
    }

    Definition(Kind kind, long seed, String moduleVersion, String scenarioVersion) {
      this(kind, seed, moduleVersion, scenarioVersion, null, null);
    }

    Definition {
      Objects.requireNonNull(kind);
      if ((kind == Kind.ENCOUNTER) != (encounter != null))
        throw new IllegalArgumentException("encounter_kind");
      identifier(moduleVersion);
      identifier(scenarioVersion);
      if ((kind == Kind.PEDESTRIAN || kind == Kind.ACTOR) != (pedestrian != null)
          || (kind == Kind.ACTOR)
              != (pedestrian != null && pedestrian.entity() == PedestrianDefinition.Entity.ACTOR))
        throw new IllegalArgumentException("definition_kind");
    }
  }

  record Context(String world, String epoch, String requestId) {}

  record Reply(boolean accepted, String code, String eventId) {}

  record View(
      String id,
      Definition definition,
      Phase phase,
      boolean cancelRequested,
      String reason,
      List<EventResources.Resource> resources) {}

  private record Request(Definition definition, Reply reply) {}

  private static final class Event {
    final String id;
    final Definition definition;
    final EventResources resources = new EventResources();
    Phase phase = Phase.QUEUED;
    Phase outcome = Phase.COMPLETED;
    boolean cancel;
    String reason = "";

    Event(String id, Definition definition) {
      this.id = id;
      this.definition = definition;
    }

    View view() {
      return new View(id, definition, phase, cancel, reason, resources.snapshot());
    }
  }

  private final String world, epoch;
  private final int queueCapacity, receiptCapacity;
  private final ArrayDeque<Event> pending = new ArrayDeque<>();
  private final LinkedHashMap<String, Event> events = new LinkedHashMap<>();
  private final Map<String, Request> requests = new HashMap<>();
  private Event active;
  private Thread gameThread;
  private long sequence;

  /** Bind event IDs and bounded queue/receipt capacity to one world and server epoch. */
  EventScheduler(String world, String epoch, int queueCapacity, int receiptCapacity) {
    identifier(world);
    identifier(epoch);
    if (queueCapacity < 1
        || queueCapacity > 64
        || receiptCapacity < queueCapacity
        || receiptCapacity > 16384) throw new IllegalArgumentException("scheduler_capacity");
    this.world = world;
    this.epoch = epoch;
    this.queueCapacity = queueCapacity;
    this.receiptCapacity = receiptCapacity;
  }

  private static void identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_.-]{1,128}"))
      throw new IllegalArgumentException("identifier");
  }

  private void context(Context context) {
    if (context == null || !world.equals(context.world()) || !epoch.equals(context.epoch()))
      throw new IllegalArgumentException("stale_world_or_epoch");
    identifier(context.requestId());
  }

  private void gameThread() {
    if (gameThread == null) gameThread = Thread.currentThread();
    if (gameThread != Thread.currentThread()) throw new IllegalStateException("not_game_thread");
  }

  /** Reserve a queue slot idempotently; a repeated request ID returns its first receipt. */
  synchronized Reply submit(Context context, Definition definition) {
    context(context);
    Objects.requireNonNull(definition);
    Request previous = requests.get(context.requestId());
    if (previous != null) {
      if (!previous.definition().equals(definition))
        return new Reply(false, "request_conflict", "");
      return previous.reply();
    }
    // Never evict deduplication history and then accidentally execute an old retry.
    if (requests.size() >= receiptCapacity) return new Reply(false, "receipt_capacity", "");
    if (pending.size() >= queueCapacity) return new Reply(false, "busy", "");
    Event event = new Event(epoch + "." + (++sequence), definition);
    Reply reply = new Reply(true, "queued", event.id);
    requests.put(context.requestId(), new Request(definition, reply));
    events.put(event.id, event);
    pending.addLast(event);
    return reply;
  }

  /**
   * Cancellation does not consume queue or receipt capacity, and repeated requests are harmless.
   */
  /** Request cancellation while retaining resources until verified cleanup. */
  synchronized Reply cancel(Context context, String eventId) {
    context(context);
    Event event = events.get(eventId);
    if (event == null) return new Reply(false, "unknown_event", eventId);
    if (terminal(event.phase)) return new Reply(true, "already_terminal", eventId);
    if (event.phase == Phase.CLEANING || event.phase == Phase.CLEANUP_BLOCKED)
      return new Reply(true, "already_cleaning", eventId);
    event.cancel = true;
    if (event.phase == Phase.QUEUED) {
      pending.remove(event);
      event.phase = Phase.CANCELLED;
      event.reason = "cancelled_before_start";
    }
    return new Reply(true, "cancel_requested", eventId);
  }

  /** Call once from the resident game-thread hook; execution happens outside the scheduler lock. */
  /** Admit one queued event on the bound game thread and return its current view. */
  synchronized View next() {
    gameThread();
    if (active == null) {
      active = pending.pollFirst();
      if (active != null) active.phase = Phase.PREPARING;
    }
    if (active != null
        && active.cancel
        && (active.phase == Phase.PREPARING || active.phase == Phase.RUNNING))
      active.phase = Phase.STOPPING;
    return active == null ? null : active.view();
  }

  private Event active(String id) {
    if (gameThread != Thread.currentThread()) throw new IllegalStateException("not_game_thread");
    if (active == null || !active.id.equals(id))
      throw new IllegalArgumentException("not_active_event");
    return active;
  }

  /** Mark a prepared event active after its backend has established ownership. */
  synchronized void running(String id) {
    Event event = active(id);
    if (event.phase != Phase.PREPARING || event.cancel)
      throw new IllegalStateException("not_preparing");
    event.phase = Phase.RUNNING;
  }

  /** Record an owned native resource before subsequent effects can run. */
  synchronized void acquire(String id, EventResources.Resource resource) {
    Event event = active(id);
    if ((event.phase != Phase.PREPARING && event.phase != Phase.RUNNING) || event.cancel)
      throw new IllegalStateException("cannot_acquire");
    event.resources.acquire(resource);
  }

  synchronized boolean release(
      String id, EventResources.Resource resource, boolean worldRemoved, boolean nativeRemoved) {
    return active(id).resources.release(resource, worldRemoved, nativeRemoved);
  }

  /** Execution finishing is not event completion. Braking must finish before calling this. */
  /** Enter cleanup, recording whether execution failed and why. */
  synchronized void cleaning(String id, boolean failed, String reason) {
    Event event = active(id);
    if (event.phase == Phase.CLEANING || event.phase == Phase.CLEANUP_BLOCKED)
      throw new IllegalStateException("already_cleaning");
    if (reason == null || reason.length() > 256) throw new IllegalArgumentException("reason");
    event.outcome = failed ? Phase.FAILED : event.cancel ? Phase.CANCELLED : Phase.COMPLETED;
    event.reason = reason;
    event.phase = Phase.CLEANING;
  }

  /** Complete cleanup only when every resource and the baseline are verified absent. */
  synchronized boolean cleaned(String id, boolean baselineVerified) {
    Event event = active(id);
    if (event.phase != Phase.CLEANING && event.phase != Phase.CLEANUP_BLOCKED)
      throw new IllegalStateException("not_cleaning");
    if (!event.resources.empty() || !baselineVerified) {
      event.phase = Phase.CLEANUP_BLOCKED;
      return false;
    }
    event.phase = event.outcome;
    active = null;
    return true;
  }

  /** Return the latest bounded view for one event ID. */
  synchronized View status(String id) {
    Event event = events.get(id);
    return event == null ? null : event.view();
  }

  /** Page terminal receipts without exposing mutable scheduler internals. */
  synchronized List<View> outcomes(int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 64) throw new IllegalArgumentException("pagination");
    return events.values().stream()
        .filter(e -> terminal(e.phase))
        .skip(offset)
        .limit(limit)
        .map(Event::view)
        .toList();
  }

  private static boolean terminal(Phase phase) {
    return phase == Phase.COMPLETED || phase == Phase.FAILED || phase == Phase.CANCELLED;
  }
}
