package net.akr.scenario;

import static net.akr.scenario.npc.core.CivilianNavigation.*;

import java.util.*;
import net.akr.scenario.npc.core.CivilianCombat;
import net.akr.scenario.npc.core.CivilianEscape;
import net.akr.scenario.npc.core.CivilianGeometryCache;
import net.akr.scenario.npc.core.CivilianGraphCapture;
import net.akr.scenario.npc.core.CivilianPerception;
import net.akr.scenario.npc.core.CivilianPool;
import net.akr.scenario.npc.core.CivilianTraversal;
import se.krka.kahlua.vm.*;
import zombie.Lua.LuaManager;
import zombie.characters.*;
import zombie.inventory.types.HandWeapon;
import zombie.network.ServerMap;

/** Bounded server adapter for the same AKRResidents model used by detached tests. */
public final class NativeResidentController {
  private final CivilianPool<IsoPlayer> pool;
  private final NativeCivilianActors actors;
  private final CivilianPool.Token token;
  private final IsoPlayer body;
  private final KahluaTable model, resident;
  private final CivilianPerception perception;
  public final NativeCivilianCombat combat;
  public final NativeCivilianInjuries injuries;
  private final Map<String, Seen> seen = new HashMap<>();
  private long targetGeneration;

  private record Seen(IsoZombie body, long generation, long observed) {}

  private CivilianGraphCapture capture;
  private CivilianEscape escape;
  private Search roam;
  private CivilianTraversal traversal;
  private NativeCivilianTraversal movementPort;
  private Result path, reply;
  private Key routeKey, searchKey;
  private long revision, mapRevision = 1, nextDecision;
  private boolean assessed, reachable, blocked, captureUnknown;
  private List<Tile> planningThreats = List.of();
  private Tile planningOrigin, planningGoal;
  public int decisions, defenses, completedAttacks, pathRequests, fleeRoutes;
  public double travelled;
  public final ProbeTiming work = new ProbeTiming(),
      perceptionWork = new ProbeTiming(),
      navigationWork = new ProbeTiming(),
      combatWork = new ProbeTiming(),
      injuryWork = new ProbeTiming(),
      decisionWork = new ProbeTiming();
  private long pathRequestedAt;
  private boolean nativeRoutineRouting, nativeRoutePending;
  private long pathProgress;
  private int nativeRoutes, rejectedNativeRoutes, groundAlternatives;
  private final Set<Action> pendingTraversal = EnumSet.noneOf(Action.class);
  private final ProbeTiming nativeQueue = new ProbeTiming();

  String routineNavigationSummary() {
    return "native="
        + nativeRoutes
        + " rejected="
        + rejectedNativeRoutes
        + " ground_alternatives="
        + groundAlternatives
        + " pending_actions="
        + pendingTraversal
        + " queue_p95_ms="
        + nativeQueue.percentile(.95);
  }

  private NativeItemCollection collection;

  /** Bind one exact native inventory operation before the resident receives its first plan. */
  void collectAtActivity(NativeItemCollection operation) {
    var goal = goalPlan();
    if (goal == null || currentAction() != null)
      throw new IllegalStateException("collection_plan_already_started");
    collection = Objects.requireNonNull(operation);
    goal.rawset("collectItem", true);
  }

  /** Opt in to whole-route native planning while retaining bounded local execution windows. */
  void nativeRoutineRouting() {
    nativeRoutineRouting = true;
  }

  private int captureRadius;
  private final CivilianGeometryCache geometry;
  private final ArrayDeque<String> transitions = new ArrayDeque<>();
  private String traceSignature = "", lastInterrupt = "";
  private long executedEdges;
  private boolean wasReacting;
  public int pauses, resumes, routeReplacements;
  private Result committed;

  public List<String> trace() {
    return List.copyOf(transitions);
  }

  public String goal() {
    return resident.rawget("goalPlan") instanceof KahluaTable g
        ? Objects.toString(g.rawget("goal"), "")
        : "";
  }

  public String planSource() {
    return resident.rawget("goalPlan") instanceof KahluaTable g
        ? Objects.toString(g.rawget("source"), "")
        : "";
  }

