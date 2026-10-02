package net.akr.scenario;

import java.io.*;
import java.lang.classfile.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import net.akr.scenario.bridge.PrimitiveCopy;
import net.akr.scenario.bridge.ProtocolCodec;
import net.akr.scenario.compat.BuildGuard;
import net.akr.scenario.compat.LaunchGuard;
import net.akr.scenario.protocol.NpcControl.*;
import se.krka.kahlua.j2se.KahluaTableImpl;
import se.krka.kahlua.vm.*;

/** Independent protocol/bytecode fixtures; this does not replace two-client tests. */
public final class ScenarioFixture {
  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  interface Throwing {
    void run() throws Exception;
  }

  static void rejects(Throwing code, String why) throws Exception {
    try {
      code.run();
      throw new AssertionError(why);
    } catch (IllegalArgumentException | IllegalStateException | IOException expected) {
    }
  }

  static Map<Object, Object> map(Object... pairs) {
    var m = new LinkedHashMap<Object, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  static KahluaTable lua(Object... pairs) {
    return new KahluaTableImpl(map(pairs));
  }

  public static void main(String[] args) throws Exception {
    ProbeFixture.run();
    BuildGuard.verify(ScenarioFixture.class.getClassLoader());
    NativeGuardFixture.run(Path.of(args[0]).getParent().getParent());
    try (var helper =
        new URLClassLoader(
            new URL[] {
              Path.of(args[0]).getParent().getParent().resolve("pzexe.jar").toUri().toURL()
            })) {
      check(
          LaunchGuard.isPinnedDiscovery("zombie.pzexe", "pzexe.jar", helper),
          "Pinned launcher discovery was not recognized");
      check(
          !LaunchGuard.isPinnedDiscovery("zombie.gameStates.MainScreenState", "pzexe.jar", helper),
          "Game JVM exempted");
      check(
          !LaunchGuard.isPinnedDiscovery("zombie.pzexe --anything", "pzexe.jar", helper),
          "Other helper command exempted");
      check(
          !LaunchGuard.isPinnedDiscovery("zombie.pzexe", "pzexe.jar:projectzomboid.jar", helper),
          "Game classpath exempted");
    }
    ClassLoader tampered =
        new ClassLoader(ScenarioFixture.class.getClassLoader()) {
          public InputStream getResourceAsStream(String path) {
            if (path.equals("zombie/iso/IsoWorld.class") || path.equals("zombie/pzexe.class"))
              return new ByteArrayInputStream(new byte[] {1});
            return super.getResourceAsStream(path);
          }
        };
    rejects(() -> BuildGuard.verify(tampered), "Unknown build accepted");
    check(
        !LaunchGuard.isPinnedDiscovery("zombie.pzexe", "pzexe.jar", tampered),
        "Unknown launcher helper exempted");
    PedestrianConditionFixture.run();
    // Real pooled native Path/PathNode contract; does not initialize a solver or game world.
    var pooledPath = new zombie.pathfind.Path();
    pooledPath.addNode(10.5f, 11.5f, 0, 13);
    pooledPath.addNode(12.5f, 11.5f, 0, 4);
    var detachedPath = new NativeCivilianNavigation().copy(pooledPath);
    pooledPath.getNode(0).x = 999;
    pooledPath.clear();
    check(
        detachedPath.size() == 2
            && detachedPath.getFirst().x() == 10.5f
            && detachedPath.getFirst().flags() == 13,
        "Pooled native path copied with flags");
    try (ZipFile game = new ZipFile(args[0])) {
      ProbeCrashFeedbackFixture.run(game);
      ProbeRockColliderFixture.run(game);
      for (String name : ScenarioTransformer.TARGETS) {
        check(BuildGuard.HASHES.containsKey(name), "Unguarded transformed class " + name);
        byte[] original = game.getInputStream(game.getEntry(name + ".class")).readAllBytes();
        byte[] changed =
            ScenarioTransformer.instrument(name, original, ScenarioFixture.class.getClassLoader());
        check(!Arrays.equals(original, changed), "Missing hook " + name);
        var errors = ClassFile.of().verify(changed);
        check(errors.isEmpty(), "Invalid JVM bytecode " + name + errors);
        if (name.equals("zombie/CombatManager")) {
          int originalUi = 0, guardedUi = 0, remainingUi = 0;
          for (var method : ClassFile.of().parse(original).methods())
            if (method.methodName().equalsString("attackCollisionCheck"))
              for (var code : method.code().orElseThrow())
                if (code instanceof java.lang.classfile.instruction.InvokeInstruction call
                    && call.owner().asInternalName().equals("zombie/ui/MoodlesUI")
                    && call.name().equalsString("wiggle")) originalUi++;
          for (var method : ClassFile.of().parse(changed).methods())
            if (method.methodName().equalsString("attackCollisionCheck"))
              for (var code : method.code().orElseThrow())
                if (code instanceof java.lang.classfile.instruction.InvokeInstruction call
                    && call.name().equalsString("wiggle")) {
                  if (call.owner().asInternalName().equals("net/akr/scenario/NativeCombatRelay"))
                    guardedUi++;
                  if (call.owner().asInternalName().equals("zombie/ui/MoodlesUI")) remainingUi++;
                }
          check(
              originalUi > 0 && guardedUi == originalUi && remainingUi == 0,
              "Scoped native combat UI guard coverage");
        }
        if (name.equals("zombie/characters/IsoPlayer")) {
          for (String entry : List.of("update", "postupdate")) {
            var method =
                ClassFile.of().parse(changed).methods().stream()
                    .filter(
                        m ->
                            m.methodName().stringValue().equals(entry)
                                && m.methodType().stringValue().equals("()V"))
                    .findFirst()
                    .orElseThrow();
            int begin = 0, end = 0;
            for (var element : method.code().orElseThrow())
              if (element instanceof java.lang.classfile.instruction.InvokeInstruction call
                  && call.owner().asInternalName().equals("net/akr/scenario/ServerActors")) {
                if (call.name().stringValue().equals("beginNativeStep")) begin++;
                if (call.name().stringValue().equals("endNativeStep")) end++;
              }
            check(begin == 1 && end >= 1, "Actor sentinel hook sites " + entry);
          }
        }
      }
    }
    PrimitiveCopy copy =
        new PrimitiveCopy(
            lua(
                "revision",
                1d,
                "residents",
                lua(1d, lua("id", "r-1", "position", lua("x", 42d, "y", 43d, "z", 0d)))));
    check(!copy.step(System.nanoTime() - 1), "Copy ignored budget");
    while (!copy.step(System.nanoTime() + 1_000_000)) {}
    var obs = (ObservationBatch) ProtocolCodec.encode("ObservationBatch", copy.result);
    check(
        obs.getRevision() == 1 && obs.getResidents(0).getPosition().getX() == 42,
        "Primitive/protobuf roundtrip");
    rejects(
        () ->
            ProtocolCodec.encode(
                "ObservationBatch", map("residents", map(2d, map("id", "sparse")))),
        "Sparse array accepted");
    rejects(
        () -> ProtocolCodec.encode("ObservationBatch", map("revision", -1d)),
        "Negative unsigned field");
    rejects(
        () -> ProtocolCodec.encode("ObservationBatch", map("world_hour", Double.NaN)),
        "Nonfinite field");
    KahluaTable cyclic = lua();
    cyclic.rawset("loop", cyclic);
    rejects(
        () -> new PrimitiveCopy(cyclic).step(System.nanoTime() + 100_000_000), "Cycle accepted");
    rejects(
        () -> new PrimitiveCopy(lua("actor", new Object())).step(System.nanoTime() + 100_000_000),
        "Game object copied");
    var plans =
        PlanBatch.newBuilder()
            .setObservationRevision(9)
            .addPlans(
                Plan.newBuilder()
                    .setResidentId("a")
                    .setGeneration(2)
                    .addActions(
                        Action.newBuilder()
                            .setKind(ActionKind.DRIVE)
                            .setTarget(Point.newBuilder().setX(5))
                            .setNavigationId("nav-fixture")
                            .addRoadNodeIds(17)
                            .addRoadNodeIds(23)))
            .build();
    Map<Object, Object> decoded = ProtocolCodec.decode("PlanBatch", plans);
    check(
        ((PlanBatch) ProtocolCodec.encode("PlanBatch", decoded)).equals(plans),
        "Plan decode/encode");
    var navigation =
        ObservationBatch.newBuilder()
            .setNavigationId("nav-fixture")
            .addRoadClosures(
                RoadClosure.newBuilder()
                    .setFrom(17)
                    .setTo(23)
                    .setExpiresWorldHour(12.25)
                    .setReason("obstruction"))
            .addResidents(
                Resident.newBuilder()
                    .setId("a")
                    .setHasVehicle(true)
                    .setVehicleId("car-a")
                    .setVehicleObservation(
                        VehicleObservation.newBuilder()
                            .setId("car-a")
                            .setAvailable(true)
                            .setOccupancyRevision(4)
                            .setProfile("smallcar")
                            .setPosition(Point.newBuilder().setX(3))
                            .setEntryPoint(Point.newBuilder().setX(4))))
            .build();
    check(
        ProtocolCodec.encode(
                "ObservationBatch", ProtocolCodec.decode("ObservationBatch", navigation))
            .equals(navigation),
        "Navigation/vehicle observation roundtrip");
    Path dir = RuntimeTestPaths.directory("ipc-");
    Path socket = dir.resolve("npc.sock");
    try (ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      listener.bind(UnixDomainSocketAddress.of(socket));
      ExecutorService service = Executors.newSingleThreadExecutor();
      Future<?> serving =
          service.submit(
              () -> {
                try (SocketChannel ch = listener.accept()) {
                  ch.configureBlocking(false);
                  Envelope hello = PlannerTransport.read(ch);
                  check(hello.hasHello(), "No hello");
                  PlannerTransport.write(ch, hello);
                  Envelope request = PlannerTransport.read(ch);
                  check(request.hasObservations(), "No observations");
                  PlannerTransport.write(
                      ch,
                      Envelope.newBuilder()
                          .setProtocolVersion(1)
                          .setWorld("fixture")
                          .setServerEpoch("epoch")
                          .setRequestId(request.getRequestId())
                          .setPlans(
                              plans.toBuilder()
                                  .setObservationRevision(request.getObservations().getRevision()))
                          .build());
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      PlannerTransport transport = new PlannerTransport(socket, "fixture", "epoch", "");
      Thread worker = new Thread(transport);
      worker.setDaemon(true);
      worker.start();
      transport.pending.set(new PlannerTransport.Sample(3, System.nanoTime(), map("revision", 9d)));
      long end = System.nanoTime() + 5_000_000_000L;
      while (transport.ready.get() == null && System.nanoTime() < end) Thread.sleep(5);
      check(
          transport.ready.get() != null && transport.ready.get().revision() == 9,
          "IPC roundtrip failed: " + transport.health);
      rejects(
          () ->
              transport.validate(
                  Envelope.newBuilder()
                      .setProtocolVersion(1)
                      .setWorld("wrong")
                      .setServerEpoch("epoch")
                      .setRequestId(3)
                      .build(),
                  3),
          "Wrong world accepted");
      rejects(
          () ->
              transport.validate(
                  Envelope.newBuilder()
                      .setProtocolVersion(1)
                      .setWorld("fixture")
                      .setServerEpoch("old")
                      .setRequestId(3)
                      .build(),
                  3),
          "Stale epoch accepted");
      rejects(
          () ->
              transport.validate(
                  Envelope.newBuilder()
                      .setProtocolVersion(1)
                      .setWorld("fixture")
                      .setServerEpoch("epoch")
                      .setRequestId(2)
                      .build(),
                  3),
          "Wrong request accepted");
      serving.get(5, TimeUnit.SECONDS);
      worker.interrupt();
      service.shutdownNow();
    } finally {
      Files.deleteIfExists(socket);
      Files.deleteIfExists(dir);
    }
    Path oversizeDir = RuntimeTestPaths.directory("oversize-");
    try (ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      Path socket2 = oversizeDir.resolve("oversize.sock");
      Files.deleteIfExists(socket2);
      listener.bind(UnixDomainSocketAddress.of(socket2));
      try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socket2));
          SocketChannel server = listener.accept()) {
        server.write(ByteBuffer.allocate(4).putInt(PlannerTransport.MAX_FRAME + 1).flip());
        client.configureBlocking(false);
        rejects(() -> PlannerTransport.read(client), "Oversized frame accepted");
      } finally {
        Files.deleteIfExists(socket2);
      }
    } finally {
      Files.deleteIfExists(oversizeDir);
    }
    System.out.println(
        "Scenario fixtures passed: pinned build, JVM verification, bounded copy, protobuf, Unix"
            + " IPC, rejection cases");
  }
}
