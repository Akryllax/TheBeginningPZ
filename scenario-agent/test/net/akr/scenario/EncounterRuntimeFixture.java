package net.akr.scenario;

import java.util.*;
import net.akr.scenario.protocol.RuntimeControl.*;

final class EncounterRuntimeFixture {
  private static final class Backend implements RuntimeSession.Backend {
    EventScheduler scheduler;
    String id;
    int begins;
    boolean release = true, done;
    final EventResources.Resource resource =
        new EventResources.Resource(EventResources.Kind.ACTOR, "pooled");

    public boolean encountersEnabled() {
      return true;
    }

    public void begin(EventScheduler.View e, EventScheduler s) {
      begins++;
      id = e.id();
      scheduler = s;
      s.acquire(id, resource);
      done = false;
    }

    public boolean prepare() {
      return true;
    }

    public boolean update() {
      return done;
    }

    public boolean cleanup() {
      if (!release) return false;
      scheduler.release(id, resource, true, true);
      return true;
    }

    public List<ActorSample> samples() {
      return List.of();
    }

    public EncounterProgress encounter() {
      return EncounterProgress.newBuilder()
          .setConstructed(4)
          .setCleanupVerified(done && release)
          .build();
    }
  }

  private static Request.Builder request(String id) {
    return Request.newBuilder().setVersion(1).setWorld("world").setEpoch("epoch").setRequestId(id);
  }

  static void run() {
    assert EncounterCleanupGate.clearance(2, 2, true, true, 2);
    assert !EncounterCleanupGate.clearance(2, 2, true, true, 1);
    assert !EncounterCleanupGate.clearance(1, 1, true, true, 2);
    assert !EncounterCleanupGate.clearance(2, 2, false, true, 2);
    assert !EncounterCleanupGate.absent(2, 2, false, 2);
    assert EncounterCleanupGate.absent(2, 2, true, 2);
    assert EncounterCleanupGate.absent(0, 0, false, 2);

    assert EncounterObserverGate.evaluate(true, false, true, 1, 100)
        == EncounterObserverGate.State.WAITING;
    assert EncounterObserverGate.evaluate(true, true, true, 2, 100)
        == EncounterObserverGate.State.READY;
    assert EncounterObserverGate.evaluate(false, false, true, 2, 100)
        == EncounterObserverGate.State.DISCONNECTED;
    assert EncounterObserverGate.evaluate(false, false, false, 2, 100)
        == EncounterObserverGate.State.WAITING;
    assert EncounterObserverGate.evaluate(true, false, true, 101, 100)
        == EncounterObserverGate.State.TIMED_OUT;
    var comparison =
        CivilianEncounterCase.newBuilder()
            .setActors(2)
            .setScenario(CivilianEncounterCase.Scenario.STRIDE_COMPARE)
            .setTimeoutSeconds(120)
            .setHoldSeconds(8)
            .build();
    assert EncounterDefinition.from(comparison).actors() == 2;
    try {
      EncounterDefinition.from(comparison.toBuilder().setActors(1).build());
      throw new AssertionError("single comparison accepted");
    } catch (IllegalArgumentException expected) {
    }
    var routine =
        comparison.toBuilder()
            .setActors(1)
            .setScenario(CivilianEncounterCase.Scenario.ROUTINE)
            .build();
    assert EncounterDefinition.from(routine).actors() == 1;
    try {
      EncounterDefinition.from(routine.toBuilder().setActors(4).build());
      throw new AssertionError("routine crowd accepted");
    } catch (IllegalArgumentException expected) {
    }
    var backend = new Backend();
    var runtime = new RuntimeSession("world", "epoch", backend);
    var definition =
        CivilianEncounterCase.newBuilder()
            .setActors(1)
            .setScenario(CivilianEncounterCase.Scenario.OPEN_ESCAPE)
            .setSeed(1)
            .setTimeoutSeconds(120)
            .setHoldSeconds(20)
            .build();
    var submit =
        request("first")
            .setOperation(Request.Operation.SUBMIT)
            .setCivilianEncounter(definition)
            .build();
    var first = runtime.handle(submit);
    assert first.getAccepted();
    assert runtime.handle(submit).getEventId().equals(first.getEventId());
    assert runtime
        .handle(submit.toBuilder().setCivilianEncounter(definition.toBuilder().setSeed(2)).build())
        .getCode()
        .equals("request_conflict");
    assert !runtime.handle(submit.toBuilder().setEpoch("old").build()).getAccepted();
    assert !runtime
        .handle(
            submit.toBuilder()
                .setRequestId("too-many")
                .setCivilianEncounter(definition.toBuilder().setActors(64))
                .build())
        .getAccepted();
    assert !runtime
        .handle(
            submit.toBuilder()
                .setRequestId("four-open")
                .setCivilianEncounter(definition.toBuilder().setActors(4))
                .build())
        .getAccepted();
    assert !runtime
        .handle(
            submit.toBuilder()
                .setRequestId("ambiguous")
                .setPedestrian(PedestrianCase.getDefaultInstance())
                .build())
        .getAccepted();
    assert !runtime
        .handle(
            request("legacy")
                .setOperation(Request.Operation.SUBMIT)
                .setPedestrian(PedestrianCase.getDefaultInstance())
                .build())
        .getAccepted();
    var second =
        runtime.handle(
            submit.toBuilder()
                .setRequestId("second")
                .setCivilianEncounter(
                    definition.toBuilder()
                        .setActors(4)
                        .setScenario(CivilianEncounterCase.Scenario.DEFENSE_ESCAPE))
                .build());
    assert second.getAccepted();
    runtime.tick();
    assert backend.begins == 1;
    backend.done = true;
    runtime.tick();
    backend.release = false;
    runtime.tick();
    runtime.tick();
    assert runtime.scheduler.status(first.getEventId()).phase()
        == EventScheduler.Phase.CLEANUP_BLOCKED;
    assert backend.begins == 1;
    backend.release = true;
    runtime.tick();
    runtime.tick();
    assert backend.begins == 2;
    var cancel =
        runtime.handle(
            request("cancel")
                .setOperation(Request.Operation.CANCEL)
                .setEventId(second.getEventId())
                .build());
    assert cancel.getAccepted();
    runtime.tick();
    runtime.tick();
    assert runtime.scheduler.status(second.getEventId()).phase() == EventScheduler.Phase.CANCELLED;
    var third = runtime.handle(submit.toBuilder().setRequestId("third").build());
    assert third.getAccepted();
    runtime.tick();
    assert backend.begins == 3;
    assert runtime
        .handle(request("hello").setOperation(Request.Operation.HELLO).build())
        .getPhase()
        .equals("READY");
    System.out.println(
        "Encounter runtime: typed limits, capability, duplicate/conflict/stale requests, blocked"
            + " cleanup, cancel and same-session reuse passed");
  }
}