  public double health() {
    return body.getBodyDamage().getHealth();
  }

  public List<String> wireTrace() {
    var all = trace();
    return all.subList(Math.max(0, all.size() - 6), all.size()).stream()
        .map(s -> s.substring(0, Math.min(240, s.length())))
        .toList();
  }

  public String perceptionDiagnostic() {
    return perception.diagnostic(System.nanoTime());
  }

  public String gait() {
    return actors.gait(body);
  }

  public double speed() {
    return actors.commandedSpeed(body);
  }

  public String nativeState() {
    return body.getCurrentStateName();
  }

  public String interruptReason() {
    return traversal != null && traversal.view().phase() == CivilianTraversal.Phase.BLOCKED
        ? traversal.view().reason()
        : lastInterrupt;
  }

  private void trace(long now, String reason) {
    String state =
        reason
            + " state="
            + state()
            + " native="
            + body.getCurrentStateName()
            + " hit="
            + body.getHitReaction()
            + " locked="
            + body.getIgnoreMovement()
            + " floor="
            + body.isOnFloor()
            + " grapple="
            + body.isGrappling()
            + " gait="
            + gait()
            + " route="
            + (routeKey == null ? 0 : routeKey.actionRevision())
            + " collision="
            + actors.movementFailure(body);
    if (state.equals(traceSignature)) return;
    traceSignature = state;
    if (transitions.size() == 32) transitions.removeFirst();
    transitions.addLast(now + " " + state + " xy=" + body.getX() + "," + body.getY());
  }

  KahluaTable logical() {
    return resident;
  }

  Map<Object, Object> actualPosition() {
    return Map.of("x", (double) body.getX(), "y", (double) body.getY(), "z", (double) body.getZ());
  }

  private KahluaTable goalPlan() {
    return resident.rawget("goalPlan") instanceof KahluaTable g ? g : null;
  }

  private KahluaTable currentAction() {
    var g = goalPlan();
    return g != null
            && g.rawget("actions") instanceof KahluaTable actions
            && actions.rawget(g.rawget("cursor")) instanceof KahluaTable a
        ? a
        : null;
  }

  String actionId() {
    var a = currentAction();
    return a == null ? "" : Objects.toString(a.rawget("id"), "");
  }

  String executionStatus() {
    var g = goalPlan();
    if (g != null && "complete".equals(g.rawget("status"))) return "completed";
    if (Set.of("TERMINAL", "UNRESOLVED").contains(state())) return "failed";
    if (wasReacting || Set.of("FLEE", "DEFEND", "RECOVER").contains(state())) return "paused";
    if (state().equals("BLOCKED")) return "blocked";
    if (actionId().isEmpty()) return "needs_plan";
    return state().equals("WALK") && bufferedSteps() == 0 ? "waiting_path" : "executing";
  }

  private boolean admitRoutine() {
    collectOutcomes();
    var g = goalPlan();
    var a = currentAction();
    if (g == null
        || a == null
        || g.rawget("outcomes") instanceof KahluaTable pending && pending.rawget(1d) != null)
      return false;
    return ResidentPlannerBridge.outcomes.reserve(
        new ResidentReceipts.Key(
            token.resident(),
            token.residentGeneration(),
            ((Number) g.rawget("revision")).longValue(),
            actionId()));
  }

  private void collectOutcomes() {
    var g = goalPlan();
    if (g == null
        || !(g.rawget("outcomes") instanceof KahluaTable pending)
        || pending.rawget(1d) == null) return;
    var retained = table();
    int index = 0;
    for (int i = 1; i <= 6; i++)
      if (pending.rawget((double) i) instanceof KahluaTable row) {
        var key =
            new ResidentReceipts.Key(
                token.resident(),
                ((Number) row.rawget("generation")).longValue(),
                ((Number) row.rawget("planRevision")).longValue(),
                (String) row.rawget("action"));
        if (ResidentPlannerBridge.outcomes.reserve(key))
          ResidentPlannerBridge.outcomes.finish(
              key, (String) row.rawget("state"), (String) row.rawget("reason"));
        else retained.rawset((double) ++index, row);
      }
    g.rawset("outcomes", retained);
  }

