package net.akr.scenario;

import java.util.*;
import net.akr.scenario.protocol.RuntimeControl.*;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.vm.*;
import zombie.Lua.LuaManager;
import zombie.MovingObjectUpdateScheduler;
import zombie.MovingObjectUpdateSchedulerUpdateBucket;
import zombie.UpdateSchedulerSimulationLevel;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;
import zombie.iso.Vector2;
import zombie.network.ServerMap;
import zombie.network.packets.character.ZombieSynchronizationPacket;
import zombie.popman.NetworkZombieManager;
import zombie.popman.NetworkZombiePacker;

/** Experimental ownership/stream adapter. It never supplies synthetic movement. */
public final class ServerPedestrians implements RuntimeSession.Backend {
  private static ServerPedestrians instance;
  private static final Set<IsoZombie> animationListeners =
      Collections.newSetFromMap(new WeakHashMap<>());
  private final LinkedHashMap<String, Actor> actors = new LinkedHashMap<>();
  private final IdentityHashMap<IsoZombie, Actor> bindings = new IdentityHashMap<>();
  private EventScheduler scheduler;
  private EventScheduler.View event;
  private PedestrianDefinition definition;
  private LuaClosure factory, retire;
  private int prepared;
  private long started, arrivedAt;
  private boolean uncertainSpawn;
  private String pendingId;

  private static final class Actor {
    final String id;
    final IsoZombie zombie;
    final short onlineId;
    final double initialX, initialY;
    final EventResources.Resource resource;
    int waypoint = 1;
    long pathAt, nativeStepAt;
    final ProbeTiming nativeTiming = new ProbeTiming();
    String action = "PREPARING";
    boolean retirementAcknowledged;

    Actor(String id, IsoZombie zombie, EventResources.Resource resource) {
      this.id = id;
      this.zombie = zombie;
      this.onlineId = zombie.getOnlineID();
      this.resource = resource;
      initialX = zombie.getX();
      initialY = zombie.getY();
    }
  }

  ServerPedestrians() {
    instance = this;
  }

  /** Register the Lua factory and retire callbacks after the game environment starts. */
  void register(LuaClosure factory, LuaClosure retire) {
    GameHooks.ownThread();
    if (!actors.isEmpty() || uncertainSpawn) throw new IllegalStateException("owned_entities");
    this.factory = factory;
    this.retire = retire;
  }

  /** Civilian disguised-zombie bindings: goal gating (sight/sound/wander) applies to these only. */
  /** Check whether a zombie-shaped engine body belongs to this civilian adapter. */
  public static boolean owns(IsoZombie zombie) {
    return ScenarioAgent.server && instance != null && instance.bindings.containsKey(zombie);
  }

  /**
   * Hostile zombies simulated on the server so they can engage connectionless Actors (Decision 0006
   * gate 3). Stock AI runs unmodified; only ownership, scheduling, replication and animation
   * registration reuse the civilian machinery. At most four.
   */
  private static final Set<IsoZombie> hunters = Collections.newSetFromMap(new IdentityHashMap<>());

  static final ProbeTiming hunterTiming = new ProbeTiming();
  private static IsoZombie hunterStep;
  private static long hunterStepAt;

  static void bindHunter(IsoZombie zombie) {
    GameHooks.ownThread();
    if (hunters.size() >= 4) throw new IllegalStateException("hunter_capacity");
    if (owns(zombie) || zombie.getOnlineID() < 0) throw new IllegalStateException("hunter_binding");
    hunters.add(zombie);
    NetworkZombieManager.getInstance().moveZombie(zombie, null, null);
    registerNativeCallbacks(zombie);
  }

  static void unbindHunter(IsoZombie zombie) {
    GameHooks.ownThread();
    hunters.remove(zombie);
  }

  /**
   * Also used by the isRemoteZombie hook: stock NetworkZombieComponent reports every zombie without
   * a client authOwner as remote, so a server-simulated hunter would never accept a sighting
   * (NetworkZombieManager.canSpotted) or record aggro. The server is its simulator.
   */
  /** Identify a native hunter whose target acquisition is controlled by this event. */
  public static boolean hunts(IsoZombie zombie) {
    return ScenarioAgent.server && hunters.contains(zombie);
  }

