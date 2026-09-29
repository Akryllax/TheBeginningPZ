package net.lofers.observer;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.zip.DeflaterOutputStream;
import net.lofers.observer.protocol.Positions;

/** One detached player mask per two seconds, round-robin, on its own worker. */
final class ExplorationExporter {
  record Sample(long sequence, long capturedAt, GameExploration.Mask mask) {}

  private final AtomicReference<Sample> pending = new AtomicReference<>();
  private final String world, session, token;
  private final long started;
  private final URI endpoint;
  private final Thread sender;
  private GameExploration game;
  private boolean enabled = true;
  private long sequence, nextCapture, nextWarning;
  private int cursor;

  ExplorationExporter(
      String world, String session, long started, String token, URI positionsEndpoint) {
    this.world = world;
    this.session = session;
    this.started = started;
    this.token = token;
    endpoint = positionsEndpoint.resolve("/internal/v1/exploration");
    sender = new Thread(this::sendLoop, "observer-exploration-sender");
    sender.setDaemon(true);
    sender.start();
  }

  void capture(List<PositionAgent.Player> players, long now) {
    if (!enabled) return;
    if (now < nextCapture) return;
    nextCapture = now + 2_000_000_000L;
    try {
      if (game == null) game = new GameExploration(PositionAgent.class.getClassLoader());
      List<String> names = players.stream().map(PositionAgent.Player::username).sorted().toList();
      GameExploration.Mask mask =
          names.isEmpty() ? null : game.capture(names.get(Math.floorMod(cursor++, names.size())));
      pending.set(new Sample(++sequence, System.currentTimeMillis(), mask));
      LockSupport.unpark(sender);
    } catch (Throwable error) {
      enabled = false;
      pending.set(null);
      PositionAgent.log(
          "Exploration adapter failed ("
              + error.getClass().getSimpleName()
              + "); exploration export disabled until restart. Positions remain enabled.");
    }
  }

  static byte[] encode(Sample sample, String world, String session, long started) throws Exception {
    var message =
        Positions.ExplorationSnapshot.newBuilder()
            .setProtocolVersion(1)
            .setWorld(world)
            .setServerSession(session)
            .setSessionStartedAtMs(started)
            .setSequence(sample.sequence())
            .setCapturedAtMs(sample.capturedAt());
    GameExploration.Mask m = sample.mask();
    if (m != null) {
      var bytes = new ByteArrayOutputStream();
      try (var zipped = new DeflaterOutputStream(bytes)) {
        zipped.write(m.flags());
      }
      message.addPlayers(
          Positions.PlayerExploration.newBuilder()
              .setUsername(m.username())
              .setMinCellX(m.minX())
              .setMinCellY(m.minY())
              .setMaxCellX(m.maxX())
              .setMaxCellY(m.maxY())
              .setWorldVersion(m.version())
              .setVisitedZlib(ByteString.copyFrom(bytes.toByteArray())));
    }
    return message.build().toByteArray();
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
        if (System.currentTimeMillis() - sample.capturedAt() > 5000) continue;
        try {
          byte[] payload = encode(sample, world, session, started);
          if (payload.length > 8 * 1024 * 1024)
            throw new IllegalArgumentException("Exploration too large");
          if (System.currentTimeMillis() - sample.capturedAt() > 5000) continue;
          HttpRequest request =
              HttpRequest.newBuilder(endpoint)
                  .timeout(Duration.ofSeconds(3))
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
      PositionAgent.log(
          "Exploration sender stopped; game and positions continue without exploration export.");
    }
  }

  private void warn() {
    long now = System.nanoTime();
    if (now >= nextWarning) {
      nextWarning = now + 60_000_000_000L;
      PositionAgent.log(
          "Observer unavailable or rejecting exploration; retaining only the latest pending mask.");
    }
  }
}
