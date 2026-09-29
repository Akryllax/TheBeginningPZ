package net.akr.scenario.npc.core;

import java.util.*;

/**
 * Detached layered graph and bounded deterministic A*. No engine references or background world
 * reads.
 */
public final class CivilianNavigation {
  public static final int MAX_NODES = 4096,
      MAX_EXPANSIONS = 2048,
      GLOBAL_EXPANSIONS = 128,
      MAX_ROUTE = 128;

  public enum Action {
    WALK,
    DOOR,
    STAIRS,
    WINDOW,
    LOW_FENCE,
    HIGH_WALL
  }

  public enum Status {
    PENDING,
    FOUND,
    NO_PATH,
    BUDGET_EXHAUSTED,
    STALE,
    CANCELLED
  }

  public record Tile(int x, int y, int z) implements Comparable<Tile> {
    @Override
    public int compareTo(Tile b) {
      int c = Integer.compare(z, b.z);
      if (c == 0) c = Integer.compare(y, b.y);
      return c == 0 ? Integer.compare(x, b.x) : c;
    }

    public double distance(Tile b) {
      return Math.sqrt(
          (double) (x - b.x) * (x - b.x)
              + (double) (y - b.y) * (y - b.y)
              + (double) (z - b.z) * (z - b.z));
    }
  }

  public record Edge(Tile to, Action action, double cost) {
    public Edge {
      if (to == null || action == null || !Double.isFinite(cost) || cost <= 0)
        throw new IllegalArgumentException("edge");
    }
  }

  public record Step(Tile at, Action action) {}

  public record Graph(long revision, Map<Tile, List<Edge>> edges, Map<Tile, Double> danger) {
    public Graph {
      if (edges.size() > MAX_NODES) throw new IllegalArgumentException("graph_budget");
      var copy = new TreeMap<Tile, List<Edge>>();
      for (var entry : edges.entrySet()) {
        if (entry.getValue().size() > 16) throw new IllegalArgumentException("edge_budget");
        var sorted = new ArrayList<>(entry.getValue());
        sorted.sort(Comparator.comparing(Edge::to).thenComparing(Edge::action));
        for (Edge edge : sorted) {
          if (!edges.containsKey(edge.to()) || edge.cost() < entry.getKey().distance(edge.to()))
            throw new IllegalArgumentException("invalid_edge");
          Tile a = entry.getKey(), b = edge.to();
          if (edge.action() == Action.WALK
              && (a.z() != b.z() || Math.abs(a.x() - b.x()) > 1 || Math.abs(a.y() - b.y()) > 1))
            throw new IllegalArgumentException("walk_edge");
          if (edge.action() == Action.WALK && a.x() != b.x() && a.y() != b.y()) {
            Tile h = new Tile(b.x(), a.y(), a.z()), v = new Tile(a.x(), b.y(), a.z());
            if (!walk(edges, a, h)
                || !walk(edges, a, v)
                || !walk(edges, h, b)
                || !walk(edges, v, b)) throw new IllegalArgumentException("corner_cut");
          }
        }
        copy.put(entry.getKey(), List.copyOf(sorted));
      }
      for (var entry : danger.entrySet())
        if (!edges.containsKey(entry.getKey())
            || !Double.isFinite(entry.getValue())
            || entry.getValue() < 0) throw new IllegalArgumentException("danger");
      edges = Collections.unmodifiableMap(copy);
      danger = Map.copyOf(danger);
    }

    private static boolean walk(Map<Tile, List<Edge>> edges, Tile a, Tile b) {
      return edges.getOrDefault(a, List.of()).stream()
          .anyMatch(e -> e.to().equals(b) && e.action() == Action.WALK);
    }
  }

  public record Key(CivilianPool.Token actor, long actionRevision, long mapRevision) {}

  public record Result(Key key, Status status, List<Step> route, int expansions, double cost) {}

  private record Open(Tile tile, double g, double f) {}

  public static final class Search {
    public final Key key;
    private final Graph graph;
    private final Tile goal;
    private final Set<Action> allowed;
    private final PriorityQueue<Open> open =
        new PriorityQueue<>(
            Comparator.comparingDouble(Open::f)
                .thenComparingDouble(Open::g)
                .thenComparing(Open::tile));
    private final Map<Tile, Double> best = new HashMap<>();
    private final Map<Tile, Step> parents = new HashMap<>();
    private Status status = Status.PENDING;
    private List<Step> route = List.of();
    private int expansions;
    private double cost;

