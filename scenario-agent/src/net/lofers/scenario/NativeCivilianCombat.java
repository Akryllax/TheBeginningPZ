package net.lofers.scenario;

import zombie.*;
import zombie.ai.states.SwipeStatePlayer;
import zombie.characters.*;
import zombie.inventory.types.*;
import zombie.iso.*;
import zombie.network.*;

/** Native execution for one managed body. World and generation checks precede every effect. */
public final class NativeCivilianCombat implements CivilianCombat.Port {
  private final CivilianPool<IsoPlayer> pool;
  private final CivilianPool.Token token;
  private final NativeCivilianActors actors;
  private final IsoPlayer actor;
  private IsoZombie target;
  private short targetId;
  private long targetGeneration;
  private HandWeapon weapon;
  private boolean oldNpc;
  private boolean prepared;

  private void publish(CivilianCombat.Key key, String stage) {
    var root = zombie.Lua.LuaManager.env.rawget("AKRCombatActions");
    se.krka.kahlua.vm.KahluaTable records;
    if (root instanceof se.krka.kahlua.vm.KahluaTable t) records = t;
    else {
      records = zombie.Lua.LuaManager.platform.newTable();
      zombie.Lua.LuaManager.env.rawset("AKRCombatActions", records);
    }
    var row = zombie.Lua.LuaManager.platform.newTable();
    row.rawset("actor", (double) token.slot());
    row.rawset("target", (double) targetId);
    row.rawset("action", token.epoch() + ":" + token.generation() + ":" + key.revision());
    row.rawset("stage", stage);
    row.rawset("at", (double) System.currentTimeMillis());
    records.rawset((double) token.slot(), row);
  }

  public final CivilianCombat action = new CivilianCombat(this);
  public int contacts, packets, skippedUi;
  public float before, after;

  public NativeCivilianCombat(
      CivilianPool<IsoPlayer> pool, NativeCivilianActors actors, CivilianPool.Token token) {
    this.pool = pool;
    this.actors = actors;
    this.token = token;
    actor = pool.body(token);
    if (actor == null) throw new IllegalArgumentException("actor_not_materialized");
  }

  public boolean defend(
      long revision, IsoZombie target, long generation, CivilianCombat.Style style, long now) {
    GameHooks.ownThread();
    if (action.busy() || action.phase() == CivilianCombat.Phase.UNRESOLVED) return false;
    this.target = target;
    targetId = target.getOnlineID();
    targetGeneration = generation;
    return action.begin(
        new CivilianCombat.Key(token, revision, Short.toString(targetId), generation), style, now);
  }

  @Override
  public boolean current(CivilianCombat.Key key) {
    return key.actor().equals(token)
        && pool.body(token) == actor
        && pool.current(token, key.revision())
        && target != null
        && key.targetGeneration() == targetGeneration
        && target.getOnlineID() == targetId
        && ServerMap.instance.zombieMap.get(targetId) == target;
  }

  @Override
  public boolean interrupted() {
    return actor.isDead()
        || actor.isOnFloor()
        || actor.isKnockedDown()
        || actor.isGrappling()
        || actor.getVehicle() != null
        || actor.getSquare() == null;
  }

  @Override
  public boolean contactAllowed(CivilianCombat.Key key) {
    if (!current(key)
        || interrupted()
        || !target.isAlive()
        || !target.isStanding()
        || target.getSquare() == null) return false;
    if (Math.abs(target.getZ() - actor.getZ()) > .1
        || Math.hypot(target.getX() - actor.getX(), target.getY() - actor.getY()) > 1.15)
      return false;
    var los =
        LosUtil.lineClear(
            actor.getCell(),
            (int) Math.floor(actor.getX()),
            (int) Math.floor(actor.getY()),
            (int) actor.getZ(),
            (int) Math.floor(target.getX()),
            (int) Math.floor(target.getY()),
            (int) target.getZ(),
            false);
    return los == LosUtil.TestResults.Clear;
  }

  @Override
  public CivilianCombat.Timing prepare(CivilianCombat.Key key, CivilianCombat.Style style) {
    actors.stop(actor);
    actor.setTargetAndCurrentDirection(target.getX() - actor.getX(), target.getY() - actor.getY());
    weapon =
        style == CivilianCombat.Style.SHOVE
            ? actor.bareHands
            : actor.getPrimaryHandItem() instanceof HandWeapon w ? w : null;
    if (weapon == null || weapon.isRanged() || weapon.getCondition() <= 0)
      throw new IllegalStateException("usable_melee_weapon_required");
    oldNpc = actor.isNpc();
    prepared = true;
    actor.setNpc(true);
    actor.setDoShove(style == CivilianCombat.Style.SHOVE);
    actor.setAttackType(
        (style == CivilianCombat.Style.SHOVE ? WeaponType.UNARMED : WeaponType.getWeaponType(actor))
            .getPossibleAttack()
            .getRandom());
    actor.setCombatSpeed(actor.calculateCombatSpeed());
    actor.changeState(SwipeStatePlayer.instance());
    actor.setDoShove(style == CivilianCombat.Style.SHOVE);
    actor.setUseHandWeapon(weapon);
    float speed = actor.getCombatSpeed();
    if (!Float.isFinite(speed) || speed <= 0) throw new IllegalStateException("combat_speed");
    publish(key, "windup");
    // Bounded action timing; visual contact alignment remains a watched acceptance check.
    double scale = 1 / Math.max(.5, Math.min(2, speed));
    return new CivilianCombat.Timing(
        (long) (350_000_000L * scale),
        (long) (650_000_000L * scale),
        (long) (1_500_000_000L * scale));
  }

  @Override
  public int contact(CivilianCombat.Key key, CivilianCombat.Style style) {
    GameHooks.ownThread();
    if (!contactAllowed(key)) return 0;
    if (style == CivilianCombat.Style.MELEE
        && (actor.getPrimaryHandItem() != weapon || weapon.getCondition() <= 0)) return 0;
    actor.setTargetAndCurrentDirection(target.getX() - actor.getX(), target.getY() - actor.getY());
    before = target.getHealth();
    NativeCombatRelay.begin(actor, target);
    try {
      CombatManager.getInstance()
          .attackCollisionCheck(actor, weapon, SwipeStatePlayer.instance(), AttackType.NONE);
    } finally {
      NativeCombatRelay.end();
    }
    after = target.getHealth();
    contacts += NativeCombatRelay.contacts;
    packets += NativeCombatRelay.packets;
    skippedUi += NativeCombatRelay.skippedUi;
    publish(key, "recovery");
    return NativeCombatRelay.contacts;
  }

  @Override
  public void finish(CivilianCombat.Key key) {
    GameHooks.ownThread();
    if (!prepared) return;
    // A reused body must never receive cleanup belonging to its previous resident.
    if (pool.body(token) != actor)
      throw new IllegalStateException("actor_generation_changed_during_attack");
    if (actor.getCurrentState() == SwipeStatePlayer.instance()) actor.setDefaultState();
    actor.clearHandToHandAttack();
    actor.setDoShove(false);
    actor.setNpc(oldNpc);
    prepared = false;
    actors.stop(actor);
    publish(key, "complete");
  }
}
