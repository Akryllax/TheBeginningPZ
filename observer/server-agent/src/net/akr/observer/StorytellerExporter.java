package net.akr.observer;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.akr.observer.protocol.Positions;

/** Optional independent worker. One pending snapshot; no queue, files or commands. */
final class StorytellerExporter {
  record Sample(long sequence, long capturedAt, GameStoryteller.Snapshot state) {}

  private final AtomicReference<Sample> pending = new AtomicReference<>();
  private final String world, session, token;
  private final long started;
  private final URI endpoint;
  private final Thread sender;
  private GameStoryteller game;
  private boolean enabled = true;
  private long sequence, nextCapture, nextWarning;

  StorytellerExporter(String world, String session, long started, String token, URI endpoint) {
    this.world = world;
    this.session = session;
    this.started = started;
    this.token = token;
    this.endpoint = endpoint.resolve("/internal/v1/storyteller");
    sender = new Thread(this::sendLoop, "observer-storyteller-sender");
    sender.setDaemon(true);
    sender.start();
  }

  void capture(long now) {
    if (!enabled || now < nextCapture) return;
    nextCapture = now + 5_000_000_000L;
    try {
      if (game == null) game = new GameStoryteller(PositionAgent.class.getClassLoader());
      GameStoryteller.Snapshot state = game.capture();
      if (state == null) return;
      pending.set(new Sample(++sequence, System.currentTimeMillis(), state));
      LockSupport.unpark(sender);
    } catch (Throwable error) {
      enabled = false;
      pending.set(null);
      PositionAgent.log(
          "Storyteller adapter failed ("
              + error.getClass().getSimpleName()
              + "); storyteller export disabled until restart. Positions and exploration remain"
              + " enabled.");
    }
  }

  static byte[] encode(Sample sample, String world, String session, long started) {
    var s = sample.state();
    var t = s.timings();
    var m =
        Positions.StorytellerSnapshot.newBuilder()
            .setProtocolVersion(1)
            .setWorld(world)
            .setServerSession(session)
            .setSessionStartedAtMs(started)
            .setSequence(sample.sequence())
            .setCapturedAtMs(sample.capturedAt())
            .setPublishedTick(s.tick())
            .setWorldAgeHours(s.age())
            .setPhase(s.phase())
            .setMode(s.mode())
            .setPressure(s.pressure())
            .setBudget(s.budget())
            .setOnlinePlayers(s.players())
            .setNpcCount(s.npcs())
            .setCellCount(s.cellCount())
            .setScanQueue(s.queue())
            .setDroppedCells(s.dropped())
            .setHealth(s.health())
            .setTruncated(s.truncated())
            .setCaptureMs(s.captureMs())
            .setTimingsMs(
                Positions.StorytellerTimings.newBuilder()
                    .setLast(t.last())
                    .setP95(t.p95())
                    .setP99(t.p99())
                    .setMax(t.max()));
    for (var c : s.cells())
      m.addCells(
          Positions.StorytellerCell.newBuilder()
              .setX(c.x())
              .setY(c.y())
              .setZ(c.z())
              .setDwell(c.dwell())
              .setWealth(c.wealth())
              .setConfidence(c.confidence())
              .setAgeHours(c.age()));
    for (var d : s.decisions())
      m.addDecisions(
          Positions.StorytellerDecision.newBuilder()
              .setId(d.id())
              .setEvent(d.event())
              .setOutcome(d.outcome())
              .setReason(d.reason())
              .setAtHour(d.hour()));
    if (s.scenario() != null) {
      var v = s.scenario();
      m.setScenario(
          Positions.FirstWeekDiagnostics.newBuilder()
              .setStatus(v.status())
              .setPhase(v.phase())
              .setElapsedHours(v.elapsed())
              .setResidents(v.residents())
              .setMaterialized(v.materialized())
              .setPedestrians(v.pedestrians())
              .setVehicles(v.vehicles())
              .setWorkerHealth(v.workerHealth())
              .setWorkerComputeMs(v.computeMs())
              .setPlanRejections(v.rejected())
              .setLeaseChanges(v.leases())
              .setLastStepMs(v.last())
              .setP95StepMs(v.p95())
              .setP99StepMs(v.p99())
              .setWorkerQueue(v.queue()));
    }
    return m.build().toByteArray();
  }

  private void sendLoop() {
    try (HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build()) {
      while (true) {
        Sample sample = pending.getAndSet(null);
        if (sample == null) {
          LockSupport.parkNanos(250_000_000L);
          continue;
        }
        if (System.currentTimeMillis() - sample.capturedAt() > 15000) continue;
        try {
          byte[] payload = encode(sample, world, session, started);
          if (payload.length > 65536)
            throw new IllegalArgumentException("Storyteller snapshot too large");
          var request =
              HttpRequest.newBuilder(endpoint)
                  .timeout(Duration.ofSeconds(2))
                  .header("Authorization", "Bearer " + token)
                  .header("Content-Type", "application/x-protobuf")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                  .build();
          if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 204)
            warn();
        } catch (Exception error) {
          warn();
        }
      }
    } catch (Throwable error) {
      PositionAgent.log("Storyteller sender stopped; game, positions and exploration continue.");
    }
  }

  private void warn() {
    long now = System.nanoTime();
    if (now >= nextWarning) {
      nextWarning = now + 60_000_000_000L;
      PositionAgent.log(
          "Observer unavailable or rejecting storyteller diagnostics; dropping old samples.");
    }
  }
}
