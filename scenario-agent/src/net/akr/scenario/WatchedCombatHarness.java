package net.akr.scenario;

import java.nio.file.*;
import java.util.*;
import net.akr.scenario.npc.core.CivilianPool;
import se.krka.kahlua.vm.KahluaTable;
import zombie.*;
import zombie.Lua.LuaManager;
import zombie.ai.states.SwipeStatePlayer;
import zombie.characters.*;
import zombie.inventory.types.HandWeapon;
import zombie.iso.*;
import zombie.network.*;
import zombie.network.chat.ChatServer;

/** Disposable, watched native-contact probe. This is not the autonomous defense executor. */
final class WatchedCombatHarness {
  private static final float X = 10589.5f, Y = 10060.5f, VX = 10596.5f, VY = 10060.5f;
  private final String world, epoch;
  private final Path directory;
  private NativeCivilianActors actors;
  private CivilianPool<IsoPlayer> pool;
  private CivilianPool.Token token;
  private IsoPlayer actor;
  private IsoZombie target;
  private KahluaTable config;
  private long tick, started, phaseAt, published, lastMove, lastPosition;
  private int phase, packets, contacts;
  private float before, after, damage;
  private boolean finished, clientAttackFlag, clientSawReaction;
  private String failure = "";
  private final ProbeTiming work = new ProbeTiming();

  WatchedCombatHarness(Properties p, String world, String epoch, boolean server, boolean runtime) {
    if (!server || !runtime || !world.startsWith("AKR_DayOne_Test_") || world.contains("Headless"))
      throw new IllegalArgumentException("combat_requires_watched_disposable_runtime");
    for (String key : List.of("headless.enabled", "watched.enabled", "chase.enabled"))
      if (Boolean.parseBoolean(p.getProperty(key, "false")))
        throw new IllegalArgumentException("combat_exclusive_harness");
    this.world = world;
    this.epoch = epoch;
    directory = Path.of(p.getProperty("combat.directory", ""));
    if (!directory.isAbsolute() || !directory.normalize().startsWith("/run/akr"))
      throw new IllegalArgumentException("combat_directory");
  }

  private static void require(boolean value, String reason) {
    if (!value) throw new IllegalStateException(reason);
  }

  private void announce(String text) {
    System.out.println("[CivilianCombat] " + text);
    ChatServer.getInstance().sendServerAlertMessageToServerChat("[Civilian combat test] " + text);
  }

  private void next() {
    phase++;
    phaseAt = System.nanoTime();
    config.rawset("phase", (double) phase);
  }

  private IsoPlayer observer() {
    for (var p : GameServer.Players)
      if (p != null && "akr".equals(p.getUsername()) && p.getSquare() != null) {
        var c = GameServer.getConnectionFromPlayer(p);
        if (c != null && c.isFullyConnected()) return p;
      }
    return null;
  }

  private void protect(IsoPlayer p) {
    require(
        p.getRole() != null && "admin".equalsIgnoreCase(p.getRole().getName()),
        "observer_admin_required");
    require(p.getVehicle() == null, "observer_must_be_on_foot");
    if (!p.isGodMod() || !p.isInvisible() || !p.isGhostMode()) {
      p.setGodMod(true, true);
      p.setInvisible(true, true);
      p.setGhostMode(true, true);
      GameServer.sendPlayerExtraInfo(p, null, true);
    }
  }

  private KahluaTable report() {
    var r = LuaManager.env.rawget("AKRCombatReport");
    if (!(r instanceof KahluaTable t)
        || !epoch.equals(t.rawget("epoch"))
        || !(t.rawget("received_ms") instanceof Number n)
        || System.currentTimeMillis() - n.longValue() > 1500) return null;
    return t;
  }

