package net.akr.scenario;

import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.vehicles.BaseVehicle;

/** Adds the missing dedicated-server sound to an actual stock crash call. */
public final class ProbeCrashFeedback {
  private static final java.util.IdentityHashMap<BaseVehicle, Long> managed =
      new java.util.IdentityHashMap<>();
  private static Thread owner;
  private static long crashes, soundRequests, failures;
  private static float lastAmount, lastX, lastY;
  private static boolean lastFront;
  private static String lastSound = "", lastFailure = "";

  /** The probe calls this only after registering its native body. */
  static void register(BaseVehicle vehicle) {
    if (vehicle == null)
      throw new IllegalArgumentException("Crash feedback requires a native vehicle");
    if (managed.size() >= 2
        || managed.containsKey(vehicle)
        || (!managed.isEmpty() && owner != Thread.currentThread()))
      throw new IllegalStateException("Crash feedback registration conflict");
    if (managed.isEmpty()) {
      owner = Thread.currentThread();
      crashes = soundRequests = failures = 0;
      lastAmount = lastX = lastY = 0;
      lastFront = false;
      lastSound = lastFailure = "";
    }
    managed.put(vehicle, 0L);
  }

  /** Retain the final telemetry, but revoke the hook before removing the native body. */
  static void unregister(BaseVehicle vehicle) {
    managed.remove(vehicle);
    if (managed.isEmpty()) owner = null;
  }

  static boolean accepts(
      Object registered,
      Object observed,
      Thread registeredThread,
      Thread currentThread,
      boolean server,
      boolean client,
      boolean nativeServerOwner) {
    return registered != null
        && registered == observed
        && registeredThread == currentThread
        && server
        && !client
        && nativeServerOwner;
  }

  static String sound(float amount) {
    if (!Float.isFinite(amount) || amount <= 0) return "";
    return amount < 5 ? "VehicleCrash1" : amount < 30 ? "VehicleCrash2" : "VehicleCrash";
  }

  /** Instrumented only at BaseVehicle.crash(FZ)V; never creates an impact or applies damage. */
  public static void onCrash(BaseVehicle vehicle, float amount, boolean front) {
    if (vehicle == null || !managed.containsKey(vehicle) || Thread.currentThread() != owner) return;
    try {
      if (!accepts(
          managed.containsKey(vehicle) ? vehicle : null,
          vehicle,
          owner,
          Thread.currentThread(),
          GameServer.server,
          GameClient.client,
          !vehicle.isRemovedFromWorld()
              && vehicle.getNetPlayerId() == -1
              && vehicle.isNetPlayerAuthorization(BaseVehicle.Authorization.Server))) return;
      String sound = sound(amount);
      if (sound.isEmpty()) return;
      managed.put(vehicle, managed.get(vehicle) + 1);
      crashes++;
      lastAmount = amount;
      lastFront = front;
      lastX = vehicle.getX();
      lastY = vehicle.getY();
      lastSound = sound;
      if (vehicle.getSquare() == null) {
        failures++;
        lastFailure = "crash_square_unavailable";
        return;
      }
      // Stock crash applies and transmits damage, but SoundManager's world
      // sound implementation returns immediately on a dedicated server.
      GameServer.PlayWorldSoundServer(sound, vehicle.getSquare(), 20f, -1);
      soundRequests++;
    } catch (Throwable error) {
      failures++;
      lastFailure = error.getClass().getSimpleName();
      // A feedback failure must not interrupt the original crash/damage path.
    }
  }

  record Snapshot(
      long crashes,
      long soundRequests,
      long failures,
      float amount,
      boolean front,
      float x,
      float y,
      String sound,
      String failure) {}

  static long crashesFor(BaseVehicle vehicle) {
    return managed.getOrDefault(vehicle, 0L);
  }

  static Snapshot snapshot() {
    return new Snapshot(
        crashes,
        soundRequests,
        failures,
        lastAmount,
        lastFront,
        lastX,
        lastY,
        lastSound,
        lastFailure);
  }

  private ProbeCrashFeedback() {}
}
