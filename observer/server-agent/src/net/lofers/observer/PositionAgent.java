package net.lofers.observer;

import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.lofers.observer.protocol.Positions;

/** One immutable snapshot in memory, one daemon sender; no game-side commands. */
public final class PositionAgent {
    record Player(String username, String character, double x, double y, double z, boolean dead, String connection) {}
    record Sample(long sequence, long capturedAt, List<Player> players) {}
    private static final AtomicReference<Sample> pending = new AtomicReference<>();
    private static volatile boolean enabled;
    private static GamePositions game;
    private static ExplorationExporter exploration;
    private static StorytellerExporter storyteller;
    private static String world, token, session;
    private static URI endpoint;
    private static long startedAt, sequence, nextCapture;
    private static Thread sender;
    private static long nextWarning;

    public static void premain(String options, Instrumentation instrumentation) {
        try {
            if (Runtime.version().feature() != 25) throw new IllegalStateException("Java 25 required");
            Properties config = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(options))) { config.load(reader); }
            world = config.getProperty("world", "").strip();
            if (world.isEmpty() || world.length() > 128) throw new IllegalArgumentException("Invalid world");
            endpoint = URI.create(config.getProperty("endpoint", ""));
            if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || !"/internal/v1/positions".equals(endpoint.getPath())) {
                throw new IllegalArgumentException("Invalid endpoint");
            }
            token = Files.readString(Path.of(config.getProperty("token_file"))).strip();
            if (!token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid token file");
            session = UUID.randomUUID().toString();
            startedAt = System.currentTimeMillis();
            exploration = new ExplorationExporter(world, session, startedAt, token, endpoint);
            if ("true".equals(config.getProperty("storyteller_enabled", "false"))) {
                try { storyteller = new StorytellerExporter(world, session, startedAt, token, endpoint); }
                catch (Throwable error) { log("Storyteller worker unavailable; other exports remain enabled."); }
            }
            sender = new Thread(PositionAgent::sendLoop, "observer-position-sender");
            sender.setDaemon(true);
            sender.start();
            enabled = true;
            instrumentation.addTransformer(new PositionTransformer());
            log("Position exporter ready; one-second samples, no travel history.");
        } catch (Throwable error) {
            enabled = false;
            log("Invalid exporter setup; game continues without position export.");
        }
    }

    // The injected call must never propagate an exporter exception into the game.
    public static void tick() {
        if (!enabled) return;
        long now = System.nanoTime();
        if (now < nextCapture) return;
        nextCapture = now + 1_000_000_000L;
        try {
            if (game == null) game = new GamePositions(PositionAgent.class.getClassLoader(), world);
            List<Player> players = game.capture();
            pending.set(new Sample(++sequence, System.currentTimeMillis(), players));
            LockSupport.unpark(sender);
            exploration.capture(players, now);
            if (storyteller != null) storyteller.capture(now);
        } catch (Throwable error) {
            enabled = false;
            pending.set(null);
            log("Game adapter failed (" + error.getClass().getSimpleName() + ": "
                + error.getMessage() + "); position export disabled until restart.");
        }
    }

    static byte[] encode(Sample sample, String world, String session, long started) {
        var message = Positions.PositionSnapshot.newBuilder().setProtocolVersion(1).setWorld(world)
            .setServerSession(session).setSessionStartedAtMs(started).setSequence(sample.sequence())
            .setCapturedAtMs(sample.capturedAt());
        for (Player p : sample.players()) {
            message.addPlayers(Positions.PlayerPosition.newBuilder().setUsername(p.username())
                .setCharacter(p.character()).setX(p.x()).setY(p.y()).setZ(p.z()).setDead(p.dead())
                .setConnectionId(p.connection()));
        }
        return message.build().toByteArray();
    }

    private static void sendLoop() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build()) {
            while (true) {
                Sample sample = pending.getAndSet(null);
                if (sample == null) { LockSupport.parkNanos(250_000_000L); continue; }
                // After an outage send only a recent snapshot. Never replay a backlog.
                if (System.currentTimeMillis() - sample.capturedAt() > 5000) continue;
                try {
                    byte[] payload = encode(sample, world, session, startedAt);
                    if (payload.length > 65536) throw new IllegalArgumentException("Snapshot too large");
                    HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/x-protobuf")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                    int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                    if (status != 204) warn();
                } catch (Exception error) { warn(); }
            }
        } catch (Throwable error) {
            enabled = false;
            log("Position sender stopped; game continues without export.");
        }
    }

    private static void warn() {
        long now = System.nanoTime();
        if (now >= nextWarning) {
            nextWarning = now + 60_000_000_000L;
            log("Observer unavailable or rejecting positions; dropping old samples and retrying.");
        }
    }

    static void log(String text) { System.out.println("[ObserverPositions] " + text); }
}
