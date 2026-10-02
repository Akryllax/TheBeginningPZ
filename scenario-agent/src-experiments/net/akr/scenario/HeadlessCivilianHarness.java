package net.akr.scenario;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

import java.nio.file.*;
import java.util.*;
import net.akr.scenario.npc.core.CivilianPathRequests;
import net.akr.scenario.npc.core.CivilianPool;
import net.akr.scenario.npc.core.CivilianTraversal;
import zombie.AttackType;
import zombie.CombatManager;
import zombie.ai.states.SwipeStatePlayer;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.InventoryContainer;
import zombie.iso.*;
import zombie.iso.objects.IsoDeadBody;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.network.*;

/** Explicit opt-in native tests inside a real disposable dedicated server, with zero clients. */
final class HeadlessCivilianHarness {
  private final Path report;
  private final String world, epoch;
  private final List<String> checks = new ArrayList<>();
  private final List<CivilianPool.Token> tokens = new ArrayList<>();
  private final IdentityHashMap<IsoPlayer, CivilianPool.Token> bodies = new IdentityHashMap<>();
  private final Map<String, CivilianPool.Snapshot> residentBodies = new HashMap<>();
  private final Map<String, IsoPlayer> residentReferences = new HashMap<>();
  private final IdentityHashMap<IsoPlayer, CivilianPool.Token> previousAssignments =
      new IdentityHashMap<>();
  private final Map<String, Integer> residentItems = new HashMap<>();
  private NativeCivilianActors actors;
  private CivilianPool<IsoPlayer> pool;
  private CivilianPathRequests<IsoPlayer> paths;
  private NativeCivilianActors deathActors;
  private CivilianPool<IsoPlayer> deathPool;
  private NativeCivilianActors capacityActors;
  private NativeCivilianActors combatActors;
  private CivilianPool<IsoPlayer> combatPool;
  private CivilianPool.Token combatToken;
  private IsoPlayer combatBody;
  private IsoZombie combatZombie;
  private final boolean combatProbe, plannerEnabled;
  private NativeRoutineBatch routine;
  private final boolean houseEnabled;
  private NativeHouseRoutineBatch house;
  private final boolean survivalEnabled;
  private NativeSurvivalBatch survival;
  private CivilianPool.Token deathToken;
  private IsoPlayer originalDeadActor;
  private IsoDeadBody deathCorpse;
  private NativeResidentController deathController;
  private NativeResidentLifecycle lifecycle;
  private TerminalJournal terminalJournal;
  private int deathItemId;
  private ItemContainer deathInventory;
  private long deathTick;
  private CivilianTraversal traversal;
  private CivilianPool.Snapshot saved;
  private Tile start, goal;
  private long started, phaseAt, tick;
  private int phase, round, cancelStage;
  private boolean finished;
  private String failure = "";
  private int itemId, bagId, nestedId, worn;
  private long pathNanos;
  private double xBefore;
  private int doorSteps;
  private Tile doorFrom, doorTo;
  private IsoDoor door;
  private boolean doorWasOpen;
  private final ProbeTiming timing = new ProbeTiming();
  private final ProbeTiming walkingTiming = new ProbeTiming();

  HeadlessCivilianHarness(
      Properties p, String world, String epoch, boolean server, boolean runtime) {
    if (!server || !runtime || !world.startsWith("AKR_DayOne_Test_Headless_"))
      throw new IllegalArgumentException("headless_requires_isolated_runtime");
    report = Path.of(p.getProperty("headless.report", ""));
    if (!report.isAbsolute() || !report.startsWith("/run/akr"))
      throw new IllegalArgumentException("headless_report_path");
    this.world = world;
    this.epoch = epoch;
    houseEnabled = Boolean.parseBoolean(p.getProperty("headless.house", "false"));
    plannerEnabled = Boolean.parseBoolean(p.getProperty("headless.planner", "false"));
    combatProbe = Boolean.parseBoolean(p.getProperty("headless.combat_probe", "false"));
    survivalEnabled = Boolean.parseBoolean(p.getProperty("headless.survival", "false"));
  }

  private void pass(String name, String detail) {
    checks.add(
        "{\"name\":" + quote(name) + ",\"status\":\"passed\",\"detail\":" + quote(detail) + "}");
    System.out.println("[CivilianHeadless] PASS " + name + " " + detail);
  }

  private void next() {
    phase++;
    phaseAt = System.nanoTime();
    System.out.println("[CivilianHeadless] phase=" + phase);
  }

