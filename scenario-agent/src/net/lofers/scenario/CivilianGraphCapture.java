package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;

/** Incremental loaded-neighborhood capture. Source calls stay on the game thread. */
public final class CivilianGraphCapture {
  public interface Source {
    long revision();

    /** null means missing/unknown. No more than 16 detached outgoing edges. */
    List<Edge> edges(Tile tile);

    double danger(Tile tile);

    default boolean complete(Tile tile) {
      return true;
    }
  }

  private final Source source;
  private final Tile origin;
  private final long revision;
  private final int radius;
  private final ArrayDeque<Tile> pending = new ArrayDeque<>();
  private final Set<Tile> visited = new HashSet<>();
  private final Map<Tile, List<Edge>> edges = new TreeMap<>();
  private final Map<Tile, Double> danger = new HashMap<>();
  private boolean stale, limited, unknown;

  public CivilianGraphCapture(Source source, Tile origin) {
    this(source, origin, 16);
  }

  public CivilianGraphCapture(Source source, Tile origin, int radius) {
    if (radius < 1 || radius > 16) throw new IllegalArgumentException("capture_radius");
    this.radius = radius;
    this.source = source;
    this.origin = origin;
    revision = source.revision();
    pending.add(origin);
    visited.add(origin);
  }

  public int advance(int budget) {
    if (source.revision() != revision) {
      stale = true;
      pending.clear();
      return 0;
    }
    int used = 0;
    while (used < budget && !pending.isEmpty()) {
      Tile at = pending.remove();
      used++;
      List<Edge> observed = source.edges(at);
      if (observed == null) {
        unknown = true;
        continue;
      }
      if (!source.complete(at)) unknown = true;
      if (observed.size() > 16) throw new IllegalArgumentException("source_edge_budget");
      var accepted = new ArrayList<Edge>();
      for (Edge edge : observed) {
        Tile next = edge.to();
        if (Math.abs(next.x() - origin.x()) > radius || Math.abs(next.y() - origin.y()) > radius)
          continue;
        if (!visited.contains(next)) {
          if (visited.size() >= MAX_NODES) {
            limited = true;
            continue;
          }
          visited.add(next);
          pending.add(next);
        }
        accepted.add(edge);
      }
      edges.put(at, accepted);
      danger.put(at, source.danger(at));
    }
    return used;
  }

  public boolean complete() {
    return pending.isEmpty();
  }

  public boolean limited() {
    return limited;
  }

  public boolean unknown() {
    return unknown;
  }

  public Graph result() {
    if (stale || source.revision() != revision) throw new IllegalStateException("stale_geometry");
    if (!complete()) throw new IllegalStateException("capture_pending");
    var known = new TreeMap<Tile, List<Edge>>();
    for (var entry : edges.entrySet())
      known.put(
          entry.getKey(),
          entry.getValue().stream().filter(e -> edges.containsKey(e.to())).toList());
    return new Graph(revision, known, danger);
  }
}
