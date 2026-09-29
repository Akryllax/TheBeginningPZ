package net.akr.scenario;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.*;
import net.akr.scenario.protocol.RuntimeControl.*;

/** Local-only bounded transport. Socket work never calls the engine. */
final class RuntimeSocket implements AutoCloseable {
  static final int MAX_FRAME = 16384;
  private final RuntimeSession runtime;
  private final Path path;
  private final ServerSocketChannel listener;
  private final ThreadPoolExecutor clients;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private volatile boolean closed;

  RuntimeSocket(Path path, RuntimeSession runtime) throws IOException {
    this.path = path;
    this.runtime = runtime;
    if (!path.isAbsolute() || path.toString().length() > 100) throw new IOException("socket_path");
    Files.createDirectories(path.getParent());
    Files.setPosixFilePermissions(path.getParent(), PosixFilePermissions.fromString("rwx------"));
    lockChannel =
        FileChannel.open(
            path.resolveSibling(path.getFileName() + ".lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE);
    lock = lockChannel.tryLock();
    if (lock == null) {
      lockChannel.close();
      throw new IOException("runtime_already_listening");
    }
    listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    try {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        try (SocketChannel test = SocketChannel.open(StandardProtocolFamily.UNIX)) {
          test.connect(UnixDomainSocketAddress.of(path));
          throw new IOException("socket_in_use");
        } catch (ConnectException stale) {
          if (!Boolean.TRUE.equals(
              Files.getAttribute(path, "unix:isOther", LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("not_a_stale_socket");
          Files.delete(path);
        }
      }
      listener.bind(UnixDomainSocketAddress.of(path));
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    } catch (Throwable failure) {
      listener.close();
      lock.release();
      lockChannel.close();
      throw failure;
    }
    clients =
        new ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
              Thread t = new Thread(r, "akr-runtime-client");
              t.setDaemon(true);
              return t;
            });
    Thread acceptor = new Thread(this::accept, "akr-runtime-listener");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  private void accept() {
    while (!closed)
      try {
        SocketChannel client = listener.accept();
        try {
          clients.execute(() -> serve(client));
        } catch (RejectedExecutionException full) {
          client.close();
        }
      } catch (IOException failure) {
        if (!closed) {
          closed = true;
        }
      }
  }

  private void serve(SocketChannel client) {
    try (client) {
      client.configureBlocking(false);
      byte[] data = readFrame(client);
      var input = com.google.protobuf.CodedInputStream.newInstance(data);
      input.setRecursionLimit(8);
      input.setSizeLimit(MAX_FRAME);
      Request request = Request.parseFrom(input);
      if (!input.isAtEnd()) throw new IOException("trailing_frame");
      writeFrame(client, runtime.handle(request).toByteArray());
    } catch (IOException ignored) {
      /* Invalid/disconnected clients have no engine effects. */
    }
  }

  static byte[] readFrame(SocketChannel channel) throws IOException {
    ByteBuffer header = ByteBuffer.allocate(4);
    PlannerTransport.transfer(channel, header, false);
    header.flip();
    int size = header.getInt();
    if (size < 1 || size > MAX_FRAME) throw new IOException("frame_limit");
    ByteBuffer body = ByteBuffer.allocate(size);
    PlannerTransport.transfer(channel, body, false);
    return body.array();
  }

  static void writeFrame(SocketChannel channel, byte[] data) throws IOException {
    if (data.length < 1 || data.length > MAX_FRAME) throw new IOException("frame_limit");
    ByteBuffer frame = ByteBuffer.allocate(data.length + 4).putInt(data.length).put(data);
    frame.flip();
    PlannerTransport.transfer(channel, frame, true);
  }

  public void close() throws IOException {
    closed = true;
    listener.close();
    clients.shutdownNow();
    Files.deleteIfExists(path);
    lock.release();
    lockChannel.close();
  }
}
