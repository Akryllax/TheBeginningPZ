package net.akr.scenario;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

import java.util.*;
import net.akr.scenario.npc.core.CivilianCombat;
import net.akr.scenario.npc.core.CivilianPool;
import zombie.VirtualZombieManager;
import zombie.characters.*;
import zombie.inventory.types.HandWeapon;
import zombie.iso.*;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.network.*;
import zombie.popman.NetworkZombiePacker;

/** Deterministic geometry fixture around the production decision/actuation adapter. */
final class NativeSurvivalBatch {
  private final String epoch;
  private final Tile base;
  private final boolean watched;
  private NativeCivilianActors actors;
  private CivilianPool<IsoPlayer> pool;

  private static final class Case {
    CivilianPool.Token token;
    IsoPlayer body;
    IsoZombie zombie;
    NativeResidentController controller;
    final List<IsoDoor> doors = new ArrayList<>();
    float x, y;
    boolean opened, retiring, done, interrupted;
  }

  private final List<Case> cases = new ArrayList<>();
  private final Set<IsoPlayer> originals = Collections.newSetFromMap(new IdentityHashMap<>());
  private int phase, round;
  private long tick, phaseAt, lastReport;
  private boolean complete;
  int encounters, hits;
  final ProbeTiming work = new ProbeTiming();

  NativeSurvivalBatch(String epoch, Tile base) {
    this(epoch, base, false);
  }

  NativeSurvivalBatch(String epoch, Tile base, boolean watched) {
    this.epoch = epoch;
    this.base = base;
    this.watched = watched;
  }

  boolean prewarmed() {
    return phase >= 1;
  }

  void beginObserved(long now) {
    if (!watched || phase != 1 || !cases.isEmpty())
      throw new IllegalStateException("survival_not_ready");
    phaseAt = now;
  }

  void hold() {
    for (Case c : cases) if (c.body != null && !c.retiring) actors.replicate(c.body, 0, 0, false);
  }

  boolean awaitingClearance() {
    return watched
        && cases.stream()
            .anyMatch(
                c ->
                    c.opened && c.controller != null && c.controller.travelled >= 7 && !c.retiring);
  }

  private static void require(boolean ok, String why) {
    if (!ok) throw new IllegalStateException("survival:" + why);
  }

  private void next(long now) {
    phase++;
    phaseAt = now;
  }

