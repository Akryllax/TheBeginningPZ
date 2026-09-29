package net.akr.scenario.npc.core;

/** Fixed, bounded test geometry. Rows retain their spacing for every 150-tile pass. */
public final class CivilianFormation {
  public final int count, columns, rows;

  /** Accept only the bounded watched batch sizes used by the test harness. */
  public CivilianFormation(int count) {
    if (count != 4 && count != 32 && count != 64)
      throw new IllegalArgumentException("watched_actors_must_be_4_32_or_64");
    this.count = count;
    columns = count >= 32 ? 8 : 4;
    rows = count / columns;
  }

  /** Return the protected spectator's horizontal viewing position. */
  public float viewX() {
    return count == 64 ? 10616.5f : 10596.5f;
  }

  /** Return the lane position for the indexed civilian. */
  public float x(int index) {
    check(index);
    return 10587.5f + index % columns;
  }

  /** Return the starting row for the indexed civilian. */
  public float startY(int index) {
    check(index);
    return 9985.5f + (index / columns - (rows - 1) / 2f) * 1.5f;
  }

  /** Return the 150-tile route endpoint for the indexed civilian. */
  public float endY(int index) {
    return startY(index) + 150;
  }

  private void check(int i) {
    if (i < 0 || i >= count) throw new IllegalArgumentException("formation_index");
  }
}
