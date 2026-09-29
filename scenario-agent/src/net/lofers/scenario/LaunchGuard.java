package net.lofers.scenario;

import java.nio.file.Path;

/** The bundled launcher runs a tiny JVM-discovery process before the game JVM. */
final class LaunchGuard {
  static boolean isPinnedDiscovery(String command, String classpath, ClassLoader loader)
      throws Exception {
    if (!"zombie.pzexe".equals(command)
        || classpath == null
        || classpath.indexOf(java.io.File.pathSeparatorChar) >= 0) return false;
    if (!"pzexe.jar".equals(Path.of(classpath).getFileName().toString())) return false;
    try (var in = loader.getResourceAsStream("zombie/pzexe.class")) {
      return in != null
          && BuildGuard.hash(in.readAllBytes())
              .equals("869f0b38cde12c111c451945ad79d55ac49664c5a221b9807adf4df72c946edf");
    }
  }
}
