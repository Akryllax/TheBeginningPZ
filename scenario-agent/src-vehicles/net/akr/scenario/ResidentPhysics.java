package net.akr.scenario;

/** One game-thread owner of the native world and its exclusive event terrain lease. */
final class ResidentPhysics {
  interface Backend {
    boolean initialized();

    void initialize();

    int bodies();

    void activate(int x, int y, int width);

    void deactivate();
  }

  private final Backend backend;
  private Thread thread;
  private boolean owned, initializationFailed, terrain;
  private String lease;

  ResidentPhysics(Backend backend) {
    this.backend = java.util.Objects.requireNonNull(backend);
  }

  private void checkThread() {
    if (thread == null) thread = Thread.currentThread();
    if (thread != Thread.currentThread())
      throw new IllegalStateException("physics_not_game_thread");
  }

  void acquire(String eventId) {
    checkThread();
    if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("event_id");
    if (lease != null) throw new IllegalStateException("native_lease_unresolved");
    if (initializationFailed) throw new IllegalStateException("native_initialization_unresolved");
    if (!owned) {
      if (backend.initialized())
        throw new IllegalStateException("unowned_native_world_already_initialized");
      initializationFailed = true; // A throwing native initializer may already have side effects.
      backend.initialize();
      if (!backend.initialized())
        throw new IllegalStateException("native_world_initialization_failed");
      owned = true;
      initializationFailed = false;
    }
    if (!backend.initialized() || backend.bodies() != 0 || terrain)
      throw new IllegalStateException("runtime_requires_owned_empty_native_world");
    lease = eventId;
  }

  private void require(String eventId) {
    checkThread();
    if (lease == null || !lease.equals(eventId))
      throw new IllegalStateException("wrong_native_lease");
  }

  void activate(String eventId, int x, int y, int width) {
    require(eventId);
    if (terrain || width < 1 || width > 81)
      throw new IllegalStateException("native_terrain_extent_or_state");
    terrain = true; // Preserve ownership if activation throws after creating native resources.
    backend.activate(x, y, width);
  }

  void release(String eventId) {
    require(eventId);
    if (backend.bodies() != 0) throw new IllegalStateException("native_bodies_remain");
    if (terrain) {
      backend.deactivate();
      terrain = false;
    }
    lease = null; // Never destroy/reinitialize the shared native world between events.
  }

  boolean leased() {
    return lease != null;
  }

  boolean terrain() {
    return terrain;
  }

  boolean clean() {
    return lease == null && !terrain && !initializationFailed;
  }
}
