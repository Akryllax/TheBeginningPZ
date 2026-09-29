package net.lofers.scenario;

/** Fixed-space, no-sort latency accounting. Percentiles are 0.1ms upper bounds. */
final class ProbeTiming {
  private final long[] buckets = new long[101];
  long count, total, max, over2, over5;

  void add(long nanos) {
    count++;
    total += nanos;
    max = Math.max(max, nanos);
    if (nanos > 2_000_000) over2++;
    if (nanos > 5_000_000) over5++;
    buckets[(int) Math.min(100, nanos / 100_000)]++;
  }

  double percentile(double quantile) {
    long threshold = (long) Math.ceil(count * quantile), seen = 0;
    if (count == 0) return 0;
    for (int i = 0; i < buckets.length; i++) {
      seen += buckets[i];
      if (seen >= threshold) return i == 100 ? max / 1_000_000.0 : (i + 1) / 10.0;
    }
    return max / 1_000_000.0;
  }
}
