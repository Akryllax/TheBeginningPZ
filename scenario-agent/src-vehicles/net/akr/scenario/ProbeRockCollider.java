package net.akr.scenario;

import zombie.core.physics.Bullet;
import zombie.iso.IsoChunk;
import zombie.network.GameServer;

/** Original convex rock geometry, scoped to one tagged disposable experiment tile. */
public final class ProbeRockCollider {
  static final int SOLID = ordinal("Solid"),
      FLOOR = ordinal("Floor"),
      FIRST_MESH = ordinal("FIRST_MESH");

  private static int ordinal(String name) {
    try {
      for (Object value : Class.forName("zombie.iso.IsoChunk$PhysicsShapes").getEnumConstants())
        if (((Enum<?>) value).name().equals(name)) return ((Enum<?>) value).ordinal();
    } catch (ClassNotFoundException e) {
      throw new ExceptionInInitializerError(e);
    }
    throw new IllegalStateException("Missing guarded terrain shape " + name);
  }

  private static final String NAME = "AKRProbe.SlopedRock";
  private static ProbeImpactTarget target;
  private static Thread owner;
  private static int mesh = -1;
  private static long uploads;
  private static String failure = "";

  private ProbeRockCollider() {}

  static float[] points() {
    return new float[] {
      -.48f, 0, -.48f, .48f, 0, -.48f, .48f, 0, .48f, -.48f, 0, .48f, -.20f, .60f, -.10f, .20f,
      .60f, -.10f, .20f, .60f, .20f, -.20f, .60f, .20f
    };
  }

  static void activate(ProbeImpactTarget next) {
    if (next == null || !next.slopedRock()) return;
    if (!GameServer.server
        || !GameServer.serverName.matches("AKRVehicleProbe_[A-Za-z0-9_-]{1,64}")
        || !Bullet.isWorldInit())
      throw new IllegalStateException("rock_requires_disposable_native_world");
    if (target != null) throw new IllegalStateException("rock_collider_already_active");
    Integer registered = Bullet.physicsShapeNameToIndex.get(NAME);
    if (registered == null) {
      int index =
          Bullet.physicsShapeNameToIndex.values().stream()
                  .mapToInt(Integer::intValue)
                  .max()
                  .orElse(-1)
              + 1;
      if (index < 0 || index + FIRST_MESH >= 254)
        throw new IllegalStateException("rock_mesh_index_capacity");
      Bullet.definePhysicsMesh(index, false, points());
      Bullet.physicsShapeNameToIndex.put(NAME, index);
      registered = index;
    }
    mesh = FIRST_MESH + registered;
    owner = Thread.currentThread();
    target = next;
    uploads = 0;
    failure = "";
  }

  static void deactivate() {
    target = null;
    owner = null;
    failure = "";
  }

  static String failure() {
    return failure;
  }

  static long uploads() {
    return uploads;
  }

  /** Only replace an otherwise ordinary solid+floor square; never erase another shape. */
  static boolean replace(int[] shapes, int replacement) {
    if (shapes == null || shapes.length != 4) return false;
    int solid = -1;
    for (int i = 0; i < shapes.length; i++) {
      if (shapes[i] == SOLID) {
        if (solid != -1) return false;
        solid = i;
      } else if (shapes[i] != -1 && shapes[i] != FLOOR) return false;
    }
    if (solid < 0) return false;
    shapes[solid] = replacement;
    return true;
  }

  /** Called after the stock calculator; installed engine files remain untouched. */
  public static void apply(IsoChunk chunk, int x, int y, int z, int[] shapes) {
    var current = target;
    if (current == null
        || z != 0
        || chunk == null
        || !current.tile(chunk.wx * 8 + x, chunk.wy * 8 + y)) return;
    if (!GameServer.server || Thread.currentThread() != owner) {
      failure = "rock_upload_wrong_thread_or_role";
      return;
    }
    var sq = chunk.getGridSquare(x, y, z);
    if (sq == null) return;
    int found = 0;
    var objects = sq.getObjects();
    for (int i = 0; i < objects.size(); i++) if (current.matches(objects.get(i))) found++;
    if (found == 0) return; // Removal restores the ordinary floor on its next update.
    if (found != 1 || !replace(shapes, mesh)) {
      failure = "rock_upload_unexpected_tile_shapes";
      return;
    }
    uploads++;
  }
}
