package net.akr.scenario;

import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import net.akr.scenario.protocol.RuntimeControl.*;

final class PedestrianRuntimeFixture {
  static final class Fake implements RuntimeSession.Backend {
    EventScheduler scheduler;
    String id;
    int starts;
    boolean complete, removable = true;
    final EventResources.Resource resource =
        new EventResources.Resource(EventResources.Kind.PEDESTRIAN, "actor");

    public void begin(EventScheduler.View event, EventScheduler scheduler) {
      this.scheduler = scheduler;
      id = event.id();
      starts++;
    }

    public boolean prepare() {
      scheduler.acquire(id, resource);
      return true;
    }

    public boolean update() {
      return complete;
    }

    public boolean cleanup() {
      if (removable) scheduler.release(id, resource, true, true);
      return removable;
    }

    public List<ActorSample> samples() {
      return List.of();
    }
  }

  static void check(boolean value, String reason) {
    if (!value) throw new AssertionError(reason);
  }

  static Request.Builder request(String id) {
    return Request.newBuilder().setVersion(1).setWorld("world").setEpoch("epoch").setRequestId(id);
  }

  static PedestrianCase definition() {
    return PedestrianCase.newBuilder()
        .setActors(1)
        .setTimeoutSeconds(30)
        .setHoldSeconds(2)
        .addRoute(Point.newBuilder().setX(100).setY(100))
        .addRoute(Point.newBuilder().setX(110).setY(100))
        .build();
  }

  /** Actor cases route to their own adapter and keep gate-1 limits; zombie cases are unchanged. */
  static void actors() {
    Fake zombies = new Fake(), actors = new Fake();
    RuntimeSession runtime =
        new RuntimeSession(
            "world",
            "epoch",
            new RuntimeSession.Routed(
                java.util.Map.of(
                    EventScheduler.Kind.PEDESTRIAN, zombies, EventScheduler.Kind.ACTOR, actors)));
    PedestrianCase actor =
        definition().toBuilder()
            .setEntity(PedestrianCase.Entity.ACTOR)
            .setHoldSeconds(10)
            .setReassignAfterSeconds(4)
            .build();
    Reply accepted =
        runtime.handle(
            request("actor").setOperation(Request.Operation.SUBMIT).setPedestrian(actor).build());
    check(accepted.getAccepted(), "actor submit");
    check(
        runtime.scheduler.status(accepted.getEventId()).definition().kind()
            == EventScheduler.Kind.ACTOR,
        "actor kind");
    check(
        runtime
            .handle(
                request("actor")
                    .setOperation(Request.Operation.SUBMIT)
                    .setPedestrian(actor.toBuilder().setReassignAfterSeconds(0))
                    .build())
            .getCode()
            .equals("request_conflict"),
        "actor retry conflict");
    PedestrianCase walker =
        actor.toBuilder().setReassignAfterSeconds(0).setWalkTilesPerSecond(2.5).build();
    check(
        runtime
            .handle(
                request("hunter-shape")
                    .setOperation(Request.Operation.SUBMIT)
                    .setPedestrian(
                        actor.toBuilder().setReassignAfterSeconds(0).setHunterZombies(1).build())
                    .build())
            .getAccepted(),
        "hunter definition");
    Reply walking =
        runtime.handle(
            request("walker").setOperation(Request.Operation.SUBMIT).setPedestrian(walker).build());
    check(walking.getAccepted(), "walking actor");
    for (PedestrianCase invalid :
        List.of(
            actor.toBuilder().setActors(4).build(),
            actor.toBuilder().setReassignAfterSeconds(10).build(),
            definition().toBuilder().setReassignAfterSeconds(1).setHoldSeconds(5).build(),
            walker.toBuilder().setWalkTilesPerSecond(7).build(),
            walker.toBuilder().setWalkTilesPerSecond(0.2).build(),
            walker.toBuilder().setWalkTilesPerSecond(Double.NaN).build(),
            walker.toBuilder().setReassignAfterSeconds(3).build(),
            definition().toBuilder().setWalkTilesPerSecond(2).build(),
            walker.toBuilder().setHunterZombies(1).build(),
            actor.toBuilder().setReassignAfterSeconds(0).setHunterZombies(2).build(),
            actor.toBuilder()
                .setReassignAfterSeconds(0)
                .setHunterZombies(1)
                .setRoute(1, Point.newBuilder().setX(102).setY(100))
                .build(),
            definition().toBuilder().setHunterZombies(1).build(),
            actor.toBuilder().setReassignAfterSeconds(0).setChaseDelaySeconds(5).build(),
            actor.toBuilder()
                .setReassignAfterSeconds(0)
                .setHunterZombies(1)
                .setChaseDelaySeconds(31)
                .build(),
            actor.toBuilder()
                .setReassignAfterSeconds(0)
                .setHunterZombies(1)
                .setFleeTilesPerSecond(2.6)
                .setTripFall(true)
                .build()))
      check(
          !runtime
              .handle(
                  request("bad-" + invalid.hashCode())
                      .setOperation(Request.Operation.SUBMIT)
                      .setPedestrian(invalid)
                      .build())
              .getAccepted(),
          "actor limits " + invalid);
    runtime.tick();
    check(actors.starts == 1 && zombies.starts == 0, "actor routed");
    actors.complete = true;
    runtime.tick();
    runtime.tick();
    check(
        runtime.scheduler.status(accepted.getEventId()).phase() == EventScheduler.Phase.COMPLETED,
        "actor completed");
    for (int i = 0; i < 6; i++) runtime.tick();
    check(
        actors.starts == 3
            && runtime.scheduler.status(walking.getEventId()).phase()
                == EventScheduler.Phase.COMPLETED,
        "walking actor routed");
    Reply zombie =
        runtime.handle(
            request("zombie")
                .setOperation(Request.Operation.SUBMIT)
                .setPedestrian(definition())
                .build());
    runtime.tick();
    check(
        zombies.starts == 1
            && runtime.scheduler.status(zombie.getEventId()).definition().kind()
                == EventScheduler.Kind.PEDESTRIAN,
        "default entity stays zombie");
    EventResources resources = new EventResources();
    for (int i = 0; i < 4; i++)
      resources.acquire(
          new EventResources.Resource(
              i % 2 == 0 ? EventResources.Kind.ACTOR : EventResources.Kind.PEDESTRIAN, "a" + i));
    try {
      resources.acquire(new EventResources.Resource(EventResources.Kind.ACTOR, "a5"));
      check(false, "actor capacity");
    } catch (IllegalStateException expected) {
    }
    check(
        !resources.release(
            new EventResources.Resource(EventResources.Kind.ACTOR, "a0"), true, false),
        "actor released without network absence");
  }