  void tick() {
    if (finished) return;
    long now = System.nanoTime(), begin = now;
    try {
      if (!GameServer.server
          || !world.equals(GameServer.serverName)
          || IsoWorld.instance == null
          || IsoWorld.instance.currentCell == null
          || ServerMap.instance == null
          || ServerMap.instance.cellMap == null) return;
      if (started == 0) {
        started = phaseAt = now;
        actors = new NativeCivilianActors(IsoWorld.getWorldVersion(), true, 1);
        pool = new CivilianPool<>(epoch, actors, 1);
        config = LuaManager.platform.newTable();
        config.rawset("epoch", epoch);
        config.rawset("actor", 4096.0);
        config.rawset("phase", 0.0);
        LuaManager.env.rawset("AKRCombatWatch", config);
      }
      if (now - started > 900_000_000_000L) throw new IllegalStateException("combat_batch_timeout");
      if (phase >= 2 && now - phaseAt > 120_000_000_000L)
        throw new IllegalStateException("combat_phase_timeout:" + phase);
      for (int y = 9980; y <= 10220; y += 40)
        ServerMap.instance.characterIn(Math.floorDiv(10589, 8), Math.floorDiv(y, 8), 5);
      pool.tick(++tick, now);
      if (actor != null && phase < 7) actors.replicate(actor, 0, 0, false);
      var r = report();
      if (r != null) {
        clientAttackFlag |= Boolean.TRUE.equals(r.rawget("attack_animation_flag_seen"));
        clientSawReaction |= Boolean.TRUE.equals(r.rawget("saw_reaction"));
      }
      switch (phase) {
        case 0 -> {
          for (int[] xy :
              new int[][] {
                {10587, 9985}, {10589, 10060}, {10589, 10062}, {10596, 10060}, {10590, 10220}
              }) {
            var s = ServerMap.instance.getGridSquare(xy[0], xy[1], 0);
            if (s == null) return;
            require(
                s.TreatAsSolidFloor() && s.isFree(false),
                "fixture_square_blocked:" + xy[0] + "," + xy[1]);
          }
          require(
              LosUtil.lineClear(
                      IsoWorld.instance.currentCell,
                      (int) VX,
                      (int) VY,
                      0,
                      (int) X,
                      (int) Y,
                      0,
                      false)
                  == LosUtil.TestResults.Clear,
              "observer_los_blocked");
          if (actors.parkedCount() == 0) {
            actors.prewarmOne(10587, 9985, 0);
            return;
          }
          next();
        }
        case 1 -> {
          var p = observer();
          if (p == null) return;
          protect(p);
          GameTime.getInstance().setTimeOfDay(12);
          GameServer.sendWeather();
          announce(
              "Stage 1/1: one civilian with a hammer against one stationary zombie. Moving you to"
                  + " the clear viewing point; please stay there.");
          GameServer.sendTeleport(p, VX, VY, 0);
          lastPosition = now;
          next();
        }
        case 2 -> {
          var p = observer();
          if (p == null) return;
          protect(p);
          if (Math.hypot(p.getX() - VX, p.getY() - VY) > 2) {
            if (now - lastPosition > 3_000_000_000L) {
              GameServer.sendTeleport(p, VX, VY, 0);
              lastPosition = now;
            }
            return;
          }
          if (now - phaseAt < 10_000_000_000L) return;
          announce(
              "Spawning the visible combat fixture beside you. The strike will have a countdown.");
          actors.profile(
              "watched-fighter",
              new NativeCivilianActors.Profile("Hammer Civilian", "Generic01", false, X, Y, 0));
          token = pool.reserve("watched-fighter", 1, true, null);
          require(token != null, "reservation_failed");
          next();
        }
        case 3 -> {
          actor = pool.body(token);
          if (actor == null) return;
          require(
              pool.views().stream()
                  .anyMatch(v -> v.token().equals(token) && v.state() == CivilianPool.State.ACTIVE),
              "actor_not_active");
          var item = actor.getInventory().AddItem("Base.Hammer");
          require(item instanceof HandWeapon, "hammer_missing");
          actor.setPrimaryHandItem(item);
          var factory = VirtualZombieManager.instance;
          var saved = new ArrayList<>(factory.choices);
          factory.choices.clear();
          factory.choices.add(ServerMap.instance.getGridSquare(10589, 10062, 0));
          try {
            target =
                GameHooks.withSpawnPermit(
                    () -> factory.createRealZombieAlways(IsoDirections.N, false));
          } finally {
            factory.choices.clear();
            factory.choices.addAll(saved);
          }
          require(target != null && target.getOnlineID() >= 0, "target_spawn_failed");
          target.setUseless(true);
          target.setTarget(null);
          config.rawset("target", (double) target.getOnlineID());
          actors.replicate(actor, 0, 0, true);
          next();
        }
        case 4 -> {
          require(observer() != null, "observer_disconnected");
          protect(observer());
          if (r == null
              || !Boolean.TRUE.equals(r.rawget("actor_present"))
              || !Boolean.TRUE.equals(r.rawget("target_present"))) return;
          require(target.isAlive() && target.isStanding(), "target_not_standing");
          double distance = Math.hypot(target.getX() - actor.getX(), target.getY() - actor.getY());
          if (distance > 1.0) {
            double dx = (actor.getX() - target.getX()) / distance,
                dy = (actor.getY() - target.getY()) / distance;
            double dt = lastMove == 0 ? 0 : Math.min(.1, (now - lastMove) / 1e9);
            lastMove = now;
            require(
                actors.walk(
                    token,
                    actor,
                    (float) (target.getX() + dx * .9),
                    (float) (target.getY() + dy * .9),
                    .8,
                    dt),
                "approach_blocked");
            return;
          }
          actors.stop(actor);
          actor.setTargetAndCurrentDirection(
              target.getX() - actor.getX(), target.getY() - actor.getY());
          announce(
              "Hammer strike in 5 seconds. Watch the civilian's swing and the zombie's hit"
                  + " reaction.");
          next();
        }
        case 5 -> {
          if (now - phaseAt < 5_000_000_000L) return;
          require(
              r != null
                  && Boolean.TRUE.equals(r.rawget("target_present"))
                  && Boolean.TRUE.equals(r.rawget("actor_present")),
              "replica_stale");
          require(target.isAlive() && target.isStanding(), "target_no_longer_standing");
          float dx = target.getX() - actor.getX(),
              dy = target.getY() - actor.getY(),
              d = (float) Math.hypot(dx, dy);
          require(d > .2 && d < 1.1, "target_left_contact_range");
          require(
              IsoWorld.instance.currentCell.getObjectList().contains(target),
              "target_not_in_native_object_list");
          for (var p : GameServer.Players)
            require(
                Math.hypot(p.getX() - actor.getX(), p.getY() - actor.getY()) > 4,
                "observer_entered_contact_area");
          actor.setTargetAndCurrentDirection(dx / d, dy / d);
          before = target.getHealth();
          // The remote client consumes these exact values; it does not select them locally.
          actor.setAttackType(
              zombie.inventory.types.WeaponType.getWeaponType(actor)
                  .getPossibleAttack()
                  .getRandom());
          actor.setCombatSpeed(actor.calculateCombatSpeed());
          require(
              actor.getAttackType() != AttackType.NONE && actor.getCombatSpeed() > 0,
              "attack_presentation_not_prepared");
          actor.setNpc(true);
          NativeCombatRelay.begin(actor, target);
          try {
            actor.changeState(SwipeStatePlayer.instance());
            CombatManager.getInstance()
                .attackCollisionCheck(
                    actor, actor.getUseHandWeapon(), SwipeStatePlayer.instance(), AttackType.NONE);
          } finally {
            NativeCombatRelay.end();
            actor.setDefaultState();
            actor.clearHandToHandAttack();
            actor.setNpc(false);
          }
          contacts = NativeCombatRelay.contacts;
          packets = NativeCombatRelay.packets;
          damage = NativeCombatRelay.damage;
          after = target.getHealth();
          require(
              contacts == 1 && packets >= 1 && after < before, "native_contact_or_relay_failed");
          announce(
              "Strike sent through the stock hit packet. Holding both characters for 30 seconds;"
                  + " please check the animation, impact and sound.");
          next();
        }
        case 6 -> {
          if (now - phaseAt < 30_000_000_000L) return;
          announce(
              "Combat stage finished. Moving you away for cleanup. Server contact passed; visual"
                  + " confirmation is still required.");
          var p = observer();
          require(p != null, "observer_disconnected");
          GameServer.sendTeleport(p, 10590.5f, 10220.5f, 0);
          next();
        }
        case 7 -> {
          for (var p : GameServer.Players)
            if (p.getSquare() == null
                || Math.hypot(p.getX() - actor.getX(), p.getY() - actor.getY()) < 72) return;
          if (now - phaseAt < 5_000_000_000L) return;
          require(target.isAlive(), "target_corpse_retained");
          zombie.popman.NetworkZombiePacker.getInstance().deleteZombie(target);
          target.removeFromWorld();
          target.removeFromSquare();
          zombie.popman.NetworkZombiePacker.getInstance().setExtraUpdate();
          require(pool.retire(token), "actor_retirement_refused");
          next();
        }
        case 8 -> {
          var retired = pool.pollRetired();
          if (retired == null) return;
          actors.park(retired.token(), actor, retired.snapshot());
          actors.clearParked();
          require(pool.occupied() == 0 && actors.retainedBodies() == 0, "cleanup_incomplete");
          announce(
              "TEST COMPLETE: one native hammer contact; test resources cleaned up. Awaiting your"
                  + " visual feedback.");
          finished = true;
          next();
        }
      }
    } catch (Throwable error) {
      failure = error.getClass().getSimpleName() + ":" + Objects.toString(error.getMessage(), "");
      error.printStackTrace();
      finished = true;
      try {
        announce("TEST STOPPED: " + failure + ". Resources retained for diagnosis.");
      } catch (Exception ignored) {
      }
    } finally {
      work.add(System.nanoTime() - begin);
      if (started != 0 && (finished || now - published > 1_000_000_000L)) {
        published = now;
        try {
          publish();
        } catch (Exception error) {
          error.printStackTrace();
        }
      }
    }
  }

