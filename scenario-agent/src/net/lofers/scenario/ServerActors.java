package net.lofers.scenario;

import java.util.*;
import zombie.VirtualZombieManager;
import zombie.ai.State;
import zombie.ai.states.BumpedState;
import zombie.network.packets.hit.ZombieHitPlayerPacket;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoDirections;
import zombie.iso.LosUtil;
import zombie.iso.IsoMovingObject;
import zombie.characters.NetworkPlayerVariables;
import zombie.characters.SurvivorDesc;
import zombie.characters.SurvivorFactory;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.network.GameServer;
import zombie.network.PacketTypes.PacketType;
import zombie.network.ServerMap;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.character.PlayerPacket;
import zombie.pathfind.PolygonalMap2;
import zombie.popman.NetworkZombiePacker;
import net.lofers.scenario.protocol.RuntimeControl.*;

/**
 * Actor pool v0 (Decision 0006, gate 1). An Actor is a connectionless server {@link IsoPlayer}
 * that other clients see through stock player packets. It is registered only in
 * {@code GameServer.IDToPlayerMap} under a reserved online ID, never in {@code GameServer.Players}
 * or a connection, so it takes no player slot and loads no chunks. The stock server purges every
 * {@code IsoPlayer} missing from {@code Players} from the cell object list each frame, so an Actor
 * stays out of the stock update loop: it is a puppet placed on its square's moving objects (as the
 * stock remote-player update would place it) so spatial queries, collisions and zombies find it.
 * Gate 1 Actors stand still. Gate 2 Actors walk the route with a bounded server path follower
 * (the stock server never simulates player locomotion; clients animate remote players from
 * movement predictions), so the server speed must match the client walk animation.
 */
public final class ServerActors implements RuntimeSession.Backend {
    /** Real player IDs are slot*4+index with at most 255 slots (<=1019); this range cannot collide. */
    static final short ID_BASE = 4096;
    static final int POOL = 4;
    private static final String[] OUTFITS = {"Generic01", "Tourist", "Student", "Young", "Classy", "Generic02"};
    private static final long PACKET_INTERVAL = 250_000_000L, MOVING_INTERVAL = 100_000_000L,
        RELIABLE_INTERVAL = 1_000_000_000L, REANNOUNCE_DELAY = 1_000_000_000L;
    /** Stock clients extrapolate a moving prediction for about 1.2 s (NetworkPlayerAI.setMoving). */
    private static final double LOOKAHEAD_SECONDS = 1.2;
    private static ServerActors instance;

    private static final class Body {
        final IsoPlayer player;
        final int identity;
        final String outfit;
        final double createMs;
        final int worn;
        Body(IsoPlayer player, int identity, String outfit, double createMs, int worn) {
            this.player = player; this.identity = identity; this.outfit = outfit; this.createMs = createMs; this.worn = worn;
        }
    }
    private static final class Slot {
        final short onlineId;
        final String id;
        final EventResources.Resource resource;
        final Set<Long> announced = new HashSet<>();
        final ProbeTiming nativeTiming = new ProbeTiming();
        Body body;
        long nativeStepAt, lastPacket, lastReliable, silentUntil, lastStep, arrivedAt;
        int packets, announcements, reassignments, nativeFaults, placementRepairs, pushes, stateFaults, waypoint = 1;
        double walked, angle, speed;
        boolean moving, running;
        /** Current movement path in world coordinates; index `waypoint` is the next target. */
        List<float[]> path;
        long fleeAt, tripAt, flooredAt, getUpAt;
        double tripGap = -1;
        int ticks, statePackets, hitPackets;
        String action = "PREPARING";
        Slot(short onlineId, String id) {
            this.onlineId = onlineId; this.id = id;
            resource = new EventResources.Resource(EventResources.Kind.ACTOR, id);
        }
    }
    /**
     * Gate 3: one stock hostile zombie, simulated on the server (the only side that can apply a
     * bite to a connectionless player), spotted and driven entirely by stock zombie AI.
     */
    private static final class Hunter {
        final IsoZombie zombie;
        final short onlineId;
        long targetedAt, contactAt, ownedAt, attackAt;
        boolean bitThisEntry;
        int biteMisses;
        int spotCalls, attackSuccess, attackFail, attackInterrupted, reactions, damagingHits, lastWounds, realTargets,
            controls, attackEntries, ownerChanges;
        boolean owned, wasAttacking;
        long lastControl;
        UdpConnection lastOwner;
        boolean lastRealGhost, lastRealInvisible;
        float lastHealth = 100;
        String outcome = "";
        int lastSinceAttack = Integer.MAX_VALUE;
        boolean removalQueued;
        Hunter(IsoZombie zombie) { this.zombie = zombie; onlineId = zombie.getOnlineID(); }
    }
    private Hunter hunter;
    /** Owner connection of the live hunter (game thread); read by the NetworkZombiePacker hook. */
    private static UdpConnection hunterOwner;
    public static boolean neighborPlayer(boolean stock, UdpConnection connection) {
        return stock || (connection != null && connection == hunterOwner) || WatchedHunters.neighborPlayer(connection) || NativeCivilianInjuries.neighborPlayer(connection);
    }
    private EventResources.Resource hunterResource;
    /** Replaced bodies stay listed until their removal is verified, never assumed. */
    private final List<IsoPlayer> retiring = new ArrayList<>();
    private final Set<IsoPlayer> constructed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<IsoPlayer, IsoGridSquare> retirementSquares = new IdentityHashMap<>();
    private final Set<IsoPlayer> removalAnnounced = Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<IsoPlayer, Slot> bindings = new IdentityHashMap<>();
    private Slot slot;
    private EventScheduler scheduler;
    private EventScheduler.View event;
    private PedestrianDefinition definition;
    private long seed, started, spawnedAt;
    private boolean acquired;

