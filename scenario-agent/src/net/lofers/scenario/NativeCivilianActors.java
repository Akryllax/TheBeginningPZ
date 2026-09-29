package net.lofers.scenario;

import java.util.*;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import org.joml.Vector2f;
import zombie.characters.*;
import zombie.core.raknet.UdpConnection;
import zombie.iso.*;
import zombie.network.*;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.character.PlayerPacket;
import zombie.pathfind.PolygonalMap2;
import zombie.iso.objects.IsoDeadBody;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.ItemContainer;

/** Candidate native pool port. Explicitly driven by an experiment; never auto-spawns on load. */
public final class NativeCivilianActors implements CivilianPool.Port<IsoPlayer> {
    public enum Gait { IDLE, WALK, RUN }
    public record Profile(String name,String outfit,boolean female,float x,float y,int z) {
        public Profile {
            if(name==null||name.isBlank()||outfit==null||outfit.isBlank()||!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("profile");
        }
    }
    private static final class Binding {
        final CivilianPool.Token token;final Set<Long> announced=new HashSet<>();
        final MovementTransition transition=new MovementTransition();float targetBlend,lastWireDirection;boolean packetUrgent;
        IsoGridSquare oldSquare;ItemContainer corpseContainer;boolean retiring,timeoutSent,deferPackets;long lastPacket;int packets,stockUpdates;long ratesAt;Gait gait=Gait.IDLE;String movementFailure="";double commandedSpeed;boolean wireMovement;float wireWalk,wireInjury;
        Binding(CivilianPool.Token token){this.token=token;}
    }
    private final Map<String,Profile> profiles=new HashMap<>();
    private static final IdentityHashMap<IsoPlayer,Binding> registry=new IdentityHashMap<>();
    private final IdentityHashMap<IsoPlayer,Binding> owned=new IdentityHashMap<>();
    private record Parked(CivilianPool.Token token,IsoPlayer body) { }
    private final ArrayDeque<Parked> parked=new ArrayDeque<>();
    private final IdentityHashMap<IsoPlayer,CivilianPool.Snapshot> pristine=new IdentityHashMap<>();
    private final NativeActorReset reset=new NativeActorReset();
    private final Field removedEmitters;
    private final Field diedBody,corpsePlayer;
    private final IdentityHashMap<IsoPlayer,IsoDeadBody> heldCorpses=new IdentityHashMap<>();
    private final NativeActorBodyCodec codec;
    private final boolean watchedExperiment;
    private final int capacity;
    interface StagingPolicy {
        boolean admit(CivilianPool.Token token,Profile profile);
        boolean hidden(IsoPlayer body);
    }
    private final StagingPolicy staging;
    public long replicationPackets;
    public final ProbeTiming warmTiming=new ProbeTiming(),coldTiming=new ProbeTiming();
    public int constructed,reused;
    public final ProbeTiming materializeTiming=new ProbeTiming(),moveTiming=new ProbeTiming(),replicationTiming=new ProbeTiming();
    public NativeCivilianActors(int worldVersion,boolean watchedExperiment){
        this(worldVersion,watchedExperiment,32);
    }
    public NativeCivilianActors(int worldVersion,boolean watchedExperiment,int capacity){
        this(worldVersion,watchedExperiment,capacity,null);
    }
    NativeCivilianActors(int worldVersion,boolean watchedExperiment,int capacity,StagingPolicy staging){
        if(capacity<1||capacity>64)throw new IllegalArgumentException("actor_capacity");
        this.capacity=capacity;
        this.staging=staging;
        codec=new NativeActorBodyCodec(worldVersion);this.watchedExperiment=watchedExperiment;
        try {
            removedEmitters=IsoPlayer.class.getDeclaredField("RecentlyRemoved");
            if(removedEmitters.getType()!=ArrayList.class)throw new IllegalStateException("removed_emitters_contract");
            removedEmitters.setAccessible(true);
            diedBody=IsoGameCharacter.class.getDeclaredField("diedBody");
            corpsePlayer=IsoDeadBody.class.getDeclaredField("player");
            if(diedBody.getType()!=IsoDeadBody.class||corpsePlayer.getType()!=IsoPlayer.class)
                throw new IllegalStateException("corpse_reference_contract");
            diedBody.setAccessible(true);corpsePlayer.setAccessible(true);
        } catch(ReflectiveOperationException error){throw new IllegalStateException("removed_emitters_contract",error);}
    }
    public static boolean owns(IsoPlayer body){return registry.containsKey(body);}
    public static void noteStockUpdate(IsoPlayer body){Binding binding=registry.get(body);if(binding!=null)binding.stockUpdates++;}
    /** Supply only the admitted residents, not an unbounded town registry. */
    public void profile(String resident,Profile profile){GameHooks.ownThread();if(!profiles.containsKey(resident)&&profiles.size()>=capacity)throw new IllegalStateException("profile_budget");profiles.put(resident,profile);}
    @Override public void materialize(CivilianPool.Token token,CivilianPool.Snapshot saved,Consumer<IsoPlayer> own) throws Exception {
        GameHooks.ownThread();long start=System.nanoTime();boolean warm=false;
        try {
            Profile profile=Objects.requireNonNull(profiles.get(token.resident()),"resident_profile");
            if(GameServer.IDToPlayerMap.containsKey((short)token.slot()))throw new IllegalStateException("online_id_in_use");
            IsoGridSquare square=ServerMap.instance.getGridSquare((int)Math.floor(profile.x()),(int)Math.floor(profile.y()),profile.z());
            if(square==null||!square.isFree(false))throw new IllegalStateException("spawn_square_unavailable");
            if(staging!=null&&!staging.admit(token,profile))throw new CivilianPool.AdmissionDeferred("staging_visible_or_stale");
            if(staging==null&&!watchedExperiment&&nearPlayer(profile.x(),profile.y()))throw new IllegalStateException("spawn_visible_or_unknown");
            Parked cached=parked.peekFirst();
            if(cached==null)throw new IllegalStateException("initialized_actor_pool_empty");
            IsoPlayer body=cached.body();
            if(!verify(cached.token(),body).confirmed())throw new IllegalStateException("parked_body_attached");
            NativeActorReset.requireQuiescent(body);
            // Own before any reset/restore can fail. Failure quarantines this object in the pool.
            own.accept(body);parked.removeFirst();warm=true;reused++;
            Binding binding=new Binding(token);owned.put(body,binding);registry.put(body,binding);
            reset.clearBeforeLoad(body);
            codec.restore(body,saved==null?Objects.requireNonNull(pristine.get(body)):saved);
            if(saved==null) {
                NativeActorReset.detachDescriptor(body);
                SurvivorDesc desc=SurvivorFactory.CreateSurvivor(SurvivorFactory.SurvivorType.Neutral,profile.female());
                body.setDescriptor(desc);desc.setInstance(body);body.setFemale(profile.female());
                body.getHumanVisual().copyFrom(desc.getHumanVisual());
                body.dressInNamedOutfit(profile.outfit());body.getWornItems().setFromItemVisuals(body.getItemVisuals());
                body.getWornItems().addItemsToItemContainer(body.getInventory());
                if(body.getWornItems().size()==0)throw new IllegalStateException("actor_undressed");
                body.setDisplayName(profile.name());
            } else body.getDescriptor().setInstance(body);
            body.setOnlineID((short)token.slot());body.setUsername("akr-civilian-"+token.slot());body.remote=true;
            body.getModData().rawset("AKRResidentId",token.resident());
            body.getModData().rawset("AKRResidentGeneration",(double)token.residentGeneration());
            body.setForceX(profile.x());body.setForceY(profile.y());body.setZ(profile.z());body.setLastZ(profile.z());
            body.realx=profile.x();body.realy=profile.y();body.realz=(byte)profile.z();
            if(GameServer.IDToPlayerMap.putIfAbsent((short)token.slot(),body)!=null)throw new IllegalStateException("online_id_claim_lost");
            place(body);
        } finally {long elapsed=System.nanoTime()-start;materializeTiming.add(elapsed);if(warm)warmTiming.add(elapsed);}
    }
    private static void place(IsoPlayer body){body.setCurrentSquareFromPosition();body.setMovingSquareNow();if(body.getSquare()==null||!body.getSquare().getMovingObjects().contains(body))throw new IllegalStateException("actor_unplaced");}
    @Override public boolean dead(IsoPlayer body){return body.isDead();}
    IsoDeadBody corpseFor(CivilianPool.Token token,IsoPlayer body){
        GameHooks.ownThread();binding(token,body);return heldCorpses.get(body);
    }
    IsoZombie reanimatedFor(CivilianPool.Token token,IsoPlayer body){
        GameHooks.ownThread();binding(token,body);
        return body.reanimatedCorpse instanceof IsoZombie zombie?zombie:null;
    }
    @Override public String handoffDeath(CivilianPool.Token token,IsoPlayer body) throws Exception {
        GameHooks.ownThread();Binding b=binding(token,body);
        if(!body.isDead()||b.retiring)throw new IllegalStateException("death_requires_owned_actor");
        if(body.getSquare()==null)throw new IllegalStateException("death_square_unknown");
        b.oldSquare=body.getSquare();
        body.die(); // Stock death creates a persistent IsoDeadBody and schedules native reanimation.
        IsoDeadBody corpse=(IsoDeadBody)diedBody.get(body);
        if(corpse==null||corpse.getSquare()==null||corpse.getCharacterOnlineID()!=token.slot()
            ||corpse.getContainer()==null||corpse.getObjectIDAsLong()==0)
            throw new IllegalStateException("native_corpse_unconfirmed");
        if(!token.resident().equals(corpse.getModData().rawget("AKRResidentId"))
            ||!Double.valueOf(token.residentGeneration()).equals(corpse.getModData().rawget("AKRResidentGeneration")))
            throw new IllegalStateException("corpse_identity_transfer_failed");
        if(corpsePlayer.get(corpse)!=body||corpse.animationPlayer!=body.getAnimationPlayer())
            throw new IllegalStateException("corpse_actor_reference_mismatch");
        b.corpseContainer=corpse.getContainer();
        heldCorpses.put(body,corpse);
        NativeResidentLifecycle.watch(corpse);
        return Long.toUnsignedString(corpse.getObjectIDAsLong());
    }
    @Override public boolean releaseDeath(CivilianPool.Token token,IsoPlayer body,String corpseId) throws Exception {
        GameHooks.ownThread();Binding b=binding(token,body);IsoDeadBody corpse=heldCorpses.get(body);
        if(corpse==null||!corpseId.equals(Long.toUnsignedString(corpse.getObjectIDAsLong())))
            throw new IllegalStateException("corpse_ownership_mismatch");
        if(body.isRagdollSimulationActive()||body.isOnFire()||body.getVehicle()!=null||body.isGrappling())return false;
        if(!token.resident().equals(corpse.getModData().rawget("AKRResidentId")))
            throw new IllegalStateException("corpse_identity_lost");
        if(corpse.getContainer()!=b.corpseContainer) {
            IsoZombie zombie=body.reanimatedCorpse instanceof IsoZombie current?current:null;
            if(corpse.getContainer()!=null||zombie==null||zombie.getInventory()!=b.corpseContainer
                ||!token.resident().equals(zombie.getModData().rawget("AKRResidentId")))
                throw new IllegalStateException("corpse_inventory_transfer_unconfirmed");
        }
        if(corpsePlayer.get(corpse)!=body||corpse.animationPlayer!=body.getAnimationPlayer())
            throw new IllegalStateException("corpse_reference_changed");
        // PlayerDeath is already sent; retire this exact player before its online ID is reused.
        if(!b.timeoutSent){INetworkPacket.sendToAll(PacketTypes.PacketType.PlayerTimeout,body);b.timeoutSent=true;}
        GameServer.IDToPlayerMap.remove((short)token.slot(),body);
        zombie.MovingObjectUpdateScheduler.instance.removeObject(body);
        body.removeFromWorld();body.removeFromSquare();
        if(!verify(token,body).confirmed())return false;
        corpsePlayer.set(corpse,null);corpse.animationPlayer=null;
        corpse.setCharacterOnlineID((short)-1);
        body.clearDiedBody();body.deathFinished=false;body.setOnDeathDone(false);body.setOnKillDone(false);
        body.getBodyDamage().RestoreToFullHealth();body.setHealth(100);body.setOnFloor(false);
        body.setKnockedDown(false);body.setPerformingAnAction(false);body.setAttackedBy(null);
        body.reanimatedCorpse=null;body.reanimatedCorpseId=-1;
        body.setDefaultState();body.StopAllActionQueue();
        reset.clearBeforeLoad(body);
        finishDelayedCleanup(body);b.retiring=true;parked.addLast(new Parked(token,body));
        profiles.remove(token.resident());heldCorpses.remove(body);
        return true;
    }
    private static boolean nearPlayer(float x,float y){
        if(GameServer.Players==null)return true;
        for(IsoPlayer player:GameServer.Players)if(player!=null&&(player.getSquare()==null||Math.hypot(player.getX()-x,player.getY()-y)<64))return true;
        return false;
    }
    @Override public boolean visibleOrUnknown(IsoPlayer body){return body.getSquare()==null||(staging!=null?!staging.hidden(body):nearPlayer(body.getX(),body.getY()));}
    @Override public void stop(IsoPlayer body){GameHooks.ownThread();body.setRunning(false);body.setSprinting(false);Binding b=owned.get(body);if(b!=null){b.packetUrgent|=b.gait!=Gait.IDLE;b.gait=Gait.IDLE;b.commandedSpeed=0;b.transition.reset();}replicate(body,0,0,false);}
    public void beginMovementFrame(IsoPlayer body){GameHooks.ownThread();owned.get(body).deferPackets=true;}
    public void endMovementFrame(IsoPlayer body,double lookahead){
        GameHooks.ownThread();Binding b=owned.get(body);b.deferPackets=false;
        replicate(body,b.gait==Gait.IDLE?0:b.commandedSpeed,lookahead,false);
    }
    public void locomotionTick(IsoPlayer body){Binding b=owned.get(body);if(b!=null)NativeActorLocomotion.tick(body,b.gait);}
    public String packetVariables(IsoPlayer body){Binding b=owned.get(body);return b==null?"unowned":b.wireMovement?"WalkSpeed="+b.wireWalk+",WalkInjury="+b.wireInjury:"stationary";}
    public String movementFailure(IsoPlayer body){Binding b=owned.get(body);return b==null?"unowned":b.movementFailure;}
    public String gait(IsoPlayer body){Binding b=owned.get(body);return b==null?"IDLE":b.gait.name();}
    public double commandedSpeed(IsoPlayer body){Binding b=owned.get(body);return b==null?0:b.commandedSpeed;}
    public double movementSpeed(IsoPlayer body,Gait requested){
        Binding b=Objects.requireNonNull(owned.get(body));long now=System.nanoTime();
        Gait gait=requested==Gait.RUN&&body.getStats().get(CharacterStat.ENDURANCE)<.2?Gait.WALK:requested;
        boolean changed=b.gait!=gait;
        if(changed){b.transition.begin(now,b.commandedSpeed);b.packetUrgent=true;}
        body.setRunning(gait==Gait.RUN);body.setSprinting(false);
        if(changed||now-b.ratesAt>=250_000_000L){body.updateSpeedModifiers();body.updateMovementRates();b.targetBlend=body.getVariableFloat("WalkSpeed",0);b.ratesAt=now;}
        // The RUN blend starts with its native walking clip; replicate the same evolving axis.
        body.setVariable("WalkSpeed",gait==Gait.RUN?(float)(b.targetBlend*b.transition.progress(now,true)):b.targetBlend);
        b.gait=gait;
        double target=NativeGaitSpeeds.installed().speed(gait==Gait.RUN,body.getVariableFloat("WalkInjury",0),body.getVariableFloat("WalkSpeed",0));
        b.commandedSpeed=b.transition.speed(now,gait==Gait.RUN,target);return b.commandedSpeed;
    }
    @Override public CivilianPool.Snapshot capture(IsoPlayer body) throws Exception{return codec.capture(body);}
    @Override public void retire(CivilianPool.Token token,IsoPlayer body){
        GameHooks.ownThread();Binding b=binding(token,body);
        if(body.isDead())throw new IllegalStateException("corpse_owned");
        if(!b.retiring){b.retiring=true;b.oldSquare=body.getSquare();}
        IsoPlayer replacement=GameServer.IDToPlayerMap.get((short)token.slot());
        if(replacement!=null&&replacement!=body)throw new IllegalStateException("online_id_replaced");
        if(!b.timeoutSent){INetworkPacket.sendToAll(PacketTypes.PacketType.PlayerTimeout,body);b.timeoutSent=true;}
        GameServer.IDToPlayerMap.remove((short)token.slot(),body);
        zombie.MovingObjectUpdateScheduler.instance.removeObject(body);
        body.removeFromWorld();body.removeFromSquare();
    }
    @Override public CivilianPool.Removal verify(CivilianPool.Token token,IsoPlayer body){
        GameHooks.ownThread();Binding b=binding(token,body);var cell=IsoWorld.instance.currentCell;
        boolean world=!attached(b.oldSquare,body)&&!attached(body.getSquare(),body)&&!cell.getObjectList().contains(body)
            &&!cell.getAddList().contains(body)&&!cell.getRemoveList().contains(body);
        boolean network=!GameServer.IDToPlayerMap.containsValue(body)&&!GameServer.Players.contains(body);
        return new CivilianPool.Removal(world,network);
    }
    /** Create at most one initialized object per call, before admitting gameplay work.
     * There is no allocation fallback in materialize. Caller owns warmup scheduling.
     */
    public void prewarmOne(int x,int y,int z) throws Exception {
        GameHooks.ownThread();long begin=System.nanoTime();
        NativeGaitSpeeds.installed();
        if(owned.size()>=capacity||registry.size()>=capacity)throw new IllegalStateException("native_actor_budget");
        IsoGridSquare square=ServerMap.instance.getGridSquare(x,y,z);
        if(square==null||!square.isFree(false)||nearPlayer(x,y))throw new IllegalStateException("warmup_square_unavailable");
        CivilianPool.Token token=new CivilianPool.Token("warmup",4096+constructed,0,"warmup-"+constructed,1);
        if(GameServer.IDToPlayerMap.containsKey((short)token.slot()))throw new IllegalStateException("warmup_id_in_use");
        try {
            IsoPlayer body=new IsoPlayer(IsoWorld.instance.currentCell,
                SurvivorFactory.CreateSurvivor(SurvivorFactory.SurvivorType.Neutral,false),x,y,z);
            // Track immediately; failures remain owned and cannot be reused.
            constructed++;Binding binding=new Binding(token);owned.put(body,binding);registry.put(body,binding);
            binding.oldSquare=body.getSquare();body.remote=true;body.setOnlineID((short)token.slot());
            var cell=IsoWorld.instance.currentCell;cell.getObjectList().remove(body);cell.getAddList().remove(body);
            zombie.MovingObjectUpdateScheduler.instance.removeObject(body);
            reset.clearBeforeLoad(body);
            pristine.put(body,codec.capture(body));
            binding.retiring=true;body.removeFromWorld();body.removeFromSquare();
            if(!verify(token,body).confirmed())throw new IllegalStateException("warmup_removal_unconfirmed");
            finishDelayedCleanup(body);parked.addLast(new Parked(token,body));
        } finally {coldTiming.add(System.nanoTime()-begin);}
    }
    /** Only after consuming and persisting a verified retirement receipt. Cancel caller path/action jobs first. */
    public void park(CivilianPool.Token token,IsoPlayer body,CivilianPool.Snapshot saved) throws Exception {
        GameHooks.ownThread();Binding b=binding(token,body);
        if(saved==null||!b.retiring||!verify(token,body).confirmed())throw new IllegalStateException("park_requires_verified_retirement");
        NativeActorReset.requireQuiescent(body);
        for(Parked entry:parked)if(entry.body()==body)throw new IllegalStateException("body_already_parked");
        if(zombie.pathfind.nativeCode.PathfindNative.useNativeCode)
            zombie.pathfind.nativeCode.PathfindNative.instance.cancelRequest(body);
        else PolygonalMap2.instance.cancelRequest(body);
        body.getPathFindBehavior2().reset();finishDelayedCleanup(body);
        parked.addLast(new Parked(token,body));profiles.remove(token.resident());
    }
    private void finishDelayedCleanup(IsoPlayer body) throws Exception {
        NativeActorReset.detachDescriptor(body);
        body.getEmitter().stopAll();
        Object removed=removedEmitters.get(null);
        if(!(removed instanceof List<?> list))throw new IllegalStateException("removed_emitters_contract");
        list.remove(body);
        if(list.contains(body))throw new IllegalStateException("removed_emitter_still_pending");
    }
    public void clearParked(){
        GameHooks.ownThread();
        while(!parked.isEmpty()){
            Parked cached=parked.peekFirst();
            if(!verify(cached.token(),cached.body()).confirmed()||cached.body().isDead())throw new IllegalStateException("parked_removal_unconfirmed");
            owned.remove(cached.body());registry.remove(cached.body());pristine.remove(cached.body());parked.removeFirst();
        }
    }
    public int parkedCount(){return parked.size();}
    public int retainedBodies(){return owned.size();}
    private static boolean attached(IsoGridSquare square,IsoPlayer body){return square!=null&&(square.getMovingObjects().contains(body)||square.getStaticMovingObjects().contains(body)||square.getObjects().contains(body));}
    private Binding binding(CivilianPool.Token token,IsoPlayer body){Binding b=owned.get(body);if(b==null||!b.token.equals(token))throw new IllegalArgumentException("stale_actor");return b;}
    /** Collision-constrained level walking only. Traversal owns all floor changes and interactions. */
    public boolean walk(CivilianPool.Token token,IsoPlayer body,float tx,float ty,double speed,double dt){
        GameHooks.ownThread();Binding b=binding(token,body);long start=System.nanoTime();
        try {
            b.movementFailure="";
            if(b.retiring||body.isDead()||!Double.isFinite(speed)||!Double.isFinite(dt)||speed<0||speed>8||dt<0)throw new IllegalArgumentException("walk_intent");
            if(!Float.isFinite(tx)||!Float.isFinite(ty))throw new IllegalArgumentException("walk_target");
            if(body.getSquare()==null)throw new IllegalStateException("actor_unloaded");
            if(b.stockUpdates!=0)throw new IllegalStateException("actor_in_stock_update");
            double distance=Math.hypot(tx-body.getX(),ty-body.getY());double step=Math.min(distance,speed*Math.min(dt,.25));
            if(distance<.005){stop(body);return true;}
            float x=body.getX(),y=body.getY(),nx=(float)(x+(tx-x)/distance*step),ny=(float)(y+(ty-y)/distance*step);
            IsoGridSquare dest=ServerMap.instance.getGridSquare((int)Math.floor(nx),(int)Math.floor(ny),(int)body.getZ());
            if(dest==null||dest.HasStairs()||body.getSquare().HasStairs()||!dest.TreatAsSolidFloor()
                ||PolygonalMap2.instance.lineClearCollide(x,y,nx,ny,(int)body.getZ(),null,false,true)){b.movementFailure="native_line_or_floor";stop(body);return false;}
            Vector2f resolved=PolygonalMap2.instance.resolveCollision(body,nx,ny,new Vector2f());
            if(Math.hypot(resolved.x-nx,resolved.y-ny)>.01){b.movementFailure="native_collision:"+resolved.x+","+resolved.y;stop(body);return false;}
            body.setForceX(nx);body.setForceY(ny);body.realx=nx;body.realy=ny;place(body);
            float angle=(float)Math.atan2(ty-y,tx-x);body.setForwardDirection((float)Math.cos(angle),(float)Math.sin(angle));body.setDirectionAngle((float)Math.toDegrees(angle));
            replicate(body,speed,Math.min(distance-step,speed*1.2),false);return true;
        } finally {moveTiming.add(System.nanoTime()-start);}
    }
    public void replicate(IsoPlayer body,double speed,double lookahead,boolean force){
        GameHooks.ownThread();Binding b=owned.get(body);if(b==null||b.retiring||b.deferPackets||speed==0&&b.gait!=Gait.IDLE)return;long now=System.nanoTime();
        float direction=body.getDirectionAngleRadians();
        boolean turn=Math.cos(direction-b.lastWireDirection)<.866;
        if(!force&&!b.packetUrgent&&!turn&&now-b.lastPacket<100_000_000L)return;
        b.lastPacket=now;b.lastWireDirection=direction;b.packetUrgent=false;
        try {
            var packet=new PlayerPacket();packet.id.set(body);ActorMovementVariables.fill(packet.variables,body,speed>0);
            b.wireMovement=speed>0;b.wireWalk=body.getVariableFloat("WalkSpeed",0);b.wireInjury=body.getVariableFloat("WalkInjury",0);
            var p=packet.prediction;p.type=0;p.x=body.getX();p.y=body.getY();p.z=(byte)Math.floor(body.getZ());p.direction=body.getDirectionAngleRadians();
            p.position.set(p.x,p.y,p.z);p.pathFindX=p.x;p.pathFindY=p.y;
            if(lookahead>=.125){p.type=1;p.moveDirection=p.direction;p.speed=(float)speed;p.distance=(byte)Math.min(127,Math.floor(lookahead*8));}
            short flags=NetworkPlayerVariables.getBooleanVariables(body);
            flags&=~(NetworkPlayerVariables.Flags.isRunning|NetworkPlayerVariables.Flags.isSprinting|NetworkPlayerVariables.Flags.hasDeferredMovement);
            if(speed>0)flags|=NetworkPlayerVariables.Flags.hasDeferredMovement;
            if(speed>0&&(b.gait==Gait.RUN||b.gait==Gait.IDLE&&speed>1.6))flags|=NetworkPlayerVariables.Flags.isRunning;
            packet.booleanVariables=flags;packet.disconnected=false;packet.hitVehicleId.set(null);
            for(UdpConnection connection:GameServer.udpEngine.connections) {
                if(connection==null||!connection.isFullyConnected()||!connection.isRelevantTo(p.x,p.y)||!body.checkCanSeeClient(connection))continue;
                if(b.announced.add(connection.getConnectedGUID()))GameServer.sendPlayerConnected(body,connection);
                var type=PacketTypes.PacketType.PlayerUpdateReliable;var writer=connection.startPacket();type.doPacket(writer);packet.write(writer);type.send(connection);b.packets++;replicationPackets++;
            }
        } finally {replicationTiming.add(System.nanoTime()-now);}
    }
}
