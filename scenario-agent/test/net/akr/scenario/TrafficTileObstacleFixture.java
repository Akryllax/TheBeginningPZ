package net.akr.scenario;

import java.util.List;
import java.util.Set;

/** Planning properties only; native collisions still require a game-world test. */
final class TrafficTileObstacleFixture {
  static void run() {
    check(clear(null, null, "street_01_0", null, false, false), "Ordinary road rejected");
    check(
        clear("Floor", null, "floor_fixture", null, false, false),
        "Native floor shape became an obstacle");
    for (String shape :
        List.of("Tree", "Solid", "WallN", "WallS", "WallE", "WallW", "Unrecognized", ""))
      check(
          !clear(shape, null, "walkable_fixture", null, false, false),
          "Walkable physics shape admitted: " + shape);
    for (String mesh : List.of("Base.Pole", "Floor", "Unrecognized", ""))
      check(
          !clear("Floor", mesh, "walkable_fixture", null, false, false),
          "Floor exemption hid a mesh: " + mesh);
    check(!clear(null, null, "walkable_fixture", null, true, false), "StopCar declaration ignored");
    check(
        !clear(null, null, "walkable_fixture", null, false, true),
        "Breakable car obstacle ignored");
    for (String sprite :
        List.of(
            "lighting_outdoor_01_0",
            "recreational_sports_01_19",
            "recreational_sports_01_21",
            "recreational_sports_01_32")) {
      check(!clear(null, null, sprite, null, false, false), "Legacy native column admitted");
      check(
          clear(null, null, sprite, "WallObject", false, false),
          "Wall-mounted column exemption lost");
    }
    check(
        !clear("Tree", null, "lighting_outdoor_01_0", "WallObject", false, false),
        "Mount exemption hid an explicit shape");
    check(
        clear(null, null, null, null, false, false),
        "Absent sprite properties became invented collision");
    terrainMeshes();
    System.out.println(
        "Tile obstacle fixtures passed: walkable poles, meshes, unknown shapes, breakable objects,"
            + " floors and legacy columns");
  }

  private static void terrainMeshes() {
    Set<String> registry = Set.of("Base.Pole", "Other.Fixture");
    check(
        ProbeTerrainMeshGuard.meshProblem(null, registry::contains).isEmpty(),
        "Vanilla primitive required a mesh registry");
    for (String declaration : List.of("Pole", "Base.Pole", "Other.Fixture"))
      check(
          ProbeTerrainMeshGuard.meshProblem(declaration, registry::contains).isEmpty(),
          "Native mesh naming changed");
    for (String declaration : List.of("", " ", "Unknown", "Floor", "Other.Pole", " Pole"))
      check(
          !ProbeTerrainMeshGuard.meshProblem(declaration, registry::contains).isEmpty(),
          "Missing mesh silently omitted: " + declaration);
    var ground = new MeshGround();
    ProbeTerrainMeshGuard.validate(ground, registry::contains);
    check(ground.squareReads == 64, "Upload validation missed surrounding ground tiles");
    // The planned route can be in the first row; a distant uploaded tile
    // still must not silently lose its collider from an empty registry.
    ground.objects[63] = 1;
    ground.objectMesh = "Pole";
    ProbeTerrainMeshGuard.validate(ground, registry::contains);
    rejects(
        () -> ProbeTerrainMeshGuard.validate(ground, Set.<String>of()::contains),
        "Mesh outside the route was ignored");
    ground.aggregateMesh = "Base.Pole";
    ground.objectMesh = "Unknown";
    rejects(
        () -> ProbeTerrainMeshGuard.validate(ground, registry::contains),
        "Known aggregate hid an unknown object mesh");
    ground.objectMesh = "Base.Pole";
    ground.aggregateMesh = "";
    rejects(
        () -> ProbeTerrainMeshGuard.validate(ground, registry::contains),
        "Unnamed aggregate mesh was omitted");
    ground.aggregateMesh = null;
    ground.objects[0] = 33;
    ground.objectReads = 0;
    rejects(
        () -> ProbeTerrainMeshGuard.validate(ground, registry::contains),
        "Unbounded object scan accepted");
    check(ground.objectReads == 0, "Object budget checked after inspecting objects");
  }

  private static final class MeshGround implements ProbeTerrainMeshGuard.GroundMeshes {
    final int[] objects = new int[64];
    int squareReads, objectReads;
    String aggregateMesh, objectMesh;

    public int objectCount(int x, int y) {
      squareReads++;
      return objects[y * 8 + x];
    }

    public String aggregateMesh(int x, int y) {
      return y * 8 + x == 63 ? aggregateMesh : null;
    }

    public String objectMesh(int x, int y, int index) {
      objectReads++;
      return objectMesh;
    }
  }

  private static void rejects(Runnable action, String why) {
    try {
      action.run();
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError(why);
  }

  private static boolean clear(
      String shape,
      String mesh,
      String sprite,
      String moveType,
      boolean stopCar,
      boolean hitByCar) {
    return TrafficTileObstacle.problem(shape, mesh, sprite, moveType, stopCar, hitByCar).isEmpty();
  }

  private static void check(boolean value, String why) {
    if (!value) throw new AssertionError(why);
  }

  public static void main(String[] args) {
    run();
  }
}
