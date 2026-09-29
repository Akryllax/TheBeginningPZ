package net.akr.scenario.bridge;

import java.util.*;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

/** Cooperative inverse copy: no unbounded incoming plan allocation on a game tick. */
public final class PrimitiveWrite {
  record Frame(Iterator<? extends Map.Entry<?, ?>> iterator, KahluaTable target, int depth) {}

  private final Deque<Frame> frames = new ArrayDeque<>();
  public final KahluaTable result;
  int entries;

  /** Start incremental materialization of a detached worker result. */
  public PrimitiveWrite(Map<?, ?> source) {
    result = LuaManager.platform.newTable();
    frames.push(new Frame(source.entrySet().iterator(), result, 0));
  }

  /**
   * Write at most 256 values before {@code deadline} and resume on a later game tick.
   *
   * @param deadline monotonic {@link System#nanoTime()} deadline
   * @return true once the {@link #result} Lua table is complete
   */
  public boolean step(long deadline) {
    int operations = 0;
    while (!frames.isEmpty() && operations++ < 256 && System.nanoTime() < deadline) {
      Frame f = frames.peek();
      if (!f.iterator.hasNext()) {
        frames.pop();
        continue;
      }
      if (++entries > 32768) throw new IllegalArgumentException("Incoming snapshot entry limit");
      var e = f.iterator.next();
      Object value = e.getValue();
      if (value instanceof Map<?, ?> map) {
        if (f.depth >= 8) throw new IllegalArgumentException("Incoming depth limit");
        KahluaTable t = LuaManager.platform.newTable();
        f.target.rawset(e.getKey(), t);
        frames.push(new Frame(map.entrySet().iterator(), t, f.depth + 1));
      } else f.target.rawset(e.getKey(), value);
    }
    return frames.isEmpty();
  }
}
