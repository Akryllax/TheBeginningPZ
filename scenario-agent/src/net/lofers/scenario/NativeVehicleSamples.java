package net.lofers.scenario;

import java.util.*;

/** Bounded, complete native-body snapshot. Absence is distinct from lookup failure. */
final class NativeVehicleSamples {
  interface Source {
    int count();

    int read(int offset, float[] buffer);
  }

  record Motion(double vx, double vy) {}

  private final float[] buffer = new float[8192];
  private final Map<Integer, Motion> motions = new HashMap<>();

  String refresh(Source source) {
    motions.clear();
    int total = source.count();
    if (total < 0 || total > 64) return "native_vehicle_capacity";
    for (int offset = 0; offset < total; ) {
      Arrays.fill(buffer, Float.NaN);
      int count = source.read(offset, buffer);
      if (count < 1 || count > total - offset) return "native_vehicle_snapshot_incomplete";
      int at = 0;
      for (int n = 0; n < count; n++) {
        if (at + 14 > buffer.length) return "native_vehicle_snapshot_invalid";
        float id = buffer[at], wheels = buffer[at + 13];
        // The pinned JNI writer adds a fractional bias (observed +.1)
        // to integer-valued fields. Match the engine's truncating read.
        if (!Float.isFinite(id)
            || id < 0
            || id >= 32768
            || !Float.isFinite(wheels)
            || wheels < 0
            || wheels >= 5) return "native_vehicle_snapshot_header_id_" + id + "_wheels_" + wheels;
        int end = at + 14 + 4 * (int) wheels;
        if (end > buffer.length) return "native_vehicle_snapshot_invalid";
        for (int i = at; i < end; i++)
          if (!Float.isFinite(buffer[i]))
            return "native_vehicle_snapshot_nonfinite_field_" + (i - at);
        if (motions.put((int) id, new Motion(buffer[at + 8], buffer[at + 10])) != null)
          return "native_vehicle_snapshot_duplicate";
        at = end;
      }
      offset += count;
    }
    return source.count() == total ? "" : "native_vehicle_snapshot_changed";
  }

  Motion get(int id) {
    return motions.get(id);
  }
}
