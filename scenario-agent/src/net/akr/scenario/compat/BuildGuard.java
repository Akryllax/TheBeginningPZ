package net.akr.scenario.compat;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Exact reviewed test-build contract; a different build cannot silently run a scenario. */
public final class BuildGuard {
  public static final String SERVER_PHYSICS_LIBRARY = "libPZBulletNoOpenGL64.so";
  public static final String SERVER_PHYSICS_SHA256 = BuildProfile.PHYSICS_SHA256;
  public static final Map<String, String> HASHES = BuildProfile.CLASS_HASHES;

  /** Hash immutable class or dependency bytes with SHA-256. */
  public static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  /**
   * Reject an engine build whose guarded classes differ from the reviewed test-build bytes.
   *
   * @param loader loader that will resolve the game's classes
   * @throws Exception if Java or any guarded class differs
   */
  public static void verify(ClassLoader loader) throws Exception {
    if (Runtime.version().feature() != 25)
      throw new IllegalStateException("Scenario requires Java 25");
    var resource = loader.getResource("zombie/core/Core.class");
    if (resource == null
        || !(resource.openConnection() instanceof java.net.JarURLConnection connection)
        || !connection.getJarFileURL().getProtocol().equals("file"))
      throw new IllegalStateException("Expected a local game archive");
    var digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(Path.of(connection.getJarFileURL().toURI()));
        var hashed = new java.security.DigestInputStream(input, digest)) {
      hashed.transferTo(OutputStream.nullOutputStream());
    }
    if (!BuildProfile.JAR_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
      throw new IllegalStateException("Unsupported game archive");
    for (var e : HASHES.entrySet())
      try (var in = loader.getResourceAsStream(e.getKey() + ".class")) {
        if (in == null || !e.getValue().equals(hash(in.readAllBytes())))
          throw new IllegalStateException("Unsupported game class: " + e.getKey());
      }
  }

  /**
   * Verify the native terrain and collision ABI independently of guarded Java classes.
   *
   * @param instrumentation JVM access used to inspect startup native library search paths
   * @return real path of the pinned server physics library
   * @throws Exception if the platform or native library does not match
   */
  public static Path verifyServerPhysics(Instrumentation instrumentation) throws Exception {
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

  /**
   * Reject a shadowed or mismatched native library using the JVM's startup search paths.
   *
   * @param bootPath JVM bootstrap native library path
   * @param gamePath game native library path
   * @return real path of the first matching game library
   * @throws Exception if a shadow or mismatched library is found
   */
  public static Path verifyNativeSearchPath(String bootPath, String gamePath) throws Exception {
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
