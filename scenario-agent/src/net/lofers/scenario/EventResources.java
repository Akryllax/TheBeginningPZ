package net.lofers.scenario;

import java.util.*;

/** Detached ownership ledger. Only the resident adapter may confirm actual removals. */
final class EventResources {
  enum Kind {
    VEHICLE,
    FIXTURE,
    TERRAIN,
    RESERVATION,
    PEDESTRIAN,
    ACTOR,
    ZOMBIE,
    CORPSE
  }

  record Resource(Kind kind, String id) {
    Resource {
      Objects.requireNonNull(kind);
      if (id == null || id.isBlank() || id.length() > 128)
        throw new IllegalArgumentException("resource_id");
    }
  }

  private final LinkedHashSet<Resource> owned = new LinkedHashSet<>();

  void acquire(Resource resource) {
    if (owned.contains(resource)) return;
    long vehicles = owned.stream().filter(r -> r.kind() == Kind.VEHICLE).count();
    long entities = owned.stream().filter(r -> entity(r.kind())).count();
    long pedestrians =
        owned.stream().filter(r -> r.kind() == Kind.PEDESTRIAN || r.kind() == Kind.ACTOR).count();
    if (owned.size() >= 32
        || (resource.kind() == Kind.VEHICLE && vehicles >= 2)
        || ((resource.kind() == Kind.PEDESTRIAN || resource.kind() == Kind.ACTOR)
            && pedestrians >= 4)
        || (entity(resource.kind()) && entities >= 8))
      throw new IllegalStateException("resource_capacity");
    owned.add(resource);
  }

  /** Missing/stale observations must supply false, never evidence of absence. */
  boolean release(Resource resource, boolean worldRemoved, boolean nativeRemoved) {
    if (!owned.contains(resource)) throw new IllegalArgumentException("resource_not_owned");
    if (!worldRemoved
        || ((resource.kind() == Kind.VEHICLE
                || resource.kind() == Kind.PEDESTRIAN
                || resource.kind() == Kind.ACTOR
                || resource.kind() == Kind.ZOMBIE)
            && !nativeRemoved)) return false;
    owned.remove(resource);
    return true;
  }

  List<Resource> snapshot() {
    return List.copyOf(owned);
  }

  boolean empty() {
    return owned.isEmpty();
  }

  private static boolean entity(Kind kind) {
    return kind == Kind.VEHICLE
        || kind == Kind.FIXTURE
        || kind == Kind.PEDESTRIAN
        || kind == Kind.ACTOR
        || kind == Kind.ZOMBIE;
  }
}