  /**
   * IsoZombie.isTargetVisible hook: on the server stock visibility asks ServerLOS for the target
   * player's precomputed LOS, which a connectionless Actor never has, so every attack aborts
   * (attack -> idle when bCanSeeTarget is false). For hunters targeting an Actor only, answer with
   * the same stock line-of-sight primitive AttackState itself checks, within 20 tiles.
   */
  /** Allow an owned hunter to use the event's reviewed target-visibility result. */
  public static boolean overridesTargetVisibility(IsoZombie zombie) {
    return hunts(zombie)
        && zombie.getTarget() instanceof IsoPlayer target
        && ServerActors.isActor(target);
  }

  /** Return the scoped target-visibility result for an owned hunter. */
  public static boolean actorTargetVisible(IsoZombie zombie) {
    var target = zombie.getTarget();
    if (target == null || zombie.getCurrentSquare() == null || target.getCurrentSquare() == null)
      return false;
    if (Math.hypot(target.getX() - zombie.getX(), target.getY() - zombie.getY()) > 20) return false;
    var result =
        LosUtil.lineClear(
            zombie.getCell(),
            (int) Math.floor(zombie.getX()),
            (int) Math.floor(zombie.getY()),
            (int) Math.floor(zombie.getZ()),
            (int) Math.floor(target.getX()),
            (int) Math.floor(target.getY()),
            (int) Math.floor(target.getZ()),
            false);
    return result != LosUtil.TestResults.Blocked
        && result != LosUtil.TestResults.ClearThroughClosedDoor;
  }

  /** AnimationPlayer.DoAngles hook: procedural turning for hunters (no server root rotation). */
  /** Use procedural turning when the dedicated server omits deferred animation rotation. */
  public static boolean serverTurnFallback(IsoGameCharacter character) {
    return character instanceof IsoZombie zombie && hunts(zombie);
  }

  /** Server-simulated zombies of either kind: ownership, scheduler, stream and variable hooks. */
  public static boolean simulated(IsoZombie zombie) {
    return owns(zombie) || hunts(zombie);
  }

  /** Start native movement timing only for managed civilians or hunters. */
  public static void beginNativeStep(IsoZombie zombie) {
    if (!ScenarioAgent.server) return;
    if (hunters.contains(zombie)) {
      hunterStep = zombie;
      hunterStepAt = System.nanoTime();
      return;
    }
    if (instance == null) return;
    Actor actor = instance.bindings.get(zombie);
    if (actor != null) actor.nativeStepAt = System.nanoTime();
  }

  /** Finish the paired timing sample without charging ordinary zombies. */
  public static void endNativeStep(IsoZombie zombie) {
    if (!ScenarioAgent.server) return;
    if (hunterStep == zombie && hunterStepAt != 0) {
      hunterTiming.add(System.nanoTime() - hunterStepAt);
      hunterStepAt = 0;
      hunterStep = null;
      return;
    }
    if (instance == null) return;
    Actor actor = instance.bindings.get(zombie);
    if (actor != null && actor.nativeStepAt != 0) {
      actor.nativeTiming.add(System.nanoTime() - actor.nativeStepAt);
      actor.nativeStepAt = 0;
    }
  }

  /** Used only by the server setter and path-permission hooks, after exact entity binding. */
  public static boolean ownsCharacter(IsoGameCharacter character) {
    return character instanceof IsoZombie zombie && simulated(zombie);
  }

  private static void initializeLocomotion(IsoZombie zombie) {
    registerNativeCallbacks(zombie);
    zombie.setVariable("Bandit", true);
    zombie.setVariable("BanditWalkType", "Walk");
    zombie.setVariable("WalkSpeed", 1.0f);
    zombie.setWalkType("Walk");
  }