  /** Accept a newer detached long-horizon plan while the current action remains engine-safe. */
  void acceptPlan(KahluaTable plan) {
    if (terminal || body.isDead() || pool.body(token) != body) return;
    var result = call("acceptPlan", resident, plan);
    System.out.println(
        "[ResidentPlan] "
            + resident.rawget("id")
            + " accepted="
            + result.rawget("accepted")
            + " reason="
            + result.rawget("reason"));
  }

  private boolean routeSafe(long now) {
    if (traversal == null
        || committed == null
        || traversal.view().phase() != CivilianTraversal.Phase.RUNNING) return false;
    int first = traversal.view().step(), end = Math.min(committed.route().size(), first + 8);
    for (Seen threat : seen.values())
      if (now - threat.observed() < 500_000_000L && !threat.body().isDead()) {
        double
            near =
                Math.hypot(body.getX() - threat.body().getX(), body.getY() - threat.body().getY()),
            minimum = Math.min(1.5, near);
        for (int i = first; i < end; i++) {
          Tile t = committed.route().get(i).at();
          if (t.z() == (int) threat.body().getZ()
              && Math.hypot(t.x() + .5 - threat.body().getX(), t.y() + .5 - threat.body().getY())
                  < minimum) return false;
        }
      }
    return true;
  }

  public double pathAgeMs(long now) {
    return capture != null || escape != null || roam != null ? (now - pathRequestedAt) / 1e6 : 0;
  }

  public NativeResidentController(
      CivilianPool<IsoPlayer> pool,
      NativeCivilianActors actors,
      CivilianPool.Token token,
      Tile anchor) {
    this(pool, actors, token, anchor, token.slot());
  }

  public NativeResidentController(
      CivilianPool<IsoPlayer> pool,
      NativeCivilianActors actors,
      CivilianPool.Token token,
      Tile anchor,
      long seed) {
    this.pool = pool;
    this.actors = actors;
    this.token = token;
    body = Objects.requireNonNull(pool.body(token));
    if (!(LuaManager.env.rawget("AKRResidentModel") instanceof KahluaTable m))
      throw new IllegalStateException("resident_model_not_registered");
    model = m;
    var anchors = table();
    anchors.rawset(1d, point(anchor.x() + .5, anchor.y() + .5, anchor.z()));
    if (LuaManager.env.rawget("AKRResidentState") instanceof KahluaTable saved)
      resident =
          call(
              "bind",
              saved,
              token.resident(),
              (double) (Math.floorMod(seed, 2147483646L) + 1),
              position(),
              anchors,
              (double) token.residentGeneration());
    else
      resident =
          call(
              "new",
              token.resident(),
              (double) (Math.floorMod(seed, 2147483646L) + 1),
              position(),
              anchors);
    resident.rawset("generation", (double) token.residentGeneration());
    perception = new CivilianPerception(new NativeCivilianPerception(body));
    combat = new NativeCivilianCombat(pool, actors, token);
    injuries = new NativeCivilianInjuries(pool, token);
    geometry =
        new CivilianGeometryCache(
            new NativeCivilianGraphSource(body, () -> mapRevision, List.of(), List.of()),
            System::nanoTime);
    ResidentPlannerBridge.register(this);
  }

  private static KahluaTable table() {
    return LuaManager.platform.newTable();
  }

  private static KahluaTable point(double x, double y, double z) {
    var t = table();
    t.rawset("x", x);
    t.rawset("y", y);
    t.rawset("z", z);
    return t;
  }

  private KahluaTable position() {
    return point(body.getX(), body.getY(), body.getZ());
  }

  private Tile tile() {
    return new Tile(
        (int) Math.floor(body.getX()),
        (int) Math.floor(body.getY()),
        (int) Math.floor(body.getZ()));
  }

  private KahluaTable call(String name, Object... args) {
    var result =
        LuaManager.caller.protectedCall(LuaManager.thread, (LuaClosure) model.rawget(name), args);
    if (!result.isSuccess() || !(result.getFirst() instanceof KahluaTable t))
      throw new IllegalStateException("resident_model_" + name + ":" + result.getErrorString());
    return t;
  }

  public String state() {
    return Objects.toString(resident.rawget("state"), "UNKNOWN");
  }

