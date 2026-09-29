package net.akr.scenario;

import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import net.akr.scenario.bridge.ProtocolCodec;
import net.akr.scenario.protocol.NpcControl.*;

/** Talks to the real C++ service using generated Java protocol classes. */
public final class NativeRoundtripFixture {
  public static void main(String[] args) throws Exception {
    String world = args.length > 1 ? args[1] : "AKR_DayOne", epoch = "java-native-fixture";
    try (SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(Path.of(args[0])))) {
      ch.configureBlocking(false);
      Envelope.Builder base =
          Envelope.newBuilder().setProtocolVersion(1).setWorld(world).setServerEpoch(epoch);
      PlannerTransport.write(
          ch,
          base.clone()
              .setRequestId(0)
              .setHello(
                  Handshake.newBuilder()
                      .setBuild("42.20.4/b0bbce05d5")
                      .setModVersion("0.2.0")
                      .setMaxFrameBytes(262144)
                      .setMaxResidents(128))
              .build());
      Envelope hello = PlannerTransport.read(ch);
      if (!hello.hasHello()) throw new AssertionError("Handshake rejected " + hello);
      var p = Point.newBuilder().setX(10800).setY(9800).setZ(0).build();
      var resident =
          Resident.newBuilder()
              .setId("java-fixture-resident")
              .setRevision(1)
              .setGeneration(1)
              .setPosition(p)
              .setHome(p)
              .setWork(p)
              .setShop(p)
              .setClinic(p)
              .setRole("worker")
              .setName("Fixture Resident")
              .setHealth(100)
              .setInfection("susceptible")
              .setHunger(0.8)
              .setHomeSafe(true)
              .setHasFood(true)
              .build();
      PlannerTransport.write(
          ch,
          base.clone()
              .setRequestId(1)
              .setObservations(
                  ObservationBatch.newBuilder()
                      .setRevision(1)
                      .setWorldHour(9)
                      .setScenarioHour(0)
                      .setPhase("calm")
                      .setSeed(99)
                      .setOnlinePlayers(1)
                      .addResidents(resident))
              .build());
      Envelope response = PlannerTransport.read(ch);
      if (!response.hasPlans()
          || response.getRequestId() != 1
          || response.getPlans().getObservationRevision() != 1
          || response.getPlans().getPlansCount() != 1)
        throw new AssertionError("Real native planner response invalid: " + response);
      Plan plan = response.getPlans().getPlans(0);
      if (!plan.getResidentId().equals(resident.getId()) || plan.getActionsCount() == 0)
        throw new AssertionError("Missing action");
      ProtocolCodec.decode("PlanBatch", response.getPlans());
      System.out.println(
          "Java ↔ C++ Unix IPC passed: "
              + plan.getGoal()
              + ", "
              + plan.getActionsCount()
              + " actions, revision "
              + plan.getBasedOnRevision());
    }
  }
}
