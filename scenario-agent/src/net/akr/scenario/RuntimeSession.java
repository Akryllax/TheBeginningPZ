package net.akr.scenario;

import java.util.List;
import net.akr.scenario.protocol.RuntimeControl.*;

/** One game-thread backend; transport threads see detached protobuf snapshots only. */
final class RuntimeSession {
  interface Backend {
    void begin(EventScheduler.View event, EventScheduler scheduler);

    boolean prepare();

    boolean update();

    boolean cleanup();

    List<ActorSample> samples();

    default EncounterProgress encounter() {
      return EncounterProgress.getDefaultInstance();
    }

    default boolean encountersEnabled() {
      return false;
    }
  }

  /** Selects the engine adapter per event; only the active event's adapter is ever touched. */
  static final class Routed implements Backend {
    private final java.util.Map<EventScheduler.Kind, Backend> backends;
    private Backend current;

    Routed(java.util.Map<EventScheduler.Kind, Backend> backends) {
      this.backends = java.util.Map.copyOf(backends);
    }

    public void begin(EventScheduler.View event, EventScheduler scheduler) {
      Backend next = backends.get(event.definition().kind());
      if (next == null) throw new IllegalStateException("backend_unavailable");
      current = next;
      current.begin(event, scheduler);
    }

    public EncounterProgress encounter() {
      return current == null ? EncounterProgress.getDefaultInstance() : current.encounter();
    }

    public boolean encountersEnabled() {
      return backends.containsKey(EventScheduler.Kind.ENCOUNTER);
    }

    public boolean prepare() {
      return current.prepare();
    }

    public boolean update() {
      return current.update();
    }

    public boolean cleanup() {
      return current.cleanup();
    }

    public List<ActorSample> samples() {
      return current == null ? List.of() : current.samples();
    }
  }

  final EventScheduler scheduler;
  final String world, epoch;
  private final Backend backend;
  private ProbeTiming timing = new ProbeTiming();
  private String activeId;
  private long lastSample;
  private volatile boolean ticked;
  private volatile Reply sample = Reply.getDefaultInstance();

  RuntimeSession(String world, String epoch, Backend backend) {
    this.world = world;
    this.epoch = epoch;
    this.backend = backend;
    scheduler = new EventScheduler(world, epoch, 8, 256);
  }

  Reply handle(Request request) {
    Reply.Builder reply =
        Reply.newBuilder()
            .setVersion(1)
            .setWorld(world)
            .setEpoch(epoch)
            .setRequestId(request.getRequestId())
            .setCapability(
                backend.encountersEnabled()
                    ? "pedestrian-experiment-unvalidated;civilian-encounter-v1"
                    : "pedestrian-experiment-unvalidated");
    try {
      if (request.getVersion() != 1) throw new IllegalArgumentException("protocol_version");
      if (request.getOperation() == Request.Operation.HELLO)
        return reply
            .setAccepted(true)
            .setCode("hello")
            .setPhase(ticked ? "READY" : "BOOTING")
            .build();
      var context =
          new EventScheduler.Context(
              request.getWorld(), request.getEpoch(), request.getRequestId());
      if (!world.equals(request.getWorld()) || !epoch.equals(request.getEpoch()))
        throw new IllegalArgumentException("stale_world_or_epoch");
      if (!request.getRequestId().matches("[A-Za-z0-9_.-]{1,128}"))
        throw new IllegalArgumentException("request_id");
      String id = request.getEventId();
      if (request.getOperation() == Request.Operation.SUBMIT) {
        if (request.hasCivilianEncounter()) {
          if (request.hasPedestrian() || !backend.encountersEnabled())
            throw new IllegalArgumentException("encounter_payload_or_capability");
          var encounter = EncounterDefinition.from(request.getCivilianEncounter());
          var definition =
              new EventScheduler.Definition(
                  EventScheduler.Kind.ENCOUNTER,
                  encounter.seed(),
                  "encounter-1",
                  "1",
                  null,
                  encounter);
          var submitted = scheduler.submit(context, definition);
          return reply
              .setAccepted(submitted.accepted())
              .setCode(submitted.code())
              .setEventId(submitted.eventId())
              .build();
        }
        if (backend.encountersEnabled())
          throw new IllegalArgumentException("encounter_session_exclusive");
        if (!request.hasPedestrian()) throw new IllegalArgumentException("pedestrian_required");
        var pedestrian = PedestrianDefinition.from(request.getPedestrian());
        var kind =
            pedestrian.entity() == PedestrianDefinition.Entity.ACTOR
                ? EventScheduler.Kind.ACTOR
                : EventScheduler.Kind.PEDESTRIAN;
        var definition =
            new EventScheduler.Definition(
                kind, request.getPedestrian().getSeed(), "pedestrian-1", "1", pedestrian);
        var submitted = scheduler.submit(context, definition);
        return reply
            .setAccepted(submitted.accepted())
            .setCode(submitted.code())
            .setEventId(submitted.eventId())
            .build();
      } else if (request.getOperation() == Request.Operation.CANCEL) {
        var cancelled = scheduler.cancel(context, id);
        return reply
            .setAccepted(cancelled.accepted())
            .setCode(cancelled.code())
            .setEventId(cancelled.eventId())
            .build();
      } else if (request.getOperation() != Request.Operation.STATUS) {
        throw new IllegalArgumentException("operation");
      }
      var state = scheduler.status(id);
      if (state == null) return reply.setCode("unknown_event").build();
      reply
          .setAccepted(true)
          .setCode("status")
          .setEventId(id)
          .setPhase(state.phase().name())
          .setReason(state.reason());
      for (var resource : state.resources())
        reply.addResources(resource.kind() + ":" + resource.id());
      Reply observed = sample;
      if (id.equals(observed.getEventId()))
        reply
            .addAllActors(observed.getActorsList())
            .setEncounter(observed.getEncounter())
            .setSampleNanos(observed.getSampleNanos())
            .setWorkP95Ms(observed.getWorkP95Ms())
            .setWorkP99Ms(observed.getWorkP99Ms())
            .setWorkMaxMs(observed.getWorkMaxMs());
      return reply.build();
    } catch (IllegalArgumentException invalid) {
      return reply.setAccepted(false).setCode(invalid.getMessage()).build();
    }
  }

