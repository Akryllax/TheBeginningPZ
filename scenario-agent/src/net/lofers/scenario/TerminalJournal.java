package net.lofers.scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Durable, idempotent tombstones. The writer receives strings only, never game objects. A
 * failed/uncertain write is terminal for that request: ownership must remain retained.
 */
final class TerminalJournal implements AutoCloseable {
  enum State {
    PENDING,
    DURABLE,
    UNRESOLVED
  }

  record Receipt(String world, CivilianPool.Token token, String corpse, String inventory) {
    Receipt {
      Objects.requireNonNull(token);
      for (String s : List.of(world, token.epoch(), token.resident(), corpse, inventory))
        if (s.isBlank() || s.length() > 4096 || s.contains("\n") || s.contains("\r"))
          throw new IllegalArgumentException("terminal_record");
    }

    String payload() {
      return String.join(
              "\n",
              "AKR-terminal-1",
              world,
              token.epoch(),
              Integer.toString(token.slot()),
              Long.toString(token.generation()),
              token.resident(),
              Long.toString(token.residentGeneration()),
              corpse,
              inventory)
          + "\n";
    }

    String key() {
      return digest(world + "\n" + token.resident() + "\n" + token.residentGeneration());
    }
  }

  interface Writer {
    void write(String key, String payload) throws Exception;
  }

  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "resident-terminal-writer");
            t.setDaemon(true);
            return t;
          });
  private final Map<String, Entry> entries = new HashMap<>();

  private record Entry(String payload, CompletableFuture<Void> future) {}

  private final Writer writer;
  private CompletableFuture<Map<String, String>> startup =
      CompletableFuture.completedFuture(Map.of());

  TerminalJournal(Path directory) {
    this((key, payload) -> persist(directory, key, payload));
    startup =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return unresolvedOnRestart(directory);
              } catch (IOException e) {
                throw new CompletionException(e);
              }
            },
            worker);
  }

  boolean ready() {
    return startup.isDone();
  }

  boolean restartUnresolved() {
    return startup.isCompletedExceptionally() || startup.isDone() && !startup.join().isEmpty();
  }

  TerminalJournal(Writer writer) {
    this.writer = writer;
  }

  synchronized State submit(Receipt receipt) {
    if (!ready()) return State.PENDING;
    if (restartUnresolved()) return State.UNRESOLVED;
    String key = receipt.key(), payload = receipt.payload();
    Entry old = entries.get(key);
    if (old != null) {
      if (!old.payload.equals(payload)) throw new IllegalStateException("terminal_record_conflict");
      return state(old);
    }
    if (entries.size() >= 4) return State.PENDING;
    var future = new CompletableFuture<Void>();
    entries.put(key, new Entry(payload, future));
    worker.execute(
        () -> {
          try {
            writer.write(key, payload);
            future.complete(null);
          } catch (Exception e) {
            future.completeExceptionally(e);
          }
        });
    return State.PENDING;
  }

  synchronized void forget(Receipt receipt) {
    Entry e = entries.get(receipt.key());
    if (e == null || state(e) != State.DURABLE || !e.payload.equals(receipt.payload()))
      throw new IllegalStateException("terminal_not_durable");
    entries.remove(receipt.key());
  }

  private static State state(Entry e) {
    return !e.future.isDone()
        ? State.PENDING
        : e.future.isCompletedExceptionally() ? State.UNRESOLVED : State.DURABLE;
  }

  static String digest(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void persist(Path directory, String key, String payload) throws IOException {
    Files.createDirectories(directory);
    Path target = directory.resolve(key + ".terminal");
    if (Files.exists(target)) {
      if (!Files.readString(target).equals(payload))
        throw new IOException("terminal_record_conflict");
      return;
    }
    Path temp = Files.createTempFile(directory, "terminal-", ".pending");
    try {
      try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(payload);
        while (bytes.hasRemaining()) channel.write(bytes);
        channel.force(true);
      }
      // A single writer owns this directory. Atomic move is required, never a partial fallback.
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      try (var dir = FileChannel.open(directory, StandardOpenOption.READ)) {
        dir.force(true);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * Restart never manufactures bodies from receipts. All tombstones require world reconciliation.
   */
  static Map<String, String> unresolvedOnRestart(Path directory) throws IOException {
    if (!Files.exists(directory)) return Map.of();
    var result = new LinkedHashMap<String, String>();
    try (var paths = Files.newDirectoryStream(directory, "*.terminal")) {
      for (Path p : paths) {
        if (result.size() >= 4096) throw new IOException("terminal_reconcile_budget");
        result.put(p.getFileName().toString(), "UNRESOLVED:world_save_reconciliation");
      }
    }
    return Map.copyOf(result);
  }

  public void close() {
    worker.shutdown();
  }
}
