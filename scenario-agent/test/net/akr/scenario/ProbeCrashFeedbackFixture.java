package net.akr.scenario;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.zip.ZipFile;

/** Proves scope and original crash-call preservation; native contact/audio need a live test. */
final class ProbeCrashFeedbackFixture {
  static void run(ZipFile game) throws Exception {
    Object managed = new Object(), other = new Object();
    Thread owner = Thread.currentThread(), otherThread = new Thread();
    ScenarioFixture.check(
        ProbeCrashFeedback.accepts(managed, managed, owner, owner, true, false, true),
        "Managed server crash rejected");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(managed, other, owner, owner, true, false, true),
        "Ordinary car received crash feedback");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(null, null, owner, owner, true, false, true),
        "Unregistered crash accepted");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(managed, managed, owner, otherThread, true, false, true),
        "Off-thread crash accepted");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(managed, managed, owner, owner, false, false, true),
        "Single-player crash accepted");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(managed, managed, owner, owner, true, true, true),
        "Client crash accepted");
    ScenarioFixture.check(
        !ProbeCrashFeedback.accepts(managed, managed, owner, owner, true, false, false),
        "Client-owned body accepted");
    ScenarioFixture.check(
        ProbeCrashFeedback.sound(4.999f).equals("VehicleCrash1")
            && ProbeCrashFeedback.sound(5).equals("VehicleCrash2")
            && ProbeCrashFeedback.sound(29.999f).equals("VehicleCrash2")
            && ProbeCrashFeedback.sound(30).equals("VehicleCrash"),
        "Stock crash sound tiers changed");
    for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1, 0})
      ScenarioFixture.check(
          ProbeCrashFeedback.sound(bad).isEmpty(), "Invalid crash amount accepted");
    byte[] original =
        game.getInputStream(game.getEntry("zombie/vehicles/BaseVehicle.class")).readAllBytes();
    byte[] changed =
        ScenarioTransformer.instrument(
            "zombie/vehicles/BaseVehicle",
            original,
            ProbeCrashFeedbackFixture.class.getClassLoader());
    ClassModel model = ClassFile.of().parse(changed);
    int hooks = 0;
    for (MethodModel method : model.methods()) {
      if (method.code().isEmpty()) continue;
      for (CodeElement element : method.code().orElseThrow())
        if (element instanceof InvokeInstruction call
            && call.owner().asInternalName().equals("net/akr/scenario/ProbeCrashFeedback")) {
          ScenarioFixture.check(
              method.methodName().equalsString("crash")
                  && method.methodType().equalsString("(FZ)V"),
              "Hook injected outside exact crash method");
          ScenarioFixture.check(
              call.name().equalsString("onCrash")
                  && call.type().equalsString("(Lzombie/vehicles/BaseVehicle;FZ)V"),
              "Wrong crash bridge signature");
          hooks++;
        }
    }
    ScenarioFixture.check(hooks == 1, "Crash bridge must occur exactly once");
    var before =
        ClassFile.of().parse(original).methods().stream()
            .filter(
                m -> m.methodName().equalsString("crash") && m.methodType().equalsString("(FZ)V"))
            .findFirst()
            .orElseThrow();
    var after =
        model.methods().stream()
            .filter(
                m -> m.methodName().equalsString("crash") && m.methodType().equalsString("(FZ)V"))
            .findFirst()
            .orElseThrow();
    ScenarioFixture.check(
        invocations(before)
            .equals(
                invocations(after).stream()
                    .filter(v -> !v.startsWith("net/akr/scenario/ProbeCrashFeedback."))
                    .toList()),
        "Original stock damage/audio calls changed");
  }

  private static java.util.List<String> invocations(MethodModel method) {
    var out = new java.util.ArrayList<String>();
    for (CodeElement element : method.code().orElseThrow())
      if (element instanceof InvokeInstruction call)
        out.add(
            call.owner().asInternalName()
                + "."
                + call.name().stringValue()
                + call.type().stringValue());
    return out;
  }
}
