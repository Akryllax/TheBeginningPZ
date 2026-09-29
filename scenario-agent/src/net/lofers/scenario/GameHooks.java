package net.lofers.scenario;

import java.util.*;
import se.krka.kahlua.integration.*;
import se.krka.kahlua.vm.*;
import zombie.Lua.LuaManager;
import zombie.characters.*;
import zombie.core.physics.*;
import zombie.network.*;
import zombie.popman.NetworkZombieManager;
import zombie.popman.NetworkZombiePacker;
import zombie.vehicles.BaseVehicle;

/** All methods touching game objects execute on the Lua/game thread. No IPC calls here. */
public final class GameHooks {
  private static final ThreadLocal<Integer> permits = ThreadLocal.withInitial(() -> 0);
  private static final ThreadLocal<Integer> factoryBudget = ThreadLocal.withInitial(() -> 0);
  private static boolean background;
  private static Thread thread;
  private static final Map<BaseVehicle, Control> controls = new WeakHashMap<>();

  private record Control(double throttle, double brake, double steer, long deadline) {}

  static void ownThread() {
    if (thread == null) thread = Thread.currentThread();
    if (thread != Thread.currentThread())
      throw new IllegalStateException("Scenario game-thread API");
  }

  public static boolean inSpawnPermit() {
    return permits.get() > 0;
  }

  /** Java-side equivalent of LofersNative.spawn: one bounded native creation inside a permit. */
  static <T> T withSpawnPermit(java.util.function.Supplier<T> action) {
    ownThread();
    int depth = permits.get(), budget = factoryBudget.get();
    permits.set(depth + 1);
    factoryBudget.set(1);
    try {
      return action.get();
    } finally {
      permits.set(depth);
      factoryBudget.set(budget);
    }
  }

  public static boolean populationBlocked() {
    return ScenarioAgent.server && ScenarioAgent.enabled && !background && !inSpawnPermit();
  }

  public static boolean factoryBlocked() {
    if (inSpawnPermit()) {
      int n = factoryBudget.get();
      factoryBudget.set(n - 1);
      return n <= 0;
    }
    return populationBlocked();
  }

  static boolean paired() {
    if (!ScenarioAgent.enabled) return false;
    if (ScenarioAgent.pedestrianRuntimeEnabled()) return true;
    Object sandbox = LuaManager.env == null ? null : LuaManager.env.rawget("SandboxVars");
    Object scenario = sandbox instanceof KahluaTable t ? t.rawget("LofersScenario") : null;
    return scenario instanceof KahluaTable t && Boolean.TRUE.equals(t.rawget("Enabled"));
  }

  static KahluaTable tag(IsoZombie z) {
    Object v = z.getModData().rawget("LofersScenario");
    return v instanceof KahluaTable t ? t : null;
  }

  static boolean managed(IsoZombie z) {
    KahluaTable t = tag(z);
    return t != null
        && t.rawget("id") instanceof String
        && t.rawget("generation") instanceof Number;
  }

  static boolean pendingManaged(IsoZombie z) {
    if (managed(z)) return true;
    // Bandits' event is registered before our Lua executor. Recognize the
    // published outfit/cluster identity before its first callback can run.
    long raw = z.getPersistentOutfitID(), clean = Integer.toUnsignedLong((int) raw) & ~0x8000L;
    Object client = LuaManager.env.rawget("LofersScenarioClient");
    Object outfits = client instanceof KahluaTable t ? t.rawget("outfits") : null;
    if (outfits instanceof KahluaTable t
        && (t.rawget((double) raw) != null || t.rawget((double) clean) != null)) return true;
    Object clusters = LuaManager.env.rawget("BanditClusters");
    if (clusters instanceof KahluaTable tables) {
      for (double id : new double[] {raw, clean}) {
        Object c = tables.rawget(Math.abs(id) % 32);
        Object b = c instanceof KahluaTable t ? t.rawget(id) : null;
        if (b instanceof KahluaTable brain
            && "lofers-scenario-civilians-v1".equals(brain.rawget("cid"))) return true;
      }
    }
    return false;
  }