  public boolean unresolved() {
    return state().equals("UNRESOLVED") || combat.action.phase() == CivilianCombat.Phase.UNRESOLVED;
  }

  private boolean terminal;

  void died() {
    if (terminal) return;
    terminal = true;
    stop();
    injuries.dispose();
    call("cancelAction", resident, "failed", "resident_dead");
    collectOutcomes();
    resident.rawset("state", "TERMINAL");
    resident.rawset("life", "DEAD");
    resident.rawset("newBody", false);
    resident.rawset("body", null);
    ResidentPlannerBridge.unregister(this);
  }

  private boolean fixtureMovementHold, stationaryDefenseFixture;

  void stationaryDefenseFixture() {
    stationaryDefenseFixture = true;
    holdFixtureMovement(true);
  }

  void holdFixtureMovement(boolean held) {
    fixtureMovementHold = held;
    if (held) {
      if (traversal != null) traversal.pause();
      else actors.stop(body);
    }
  }

  /** Invalidate captured geometry after a door, wall or traversal state changes. */
  public void geometryChanged() {
    NativeRoutinePaths.cancel(token);
    nativeRoutePending = false;
    mapRevision++;
    assessed = reachable = false;
    capture = null;
    escape = null;
    roam = null;
    reply = null;
    if (traversal != null) traversal.cancel();
    blocked = true;
  }

  private void clearCandidate() {
    NativeRoutinePaths.cancel(token);
    nativeRoutePending = false;
    capture = null;
    escape = null;
    roam = null;
    reply = null;
    path = null;
    searchKey = null;
    planningOrigin = null;
    assessed = reachable = false;
  }

  /** Request a bounded movement stop without discarding the retained resident goal. */
  public void stop() {
    if (traversal != null) traversal.cancel();
    clearCandidate();
    committed = null;
    combat.action.cancel("controller_stopped");
    actors.stop(body);
  }

  public int bufferedSteps() {
    return traversal == null ? 0 : traversal.buffered();
  }

  public long completedEdges() {
    return executedEdges;
  }

  public long routeRevision() {
    return routeKey == null ? 0 : routeKey.actionRevision();
  }

  /** Release this controller's planning state after the owning Actor is retired. */
  public void dispose() {
    if (body.isDead()) died();
    stop();
    injuries.dispose();
    if (Boolean.TRUE.equals(resident.rawget("planEnabled"))) {
      call("cancelAction", resident, "cancelled", "execution_disposed");
      collectOutcomes();
      call("detach", resident);
    }
    ResidentPlannerBridge.unregister(this);
    if (LuaManager.env.rawget("AKRCombatActions") instanceof KahluaTable actions)
      actions.rawset((double) token.slot(), null);
  }

