package net.lofers.scenario;

import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.*;
import net.lofers.scenario.protocol.NpcControl.*;

/** Real framed IPC: a lost ack must retry the identical terminal outcome after reconnect. */
final class PlannerTransportFixture {
  static void run() throws Exception {
    Path folder = Files.createTempDirectory(Path.of(".tooling"), "pt-").toAbsolutePath(),
        socket = folder.resolve("p.sock");
    var journal = new ResidentReceipts();
    var key = new ResidentReceipts.Key("resident", 2, 3, "walk-1");
    journal.finish(key, "completed", "arrived");
    var transport = new PlannerTransport(socket, "world", "epoch", "registry", journal);
    Thread client = new Thread(transport, "planner-retry-fixture");
    var hold = new CountDownLatch(1);
    var accepted = new CompletableFuture<Void>();
    try (var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      listener.bind(UnixDomainSocketAddress.of(socket));
      Thread server =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      Receipt first = null;
                      for (int connection = 0; connection < 2; connection++)
                        try (var channel = listener.accept()) {
                          channel.configureBlocking(false);
                          var hello = PlannerTransport.read(channel);
                          PlannerTransport.write(
                              channel, hello.toBuilder().setHello(hello.getHello()).build());
                          var request = PlannerTransport.read(channel);
                          if (!request.hasReceipt() || request.getRequestId() != 1)
                            throw new AssertionError("fresh session receipt identity");
                          if (connection == 0) {
                            first = request.getReceipt();
                            continue;
                          }
                          if (!first.equals(request.getReceipt()) || journal.size() != 1)
                            throw new AssertionError("lost ack discarded outcome");
                          PlannerTransport.write(
                              channel,
                              request.toBuilder()
                                  .setStatus(
                                      Status.newBuilder()
                                          .setHealth("ready")
                                          .setDetail("receipt_observed"))
                                  .build());
                          accepted.complete(null);
                          hold.await(3, TimeUnit.SECONDS);
                        }
                    } catch (Throwable failure) {
                      accepted.completeExceptionally(failure);
                    }
                  });
      client.start();
      accepted.get(8, TimeUnit.SECONDS);
      long deadline = System.nanoTime() + 1_000_000_000L;
      while (journal.size() != 0 && System.nanoTime() < deadline) Thread.sleep(2);
      if (journal.size() != 0) throw new AssertionError("ack did not release journal slot");
      client.interrupt();
      hold.countDown();
      client.join(3000);
      server.join(3000);
      if (client.isAlive() || server.isAlive())
        throw new AssertionError("transport fixture leaked threads");
    } finally {
      client.interrupt();
      hold.countDown();
      client.join(3000);
      Files.deleteIfExists(socket);
      Files.deleteIfExists(folder);
    }
    System.out.println(
        "Planner transport: lost acknowledgment, reconnect, identical receipt retry and"
            + " acknowledged release passed");
  }
}