  private static void registerNativeCallbacks(IsoZombie zombie) {
    // Invoke the stock registrations skipped by the dedicated-server constructor.
    // No global server flag changes and no replacement movement integrator.
    try {
      for (Class<?> type : new Class<?>[] {IsoGameCharacter.class, IsoZombie.class}) {
        var method = type.getDeclaredMethod("registerVariableCallbacks");
        method.setAccessible(true);
        method.invoke(zombie);
      }
      if (!animationListeners.contains(zombie)) {
        var events = IsoGameCharacter.class.getDeclaredMethod("registerAnimEventCallbacks");
        events.setAccessible(true);
        events.invoke(zombie);
        animationListeners.add(zombie);
      }
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("pedestrian_animation_registration", ex);
    }
  }

  private static final class SchedulerAccess {
    static final java.lang.reflect.Field LEVELS = levels();

    private static java.lang.reflect.Field levels() {
      try {
        var field = MovingObjectUpdateScheduler.class.getDeclaredField("simulationLevels");
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException ex) {
        throw new ExceptionInInitializerError(ex);
      }
    }
  }

  /** The stock server scheduler excludes every zombie. Admit only our bounded set. */
  /** Admit bounded managed bodies to the server's native moving-object scheduler. */
  public static void scheduleActors(MovingObjectUpdateScheduler scheduler) {
    if (!ScenarioAgent.server
        || ((instance == null || instance.actors.isEmpty()) && hunters.isEmpty())) return;
    try {
      var levels =
          (MovingObjectUpdateSchedulerUpdateBucket[]) SchedulerAccess.LEVELS.get(scheduler);
      var bucket = levels[UpdateSchedulerSimulationLevel.FULL.getUpdateOrderIndex()];
      for (IsoZombie zombie : hunters)
        if (zombie.getOnlineID() >= 0
            && zombie.getSquare() != null
            && !zombie.isDead()
            && zombie.getOwner() == null) bucket.add(zombie);
      if (instance == null) return;
      for (Actor actor : instance.actors.values()) {
        IsoZombie zombie = actor.zombie;
        if (!actor.action.equals("RETIRING")
            && zombie.getOnlineID() >= 0
            && zombie.getSquare() != null
            && !zombie.isDead()
            && zombie.getOwner() == null) bucket.add(zombie);
      }
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("pedestrian_scheduler_admission", ex);
    }
  }

  /** Append only this adapter's actors to the stock packet, preserving relevance and identity. */
  public static int appendSnapshots(
      int count, UdpConnection connection, ZombieSynchronizationPacket packet) {
    if (!ScenarioAgent.server) return count;
    for (IsoZombie zombie : hunters) {
      if (zombie.getOnlineID() < 0
          || zombie.getOwner() != null
          || zombie.getSquare() == null
          || !connection.isFullyConnected()
          || !connection.RelevantTo(
              zombie.getX(), zombie.getY(), (connection.getRelevantRange() - 2) * 10)
          || packet.sendQueue.contains(zombie)) continue;
      zombie.zombiePacket.set(zombie);
      packet.sendQueue.add(zombie);
      count++;
    }
    if (instance == null) return count;
    for (Actor actor : instance.actors.values()) {
      IsoZombie zombie = actor.zombie;
      if (actor.action.equals("RETIRING")
          || zombie.getOnlineID() < 0
          || zombie.getOwner() != null
          || zombie.getSquare() == null
          || !connection.isFullyConnected()
          || !connection.RelevantTo(
              zombie.getX(), zombie.getY(), (connection.getRelevantRange() - 2) * 10)
          || packet.sendQueue.contains(zombie)) continue;
      zombie.zombiePacket.set(zombie);
      packet.sendQueue.add(zombie);
      count++;
    }
    return count;
  }

  @Override
  /** Bind one admitted pedestrian event to its Lua factory and resource ledger. */
  public void begin(EventScheduler.View event, EventScheduler scheduler) {
    if (!actors.isEmpty() || uncertainSpawn)
      throw new IllegalStateException("unresolved_previous_event");
    this.event = event;
    this.scheduler = scheduler;
    definition = event.definition().pedestrian();
    prepared = 0;
    arrivedAt = 0;
    started = System.nanoTime();
  }

