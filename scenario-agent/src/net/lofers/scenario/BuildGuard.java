package net.lofers.scenario;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Exact B42.20.4 method contract; a different build cannot silently run a scenario. */
final class BuildGuard {
  static final String SERVER_PHYSICS_LIBRARY = "libPZBulletNoOpenGL64.so";
  static final String SERVER_PHYSICS_SHA256 =
      "256304a998a33fa9ba356182cad3ebaad0db14ac36762b806a950d0f08e95d6f";
  static final Map<String, String> HASHES =
      Map.ofEntries(
          Map.entry(
              "zombie/network/fields/character/Prediction",
              "03eb2cfb7c27a08736ddba82d6d9d19a750ad18be78719208eac73b2a6463f00"),
          Map.entry(
              "zombie/pathfind/PathFindBehavior2",
              "a0b584f622beaf33da0da5af0510d6517f92bce08714a23f18a2201d2472a9d4"),
          Map.entry(
              "zombie/core/skinnedmodel/advancedanimation/Anim2DBlendPicker",
              "445b3cab515d642eb67ed10b6ff84cc6c5a1ef4cd249d32ce1de6270ffb39446"),
          Map.entry(
              "zombie/core/skinnedmodel/advancedanimation/Anim2DBlendTriangle",
              "f387287fddce2917d586124c26b7fffe195ea6024f7089fff88e61a0c9656ea4"),
          Map.entry(
              "zombie/core/skinnedmodel/advancedanimation/AnimLayer",
              "dd53bf0235a8ef65edb71049bbadea742dcfbfaa0c8a1da7781a26b3282feb01"),
          Map.entry(
              "zombie/core/skinnedmodel/advancedanimation/AdvancedAnimator",
              "1a068fa6ec030b781de55b05434ee10b17f0a63d790e15e574c51eb7a79eba9b"),
          Map.entry(
              "zombie/network/fields/hit/WeaponHit",
              "9b31d094718786c036bf9ef72fcf20c45efc3a589e4fb5c6d0ef6fc3976faa26"),
          Map.entry(
              "zombie/network/fields/hit/Player",
              "3f5a6621c741770cb47b7f998f50c85eae7a983483320c69b93b47f4371bf304"),
          Map.entry(
              "zombie/network/packets/hit/PlayerHitZombiePacket",
              "2f24c348c03f62dcc2f3ab5b8d7d2baac893603983be52a881a9eb00e1819ce5"),
          Map.entry(
              "zombie/ai/states/SwipeStatePlayer",
              "7aaa7c60d9bcaa478f337b2b8577ed729c3b33086db8bab12cfad7b91f1ac809"),
          Map.entry(
              "zombie/CombatManager",
              "42f4ecabf2933bb780afca97f07c11bdeb0b735c302f7835743367eb0c3e5080"),
          Map.entry(
              "zombie/iso/objects/IsoDeadBody",
              "8d6bc16e9e2908381be94cf3c3348e7f869d4c4dbc091b69f281e21c9c725eea"),
          Map.entry(
              "zombie/characters/SurvivorDesc",
              "603cbdacbb5ba007a09d2abd35ca29a571d3ff39d043bdbe2cb81a252f20ee0a"),
          Map.entry(
              "zombie/iso/IsoMovingObject",
              "1ba014bed69a86c2397983b34286da4cb130684871cda86233efe18e9665df4f"),
          Map.entry(
              "zombie/characters/BodyDamage/Fitness",
              "101caeeac9b899ee21bb87951dd57d92142f72fed2e47f96090fd2695c903d17"),
          Map.entry(
              "zombie/characters/BodyDamage/BodyDamage",
              "52fc89141d68ad610743520150c2b546a83701f2a019a393d76938b01af28837"),
          Map.entry(
              "zombie/characters/BodyDamage/BodyPart",
              "621f81a768ac36388a811057ab2e08c194c913b93e5d5934f7f1e457bee9959a"),
          Map.entry(
              "zombie/network/fields/character/PlayerVariables$NetworkPlayerVariable",
              "d757f462003e3643f6d2d88a507162cc6c64084ffc1202c17baba7748524f286"),
          Map.entry(
              "zombie/network/fields/character/PlayerVariables$NetworkPlayerVariableIDs",
              "aedfba13f530abf1cfa4b839368d3f853f59c26ea17cae5e19e0ec19d3ced0bd"),
          Map.entry(
              "zombie/network/fields/character/PlayerVariables",
              "8d95bb7a187d3d229dbb8fd8f6b281b84501052ea6af1c746c0f169c464288e4"),
          Map.entry(
              "zombie/characters/NetworkPlayerAI",
              "de3402d7dde43c238dc0a7479926d5b3fc86e766fab8066f5deac4cee397e183"),
          Map.entry(
              "zombie/characters/action/ActionContext",
              "54aeea6779da4c40e594dd3f7d515564099fcc66ead8fa09aadc716eecc44253"),
          Map.entry(
              "zombie/core/skinnedmodel/visual/HumanVisual",
              "bb234b6efe9ac225125e8a7c074d8a9797acbb8d70b97f39cff7b4d547d6e0b5"),
          Map.entry(
              "zombie/characters/IsoPlayer",
              "37bc21ec7f3b8882e7da72b2829a9ac57747f0629e74a86a07c2fdbe913005e9"),
          Map.entry(
              "zombie/core/skinnedmodel/animation/AnimationPlayer",
              "3edadcd3a6bfff86239d38bc663c2ed0dd8fc9566ec375a6eef1a949d6346b49"),
          Map.entry(
              "zombie/pathfind/Path",
              "193619ec855656c2516fa8dd0fe9157989d00695ae2edf6898fed4acc17a0b3e"),
          Map.entry(
              "zombie/pathfind/PathNode",
              "6c97b914a093d76efc0129f181fc2e81dbacd16ee59fb37e13fb16886a759e2b"),
          Map.entry(
              "zombie/pathfind/PolygonalMap2",
              "15e8f1d6a9bcbfc3f11262350e08b58cd6efb05a568a36d4cd3d8c7c147a5bb6"),
          Map.entry(
              "zombie/pathfind/nativeCode/PathfindNative",
              "7fbc06c9757083c318dcc136cfcb31c2ad4941d15926c9de6f7792dcef3d6022"),
          Map.entry(
              "zombie/MovingObjectUpdateScheduler",
              "b6383937d72e5056e2c8ca4e9af7ad0b390a41a2a978075ffecde2e74c3dede6"),
          Map.entry(
              "zombie/characters/IsoGameCharacter",
              "3844a8542a1af7ef8c5d6adfd8136807bfb42e1721860348e4ac7b8b41f2d85a"),
          Map.entry(
              "zombie/SoundManager",
              "42753adceb47d3157402eabc1efd871891845ac8bc66aa9be6ef7ff7d81d2c77"),
          Map.entry(
              "zombie/network/GameServer",
              "f6f584c60026d685fdc12012fe5b3e498f599ac02d0fb5b56fa5ae85218c8566"),
          Map.entry(
              "zombie/network/packets/sound/PlayWorldSoundPacket",
              "653a0ceb2325ed167d861f85d98e8e316b61a80178196743360d730dc0a2ca33"),
          Map.entry(
              "zombie/vehicles/VehiclePart",
              "27febf9f1cbc8c057bf34e400c743f1e19e70638f6553b45f0140d086e329311"),
          Map.entry(
              "zombie/network/RCONServer",
              "c31a83c6868d6c88da96db66db10a3d3414ae39947b13e3d800afbb2c034c645"),
          Map.entry(
              "zombie/Lua/Event",
              "cf4b0ba953b8f965fbcacb73ebb5d1a173dc3ed140656e15b90ad932eff0e952"),
          Map.entry(
              "zombie/Lua/LuaManager",
              "16ea31579ae9f57725b2eca788baa135c8629b29e4cc9bafc4fb096f7742e60d"),
          Map.entry(
              "zombie/iso/IsoWorld",
              "b47ca376477c5676ebcc150bc7eaa79eeeed12f3821537a8e3020a6676f3fcae"),
          Map.entry(
              "zombie/VirtualZombieManager",
              "fc56505290de3fe532478c43f20951febe3bcfd1e63cfe89cfb4fa377e449463"),
          Map.entry(
              "zombie/core/physics/CarController",
              "5b660138ba8f2a575502d641318371ecf9e9b3555dc0815241dee45c5f30410d"),
          Map.entry(
              "zombie/world/moddata/GlobalModData",
              "61b27632d28a63f92667a727d40614f6ec1e22640a64a65a93508427bcf4d47d"),
          Map.entry(
              "se/krka/kahlua/vm/KahluaTable",
              "398e2f6df2108991fdbf3290e2725236eb71e4d070039a114a3cc1cf5a14af18"),
          Map.entry(
              "zombie/characters/IsoZombie",
              "b06e8a05773c11166dd97b356fb58aa664d40eb67d5f1d3c35d9f0d02b21b86a"),
          Map.entry(
              "zombie/vehicles/BaseVehicle",
              "9c44f6a428c2fbefdd4e6c3d000c7d41dc961c43d4118605483946979daad24a"),
          Map.entry(
              "zombie/popman/NetworkZombieManager",
              "27af8b12cc9ab2af778cbce03ed546168c69976e63a905b16279445234d89ad8"),
          Map.entry(
              "zombie/popman/NetworkZombiePacker",
              "5b202038b360877e8a308265e6bf7ce720e34d0c3670adfb3a7c5dbddaf5197e"),
          Map.entry(
              "zombie/core/physics/WorldSimulation",
              "f41d89d8c1fafd21f35b4ecd3ac3917fc58a6b9f5bbbe26404ef5c035238c09a"),
          Map.entry(
              "zombie/core/physics/Bullet",
              "918622eddad21725fd321dd0f6ec6542059fcbbb0c5c08c76eba6c68fa4e6234"),
          Map.entry(
              "zombie/vehicles/VehicleManager",
              "4e05b6c225b17c06aed0bf7c51356375162117399dafd7ac325ca0aa045d517f"),
          Map.entry(
              "zombie/vehicles/BaseVehicle$ServerVehicleState",
              "d3e2ea2c63db14312c99ceeef46b4b3fbeba5324384c3841a15e190c6eb4c0fe"),
          Map.entry(
              "zombie/scripting/objects/VehicleScript",
              "45acc776dcd9aed2d625ecbb8213dc4e169137d4f17c933c2ce9ede288565d8c"),
          Map.entry(
              "zombie/scripting/ScriptManager",
              "807ee76216ca7643f6a24ff69dde5c473bbe37c87ca57195dae813f7cdb45d7f"),
          Map.entry(
              "zombie/network/ServerMap",
              "5a3e449c6b7f3ea237939502c5b43ab0dd490a3752fd675611aade7ae79c548d"),
          Map.entry(
              "zombie/vehicles/VehiclesDB2",
              "f908628f3a94a018cc4666ef14bdb01326eeb90ca27f55d7b744d215a7a9f9ba"),
          Map.entry(
              "zombie/iso/IsoChunk",
              "68431ace471b30c842ff7c2a6e706d8ba48d7a84ae07f876484153c0d62a794b"),
          Map.entry(
              "zombie/iso/IsoGridSquare",
              "cf5ef9005829d258f6ef28394a9f514350fa8fa1ea5f6209cc7836a656f0b0eb"),
          Map.entry(
              "zombie/iso/IsoObject",
              "aa11e4c764ea17a731f2cccc2d968477aa40b488ca348f1b22699b98874044b6"),
          Map.entry(
              "zombie/core/properties/PropertyContainer",
              "bc236f8e9e4e0da8a86b0d1fd19ad8d44a040fe4f67d1baac77401b3b148bd77"),
          Map.entry(
              "zombie/scripting/objects/VehicleScript$Wheel",
              "4892e3df3443aea2ecec0ffcc6a2383820cc31a859b455c8ca3d2f99369cda3f"),
          Map.entry(
              "zombie/network/packets/vehicle/VehicleUpdatePacket",
              "2ee2b9be9d06a502a7d5169026d37c5d8174e0a60228c9ca9d0379a7686aa995"),
          Map.entry(
              "zombie/network/packets/vehicle/VehicleFullUpdatePacket",
              "87d27cd4af602a6f6c2424a421de3167bec28a8c68179515a19f16ba0227989e"),
          Map.entry(
              "zombie/vehicles/VehicleInterpolationData",
              "9c9b6518838495b8740dcc105bc007682abb5e9773c0830a4f00aa67b2fc743b"));

