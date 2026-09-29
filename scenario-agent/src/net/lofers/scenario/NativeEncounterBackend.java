package net.lofers.scenario;

import java.nio.file.*;
import java.util.*;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameTime;
import zombie.VirtualZombieManager;
import zombie.Lua.LuaManager;
import zombie.characters.*;
import zombie.core.raknet.UdpConnection;
import zombie.iso.*;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.network.*;
import zombie.network.chat.ChatServer;
import zombie.network.packets.INetworkPacket;
import zombie.popman.NetworkZombiePacker;
import net.lofers.scenario.protocol.RuntimeControl.*;
import static net.lofers.scenario.protocol.RuntimeControl.CivilianEncounterCase.Scenario.*;

/** Reusable, opt-in native encounter backend. Session pool persists; each event owns its scene. */
final class NativeEncounterBackend implements RuntimeSession.Backend {
    private static final int X=10589,Y=10060;
    private static final float PARK_X=10590.5f,PARK_Y=10220.5f;
    private final String world,epoch;
    private final Path directory;
    private final boolean lifecycleSession;private final int capacity;private final TerminalJournal journal;
    private String lastCleanupError="";private String scenarioOutcome="PENDING";private boolean sameActorReused;
    private boolean lifecycle(){return definition!=null&&definition.scenario()==LIFECYCLE;}
    private NativeCivilianActors actors;
    private CivilianPool<IsoPlayer> pool;
    private EventScheduler scheduler;
    private EventScheduler.View event;
    private EncounterDefinition definition;
    private final List<Pair> pairs=new ArrayList<>();
    private final List<IsoDoor> doors=new ArrayList<>();
    private final List<IsoGridSquare> doorSquares=new ArrayList<>();
    private final Set<EventResources.Resource> resources=new LinkedHashSet<>();
    private String stage="IDLE";
    private long tick,stageAt,activeAt,holdAt,lastTeleport,clearAt,aftermathVisibleAt;
    private int preparation,fixtureCursor;
    private boolean cleanupStarted,cleanupVerified;
    private ProbeTiming controllerWork=new ProbeTiming();
    private NativeStrideComparison stride;
    private boolean comparison(){return definition!=null&&definition.scenario()==STRIDE_COMPARE;}
    private static final class Pair {
        final ReplicaDriftGate drift=new ReplicaDriftGate();
        CivilianPool.Token token; IsoPlayer actor; IsoZombie hunter; short hunterId;
        IsoGridSquare hunterSquare; NativeResidentController controller,replacementController;
        UdpConnection owner; long lastControl,safeAt,motionResumeAt,exitSyncAt;
        float hx,hy; double hunterTravel,openedTravel;
        CivilianTraversal locomotion;CivilianNavigation.Key motionKey;int motionLeg;boolean motionStaged,motionCancel;
        int ownerChanges,slot,exitRemoved; boolean opened,escaped,hunterRemoved,retiring,parked;
        EventResources.Resource actorResource,hunterResource,aftermathResource;
        NativeResidentLifecycle death;IsoPlayer original;boolean timerShortened,aftermathRemoved,aftermathDeleting;IsoGridSquare aftermathSquare;short aftermathId=-1;
        CivilianPool.Token deadToken;CivilianTraversal reuseWalk;String beforeDeathInventory;int beforeWorn,skin;
    }
    NativeEncounterBackend(String world,String epoch,Path directory){this(world,epoch,directory,false);}
    NativeEncounterBackend(String world,String epoch,Path directory,boolean lifecycleSession) {
        if(!world.startsWith("AKR_DayOne_Test_")||world.contains("Headless")
            ||!directory.isAbsolute()||!directory.normalize().startsWith("/run/lofers"))
            throw new IllegalArgumentException("encounter_disposable_only");
        this.world=world;this.epoch=epoch;this.directory=directory;
        this.lifecycleSession=lifecycleSession;capacity=lifecycleSession?1:4;journal=new TerminalJournal(directory.resolve("terminal"));
    }
    public boolean encountersEnabled(){return true;}
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
    private void announce(String text){System.out.println("[CivilianEncounter "+event.id()+"] "+text);ChatServer.getInstance().sendServerAlertMessageToServerChat("[Civilian test] "+text);}
    private void stage(String value){stage=value;stageAt=System.nanoTime();publishConfig();}
    private void acquire(EventResources.Resource resource){scheduler.acquire(event.id(),resource);resources.add(resource);}
    private void release(EventResources.Resource resource){if(resources.contains(resource)){require(scheduler.release(event.id(),resource,true,true),"resource_release");resources.remove(resource);}}
    private IsoPlayer observer() {
        for(var p:GameServer.Players)if(p!=null&&"akr".equals(p.getUsername())){
            var c=GameServer.getConnectionFromPlayer(p);if(c!=null&&c.isFullyConnected())return p;
        }
        return null;
    }
    private IsoPlayer protect() {
        var p=observer();require(p!=null,"observer_disconnected");
        require(p.getVehicle()==null&&p.getRole()!=null&&"admin".equalsIgnoreCase(p.getRole().getName()),"observer_admin_on_foot_required");
        if(!p.isGodMod()||!p.isInvisible()||!p.isGhostMode()){
            p.setGodMod(true,true);p.setInvisible(true,true);p.setGhostMode(true,true);GameServer.sendPlayerExtraInfo(p,null,true);
        }
        return p;
    }
    private boolean loadoutReady(IsoPlayer p){
        return LuaManager.env.rawget("AKRObserverLoadoutReady") instanceof KahluaTable r
            &&r.rawget("player")==p&&epoch.equals(r.rawget("epoch"))&&event.id().equals(r.rawget("event"));
    }
    private boolean teleport(IsoPlayer p,float x,float y,long now) {
        if(Math.hypot(p.getX()-x,p.getY()-y)<=2&&Math.abs(p.getZ())<.1)return true;
        if(now-lastTeleport>3_000_000_000L){GameServer.sendTeleport(p,x,y,0);lastTeleport=now;}
        return false;
    }
    private void interest(){
        for(int y=Y-16;y<=10224;y+=32)ServerMap.instance.characterIn(Math.floorDiv(X+8,8),Math.floorDiv(y,8),5);
    }
    public void begin(EventScheduler.View event,EventScheduler scheduler) {
        GameHooks.ownThread();
        require(resources.isEmpty()&&(pool==null||pool.occupied()==0),"previous_encounter_owned");
        for(var p:pairs)require(p.parked&&p.hunterRemoved,"previous_cleanup_unverified");
        this.event=event;this.scheduler=scheduler;definition=Objects.requireNonNull(event.definition().encounter());
        require(lifecycle()==lifecycleSession,"lifecycle_requires_dedicated_one_actor_session");
        scenarioOutcome="PENDING";sameActorReused=false;
        pairs.clear();doors.clear();doorSquares.clear();preparation=fixtureCursor=0;activeAt=holdAt=lastTeleport=clearAt=aftermathVisibleAt=0;
        cleanupStarted=cleanupVerified=false;stride=null;controllerWork=new ProbeTiming();stage("WAIT_OBSERVER");
    }
    public boolean prepare() {
        GameHooks.ownThread();long now=System.nanoTime();interest();
        if(!journal.ready())return false;require(!journal.restartUnresolved(),"terminal_world_restart_requires_reconciliation");
        IsoPlayer p=observer();
        var readiness=EncounterObserverGate.evaluate(p!=null,p!=null&&p.getSquare()!=null,
            preparation>0,now-stageAt,180_000_000_000L);
        require(readiness!=EncounterObserverGate.State.DISCONNECTED,"observer_disconnected");
        require(readiness!=EncounterObserverGate.State.TIMED_OUT,"encounter_prepare_timeout:"+stage);
        if(readiness==EncounterObserverGate.State.WAITING)return false;
        protect();
        try {
            if(preparation==0){
                GameTime.getInstance().setTimeOfDay(12);GameServer.sendWeather();
                announce("Preparing "+definition.scenario()+": "+definition.actors()+" civilians, "+(comparison()?0:definition.actors())+" shamblers"+(definition.scenario()==LOCOMOTION?" (held for the WALK/RUN demonstration)":"")+". Moving you away for off-screen setup.");
                stage("PREWARM");preparation=1;
            }
            if(preparation==1){
                if(!teleport(p,PARK_X,PARK_Y,now))return false;
                if(ServerMap.instance.getGridSquare(X,Y,0)==null)return false;
                if(actors==null){
                    transparentDoor(false);transparentDoor(true);
                    actors=new NativeCivilianActors(IsoWorld.getWorldVersion(),true,capacity);pool=new CivilianPool<>(epoch+"-encounters",actors,capacity);}
                if(actors.constructed<capacity){actors.prewarmOne(X,Y,0);return false;}
                require(actors.parkedCount()==capacity,"warm_pool_baseline");
                acquire(new EventResources.Resource(EventResources.Kind.TERRAIN,event.id()+".geometry"));
                stage("MATERIALIZE");preparation=2;
            }
            pool.tick(++tick,now);
            if(preparation==2){
                if(pairs.size()<definition.actors()){
                    Pair c=new Pair();c.slot=pairs.size();pairs.add(c);
                    c.actorResource=new EventResources.Resource(EventResources.Kind.ACTOR,event.id()+".actor"+c.slot);acquire(c.actorResource);
                    c.aftermathResource=new EventResources.Resource(EventResources.Kind.CORPSE,event.id()+".aftermath"+c.slot);acquire(c.aftermathResource);
                    String resident=event.id()+"-resident-"+c.slot;
                    actors.profile(resident,new NativeCivilianActors.Profile(resident,comparison()?"Generic01":c.slot%2==0?"Generic01":"Tourist",!comparison()&&(definition.seed()+c.slot)%2!=0,X+c.slot*5+.5f,Y+4.5f,0));
                    if(comparison())c.hunterId=-1;
                    c.token=pool.reserve(resident,1,true,null);require(c.token!=null,"pool_reservation");return false;
                }
                for(Pair c:pairs){
                    if(c.actor!=null)continue;c.actor=pool.body(c.token);if(c.actor==null)return false;
                    require(!c.actor.isDead()&&c.actor.getBodyDamage().getHealth()>99,"new_resident_health_leak");
                    if(lifecycle()){
                        require(zombie.SandboxOptions.instance.lore.transmission.getValue()==1&&zombie.SandboxOptions.instance.lore.mortality.getValue()==7&&zombie.SandboxOptions.instance.lore.reanimate.getValue()==5,"lifecycle_sandbox_required");
                        // Full health: only actual native injuries may end this encounter.
                        c.actor.getBodyDamage().setInfected(true);c.actor.getStats().set(CharacterStat.ZOMBIE_INFECTION,.5f);
                        require(c.actor.isAlive()&&c.actor.getBodyDamage().getHealth()>99&&c.actor.shouldBecomeZombieAfterDeath(),"lifecycle_fixture_health");
                        var bag=c.actor.getInventory().AddItem("Base.Bag_Schoolbag");
                        require(bag instanceof zombie.inventory.types.InventoryContainer,"fixture_bag");
                        ((zombie.inventory.types.InventoryContainer)bag).getInventory().AddItem("Base.Pen");
                    }
                    if(definition.scenario()==DEFENSE_ESCAPE&&c.slot%2==0)c.actor.setPrimaryHandItem(c.actor.getInventory().AddItem("Base.Hammer"));
                }
                stage("FIXTURES");preparation=3;
            }
            if(preparation==3){
                if(comparison()&&fixtureCursor<100){
                    for(int budget=0;budget<8&&fixtureCursor<100;budget++,fixtureCursor++){
                        Pair c=pairs.get(fixtureCursor/50);int edge=fixtureCursor%50;
                        var from=new CivilianNavigation.Tile(X+c.slot*5,Y+4+edge,0);var to=new CivilianNavigation.Tile(from.x(),from.y()+1,0);
                        var checked=NativeCivilianGeometry.classify(c.actor,from,to);
                        require(checked.permitted()&&checked.action()==CivilianNavigation.Action.WALK,"stride_lane_blocked:"+c.slot+":"+edge);
                    }return false;
                }
                if(!lifecycle()&&!comparison()&&definition.scenario()!=OPEN_ESCAPE&&definition.scenario()!=LOCOMOTION&&fixtureCursor<20*definition.actors()){
                    int lane=fixtureCursor/20,n=fixtureCursor%20,x=X+lane*5;
                    if(n<18)addDoor(x+n%2,Y+n/2,false);
                    else addDoor(x,Y+(n==18?0:9),true);
                    fixtureCursor++;return false;
                }
                if(!comparison())for(Pair c:pairs)if(c.hunter==null){spawn(c);return false;}
                stage("POSITIONING");preparation=4;
                announce("Scene ready. Moving you to the clear viewing point; the countdown waits for visible participants.");
            }
            heartbeat();
            if(preparation==4){
                if(!teleport(p,X+definition.actors()*5+3.5f,Y+5.5f,now))return false;
                if(!clientReady(false)||!loadoutReady(p))return false;
                if(!comparison())for(Pair c:pairs)if(c.hunter.getOwner()==null||!c.hunter.getOwner().isFullyConnected())return false;
                stage("COUNTDOWN");preparation=5;announce("Starting "+definition.scenario()+" in 2 seconds.");
            }
            if(preparation==5){
                if(!clientReady(false)){stage("POSITIONING");preparation=4;return false;}
                if(now-stageAt<2_000_000_000L)return false;
                for(Pair c:pairs){
                    c.controller=new NativeResidentController(pool,actors,c.token,new CivilianNavigation.Tile(X+c.slot*5,Y+8,0),definition.seed()+c.slot);
                    if(lifecycle())c.controller.stationaryDefenseFixture();
                    c.original=c.actor;c.deadToken=c.token;c.beforeDeathInventory=NativeResidentLifecycle.inventoryDigest(c.actor.getInventory());c.beforeWorn=c.actor.getWornItems().size();c.skin=c.actor.getHumanVisual().getSkinTextureIndex();
                    c.death=new NativeResidentLifecycle(world,journal,pool,actors,c.token,c.actor,c.controller.logical());
                    if(c.hunter!=null){c.hunter.setUseless(definition.scenario()==LOCOMOTION);c.hunter.setTarget(null);}
                    if(definition.scenario()==LOCOMOTION)startMotion(c);
                }
                if(comparison())stride=new NativeStrideComparison(pool,actors,pairs.stream().map(c->c.token).toArray(CivilianPool.Token[]::new),pairs.stream().map(c->c.actor).toArray(IsoPlayer[]::new));
                activeAt=now;stage("RUNNING");announce(lifecycle()?"Stationary defense fixture: one full-health civilian and one strong shambler; escape movement is held for this contact test only. Native defense, injury and death remain active.":comparison()?"50-tile parallel comparison: western lane WALKS, eastern lane RUNS. Same start, distance and healthy profile; no zombies.":"RUNNING: "+definition.actors()+" civilians and "+definition.actors()+" hunters. Decisions and damage are native/server validated.");
                return true;
            }
            return false;
        }catch(Exception e){throw new IllegalStateException("encounter_prepare:"+e.getMessage(),e);}
        finally{publishConfig();}
    }
    static zombie.iso.sprite.IsoSprite transparentDoor(boolean north){
        // Bounded lookup of installed stock glass doors; never mutate a shared sprite.
        for(int i=0;i<256;i++){
            var sprite=IsoSpriteManager.instance.namedMap.get("fixtures_doors_01_"+i);
            if(sprite==null||sprite.getType()!=(north?zombie.iso.SpriteDetails.IsoObjectType.doorN:zombie.iso.SpriteDetails.IsoObjectType.doorW))continue;
            var props=sprite.getProperties();
            if(props.has(zombie.core.properties.IsoPropertyType.DOOR_TRANS)&&!props.has(zombie.iso.SpriteDetails.IsoFlagType.open)
                &&!props.has(zombie.core.properties.IsoPropertyType.DOUBLE_DOOR)&&!props.has(zombie.core.properties.IsoPropertyType.GARAGE_DOOR))return sprite;
        }
        throw new IllegalStateException("stock_transparent_door_unavailable");
    }
    private void addDoor(int x,int y,boolean north){
        var square=ServerMap.instance.getGridSquare(x,y,0);require(square!=null&&square.TreatAsSolidFloor()&&!square.HasStairs()&&square.isFree(false),"fixture_square_unavailable");
        var door=new IsoDoor(IsoWorld.instance.currentCell,square,transparentDoor(north),north);
        doors.add(door);doorSquares.add(square);door.setLocked(true);door.setLockedByKey(true);
        square.AddSpecialObject(door);square.RecalcAllWithNeighbours(true);door.transmitCompleteItemToClients();
    }
    private void spawn(Pair c){
        c.hunterResource=new EventResources.Resource(EventResources.Kind.ZOMBIE,event.id()+".hunter"+c.slot);acquire(c.hunterResource);
        var square=ServerMap.instance.getGridSquare(X+c.slot*5,Y,0);require(square!=null&&square.isFree(false),"hunter_square_unavailable");
        var factory=VirtualZombieManager.instance;var saved=new ArrayList<>(factory.choices);
        factory.choices.clear();factory.choices.add(square);
        try{c.hunter=GameHooks.withSpawnPermit(()->factory.createRealZombieAlways(IsoDirections.S,false));}
        finally{factory.choices.clear();factory.choices.addAll(saved);}
        require(c.hunter!=null,"hunter_spawn_uncertain");c.hunterId=c.hunter.getOnlineID();
        require(c.hunterId>=0,"hunter_identity");c.hunter.doFastShambler();c.hunter.setUseless(true);c.hunter.setTarget(null);
        c.hx=c.hunter.getX();c.hy=c.hunter.getY();
    }
    public boolean update(){
        GameHooks.ownThread();long now=System.nanoTime();interest();require(protect().getSquare()!=null,"observer_unloaded_during_encounter");
        require(clientFresh(),"observer_report_stale");
        if(stage.equals("HOLD")){heartbeat();publishConfig();return now-holdAt>=definition.holdSeconds()*1_000_000_000L;}
        if(lifecycle()&&stage.equals("RUNNING")&&!pairs.getFirst().actor.isDead()&&now-activeAt>=definition.timeoutSeconds()*1_000_000_000L){
            scenarioOutcome="NOT_EXERCISED";announce("NOT EXERCISED: no qualifying fatal native attack within 120 seconds. Cleaning up; no automatic retry.");return true;
        }
        if(!lifecycle())require(now-activeAt<definition.timeoutSeconds()*1_000_000_000L,"encounter_active_timeout");
        pool.tick(++tick,now);
        if(lifecycle()){try{return updateLifecycle(now);}finally{heartbeat();publishConfig();}}
        for(Pair c:pairs)checkReplica(c,now);
        if(comparison()){
            for(Pair c:pairs){require(c.actor.isAlive(),"stride_actor_dead");if(c.actor.getAttackedBy() instanceof IsoPlayer other&&!NativeCivilianActors.owns(other))throw new IllegalStateException("observer_interference");}
            if(stride.tick(now)){
                pairs.forEach(c->c.escaped=true);scenarioOutcome="PASSED";holdAt=now;stage("HOLD");
                announce("50 tiles complete: WALK "+String.format(Locale.ROOT,"%.2f",stride.seconds[0])+"s, RUN "+String.format(Locale.ROOT,"%.2f",stride.seconds[1])+"s. Holding for visual feedback.");
            }
            heartbeat();publishConfig();return false;
        }
        for(Pair c:pairs){
            require(c.actor.isAlive()&&c.hunter.isAlive(),"unexpected_death_resources_retained");
            require(ServerMap.instance.zombieMap.get(c.hunterId)==c.hunter,"hunter_identity_changed");
            if(c.escaped)continue;
            if(c.actor.getAttackedBy() instanceof IsoPlayer other&&!NativeCivilianActors.owns(other)
                ||c.hunter.getAttackedBy() instanceof IsoPlayer other2&&!NativeCivilianActors.owns(other2))
                throw new IllegalStateException("observer_interference");
            if(definition.scenario()==LOCOMOTION){
                c.locomotion.tick(c.motionKey);
                c.motionKey=c.locomotion.view().key();
                require(c.locomotion.view().phase()!=CivilianTraversal.Phase.BLOCKED&&c.locomotion.view().phase()!=CivilianTraversal.Phase.UNRESOLVED,"locomotion_blocked");
                if(c.motionLeg==1&&!c.motionStaged&&c.locomotion.completedEdges()>=1){
                    long rev=c.motionKey.actionRevision()+1;require(pool.acceptIntent(c.token,rev),"motion_continuation_revision");
                    require(c.locomotion.replaceAtBoundary(motionRoute(c,rev,12,16)),"motion_continuation_join");c.motionStaged=true;
                    announce("RUN continuation queued: watch for uninterrupted running across the path handoff.");
                }
                if(c.motionLeg==2&&!c.motionCancel&&c.actor.getY()<Y+16.45){
                    c.locomotion.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);c.motionCancel=true;
                    announce("Deferred stop requested: finish the current tile, then stop.");
                }
                require(c.locomotion.buffered()<=8,"motion_window_overflow");
                if(c.locomotion.view().phase()==CivilianTraversal.Phase.CANCELLED){
                    require(c.motionLeg==2&&c.motionCancel&&c.locomotion.completedEdges()==1&&Math.abs(c.actor.getY()-(Y+15.5))<.01,"motion_soft_stop_failed");
                    if(c.motionResumeAt==0){c.motionResumeAt=now+2_000_000_000L;announce("Boundary stop confirmed. Holding still for two seconds, then resuming RUN.");}
                    else if(now>=c.motionResumeAt){c.motionLeg=3;startMotion(c);}
                }else if(c.locomotion.view().phase()==CivilianTraversal.Phase.COMPLETE){
                    if(c.motionLeg==0){announce("Walking complete. Now RUNNING; the hunter remains held.");c.motionLeg=1;startMotion(c);}
                    else if(c.motionLeg==1){require(c.motionStaged&&c.locomotion.completedEdges()==8,"motion_continuation_incomplete");announce("Continuous RUN complete. Turning back for a deferred-stop check.");c.motionLeg=2;startMotion(c);}
                    else {require(c.motionLeg==3,"motion_unexpected_completion");c.escaped=true;}
                }
                continue;
            }
            var owner=c.hunter.getOwner();
            if(owner!=c.owner){c.owner=owner;c.ownerChanges++;c.lastControl=0;}
            c.hunterTravel+=Math.hypot(c.hunter.getX()-c.hx,c.hunter.getY()-c.hy);c.hx=c.hunter.getX();c.hy=c.hunter.getY();
            if(owner!=null&&owner.isFullyConnected()&&now-c.lastControl>=1_000_000_000L){
                INetworkPacket.send(owner,PacketTypes.PacketType.ZombieControl,c.hunter,c.actor);c.lastControl=now;
            }
            long began=System.nanoTime();c.controller.tick(now,true);controllerWork.add(System.nanoTime()-began);
            require(!c.controller.unresolved(),"controller_unresolved");
            if(definition.scenario()==INCOMING_INJURY&&c.controller.injuries.attempts>=5&&c.controller.injuries.damagingHits==0)
                throw new IllegalStateException("injury_unqualified_five_native_misses");
            boolean contact=definition.scenario()==INCOMING_INJURY?c.controller.injuries.damagingHits>0:c.controller.combat.contacts>0&&!c.controller.combat.action.busy();
            if(definition.scenario()!=OPEN_ESCAPE&&!c.opened&&(contact||c.exitRemoved>0)){
                // Release the artificial corridor without introducing a rotated door leaf
                // into the escape path. Remove at most one owned fixture per pair/tick.
                c.controller.holdFixtureMovement(true);
                if(c.exitSyncAt==0)c.exitSyncAt=now;
                require(now-c.exitSyncAt<5_000_000_000L,"client_exit_geometry_timeout:"+c.slot);
                if(c.exitRemoved<9){
                int exitIndex=c.slot*20+c.exitRemoved*2+1;
                IsoDoor exit=doors.get(exitIndex);IsoGridSquare square=doorSquares.get(exitIndex);
                if(square.getObjects().contains(exit)||square.getSpecialObjects().contains(exit))
                    square.transmitRemoveItemFromSquare(exit);
                require(!square.getObjects().contains(exit)&&!square.getSpecialObjects().contains(exit),"exit_fixture_removal_failed");
                c.exitRemoved++;continue;}
                if(!clientExitClear(c))continue;
                c.controller.holdFixtureMovement(false);
                c.opened=true;c.openedTravel=c.controller.travelled;c.controller.geometryChanged();
                announce("Civilian "+(c.slot+1)+": "+(definition.scenario()==INCOMING_INJURY?"native injury":"native defense")+" confirmed; the fixture exit-side barriers are removed.");
            }
            double gap=Math.hypot(c.actor.getX()-c.hunter.getX(),c.actor.getY()-c.hunter.getY());
            boolean escaped=c.hunterTravel>=2&&c.controller.fleeRoutes>0&&
                (definition.scenario()==OPEN_ESCAPE?c.controller.travelled>=10&&gap>=8:
                 c.opened&&c.controller.travelled-c.openedTravel>=7&&gap>=4);
            if(!escaped)c.safeAt=0;else if(c.safeAt==0)c.safeAt=now;
            if(c.safeAt!=0&&now-c.safeAt>=2_000_000_000L){
                c.escaped=true;c.controller.stop();c.hunter.setUseless(true);c.hunter.setTarget(null);
            }
        }
        heartbeat();publishConfig();
        if(pairs.stream().allMatch(c->c.escaped)){
            scenarioOutcome="PASSED";holdAt=now;stage("HOLD");announce("Case passed its native checks. Holding the scene for "+definition.holdSeconds()+" seconds before verified cleanup.");
        }
        return false;
    }
    private boolean updateLifecycle(long now)throws RuntimeException{
        Pair c=pairs.getFirst();
        require(stage.equals("RUNNING")||now-stageAt<30_000_000_000L,"lifecycle_stage_timeout:"+stage);
        if(stage.equals("RUNNING")){
            require(c.hunter.isAlive(),"lifecycle_hunter_died");checkReplica(c,now);
            if(c.actor.getAttackedBy() instanceof IsoPlayer other&&!NativeCivilianActors.owns(other)
                ||c.hunter.getAttackedBy() instanceof IsoPlayer other2&&!NativeCivilianActors.owns(other2))throw new IllegalStateException("observer_interference");
            var owner=c.hunter.getOwner();
            if(owner!=c.owner){c.owner=owner;c.ownerChanges++;c.lastControl=0;}
            if(owner!=null&&owner.isFullyConnected()&&now-c.lastControl>=1_000_000_000L){
                INetworkPacket.send(owner,PacketTypes.PacketType.ZombieControl,c.hunter,c.actor);c.lastControl=now;
            }
            long began=System.nanoTime();c.controller.tick(now,true);controllerWork.add(System.nanoTime()-began);
            if(c.actor.isDead()){
                c.controller.died();var contact=c.controller.injuries.lastDamage;
                require(contact!=null&&contact.token().equals(c.token)&&c.actor.getAttackedBy()==c.hunter,"death_without_validated_native_injury");
                c.hunter.setUseless(true);c.hunter.setTarget(null);stage("DYING");announce("Native attack was fatal. Waiting for the native corpse and durable terminal receipt.");
            }
        }else if(stage.equals("DYING")){
            c.death.tick(false);require(c.death.failure.isEmpty(),c.death.failure);
            if(c.death.corpse!=null){
                require(c.beforeDeathInventory.equals(c.death.fingerprint),"fatal_inventory_transfer");
                if(c.death.zombie()==null)require(c.death.corpse.getWornItems().size()==c.beforeWorn&&c.death.corpse.getHumanVisual().getSkinTextureIndex()==c.skin,"corpse_appearance_transfer");
                stage("CORPSE_HOLD");announce("Corpse created with its original inventory. Holding for 8 seconds before native reanimation.");}
        }else if(stage.equals("CORPSE_HOLD")){
            c.death.tick(false);require(c.death.failure.isEmpty(),c.death.failure);
            if(now-stageAt>=8_000_000_000L){c.death.corpse.reanimateNow();c.timerShortened=true;stage("REANIMATION");}
        }else if(stage.equals("REANIMATION")){
            c.death.tick(false);require(c.death.failure.isEmpty(),c.death.failure);
            if(c.death.zombie()!=null){c.death.verifyTransfer();require(c.death.zombie().getWornItems().size()==c.beforeWorn,"reanimated_equipment_transfer");stage("REANIMATED_HOLD");announce("Native reanimation confirmed. Staying here to watch it before Actor reuse.");}
        }else if(stage.equals("REANIMATED_HOLD")){
            c.death.tick(false);require(c.death.failure.isEmpty(),c.death.failure);
            if(!clientActorFlag(c,"reanimated_visible",true)){aftermathVisibleAt=0;return false;}
            if(aftermathVisibleAt==0)aftermathVisibleAt=now;
            if(now-aftermathVisibleAt>=8_000_000_000L){stage("RETIRE_OLD");announce("Reanimation viewing complete. Moving you away for verified Actor reuse.");}
        }else if(stage.equals("RETIRE_OLD")){
            if(!clearViewers(now))return false;
            c.death.tick(true);require(c.death.failure.isEmpty(),c.death.failure);
            if(c.death.released){c.parked=true;stage("ABSENCE");}
        }else if(stage.equals("ABSENCE")){
            if(!clientActorAbsent(c)||now-stageAt<1_000_000_000L)return false;
            require(clearViewers(now),"viewer_returned_before_reuse");
            c.controller.dispose();String id=event.id()+"-replacement";
            actors.profile(id,new NativeCivilianActors.Profile("Replacement Resident","Tourist",true,X+.5f,Y+20.5f,0));
            c.token=pool.reserve(id,1,true,null);require(c.token!=null,"lifecycle_reuse_capacity");c.parked=false;stage("REASSIGN");
        }else if(stage.equals("REASSIGN")){
            var fresh=pool.body(c.token);if(fresh==null)return false;
            require(fresh==c.original&&fresh.isAlive()&&fresh.getBodyDamage().getHealth()>99,"lifecycle_reuse_identity_or_health");
            require(!NativeResidentLifecycle.sharesItem(fresh.getInventory(),c.death.inventory),"lifecycle_inventory_leak");
            c.death.verifyTransfer();c.actor=fresh;
            c.replacementController=new NativeResidentController(pool,actors,c.token,new CivilianNavigation.Tile(X,Y+28,0),definition.seed()+1);
            sameActorReused=true;stage("REUSE_POSITION");
        }else if(stage.equals("REUSE_POSITION")){
            if(!teleport(protect(),X+8.5f,Y+23.5f,now)||!clientActorVisible(c))return false;
            long revision=1;require(pool.acceptIntent(c.token,revision),"reuse_walk_revision");
            var steps=new ArrayList<CivilianNavigation.Step>();
            for(int n=20;n<=28;n++)steps.add(new CivilianNavigation.Step(new CivilianNavigation.Tile(X,Y+n,0),CivilianNavigation.Action.WALK));
            var port=new NativeCivilianTraversal(c.token,c.actor,actors);port.speed(1.45);c.reuseWalk=new CivilianTraversal(port);
            c.reuseWalk.start(new CivilianNavigation.Result(new CivilianNavigation.Key(c.token,revision,1),CivilianNavigation.Status.FOUND,steps,0,0));
            stage("REUSE_WALK");announce("The same initialized Actor now has a new identity and outfit. Watch its eight-tile walk.");
        }else if(stage.equals("REUSE_WALK")){
            checkReplica(c,now);c.reuseWalk.tick(c.reuseWalk.view().key());
            require(c.reuseWalk.view().phase()!=CivilianTraversal.Phase.BLOCKED&&c.reuseWalk.view().phase()!=CivilianTraversal.Phase.UNRESOLVED,"reuse_walk_blocked");
            if(c.reuseWalk.view().phase()==CivilianTraversal.Phase.COMPLETE){scenarioOutcome="PASSED";holdAt=now;stage("HOLD");announce("Lifecycle native checks passed. Holding before exact-resource cleanup.");}
        }
        heartbeat();publishConfig();return false;
    }
    private boolean clientActorAbsent(Pair c){return clientActorFlag(c,"actor_present",false);}
    private boolean clientActorVisible(Pair c){return clientActorFlag(c,"actor_visible",true);}
    private boolean clientActorFlag(Pair c,String key,boolean expected){
        if(!clientFresh())return false;var r=(KahluaTable)LuaManager.env.rawget("AKREncounterReport");
        return r.rawget("stage").equals(stage)&&r.rawget("pairs") instanceof KahluaTable rows&&rows.rawget((double)c.slot+1) instanceof KahluaTable row
            &&Double.valueOf(c.token.generation()).equals(row.rawget("generation"))&&Boolean.valueOf(expected).equals(row.rawget(key));
    }
    private boolean noConnections(){return GameServer.udpEngine!=null&&GameServer.udpEngine.connections.isEmpty()&&GameServer.Players.isEmpty();}
    private boolean clearViewers(long now){
        IsoPlayer p=observer();
        if(p!=null){protect();if(p.getSquare()==null||!teleport(p,PARK_X,PARK_Y,now)){clearAt=0;return false;}}
        else if(!noConnections()){clearAt=0;return false;}
        // This fixture has one reporting observer. Additional/unidentified connections retain ownership.
        if(!EncounterCleanupGate.clearance(GameServer.udpEngine.connections.size(),GameServer.Players.size(),p!=null,true)){clearAt=0;return false;}
        for(Pair c:pairs)for(IsoPlayer viewer:GameServer.Players)
            if(viewer.getSquare()==null||c.actor!=null&&Math.hypot(viewer.getX()-c.actor.getX(),viewer.getY()-c.actor.getY())<72
                ||c.hunter!=null&&Math.hypot(viewer.getX()-c.hunter.getX(),viewer.getY()-c.hunter.getY())<72){clearAt=0;return false;}
        if(clearAt==0)clearAt=now;return now-clearAt>=2_000_000_000L;
    }
    private void startMotion(Pair c){
        long revision=c.motionKey==null?1:c.motionKey.actionRevision()+1;
        c.motionKey=new CivilianNavigation.Key(c.token,revision,1);require(pool.acceptIntent(c.token,revision),"motion_revision");
        int from=switch(c.motionLeg){case 0->4;case 1->8;case 2->16;default->15;};
        int to=c.motionLeg==0?8:12;
        var port=new NativeCivilianTraversal(c.token,c.actor,actors);port.speed(c.motionLeg==0?1.45:2.6);
        c.locomotion=new CivilianTraversal(port);c.locomotion.start(motionRoute(c,revision,from,to));
    }
    private CivilianNavigation.Result motionRoute(Pair c,long revision,int from,int to){
        var steps=new ArrayList<CivilianNavigation.Step>();int direction=from<=to?1:-1;
        for(int i=from;;i+=direction){steps.add(new CivilianNavigation.Step(new CivilianNavigation.Tile(X,Y+i,0),CivilianNavigation.Action.WALK));if(i==to)break;}
        return new CivilianNavigation.Result(new CivilianNavigation.Key(c.token,revision,1),CivilianNavigation.Status.FOUND,steps,0,0);
    }
    private void heartbeat(){if(actors!=null)for(Pair c:pairs)if(c.actor!=null&&!c.retiring&&!c.parked&&!c.actor.isDead())actors.replicate(c.actor,0,0,false);}
    private boolean clientFresh(){
        if(!(LuaManager.env.rawget("AKREncounterReport") instanceof KahluaTable r))return false;
        return r.rawget("player")==observer()&&epoch.equals(r.rawget("epoch"))&&event.id().equals(r.rawget("event"))
            &&r.rawget("received_ms") instanceof Number n&&System.currentTimeMillis()-n.longValue()<=2500;
    }
    private void checkReplica(Pair c,long now){
        if(!(LuaManager.env.rawget("AKREncounterReport") instanceof KahluaTable report)
            ||!(report.rawget("pairs") instanceof KahluaTable rows)
            ||!(rows.rawget((double)c.slot+1) instanceof KahluaTable row))return;
        boolean fresh=report.rawget("received_ms") instanceof Number received&&System.currentTimeMillis()-received.longValue()<500
            &&Boolean.TRUE.equals(row.rawget("actor_present"));
        double gap=row.rawget("actor_x") instanceof Number x&&row.rawget("actor_y") instanceof Number y?
            Math.hypot(c.actor.getX()-x.doubleValue(),c.actor.getY()-y.doubleValue()):0;
        require(!c.drift.sample(now,fresh,gap),"client_replica_divergence:"+c.slot+":"+String.format(Locale.ROOT,"%.2f",gap));
    }
    private boolean clientExitClear(Pair c){
        if(!clientFresh())return false;
        var report=(KahluaTable)LuaManager.env.rawget("AKREncounterReport");
        if(!(report.rawget("pairs") instanceof KahluaTable rows)||!(rows.rawget((double)c.slot+1) instanceof KahluaTable row))return false;
        return Boolean.TRUE.equals(row.rawget("exit_clear"));
    }
    private boolean clientReady(boolean absent){
        if(!clientFresh())return false;
        var r=(KahluaTable)LuaManager.env.rawget("AKREncounterReport");
        if(!(r.rawget("pairs") instanceof KahluaTable list))return false;
        for(int i=0;i<pairs.size();i++){
            if(!(list.rawget((double)i+1) instanceof KahluaTable row))return false;
            if(absent){if(!Boolean.FALSE.equals(row.rawget("actor_present"))||!Boolean.FALSE.equals(row.rawget("hunter_present")))return false;
                if(pairs.get(i).death!=null&&pairs.get(i).death.corpse!=null&&(!Boolean.FALSE.equals(row.rawget("corpse_present"))||!Boolean.FALSE.equals(row.rawget("reanimated_present"))))return false;}
            else if(!Boolean.TRUE.equals(row.rawget("actor_visible"))||!comparison()&&!Boolean.TRUE.equals(row.rawget("hunter_visible")))return false;
        }
        return true;
    }
    private void publishConfig(){
        if(event==null)return;
        var config=LuaManager.platform.newTable();config.rawset("epoch",epoch);config.rawset("event",event.id());config.rawset("stage",stage);
        config.rawset("count",(double)definition.actors());config.rawset("actor_only",comparison());var list=LuaManager.platform.newTable();
        for(int i=0;i<pairs.size();i++){
            Pair c=pairs.get(i);if(c.token==null||!comparison()&&c.hunter==null)continue;
            var row=LuaManager.platform.newTable();row.rawset("actor",(double)c.token.slot());row.rawset("generation",(double)c.token.generation());row.rawset("hunter",(double)c.hunterId);
            if(c.death!=null&&c.death.corpse!=null){
                row.rawset("corpse",c.death.corpseId);row.rawset("corpse_x",(double)c.death.corpse.getX());row.rawset("corpse_y",(double)c.death.corpse.getY());
                var z=c.death.zombie();if(z!=null&&z.getOnlineID()>=0)c.aftermathId=z.getOnlineID();row.rawset("reanimated",(double)c.aftermathId);
            }
            if(c.exitRemoved>0){row.rawset("exit_x",(double)(X+c.slot*5+1));row.rawset("exit_y",(double)Y);}
            row.rawset("active",!comparison()&&stage.equals("RUNNING")&&!c.escaped&&definition.scenario()!=LOCOMOTION);list.rawset((double)i+1,row);
        }
        config.rawset("pairs",list);LuaManager.env.rawset("AKREncounterWatch",config);
    }
    public boolean cleanup(){
        GameHooks.ownThread();long now=System.nanoTime();interest();
        try {
            if(!cleanupStarted){
                cleanupStarted=true;stage("CLEANING");
                if(stride!=null)stride.cancel();
                announce("Moving you away for cleanup. The next case waits for verified removal.");
                for(Pair c:pairs){if(c.reuseWalk!=null)c.reuseWalk.cancel();if(c.locomotion!=null)c.locomotion.cancel();if(c.controller!=null)c.controller.dispose();if(c.replacementController!=null)c.replacementController.dispose();}
            }
            if(pool==null||pairs.isEmpty()&&resources.isEmpty()){
                cleanupVerified=resources.isEmpty()&&(pool==null||pool.occupied()==0);
                return cleanupVerified;
            }
            if(!clearViewers(now)){heartbeat();return false;}
            pool.tick(++tick,now);
            for(Pair c:pairs){
                if(c.actor==null&&c.token!=null)c.actor=pool.body(c.token);
                if(c.actor!=null&&c.actor.isDead()){
                    if(c.death==null)return false;c.controller.died();c.death.tick(true);
                    if(!c.death.failure.isEmpty()||!c.death.released)return false;
                    c.parked=true;release(c.actorResource);
                }
                if(c.hunter!=null&&c.hunter.isDead())return false; // Unproven hunter corpse remains owned.
                if(c.death!=null&&c.death.corpse!=null&&!c.aftermathRemoved){
                    var z=c.death.zombie();
                    if(!c.aftermathDeleting){
                        c.death.verifyTransfer();c.aftermathDeleting=true;
                        if(z==null){
                            c.aftermathSquare=c.death.corpse.getSquare();
                            if(c.aftermathSquare==null)return false;
                            INetworkPacket.sendToAll(PacketTypes.PacketType.RemoveCorpseFromMap,c.death.corpse);
                            c.aftermathSquare.removeCorpse(c.death.corpse,true);
                        }else{
                            c.aftermathSquare=z.getSquare();c.aftermathId=z.getOnlineID();
                            NetworkZombiePacker.getInstance().deleteZombie(z);z.removeFromWorld();z.removeFromSquare();NetworkZombiePacker.getInstance().setExtraUpdate();
                        }
                        return false;
                    }
                    var cell=IsoWorld.instance.currentCell;
                    if(z!=null&&(z.getSquare()!=null||ServerMap.instance.zombieMap.get(c.aftermathId)==z||cell.getZombieList().contains(z)||cell.getObjectList().contains(z)
                        ||cell.getAddList().contains(z)||cell.getRemoveList().contains(z)||c.aftermathSquare!=null&&c.aftermathSquare.getMovingObjects().contains(z)))return false;
                    // Stock removal retains the square reference; membership, not that pointer, proves attachment.
                    var corpse=c.death.corpse;var corpseSquare=corpse.getSquare();
                    if(corpse.getStaticMovingObjectIndex()>=0||corpseSquare!=null&&corpseSquare.getStaticMovingObjects().contains(corpse)
                        ||cell.getObjectList().contains(corpse)||cell.getAddList().contains(corpse)||cell.getRemoveList().contains(corpse))return false;
                    NativeResidentLifecycle.forget(c.death.corpse);c.aftermathRemoved=true;
                }
                release(c.aftermathResource);if(c.parked)release(c.actorResource);
            }
            for(Pair c:pairs)if(!c.hunterRemoved){
                if(c.hunter==null){if(c.hunterResource!=null)return false;c.hunterRemoved=true;continue;}
                c.hunterSquare=c.hunter.getSquare();c.hunter.setTarget(null);c.hunter.setUseless(true);OffscreenZombieLease.release(c.hunter);
                NetworkZombiePacker.getInstance().deleteZombie(c.hunter);c.hunter.removeFromWorld();c.hunter.removeFromSquare();NetworkZombiePacker.getInstance().setExtraUpdate();
                c.hunterRemoved=true;return false;
            }
            for(Pair c:pairs)if(c.hunter!=null){
                var cell=IsoWorld.instance.currentCell;
                if(c.hunter.getSquare()!=null||c.hunter.getOnlineID()>=0||ServerMap.instance.zombieMap.get(c.hunterId)==c.hunter
                    ||cell.getZombieList().contains(c.hunter)||cell.getObjectList().contains(c.hunter)||cell.getAddList().contains(c.hunter)
                    ||cell.getRemoveList().contains(c.hunter)||c.hunterSquare!=null&&c.hunterSquare.getMovingObjects().contains(c.hunter))return false;
                release(c.hunterResource);
            }
            for(int i=0;i<doors.size();i++)if(doorSquares.get(i).getObjects().contains(doors.get(i))||doorSquares.get(i).getSpecialObjects().contains(doors.get(i))){
                doorSquares.get(i).transmitRemoveItemFromSquare(doors.get(i));return false;
            }
            release(new EventResources.Resource(EventResources.Kind.TERRAIN,event.id()+".geometry"));
            pool.tick(++tick,now);
            for(Pair c:pairs)if(!c.retiring&&!c.parked){
                if(c.token==null){return false;}
                if(c.actor==null){if(!pool.retire(c.token))return false;c.parked=true;release(c.actorResource);continue;}
                if(!pool.retire(c.token))return false;c.retiring=true;
            }
            CivilianPool.Retired retired;
            while((retired=pool.pollRetired())!=null){
                var receipt=retired;Pair c=pairs.stream().filter(a->receipt.token().equals(a.token)).findFirst().orElseThrow();
                Path dir=directory.resolve("encounters").resolve(event.id());Files.createDirectories(dir);
                Files.write(dir.resolve("resident-"+c.slot+".bin"),receipt.snapshot().bytes(),StandardOpenOption.CREATE_NEW);
                actors.park(receipt.token(),c.actor,receipt.snapshot());c.parked=true;release(c.actorResource);
            }
            if(pool.occupied()!=0||!pairs.stream().allMatch(c->c.parked)||!EncounterCleanupGate.absent(GameServer.udpEngine.connections.size(),GameServer.Players.size(),clientReady(true)))return false;
            require(actors.constructed==capacity&&actors.parkedCount()==capacity&&resources.isEmpty(),"pool_cleanup_baseline");
            cleanupVerified=true;stage("COMPLETE");announce("CASE COMPLETE: cleanup verified; all initialized bodies are parked for reuse.");return true;
        }catch(Exception e){String why=e.getClass().getSimpleName()+":"+e.getMessage();if(!why.equals(lastCleanupError)){lastCleanupError=why;System.err.println("[CivilianEncounter] Cleanup retained resources: "+why);}return false;}
        finally{publishConfig();}
    }
    public List<ActorSample> samples(){
        var list=new ArrayList<ActorSample>();
        for(Pair c:pairs)if(c.actor!=null&&c.token!=null)list.add(ActorSample.newBuilder().setId(c.token.resident()).setOnlineId(c.token.slot())
            .setPosition(Point.newBuilder().setX(c.actor.getX()).setY(c.actor.getY()).setZ((int)c.actor.getZ()))
            .setAction(c.controller==null?stage:c.controller.state()).setServerOwned(true).setPathAgeMs(c.controller==null?0:c.controller.pathAgeMs(System.nanoTime()))
            .setDistanceMoved(c.controller==null?0:c.controller.travelled).build());
        return list;
    }
    public EncounterProgress encounter(){
        var result=EncounterProgress.newBuilder().setStage(stage).setCleanupVerified(cleanupVerified)
            .setScenarioOutcome(scenarioOutcome).setCleanupOutcome(cleanupVerified?"VERIFIED":cleanupStarted?"PENDING_OR_RETAINED":"NOT_STARTED")
            .setSameActorReused(sameActorReused)
            .setControllerP95Ms(controllerWork.percentile(.95)).setControllerP99Ms(controllerWork.percentile(.99));
        if(lifecycle()&&!pairs.isEmpty()&&pairs.getFirst().death!=null){var d=pairs.getFirst().death;
            result.setCorpseId(d.corpseId).setReanimatedId(pairs.getFirst().aftermathId)
                .setTerminalDurable(d.durable).setLifecycleFailure(d.failure).setInventoryDigest(Objects.toString(d.fingerprint,""));}
        if(actors!=null)result.setConstructed(actors.constructed).setReused(actors.reused).setParked(actors.parkedCount()).setWarmupMaxMs(actors.coldTiming.max/1e6);
        for(Pair c:pairs)if(c.token!=null){
            var row=EncounterPair.newBuilder().setActor(c.token.slot()).setGeneration(c.token.generation()).setHunter(c.hunterId)
                .setReplicaPeakGap(c.drift.peak).setState(c.controller==null?stage:c.controller.state()).setHunterTravelled(c.hunterTravel).setEscaped(c.escaped)
                .setOwned(c.owner!=null).setOwnerChanges(c.ownerChanges).setHealth(c.actor==null?0:c.actor.getBodyDamage().getHealth());
            if(c.controller!=null)row.setTravelled(c.controller.travelled).setContacts(c.controller.combat.contacts)
                .setInjuryAttempts(c.controller.injuries.attempts).setInjuries(c.controller.injuries.damagingHits).setPathRequests(c.controller.pathRequests)
                .setGait(c.controller.gait()).setNativeState(c.controller.nativeState()).setHitReaction(Objects.toString(c.controller.injuries.reaction(),""))
                .setInterruptReason(c.controller.interruptReason()).addAllTransitions(c.controller.wireTrace())
                .setPauses(c.controller.pauses).setResumes(c.controller.resumes).setRouteReplacements(c.controller.routeReplacements)
                .setCommandedSpeed(c.controller.speed()).setGoal(c.controller.goal()).setPlanSource(c.controller.planSource())
                .setPerceptionDiagnostic(c.controller.perceptionDiagnostic()).setBufferedEdges(c.controller.bufferedSteps()).setRouteRevision(c.controller.routeRevision());
            if(c.actor!=null)row.setServerWalkModifier(c.actor.getVariableFloat("WalkSpeed",0)).setPacketVariables(actors.packetVariables(c.actor));
            if(clientFresh()&&LuaManager.env.rawget("AKREncounterReport") instanceof KahluaTable report
                &&report.rawget("pairs") instanceof KahluaTable list
                &&list.rawget((double)pairs.indexOf(c)+1) instanceof KahluaTable client){
                row.setClientObservationFresh(true).setClientRunning(Boolean.TRUE.equals(client.rawget("actor_running")))
                    .setClientLocked(Boolean.TRUE.equals(client.rawget("actor_locked")))
                    .setClientState(Objects.toString(client.rawget("actor_state"),""))
                    .setClientVisibility(Objects.toString(client.rawget("actor_visibility"),"unavailable"))
                    .setClientAction(Objects.toString(client.rawget("actor_action"),""))
                    .setClientReaction(Objects.toString(client.rawget("actor_reaction"),""))
                    .setClientHunterDiagnostic(Objects.toString(client.rawget("hunter_diagnostic"),""));
                if(client.rawget("actor_walk_speed") instanceof Number n)row.setClientWalkModifier(n.doubleValue());
                if(client.rawget("actor_run_speed") instanceof Number n)row.setClientRunModifier(n.doubleValue());
                if(client.rawget("actor_x") instanceof Number n)row.setClientX(n.doubleValue());
                if(client.rawget("actor_y") instanceof Number n)row.setClientY(n.doubleValue());
            }
            result.addPairs(row);
        }
        for(Pair c:pairs)if(c.controller!=null){
            var timings=Map.of("perception",c.controller.perceptionWork,"navigation",c.controller.navigationWork,
                "combat",c.controller.combatWork,"injury",c.controller.injuryWork,"decision",c.controller.decisionWork);
            for(var entry:timings.entrySet())result.addTimings(EncounterTiming.newBuilder().setComponent("actor"+c.slot+"."+entry.getKey())
                .setP95Ms(entry.getValue().percentile(.95)).setP99Ms(entry.getValue().percentile(.99)).setMaxMs(entry.getValue().max/1e6));
        }
        if(actors!=null)for(var entry:Map.of("materialization",actors.materializeTiming,"movement",actors.moveTiming,"replication",actors.replicationTiming).entrySet())
            result.addTimings(EncounterTiming.newBuilder().setComponent(entry.getKey()).setP95Ms(entry.getValue().percentile(.95))
                .setP99Ms(entry.getValue().percentile(.99)).setMaxMs(entry.getValue().max/1e6));
        return result.build();
    }
}