  void tick() {
    if (finished) return;
    long begin = System.nanoTime();
    try {
      if (!GameServer.server
          || !world.equals(GameServer.serverName)
          || IsoWorld.instance == null
          || IsoWorld.instance.currentCell == null
          || IsoWorld.instance.metaGrid == null
          || ServerMap.instance == null
          || ServerMap.instance.cellMap == null) return;
      if (started == 0) {
        started = phaseAt = begin;
        System.out.println("[CivilianHeadless] loading zero-client fixture");
      }
      if (!GameServer.Players.isEmpty()) throw new IllegalStateException("real_player_connected");
      if (begin - started
          > (survivalEnabled || plannerEnabled ? 600_000_000_000L : 240_000_000_000L))
        throw new IllegalStateException("batch_timeout_phase_" + phase);
      if (!plannerEnabled && phase != 23 && begin - phaseAt > 60_000_000_000L)
        throw new IllegalStateException("phase_timeout_" + phase);
      // Native ServerMap loading interest without a fake network player or account.
      // The integer overload accepts B42 chunk coordinates (8 tiles), not world tiles.
      if (!houseEnabled)
        ServerMap.instance.characterIn(Math.floorDiv(10756, 8), Math.floorDiv(9856, 8), 5);
      if (houseEnabled) {
        if (house == null) house = new NativeHouseRoutineBatch(epoch);
        if (house.tick(begin)) {
          pass("house_routine", house.summary());
          finish();
        }
        return;
      }
      if (plannerEnabled) {
        if (ServerMap.instance.getGridSquare(10756, 9856, 0) == null) return;
        if (routine == null) {
          findGround();
          if (start == null) return;
          routine = new NativeRoutineBatch(epoch, start);
        }
        if (routine.tick(begin)) {
          pass(
              "native_worker_routine_run_reaction",
              "worker_plans="
                  + routine.workerPlans
                  + " assignments="
                  + routine.assignments
                  + " "
                  + routine.strideResult()
                  + " initialized_bodies=4 last_run_tiles_per_second="
                  + routine.runSpeed
                  + " p95_ms="
                  + routine.work.percentile(.95));
          finish();
        }
        return;
      }
      if (pool != null) {
        pool.tick(++tick, begin);
        if (paths != null) paths.tick(tick, begin);
      }
      if (deathPool != null) deathPool.tick(++deathTick, begin);
      if (combatPool != null) combatPool.tick(++tick, begin);
      switch (phase) {
        case 0 -> {
          if (ServerMap.instance.getGridSquare(10756, 9856, 0) == null) return;
          findGround();
          if (start == null) return;
          if (actors == null) {
            actors = new NativeCivilianActors(IsoWorld.getWorldVersion(), false, 4);
            pool = new CivilianPool<>(epoch, actors, 4);
            paths =
                new CivilianPathRequests<>(
                    new NativeCivilianNavigation(),
                    key -> pool.current(key.actor(), key.actionRevision()));
          }
          if (actors.parkedCount() < 4) {
            actors.prewarmOne(start.x(), start.y(), start.z());
            return;
          }
          require(
              actors.constructed == 4 && GameServer.IDToPlayerMap.isEmpty(), "prewarm_world_leak");
          pass(
              "prewarm",
              "four initialized bodies; no network entries; no constructor after warmup");
          pass(
              "loaded_without_clients",
              "start=" + start + " goal=" + goal + " players=" + GameServer.Players.size());
          reserve("resident-0", start, null);
          next();
        }
        case 1 -> {
          IsoPlayer body = active(tokens.getFirst());
          if (body == null) return;
          pass(
              "materialize",
              "onlineId=" + body.getOnlineID() + " worn=" + body.getWornItems().size());
          pool.acceptIntent(tokens.getFirst(), 1);
          pathNanos = begin;
          paths.submit(key(), body, point(body), point(goal), begin);
          next();
        }
        case 2 -> {
          var reply = paths.poll(tokens.getFirst());
          if (reply == null) return;
          require(reply.status() == Status.FOUND, "native_path:" + reply.reason());
          var inspected = NativeCivilianGeometry.inspect(active(tokens.getFirst()), reply.points());
          require(inspected.reason().isEmpty(), "path_inspection:" + inspected.reason());
          require(
              inspected.steps().stream().allMatch(s -> s.action() == Action.WALK),
              "fixture_route_requires_traversal");
          pass(
              "native_path",
              "nodes=" + reply.points().size() + " callback_ms=" + (begin - pathNanos) / 1e6);
          traversal =
              new CivilianTraversal(
                  new NativeCivilianTraversal(
                      tokens.getFirst(), active(tokens.getFirst()), actors));
          traversal.start(new Result(key(), Status.FOUND, inspected.steps(), 0, 0));
          next();
        }
        case 3 -> {
          long moveAt = System.nanoTime();
          traversal.tick(key());
          walkingTiming.add(System.nanoTime() - moveAt);
          require(
              traversal.view().phase() != CivilianTraversal.Phase.UNRESOLVED
                  && traversal.view().phase() != CivilianTraversal.Phase.BLOCKED,
              "native_walk:" + traversal.view());
          if (traversal.view().phase() != CivilianTraversal.Phase.COMPLETE) return;
          IsoPlayer body = active(tokens.getFirst());
          require(
              Math.hypot(body.getX() - goal.x() - .5, body.getY() - goal.y() - .5) < .2,
              "walk_destination");
          if (cancelStage == 0) {
            pass(
                "native_walk",
                "position=" + body.getX() + "," + body.getY() + " stock collision respected");
            paths.submit(key(), body, point(body), point(start), begin);
            cancelStage = 1;
            return;
          }
          require(paths.outstanding() == 1, "native_cancel_request_not_inflight");
          paths.cancel(tokens.getFirst());
          require(paths.outstanding() == 0 && paths.size() == 0, "cancel_capacity");
          pass("native_cancel", "in-flight native request cancelled; queue released");
          InventoryItem item = body.getInventory().AddItem("Base.Hammer");
          require(item != null, "hammer_fixture");
          item.setCondition(3);
          itemId = item.getID();
          body.setPrimaryHandItem(item);
          InventoryItem bag = body.getInventory().AddItem("Base.Bag_Schoolbag");
          require(bag instanceof InventoryContainer, "bag_fixture");
          bagId = bag.getID();
          InventoryItem nested = ((InventoryContainer) bag).getInventory().AddItem("Base.Pen");
          require(nested != null, "nested_fixture");
          nestedId = nested.getID();
          body.getBodyDamage().getBodyParts().getFirst().setScratched(true, false);
          worn = body.getWornItems().size();
          require(pool.retire(tokens.getFirst()), "retire_denied");
          next();
        }
        case 4 -> {
          var retired = pool.pollRetired();
          if (retired == null) return;
          saved = retired.snapshot();
          require(saved != null, "snapshot_missing");
          park(retired);
          require(pool.occupied() == 0, "pool_leak");
          pass(
              "retire_and_snapshot",
              "bytes=" + saved.bytes().length + " worldVersion=" + saved.worldVersion());
          tokens.clear();
          reserve("resident-0", start, saved);
          next();
        }
        case 5 -> {
          IsoPlayer body = active(tokens.getFirst());
          if (body == null) return;
          InventoryItem item = body.getInventory().getItemWithID(itemId);
          require(item != null && item.getCondition() == 3, "item_roundtrip");
          require(
              body.getPrimaryHandItem() != null && body.getPrimaryHandItem().getID() == itemId,
              "equipped_item_roundtrip");
          InventoryItem bag = body.getInventory().getItemWithID(bagId);
          require(bag instanceof InventoryContainer, "bag_roundtrip");
          require(
              ((InventoryContainer) bag).getInventory().getItemWithID(nestedId) != null,
              "nested_id_roundtrip");
          require(body.getBodyDamage().getBodyParts().getFirst().scratched(), "wound_roundtrip");
          require(body.getWornItems().size() == worn, "clothing_roundtrip");
          pass(
              "body_roundtrip",
              "wound, item IDs, condition, equipped hammer, nested pen and clothing preserved");
          require(pool.retire(tokens.getFirst()), "retire_restored");
          next();
        }
        case 6 -> {
          var retired = pool.pollRetired();
          if (retired == null) return;
          park(retired);
          tokens.clear();
          reserveRound();
          next();
        }
        case 7 -> {
          for (var token : tokens) if (active(token) == null) return;
          require(pool.occupied() == 4, "four_admission");
          for (var token : tokens) {
            IsoPlayer body = active(token);
            String id = token.resident();
            if (round == 0) {
              residentReferences.put(id, body);
              InventoryItem item = body.getInventory().AddItem("Base.Pen");
              require(item != null, "pool_item_fixture");
              body.setPrimaryHandItem(item);
              residentItems.put(id, item.getID());
              body.getBodyDamage().getBodyParts().getFirst().setScratched(true, false);
              body.getKnownRecipes().add("HeadlessResidentRecipe");
              body.setAlreadyReadPages("HeadlessBook", 17);
              body.getReadLiterature().put("HeadlessLiterature", 3);
              body.getFitness().getRegularityMap().put("HeadlessExercise", 12f);
              body.setPerformingAnAction(true);
              boolean busyRejected = false;
              try {
                NativeActorReset.requireQuiescent(body);
              } catch (IllegalStateException expected) {
                busyRejected = true;
              }
              require(busyRejected, "busy_actor_reset_permitted");
              body.setPerformingAnAction(false);
              body.getModData().rawset("ResidentFixture", id);
              body.setSneaking(true);
            } else {
              require(residentReferences.containsValue(body), "reassignment_allocated_replacement");
              if (round == 1) {
                require(
                    body.getPrimaryHandItem() == null && body.getSecondaryHandItem() == null,
                    "old_equipment_leaked");
                require(
                    !body.getBodyDamage().getBodyParts().getFirst().scratched(),
                    "old_wound_leaked");
                require(
                    !body.getKnownRecipes().contains("HeadlessResidentRecipe"),
                    "old_recipe_leaked");
                require(body.getModData().rawget("ResidentFixture") == null, "old_moddata_leaked");
                require(!body.isSneaking(), "old_posture_leaked");
                require(body.getAlreadyReadPages("HeadlessBook") == 0, "old_book_leaked");
                require(
                    !body.getReadLiterature().containsKey("HeadlessLiterature"),
                    "old_literature_leaked");
                require(
                    !body.getFitness().getRegularityMap().containsKey("HeadlessExercise"),
                    "old_fitness_leaked");
                require(body.isFemale(), "new_resident_appearance_not_applied");
                for (int old : residentItems.values())
                  require(body.getInventory().getItemWithID(old) == null, "old_item_leaked");
              } else {
                require(
                    body != residentReferences.get(id), "restoration_did_not_change_physical_body");
                require(
                    body.getInventory().getItemWithID(residentItems.get(id)) != null,
                    "resident_inventory_not_restored");
                require(
                    body.getPrimaryHandItem() != null
                        && body.getPrimaryHandItem().getID() == residentItems.get(id),
                    "resident_hand_not_restored");
                require(
                    body.getBodyDamage().getBodyParts().getFirst().scratched(),
                    "resident_wound_not_restored");
                require(
                    id.equals(body.getModData().rawget("ResidentFixture")),
                    "resident_moddata_not_restored");
                require(
                    Collections.frequency(body.getKnownRecipes(), "HeadlessResidentRecipe") == 1,
                    "recipe_duplicated_or_missing");
                require(!body.isFemale(), "saved_resident_sex_not_restored");
                require(body.getAlreadyReadPages("HeadlessBook") == 17, "book_not_restored");
                require(
                    Integer.valueOf(3).equals(body.getReadLiterature().get("HeadlessLiterature")),
                    "literature_not_restored");
                require(
                    Float.valueOf(12f)
                        .equals(body.getFitness().getRegularityMap().get("HeadlessExercise")),
                    "fitness_not_restored");
              }
              boolean staleRejected = false;
              try {
                actors.walk(previousAssignments.get(body), body, body.getX(), body.getY(), 0, 0);
              } catch (IllegalArgumentException expected) {
                staleRejected = true;
              }
              require(staleRejected, "old_binding_controlled_reused_body");
              float before = body.getY();
              require(
                  actors.walk(token, body, body.getX(), before + .2f, 1, .1)
                      && body.getY() > before,
                  "reassigned_body_cannot_walk");
            }
            previousAssignments.put(body, token);
          }
          if (round > 0)
            pass(
                "cross_resident_reuse",
                "round="
                    + round
                    + " same four Java objects; new people clean, original residents restored onto"
                    + " different bodies; old tokens rejected");
          require(actors.constructed == 4, "constructor_during_gameplay");
          require(pool.reserve("overflow", 1, true, null) == null, "overflow_admitted");
          pass(
              "four_actor_pool",
              "round=" + round + " identities=" + GameServer.IDToPlayerMap.size());
          for (var token : tokens) require(pool.retire(token), "four_retire");
          next();
        }
        case 8 -> {
          CivilianPool.Retired retired;
          while ((retired = pool.pollRetired()) != null) {
            residentBodies.put(retired.token().resident(), retired.snapshot());
            park(retired);
          }
          if (pool.occupied() != 0) return;
          require(bodies.isEmpty(), "adapter_reference_leak");
          if (++round < 3) {
            tokens.clear();
            reserveRound();
            phase = 7;
            phaseAt = begin;
          } else {
            pass(
                "repeat_cleanup", "3 rounds; native world/network removal confirmed; zero clients");
            tokens.clear();
            next();
          }
        }
        case 9 -> {
          doorFrom = new Tile(start.x(), start.y() + 2, 0);
          doorTo = new Tile(start.x(), start.y() + 3, 0);
          var square = ServerMap.instance.getGridSquare(doorTo.x(), doorTo.y(), 0);
          require(
              square.getDoorTo(ServerMap.instance.getGridSquare(doorFrom.x(), doorFrom.y(), 0))
                  == null,
              "fixture_edge_occupied");
          var sprite = IsoSpriteManager.instance.getSprite("fixtures_doors_01_1");
          require(sprite != null, "door_sprite");
          // Same public native construction path as the game's timed-action fixture.
          door = new IsoDoor(IsoWorld.instance.currentCell, square, sprite, true);
          door.setLocked(false);
          door.setLockedByKey(false);
          square.AddSpecialObject(door);
          square.RecalcAllWithNeighbours(true);
          reserve("door-resident", doorFrom, null);
          next();
        }
        case 10 -> {
          IsoPlayer body = active(tokens.getFirst());
          if (body == null) return;
          require(door.couldBeOpen(body), "fixture_door_locked");
          doorWasOpen = door.isOpen();
          if (doorWasOpen) door.ToggleDoor(body);
          require(!door.isOpen(), "fixture_door_close");
          next();
        }
        case 11 -> {
          IsoPlayer body = active(tokens.getFirst());
          // Let the native collision map consume the door change before walking.
          if (begin - phaseAt < 500_000_000L) return;
          boolean moved =
              actors.walk(tokens.getFirst(), body, doorTo.x() + .5f, doorTo.y() + .5f, 1.45, .1);
          require(
              Math.hypot(body.getX() - doorTo.x() - .5, body.getY() - doorTo.y() - .5) > .15,
              "phased_through_closed_door");
          if (moved) {
            if (++doorSteps > 25) throw new IllegalStateException("closed_door_never_blocked");
            return;
          }
          pass("closed_door_collision", "stopped before crossing " + doorFrom + " -> " + doorTo);
          door.setLocked(true);
          door.setLockedByKey(true);
          require(
              !NativeCivilianGeometry.openDoor(body, doorFrom, doorTo) && !door.isOpen(),
              "locked_door_bypass");
          pass("locked_door_refusal", "no key or administrative bypass");
          door.setLocked(false);
          door.setLockedByKey(false);
          require(
              NativeCivilianGeometry.openDoor(body, doorFrom, doorTo), "native_door_open_failed");
          require(door.isOpen(), "native_door_still_closed");
          next();
        }
        case 12 -> {
          IsoPlayer body = active(tokens.getFirst());
          if (begin - phaseAt < 500_000_000L) return;
          require(
              actors.walk(tokens.getFirst(), body, doorTo.x() + .5f, doorTo.y() + .5f, 1.45, .1),
              "open_door_path_blocked");
          if (Math.hypot(body.getX() - doorTo.x() - .5, body.getY() - doorTo.y() - .5) > .15)
            return;
          pass("native_door_open_and_cross", "stock ToggleDoor then collision-constrained walk");
          if (!doorWasOpen && door.isOpen()) door.ToggleDoor(body);
          require(pool.retire(tokens.getFirst()), "door_actor_retire");
          next();
        }
        case 13 -> {
          var retired = pool.pollRetired();
          if (retired == null) return;
          park(retired);
          var square = door.getSquare();
          square.transmitRemoveItemFromSquare(door);
          require(
              !square.getObjects().contains(door) && !square.getSpecialObjects().contains(door),
              "fixture_door_cleanup");
          actors.clearParked();
          require(actors.retainedBodies() == 0, "retained_body_leak");
          require(pool.occupied() == 0 && bodies.isEmpty(), "final_cleanup");
          pass("final_cleanup", "zero owned Actors/world/network references");
          next();
        }
        case 14 -> {
          deathActors = new NativeCivilianActors(IsoWorld.getWorldVersion(), false, 1);
          deathPool = new CivilianPool<>(epoch + "-death", deathActors, 1);
          deathActors.prewarmOne(start.x(), start.y(), start.z());
          deathActors.profile(
              "death-resident",
              new NativeCivilianActors.Profile(
                  "Death Resident", "Generic01", false, start.x() + .5f, start.y() + .5f, 0));
          deathToken = deathPool.reserve("death-resident", 1, true, null);
          require(deathToken != null, "death_reservation");
          next();
        }
        case 15 -> {
          originalDeadActor = deathPool.body(deathToken);
          if (originalDeadActor == null) return;
          deathController = new NativeResidentController(deathPool, deathActors, deathToken, start);
          terminalJournal = new TerminalJournal(report.getParent().resolve("terminal"));
          lifecycle =
              new NativeResidentLifecycle(
                  world,
                  terminalJournal,
                  deathPool,
                  deathActors,
                  deathToken,
                  originalDeadActor,
                  deathController.logical());
          var bag =
              (InventoryContainer) originalDeadActor.getInventory().AddItem("Base.Bag_Schoolbag");
          bag.getInventory().AddItem("Base.Pencil");
          InventoryItem item = originalDeadActor.getInventory().AddItem("Base.Pen");
          require(item != null, "death_item");
          deathItemId = item.getID();
          // This disposable world may inherit a no-transmission sandbox. Exercise
          // stock infection/reanimation with a transmissible setting in this fixture.
          zombie.SandboxOptions.instance.lore.transmission.setValue(1);
          originalDeadActor.getBodyDamage().setInfected(true);
          originalDeadActor.getStats().set(zombie.characters.CharacterStat.ZOMBIE_INFECTION, .5f);
          require(
              originalDeadActor.shouldBecomeZombieAfterDeath(),
              "fixture_infection_not_transmissible");
          originalDeadActor.setHealth(0);
          deathController.tick(begin, true);
          require(deathController.state().equals("TERMINAL"), "death_controller_not_terminal");
          next();
        }
        case 16 -> {
          CivilianPool.Dead receipt = deathPool.pollDead();
          if (receipt == null) return;
          require(receipt.token().equals(deathToken), "death_receipt_identity");
          deathCorpse = deathActors.corpseFor(receipt.token(), originalDeadActor);
          require(deathCorpse != null, "native_corpse_missing");
          combatZombie = deathActors.reanimatedFor(receipt.token(), originalDeadActor);
          if (deathCorpse.getContainer() != null) {
            require(
                deathCorpse.getSquare() != null
                    && deathCorpse.getSquare().getStaticMovingObjects().contains(deathCorpse),
                "native_corpse_not_in_world");
            deathInventory = deathCorpse.getContainer();
            // The opt-in probe forces the fast-reanimation race before Actor
            // release; the normal run also accepts a naturally fast reanimation.
            if (combatProbe) {
              deathCorpse.reanimateNow();
              var revived = deathCorpse.reanimate();
              require(revived instanceof IsoZombie, "early_native_reanimation_failed");
              combatZombie = (IsoZombie) revived;
            }
          } else {
            require(combatZombie != null, "reanimated_zombie_missing");
            deathInventory = combatZombie.getInventory();
          }
          require(
              deathInventory != null && deathInventory.getItemWithID(deathItemId) != null,
              "native_corpse_inventory");
          if (combatZombie != null)
            require(
                combatZombie.getInventory() == deathInventory,
                "early_reanimation_container_transfer");
          require(deathCorpse.getReanimateTime() > 0, "native_infection_reanimation_not_scheduled");
          require(
              deathPool.occupied() == 1 && deathPool.reserve("early-reuse", 1, true, null) == null,
              "dead_actor_reused_before_receipt");
          lifecycle.tick(true);
          require(lifecycle.failure.isEmpty(), "terminal_coordinator:" + lifecycle.failure);
          if (!lifecycle.released) return;
          require(lifecycle.durable, "release_without_durable_receipt");
          deathController.dispose();
          pass(
              "durable_terminal_receipt",
              "resident terminal before release; nested inventory fingerprint retained; writer"
                  + " fsync acknowledged");
          require(
              deathActors.parkedCount() == 1 && deathPool.occupied() == 0, "dead_actor_not_parked");
          require(
              deathInventory.getItemWithID(deathItemId) != null,
              "corpse_lost_inventory_after_reset");
          pass(
              "native_corpse_handoff",
              "corpse keeps identity, same inventory item, native reanimation schedule; Actor"
                  + " released");
          deathActors.profile(
              "fresh-after-death",
              new NativeCivilianActors.Profile(
                  "Fresh Resident", "Generic01", true, start.x() + .5f, start.y() + .5f, 0));
          deathToken = deathPool.reserve("fresh-after-death", 1, true, null);
          require(deathToken != null, "replacement_slot");
          next();
        }
        case 17 -> {
          IsoPlayer fresh = deathPool.body(deathToken);
          if (fresh == null) return;
          require(
              fresh == originalDeadActor && fresh.isAlive(),
              "same_initialized_actor_not_reclaimed");
          require(
              fresh.getInventory().getItemWithID(deathItemId) == null,
              "dead_resident_item_leaked_to_replacement");
          require(
              "fresh-after-death".equals(fresh.getModData().rawget("AKRResidentId")),
              "replacement_identity");
          require(
              deathInventory.getItemWithID(deathItemId) != null,
              "corpse_item_changed_on_reassignment");
          pass(
              "dead_actor_reuse",
              "same engine object now a clean resident; old corpse inventory unchanged");
          require(deathPool.retire(deathToken), "fresh_retire");
          next();
        }
        case 18 -> {
          var receipt = deathPool.pollRetired();
          if (receipt == null) return;
          deathActors.park(receipt.token(), originalDeadActor, receipt.snapshot());
          deathActors.clearParked();
          require(
              deathPool.occupied() == 0 && deathActors.retainedBodies() == 0, "death_pool_cleanup");
          for (IsoZombie zombie : IsoWorld.instance.currentCell.getZombieList())
            if ("death-resident".equals(zombie.getModData().rawget("AKRResidentId"))
                && zombie.getInventory().getItemWithID(deathItemId) != null) combatZombie = zombie;
          if (combatZombie == null) {
            require(
                deathCorpse.getContainer() != null, "reanimation_container_already_transferred");
            deathCorpse.reanimateNow();
            var revived = deathCorpse.reanimate();
            require(revived instanceof IsoZombie, "native_reanimation_failed");
            combatZombie = (IsoZombie) revived;
          }
          require(
              combatZombie.getInventory().getItemWithID(deathItemId) != null,
              "reanimated_inventory_lost");
          require(
              "death-resident".equals(combatZombie.getModData().rawget("AKRResidentId")),
              "reanimated_identity_lost");
          require(lifecycle.zombie() == combatZombie, "native_reanimation_return_hook");
          lifecycle.verifyTransfer();
          require(
              TerminalJournal.unresolvedOnRestart(report.getParent().resolve("terminal")).size()
                  == 1,
              "terminal_restart_quarantine");
          terminalJournal.close();
          pass(
              "native_reanimation",
              "stock zombie inherits deceased identity and actual remaining inventory");
          next();
        }
        case 19 -> {
          if (capacityActors == null)
            capacityActors = new NativeCivilianActors(IsoWorld.getWorldVersion(), false, 32);
          if (capacityActors.parkedCount() < 32) {
            capacityActors.prewarmOne(start.x(), start.y(), start.z());
            return;
          }
          require(
              capacityActors.constructed == 32 && GameServer.IDToPlayerMap.isEmpty(),
              "capacity_prewarm_world_leak");
          capacityActors.clearParked();
          require(capacityActors.retainedBodies() == 0, "capacity_prewarm_cleanup");
          pass(
              "thirty_two_actor_prewarm",
              "32 native engine bodies initialized incrementally, parked off-world, then cleaned"
                  + " without network entries");
          if (combatProbe) next();
          else finish();
        }
        case 20 -> {
          require(
              combatZombie != null && combatZombie.isAlive() && combatZombie.getSquare() != null,
              "combat_target_missing");
          if (combatActors == null) {
            combatActors = new NativeCivilianActors(IsoWorld.getWorldVersion(), false, 1);
            combatPool = new CivilianPool<>(epoch + "-combat", combatActors, 1);
          }
          if (combatActors.parkedCount() < 1) {
            combatActors.prewarmOne(start.x(), start.y(), start.z());
            return;
          }
          IsoGridSquare spawn = null;
          int x = (int) Math.floor(combatZombie.getX()), y = (int) Math.floor(combatZombie.getY());
          for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var candidate = ServerMap.instance.getGridSquare(x + d[0], y + d[1], 0);
            if (candidate != null
                && candidate.isFree(false)
                && candidate.TreatAsSolidFloor()
                && zombie.iso.LosUtil.lineClear(
                        combatZombie.getCell(), candidate.x, candidate.y, 0, x, y, 0, false)
                    == zombie.iso.LosUtil.TestResults.Clear
                && !zombie.pathfind.PolygonalMap2.instance.lineClearCollide(
                    candidate.x + .5f,
                    candidate.y + .5f,
                    combatZombie.getX(),
                    combatZombie.getY(),
                    0,
                    null,
                    false,
                    true)) {
              spawn = candidate;
              break;
            }
          }
          require(spawn != null, "combat_spawn_square_missing");
          combatActors.profile(
              "combat-probe",
              new NativeCivilianActors.Profile(
                  "Combat Probe", "Generic01", false, spawn.x + .5f, spawn.y + .5f, 0));
          combatToken = combatPool.reserve("combat-probe", 1, true, null);
          require(combatToken != null, "combat_reservation");
          next();
        }
        case 21 -> {
          IsoPlayer attacker = combatPool.body(combatToken);
          if (attacker == null) return;
          combatBody = attacker;
          if (!IsoWorld.instance.currentCell.getObjectList().contains(combatZombie)) return;
          combatZombie.setOnFloor(false);
          combatZombie.setKnockedDown(false);
          InventoryItem item = attacker.getInventory().AddItem("Base.Hammer");
          require(item instanceof HandWeapon, "combat_weapon_missing");
          attacker.setPrimaryHandItem(item);
          float dx = combatZombie.getX() - attacker.getX(),
              dy = combatZombie.getY() - attacker.getY();
          float length = (float) Math.hypot(dx, dy);
          require(length > 0 && length < 2, "combat_contact_range");
          attacker.setTargetAndCurrentDirection(dx / length, dy / length);
          float healthBefore = combatZombie.getHealth();
          attacker.setNpc(true);
          try {
            attacker.changeState(SwipeStatePlayer.instance());
            HandWeapon weapon = attacker.getUseHandWeapon();
            require(weapon != null, "combat_weapon_not_selected");
            CombatManager.getInstance()
                .attackCollisionCheck(
                    attacker, weapon, SwipeStatePlayer.instance(), AttackType.NONE);
          } finally {
            attacker.setDefaultState();
            attacker.setNpc(false);
          }
          require(
              attacker.getLastHitCount() > 0,
              "combat_no_hit_candidates:distance="
                  + length
                  + ":standing="
                  + combatZombie.isStanding()
                  + ":range="
                  + ((HandWeapon) item).getMaxRange(attacker)
                  + ":dot="
                  + attacker.getDotWithForwardDirection(combatZombie.getX(), combatZombie.getY()));
          require(combatZombie.getHealth() < healthBefore, "combat_native_hit_not_applied");
          pass(
              "native_melee_contact_probe",
              "stock combat manager reduced zombie health from "
                  + healthBefore
                  + " to "
                  + combatZombie.getHealth());
          require(combatPool.retire(combatToken), "combat_probe_retire");
          next();
        }
        case 22 -> {
          var receipt = combatPool.pollRetired();
          if (receipt == null) return;
          require(receipt.token().equals(combatToken), "combat_retirement_receipt");
          combatActors.park(receipt.token(), combatBody, receipt.snapshot());
          require(
              combatPool.occupied() == 0 && combatActors.parkedCount() == 1,
              "combat_actor_not_parked");
          combatActors.clearParked();
          require(combatActors.retainedBodies() == 0, "combat_actor_cleanup");
          pass(
              "native_melee_probe_cleanup",
              "Actor removed from world/network and pool after stock hit");
          if (survivalEnabled) next();
          else finish();
        }
        case 23 -> {
          if (survival == null) survival = new NativeSurvivalBatch(epoch, start);
          if (survival.tick(begin)) {
            pass(
                "autonomous_survival_batch",
                "one then four residents, 4 waves, "
                    + survival.encounters
                    + " encounters, "
                    + survival.hits
                    + " native contacts; original 4 bodies reused; work p95_ms="
                    + survival.work.percentile(.95));
            finish();
          }
        }
        default -> throw new IllegalStateException("phase");
      }
    } catch (Throwable error) {
      failure = error.getClass().getSimpleName() + ":" + Objects.toString(error.getMessage(), "");
      error.printStackTrace();
      finish();
    } finally {
      timing.add(System.nanoTime() - begin);
    }
  }

  private IsoPlayer active(CivilianPool.Token token) {
    var view = pool.views().stream().filter(v -> token.equals(v.token())).findFirst().orElseThrow();
    if (view.state() == CivilianPool.State.UNRESOLVED)
      throw new IllegalStateException(view.reason());
    IsoPlayer body = pool.body(token);
    if (body != null) bodies.put(body, token);
    return view.state() == CivilianPool.State.ACTIVE ? body : null;
  }

  private void reserve(String id, Tile position, CivilianPool.Snapshot snapshot) {
    actors.profile(
        id,
        new NativeCivilianActors.Profile(
            id,
            "Generic01",
            id.startsWith("fresh-"),
            position.x() + .5f,
            position.y() + .5f,
            position.z()));
    var token = pool.reserve(id, snapshot == null ? 1 : 2, snapshot == null, snapshot);
    require(token != null, "pool_capacity");
    tokens.add(token);
  }

  private void reserveRound() {
    for (int i = 0; i < 4; i++) {
      String id = round == 1 ? "fresh-" + i : "crowd-" + ((i + (round == 2 ? 1 : 0)) % 4);
      reserve(id, new Tile(start.x(), start.y() + i * 2, start.z()), residentBodies.get(id));
    }
  }

  private void park(CivilianPool.Retired retired) throws Exception {
    var token = retired.token();
    IsoPlayer body =
        bodies.entrySet().stream()
            .filter(e -> token.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    var descriptor = body.getDescriptor();
    actors.park(token, body, retired.snapshot());
    bodies.remove(body);
    require(
        descriptor.getInstance() == null
            && !IsoWorld.instance.survivorDescriptors.containsValue(descriptor),
        "retired_descriptor_leaked");
  }

  private Key key() {
    return new Key(tokens.getFirst(), 1, 1);
  }

  private static CivilianPathRequests.Point point(IsoPlayer body) {
    return new CivilianPathRequests.Point(body.getX(), body.getY(), body.getZ(), 0);
  }

  private static CivilianPathRequests.Point point(Tile tile) {
    return new CivilianPathRequests.Point(tile.x() + .5f, tile.y() + .5f, tile.z(), 0);
  }

  private void findGround() {
    // Fixed small outdoor fixture; all four pool spawn squares and route must already be
    // loaded/free.
    for (int x = 10750; x < 10765; x++)
      for (int y = 9850; y < 9865; y++) {
        boolean free = true;
        for (int i = 0; i <= 8; i++) {
          var square = ServerMap.instance.getGridSquare(x, y + i, 0);
          if (square == null
              || !square.isFree(false)
              || !square.TreatAsSolidFloor()
              || square.HasStairs()) {
            free = false;
            break;
          }
        }
        if (free) {
          start = new Tile(x, y, 0);
          goal = new Tile(x, y + 8, 0);
          return;
        }
      }
  }

  private void finish() {
    if (finished) return;
    finished = true;
    try {
      var measuredActors = house == null ? actors : house.metricActors();
      String json =
          "{\"world\":"
              + quote(world)
              + ",\"epoch\":"
              + quote(epoch)
              + ",\"status\":"
              + quote(failure.isEmpty() ? "passed" : "failed")
              + ",\"failure\":"
              + quote(failure)
              + ",\"phase\":"
              + phase
              + ",\"clients\":0,\"cases\":["
              + String.join(",", checks)
              + "]"
              + ",\"work_p95_ms\":"
              + timing.percentile(.95)
              + ",\"work_p99_ms\":"
              + timing.percentile(.99)
              + ",\"walking_p95_ms\":"
              + walkingTiming.percentile(.95)
              + ",\"walking_p99_ms\":"
              + walkingTiming.percentile(.99)
              + ",\"materialize_p95_ms\":"
              + (measuredActors == null ? 0 : measuredActors.materializeTiming.percentile(.95))
              + ",\"cold_p95_ms\":"
              + (measuredActors == null ? 0 : measuredActors.coldTiming.percentile(.95))
              + ",\"warm_p95_ms\":"
              + (measuredActors == null ? 0 : measuredActors.warmTiming.percentile(.95))
              + ",\"warm_max_ms\":"
              + (measuredActors == null ? 0 : measuredActors.warmTiming.max / 1e6)
              + ",\"constructed\":"
              + (house != null
                  ? house.constructed()
                  : routine != null
                      ? routine.constructed()
                      : actors == null ? 0 : actors.constructed)
              + ",\"house_summary\":"
              + quote(house == null ? "not_run" : house.summary())
              + ",\"house_work_p95_ms\":"
              + (house == null ? 0 : house.work.percentile(.95))
              + ",\"house_work_p99_ms\":"
              + (house == null ? 0 : house.work.percentile(.99))
              + ",\"house_work_max_ms\":"
              + (house == null ? 0 : house.work.max / 1e6)
              + ",\"capacity_prewarmed\":"
              + (capacityActors == null ? 0 : capacityActors.constructed)
              + ",\"combat_probe\":"
              + combatProbe
              + ",\"combat_occupied\":"
              + (combatPool == null ? 0 : combatPool.occupied())
              + ",\"survival_encounters\":"
              + (survival == null ? 0 : survival.encounters)
              + ",\"survival_contacts\":"
              + (survival == null ? 0 : survival.hits)
              + ",\"survival_work_p95_ms\":"
              + (survival == null ? 0 : survival.work.percentile(.95))
              + ",\"survival_work_p99_ms\":"
              + (survival == null ? 0 : survival.work.percentile(.99))
              + ",\"reused\":"
              + (house != null
                  ? house.reused()
                  : routine != null ? routine.reused() : actors == null ? 0 : actors.reused)
              + ",\"parked\":"
              + (house != null
                  ? house.parked()
                  : routine != null ? routine.parked() : actors == null ? 0 : actors.parkedCount())
              + ",\"performance_gate\":\"short functional batch; release-cap qualification"
              + " pending\",\"occupied\":"
              + (house != null
                  ? house.occupied()
                  : routine != null ? routine.occupied() : pool == null ? 0 : pool.occupied())
              + ",\"pending_gates\":["
              + (survivalEnabled && failure.isEmpty() ? "" : "\"timed_attack_executor\",")
              + "\"timed_attack_visual_alignment\",\"incoming_zombie_contact\",\"combat_to_death_integration\",\"corpse_save_reload\",\"thirty_two_active_combat\",\"moving_obstacles\",\"stairs_climbing\",\"visual\",\"two_client_replication\",\"client_owned_hunters\"]}\n";
      Path temp = report.resolveSibling(report.getFileName() + ".tmp");
      Files.writeString(temp, json);
      Files.move(temp, report, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      System.out.println("[CivilianHeadless] FINISHED " + (failure.isEmpty() ? "passed" : failure));
    } catch (Exception error) {
      error.printStackTrace();
    }
  }

  private static void require(boolean value, String reason) {
    if (!value) throw new IllegalStateException(reason);
  }

  private static String quote(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        + "\"";
  }
}
