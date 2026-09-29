package net.lofers.scenario;

import java.util.*;

/** Detached admission calculation. Unknown/stale/moved observers never grant a spawn. */
final class OffscreenAdmission {
  record Observer(int id, double x, double y) {}

  record Report(
      int id, long received, long revision, double x, double y, boolean hidden, boolean loaded) {}

  static int owner(
      List<Observer> observers, List<Report> reports, long now, long revision, double x, double y) {
    if (observers.isEmpty() || observers.size() > 16 || reports.size() != observers.size())
      return -1;
    int result = -1;
    double best = Double.POSITIVE_INFINITY;
    Set<Integer> unique = new HashSet<>();
    for (Observer p : observers) {
      if (!unique.add(p.id())) return -1;
      List<Report> matches = reports.stream().filter(r -> r.id() == p.id()).toList();
      if (matches.size() != 1) return -1;
      Report r = matches.getFirst();
      if (r.revision() != revision
          || now < r.received()
          || now - r.received() > 750
          || !r.hidden()
          || !Double.isFinite(r.x())
          || !Double.isFinite(r.y())
          || !Double.isFinite(p.x())
          || !Double.isFinite(p.y())
          || Math.hypot(r.x() - p.x(), r.y() - p.y()) > 1) return -1;
      double d = Math.hypot(x - p.x(), y - p.y());
      if (r.loaded() && d < best) {
        result = p.id();
        best = d;
      }
    }
    return result;
  }
}
