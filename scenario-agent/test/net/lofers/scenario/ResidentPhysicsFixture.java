package net.lofers.scenario;

import java.util.concurrent.*;

/** Ownership/failure injection with a detached backend, not a native collision test. */
final class ResidentPhysicsFixture {
  private static final class Backend implements ResidentPhysics.Backend {
    boolean initialized, terrain, failInitialize, failActivate, failDeactivate;
    int bodies, initializations, activations, deactivations;

    public boolean initialized() {
      return initialized;
    }

    public void initialize() {
      initializations++;
      initialized = true;
      if (failInitialize) throw new IllegalStateException("partial_initialize");
    }

    public int bodies() {
      return bodies;
    }

    public void activate(int x, int y, int width) {
      activations++;
      terrain = true;
      if (failActivate) throw new IllegalStateException("partial_activate");
    }

    public void deactivate() {
      deactivations++;
      if (failDeactivate) throw new IllegalStateException("partial_deactivate");
      terrain = false;
    }
  }

  static void check(boolean value, String reason) {
    ScenarioFixture.check(value, reason);
  }

  static void run() throws Exception {
    Backend backend = new Backend();
    ResidentPhysics owner = new ResidentPhysics(backend);
    for (int i = 0; i < 100; i++) {
      String event = "event-" + i;
      owner.acquire(event);
      owner.activate(event, 100 + i, 200, 13);
      backend.bodies = 1;
      ScenarioFixture.rejects(() -> owner.release(event), "Live native body lost terrain lease");
      ScenarioFixture.rejects(
          () -> owner.acquire("other"), "Competing event acquired native world");
      ScenarioFixture.rejects(() -> owner.release("other"), "Unrelated event released terrain");
      backend.bodies = 0;
      owner.release(event);
      check(owner.clean() && !backend.terrain, "Terrain survived verified release");
    }
    check(
        backend.initializations == 1 && backend.activations == 100 && backend.deactivations == 100,
        "World recreated between events");
    try (var worker = Executors.newSingleThreadExecutor()) {
      check(
          worker
              .submit(
                  () -> {
                    try {
                      owner.acquire("off-thread");
                      return false;
                    } catch (IllegalStateException expected) {
                      return true;
                    }
                  })
              .get(2, TimeUnit.SECONDS),
          "Off-thread physics accepted");
    }
    backend.failActivate = true;
    owner.acquire("partial");
    ScenarioFixture.rejects(() -> owner.activate("partial", 1, 2, 3), "Activation fault missing");
    check(owner.leased() && owner.terrain(), "Partial activation ownership discarded");
    backend.failDeactivate = true;
    ScenarioFixture.rejects(() -> owner.release("partial"), "Deactivation fault missing");
    check(owner.leased() && owner.terrain(), "Failed cleanup released capacity");
    backend.failDeactivate = false;
    owner.release("partial");
    check(owner.clean(), "Cleanup recovery failed");
    Backend unknown = new Backend();
    unknown.initialized = true;
    ResidentPhysics other = new ResidentPhysics(unknown);
    ScenarioFixture.rejects(() -> other.acquire("event"), "Unowned native world adopted");
    check(unknown.initializations == 0, "Foreign world reinitialized");
    Backend broken = new Backend();
    broken.failInitialize = true;
    ResidentPhysics poisoned = new ResidentPhysics(broken);
    ScenarioFixture.rejects(() -> poisoned.acquire("event"), "Initialization fault missing");
    check(!poisoned.clean(), "Partial initialization reported clean");
    broken.failInitialize = false;
    ScenarioFixture.rejects(
        () -> poisoned.acquire("retry"), "Uncertain initialization silently retried");
    check(broken.initializations == 1, "Native initializer ran twice after partial failure");
    System.out.println(
        "Resident physics fixtures passed: single world across 100 detached leases, exact"
            + " ownership, thread affinity, partial native failures and cleanup recovery; no native"
            + " soak claim");
  }
}
