package net.akr.scenario;

import java.util.ArrayDeque;

/** Transitional file adapter: FIFO starts; stop cancels all pending legacy commands. */
final class ProbeCommands {
  private final ArrayDeque<ProbeControl.Command> pending = new ArrayDeque<>();
  private ProbeControl.Command stop;

  synchronized boolean offer(ProbeControl.Command command) {
    if (command.action().equals("stop")) {
      stop = command;
      pending.clear();
      return true;
    }
    if (pending.size() >= 16) return false;
    pending.addLast(command);
    return true;
  }

  synchronized ProbeControl.Command poll(boolean allowStart) {
    if (stop != null) {
      var result = stop;
      stop = null;
      return result;
    }
    return allowStart ? pending.pollFirst() : null;
  }

  synchronized ProbeControl.Command peek() {
    return stop != null ? stop : pending.peekFirst();
  }
}
