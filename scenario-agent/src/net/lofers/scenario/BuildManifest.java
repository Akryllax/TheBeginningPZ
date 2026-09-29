package net.lofers.scenario;

/** Emits the compiled compatibility contract for the packaging script. */
public final class BuildManifest {
  private BuildManifest() {}

  /** Prints the guarded class hashes and native library identity. */
  public static void main(String[] args) {
    if (BuildGuard.HASHES.isEmpty()) {
      throw new IllegalStateException("No guarded game classes");
    }
    System.out.println(
        "native\t" + BuildGuard.SERVER_PHYSICS_LIBRARY + "\t" + BuildGuard.SERVER_PHYSICS_SHA256);
    BuildGuard.HASHES.entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .forEach(entry -> System.out.println("class\t" + entry.getKey() + "\t" + entry.getValue()));
  }
}