    ServerActors() { instance = this; }

    /** True only for the live body of a pooled Actor (never a real player). */
    public static boolean isActor(IsoPlayer player) {
        return ScenarioAgent.server && ((instance != null && instance.bindings.containsKey(player)) || NativeCivilianActors.owns(player));
    }

    // ---- sentinel: stock IsoPlayer.update/postupdate must never run for an Actor (hooked by ScenarioTransformer) ----
    public static void beginNativeStep(IsoPlayer player) {
        NativeCivilianActors.noteStockUpdate(player);
        if (instance == null || !ScenarioAgent.server) return;
        Slot slot = instance.bindings.get(player);
        if (slot == null) return;
        // A begin without the matching end means the previous stock update threw.
        if (slot.nativeStepAt != 0) slot.nativeFaults++;
        slot.nativeStepAt = System.nanoTime();
    }
    public static void endNativeStep(IsoPlayer player) {
        if (instance == null || !ScenarioAgent.server) return;
        Slot slot = instance.bindings.get(player);
        if (slot != null && slot.nativeStepAt != 0) {
            slot.nativeTiming.add(System.nanoTime() - slot.nativeStepAt); slot.nativeStepAt = 0;
        }
    }

    @Override public void begin(EventScheduler.View event, EventScheduler scheduler) {
        if (slot != null || acquired || !retiring.isEmpty() || !constructed.isEmpty() || hunterResource != null)
            throw new IllegalStateException("unresolved_previous_actor");
        this.event = event; this.scheduler = scheduler; definition = event.definition().pedestrian();
        seed = event.definition().seed(); started = System.nanoTime(); spawnedAt = 0;
    }

    @Override public boolean prepare() {
        GameHooks.ownThread();
        if (definition.entity() != PedestrianDefinition.Entity.ACTOR) throw new IllegalStateException("actor_definition_required");
        var origin = definition.route().getFirst();
        IsoGridSquare square = ServerMap.instance.getGridSquare((int)origin.x(), (int)origin.y(), 0);
        // Materialize only into already loaded, free squares; an Actor never loads chunks.
        if (square == null || !square.isFree(false)) throw new IllegalStateException("spawn_square_unavailable");
        if (definition.walkTilesPerSecond() > 0) validateRoute();
        short onlineId = ID_BASE;
        if (GameServer.IDToPlayerMap.containsKey(onlineId)) throw new IllegalStateException("actor_id_in_use");
        slot = new Slot(onlineId, event.id() + ".actor0");
        scheduler.acquire(event.id(), slot.resource); acquired = true;
        materialize(0, origin.x() + 0.5, origin.y() + 0.5);
        spawnedAt = System.nanoTime(); slot.action = "STAND";
        if (definition.walkTilesPerSecond() > 0) {
            slot.path = new ArrayList<>();
            for (var point : definition.route()) slot.path.add(new float[]{(float)point.x() + 0.5f, (float)point.y() + 0.5f});
            slot.speed = definition.walkTilesPerSecond();
        }
        if (definition.hunterZombies() > 0) spawnHunter();
        return true;
    }

    private void spawnHunter() {
        var point = definition.route().getLast();
        IsoGridSquare square = ServerMap.instance.getGridSquare((int)point.x(), (int)point.y(), 0);
        if (square == null || !square.isFree(false)) throw new IllegalStateException("hunter_square_unavailable");
        hunterResource = new EventResources.Resource(EventResources.Kind.ZOMBIE, event.id() + ".hunter0");
        scheduler.acquire(event.id(), hunterResource);
        IsoPlayer actor = slot.body.player;
        // Spawn orientation only: face the Actor. Spotting itself is the stock chance model.
        IsoDirections facing = IsoDirections.fromAngle(actor.getX() - (square.x + 0.5f), actor.getY() - (square.y + 0.5f));
        var factory = VirtualZombieManager.instance;
        IsoZombie zombie;
        factory.choices.clear(); factory.choices.add(square);
        try { zombie = GameHooks.withSpawnPermit(() -> factory.createRealZombieAlways(facing, false)); }
        finally { factory.choices.clear(); }
        if (zombie == null) throw new IllegalStateException("hunter_spawn_refused");
        hunter = new Hunter(zombie);
        if (zombie.getOnlineID() < 0) throw new IllegalStateException("hunter_without_network_identity");
        // Client-owned hunter (user decision): no server simulation binding. Stock ownership hands
        // it to the nearest real client, which runs chase and attack at full client fidelity; every
        // other client sees the stock zombie stream. The server only directs the target.
        zombie.setUseless(false); zombie.setTarget(null);
        // Deterministic stock speed setup (the sandbox default picks randomly per zombie).
        switch (definition.hunterSpeed()) {
            case SPRINTER -> zombie.doSprinter();
            case FAST_SHAMBLER -> zombie.doFastShambler();
            case SHAMBLER -> zombie.doShambler();
            case SANDBOX -> { }
        }
    }

