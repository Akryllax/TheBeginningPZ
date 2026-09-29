package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;

/**
 * Action sequencing shared by native and detached adapters. Engine observations complete actions.
 */
public final class CivilianTraversal {
  public static final int WINDOW = 8, REFILL = 4;

  public enum Cancellation {
    DEFER_TO_BOUNDARY,
    INTERRUPT_NOW
  }

  public enum Phase {
    IDLE,
    RUNNING,
    COMPLETE,
    BLOCKED,
    CANCELLED,
    UNRESOLVED
  }

  public enum Outcome {
    WORKING,
    COMPLETE,
    BLOCKED,
    DEAD
  }

  public record ActionKey(Key route, int step) {}

  public record View(Key key, int step, Phase phase, Action action, String reason) {}

  public interface Port {
    default void frame() {}

    default void endFrame() {}

    default void forecast(Result route, int next, Result continuation, boolean stopping) {}

    default void pause() {}

    default boolean at(Step step) {
      return false;
    }

    boolean supported(Action action);

    boolean valid(Step from, Step to);

    /** Idempotent for the action key; this is the only owner of authoritative effects. */
    void begin(ActionKey key, Step from, Step to) throws Exception;

    Outcome observe(ActionKey key) throws Exception;

    void stop(ActionKey key) throws Exception;
  }

  private final Port port;
  private Result route;
  private int step = 1;
  private Phase phase = Phase.IDLE;
  private boolean started, deferredCancel;
  private String reason = "";
  private Result replacement;
  private long completedEdges;

  public CivilianTraversal(Port port) {
    this.port = port;
  }

  public void start(Result route) {
    if (phase == Phase.RUNNING || phase == Phase.UNRESOLVED)
      throw new IllegalStateException("action_owned");
    if (route.status() != Status.FOUND || route.route().isEmpty())
      throw new IllegalArgumentException("route_required");
    this.route = route;
    step = 1;
    started = false;
    deferredCancel = false;
    replacement = null;
    reason = "";
    phase = route.route().size() == 1 ? Phase.COMPLETE : Phase.RUNNING;
  }

  public Result route() {
    return route;
  }

  public long completedEdges() {
    return completedEdges;
  }

  public boolean endingAtBoundary() {
    return deferredCancel;
  }

  public boolean hasReplacement() {
    return replacement != null;
  }

  public int buffered() {
    return phase == Phase.RUNNING
        ? (deferredCancel
            ? 1
            : route.route().size()
                - step
                + (replacement == null ? 0 : replacement.route().size() - 1))
        : 0;
  }

  public Step nextBoundary() {
    return phase == Phase.RUNNING ? route.route().get(step) : null;
  }

  public boolean canReplace(Result next) {
    if (phase != Phase.RUNNING
        || replacement != null
        || next.status() != Status.FOUND
        || next.route().isEmpty()
        || !next.key().actor().equals(route.key().actor())
        || next.key().actionRevision() <= route.key().actionRevision()) return false;
    for (int i = step - 1; i < (deferredCancel ? step + 1 : route.route().size()); i++)
      if (route.route().get(i).at().equals(next.route().getFirst().at())
          && (i >= step || port.at(next.route().getFirst()))) return true;
    return false;
  }

  /** Stage a bounded tail. The active native action and its clock remain owned by this port. */
  public boolean replaceAtBoundary(Result next) {
    if (!canReplace(next)) return false;
    int anchor = step - 1;
    while (!route.route().get(anchor).at().equals(next.route().getFirst().at())) anchor++;
    int prefix = Math.max(0, anchor - step + 1), capacity = WINDOW - prefix;
    if (capacity < 1) return false;
    deferredCancel = false;
    route =
        new Result(
            route.key(),
            route.status(),
            List.copyOf(route.route().subList(0, anchor + 1)),
            route.expansions(),
            route.cost());
    replacement =
        new Result(
            next.key(),
            next.status(),
            List.copyOf(next.route().subList(0, Math.min(next.route().size(), capacity + 1))),
            next.expansions(),
            next.cost());
    return true;
  }

  private void join() {
    if (started || replacement == null || !port.at(replacement.route().getFirst())) return;
    route = replacement;
    replacement = null;
    step = 1;
    started = false;
    phase = route.route().size() == 1 ? Phase.COMPLETE : Phase.RUNNING;
  }

  public void tick(Key current) {
    if (phase != Phase.RUNNING) return;
    if (!route.key().equals(current)) {
      cancel();
      return;
    }
    try {
      port.frame();
      join();
      if (phase == Phase.COMPLETE) {
        try {
          port.stop(new ActionKey(route.key(), step));
        } catch (Exception error) {
          phase = Phase.UNRESOLVED;
          reason = "stop_unconfirmed";
        }
        return;
      }
      for (int advanced = 0; advanced < 8 && phase == Phase.RUNNING; advanced++) {
        Step from = route.route().get(step - 1), to = route.route().get(step);
        ActionKey key = new ActionKey(route.key(), step);
        try {
          if (!started) {
            if (!port.supported(to.action())) {
              port.stop(key);
              phase = Phase.BLOCKED;
              reason = "native_traversal_unqualified:" + to.action();
              return;
            }
            if (!port.valid(from, to)) {
              port.stop(key);
              phase = Phase.BLOCKED;
              reason = "changed_obstacle";
              return;
            }
            // Set before calling: a thrown begin can already have applied an effect.
            started = true;
            port.begin(key, from, to);
            // Classify this edge before its first movement, not only after the prior edge.
            port.forecast(route, step, replacement, deferredCancel);
          }
          switch (port.observe(key)) {
            case WORKING -> {
              return;
            }
            case COMPLETE -> {
              started = false;
              completedEdges++;
              step++;
              if (deferredCancel) {
                replacement = null;
                port.stop(key);
                phase = Phase.CANCELLED;
              } else {
                join();
                if (step >= route.route().size()) {
                  port.stop(key);
                  phase = Phase.COMPLETE;
                }
              }
            }
            case BLOCKED -> {
              port.stop(key);
              started = false;
              phase = Phase.BLOCKED;
              reason = "blocked";
            }
            case DEAD -> {
              phase = Phase.UNRESOLVED;
              reason = "corpse_owned";
            }
          }
        } catch (Exception error) {
          phase = Phase.UNRESOLVED;
          reason = "native_action:" + error.getClass().getSimpleName();
        }
      }
    } finally {
      port.forecast(route, step, replacement, deferredCancel);
      port.endFrame();
    }
  }

  public void pause() {
    port.pause();
  }

  public void cancel(Cancellation policy) {
    replacement = null;
    if (policy == Cancellation.DEFER_TO_BOUNDARY && phase == Phase.RUNNING) {
      deferredCancel = true;
      return;
    }
    cancel();
  }

  public void cancel() {
    if (phase == Phase.UNRESOLVED) return;
    replacement = null;
    deferredCancel = false;
    try {
      if (started || phase == Phase.RUNNING) port.stop(new ActionKey(route.key(), step));
      started = false;
      phase = Phase.CANCELLED;
    } catch (Exception error) {
      phase = Phase.UNRESOLVED;
      reason = "stop_unconfirmed";
    }
  }

  /** Pure current state for late-observer replay, never a second application of enter effects. */
  public View view() {
    return new View(
        route == null ? null : route.key(),
        step,
        phase,
        route == null || step >= route.route().size() ? null : route.route().get(step).action(),
        reason);
  }
}
