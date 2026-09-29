package net.lofers.scenario;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import zombie.ai.astar.Mover;
import zombie.characters.IsoPlayer;
import zombie.pathfind.*;
import zombie.pathfind.nativeCode.PathfindNative;

/**
 * Pinned path-only bridge. Does not call PathFindBehavior2.update or initialize a second solver.
 */
public final class NativeCivilianNavigation implements CivilianPathRequests.Driver<IsoPlayer> {
  private final Field flags;

  private static final class Flight {
    final boolean nativeBackend;

    Flight(boolean value) {
      nativeBackend = value;
    }
  }

  private final IdentityHashMap<IsoPlayer, Flight> backends = new IdentityHashMap<>();

  public NativeCivilianNavigation() {
    try {
      flags = PathNode.class.getDeclaredField("flags");
      flags.setAccessible(true);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("native_path_flags_contract", error);
    }
  }

  List<CivilianPathRequests.Point> copy(Path path) throws IllegalAccessException {
    if (path.size() > CivilianNavigation.MAX_ROUTE)
      throw new IllegalArgumentException("native_route_limit");
    var result = new ArrayList<CivilianPathRequests.Point>(path.size());
    for (int i = 0; i < path.size(); i++) {
      PathNode node = path.getNode(i);
      result.add(new CivilianPathRequests.Point(node.x, node.y, node.z, flags.getInt(node)));
    }
    return List.copyOf(result);
  }

  @Override
  public void request(
      IsoPlayer body,
      CivilianPathRequests.Point start,
      CivilianPathRequests.Point goal,
      Consumer<List<CivilianPathRequests.Point>> success,
      Runnable failed) {
    GameHooks.ownThread();
    boolean nativeBackend = PathfindNative.useNativeCode;
    if (backends.size() >= 4 && !backends.containsKey(body))
      throw new IllegalStateException("native_path_budget");
    Flight flight = new Flight(nativeBackend);
    backends.put(body, flight);
    IPathfinder callback =
        new IPathfinder() {
          public void Succeeded(Path path, Mover mover) {
            try {
              if (mover != body || path.size() > CivilianNavigation.MAX_ROUTE) {
                failed.run();
                return;
              }
              success.accept(copy(path));
            } catch (Exception error) {
              try {
                failed.run();
              } catch (RuntimeException ignored) {
                /* stock must release its pooled request */
              }
            } finally {
              backends.remove(body, flight);
            }
          }

          public void Failed(Mover mover) {
            try {
              failed.run();
            } catch (RuntimeException ignored) {
              /* nonthrowing callback */
            } finally {
              backends.remove(body, flight);
            }
          }
        };
    try {
      if (nativeBackend)
        PathfindNative.instance.addRequest(
            callback, body, start.x(), start.y(), start.z(), goal.x(), goal.y(), goal.z());
      else
        PolygonalMap2.instance.addRequest(
            callback, body, start.x(), start.y(), start.z(), goal.x(), goal.y(), goal.z());
    } catch (RuntimeException error) {
      cancel(body);
      throw error;
    }
  }

  @Override
  public void cancel(IsoPlayer body) {
    GameHooks.ownThread();
    Flight flight = backends.remove(body);
    if (flight == null) return;
    if (flight.nativeBackend) PathfindNative.instance.cancelRequest(body);
    else PolygonalMap2.instance.cancelRequest(body);
  }
}
