package net.akr.scenario;

import java.nio.file.*;
import java.util.*;
import net.akr.scenario.npc.core.CivilianPool;
import net.akr.scenario.npc.core.OffscreenAdmission;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.iso.*;
import zombie.network.*;
import zombie.network.chat.ChatServer;
import zombie.pathfind.PolygonalMap2;
import zombie.popman.NetworkZombieManager;

/**
 * Opt-in one-pair staging qualification. No ambient activation, client JVM or custom transforms.
 */
final class OffscreenChaseHarness {
  private record Candidate(float x, float y, int dir) {}

  private final List<Candidate> candidates = new ArrayList<>();
  private final String epoch, world;
  private final Path dir;
  private final ProbeTiming timing = new ProbeTiming();
  private NativeCivilianActors nativeActors;
  private CivilianPool<IsoPlayer> pool;
  private CivilianPool.Token token;
  private IsoPlayer actor;
  private WatchedHunters hunters;
  private KahluaTable config, resident;
  private int phase, scan, selected = -1, ownerId = -1;
  private long tick, phaseAt, last, published, quiet;
  private boolean finished, seenActor, seenHunter, earlyReveal, primedHidden, positioned, retired;
  private String failure = "", waiting = "warming", residentId = "";
  private double observerMoved;
  private float observerX, observerY;
  private int constructedAtAssignment;

  OffscreenChaseHarness(Properties p, String world, String epoch, boolean server, boolean runtime) {
    if (!server || !runtime || !world.startsWith("AKR_DayOne_Test_") || world.contains("Headless"))
      throw new IllegalArgumentException("chase_disposable_only");
    this.world = world;
    this.epoch = epoch;
    dir = Path.of(p.getProperty("chase.directory", ""));
    if (!dir.isAbsolute() || !dir.normalize().startsWith("/run/akr"))
      throw new IllegalArgumentException("chase_directory");
    for (int d : new int[] {1, -1})
      for (int distance : new int[] {32, 40, 48})
        candidates.add(new Candidate(10590.5f, 10060.5f - distance * d, d));
  }