  private void publish() throws Exception {
    String json =
        "{\"world\":\""
            + world
            + "\",\"epoch\":\""
            + epoch
            + "\",\"status\":\""
            + (failure.isEmpty()
                ? (finished ? "contact_passed_visual_pending" : "running")
                : "failed")
            + "\",\"phase\":"
            + phase
            + ",\"failure\":\""
            + failure.replace("\\", "/").replace("\"", "'")
            + "\",\"contacts\":"
            + contacts
            + ",\"hit_packets\":"
            + packets
            + ",\"native_damage_argument\":"
            + damage
            + ",\"health_before\":"
            + before
            + ",\"health_after\":"
            + after
            + ",\"client_attack_animation_flag_seen\":"
            + clientAttackFlag
            + ",\"client_saw_reaction\":"
            + clientSawReaction
            + ",\"target_health_now\":"
            + (target == null || phase >= 8 ? "null" : Float.toString(target.getHealth()))
            + ",\"work_p95_ms\":"
            + work.percentile(.95)
            + ",\"work_max_ms\":"
            + work.max / 1e6
            + "}\n";
    Files.createDirectories(directory);
    Path temp = directory.resolve("combat-report.tmp");
    Files.writeString(temp, json);
    Files.move(
        temp,
        directory.resolve("combat-report.json"),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }
}
