package net.lofers.scenario;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Exact B42.20.4 method contract; a different build cannot silently run a scenario. */
final class BuildGuard {
    static final Map<String,String> HASHES=Map.ofEntries(
        Map.entry("zombie/network/RCONServer","c31a83c6868d6c88da96db66db10a3d3414ae39947b13e3d800afbb2c034c645"),
        Map.entry("zombie/Lua/Event","cf4b0ba953b8f965fbcacb73ebb5d1a173dc3ed140656e15b90ad932eff0e952"),
        Map.entry("zombie/Lua/LuaManager","16ea31579ae9f57725b2eca788baa135c8629b29e4cc9bafc4fb096f7742e60d"),
        Map.entry("zombie/iso/IsoWorld","b47ca376477c5676ebcc150bc7eaa79eeeed12f3821537a8e3020a6676f3fcae"),
        Map.entry("zombie/VirtualZombieManager","fc56505290de3fe532478c43f20951febe3bcfd1e63cfe89cfb4fa377e449463"),
        Map.entry("zombie/core/physics/CarController","5b660138ba8f2a575502d641318371ecf9e9b3555dc0815241dee45c5f30410d"),
        Map.entry("zombie/world/moddata/GlobalModData","61b27632d28a63f92667a727d40614f6ec1e22640a64a65a93508427bcf4d47d"),
        Map.entry("se/krka/kahlua/vm/KahluaTable","398e2f6df2108991fdbf3290e2725236eb71e4d070039a114a3cc1cf5a14af18"),
        Map.entry("zombie/characters/IsoZombie","b06e8a05773c11166dd97b356fb58aa664d40eb67d5f1d3c35d9f0d02b21b86a"),
        Map.entry("zombie/vehicles/BaseVehicle","9c44f6a428c2fbefdd4e6c3d000c7d41dc961c43d4118605483946979daad24a"),
        Map.entry("zombie/popman/NetworkZombieManager","27af8b12cc9ab2af778cbce03ed546168c69976e63a905b16279445234d89ad8"),
        Map.entry("zombie/popman/NetworkZombiePacker","5b202038b360877e8a308265e6bf7ce720e34d0c3670adfb3a7c5dbddaf5197e"),
        Map.entry("zombie/core/physics/WorldSimulation","f41d89d8c1fafd21f35b4ecd3ac3917fc58a6b9f5bbbe26404ef5c035238c09a"),
        Map.entry("zombie/core/physics/Bullet","918622eddad21725fd321dd0f6ec6542059fcbbb0c5c08c76eba6c68fa4e6234"),
        Map.entry("zombie/vehicles/VehicleManager","4e05b6c225b17c06aed0bf7c51356375162117399dafd7ac325ca0aa045d517f"),
        Map.entry("zombie/vehicles/BaseVehicle$ServerVehicleState","d3e2ea2c63db14312c99ceeef46b4b3fbeba5324384c3841a15e190c6eb4c0fe"),
        Map.entry("zombie/scripting/objects/VehicleScript","45acc776dcd9aed2d625ecbb8213dc4e169137d4f17c933c2ce9ede288565d8c"),
        Map.entry("zombie/scripting/ScriptManager","807ee76216ca7643f6a24ff69dde5c473bbe37c87ca57195dae813f7cdb45d7f"),
        Map.entry("zombie/network/ServerMap","5a3e449c6b7f3ea237939502c5b43ab0dd490a3752fd675611aade7ae79c548d"),
        Map.entry("zombie/vehicles/VehiclesDB2","f908628f3a94a018cc4666ef14bdb01326eeb90ca27f55d7b744d215a7a9f9ba"),
        Map.entry("zombie/iso/IsoChunk","68431ace471b30c842ff7c2a6e706d8ba48d7a84ae07f876484153c0d62a794b"),
        Map.entry("zombie/iso/IsoGridSquare","cf5ef9005829d258f6ef28394a9f514350fa8fa1ea5f6209cc7836a656f0b0eb"),
        Map.entry("zombie/network/packets/vehicle/VehicleUpdatePacket","2ee2b9be9d06a502a7d5169026d37c5d8174e0a60228c9ca9d0379a7686aa995"),
        Map.entry("zombie/network/packets/vehicle/VehicleFullUpdatePacket","87d27cd4af602a6f6c2424a421de3167bec28a8c68179515a19f16ba0227989e"),
        Map.entry("zombie/vehicles/VehicleInterpolationData","9c9b6518838495b8740dcc105bc007682abb5e9773c0830a4f00aa67b2fc743b"));
    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static void verify(ClassLoader loader) throws Exception {
        if(Runtime.version().feature()!=25) throw new IllegalStateException("Scenario requires Java 25");
        for(var e:HASHES.entrySet()) try(var in=loader.getResourceAsStream(e.getKey()+".class")) {
            if(in==null || !e.getValue().equals(hash(in.readAllBytes())))
                throw new IllegalStateException("Unsupported game class: "+e.getKey());
        }
    }
}
