package net.lofers.scenario;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Private operator files, detached diagnostics and bounded route geometry; no game objects. */
final class ProbeControl implements Runnable {
  record Config(
      Path directory,
      double x,
      double y,
      double yaw,
      double distance,
      double speed,
      ProbeRoute route,
      boolean roadMode,
      boolean driverModel,
      String vehicleScript,
      List<ProbeDriver.Stop> stops,
      boolean bypass,
      boolean shoulder,
      ProbeImpactTarget impact,
      boolean opposing) {
    Config(Path directory, double x, double y, double yaw, double distance, double speed) {
      this(
          directory,
          x,
          y,
          yaw,
          distance,
          speed,
          ProbeRoute.straight(x, y, yaw, distance),
          false,
          false,
          "Base.SmallCar",
          List.of(),
          false,
          false,
          null,
          false);
    }

    double deadlineSeconds() {
      return roadMode ? 120 : 30;
    }

    static Config read(Properties p, String world, boolean server) {
      if (!Boolean.parseBoolean(p.getProperty("vehicle_probe.enabled", "false"))) return null;
      if (!server || !world.matches("LofersVehicleProbe_[A-Za-z0-9_-]{1,64}"))
        throw new IllegalArgumentException(
            "Vehicle probe requires a disposable LofersVehicleProbe_ world on server");
      Path dir = Path.of(p.getProperty("vehicle_probe.directory", "")).normalize();
      if (!dir.isAbsolute() || dir.getParent() == null)
        throw new IllegalArgumentException("Private absolute probe directory required");
      double x = bounded(p, "x", Double.NaN, -20000, 60000),
          y = bounded(p, "y", Double.NaN, -20000, 60000);
      if (bounded(p, "z", 0, 0, 0) != 0)
        throw new IllegalArgumentException("Ground-level probe only");
      String points = p.getProperty("vehicle_probe.waypoints", "").strip();
      boolean roadMode = !points.isEmpty();
      String curves = p.getProperty("vehicle_probe.beziers", "").strip();
      boolean laneMode = Boolean.parseBoolean(p.getProperty("vehicle_probe.lane_mode", "false"));
      if (laneMode && !roadMode)
        throw new IllegalArgumentException("Lane mode requires road route");
      boolean extendedImpact =
          Boolean.parseBoolean(p.getProperty("vehicle_probe.extended_impact", "false"));
      double yaw = bounded(p, "heading_degrees", 90, 0, 360),
          distance = bounded(p, "distance", 10, 2, 12),
          speed =
              bounded(
                  p,
                  "speed_kmh",
                  4,
                  1,
                  !curves.isEmpty() ? (extendedImpact ? 120 : 50) : laneMode ? 15 : 5);
      if (!curves.isEmpty() && (!roadMode || !laneMode))
        throw new IllegalArgumentException("Bezier course requires lane mode");
      ProbeRoute route =
          !curves.isEmpty()
              ? new ProbeRoute(BezierPath.parse(curves, extendedImpact))
              : (roadMode
                  ? ProbeRoute.parse(points, laneMode)
                  : ProbeRoute.straight(x, y, yaw, distance));
      if (Math.hypot(route.points.getFirst().x() - x, route.points.getFirst().y() - y) > 0.01)
        throw new IllegalArgumentException("Route must start at configured spawn");
      if (Math.abs(ProbeRoute.wrap(route.heading() - Math.toRadians(yaw))) > Math.toRadians(10))
        throw new IllegalArgumentException("Spawn heading differs from route");
      String script = p.getProperty("vehicle_probe.script", "Base.SmallCar");
      if (!Set.of("Base.SmallCar", "Base.LofersSmallCar").contains(script)
          && !(extendedImpact && script.equals("Base.SportsCar")))
        throw new IllegalArgumentException("Unsupported probe vehicle script");
      List<ProbeDriver.Stop> stops = new ArrayList<>();
      String stopText = p.getProperty("vehicle_probe.stops", "");
      if (stopText.length() > 256) throw new IllegalArgumentException("Stop config too long");
      if (!stopText.isBlank())
        for (String item : stopText.split(";", -1)) {
          String[] pair = item.split(",", -1);
          if (pair.length != 2) throw new IllegalArgumentException("Invalid stop config");
          stops.add(new ProbeDriver.Stop(Double.parseDouble(pair[0]), Double.parseDouble(pair[1])));
        }
      new ProbeDriver(route, speed, 2, stops); // Validate bounded stop contract before startup.
      boolean bypass = Boolean.parseBoolean(p.getProperty("vehicle_probe.bypass", "false"));
      if (bypass && (!roadMode || !laneMode || !stops.isEmpty() || !TrafficBypass.straight(route)))
        throw new IllegalArgumentException(
            "Bypass requires a reviewed straight road without junction stops");
      if (bypass)
        route =
            new ProbeRoute(
                route.trajectory, 13); // Retain both detours' normal nine-tile observation margins.
      boolean shoulder = Boolean.parseBoolean(p.getProperty("vehicle_probe.shoulder", "false"));
      if (shoulder && !bypass)
        throw new IllegalArgumentException("Shoulder choice requires a reviewed bypass route");
      ProbeImpactTarget impact =
          ProbeImpactTarget.read(p, route, roadMode, bypass, !stops.isEmpty());
      if (extendedImpact && (impact == null || !route.extendedImpact))
        throw new IllegalArgumentException("Extended course requires marked impact scene");
      boolean opposing = Boolean.parseBoolean(p.getProperty("vehicle_probe.opposing", "false"));
      if (opposing)
        throw new IllegalArgumentException(
            "Opposing impacts require the resident runtime; legacy probe supports one vehicle"
                + " only");
      return new Config(
          dir,
          x,
          y,
          yaw,
          roadMode ? route.length : distance,
          speed,
          route,
          roadMode,
          script.equals("Base.LofersSmallCar"),
          script,
          List.copyOf(stops),
          bypass,
          shoulder,
          impact,
          opposing);
    }

    private static double bounded(
        Properties p, String name, double fallback, double min, double max) {
      double n =
          Double.parseDouble(p.getProperty("vehicle_probe." + name, Double.toString(fallback)));
      if (!Double.isFinite(n) || n < min || n > max)
        throw new IllegalArgumentException("Invalid vehicle_probe." + name);
      return n;
    }
  }