  /** Caller schedules at most four adapters: eight geometry nodes and 32 A* polls each per tick. */
  /** Reconcile engine state, threats and buffered actions under one game-thread budget. */
  public void tick(long now, boolean online) {
    GameHooks.ownThread();
    long began = System.nanoTime();
    try {
      if (pool.body(token) != body) return;
      if (terminal || body.isDead()) {
        died();
        return;
      }
      if (!online) {
        stop();
        return;
      }
      int priorInjuries = injuries.attempts;
      long component = System.nanoTime();
      injuries.tick(
          seen.values().stream()
              .filter(s -> now - s.observed() < 500_000_000L)
              .map(Seen::body)
              .toList(),
          now);
      injuryWork.add(System.nanoTime() - component);
      if (body.isDead()) {
        died();
        return;
      }
      if (injuries.attempts != priorInjuries) {
        lastInterrupt = "native_attack";
        combat.action.cancel("native_attack");
        trace(now, "injury");
      }
      boolean reacting = injuries.reacting(now);
      if (reacting) {
        if (!wasReacting) pauses++;
        if (traversal != null) traversal.pause();
        else actors.stop(body);
      } else if (wasReacting) {
        resumes++;
        lastInterrupt = "reaction_completed";
        if (traversal != null) traversal.pause();
      }
      wasReacting = reacting;
      component = System.nanoTime();
      var prior = combat.action.phase();
      combat.action.tick(now);
      if (prior != CivilianCombat.Phase.COMPLETE
          && combat.action.phase() == CivilianCombat.Phase.COMPLETE) {
        completedAttacks++;
        assessed = reachable = false;
      }
      if (combat.action.phase() == CivilianCombat.Phase.UNRESOLVED)
        throw new IllegalStateException(combat.action.reason(), combat.action.failure());
      combatWork.add(System.nanoTime() - component);
      component = System.nanoTime();
      advancePath(now);
      if (fixtureMovementHold && traversal != null) traversal.pause();
      if (traversal != null && !combat.action.busy() && !reacting && !fixtureMovementHold) {
        if (traversal.view().phase() == CivilianTraversal.Phase.RUNNING && !routeSafe(now)) {
          lastInterrupt = "unsafe_route";
          stop();
          blocked = true;
        }
        long beforeEdges = traversal.completedEdges();
        float x = body.getX(), y = body.getY();
        traversal.tick(routeKey);
        executedEdges += traversal.completedEdges() - beforeEdges;
        travelled += Math.hypot(body.getX() - x, body.getY() - y);
        routeKey = traversal.view().key();
        committed = traversal.route();
        if (traversal.view().phase() == CivilianTraversal.Phase.UNRESOLVED)
          throw new IllegalStateException(traversal.view().reason());
        if (traversal.view().phase() == CivilianTraversal.Phase.BLOCKED) {
          blocked = true;
          assessed = reachable = false;
        }
      }
      navigationWork.add(System.nanoTime() - component);
      component = System.nanoTime();
      var observed = perception.sample(tile(), 32, now);
      perceptionWork.add(System.nanoTime() - component);
      if (now < nextDecision) return;
      nextDecision = now + 200_000_000L;
      decisions++;
      component = System.nanoTime();
      var o = table();
      if (nativeRoutineRouting && (capture != null || roam != null))
        o.rawset("pathProgress", (double) pathProgress);
      o.rawset("online", true);
      o.rawset("known", observed.known());
      o.rawset("position", position());
      o.rawset("dead", body.isDead());
      o.rawset("blocked", blocked);
      o.rawset("reacting", reacting);
      o.rawset("plannerPending", ResidentPlannerBridge.pending());
      blocked = false;
      o.rawset("routineAdmission", admitRoutine());
      o.rawset("admittedAction", actionId());
      boolean safe = routeSafe(now);
      o.rawset("routeSafe", safe);
      o.rawset(
          "routeRefresh",
          safe
              && !traversal.hasReplacement()
              && traversal.buffered() <= CivilianTraversal.REFILL
              && (planningGoal == null
                  || !traversal.route().route().getLast().at().equals(planningGoal)));
      o.rawset(
          "goalReached",
          planningGoal != null
              && Math.hypot(
                      body.getX() - (planningGoal.x() + .5), body.getY() - (planningGoal.y() + .5))
                  < .15);
      var threats = table();
      int index = 0;
      seen.entrySet().removeIf(e -> now - e.getValue().observed() > 3_000_000_000L);
      for (var t : observed.threats()) {
        IsoZombie z = ServerMap.instance.zombieMap.get(Short.parseShort(t.id()));
        if (z == null || z.isDead()) continue;
        var previous = seen.get(t.id());
        long gen =
            previous != null && previous.body() == z ? previous.generation() : ++targetGeneration;
        seen.put(t.id(), new Seen(z, gen, now));
        var row = table();
        row.rawset("id", t.id());
        row.rawset("generation", (double) gen);
        row.rawset("position", point(z.getX(), z.getY(), z.getZ()));
        row.rawset("zombie", true);
        row.rawset("visible", t.visible());
        row.rawset("dead", false);
        threats.rawset((double) ++index, row);
      }
      o.rawset("threats", threats);
      var c = table();
      c.rawset("known", observed.known());
      c.rawset("escapeAssessed", stationaryDefenseFixture || assessed);
      c.rawset("escapeReachable", !stationaryDefenseFixture && reachable);
      c.rawset(
          "pathPending",
          !stationaryDefenseFixture
              && (nativeRoutePending || capture != null || escape != null || roam != null));
      c.rawset("attacking", combat.action.busy());
      c.rawset("knockedDown", body.isKnockedDown() || body.isOnFloor());
      c.rawset(
          "weaponUsable",
          body.getPrimaryHandItem() instanceof HandWeapon w
              && !w.isRanged()
              && w.getCondition() > 0);
      c.rawset("endurance", (double) body.getStats().get(CharacterStat.ENDURANCE));
      o.rawset("combat", c);
      if (collection != null) {
        boolean permitted =
            !reacting
                && Boolean.TRUE.equals(o.rawget("routineAdmission"))
                && observed.known()
                && observed.threats().isEmpty()
                && Set.of("IDLE", "WALK").contains(state())
                && !combat.action.busy()
                && currentAction() != null
                && "COLLECT".equals(currentAction().rawget("kind"));
        if (collection.tick(body, actionId(), now, permitted))
          o.rawset("interactionComplete", actionId());
      }
      if (reply != null
          && reply.status() == Status.FOUND
          && (traversal != null && traversal.view().phase() == CivilianTraversal.Phase.RUNNING
              ? !approachingCandidate()
              : reply.route().stream().noneMatch(s -> s.at().equals(tile())))) {
        reply = path = new Result(reply.key(), Status.STALE, List.of(), reply.expansions(), 0);
        lastInterrupt = "candidate_origin_stale";
      }
      if (reply != null && observed.known() && !reacting) {
        var p = table();
        p.rawset("revision", (double) reply.key().actionRevision());
        p.rawset("generation", (double) token.residentGeneration());
        p.rawset(
            "status",
            reply.status() == Status.FOUND
                ? "found"
                : reply.status().name().toLowerCase(Locale.ROOT));
        p.rawset("route", table());
        o.rawset("path", p);
        reply = null;
      }
      if (traversal != null && traversal.view().phase() == CivilianTraversal.Phase.COMPLETE)
        o.rawset("arrivedRevision", (double) routeKey.actionRevision());
      var intents = call("tick", resident, now / 1e9, o);
      for (int i = 1; i <= 8; i++) {
        Object value = intents.rawget((double) i);
        if (value == null) break;
        if (!(value instanceof KahluaTable intent))
          throw new IllegalStateException("resident_intent");
        apply(intent, now);
      }
      collectOutcomes();
      trace(now, lastInterrupt);
      decisionWork.add(System.nanoTime() - component);
    } catch (Exception error) {
      resident.rawset("state", "UNRESOLVED");
      stop();
      throw new IllegalStateException("resident_execution:" + token.resident(), error);
    } finally {
      work.add(System.nanoTime() - began);
    }
  }

