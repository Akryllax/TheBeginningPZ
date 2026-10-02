package net.akr.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Private short-lived socket directories, independent of checkout depth. */
final class RuntimeTestPaths {
  private RuntimeTestPaths() {}

  static Path directory(String prefix) throws IOException {
    Object uid = Files.getAttribute(Path.of("."), "unix:uid");
    Path runtime = Path.of("/run/user", uid.toString());
    if (!Files.isDirectory(runtime)) runtime = Path.of(System.getProperty("java.io.tmpdir"));
    Path directory = Files.createTempDirectory(runtime, "akr-" + prefix).toAbsolutePath();
    if (directory.toString().length() > 75) {
      Files.delete(directory);
      throw new IOException("Test socket directory exceeds safe Unix socket path budget");
    }
    return directory;
  }
}