    /**
     * Stock spotting, as the server's per-player LOS pass would call it, bounded to one hunter
     * and one Actor. The first wound disengages the hunter so the Actor survives (death is gate 4).
     */
    private void hunt(IsoPlayer actor, long now) {
        IsoZombie zombie = hunter.zombie;
        if (zombie.isDead()) throw new IllegalStateException("hunter_died");
        if (zombie.getSquare() == null) throw new IllegalStateException("hunter_unloaded");
        UdpConnection owner = zombie.getOwner();
        hunter.owned = owner != null;
        hunterOwner = owner;
        // Relevant only while a real player is near: without an owner the zombie is not simulated.
        if (owner == null) return;
        if (owner != hunter.lastOwner) { hunter.lastOwner = owner; hunter.ownerChanges++; hunter.lastControl = 0; }
        if (hunter.ownedAt == 0) hunter.ownedAt = now;
        // Warm-up: both bodies load and present on the owner before the chase is directed.
        if (now - hunter.ownedAt < definition.chaseDelaySeconds() * 1_000_000_000L) return;
        // The server copy mirrors the owner's replicated target and state (NetworkZombiePacker).
        IsoMovingObject target = zombie.getTarget();
        if (target instanceof IsoPlayer other && other != actor) hunter.realTargets++;
        if (hunter.contactAt == 0 && (target != actor || now - hunter.lastControl >= 1_000_000_000L)) {
            // Stock server-to-owner targeting message (sent by stock TestZombieSpotPlayer).
            INetworkPacket.send(owner, PacketType.ZombieControl, zombie, actor);
            hunter.controls++; hunter.lastControl = now;
        }
        if (target == actor && hunter.targetedAt == 0) hunter.targetedAt = now;
        String state = zombie.getRealState();
        boolean attacking = attackState.equals(state);
        if (!state.equals(hunter.outcome)) hunter.outcome = state;
        if (attacking && !hunter.wasAttacking) {
            hunter.attackEntries++; hunter.attackAt = now; hunter.bitThisEntry = false;
        }
        if (!attacking && hunter.wasAttacking && !hunter.bitThisEntry && hunter.contactAt == 0) hunter.biteMisses++;
        // The owner's bite lands (stock AttackCollisionCheck) about 0.5 s into its attack; the server
        // copy learns of the attack a packet later. Judge once per attack, on the owner's position.
        if (attacking && !hunter.bitThisEntry && target == actor && hunter.contactAt == 0
            && now - hunter.attackAt >= BITE_DELAY && bite(actor, zombie, now)) hunter.bitThisEntry = true;
        hunter.wasAttacking = attacking;
    }

    private static final long BITE_DELAY = 350_000_000L;
    private static final double TRIP_GAP = 3.0;
    /** Stock triggerPlayerReaction reach (1.0) plus one owner packet of travel for a sprinter. */
    private static final double BITE_REACH = 1.5;

    private static final String attackState = zombie.network.NetworkVariables.ZombieState.Attack.toString();

    /**
     * The owner's replicated attack reached the Actor. A client only reports bites on its own
     * local player, so apply what stock Bite.process applies for a reported hit, on the server,
     * after validating range and line of sight, then relay the stock hit to observers.
     */
    private boolean bite(IsoPlayer actor, IsoZombie zombie, long now) {
        // The server copy's position is the owner's last report (stock NetworkZombiePacker.applyZombie).
        if (actor.isOnFloor() || Math.hypot(zombie.getX() - actor.getX(), zombie.getY() - actor.getY()) > BITE_REACH) return false;
        var los = LosUtil.lineClear(zombie.getCell(), (int)Math.floor(zombie.getX()), (int)Math.floor(zombie.getY()),
            (int)Math.floor(zombie.getZ()), (int)Math.floor(actor.getX()), (int)Math.floor(actor.getY()), (int)Math.floor(actor.getZ()), false);
        if (los == LosUtil.TestResults.Blocked || los == LosUtil.TestResults.ClearThroughClosedDoor) return false;
        int woundsBefore = wounds(actor); float healthBefore = actor.getBodyDamage().getHealth();
        actor.setAttackedBy(zombie);
        actor.getBodyDamage().AddRandomDamageFromZombie(zombie, null, -1);
        actor.getBodyDamage().Update();
        hunter.reactions++;
        boolean damaged = wounds(actor) > woundsBefore || actor.getBodyDamage().getHealth() < healthBefore - 0.01f;
        sendHit(actor, zombie, damaged);
        if (damaged) { hunter.damagingHits++; hunter.contactAt = now; slot.action = "WOUNDED"; }
        else if (hunter.reactions >= 5) { hunter.contactAt = now; slot.action = "DEFENDED"; }
        return true;
    }

