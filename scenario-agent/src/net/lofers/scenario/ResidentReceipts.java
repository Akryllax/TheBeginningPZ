package net.lofers.scenario;

import java.util.*;

/**
 * Bounded delivery journal. Reserve before routine execution; only an IPC ack frees a slot. Never
 * owns physical resources, and never blocks local emergency actions or cleanup.
 */
final class ResidentReceipts {
  static final int LIMIT = 64;

  record Key(String resident, long generation, long planRevision, String action) {}

  record Outcome(Key key, String state, String reason) {}

  private final LinkedHashMap<Key, Outcome> entries = new LinkedHashMap<>();

  synchronized boolean reserve(Key key) {
    if (entries.containsKey(key)) return true;
    if (entries.size() >= LIMIT) return false;
    entries.put(key, null);
    return true;
  }

  synchronized void finish(Key key, String state, String reason) {
    if (!Set.of("completed", "cancelled", "failed").contains(state))
      throw new IllegalArgumentException("terminal_receipt");
    if (!reserve(key)) throw new IllegalStateException("unreserved_terminal_receipt");
    Outcome next = new Outcome(key, state, reason), prior = entries.get(key);
    if (prior != null && !prior.equals(next))
      throw new IllegalStateException("conflicting_terminal_receipt");
    entries.put(key, next);
  }

  synchronized Outcome peek() {
    for (Outcome value : entries.values()) if (value != null) return value;
    return null;
  }

  synchronized void acknowledge(Outcome outcome) {
    if (outcome.equals(entries.get(outcome.key()))) entries.remove(outcome.key());
  }

  synchronized int size() {
    return entries.size();
  }

  synchronized int pending() {
    return (int) entries.values().stream().filter(Objects::nonNull).count();
  }
}