  @Override
  /** Create or claim a body only when the event can record its exact ownership. */
  public boolean prepare() {
    if (factory == null || retire == null)
      throw new IllegalStateException("pedestrian_lua_adapter_missing");
    if (System.nanoTime() - started > 10_000_000_000L)
      throw new IllegalStateException("preparation_timeout");
    if (prepared == definition.actors()) return true;
    // Require a real player for the upstream Individual spawn API; never manufacture one.
    if (zombie.network.GameServer.Players.isEmpty())
      throw new IllegalStateException("observer_required");
    var point = definition.route().getFirst();
    double x = point.x(), y = point.y() + prepared * 2;
    var square = ServerMap.instance.getGridSquare((int) x, (int) y, 0);
    if (square == null || !square.isFree(false))
      throw new IllegalStateException("spawn_square_unavailable");
    pendingId = event.id() + ".npc" + prepared;
    var resource = new EventResources.Resource(EventResources.Kind.PEDESTRIAN, pendingId);
    scheduler.acquire(event.id(), resource);
    uncertainSpawn = true;
    LuaReturn result = LuaManager.caller.protectedCall(LuaManager.thread, factory, pendingId, x, y);
    if (result.isSuccess() && Boolean.FALSE.equals(result.getFirst())) {
      // The original factory may explicitly refuse before entering a spawn permit.
      // Exceptions and missing receipts remain unresolved, never a clean refusal.
      uncertainSpawn = false;
      scheduler.release(event.id(), resource, true, true);
      throw new IllegalStateException("spawn_refused_before_creation");
    }
    if (!result.isSuccess() || !(result.getFirst() instanceof IsoZombie zombie))
      throw new IllegalStateException("spawn_unresolved");
    Actor actor = new Actor(pendingId, zombie, resource);
    actors.put(pendingId, actor);
    bindings.put(zombie, actor);
    uncertainSpawn = false;
    if (zombie.getOnlineID() < 0) throw new IllegalStateException("spawn_without_network_identity");
    NetworkZombieManager.getInstance().moveZombie(zombie, null, null);
    zombie.setNoTeeth(true);
    zombie.setTarget(null);
    zombie.setUseless(false);
    initializeLocomotion(zombie);
    requestPath(actor);
    prepared++;
    return prepared == definition.actors();
  }

  private void requestPath(Actor actor) {
    var target = definition.route().get(actor.waypoint);
    actor.zombie.pathToLocation((int) target.x(), (int) target.y(), target.z());
    actor.action = "WALK";
    actor.pathAt = System.nanoTime();
  }

  @Override
  /** Follow bounded path progress while preserving native collision checks. */
  public boolean update() {
    long now = System.nanoTime();
    if (now - started > definition.timeoutSeconds() * 1_000_000_000L)
      throw new IllegalStateException("route_timeout");
    boolean allArrived = true;
    for (Actor actor : actors.values()) {
      IsoZombie zombie = actor.zombie;
      if (zombie.getOwner() != null || zombie.getOwnerPlayer() != null)
        throw new IllegalStateException("authority_lost");
      if (zombie.getSquare() == null) throw new IllegalStateException("actor_unloaded");
      if (zombie.isDead()) throw new IllegalStateException("actor_died");
      if (actor.waypoint >= definition.route().size()) continue;
      var target = definition.route().get(actor.waypoint);
      if (Math.hypot(zombie.getX() - target.x(), zombie.getY() - target.y()) < 1.0) {
        actor.waypoint++;
        if (actor.waypoint == definition.route().size()) {
          zombie.getPathFindBehavior2().cancel();
          zombie.setMoving(false);
          zombie.setVariable("bPathfind", false);
          zombie.setUseless(true);
          actor.action = "WAIT";
        } else requestPath(actor);
      }
      if (actor.waypoint < definition.route().size()) allArrived = false;
    }
    NetworkZombiePacker.getInstance().setExtraUpdate();
    if (!allArrived) return false;
    if (arrivedAt == 0) arrivedAt = now;
    return now - arrivedAt >= definition.holdSeconds() * 1_000_000_000L;
  }