    private static String animState(IsoZombie zombie) {
        var player = zombie.getAnimationPlayer();
        if (player == null || !player.isReady()) return ";anim=none";
        var tracks = new StringBuilder();
        var list = player.getMultiTrack().getTracks();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            var track = list.get(i);
            tracks.append(track.getName()).append(':').append(String.format("%.2f", track.getBlendWeight()))
                .append(track.getUseDeferredRotation() ? ":rot" : "").append('|');
        }
        return ";animAngle=" + player.getAngle() + ";animTarget=" + player.getTargetAngle()
            + ";fwdAngle=" + zombie.getForwardDirection().getDirection() + ";defRotW=" + player.getDeferredRotationWeight()
            + ";defRotA=" + player.getDeferredAngleDelta() + ";angleStep=" + player.angleStepDelta
            + ";turning=" + zombie.isTurning() + ";tracks=" + tracks;
    }
    private static float nearestPlayer(IsoPlayer actor) {
        float best = Float.POSITIVE_INFINITY;
        for (IsoPlayer p : GameServer.Players)
            best = Math.min(best, (float)Math.hypot(p.getX() - actor.getX(), p.getY() - actor.getY()));
        return best;
    }
    private static int holes(IsoPlayer actor) {
        int count = 0;
        var visuals = new zombie.core.skinnedmodel.visual.ItemVisuals();
        actor.getWornItems().getItemVisuals(visuals);
        for (int i = 0; i < visuals.size(); i++) count += visuals.get(i).getHolesNumber();
        return count;
    }
    private static int wounds(IsoPlayer actor) {
        int count = 0;
        for (var part : actor.getBodyDamage().getBodyParts())
            if (part.bitten() || part.scratched() || part.isCut() || part.deepWounded()) count++;
        return count;
    }

    /** Straight segments between tile centres; any stock collision on a segment refuses the route. */
    private void validateRoute() {
        var route = definition.route();
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i - 1); var b = route.get(i);
            if (ServerMap.instance.getGridSquare((int)b.x(), (int)b.y(), 0) == null)
                throw new IllegalStateException("route_unloaded");
            if (PolygonalMap2.instance.lineClearCollide((float)a.x() + 0.5f, (float)a.y() + 0.5f,
                    (float)b.x() + 0.5f, (float)b.y() + 0.5f, 0, null, false, true))
                throw new IllegalStateException("route_blocked");
        }
    }

    private Body create(int identity, double x, double y) {
        long begin = System.nanoTime();
        boolean female = ((seed + identity) & 1) == 1;
        SurvivorDesc desc = SurvivorFactory.CreateSurvivor(SurvivorFactory.SurvivorType.Neutral, female);
        SurvivorFactory.randomName(desc);
        IsoPlayer player = new IsoPlayer(IsoWorld.instance.currentCell, desc, (int)x, (int)y, 0);
        constructed.add(player); // before clothing/placement can throw
        player.setOnlineID(slot.onlineId);
        // The character constructor queues itself on the cell; an Actor never enters the stock
        // update loop (the server would purge it as a disconnected player one frame later).
        var cell = IsoWorld.instance.currentCell;
        cell.getObjectList().remove(player); cell.getAddList().remove(player);
        zombie.MovingObjectUpdateScheduler.instance.removeObject(player);
        String outfit = OUTFITS[(int)Math.floorMod(seed + identity, (long)OUTFITS.length)];
        // Stock outfit visuals become real worn items: the server serializes worn items,
        // not preview visuals, in the player-connected packet.
        player.dressInNamedOutfit(outfit);
        player.getWornItems().setFromItemVisuals(player.getItemVisuals());
        player.getWornItems().addItemsToItemContainer(player.getInventory());
        int worn = player.getWornItems().size();
        if (worn == 0) throw new IllegalStateException("actor_undressed");
        player.setOnlineID(slot.onlineId);
        // Unique, never registered in UserNameToPlayerMap; it cannot shadow a real account.
        player.setUsername("akr-actor-" + slot.onlineId);
        player.setDisplayName(desc.getForename() + " " + desc.getSurname());
        player.remote = true;
        // Position, next and last together (stock setForceX/Y): collision separation and zombie
        // facing read next/last positions, so a stale next position is a phantom body.
        player.setForceX((float)x); player.setForceY((float)y); player.setZ(0); player.setLastZ(0);
        player.realx = (float)x; player.realy = (float)y; player.realz = 0;
        return new Body(player, identity, outfit, (System.nanoTime() - begin) / 1_000_000.0, worn);
    }

    private void materialize(int identity, double x, double y) {
        Body body = create(identity, x, y);
        IsoPlayer player = body.player;
        // Track before registering so a partial failure is still found and removed by cleanup.
        slot.body = body; bindings.put(player, slot); slot.announced.clear();
        GameServer.IDToPlayerMap.put(slot.onlineId, player);
        place(player);
    }

    /** Same placement as the stock server remote-player update, without its update loop. */
    private static void place(IsoPlayer player) {
        player.setCurrentSquareFromPosition();
        player.setMovingSquareNow();
        if (!onSquare(player)) throw new IllegalStateException("actor_unplaced");
    }
    private static boolean onSquare(IsoPlayer player) {
        IsoGridSquare square = player.getCurrentSquare();
        return square != null && square.getMovingObjects().contains(player);
    }

    @Override public boolean update() {
        GameHooks.ownThread();
        long now = System.nanoTime();
        if (now - started > definition.timeoutSeconds() * 1_000_000_000L) throw new IllegalStateException("actor_timeout");
        IsoPlayer player = slot.body.player;
        if (slot.nativeFaults > 0) throw new IllegalStateException("actor_native_update_fault");
        if (slot.nativeTiming.count > 0) throw new IllegalStateException("actor_in_stock_update");
        if (GameServer.IDToPlayerMap.get(slot.onlineId) != player) throw new IllegalStateException("actor_id_lost");
        if (player.isDead()) throw new IllegalStateException("actor_died");
        slot.ticks++;
        // Stock remote players are re-placed on every server update; do the same and count repairs.
        if (!onSquare(player)) { slot.placementRepairs++; place(player); }
        var cell = IsoWorld.instance.currentCell;
        if (cell.getObjectList().contains(player) || cell.getAddList().contains(player))
            throw new IllegalStateException("actor_in_stock_update");
        long elapsed = now - spawnedAt;
        int reassign = definition.reassignAfterSeconds();
        if (reassign > 0 && slot.reassignments == 0 && elapsed >= reassign * 1_000_000_000L) {
            reassign(now);
            return false;
        }
        // Stock body contact: a player's own update runs separate() so zombies and players push
        // each other apart. The puppet skips the stock update, so run it here and apply the
        // engine's resulting position (the engine is authoritative for physical facts).
        // Bodies push apart except while the Actor lies on the floor after a trip.
        boolean changed = !player.isOnFloor() && separate(player);
        if (hunter != null) {
            hunt(player, now);
            changed |= fleeAndTrip(player, now);
        }
        boolean moving = slot.path != null && slot.arrivedAt == 0 && slot.tripAt == 0;
        if (moving) changed |= step(player, now);
        moving = slot.path != null && slot.arrivedAt == 0 && slot.tripAt == 0;
        if (now >= slot.silentUntil && (changed || now - slot.lastPacket >= (moving ? MOVING_INTERVAL : PACKET_INTERVAL))) {
            boolean reliable = changed || !moving || now - slot.lastReliable >= RELIABLE_INTERVAL;
            slot.lastPacket = now;
            if (reliable) slot.lastReliable = now;
            replicate(player, moving, reliable);
        }
        long hold = definition.holdSeconds() * 1_000_000_000L;
        if (hunter != null) return hunter.contactAt != 0 && now - hunter.contactAt >= hold;
        if (slot.path != null) return slot.arrivedAt != 0 && now - slot.arrivedAt >= hold;
        return elapsed >= hold;
    }

    /**
     * Baked flee and trip (user-requested test): once the hunter targets the Actor it runs
     * straight away from it, then trips with the stock bumped fall, lies briefly and stands up so
     * the stock zombie attack can land (stock attacks never damage a floored victim). Observers see
     * the trip through the stock StatePacket a player's own client would send.
     */
    private boolean fleeAndTrip(IsoPlayer player, long now) {
        double flee = definition.fleeTilesPerSecond();
        if (flee <= 0) return false;
        IsoZombie zombie = hunter.zombie;
        if (slot.fleeAt == 0) {
            if (hunter.targetedAt == 0) return false;
            float px = player.getX(), py = player.getY();
            double dx = px - zombie.getX(), dy = py - zombie.getY(), d = Math.hypot(dx, dy);
            if (d < 1e-3) { dx = -1; dy = 0; d = 1; }
            float tx = px, ty = py;
            // Long enough to run until the trip (and on a little), bounded to nearby loaded ground.
            double len = Math.min(30, Math.max(12, Math.ceil(flee * (definition.tripAfterSeconds() + 2))));
            for (; len >= 3; len -= 1) {
                tx = (float)(px + dx / d * len); ty = (float)(py + dy / d * len);
                if (ServerMap.instance.getGridSquare((int)tx, (int)ty, 0) != null
                    && !PolygonalMap2.instance.lineClearCollide(px, py, tx, ty, 0, null, false, true)) break;
            }
            if (len < 3) throw new IllegalStateException("flee_blocked");
            slot.path = new ArrayList<>(List.of(new float[]{px, py}, new float[]{tx, ty}));
            slot.waypoint = 1; slot.speed = flee; slot.running = true; slot.moving = false; slot.arrivedAt = 0;
            slot.fleeAt = now; slot.action = "FLEE";
            return true;
        }
        int trip = definition.tripAfterSeconds();
        if (trip <= 0) return false;
        // Trip as the hunter closes in (its owner reports every 200 ms), so it reaches the Actor
        // during the stumble instead of attacking a runner (a stock sprinter attack also slows its
        // victim on the owner only, splitting the runner's position); the timer is the bound.
        double gap = Math.hypot(zombie.getX() - player.getX(), zombie.getY() - player.getY());
        boolean closing = gap <= TRIP_GAP && now - slot.fleeAt >= 1_000_000_000L;
        if (slot.tripAt == 0 && (closing || now - slot.fleeAt >= trip * 1_000_000_000L || slot.arrivedAt != 0)) {
            slot.tripAt = now; slot.moving = false; slot.running = false; slot.action = "TRIPPED"; slot.tripGap = gap;
            // Stock bump setup (as IsoPlayer's drunk-sprint trip writes it). The fall variant runs
            // the stock run bump -> stumble -> fall on front; the default is the stock forward
            // stagger (bumped, BumpType stagger, pushed from behind, no fall), so the arriving
            // hunter meets a standing Actor and the stock bite (not the floored eat) applies.
            boolean fall = definition.tripFall();
            String type = fall ? "left" : "stagger", fallType = fall ? "pushedFront" : "pushedBehind";
            player.setVariable("BumpDone", false); player.clearVariable("BumpFallType");
            player.setBumpType(type); player.setBumpFall(fall); player.setBumpFallType(fallType);
            player.setVariable("TripObstacleType", "zombie");
            try { player.changeState(BumpedState.instance()); }
            catch (RuntimeException ignored) { slot.stateFaults++; }
            // The puppet is a remote player on the server, so stock BumpedState.setParams takes its
            // "remote" branch and reads the bump from the state params (clearing it) instead of
            // writing it. Write the stock params directly; clients then setBumpType(type) and their
            // "bumped" variable starts the stock bump chain.
            player.set(BumpedState.BUMP_TYPE, type);
            player.set(BumpedState.BUMP_FALL_TYPE, fallType);
            player.set(BumpedState.BUMP_FALL, fall);
            sendState(player, BumpedState.instance(), State.Stage.Enter);
            return true;
        }
        if (!definition.tripFall()) {
            // Stock Bob_StaggerForward (speed 0.8) then idle; exit clears the bump on clients.
            if (slot.tripAt != 0 && slot.getUpAt == 0 && now - slot.tripAt >= 1_600_000_000L) {
                slot.getUpAt = now; slot.action = "RECOVERED";
                sendState(player, BumpedState.instance(), State.Stage.Exit);
                try { player.setDefaultState(); } catch (RuntimeException ignored) { slot.stateFaults++; }
                player.setBumpFall(false); player.setBumpType("");
                return true;
            }
            return false;
        }
        if (slot.tripAt != 0 && slot.flooredAt == 0 && now - slot.tripAt >= 1_300_000_000L) {
            slot.flooredAt = now; player.setOnFloor(true); slot.action = "FALLEN";
            return true;
        }
        if (slot.flooredAt != 0 && slot.getUpAt == 0 && now - slot.flooredAt >= 2_000_000_000L) {
            slot.getUpAt = now; slot.action = "STOOD_UP";
            // Stock exit clears the bump on clients (setBumpType("")) so it cannot loop.
            player.set(BumpedState.BUMP_FALL, false);
            sendState(player, BumpedState.instance(), State.Stage.Exit);
            try { player.setDefaultState(); } catch (RuntimeException ignored) { slot.stateFaults++; }
            player.setOnFloor(false); player.setBumpFall(false); player.setBumpType("");
            return true;
        }
        return false;
    }

    /** The stock character-state message a player's own client would send, authored for an Actor. */
    private void sendState(IsoPlayer player, State state, State.Stage stage) {
        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if (connection == null || !connection.isFullyConnected() || !connection.isRelevantTo(player.getX(), player.getY())) continue;
            INetworkPacket.send(connection, PacketType.State, player, state, stage);
            slot.statePackets++;
        }
    }

    /**
     * The stock zombie-hit message a victim's client would report, relayed by the server to
     * observers so they play the Actor's hit reaction; damage was already applied on the server.
     */
    private void sendHit(IsoPlayer player, IsoZombie zombie, boolean damaged) {
        int part = -1;
        var parts = player.getBodyDamage().getBodyParts();
        for (int i = 0; i < parts.size(); i++)
            if (parts.get(i).bitten() || parts.get(i).scratched() || parts.get(i).isCut()) { part = i; break; }
        var packet = new ZombieHitPlayerPacket();
        packet.set(zombie, player, damaged, player.getHitReaction(), part);
        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if (connection == null || !connection.isFullyConnected() || !connection.isRelevantTo(player.getX(), player.getY())) continue;
            var writer = connection.startPacket();
            PacketType.ZombieHitPlayer.doPacket(writer);
            packet.write(writer);
            PacketType.ZombieHitPlayer.send(connection);
            INetworkPacket.send(connection, PacketType.PlayerInjuries, player);
            slot.hitPackets++;
        }
    }

    /**
     * Advances along the route by elapsed real time at the configured speed. Returns true when
     * movement starts, a waypoint is passed or the route ends, so a reliable packet follows.
     */
    private boolean step(IsoPlayer player, long now) {
        boolean started = !slot.moving;
        double dt = started ? 0 : Math.min((now - slot.lastStep) / 1e9, 0.25);
        slot.lastStep = now; slot.moving = true;
        var route = slot.path;
        double x = player.getX(), y = player.getY(), remaining = slot.speed * dt;
        boolean turned = false;
        while (slot.waypoint < route.size()) {
            var target = route.get(slot.waypoint);
            double tx = target[0], ty = target[1], dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy);
            if (d > 1e-6) slot.angle = Math.atan2(dy, dx);
            if (d > remaining) { x += dx / d * remaining; y += dy / d * remaining; slot.walked += remaining; break; }
            x = tx; y = ty; remaining -= d; slot.walked += d; slot.waypoint++; turned = true;
        }
        // Route validation is not a lease on geometry: doors/vehicles can change during a walk.
        if (ServerMap.instance.getGridSquare((int)Math.floor(x), (int)Math.floor(y), (int)player.getZ()) == null
            || PolygonalMap2.instance.lineClearCollide(player.getX(), player.getY(), (float)x, (float)y,
                (int)player.getZ(), null, false, true)) {
            slot.moving = false; slot.action = "BLOCKED";
            throw new IllegalStateException("actor_route_changed");
        }
        player.setForceX((float)x); player.setForceY((float)y);
        player.realx = (float)x; player.realy = (float)y;
        player.setForwardDirection((float)Math.cos(slot.angle), (float)Math.sin(slot.angle));
        player.setDirectionAngle((float)Math.toDegrees(slot.angle));
        place(player);
        if (slot.waypoint >= route.size()) { slot.arrivedAt = now; slot.moving = false; slot.action = "ARRIVED"; return true; }
        if (started) slot.action = slot.running ? "RUN" : "WALK";
        return started || turned;
    }

    private boolean separate(IsoPlayer player) {
        float x = player.getX(), y = player.getY();
        player.setNextX(x); player.setNextY(y);
        player.separate();
        float nx = player.getNextX(), ny = player.getNextY();
        if (Math.abs(nx - x) < 1e-4f && Math.abs(ny - y) < 1e-4f) return false;
        // Never let contact carry the puppet through walls: only accept a clear step.
        if (PolygonalMap2.instance.lineClearCollide(x, y, nx, ny, (int)player.getZ(), null, false, true)) { player.setNextX(x); player.setNextY(y); return false; }
        player.setForceX(nx); player.setForceY(ny);
        player.realx = nx; player.realy = ny;
        place(player);
        slot.pushes++;
        return true;
    }

    /** Distance left on the current segment; predictions never extrapolate past a turn or stop. */
    private double segmentRemaining(IsoPlayer player) {
        if (slot.path == null || slot.waypoint >= slot.path.size()) return 0;
        var target = slot.path.get(slot.waypoint);
        return Math.hypot(target[0] - player.getX(), target[1] - player.getY());
    }

    /**
     * Clients ignore a player-connected packet for a known ID, so another identity on the same
     * pooled slot needs the stock removal first, then a quiet interval before the re-announce.
     */
    private void reassign(long now) {
        if (definition.walkTilesPerSecond() > 0) throw new IllegalStateException("reassign_requires_standing");
        IsoPlayer old = slot.body.player;
        detach(old);
        if (!detached(old)) throw new IllegalStateException("reassign_removal_unconfirmed");
        materialize(slot.body.identity + 1, old.realx, old.realy);
        slot.reassignments++; slot.silentUntil = now + REANNOUNCE_DELAY; slot.action = "REASSIGNED";
    }

    private void detach(IsoPlayer player) {
        if (player.isDead()) throw new IllegalStateException("actor_corpse_owned");
        if (!retiring.contains(player)) {
            retiring.add(player);
            retirementSquares.put(player, player.getSquare());
        }
        IsoPlayer replacement = GameServer.IDToPlayerMap.get(slot.onlineId);
        // A timeout for a reused ID could delete somebody else's replica. Keep capacity until reconciled.
        if (replacement != null && replacement != player) throw new IllegalStateException("actor_id_replaced");
        if (!removalAnnounced.contains(player)) {
            INetworkPacket.sendToAll(PacketType.PlayerTimeout, player);
            removalAnnounced.add(player);
        }
        GameServer.IDToPlayerMap.remove(slot.onlineId, player);
        player.removeFromWorld(); player.removeFromSquare();
    }

    private boolean detached(IsoPlayer player) {
        var cell = IsoWorld.instance.currentCell;
        IsoGridSquare square = retirementSquares.get(player);
        boolean squareAttached = square != null && (square.getMovingObjects().contains(player)
            || square.getStaticMovingObjects().contains(player) || square.getObjects().contains(player));
        return !squareAttached && !onSquare(player) && !cell.getObjectList().contains(player)
            && !cell.getAddList().contains(player) && !cell.getRemoveList().contains(player)
            && !GameServer.IDToPlayerMap.containsValue(player) && !GameServer.Players.contains(player);
    }

    private void replicate(IsoPlayer player, boolean moving, boolean reliable) {
        PlayerPacket packet = new PlayerPacket();
        packet.id.set(player);
        packet.variables.set(player);
        var p = packet.prediction;
        p.type = 0; p.x = player.getX(); p.y = player.getY(); p.z = (byte)Math.floor(player.getZ());
        p.direction = moving ? (float)slot.angle : player.getDirectionAngleRadians(); p.moveDirection = 0; p.speed = 0; p.distance = 0;
        double lookahead = moving ? Math.min(slot.speed * LOOKAHEAD_SECONDS, segmentRemaining(player)) : 0;
        if (lookahead >= 0.125) {
            p.type = 1; p.moveDirection = (float)slot.angle; p.speed = (float)slot.speed;
            p.distance = (byte)Math.min(127, Math.round(lookahead * 8));
        }
        p.pathFindX = p.x; p.pathFindY = p.y; p.position.set(p.x, p.y, p.z);
        short flags = NetworkPlayerVariables.getBooleanVariables(player);
        // Walking, never running: the stock flag 256 (deferred movement) drives remote moving state.
        flags = (short)(flags & ~(NetworkPlayerVariables.Flags.isRunning | NetworkPlayerVariables.Flags.isSprinting
            | NetworkPlayerVariables.Flags.hasDeferredMovement));
        if (moving) flags = (short)(flags | NetworkPlayerVariables.Flags.hasDeferredMovement);
        if (moving && slot.running) flags = (short)(flags | NetworkPlayerVariables.Flags.isRunning);
        // The trip/fall is driven by the stock state packet; keep the floor flag consistent.
        if (player.isOnFloor()) flags = (short)(flags | NetworkPlayerVariables.Flags.isOnFloor);
        packet.booleanVariables = flags;
        packet.disconnected = false;
        packet.hitVehicleId.set(null);
        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if (connection == null || !connection.isFullyConnected() || !connection.isRelevantTo(p.x, p.y)
                || !player.checkCanSeeClient(connection)) continue;
            if (slot.announced.add(connection.getConnectedGUID())) {
                GameServer.sendPlayerConnected(player, connection); slot.announcements++;
            }
            PacketType type = reliable ? PacketType.PlayerUpdateReliable : PacketType.PlayerUpdateUnreliable;
            var writer = connection.startPacket();
            type.doPacket(writer);
            packet.write(writer);
            type.send(connection);
            slot.packets++;
        }
    }

    @Override public boolean cleanup() {
        GameHooks.ownThread();
        if (!retireHunter()) return false;
        if (slot != null) slot.action = "RETIRING";
        for (IsoPlayer player : new ArrayList<>(constructed)) {
            if (!retiring.contains(player) || !detached(player)) detach(player);
        }
        for (Iterator<IsoPlayer> it = retiring.iterator(); it.hasNext();) {
            IsoPlayer player = it.next();
            if (!detached(player)) return false;
            bindings.remove(player); constructed.remove(player); retirementSquares.remove(player); removalAnnounced.remove(player);
            it.remove();
        }
        if (acquired) {
            if (!scheduler.release(event.id(), slot.resource, true, true)) return false;
            acquired = false;
        }
        slot = null;
        return true;
    }

    /** Stock network deletion while the ID is valid, then verified absence before release. */
    private boolean retireHunter() {
        if (hunterResource == null) return true;
        if (hunter != null) {
            IsoZombie zombie = hunter.zombie;
            if (!hunter.removalQueued) {
                zombie.setTarget(null); zombie.setUseless(true);
                ServerPedestrians.unbindHunter(zombie);
                if (zombie.isDead()) return false; // a corpse is never claimed as removed
                if (zombie.getOnlineID() >= 0) NetworkZombiePacker.getInstance().deleteZombie(zombie);
                zombie.removeFromWorld(); zombie.removeFromSquare();
                NetworkZombiePacker.getInstance().setExtraUpdate();
                hunter.removalQueued = true;
            }
            var cell = IsoWorld.instance.currentCell;
            IsoGridSquare square = zombie.getSquare();
            boolean squareAttached = square != null && (square.getMovingObjects().contains(zombie)
                || square.getStaticMovingObjects().contains(zombie) || square.getObjects().contains(zombie));
            boolean worldGone = !squareAttached && !cell.getZombieList().contains(zombie) && !cell.getObjectList().contains(zombie)
                && !cell.getAddList().contains(zombie) && !cell.getRemoveList().contains(zombie);
            boolean networkGone = zombie.getOnlineID() < 0 && ServerMap.instance.zombieMap.get(hunter.onlineId) != zombie;
            if (!scheduler.release(event.id(), hunterResource, worldGone, networkGone)) return false;
        } else if (!scheduler.release(event.id(), hunterResource, true, true)) return false;
        hunter = null; hunterResource = null; hunterOwner = null;
        return true;
    }

    private String hunterState(IsoPlayer actor) {
        if (hunter == null) return "";
        IsoZombie zombie = hunter.zombie;
        IsoMovingObject target = zombie.getTarget();
        return ";hunter=" + hunter.onlineId + ";hx=" + zombie.getX() + ";hy=" + zombie.getY()
            + ";hDist=" + Math.hypot(zombie.getX() - actor.getX(), zombie.getY() - actor.getY())
            + ";hBiteMisses=" + hunter.biteMisses + ";hNeighbor=" + (hunterOwner != null) + ";tripGap=" + slot.tripGap
            + ";hState=" + zombie.getActionStateName() + ";hTarget=" + (target == actor ? "actor" : target == null ? "none" : "other")
            + ";hOwner=" + (zombie.getOwner() == null ? "none" : "client") + ";hControls=" + hunter.controls
            + ";hAttackEntries=" + hunter.attackEntries + ";hOwnerChanges=" + hunter.ownerChanges
            + ";hSpeedType=" + zombie.speedType + ";hWalkType=" + zombie.getWalkType() + ";hOutcome=" + hunter.outcome + ";hSuccess=" + hunter.attackSuccess + ";hFail=" + hunter.attackFail
            + ";hInterrupted=" + hunter.attackInterrupted + ";hReactions=" + hunter.reactions
            + ";hBAttack=" + zombie.getVariableBoolean("bAttack") + ";hNoTeeth=" + zombie.isNoTeeth()
            + ";aSinceAttack=" + hunter.lastSinceAttack + ";aOnFloor=" + actor.isOnFloor()
            + ";aHitReaction=" + actor.getHitReaction() + ";aHoles=" + holes(actor)
            + ";aNext=" + actor.getNextX() + "," + actor.getNextY() + ";pDist=" + nearestPlayer(actor)
            + ";aWidth=" + actor.getWidth() + ";aSolid=" + actor.isSolidForSeparate() + ";hWidth=" + zombie.getWidth()
            + ";hPushable=" + zombie.isPushableForSeparate() + ";aPushes=" + slot.pushes
            + ";hRealTargets=" + hunter.realTargets + ";realGhost=" + hunter.lastRealGhost + ";realInvisible=" + hunter.lastRealInvisible
            + ";hDamaging=" + hunter.damagingHits + ";hitPackets=" + slot.hitPackets + ";statePackets=" + slot.statePackets
            + ";stateFaults=" + slot.stateFaults + ";ticks=" + slot.ticks + ";hSteps=" + ServerPedestrians.hunterTiming.count
            + animState(zombie)
            + ";hTargetedMs=" + (hunter.targetedAt == 0 ? -1 : (hunter.targetedAt - spawnedAt) / 1_000_000)
            + ";hContactMs=" + (hunter.contactAt == 0 ? -1 : (hunter.contactAt - spawnedAt) / 1_000_000)
            + ";hNativeP99ms=" + ServerPedestrians.hunterTiming.percentile(.99)
            + ";hNativeMaxMs=" + ServerPedestrians.hunterTiming.max / 1_000_000.0;
    }

    @Override public List<ActorSample> samples() {
        if (slot == null || slot.body == null) return List.of();
        IsoPlayer player = slot.body.player;
        return List.of(ActorSample.newBuilder().setId(slot.id).setOnlineId(player.getOnlineID())
            .setPosition(Point.newBuilder().setX(player.getX()).setY(player.getY()).setZ((int)player.getZ()))
            .setAction(slot.action).setServerOwned(true)
            .setPathState("entity=actor;identity=" + slot.body.identity + ";female=" + player.isFemale()
                + ";outfit=" + slot.body.outfit + ";worn=" + slot.body.worn
                + ";name=" + player.getDisplayName() + ";onSquare=" + onSquare(player)
                + ";stockUpdates=" + slot.nativeTiming.count + ";announced=" + slot.announcements
                + ";placementRepairs=" + slot.placementRepairs
                + ";packets=" + slot.packets + ";reassigned=" + slot.reassignments
                + ";createMs=" + slot.body.createMs + ";nativeFaults=" + slot.nativeFaults
                + ";nativeP99ms=" + slot.nativeTiming.percentile(.99)
                + ";nativeMaxMs=" + slot.nativeTiming.max / 1_000_000.0
                + ";wallMs=" + System.currentTimeMillis() + ";speed=" + definition.walkTilesPerSecond()
                + ";waypoint=" + slot.waypoint + ";walked=" + slot.walked + ";moving=" + slot.moving
                + ";health=" + player.getBodyDamage().getHealth() + ";wounds=" + wounds(player)
                + ";bitten=" + player.getBodyDamage().getNumPartsBitten() + ";scratched=" + player.getBodyDamage().getNumPartsScratched()
                + hunterState(player))
            .setPathAgeMs(spawnedAt == 0 ? 0 : (System.nanoTime() - spawnedAt) / 1_000_000.0)
            .setDistanceMoved(slot.walked).build());
    }
}