  boolean tick(long now) throws Exception {
    if (complete) return true;
    long begin = System.nanoTime();
    try {
      if (phaseAt == 0) phaseAt = now;
      if (now - phaseAt > 120_000_000_000L)
        throw new IllegalStateException(
            "survival_phase_timeout:" + phase + ":round=" + round + ":states=" + summary());
      if (actors == null) {
        actors = new NativeCivilianActors(IsoWorld.getWorldVersion(), watched, 4);
        pool = new CivilianPool<>(epoch + "-survival", actors, 4);
      }
      pool.tick(++tick, now);
      if (now - lastReport > 5_000_000_000L) {
        lastReport = now;
        System.out.println(
            "[CivilianSurvival] phase=" + phase + " round=" + round + " " + summary());
      }
      switch (phase) {
        case 0 -> {
          if (actors.parkedCount() < 4) {
            actors.prewarmOne(base.x(), base.y(), 0);
            return false;
          }
          next(now);
        }
        case 1 -> {
          int count = round == 0 ? 1 : 4;
          if (cases.size() < count) {
            int i = cases.size();
            var c = new Case();
            c.x = base.x() + i * 5;
            c.y = base.y();
            cases.add(c);
            var square = ServerMap.instance.getGridSquare((int) c.x, (int) c.y, 0);
            require(square != null && square.isFree(false), "fixture_square");
            String id = "survivor-" + round + "-" + i;
            actors.profile(
                id,
                new NativeCivilianActors.Profile(
                    id, i % 2 == 0 ? "Generic01" : "Tourist", i % 2 != 0, c.x + .2f, c.y + .5f, 0));
            c.token = pool.reserve(id, 1, true, null);
            require(c.token != null, "reserve");
            return false;
          }
          for (Case c : cases) {
            c.body = pool.body(c.token);
            if (c.body == null) return false;
            if (round == 0 || originals.size() < 4) originals.add(c.body);
            else require(originals.contains(c.body), "constructed_during_reuse");
            if (c.zombie != null) continue;
            if (c.token.slot() % 2 == 0) {
              c.body.setPrimaryHandItem(c.body.getInventory().AddItem("Base.Hammer"));
              require(c.body.getPrimaryHandItem() instanceof HandWeapon, "hammer");
            }
            // Four genuinely locked native doors. Escape becomes possible only when the fixture
            // opens them.
            addDoor(c, (int) c.x, (int) c.y, true);
            addDoor(c, (int) c.x, (int) c.y, false);
            addDoor(c, (int) c.x + 1, (int) c.y, false);
            addDoor(c, (int) c.x, (int) c.y + 1, true);
            var factory = VirtualZombieManager.instance;
            var old = new ArrayList<>(factory.choices);
            factory.choices.clear();
            factory.choices.add(c.body.getSquare());
            try {
              c.zombie =
                  GameHooks.withSpawnPermit(
                      () -> factory.createRealZombieAlways(IsoDirections.W, false));
            } finally {
              factory.choices.clear();
              factory.choices.addAll(old);
            }
            require(c.zombie != null, "target_spawn");
            c.zombie.setUseless(true);
            c.zombie.setTarget(null);
            c.zombie.setX(c.x + .85f);
            c.zombie.setY(c.y + .5f);
            c.zombie.setCurrentSquareFromPosition();
            c.zombie.setMovingSquareNow();
          }
          next(now);
        }
        case 2 -> {
          if (watched) for (Case c : cases) actors.replicate(c.body, 0, 0, false);
          if (now - phaseAt < 2_000_000_000L) return false;
          for (Case c : cases) {
            c.controller =
                new NativeResidentController(
                    pool, actors, c.token, new Tile((int) c.x, (int) c.y + 8, 0));
          }
          next(now);
        }
        case 3 -> {
          boolean all = true;
          for (Case c : cases) {
            if (c.done) continue;
            if (c.retiring) {
              var receipt = pool.pollRetired();
              if (receipt != null) {
                var owner =
                    cases.stream()
                        .filter(a -> a.token.equals(receipt.token()))
                        .findFirst()
                        .orElseThrow();
                actors.park(receipt.token(), owner.body, receipt.snapshot());
                owner.done = true;
              }
              all = false;
              continue;
            }
            if (!watched
                && round > 0
                && !c.interrupted
                && c.controller.combat.action.phase() == CivilianCombat.Phase.WINDUP) {
              c.controller.combat.action.cancel("fixture_native_hit");
              c.body.setHitReaction("Bite");
              c.body.changeState(zombie.ai.states.PlayerHitReactionState.instance());
              c.controller.injuries.ownReaction(now);
              c.interrupted = true;
            }
            if (!watched && round > 0)
              c.body
                  .getBodyDamage()
                  .getBodyPart(zombie.characters.BodyDamage.BodyPartType.Hand_L)
                  .setAdditionalPain(40);
            c.controller.tick(now, true);
            require(!c.controller.unresolved(), "controller_unresolved");
            if (watched) actors.replicate(c.body, 0, 0, false);
            if (!c.opened
                && c.controller.combat.contacts > 0
                && !c.controller.combat.action.busy()) {
              require(c.controller.defenses > 0, "scripted_contact_without_decision");
              hits += c.controller.combat.contacts;
              if (!watched && round > 0)
                require(c.controller.combat.skippedUi > 0, "pain_ui_branch_not_exercised");
              for (var door : c.doors) {
                door.setLocked(false);
                door.setLockedByKey(false);
                if (!door.isOpen()) door.ToggleDoor(c.body);
              }
              c.controller.geometryChanged();
              c.opened = true;
            }
            if (c.opened && c.controller.travelled >= 7 && c.controller.fleeRoutes > 0) {
              c.controller.stop();
              if (watched && actors.visibleOrUnknown(c.body)) {
                all = false;
                continue;
              }
              c.controller.dispose();
              require(c.zombie.isAlive(), "target_death_owned");
              NetworkZombiePacker.getInstance().deleteZombie(c.zombie);
              c.zombie.removeFromWorld();
              c.zombie.removeFromSquare();
              NetworkZombiePacker.getInstance().setExtraUpdate();
              for (var door : c.doors) door.getSquare().transmitRemoveItemFromSquare(door);
              require(pool.retire(c.token), "retire");
              c.retiring = true;
              encounters++;
            }
            all = false;
          }
          if (all && pool.occupied() == 0) {
            require(actors.constructed == 4 && actors.parkedCount() == 4, "pool_leak");
            System.out.println(
                "[CivilianSurvival] round="
                    + round
                    + " complete encounters="
                    + encounters
                    + " native_contacts="
                    + hits
                    + " warm_bodies="
                    + actors.parkedCount());
            cases.clear();
            if (++round < (watched ? 1 : 4)) {
              phase = 1;
              phaseAt = now;
            } else next(now);
          }
        }
        case 4 -> {
          actors.clearParked();
          require(actors.retainedBodies() == 0 && pool.occupied() == 0, "final_cleanup");
          complete = true;
        }
      }
      return complete;
    } finally {
      work.add(System.nanoTime() - begin);
    }
  }

  private void addDoor(Case c, int x, int y, boolean north) {
    var square = ServerMap.instance.getGridSquare(x, y, 0);
    require(square != null, "door_square");
    var door =
        new IsoDoor(
            IsoWorld.instance.currentCell,
            square,
            IsoSpriteManager.instance.getSprite(
                north ? "fixtures_doors_01_1" : "fixtures_doors_01_0"),
            north);
    c.doors.add(door);
    door.setLocked(true);
    door.setLockedByKey(true);
    square.AddSpecialObject(door);
    square.RecalcAllWithNeighbours(true);
    if (watched) door.transmitCompleteItemToClients();
  }

  String summary() {
    return cases.stream()
        .map(
            c ->
                c.controller == null
                    ? "preparing"
                    : c.controller.state()
                        + "/def="
                        + c.controller.defenses
                        + "/hit="
                        + c.controller.combat.contacts
                        + "/travel="
                        + c.controller.travelled
                        + "/paths="
                        + c.controller.pathRequests
                        + "/xy="
                        + c.body.getX()
                        + ","
                        + c.body.getY()
                        + "/reason="
                        + c.controller.interruptReason()
                        + "/movement="
                        + actors.movementFailure(c.body)
                        + "/trace="
                        + c.controller.wireTrace())
        .toList()
        .toString();
  }

  short targetId() {
    return cases.isEmpty() || cases.getFirst().zombie == null
        ? -1
        : cases.getFirst().zombie.getOnlineID();
  }
}