  static void run() throws Exception {
    Fake backend = new Fake();
    RuntimeSession runtime = new RuntimeSession("world", "epoch", backend);
    Request submit =
        request("one").setOperation(Request.Operation.SUBMIT).setPedestrian(definition()).build();
    Reply accepted = runtime.handle(submit);
    check(accepted.getAccepted(), "submit");
    check(
        runtime.handle(submit).getEventId().equals(accepted.getEventId()), "retry executed twice");
    check(
        runtime
            .handle(submit.toBuilder().setPedestrian(definition().toBuilder().setActors(4)).build())
            .getCode()
            .equals("request_conflict"),
        "payload conflict");
    check(!runtime.handle(submit.toBuilder().setEpoch("old").build()).getAccepted(), "stale epoch");
    check(
        !runtime
            .handle(
                submit.toBuilder()
                    .setRequestId("bad")
                    .setPedestrian(definition().toBuilder().setActors(64))
                    .build())
            .getAccepted(),
        "unqualified crowd accepted");
    check(
        !runtime
            .handle(
                submit.toBuilder()
                    .setRequestId("nan")
                    .setPedestrian(
                        definition().toBuilder()
                            .setRoute(0, Point.newBuilder().setX(Double.NaN).setY(100)))
                    .build())
            .getAccepted(),
        "NaN accepted");
    runtime.tick();
    check(backend.starts == 1, "backend starts");
    check(
        runtime
                .handle(
                    request("status")
                        .setOperation(Request.Operation.STATUS)
                        .setEventId(accepted.getEventId())
                        .build())
                .getResourcesCount()
            == 1,
        "resource hidden");
    for (int i = 0; i < 8; i++)
      check(
          runtime.handle(submit.toBuilder().setRequestId("queued" + i).build()).getAccepted(),
          "queue capacity");
    check(
        !runtime.handle(submit.toBuilder().setRequestId("overflow").build()).getAccepted(),
        "overflow");
    check(
        runtime
            .handle(
                request("cancel")
                    .setOperation(Request.Operation.CANCEL)
                    .setEventId(accepted.getEventId())
                    .build())
            .getAccepted(),
        "cancel while full");
    backend.removable = false;
    runtime.tick();
    runtime.tick();
    check(
        runtime.scheduler.status(accepted.getEventId()).phase()
            == EventScheduler.Phase.CLEANUP_BLOCKED,
        "uncertain cleanup completed");
    runtime.tick();
    check(backend.starts == 1, "started while cleanup blocked");
    backend.removable = true;
    runtime.tick();
    check(
        runtime.scheduler.status(accepted.getEventId()).phase() == EventScheduler.Phase.CANCELLED,
        "cancel outcome");

    Path folder = RuntimeTestPaths.directory("rt-");
    Path socket = folder.resolve("runtime.sock");
    try (RuntimeSocket server =
        new RuntimeSocket(socket, new RuntimeSession("world", "epoch", new Fake()))) {
      try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
        client.configureBlocking(false);
        RuntimeSocket.writeFrame(
            client, request("hello").setOperation(Request.Operation.HELLO).build().toByteArray());
        Reply reply = Reply.parseFrom(RuntimeSocket.readFrame(client));
        check(reply.getAccepted() && reply.getEpoch().equals("epoch"), "real UDS roundtrip");
      }
    } finally {
      Files.deleteIfExists(socket.resolveSibling("runtime.sock.lock"));
      Files.deleteIfExists(folder);
    }
    actors();
    System.out.println(
        "Pedestrian runtime fixtures passed: typed definitions, retries, queue/cancel, cleanup"
            + " gating, actor routing and real private UDS");
  }
}
