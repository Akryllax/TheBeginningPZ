package net.akr.scenario.npc.core;

/** Per-zombie attack-entry ledger. An owner change during an attack consumes that entry. */
public final class CivilianBite {
  private Object owner;
  private boolean initialized, attacking, spent;
  private long entered, last = Long.MIN_VALUE;

  public boolean contact(
      long now, Object owner, boolean attack, boolean fresh, boolean targetAndReach) {
    if (now < last) return false;
    last = now;
    if (!initialized || this.owner != owner) {
      initialized = true;
      this.owner = owner;
      attacking = attack;
      spent = attack;
      entered = now;
      return false;
    }
    if (!attack) {
      attacking = false;
      spent = false;
      return false;
    }
    if (!attacking) {
      attacking = true;
      spent = false;
      entered = now;
    }
    if (spent || !fresh || !targetAndReach || now - entered < 350_000_000L) return false;
    spent = true;
    return true; // spent before applying an uncertain native effect
  }
}