  static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  static void verify(ClassLoader loader) throws Exception {
    if (Runtime.version().feature() != 25)
      throw new IllegalStateException("Scenario requires Java 25");
    for (var e : HASHES.entrySet())
      try (var in = loader.getResourceAsStream(e.getKey() + ".class")) {
        if (in == null || !e.getValue().equals(hash(in.readAllBytes())))
          throw new IllegalStateException("Unsupported game class: " + e.getKey());
      }
  }

  /**
   * The native terrain and collision-filter behavior is an ABI contract independent of class
   * hashes.
   */
  static Path verifyServerPhysics(Instrumentation instrumentation) throws Exception {
    if (Runtime.version().feature() != 25)
      throw new IllegalStateException("Native lookup guard requires Java 25");
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "");
    if (!os.equals("linux") || !Set.of("amd64", "x86_64").contains(arch))
      throw new IllegalStateException(
          "Server vehicle physics requires the pinned Linux x86-64 native library");
    if ("1".equals(System.getProperty("zomboid.debuglibs.bullet")))
      throw new IllegalStateException(
          "Debug Bullet library is not verified for server vehicle physics");
    // NativeLibraries reads StaticProperty's bootstrap snapshot. The mutable
    // System properties can already differ when an earlier agent has run.
    // Export only this package to our module; no deep reflection or game
    // class initialization is needed to inspect the JVM's actual paths.
    Module base = Object.class.getModule(), ours = BuildGuard.class.getModule();
    if (!base.isExported("jdk.internal.util", ours)) {
      if (instrumentation == null)
        throw new IllegalStateException("Native startup-path inspection requires instrumentation");
      instrumentation.redefineModule(
          base, Set.of(), Map.of("jdk.internal.util", Set.of(ours)), Map.of(), Set.of(), Map.of());
    }
    Class<?> startup = Class.forName("jdk.internal.util.StaticProperty", false, null);
    String bootPath = (String) startup.getMethod("sunBootLibraryPath").invoke(null);
    String gamePath = (String) startup.getMethod("javaLibraryPath").invoke(null);
    return verifyNativeSearchPath(bootPath, gamePath);
  }

  static Path verifyNativeSearchPath(String bootPath, String gamePath) throws Exception {
    // The shipped game uses System.loadLibrary. Refuse a boot-library shadow
    // and check the first existing game-path candidate, not a later good copy.
    for (String entry : bootPath.split(File.pathSeparator, -1)) {
      Path candidate = Path.of(entry).resolve(SERVER_PHYSICS_LIBRARY);
      if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
        throw new IllegalStateException("Unexpected Bullet library on the JVM boot search path");
    }
    for (String entry : gamePath.split(File.pathSeparator, -1)) {
      Path candidate = Path.of(entry).resolve(SERVER_PHYSICS_LIBRARY);
      if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
      if (!Files.isRegularFile(candidate))
        throw new IllegalStateException("Invalid server physics library candidate");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(candidate)) {
        byte[] block = new byte[65536];
        int count;
        while ((count = in.read(block)) != -1) digest.update(block, 0, count);
      }
      if (!SERVER_PHYSICS_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
        throw new IllegalStateException("Unsupported server physics library: " + candidate);
      return candidate.toRealPath();
    }
    throw new IllegalStateException("Pinned server physics library missing from java.library.path");
  }
}