  static int leaseOwner(IsoZombie z) {
    KahluaTable t = tag(z);
    return t != null && t.rawget("owner_id") instanceof Number n ? n.intValue() : -1;
  }

  static boolean canAct(IsoZombie z) {
    if (!paired() || !managed(z) || z.isDead()) return false;
    if (ScenarioAgent.server) return true;
    if (z.isRemoteZombie()) return false;
    int id = leaseOwner(z);
    for (IsoPlayer p : IsoPlayer.players)
      if (p != null && p.isLocalPlayer() && p.getOnlineID() == id) return true;
    return false;
  }

  static double number(Object v) {
    return ProtocolCodec.number(v).doubleValue();
  }

  static int integer(Object v) {
    return (int) ProtocolCodec.integer(v, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  static KahluaTable install() {
    // Lua initialization can run on the loading thread. Claim ownership only
    // when the live game tick/action first calls the runtime.
    KahluaTable api = LuaManager.platform.newTable();
    api.rawset(
        "versionReady",
        (JavaFunction) (f, n) -> f.push(ScenarioAgent.enabled && ScenarioAgent.verified));
    api.rawset("side", ScenarioAgent.server ? "server" : "client");
    api.rawset("build", "42.20.4/b0bbce05d5");
    api.rawset(
        "populationMode",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!ScenarioAgent.server || !paired()) return f.push(false);
              background = Boolean.TRUE.equals(f.get(0));
              return f.push(true);
            });
    api.rawset(
        "spawn",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!ScenarioAgent.server || !paired() || !(f.get(0) instanceof LuaClosure))
                return f.push(false, "spawn_not_authorized");
              int depth = permits.get(), budget = factoryBudget.get();
              permits.set(depth + 1);
              factoryBudget.set(16);
              try {
                LuaReturn ret =
                    LuaManager.caller.protectedCall(LuaManager.thread, f.get(0), f.get(1));
                if (!ret.isSuccess()) return f.push(false, ret.getErrorString());
                return f.push(true, ret.size() > 0 ? ret.getFirst() : null);
              } finally {
                permits.set(depth);
                factoryBudget.set(budget);
              }
            });
    api.rawset(
        "bind",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!paired()
                  || !(f.get(0) instanceof IsoZombie z)
                  || !(f.get(1) instanceof String id)
                  || id.isBlank()
                  || id.length() > 128) return f.push(false);
              long generation = ProtocolCodec.integer(f.get(2), 1, 9007199254740991L),
                  epoch = ProtocolCodec.integer(f.get(3), 0, 9007199254740991L);
              int owner = integer(f.get(4));
              KahluaTable old = tag(z);
              if (old != null
                  && old.rawget("generation") instanceof Number prev
                  && prev.longValue() > generation) return f.push(false);
              KahluaTable t = LuaManager.platform.newTable();
              t.rawset("id", id);
              t.rawset("generation", (double) generation);
              t.rawset("lease_epoch", (double) epoch);
              t.rawset("owner_id", (double) owner);
              z.getModData().rawset("LofersScenario", t);
              return f.push(true);
            });
    api.rawset(
        "ownerId",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!(f.get(0) instanceof IsoZombie z)) return f.push(-1d);
              IsoPlayer p = z.getOwnerPlayer();
              return f.push(
                  (double)
                      (p == null ? (ScenarioAgent.server ? -1 : leaseOwner(z)) : p.getOnlineID()));
            });
    api.rawset(
        "canAct",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              return f.push(f.get(0) instanceof IsoZombie z && canAct(z));
            });
    api.rawset(
        "lease",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!paired() || !(f.get(0) instanceof IsoZombie z) || !managed(z))
                return f.push(false);
              long epoch = ProtocolCodec.integer(f.get(1), 0, 9007199254740991L);
              int owner = integer(f.get(2));
              KahluaTable t = tag(z);
              if (t.rawget("lease_epoch") instanceof Number old && old.longValue() > epoch)
                return f.push(false);
              if (ScenarioAgent.server) {
                IsoPlayer p = owner >= 0 ? GameServer.IDToPlayerMap.get((short) owner) : null;
                if (owner >= 0 && p == null) return f.push(false);
                NetworkZombieManager.getInstance()
                    .moveZombie(z, p == null ? null : GameServer.getConnectionFromPlayer(p), p);
                IsoPlayer actual = z.getOwnerPlayer();
                if ((actual == null ? -1 : actual.getOnlineID()) != owner) return f.push(false);
                BaseVehicle v = z.getVehicle();
                if (v != null && v.isDriver(z))
                  v.setNetPlayerAuthorization(
                      owner >= 0
                          ? BaseVehicle.Authorization.Local
                          : BaseVehicle.Authorization.Server,
                      owner);
              }
              t.rawset("lease_epoch", (double) epoch);
              t.rawset("owner_id", (double) owner);
              return f.push(true);
            });
    api.rawset(
        "enterVehicle",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!(f.get(0) instanceof IsoZombie z)
                  || !canAct(z)
                  || !(f.get(1) instanceof BaseVehicle v)) return f.push(false);
              int seat = integer(f.get(2));
              if (seat < 0
                  || seat >= v.getMaxPassengers()
                  || v.isSeatOccupied(seat)
                  || !v.isSeatInstalled(seat)
                  || v.getCurrentAbsoluteSpeedKmHour() > 1) return f.push(false);
              if (Math.hypot(z.getX() - v.getX(), z.getY() - v.getY()) > 6) return f.push(false);
              boolean ok = v.enterRSync(seat, z, v);
              if (ok && ScenarioAgent.server && seat == 0) {
                int owner = leaseOwner(z);
                if (owner < 0) {
                  v.exitRSync(z);
                  return f.push(false);
                }
                v.setNetPlayerAuthorization(BaseVehicle.Authorization.Local, owner);
                v.tryStartEngine(true);
              }
              return f.push(ok);
            });
    api.rawset(
        "exitVehicle",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!(f.get(0) instanceof IsoZombie z) || !canAct(z) || z.getVehicle() == null)
                return f.push(false);
              BaseVehicle v = z.getVehicle();
              if (v.getCurrentAbsoluteSpeedKmHour() > 1) return f.push(false);
              controls.remove(v);
              boolean ok = v.exitRSync(z);
              if (ok && ScenarioAgent.server && v.getDriver() == null)
                v.setNetPlayerAuthorization(BaseVehicle.Authorization.Server, -1);
              return f.push(ok);
            });
    api.rawset(
        "syncSeat",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (ScenarioAgent.server
                  || !paired()
                  || !(f.get(0) instanceof IsoZombie z)
                  || !managed(z)
                  || !(f.get(1) instanceof BaseVehicle v)) return f.push(false);
              KahluaTable t = tag(z);
              int seat = integer(f.get(2));
              if (number(t.rawget("generation")) != number(f.get(4))
                  || number(t.rawget("lease_epoch")) != number(f.get(5))) return f.push(false);
              if (Boolean.TRUE.equals(f.get(3))) {
                if (z.getVehicle() == v && v.getSeat(z) == seat) return f.push(true);
                if (seat < 0
                    || seat >= v.getMaxPassengers()
                    || v.isSeatOccupied(seat)
                    || !v.isSeatInstalled(seat)) return f.push(false);
                return f.push(v.enterRSync(seat, z, v));
              }
              if (z.getVehicle() != v) return f.push(z.getVehicle() == null);
              controls.remove(v);
              return f.push(v.exitRSync(z));
            });
    api.rawset(
        "controlVehicle",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!(f.get(0) instanceof BaseVehicle v)
                  || !(v.getDriver() instanceof IsoZombie z)
                  || !canAct(z)
                  || ScenarioAgent.server) return f.push(false);
              if (!vehicleOwner(v, z)) return f.push(false);
              controls.put(
                  v,
                  new Control(
                      clamp(number(f.get(1)), -1, 1),
                      clamp(number(f.get(2)), 0, 1),
                      clamp(number(f.get(3)), -1, 1),
                      System.nanoTime() + 400_000_000));
              return f.push(true);
            });
    api.rawset(
        "removeOwned",
        (JavaFunction)
            (f, n) -> {
              ownThread();
              if (!(f.get(0) instanceof IsoZombie z) || !canAct(z)) return f.push(false);
              if (z.getVehicle() != null) {
                BaseVehicle v = z.getVehicle();
                if (v.getCurrentAbsoluteSpeedKmHour() > 1) return f.push(false);
                v.exitRSync(z);
                controls.remove(v);
              }
              // Queue the network deletion while the ID is still valid: removal
              // clears onlineId and ownership bookkeeping inside IsoZombie.
              if (ScenarioAgent.server) {
                if (z.getOnlineID() < 0) return f.push(false);
                NetworkZombiePacker.getInstance().deleteZombie(z);
              }
              z.removeFromWorld();
              z.removeFromSquare();
              if (ScenarioAgent.server) NetworkZombiePacker.getInstance().setExtraUpdate();
              return f.push(true);
            });
    return api;
  }

  static double clamp(double x, double lo, double hi) {
    return Math.max(lo, Math.min(hi, x));
  }

  static boolean vehicleOwner(BaseVehicle v, IsoZombie z) {
    return canAct(z)
        && v.getNetPlayerId() == leaseOwner(z)
        && v.isNetPlayerAuthorization(BaseVehicle.Authorization.Local);
  }

  public static boolean vehicleStep(CarController controller) {
    BaseVehicle v = controller.vehicleObject;
    if (!paired() || !(v.getDriver() instanceof IsoZombie z) || !managed(z)) return false;
    // The ordinary controller's engine-start path casts NPC drivers to IsoPlayer.
    // Remote copies interpolate; only the designated owner applies Bullet forces.
    if (ScenarioAgent.server || !vehicleOwner(v, z)) return true;
    Control c = controls.get(v);
    long now = System.nanoTime();
    double throttle = c != null && now < c.deadline ? c.throttle : 0,
        brake = c != null && now < c.deadline ? c.brake : 1,
        steer = c != null && now < c.deadline ? c.steer : 0;
    if (!v.isEngineRunning() || v.getCurrentAbsoluteSpeedKmHour() > 35) throttle = 0;
    if (v.getCurrentAbsoluteSpeedKmHour() > 40) brake = 1;
    float force = (float) (throttle * Math.min(2000, Math.max(0, v.getEnginePower()))),
        braking = (float) (brake * 80);
    controller.engineForce = force;
    controller.brakingForce = braking;
    controller.acceleratorOn = force != 0;
    controller.brakeOn = brake > 0;
    v.setBraking(brake > 0);
    v.setPhysicsActive(true);
    Bullet.controlVehicle(v.getId(), force, braking, (float) (steer * 0.4));
    return true;
  }

  public static void eventCallback(
      LuaCaller caller, KahluaThread thread, Object function, Object[] args) {
    if (paired() && function instanceof LuaClosure closure) {
      String file = Objects.toString(closure.prototype.file, "").replace('\\', '/');
      String filename = Objects.toString(closure.prototype.filename, "").replace('\\', '/');
      if (file.endsWith("BanditUpdate.lua") || filename.endsWith("BanditUpdate.lua"))
        for (Object arg : args) {
          if (arg instanceof IsoZombie z && pendingManaged(z)) return;
          if (arg instanceof zombie.iso.objects.IsoDeadBody body
              && body.getModData().rawget("LofersScenario") instanceof KahluaTable) return;
        }
    }
    caller.protectedCallVoid(thread, function, args);
  }
}