  record Command(long id, String action) {}

  final ProbeCommands command = new ProbeCommands();
  final AtomicReference<Map<String, String>> status = new AtomicReference<>();
  final AtomicReference<TrafficBypass.Job> bypassJob = new AtomicReference<>();
  final AtomicReference<TrafficBypass.Result> bypassResult = new AtomicReference<>();
  volatile long bypassPlanningNanos;
  volatile String ioError = "";
  private final Path directory;
  private final String epoch;
  private long lastCommand;

  ProbeControl(Config config, String epoch) throws IOException {
    directory = config.directory();
    this.epoch = epoch;
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
      throw new IOException("Probe directory must already exist and not be a symlink");
  }

  static Command parse(byte[] bytes, String epoch, long last) throws IOException {
    if (bytes.length > 4096) throw new IOException("Control file exceeds 4096 bytes");
    Properties p = new Properties();
    p.load(new ByteArrayInputStream(bytes));
    if (!epoch.equals(p.getProperty("server_epoch")))
      throw new IOException("Control server_epoch mismatch");
    long id;
    try {
      id = Long.parseLong(p.getProperty("command_id", ""));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid command_id");
    }
    if (id <= last) return null;
    String action = p.getProperty("action", "");
    if (!Set.of("start", "stop", "inspect").contains(action))
      throw new IOException("Action must be start, stop or inspect");
    return new Command(id, action);
  }

  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      TrafficBypass.Job job = bypassJob.getAndSet(null);
      if (job != null) {
        long begin = System.nanoTime();
        try {
          bypassResult.set(TrafficBypass.plan(job));
        } catch (RuntimeException e) {
          bypassResult.set(new TrafficBypass.Result(job, List.of(), "candidate_geometry_rejected"));
        } finally {
          bypassPlanningNanos = System.nanoTime() - begin;
        }
      }
      try {
        Path input = directory.resolve("control.properties");
        if (Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
          byte[] bytes;
          try (var in = Files.newInputStream(input, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes(4097);
          }
          Command c = parse(bytes, epoch, lastCommand);
          if (c != null && !command.offer(c)) ioError = "busy:command_queue_full";
          else {
            if (c != null) lastCommand = c.id();
            ioError = "";
          }
        }
      } catch (Exception e) {
        ioError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      }
      try {
        Map<String, String> copy = status.get();
        if (copy != null) {
          Properties p = new Properties();
          p.putAll(copy);
          p.setProperty("control_error", ioError);
          Path temp = directory.resolve("status.properties.tmp"),
              target = directory.resolve("status.properties");
          try (var writer =
              Files.newBufferedWriter(
                  temp,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING,
                  LinkOption.NOFOLLOW_LINKS)) {
            p.store(writer, "Lofers server-only empty-car probe; no client observation implied");
          }
          Files.move(
              temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception e) {
        ioError = "status_write_failed:" + e.getClass().getSimpleName();
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
