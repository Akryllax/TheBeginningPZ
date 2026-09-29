package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;
import java.util.function.LongSupplier;
import zombie.characters.IsoPlayer;
import zombie.network.ServerMap;

/** Loaded local adjacency; floor transitions are admitted only from a classified native route. */
public final class NativeCivilianGraphSource implements CivilianGraphCapture.Source {
  private static final int[][] NEIGHBORS = {
    {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };
  private final IsoPlayer actor;
  private final LongSupplier revision;
  private final Map<Tile, List<Edge>> transitions = new HashMap<>();
  private final List<Tile> threats;

  public NativeCivilianGraphSource(
      IsoPlayer actor, LongSupplier revision, List<Step> nativeRoute, List<Tile> threats) {
    if (nativeRoute.size() > MAX_ROUTE || threats.size() > 32)
      throw new IllegalArgumentException("source_budget");
    this.actor = actor;
    this.revision = revision;
    this.threats = List.copyOf(threats);
    for (int i = 1; i < nativeRoute.size(); i++) {
      Step from = nativeRoute.get(i - 1), to = nativeRoute.get(i);
      if (to.action() == Action.STAIRS)
        transitions
            .computeIfAbsent(from.at(), k -> new ArrayList<>())
            .add(new Edge(to.at(), to.action(), Math.max(from.at().distance(to.at()), 2)));
    }
  }

  @Override
  public long revision() {
    return revision.getAsLong();
  }

  @Override
  public boolean complete(Tile at) {
    for (int[] d : NEIGHBORS)
      if (ServerMap.instance.getGridSquare(at.x() + d[0], at.y() + d[1], at.z()) == null)
        return false;
    return true;
  }

  @Override
  public List<Edge> edges(Tile at) {
    GameHooks.ownThread();
    if (ServerMap.instance.getGridSquare(at.x(), at.y(), at.z()) == null) return null;
    var result = new ArrayList<Edge>();
    for (int[] offset : NEIGHBORS) {
      Tile to = new Tile(at.x() + offset[0], at.y() + offset[1], at.z());
      var passage = NativeCivilianGeometry.classify(actor, at, to);
      if (!passage.permitted() || passage.action() == Action.STAIRS) continue;
      double cost =
          switch (passage.action()) {
            case WALK -> at.distance(to);
            case DOOR -> 2;
            case WINDOW, LOW_FENCE -> 4;
            case HIGH_WALL -> 8;
            case STAIRS -> 2;
          };
      result.add(new Edge(to, passage.action(), cost));
    }
    for (Edge edge : transitions.getOrDefault(at, List.of())) {
      var passage = NativeCivilianGeometry.classify(actor, at, edge.to());
      if (passage.permitted() && passage.action() == Action.STAIRS) result.add(edge);
    }
    return result;
  }

  @Override
  public double danger(Tile at) {
    double risk = 0;
    for (Tile threat : threats) risk += Math.pow(Math.max(0, 12 - at.distance(threat)), 2) * .25;
    return risk;
  }
}