    public Search(Key key, Graph graph, Tile start, Tile goal, Set<Action> allowed) {
      this.key = Objects.requireNonNull(key);
      this.graph = graph;
      this.goal = goal;
      this.allowed = Set.copyOf(allowed);
      if (key.mapRevision() != graph.revision()) status = Status.STALE;
      else if (!graph.edges().containsKey(start) || !graph.edges().containsKey(goal))
        status = Status.NO_PATH;
      else {
        best.put(start, 0d);
        open.add(new Open(start, 0, start.distance(goal)));
      }
    }

    /** Budget counts queue polls too, including stale entries. */
    public int advance(int budget, long mapRevision) {
      if (status != Status.PENDING) return 0;
      if (mapRevision != key.mapRevision()) {
        status = Status.STALE;
        return 0;
      }
      int used = 0;
      while (used < budget && !open.isEmpty() && status == Status.PENDING) {
        if (expansions >= MAX_EXPANSIONS) {
          status = Status.BUDGET_EXHAUSTED;
          break;
        }
        Open next = open.poll();
        used++;
        expansions++;
        if (next.g() != best.getOrDefault(next.tile(), Double.POSITIVE_INFINITY)) continue;
        if (next.tile().equals(goal)) {
          finish(next);
          break;
        }
        for (Edge edge : graph.edges().get(next.tile())) {
          if (!allowed.contains(edge.action())) continue;
          double g = next.g() + edge.cost() + graph.danger().getOrDefault(edge.to(), 0d);
          if (g >= best.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) continue;
          best.put(edge.to(), g);
          parents.put(edge.to(), new Step(next.tile(), edge.action()));
          open.add(new Open(edge.to(), g, g + edge.to().distance(goal)));
        }
      }
      if (status == Status.PENDING && open.isEmpty()) status = Status.NO_PATH;
      return used;
    }

    private void finish(Open next) {
      var reverse = new ArrayList<Step>();
      Tile at = goal;
      while (parents.containsKey(at)) {
        Step parent = parents.get(at);
        reverse.add(new Step(at, parent.action()));
        at = parent.at();
        if (reverse.size() >= MAX_ROUTE) {
          status = Status.BUDGET_EXHAUSTED;
          return;
        }
      }
      reverse.add(new Step(at, Action.WALK));
      Collections.reverse(reverse);
      route = List.copyOf(reverse);
      cost = next.g();
      status = Status.FOUND;
    }

    public void cancel() {
      if (status == Status.PENDING) status = Status.CANCELLED;
    }

    public Result result() {
      return new Result(key, status, route, expansions, cost);
    }
  }

  /** Four jobs, fair round-robin slices. Completed results remain bounded until consumed. */
  public static final class Queue {
    private final LinkedHashMap<CivilianPool.Token, Search> jobs = new LinkedHashMap<>();
    private int cursor;

    public boolean submit(Search search) {
      var actor = search.key.actor();
      if (!jobs.containsKey(actor) && jobs.size() >= 4) return false;
      Search old = jobs.put(actor, search);
      if (old != null) old.cancel();
      return true;
    }

    public int tick(long revision) {
      var list = new ArrayList<>(jobs.values());
      if (list.isEmpty()) return 0;
      int used = 0;
      for (int i = 0; i < list.size(); i++)
        used +=
            list.get((cursor + i) % list.size()).advance(GLOBAL_EXPANSIONS / list.size(), revision);
      cursor = (cursor + 1) % list.size();
      return used;
    }

    public Result poll(CivilianPool.Token actor) {
      Search s = jobs.get(actor);
      if (s == null || s.result().status() == Status.PENDING) return null;
      jobs.remove(actor);
      return s.result();
    }

    public void cancel(CivilianPool.Token actor) {
      Search s = jobs.remove(actor);
      if (s != null) s.cancel();
    }

    public int size() {
      return jobs.size();
    }
  }

  private CivilianNavigation() {}
}
