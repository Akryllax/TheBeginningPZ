package net.akr.scenario.compat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Exact per-method injection counts, checked before transformed classes can be used. */
public final class HookContract {
  private static final Map<String, Integer> EXPECTED = load();

  private HookContract() {}

  private static Map<String, Integer> load() {
    var result = new TreeMap<String, Integer>();
    var stream = HookContract.class.getResourceAsStream("/META-INF/akr/scenario-hooks.tsv");
    if (stream == null) throw new IllegalStateException("Missing reviewed hook contract");
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      for (String line; (line = reader.readLine()) != null; ) {
        int split = line.lastIndexOf('\t');
        if (split < 0) throw new IllegalStateException("Malformed hook contract");
        String key = line.substring(0, split);
        int count = Integer.parseInt(line.substring(split + 1));
        if (count < 1 || result.putIfAbsent(key, count) != null)
          throw new IllegalStateException("Duplicate or invalid hook contract: " + key);
      }
    } catch (java.io.IOException error) {
      throw new IllegalStateException("Unreadable hook contract", error);
    }
    if (result.isEmpty()) throw new IllegalStateException("Empty hook contract");
    return Map.copyOf(result);
  }

  /** Verify the reviewed helper calls for one class; missing and extra sites both fail. */
  public static void verify(String name, byte[] bytes) {
    var expected = new TreeMap<String, Integer>();
    EXPECTED.forEach(
        (key, count) -> {
          if (key.startsWith(name + "\t")) expected.put(key, count);
        });
    if (expected.isEmpty())
      throw new IllegalStateException("Unreviewed transformed class: " + name);
    var actual = observe(name, bytes);
    if (!expected.equals(actual))
      throw new IllegalStateException(
          "Hook contract mismatch: " + name + " expected=" + expected + " actual=" + actual);
  }

  /** Inspect only this agent's helper calls, allowing the independent Observer hook to coexist. */
  public static Map<String, Integer> observe(String name, byte[] bytes) {
    var result = new TreeMap<String, Integer>();
    var model = ClassFile.of().parse(bytes);
    for (var method : model.methods()) {
      if (method.code().isEmpty()) continue;
      for (var element : method.code().get()) {
        if (element instanceof InvokeInstruction call
            && call.owner().asInternalName().startsWith("net/akr/scenario/")) {
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
          result.merge(site, 1, Integer::sum);
        }
      }
    }
    return result;
  }
}
