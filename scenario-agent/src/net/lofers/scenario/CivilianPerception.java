package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;

/** Fair local-square sampling, with explicit incomplete observations and expiring threat memory. */
public final class CivilianPerception {
  public record Threat(String id, Tile position, boolean visible, boolean dead) {}

  public record Page(boolean loaded, List<Threat> threats, boolean more) {
    public Page {
      threats = List.copyOf(threats);
      if (threats.size() > 8) throw new IllegalArgumentException("perception_page");
    }
  }

  public interface Source {
    Page read(Tile square, int offset, int maximum);
  }

  public record Observation(boolean known, List<Threat> threats, int cursor, long sampledAt) {}

  private record Seen(Threat threat, long at) {}

  private final Source source;
  private final Map<String, Seen> seen = new LinkedHashMap<>();
  private Tile origin;
  private int cursor, offset, squares, loaded;
  private boolean known, completedCycle;
  private long cycleStarted, lastSight = -1;
  private String rejection = "none";

  public String diagnostic(long now) {
    return "visible_age_ms="
        + (lastSight < 0 ? -1 : (now - lastSight) / 1_000_000)
        + ",remembered="
        + seen.size()
        + ",cursor="
        + cursor
        + ",last_rejection="
        + rejection;
  }

  public CivilianPerception(Source source) {
    this.source = source;
  }

  /** budget counts object pages, not whole lists. At most eight candidates per page. */
  public Observation sample(Tile position, int budget, long now) {
    if (origin == null
        || origin.z() != position.z()
        || (origin.distance(position) > 12 || (completedCycle && origin.distance(position) > 2))) {
      origin = position;
      cursor = offset = squares = loaded = 0;
      known = false;
      cycleStarted = now;
    }
    completedCycle = false;
    seen.entrySet()
        .removeIf(
            e -> {
              boolean expired = now - e.getValue().at() > 3_000_000_000L,
                  far = e.getValue().threat().position().distance(position) > 12;
              if (expired || far)
                rejection = expired ? "memory_expired" : "remembered_out_of_range";
              return expired || far;
            });
    for (int i = 0; i < budget; i++) {
      Tile tile =
          new Tile(origin.x() + cursor % 25 - 12, origin.y() + cursor / 25 - 12, origin.z());
      Page page = source.read(tile, offset, 8);
      if (!page.loaded()) {
        known = false;
        rejection = "unloaded_square";
      }
      if (offset == 0) {
        squares++;
        if (page.loaded()) loaded++;
      }
      for (Threat threat : page.threats()) {
        if (threat.dead() || !threat.visible() || position.distance(threat.position()) > 12) {
          rejection =
              threat.dead() ? "dead" : !threat.visible() ? "occluded" : "observed_out_of_range";
          seen.remove(threat.id());
          continue;
        }
        lastSight = now;
        if (seen.containsKey(threat.id()) || seen.size() < 32)
          seen.put(threat.id(), new Seen(threat, now));
        else {
          String farthest =
              seen.entrySet().stream()
                  .max(
                      Comparator.<Map.Entry<String, Seen>>comparingDouble(
                              e -> e.getValue().threat().position().distance(position))
                          .thenComparing(Map.Entry::getKey))
                  .orElseThrow()
                  .getKey();
          if (threat.position().distance(position)
              < seen.get(farthest).threat().position().distance(position)) {
            seen.remove(farthest);
            seen.put(threat.id(), new Seen(threat, now));
          }
        }
      }
      if (page.more()) offset += 8;
      else {
        offset = 0;
        if (++cursor == 625) {
          cursor = 0;
          completedCycle = true;
          known = loaded == squares && now - cycleStarted <= 3_000_000_000L;
          squares = loaded = 0;
          cycleStarted = now;
        }
      }
    }
    var threats =
        seen.values().stream().map(Seen::threat).sorted(Comparator.comparing(Threat::id)).toList();
    // An actual visible threat is actionable before the first full calm sweep completes.
    return new Observation(known || !threats.isEmpty(), threats, cursor, now);
  }
}
