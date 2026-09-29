package net.akr.scenario;

import net.akr.scenario.npc.core.CivilianBite;
import net.akr.scenario.npc.core.CivilianCombat;
import net.akr.scenario.npc.core.CivilianPool;

final class CivilianCombatFixture {
  private static final class Port implements CivilianCombat.Port {
    boolean current = true, interrupted, allowed = true, fail;
    int starts, hits, finishes;

    public boolean current(CivilianCombat.Key k) {
      return current;
    }

    public boolean interrupted() {
      return interrupted;
    }

    public boolean contactAllowed(CivilianCombat.Key k) {
      return allowed;
    }

    public CivilianCombat.Timing prepare(CivilianCombat.Key k, CivilianCombat.Style s) {
      starts++;
      return new CivilianCombat.Timing(350, 650, 1500);
    }

    public int contact(CivilianCombat.Key k, CivilianCombat.Style s) {
      hits++;
      if (fail) throw new IllegalStateException("after_effect");
      return 1;
    }

    public void finish(CivilianCombat.Key k) {
      finishes++;
    }
  }

  private static CivilianCombat.Key key(int n) {
    return new CivilianCombat.Key(
        new CivilianPool.Token("boot", 4096, 1, "resident", 1), n, "zombie", 1);
  }

  static void run() {
    var bite = new CivilianBite();
    Object owner = new Object(), replacement = new Object();
    assert !bite.contact(0, owner, false, true, true);
    assert !bite.contact(10, owner, true, true, true);
    assert !bite.contact(400_000_000, owner, true, false, true);
    assert !bite.contact(410_000_000, owner, true, true, false);
    assert bite.contact(420_000_000, owner, true, true, true);
    assert !bite.contact(800_000_000, owner, true, true, true);
    assert !bite.contact(900_000_000, replacement, true, true, true);
    assert !bite.contact(1_300_000_000, replacement, true, true, true);
    assert !bite.contact(1_400_000_000, replacement, false, true, true);
    assert !bite.contact(1_500_000_000, replacement, true, true, true);
    assert bite.contact(1_900_000_000, replacement, true, true, true);
    Port p = new Port();
    var c = new CivilianCombat(p);
    assert c.begin(key(1), CivilianCombat.Style.MELEE, 0);
    c.tick(349);
    assert p.hits == 0;
    c.tick(350);
    c.tick(350);
    c.tick(900);
    assert p.hits == 1 && c.contacts() == 1;
    c.tick(1000);
    assert c.phase() == CivilianCombat.Phase.COMPLETE && p.finishes == 1;
    assert !c.begin(key(1), CivilianCombat.Style.MELEE, 1600);
    assert !c.begin(key(2), CivilianCombat.Style.MELEE, 1100);
    assert c.begin(key(2), CivilianCombat.Style.SHOVE, 1700);
    p.allowed = false;
    c.tick(2100);
    assert p.hits == 1;
    c.tick(2700);
    assert c.reason().equals("target_left_contact");
    p.allowed = true;
    assert c.begin(key(3), CivilianCombat.Style.MELEE, 3300);
    p.current = false;
    c.tick(3700);
    assert c.phase() == CivilianCombat.Phase.CANCELLED && p.hits == 1;
    p.current = true;
    assert c.begin(key(4), CivilianCombat.Style.MELEE, 5000);
    p.interrupted = true;
    c.tick(5100);
    assert c.phase() == CivilianCombat.Phase.CANCELLED && p.hits == 1;
    p.interrupted = false;
    p.fail = true;
    assert c.begin(key(5), CivilianCombat.Style.MELEE, 6600);
    c.tick(7000);
    c.tick(9000);
    assert c.phase() == CivilianCombat.Phase.UNRESOLVED && p.hits == 2;
    assert !c.begin(key(6), CivilianCombat.Style.MELEE, 10000);
    System.out.println(
        "Combat action: windup, once-only contact, range loss, cooldown, stale generation,"
            + " interruption and uncertain-effect quarantine passed");
  }
}