  /** Translate an accepted Lua intent into safe engine action boundaries. */
  private void apply(KahluaTable intent, long now) {
    long rev = ((Number) intent.rawget("revision")).longValue();
    if (rev < revision) return;
    if (rev > revision) {
      if (!pool.acceptIntent(token, rev)) throw new IllegalStateException("intent_generation");
      revision = rev;
    }
    String kind = (String) intent.rawget("kind");
    if (kind.equals("stop")) {
      if ("DEFER_TO_BOUNDARY".equals(intent.rawget("cancellation"))
          && traversal != null
          && routeSafe(now)) {
        clearCandidate();
        traversal.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);
      } else stop();
      return;
    }
    if (kind.equals("navigate")) {
      if (stationaryDefenseFixture)
        return; // Explicit watched fixture: defense/contact only, not autonomous escape
      // qualification.
      pathRequests++;
      pathRequestedAt = now;
      assessed = reachable = false;
      planningOrigin = tile();
      planningThreats = new ArrayList<>();
      if ("flee".equals(intent.rawget("mode"))) {
        var ts = (KahluaTable) intent.rawget("threats");
        for (int i = 1; i <= 32; i++) {
          if (!(ts.rawget((double) i) instanceof KahluaTable t)) break;
          planningThreats.add(readPoint((KahluaTable) t.rawget("position")));
        }
        planningGoal = null;
        if (routeSafe(now) && !traversal.hasReplacement())
          planningOrigin =
              traversal.endingAtBoundary()
                  ? traversal.nextBoundary().at()
                  : traversal.route().route().getLast().at();
      } else {
        planningGoal = readPoint((KahluaTable) intent.rawget("target"));
        if (routeSafe(now) && traversal.nextBoundary() != null)
          planningOrigin =
              traversal.endingAtBoundary()
                  ? traversal.nextBoundary().at()
                  : traversal.route().route().getLast().at();
      }
      searchKey = new Key(token, revision, mapRevision);
      pathProgress = 0;
      if (nativeRoutineRouting && planningGoal != null) {
        capture = null;
        escape = null;
        roam = null;
        reply = null;
        nativeRoutePending =
            NativeRoutinePaths.submit(searchKey, body, planningOrigin, planningGoal, now);
        if (!nativeRoutePending)
          reply = path = new Result(searchKey, Status.BUDGET_EXHAUSTED, List.of(), 0, 0);
        return;
      }
      captureRadius =
          planningGoal == null
              ? 3
              : Math.min(8, Math.max(1, (int) Math.ceil(planningOrigin.distance(planningGoal))));
      capture = new CivilianGraphCapture(geometry, planningOrigin, captureRadius);
      escape = null;
      roam = null;
      return;
    }
    if (kind.equals("follow")) {
      if (path == null || path.status() != Status.FOUND || path.key().actionRevision() != revision)
        throw new IllegalStateException("path_receipt_mismatch");
      if (traversal != null && traversal.replaceAtBoundary(path)) {
        movementPort.speed(
            "RUN".equals(intent.rawget("gait")) || state().equals("FLEE") ? 2.6 : 1.45);
        committed = traversal.route();
        routeReplacements++;
        return;
      }
      // A fresh route can start at the actual tile. Moving replacements must join
      // through the existing port at a physically reached boundary.
      int join = -1;
      for (int i = 0; i < path.route().size(); i++)
        if (path.route().get(i).at().equals(tile())) join = i;
      if (join < 0) {
        lastInterrupt = "candidate_origin_stale";
        blocked = traversal == null;
        return;
      }
      if (traversal != null && traversal.view().phase() == CivilianTraversal.Phase.RUNNING) {
        lastInterrupt = "candidate_origin_stale";
        return;
      }
      var joined = new ArrayList<>(path.route().subList(join, path.route().size()));
      if (joined.size() == 1
          && Math.hypot(
                  body.getX() - (joined.getFirst().at().x() + .5),
                  body.getY() - (joined.getFirst().at().y() + .5))
              >= .01) joined.add(new Step(joined.getFirst().at(), Action.WALK));
      committed =
          new Result(
              path.key(),
              path.status(),
              List.copyOf(joined.subList(0, Math.min(joined.size(), CivilianTraversal.WINDOW + 1))),
              path.expansions(),
              path.cost());
      movementPort = new NativeCivilianTraversal(token, body, actors);
      movementPort.speed(
          "RUN".equals(intent.rawget("gait")) || state().equals("FLEE") ? 2.6 : 1.45);
      traversal = new CivilianTraversal(movementPort);
      traversal.start(committed);
      routeKey = committed.key();
      routeReplacements++;
      return;
    }
    if (kind.equals("defend")) {
      var t = seen.get(Objects.toString(intent.rawget("target")));
      Object gen = intent.rawget("targetGeneration");
      if (t == null
          || !(gen instanceof Number n)
          || n.longValue() != t.generation()
          || now - t.observed() > 500_000_000L) return;
      if (combat.defend(
          revision,
          t.body(),
          t.generation(),
          "melee".equals(intent.rawget("style"))
              ? CivilianCombat.Style.MELEE
              : CivilianCombat.Style.SHOVE,
          now)) defenses++;
      return;
    }
    throw new IllegalStateException("unknown_resident_intent:" + kind);
  }

  private static Tile readPoint(KahluaTable p) {
    return new Tile(
        (int) Math.floor(((Number) p.rawget("x")).doubleValue()),
        (int) Math.floor(((Number) p.rawget("y")).doubleValue()),
        ((Number) p.rawget("z")).intValue());
  }

  private boolean approachingCandidate() {
    return traversal != null && reply != null && traversal.canReplace(reply);
  }

  private void advancePath(long now) {
    if (nativeRoutePending) {
      var nativeReply = NativeRoutinePaths.poll(token, now);
      if (nativeReply == null) return;
      nativeRoutePending = false;
      nativeQueue.add(nativeReply.queueNanos());
      if (!nativeReply.key().equals(searchKey) || searchKey.mapRevision() != mapRevision) return;
      if (nativeReply.status() != Status.FOUND) {
        lastInterrupt = "native_route:" + nativeReply.reason();
        reply = path = new Result(searchKey, nativeReply.status(), List.of(), 0, 0);
        return;
      }
      var inspected = NativeCivilianGeometry.inspect(body, nativeReply.points());
      lastInterrupt = "native_inspection:" + inspected.reason();
      boolean allowed =
          inspected.reason().isEmpty()
              && inspected.steps().stream()
                  .allMatch(s -> NativeCivilianGeometry.executableActions().contains(s.action()));
      if (!allowed) {
        rejectedNativeRoutes++;
        for (var step : inspected.steps())
          if (!NativeCivilianGeometry.executableActions().contains(step.action()))
            pendingTraversal.add(step.action());
        lastInterrupt =
            "native_route_unsupported:"
                + inspected.reason()
                + ":"
                + inspected.steps().stream().map(s -> s.action()).distinct().toList();
        // Native routes may use windows or fences. Search the bounded loaded neighborhood
        // for a walk/door alternative rather than executing unsupported traversal.
        captureRadius = 16;
        capture = new CivilianGraphCapture(geometry, planningOrigin, captureRadius);
        reply = path = null;
      } else {
        nativeRoutes++;
        reply = path = new Result(searchKey, Status.FOUND, inspected.steps(), 0, 0);
      }
      return;
    }

    if (capture != null) {
      pathProgress += capture.advance(nativeRoutineRouting && planningGoal != null ? 32 : 8);
      if (!capture.complete()) return;
      var graph = capture.result();
      boolean limited = capture.limited();
      captureUnknown = capture.unknown();
      capture = null;
      if (limited) {
        reply = path = new Result(searchKey, Status.BUDGET_EXHAUSTED, List.of(), 0, 0);
        return;
      }
      if (planningGoal == null && !planningThreats.isEmpty())
        escape =
            new CivilianEscape(
                searchKey,
                graph,
                planningOrigin,
                planningThreats,
                NativeCivilianGeometry.executableActions());
      else if (planningGoal != null) {
        Tile goal =
            graph.edges().containsKey(planningGoal)
                ? planningGoal
                : graph.edges().keySet().stream()
                    .min(
                        Comparator.comparingDouble((Tile t) -> t.distance(planningGoal))
                            .thenComparing(t -> t))
                    .orElse(planningOrigin);
        roam =
            new Search(
                searchKey, graph, planningOrigin, goal, NativeCivilianGeometry.executableActions());
      } else {
        reply = path = new Result(searchKey, Status.STALE, List.of(), 0, 0);
        return;
      }
    }
    Result result = null;
    if (escape != null) {
      escape.advance(32, mapRevision);
      result = escape.result();
    } else if (roam != null) {
      roam.advance(32, mapRevision);
      pathProgress++;
      result = roam.result();
    }
    if (result == null || result.status() == Status.PENDING) return;
    if (planningGoal == null && result.status() == Status.NO_PATH && captureRadius < 8) {
      captureRadius = 8;
      capture = new CivilianGraphCapture(geometry, planningOrigin, 8);
      escape = null;
      roam = null;
      return;
    }
    if (captureUnknown && result.status() == Status.NO_PATH)
      result = new Result(result.key(), Status.STALE, List.of(), result.expansions(), 0);
    if (escape != null) {
      assessed = result.status() == Status.FOUND || result.status() == Status.NO_PATH;
      reachable = result.status() == Status.FOUND;
      if (reachable) fleeRoutes++;
    }
    if (nativeRoutineRouting && result.status() == Status.FOUND) groundAlternatives++;
    if (nativeRoutineRouting)
      lastInterrupt = "local_route:" + result.status() + ":expansions=" + result.expansions();
    reply = path = result;
    escape = null;
    roam = null;
  }
}