  void tick() {
    if (finished
        || !GameServer.server
        || !world.equals(GameServer.serverName)
        || IsoWorld.instance == null
        || IsoWorld.instance.currentCell == null
        || ServerMap.instance == null) return;
    long now = System.nanoTime(), begin = now;
    try {
      GameHooks.ownThread();
      for (int y = 9940; y <= 10200; y += 40) ServerMap.instance.characterIn(10590 / 8, y / 8, 5);
      if (config == null) {
        phaseAt = last = now;
        config = LuaManager.platform.newTable();
        config.rawset("epoch", epoch);
        config.rawset("revision", 1.0);
        KahluaTable cs = LuaManager.platform.newTable();
        for (int i = 0; i < candidates.size(); i++) {
          var c = candidates.get(i);
          var t = LuaManager.platform.newTable();
          t.rawset("x", (double) c.x);
          t.rawset("y", (double) c.y);
          t.rawset("dir", (double) c.dir);
          cs.rawset((double) i + 1, t);
        }
        config.rawset("candidates", cs);
        LuaManager.env.rawset("AKRChase", config);
        nativeActors =
            new NativeCivilianActors(
                IsoWorld.getWorldVersion(),
                false,
                4,
                new NativeCivilianActors.StagingPolicy() {
                  public boolean admit(CivilianPool.Token t, NativeCivilianActors.Profile p) {
                    return t.equals(token)
                        && selected >= 0
                        && p.x() == candidates.get(selected).x
                        && p.y() == candidates.get(selected).y
                        && admission(selected) >= 0;
                  }

                  public boolean hidden(IsoPlayer b) {
                    return b == actor && hiddenBodies(false);
                  }
                });
        pool = new CivilianPool<>(epoch, nativeActors, 4);
        hunters = new WatchedHunters(1);
      }
      if (phase > 1 && now - phaseAt > 240_000_000_000L)
        throw new IllegalStateException("phase_timeout:" + phase + ":" + waiting);
      pool.tick(++tick, now);
      config.rawset("phase", (double) phase);
      if (resident != null) resident.rawset("event_phase", (double) phase);
      switch (phase) {
        case 0 -> {
          // The entire narrow corridor is checked incrementally. Viewpoint must have open egress
          // AND LOS.
          for (int budget = 0; budget < 16 && scan < 240; budget++, scan++) {
            int y = 9940 + scan;
            var sq = ServerMap.instance.getGridSquare(10590, y, 0);
            if (sq == null) return;
            require(
                sq.TreatAsSolidFloor()
                    && !sq.HasStairs()
                    && sq.isFree(false)
                    && !blocked(10590.5f, y + .5f, 10590.5f, y + 1.5f),
                "route_blocked:" + y);
          }
          if (scan < 240) return;
          var sq = ServerMap.instance.getGridSquare(10596, 10060, 0);
          if (sq == null) return;
          require(
              sq.TreatAsSolidFloor()
                  && sq.isFree(false)
                  && !blocked(10596.5f, 10060.5f, 10590.5f, 10060.5f)
                  && LosUtil.lineClear(
                          IsoWorld.instance.currentCell, 10596, 10060, 0, 10590, 10060, 0, false)
                      == LosUtil.TestResults.Clear,
              "viewpoint_los_or_egress_blocked");
          if (nativeActors.constructed < 4) {
            nativeActors.prewarmOne(10590, 9980, 0);
            return;
          }
          next(1, "ready for observer");
        }
        case 1 -> {
          var p = observer();
          if (p == null) return;
          protect(p);
          GameTime.getInstance().setTimeOfDay(12);
          GameServer.sendWeather();
          // Test setup only, before admission. Never move the observer to reveal or clean an event.
          GameServer.sendTeleport(p, 10596.5f, 10060.5f, 0);
          positioned = true;
          announce(
              "Off-screen chase test: positioning you once at the open roadside. Stay here; the"
                  + " chase will enter view naturally.");
          next(2, "waiting for loaded, unseen staging corridor");
        }
        case 2 -> {
          var p = observer();
          if (p == null) return;
          protect(p);
          if (now - phaseAt < 12_000_000_000L) return;
          require(p.getVehicle() == null, "observer_in_vehicle");
          Object value = LuaManager.env.rawget("AKRChaseResident");
          if (!(value instanceof KahluaTable r)) {
            require(
                config.rawget("registry_error") == null,
                "resident_restart_requires_reconciliation");
            return;
          }
          for (int i = 0; i < candidates.size(); i++)
            if ((ownerId = admission(i)) >= 0) {
              selected = i;
              break;
            }
          if (selected < 0) return;
          resident = r;
          residentId = (String) r.rawget("id");
          require(
              residentId != null && Boolean.TRUE.equals(r.rawget("newBody")),
              "resident_snapshot_required");
          Candidate c = candidates.get(selected);
          var presentation = (KahluaTable) r.rawget("presentation");
          nativeActors.profile(
              residentId,
              new NativeCivilianActors.Profile(
                  (String) presentation.rawget("name"),
                  (String) presentation.rawget("outfit"),
                  Boolean.TRUE.equals(presentation.rawget("female")),
                  c.x,
                  c.y,
                  0));
          token =
              pool.reserve(residentId, ((Number) r.rawget("generation")).longValue(), true, null);
          require(token != null, "pool_capacity");
          constructedAtAssignment = nativeActors.constructed;
          resident.rawset("state", "FLEE");
          resident.rawset("physical_slot", (double) token.slot());
          observerX = p.getX();
          observerY = p.getY();
          next(3, "initializing unseen pair");
        }
        case 3 -> {
          var view =
              pool.views().stream().filter(v -> token.equals(v.token())).findFirst().orElseThrow();
          require(view.state() != CivilianPool.State.UNRESOLVED, "materialize:" + view.reason());
          actor = pool.body(token);
          if (actor == null) return;
          require(
              nativeActors.constructed == constructedAtAssignment, "assignment_constructed_actor");
          // Materialization just ran under the exact admission policy on this game thread.
          Candidate c = candidates.get(selected);
          require(admission(selected) >= 0, "staging_changed_before_hunter");
          hunters.spawnAt(c.x, c.y - 8 * c.dir, 0, c.dir);
          config.rawset("actor_id", (double) actor.getOnlineID());
          config.rawset("actor_clothes", (double) actor.getItemVisuals().size());
          config.rawset("hunter_id", (double) hunters.body().getOnlineID());
          config.rawset("revision", number(config, "revision") + 1);
          next(4, "waiting for native ownership, replicas and pursuit");
        }
        case 4, 5 -> {
          var p = observer();
          require(p != null, "observer_disconnected_owned");
          protect(p);
          observerMoved =
              Math.max(observerMoved, Math.hypot(p.getX() - observerX, p.getY() - observerY));
          observeBodies();
          if (phase == 4 && (seenActor || seenHunter)) earlyReveal = true;
          var z = hunters.body();
          var own = z.getOwner();
          if (own == null) {
            IsoPlayer candidate = GameServer.IDToPlayerMap.get((short) ownerId);
            var report = candidate == null ? null : report(candidate);
            if (candidate != null
                && report != null
                && truth(table(report, "hunter"), "loaded")
                && eligible(candidate, z.getX(), z.getY()))
              NetworkZombieManager.getInstance()
                  .moveZombie(z, GameServer.getConnectionFromPlayer(candidate), candidate);
          }
          boolean acknowledged = false;
          for (IsoPlayer viewer : GameServer.Players) {
            var r = report(viewer);
            if (r == null) continue;
            if (z.getOwner() == GameServer.getConnectionFromPlayer(viewer)
                && truth(table(r, "hunter"), "local_owner")
                && truth(table(r, "actor"), "ready")) acknowledged = true;
          }
          if (acknowledged) hunters.tick(List.of(actor), now);
          else {
            nativeActors.replicate(actor, 0, 0, false);
            if (phase == 5
                && seenActor
                && seenHunter
                && hunters.travel() >= 5
                && hiddenBodies(false)) {
              next(6, "left native relevance unseen; retiring encounter");
              return;
            }
            waiting = "waiting for native owner and client bodies";
            return;
          }
          double gap = Math.hypot(actor.getX() - z.getX(), actor.getY() - z.getY());
          if (phase == 4) {
            nativeActors.replicate(actor, 0, 0, false);
            if (hunters.targeted() && hunters.travel() >= .6) {
              primedHidden = !earlyReveal && hiddenBodies(false);
              last = now;
              next(5, "chase active");
              announce("The chase is moving. Watch the road; your position will remain unchanged.");
            } else require(now - phaseAt < 30_000_000_000L, "native_pursuit_not_started");
          } else {
            Candidate c = candidates.get(selected);
            float goal = 10060.5f + c.dir * 90;
            double dt = Math.min(.15, (now - last) / 1e9);
            last = now;
            // Keep the pursuit together rather than sprinting out of stock perception.
            double speed = Math.max(.6, Math.min(2.6, 1.4 + (12 - gap) * .15));
            require(nativeActors.walk(token, actor, c.x, goal, speed, dt), "route_collision_owned");
            if (Math.abs(actor.getY() - goal) < .1) {
              nativeActors.stop(actor);
              next(6, "waiting for unseen cleanup");
            }
          }
        }
        case 6 -> {
          observeBodies();
          if (!hiddenBodies(false)) {
            quiet = 0;
            return;
          }
          if (quiet == 0) quiet = now;
          if (now - quiet < 5_000_000_000L) return;
          if (!hunters.removeOne()) return;
          if (!retired) {
            require(pool.retire(token), "retirement_not_ready");
            retired = true;
          }
          var receipt = pool.pollRetired();
          if (receipt == null) return;
          Files.write(dir.resolve("chase-resident.bin"), receipt.snapshot().bytes());
          nativeActors.park(token, actor, receipt.snapshot());
          resident.rawset("newBody", false);
          resident.rawset("body_snapshot", "chase-resident.bin");
          resident.rawset("physical_slot", null);
          resident.rawset("state", "IDLE");
          next(7, "confirming client absence");
        }
        case 7 -> {
          if (!hiddenBodies(true)) {
            quiet = 0;
            return;
          }
          if (quiet == 0) quiet = now;
          if (now - quiet < 2_000_000_000L) return;
          require(pool.occupied() == 0, "pool_not_clear");
          nativeActors.clearParked();
          require(nativeActors.retainedBodies() == 0, "native_body_leak");
          finished = true;
          waiting =
              primedHidden && seenActor && seenHunter && hunters.travel() >= 5 && observerMoved < 3
                  ? "passed"
                  : "completed_unqualified";
          announce("Off-screen chase test " + waiting + ". Cleanup confirmed; results recorded.");
          publish();
        }
      }
    } catch (Throwable e) {
      failure = e.getClass().getSimpleName() + ":" + Objects.toString(e.getMessage(), "");
      finished = true;
      waiting = "failed";
      e.printStackTrace();
      announce("Chase test stopped: " + failure + ". Uncertain bodies retained.");
      try {
        publish();
      } catch (Exception ignored) {
      }
    } finally {
      timing.add(System.nanoTime() - begin);
      if (!finished && now - published > 1_000_000_000L) {
        published = now;
        try {
          publish();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  private boolean blocked(float x, float y, float tx, float ty) {
    return PolygonalMap2.instance.lineClearCollide(x, y, tx, ty, 0, null, false, true);
  }

  private int admission(int index) {
    Candidate c = candidates.get(index);
    List<OffscreenAdmission.Observer> ps = new ArrayList<>();
    List<OffscreenAdmission.Report> rs = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (IsoPlayer p : GameServer.Players) {
      ps.add(new OffscreenAdmission.Observer(p.getOnlineID(), p.getX(), p.getY()));
      var r = report(p);
      if (r == null) continue;
      var observations = table(r, "candidates");
      var value = observations == null ? null : observations.rawget((double) index + 1);
      if (!(value instanceof KahluaTable v)) continue;
      rs.add(
          new OffscreenAdmission.Report(
              p.getOnlineID(),
              (long) number(r, "received"),
              (long) number(r, "revision"),
              number(r, "x"),
              number(r, "y"),
              truth(v, "hidden"),
              truth(v, "loaded") && eligible(p, c.x, c.y) && eligible(p, c.x, c.y - 8 * c.dir)));
    }
    return OffscreenAdmission.owner(ps, rs, now, (long) number(config, "revision"), c.x, c.y);
  }

  private boolean eligible(IsoPlayer p, float x, float y) {
    var c = GameServer.getConnectionFromPlayer(p);
    return c != null
        && c.isFullyConnected()
        && !GameServer.isDelayedDisconnect(c)
        && Float.isFinite(p.getRelevantAndDistance(x, y, c.getRelevantRange() - 2));
  }

  private KahluaTable report(IsoPlayer p) {
    var all = LuaManager.env.rawget("AKRChaseReports");
    var value = all instanceof KahluaTable t ? t.rawget((double) p.getOnlineID()) : null;
    if (!(value instanceof KahluaTable r)
        || !epoch.equals(r.rawget("epoch"))
        || number(r, "revision") != number(config, "revision")) return null;
    double age = System.currentTimeMillis() - number(r, "received");
    return age >= 0
            && age <= 750
            && Math.hypot(number(r, "x") - p.getX(), number(r, "y") - p.getY()) <= 1
        ? r
        : null;
  }

  private boolean hiddenBodies(boolean absent) {
    if (GameServer.Players.isEmpty()) return false;
    for (IsoPlayer p : GameServer.Players) {
      var r = report(p);
      if (r == null) return false;
      for (String key : List.of("actor", "hunter")) {
        var b = table(r, key);
        if (!truth(b, "hidden") || absent && !Boolean.FALSE.equals(b.rawget("present")))
          return false;
      }
      if (!absent
          && actor != null
          && (Math.hypot(p.getX() - actor.getX(), p.getY() - actor.getY()) < 12
              || hunters.body() != null
                  && Math.hypot(p.getX() - hunters.body().getX(), p.getY() - hunters.body().getY())
                      < 12)) return false;
    }
    return true;
  }

  private void observeBodies() {
    for (IsoPlayer p : GameServer.Players) {
      var r = report(p);
      if (r == null) continue;
      seenActor |= truth(table(r, "actor"), "visible");
      seenHunter |= truth(table(r, "hunter"), "visible");
    }
  }

  private static KahluaTable table(KahluaTable t, String key) {
    Object v = t == null ? null : t.rawget(key);
    return v instanceof KahluaTable k ? k : null;
  }

  private static boolean truth(KahluaTable t, String key) {
    return t != null && Boolean.TRUE.equals(t.rawget(key));
  }

  private static double number(KahluaTable t, String key) {
    Object v = t == null ? null : t.rawget(key);
    return v instanceof Number n ? n.doubleValue() : Double.NaN;
  }

  private IsoPlayer observer() {
    for (var p : GameServer.Players)
      if (p != null
          && "akr".equals(p.getUsername())
          && p.getSquare() != null
          && GameServer.getConnectionFromPlayer(p) != null
          && GameServer.getConnectionFromPlayer(p).isFullyConnected()) return p;
    return null;
  }

  private void protect(IsoPlayer p) {
    require(
        p.getRole() != null && "admin".equalsIgnoreCase(p.getRole().getName()),
        "observer_admin_required");
    if (!p.isGodMod() || !p.isGhostMode() || !p.isInvisible()) {
      p.setGodMod(true, true);
      p.setGhostMode(true, true);
      p.setInvisible(true, true);
      GameServer.sendPlayerExtraInfo(p, null, true);
    }
  }

  private void next(int next, String why) {
    phase = next;
    phaseAt = last = System.nanoTime();
    quiet = 0;
    waiting = why;
    System.out.println("[OffscreenChase] phase=" + phase + " " + why);
  }

  private void announce(String s) {
    System.out.println("[OffscreenChase] " + s);
    try {
      ChatServer.getInstance().sendServerAlertMessageToServerChat("[Chase test] " + s);
    } catch (Exception ignored) {
    }
  }

  private static void require(boolean condition, String why) {
    if (!condition) throw new IllegalStateException(why);
  }

  private static String q(String s) {
    return "\""
        + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
        + "\"";
  }

  private void publish() throws Exception {
    var z = hunters == null ? null : hunters.body();
    int readyActors = 0, readyOwners = 0;
    for (var p : GameServer.Players) {
      var r = report(p);
      if (truth(table(r, "actor"), "ready")) readyActors++;
      if (truth(table(r, "hunter"), "local_owner")) readyOwners++;
    }
    String json =
        "{\"ready_actors\":"
            + readyActors
            + ",\"ready_owners\":"
            + readyOwners
            + ",\"expected_clothes\":"
            + (actor == null ? 0 : actor.getItemVisuals().size())
            + ",\"status\":"
            + q(finished ? waiting : "running")
            + ",\"phase\":"
            + phase
            + ",\"waiting\":"
            + q(waiting)
            + ",\"failure\":"
            + q(failure)
            + ",\"world\":"
            + q(world)
            + ",\"epoch\":"
            + q(epoch)
            + ",\"constructed\":"
            + (nativeActors == null ? 0 : nativeActors.constructed)
            + ",\"assignments\":"
            + (nativeActors == null ? 0 : nativeActors.reused)
            + ",\"retained\":"
            + (nativeActors == null ? 0 : nativeActors.retainedBodies())
            + ",\"selected\":"
            + selected
            + ",\"resident\":"
            + q(residentId)
            + ",\"owner\":"
            + (z == null || z.getOwnerPlayer() == null ? -1 : z.getOwnerPlayer().getOnlineID())
            + ",\"actor_y\":"
            + (actor == null ? 0 : actor.getY())
            + ",\"hunter_y\":"
            + (z == null ? 0 : z.getY())
            + ",\"hunter_travel\":"
            + (hunters == null ? 0 : hunters.travel())
            + ",\"primed_hidden\":"
            + primedHidden
            + ",\"early_reveal\":"
            + earlyReveal
            + ",\"seen_actor\":"
            + seenActor
            + ",\"seen_hunter\":"
            + seenHunter
            + ",\"observer_moved\":"
            + observerMoved
            + ",\"work_p95_ms\":"
            + timing.percentile(.95)
            + ",\"work_p99_ms\":"
            + timing.percentile(.99)
            + ",\"work_max_ms\":"
            + timing.max / 1e6
            + ",\"two_client_validated\":false}\n";
    Files.writeString(dir.resolve("chase-report.tmp"), json);
    Files.move(
        dir.resolve("chase-report.tmp"),
        dir.resolve("chase-report.json"),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }
}