  @Override
  /** Retire managed bodies and require acknowledgment before releasing ownership. */
  public boolean cleanup() {
    if (uncertainSpawn) return false;
    if (actors.isEmpty()) return true;
    Actor actor = actors.values().iterator().next();
    IsoZombie zombie = actor.zombie;
    if (zombie.getVehicle() != null) return false;
    actor.action = "RETIRING";
    zombie.getPathFindBehavior2().cancel();
    // A dead actor may already have become a corpse. Never claim that corpse was removed.
    if (zombie.isDead()) return false;
    if (!actor.retirementAcknowledged) {
      LuaReturn result =
          LuaManager.caller.protectedCall(LuaManager.thread, retire, zombie, actor.id);
      if (!result.isSuccess() || !Boolean.TRUE.equals(result.getFirst())) return false;
      actor.retirementAcknowledged = true;
    }
    var cell = IsoWorld.instance.currentCell;
    var square = zombie.getSquare();
    // Stock removeFromSquare retains the historical IsoObject.square field.
    // Verify membership, not whether that historical pointer is null.
    boolean squareAttached =
        square != null
            && (square.getMovingObjects().contains(zombie)
                || square.getStaticMovingObjects().contains(zombie)
                || square.getObjects().contains(zombie));
    boolean worldGone =
        !squareAttached
            && !cell.getZombieList().contains(zombie)
            && !cell.getObjectList().contains(zombie)
            && !cell.getAddList().contains(zombie)
            && !cell.getRemoveList().contains(zombie);
    boolean networkGone =
        zombie.getOnlineID() < 0 && ServerMap.instance.zombieMap.get(actor.onlineId) != zombie;
    if (!scheduler.release(event.id(), actor.resource, worldGone, networkGone)) return false;
    bindings.remove(zombie);
    actors.remove(actor.id);
    return actors.isEmpty();
  }

  @Override
  /** Return detached timing and progress metrics for the control channel. */
  public List<ActorSample> samples() {
    var result = new ArrayList<ActorSample>();
    for (Actor actor : actors.values()) {
      IsoZombie zombie = actor.zombie;
      Vector2 deferred = zombie.getDeferredMovement(new Vector2());
      result.add(
          ActorSample.newBuilder()
              .setId(actor.id)
              .setOnlineId(zombie.getOnlineID())
              .setPosition(
                  Point.newBuilder()
                      .setX(zombie.getX())
                      .setY(zombie.getY())
                      .setZ((int) zombie.getZ()))
              .setAction(actor.action)
              .setServerOwned(zombie.getOwner() == null && zombie.getOwnerPlayer() == null)
              .setPathState(
                  zombie.getFinder().progress
                      + ";anim="
                      + zombie.hasAnimationPlayer()
                      + ";deferred="
                      + deferred.getLength()
                      + ";state="
                      + zombie.getActionStateName()
                      + ";moving="
                      + zombie.isMoving()
                      + ";bMoving="
                      + zombie.getVariableBoolean("bMoving")
                      + ";baseMoving="
                      + zombie.getGameVariablesInternal().getVariableBoolean("bMoving")
                      + ";alerted="
                      + zombie.getVariableBoolean("alerted")
                      + ";sitting="
                      + zombie.getVariableBoolean("issitting")
                      + ";getUp="
                      + zombie.getVariableBoolean("bGetUpFromCrawl")
                      + ";client="
                      + zombie.getVariableBoolean("bClient")
                      + ";next="
                      + (zombie.getActionContext().peekNextState() == null
                          ? "none"
                          : zombie.getActionContext().peekNextState().getName())
                      + ";path="
                      + zombie.getVariableBoolean("bPathfind")
                      + ";nativeP99ms="
                      + actor.nativeTiming.percentile(.99)
                      + ";nativeMaxMs="
                      + actor.nativeTiming.max / 1_000_000.0)
              .setPathAgeMs((System.nanoTime() - actor.pathAt) / 1_000_000.0)
              .setDistanceMoved(
                  Math.hypot(zombie.getX() - actor.initialX, zombie.getY() - actor.initialY))
              .build());
    }
    return result;
  }
}
