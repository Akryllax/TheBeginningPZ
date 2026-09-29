package net.akr.scenario.npc.core;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

/** A replacement planned from the committed endpoint waits for the moving body to join it. */
public final class CivilianRouteJoin {
  /** Return whether a new route still needs the body to reach its committed join tile. */
  public static boolean approaching(
      Result candidate, Result committed, boolean moving, Tile origin, Tile current) {
    return candidate != null
        && candidate.status() == Status.FOUND
        && moving
        && committed != null
        && committed.route().getLast().at().equals(origin)
        && candidate.route().stream().noneMatch(s -> s.at().equals(current));
  }
}