  void tick() {
    ticked = true;
    long started = System.nanoTime();
    EventScheduler.View event = scheduler.next();
    if (event == null) return;
    try {
      if (!event.id().equals(activeId)) {
        activeId = event.id();
        timing = new ProbeTiming();
        backend.begin(event, scheduler);
      }
      switch (event.phase()) {
        case PREPARING -> {
          if (backend.prepare()) scheduler.running(event.id());
        }
        case RUNNING -> {
          if (backend.update()) scheduler.cleaning(event.id(), false, "route_complete");
        }
        case STOPPING -> scheduler.cleaning(event.id(), false, "operator_cancelled");
        case CLEANING, CLEANUP_BLOCKED -> scheduler.cleaned(event.id(), backend.cleanup());
        default -> throw new IllegalStateException("unexpected_phase");
      }
    } catch (Throwable error) {
      var state = scheduler.status(event.id());
      if (state.phase() != EventScheduler.Phase.CLEANING
          && state.phase() != EventScheduler.Phase.CLEANUP_BLOCKED) {
        error.printStackTrace();
        Throwable root = error;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++)
          root = root.getCause();
        scheduler.cleaning(
            event.id(),
            true,
            bounded(
                error.getMessage()
                    + "; cause="
                    + root.getClass().getSimpleName()
                    + ":"
                    + root.getMessage()));
      } else scheduler.cleaned(event.id(), false);
    } finally {
      try {
        if (started - lastSample >= 100_000_000L
            || event.phase() == EventScheduler.Phase.CLEANING
            || event.phase() == EventScheduler.Phase.CLEANUP_BLOCKED) {
          lastSample = started;
          sample =
              Reply.newBuilder()
                  .setEventId(event.id())
                  .setSampleNanos(started)
                  .addAllActors(backend.samples())
                  .setEncounter(backend.encounter())
                  .setWorkP95Ms(timing.percentile(.95))
                  .setWorkP99Ms(timing.percentile(.99))
                  .setWorkMaxMs(timing.max / 1_000_000.0)
                  .build();
        }
      } finally {
        // Includes observation capture/serialization; reported percentiles lag one tick.
        timing.add(System.nanoTime() - started);
      }
    }
  }

  private static String bounded(String value) {
    return value == null ? "" : value.substring(0, Math.min(160, value.length()));
  }
}
