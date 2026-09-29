package net.akr.scenario.npc.core;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

import java.util.*;
import java.util.function.*;

/**
 * Bounded asynchronous solver admission. The driver invokes completions on the simulation thread.
 */
public final class CivilianPathRequests<B> {
  public record Point(float x, float y, float z, int flags) {
    public Point {
      if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
        throw new IllegalArgumentException("point");
    }
  }

  public record Reply(Key key, Status status, List<Point> points, String reason, long queueNanos) {
    public Reply {
      points = List.copyOf(points);
    }
  }

  public interface Driver<B> {
    void request(B body, Point start, Point goal, Consumer<List<Point>> success, Runnable failed);

    void cancel(B body);
  }

  private final class Job {
    final Key key;
    final B body;
    final Point start, goal;
    final long queued;
    long submitted;
    boolean running;
    Reply reply;

    Job(Key key, B body, Point start, Point goal, long now) {
      this.key = key;
      this.body = body;
      this.start = start;
      this.goal = goal;
      queued = now;
    }
  }

  private final Driver<B> driver;
  private final Predicate<Key> current;
  private final LinkedHashMap<CivilianPool.Token, Job> jobs = new LinkedHashMap<>();
  private final Map<Integer, Long> nextSubmission = new HashMap<>();
  private long lastTick = Long.MIN_VALUE;
  private int running;

  public CivilianPathRequests(Driver<B> driver, Predicate<Key> current) {
    this.driver = driver;
    this.current = current;
  }

  public boolean submit(Key key, B body, Point start, Point goal, long now) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(body);
    Objects.requireNonNull(start);
    Objects.requireNonNull(goal);
    if (key.actor() == null
        || key.actor().slot() < 4096
        || key.actor().slot() > 4099
        || !current.test(key)) return false;
    Job existing = jobs.get(key.actor());
    if (existing != null && existing.key.equals(key)) return true;
    // A different generation of the same physical slot must first cancel the old mover.
    for (Job old : new ArrayList<>(jobs.values()))
      if (old.key.actor().slot() == key.actor().slot()) cancel(old.key.actor());
    if (jobs.size() >= 4) return false;
    jobs.put(key.actor(), new Job(key, body, start, goal, now));
    return true;
  }

  public void tick(long tick, long now) {
    if (tick <= lastTick) return;
    lastTick = tick;
    for (Job j : new ArrayList<>(jobs.values())) {
      if (j.reply != null) continue;
      if (!current.test(j.key)) {
        finish(j, Status.STALE, List.of(), "stale", now);
        driver.cancel(j.body);
        continue;
      }
      if (j.running && now - j.submitted > 5_000_000_000L) {
        finish(j, Status.BUDGET_EXHAUSTED, List.of(), "native_timeout", now);
        driver.cancel(j.body);
      }
    }
    if (running >= 2) return;
    for (Job j : jobs.values()) {
      if (j.running
          || j.reply != null
          || now < nextSubmission.getOrDefault(j.key.actor().slot(), Long.MIN_VALUE)) continue;
      j.running = true;
      running++;
      j.submitted = now;
      nextSubmission.put(j.key.actor().slot(), now + 1_000_000_000L);
      try {
        driver.request(
            j.body,
            j.start,
            j.goal,
            points -> {
              // Never let a consumer throw through stock callback/release code.
              try {
                if (points == null || points.isEmpty() || points.size() > MAX_ROUTE)
                  finish(j, Status.BUDGET_EXHAUSTED, List.of(), "native_route_limit", now);
                else finish(j, Status.FOUND, List.copyOf(points), "", now);
              } catch (RuntimeException error) {
                finish(j, Status.NO_PATH, List.of(), "invalid_native_result", now);
              }
            },
            () -> finish(j, Status.NO_PATH, List.of(), "native_no_path", now));
      } catch (RuntimeException error) {
        finish(j, Status.NO_PATH, List.of(), "native_request_failed", now);
      }
      break;
    }
  }

  private void finish(Job j, Status status, List<Point> points, String reason, long now) {
    if (jobs.get(j.key.actor()) != j || j.reply != null) return;
    if (j.running) {
      j.running = false;
      running--;
    }
    boolean valid;
    try {
      valid = current.test(j.key);
    } catch (RuntimeException error) {
      valid = false;
    }
    j.reply =
        new Reply(
            j.key,
            valid ? status : Status.STALE,
            valid ? points : List.of(),
            valid ? reason : "stale",
            Math.max(0, (j.submitted == 0 ? now : j.submitted) - j.queued));
  }

  public Reply poll(CivilianPool.Token actor) {
    Job j = jobs.get(actor);
    if (j == null || j.reply == null) return null;
    jobs.remove(actor);
    return j.reply;
  }

  public void cancel(CivilianPool.Token actor) {
    Job j = jobs.get(actor);
    if (j == null) return;
    if (j.running) driver.cancel(j.body);
    jobs.remove(actor);
    if (j.running) running--;
  }

  public int outstanding() {
    return running;
  }

  public int size() {
    return jobs.size();
  }
}
