package net.lofers.scenario;

import java.util.function.Predicate;
import zombie.core.physics.Bullet;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoChunk;

/** Refuse chunk uploads which would silently omit a declared custom collider. */
final class ProbeTerrainMeshGuard {
  private ProbeTerrainMeshGuard() {}

  interface GroundMeshes {
    /** -1 denotes a missing square; otherwise the observed object count. */
    int objectCount(int x, int y);

    String aggregateMesh(int x, int y);

    String objectMesh(int x, int y, int index);
  }

  static void validate(IsoChunk chunk) {
    if (chunk == null || !chunk.loaded)
      throw new IllegalStateException("terrain_mesh_chunk_unloaded");
    validate(
        new GroundMeshes() {
          public int objectCount(int x, int y) {
            var square = chunk.getGridSquare(x, y, 0);
            return square == null ? -1 : square.getObjects().size();
          }

          public String aggregateMesh(int x, int y) {
            return declared(chunk.getGridSquare(x, y, 0).getProperties());
          }

          public String objectMesh(int x, int y, int index) {
            return declared(chunk.getGridSquare(x, y, 0).getObjects().get(index).getProperties());
          }
        },
        name -> {
          Integer index = Bullet.physicsShapeNameToIndex.get(name);
          return index != null && index >= 0;
        });
  }

  private static String declared(PropertyContainer properties) {
    if (properties == null || !properties.has("PhysicsMesh")) return null;
    String value = properties.get("PhysicsMesh");
    return value == null ? "" : value;
  }

  static String meshProblem(String declaration, Predicate<String> registered) {
    if (declaration == null) return "";
    if (declaration.isBlank()) return "terrain_mesh_unnamed";
    String name = declaration.indexOf('.') < 0 ? "Base." + declaration : declaration;
    return registered.test(name) ? "" : "terrain_mesh_unregistered:" + name;
  }

  /** Checks the whole uploaded ground layer, including objects outside the planned path. */
  static void validate(GroundMeshes ground, Predicate<String> registered) {
    for (int y = 0; y < 8; y++)
      for (int x = 0; x < 8; x++) {
        int count = ground.objectCount(x, y);
        if (count == -1) continue;
        if (count < 0 || count > 32)
          throw new IllegalStateException("terrain_mesh_object_limit:" + x + "," + y);
        require(ground.aggregateMesh(x, y), registered, x, y);
        for (int i = 0; i < count; i++) require(ground.objectMesh(x, y, i), registered, x, y);
      }
  }

  private static void require(String declaration, Predicate<String> registered, int x, int y) {
    String problem = meshProblem(declaration, registered);
    if (!problem.isEmpty()) throw new IllegalStateException(problem + "@" + x + "," + y);
  }
}
