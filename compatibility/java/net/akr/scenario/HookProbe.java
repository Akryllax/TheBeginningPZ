package net.akr.scenario;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Inspects transformed bytes without defining or initializing any game classes. */
public final class HookProbe {
  /** Print exact helper invocation counts by class and enclosing method. */
  public static void main(String[] args) throws Exception {
    try (var jar = new ZipFile(Path.of(args[0]).toFile())) {
      for (String name : new TreeSet<>(ScenarioTransformer.TARGETS)) {
        byte[] original = jar.getInputStream(jar.getEntry(name + ".class")).readAllBytes();
        byte[] transformed =
            ScenarioTransformer.instrument(name, original, HookProbe.class.getClassLoader());
        var errors = ClassFile.of().verify(transformed);
        if (!errors.isEmpty()) throw new IllegalStateException(name + ": " + errors);
        for (var method : ClassFile.of().parse(transformed).methods()) {
          if (method.code().isEmpty()) continue;
          Map<String, Integer> counts = new TreeMap<>();
          for (var element : method.code().get()) {
            if (element instanceof InvokeInstruction call
                && call.owner().asInternalName().startsWith("net/akr/")) {
              String site =
                  name
                      + "\t"
                      + method.methodName().stringValue()
                      + method.methodType().stringValue()
                      + "\t"
                      + call.owner().asInternalName()
                      + "."
                      + call.name().stringValue()
                      + call.type().stringValue();
              counts.merge(site, 1, Integer::sum);
            }
          }
          for (var entry : counts.entrySet())
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }
      }
    }
  }
}
