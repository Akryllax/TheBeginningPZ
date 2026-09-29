package net.akr.scenario;

import java.util.*;

/**
 * Game-thread arbiter for up to four managed cars seeking the same passing space. Geometry and
 * fresh physical clearance must be supplied by the navigation adapter. Permission never overrides
 * collision checks or lets an expired occupant disappear.
 */
final class TrafficPassReservations {
  record Owner(long resident, long generation) {}

  record Request(
      Owner owner, double queuedAt, Set<ProbeRoute.Tile> corridor, boolean freshAndClear) {
    Request {
      Objects.requireNonNull(owner);
      corridor = Set.copyOf(corridor);
      if (owner.resident() < 0
          || owner.generation() < 0
          || !Double.isFinite(queuedAt)
          || queuedAt < 0
          || corridor.isEmpty()
          || corridor.size() > 256) throw new IllegalArgumentException("Invalid passing request");
    }
  }

  record Grant(Owner owner, long token) {}

  private static final double LEASE_SECONDS = 2;

  private static final class Lease {
    final Request request;
    final long token;
    double expires;
    boolean entered;

    Lease(Request request, long token, double expires) {
      this.request = request;
      this.token = token;
      this.expires = expires;
    }
  }

  private final Map<Long, Lease> leases = new HashMap<>();
  private long sequence;
  private double lastTime;

  private void advance(double now) {
    if (!Double.isFinite(now) || now < lastTime)
      throw new IllegalArgumentException("Non-monotonic reservation time");
    lastTime = now;
    // A lost heartbeat does not prove a car left the physical corridor.
    leases.values().removeIf(lease -> !lease.entered && lease.expires <= now);
  }

  List<Grant> resolve(List<Request> requests, double now) {
    advance(now);
    if (requests.size() > 4)
      throw new IllegalArgumentException("Managed moving fleet cap exceeded");
    var sorted = new ArrayList<>(requests);
    var residents = new HashSet<Long>();
    for (var request : sorted)
      if (request.queuedAt() > now || !residents.add(request.owner().resident()))
        throw new IllegalArgumentException("Invalid reservation batch");
    sorted.sort(
        Comparator.comparingDouble(Request::queuedAt).thenComparingLong(r -> r.owner().resident()));
    var grants = new ArrayList<Grant>();
    for (var request : sorted) {
      if (!request.freshAndClear()) continue;
      Lease existing = leases.get(request.owner().resident());
      if (existing != null) {
        if (existing.request.owner().equals(request.owner())
            && existing.request.corridor().equals(request.corridor())) {
          existing.expires = now + LEASE_SECONDS;
          grants.add(new Grant(request.owner(), existing.token));
        }
        continue;
      }
      if (leases.size() >= 4) continue;
      boolean conflict = false;
      for (var other : leases.values())
        if (!Collections.disjoint(request.corridor(), other.request.corridor())) {
          conflict = true;
          break;
        }
      if (conflict) continue;
      Lease lease = new Lease(request, ++sequence, now + LEASE_SECONDS);
      leases.put(request.owner().resident(), lease);
      grants.add(new Grant(request.owner(), lease.token));
    }
    return List.copyOf(grants);
  }

  boolean enter(Grant grant, double now) {
    advance(now);
    Lease lease = matching(grant);
    if (lease == null || lease.expires <= now) return false;
    lease.entered = true;
    return true;
  }

  boolean mayProceed(Grant grant, double now) {
    advance(now);
    Lease lease = matching(grant);
    return lease != null && lease.expires > now;
  }

  boolean release(Grant grant, boolean observedOutside, double now) {
    advance(now);
    Lease lease = matching(grant);
    if (lease == null || lease.entered && !observedOutside) return false;
    leases.remove(grant.owner().resident());
    return true;
  }

  private Lease matching(Grant grant) {
    Lease lease = leases.get(grant.owner().resident());
    return lease != null
            && lease.token == grant.token()
            && lease.request.owner().equals(grant.owner())
        ? lease
        : null;
  }
}
