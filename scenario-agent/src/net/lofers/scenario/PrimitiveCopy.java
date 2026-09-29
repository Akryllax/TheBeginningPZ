package net.lofers.scenario;

import java.util.*;
import se.krka.kahlua.vm.*;

/** Cooperative game-thread copy. The IPC worker receives only detached primitives. */
final class PrimitiveCopy {
  record Frame(
      KahluaTable source, KahluaTableIterator iterator, Map<Object, Object> target, int depth) {}

  private final Deque<Frame> stack = new ArrayDeque<>();
  final Map<Object, Object> result = new LinkedHashMap<>();
  int entries;

  PrimitiveCopy(KahluaTable root) {
    stack.push(new Frame(root, root.iterator(), result, 0));
  }

  boolean step(long deadline) {
    int operations = 0;
    while (!stack.isEmpty() && operations++ < 256 && System.nanoTime() < deadline) {
      Frame frame = stack.peek();
      if (!frame.iterator.advance()) {
        stack.pop();
        continue;
      }
      if (++entries > 32768) throw new IllegalArgumentException("Snapshot entry limit");
      Object key = frame.iterator.getKey(), v = frame.iterator.getValue();
      if (!(key instanceof String) && !(key instanceof Number))
        throw new IllegalArgumentException("Primitive key required");
      if (key instanceof String s && s.length() > 128)
        throw new IllegalArgumentException("Key limit");
      if (key instanceof Number n) key = n.doubleValue();
      if (v instanceof KahluaTable t) {
        if (frame.depth >= 8 || t.size() > 16384)
          throw new IllegalArgumentException("Snapshot depth/array limit");
        for (Frame parent : stack)
          if (parent.source == t) throw new IllegalArgumentException("Cyclic snapshot");
        var target = new LinkedHashMap<Object, Object>();
        frame.target.put(key, target);
        stack.push(new Frame(t, t.iterator(), target, frame.depth + 1));
      } else if (v == null || v instanceof Boolean) frame.target.put(key, v);
      else if (v instanceof Number n && Double.isFinite(n.doubleValue()))
        frame.target.put(key, n.doubleValue());
      else if (v instanceof String s && s.length() <= 1024) frame.target.put(key, s);
      else throw new IllegalArgumentException("Only bounded primitive values permitted");
    }
    return stack.isEmpty();
  }

  static KahluaTable table(Object value) {
    KahluaTable t = zombie.Lua.LuaManager.platform.newTable();
    if (!(value instanceof Map<?, ?> map))
      throw new IllegalArgumentException("Expected detached map");
    for (var e : map.entrySet())
      t.rawset(e.getKey(), e.getValue() instanceof Map<?, ?> ? table(e.getValue()) : e.getValue());
    return t;
  }
}
