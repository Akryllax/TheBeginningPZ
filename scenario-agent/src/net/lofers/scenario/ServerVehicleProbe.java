package net.lofers.scenario;

import java.util.*;
import org.joml.Vector3f;
import zombie.GameTime;
import zombie.core.physics.Bullet;
import zombie.core.physics.WorldSimulation;
import zombie.iso.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.network.GameServer;
import zombie.network.ServerMap;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.*;

/** One opt-in empty car in a disposable world. Physics and game state stay on the game thread. */
final class ServerVehicleProbe {
    private static final Set<String> PREPARING=Set.of("loading","validating_road","preparing_terrain","preparing_physics");
    private static final Set<String> ACTIVE=Set.of("loading","validating_road","preparing_terrain","preparing_physics","settling","driving","waiting_obstacle","braking","stopped_visible","inspecting");
    private boolean inspectionOnly;
    private final ProbeControl.Config config;
    private final ProbeControl io;
    private final String world,epoch;
    private final ProbeRoute route;
    private ProbeRoute driveRoute;
    private final Vector3f forward=new Vector3f();
    private final Vector3f otherForward=new Vector3f();
    private final ArrayList<TrafficFootprint> parkedShapes=new ArrayList<>();
    private final TrafficPassReservations passReservations=new TrafficPassReservations();
    private TrafficPassReservations.Grant passGrant;
    private TrafficPassReservations.Request passRequest;
    private TrafficBypass.Job bypassJob;
    private TrafficBypass.Candidate bypassCandidate;
    private List<TrafficBypass.Candidate> bypassCandidates=List.of();
    private int bypassCandidateIndex,bypassTileCursor,bypassesCompleted;
    private long bypassRevision,bypassRequestedAt;
    private boolean bypassUsed,nearbyTrafficStatic,validationShoulder,bypassOffroad;
    private String bypassStatus="disabled";
    private final NativeVehicleSamples nativeSamples=new NativeVehicleSamples();
    private final NativeVehicleSamples.Source nativeSource=new NativeVehicleSamples.Source(){
        public int count(){return Bullet.getVehicleCount();}
        public int read(int offset,float[] buffer){return Bullet.getVehiclePhysics(offset,buffer);}
    };
    private ProbeCarLimits carLimits;
    private NativeProbeControls.Applied applied;
    private float nativeMass;
    private double forecastContact=Double.POSITIVE_INFINITY;
    private int forecastBlocker=-1;
    private double parkedStop=Double.POSITIVE_INFINITY;
    private int parkedBlocker=-1,bypassRequests;
    private long waitingNanos;
    private TrafficTemperament temperament;
    private TrafficBlockage blockage;
    private TrafficBlockage.Decision blockageDecision;
    private boolean worldPresent,registryPresent,chunkPresent;
    private ProbeDriver driver;
    private final ProbeSafetyWork safetyWork;
    private long safetyScanNanos,safetyScanMaxNanos,safetyScanOver1ms;
    private String safetyStatus="";
    private ProbeDriver.Output control;
    private BaseVehicle vehicle;
    private boolean nativeBody,worldAdded,nativeTerrain,ownsNativeWorld;
    private int terrainMinX,terrainMinY,terrainWidth;
    private int terrainCreated,terrainRemoved;

    private int nativeBefore=-1,nativeAfter=-1,vehicleId=-1,chunkCursor,validationCursor;
    private long commandId,ticks,publications,started,bodyStarted,phaseAt,nextStatus,physicsFrameStart,physicsFrames;
    private String phase="waiting_for_world",error="",finishReason="";
    private double maxTiltDegrees;
    private double distance,displacement,maxSpeed,lastX,lastY,lastZ,speed,stopDistance,goalDistance,heading,bodyRadius,wheelbase;
    private long lastControlAt,coldNanosThisTick;
    private ProbeTiming warmTiming=new ProbeTiming(),coldTiming=new ProbeTiming();
    private final Map<String,Long> coldOperations=new LinkedHashMap<>();
    private long stepMaxNanos;
    private boolean readyServer,readyName,readyCell,readyMeta,readyMap,nativeWorldReady;
    ServerVehicleProbe(ProbeControl.Config config,String world,String epoch)throws Exception {
        this.config=config;route=config.route();safetyWork=new ProbeSafetyWork(route.extendedImpact);driveRoute=route;this.world=world;this.epoch=epoch;io=new ProbeControl(config,epoch);
        publish();Thread thread=new Thread(io,"lofers-vehicle-probe-files");thread.setDaemon(true);thread.start();
    }
    private void phase(String next){phase=next;phaseAt=System.nanoTime();System.out.println("[LofersVehicleProbe] "+next+" command="+commandId+(error.isEmpty()?"":" error="+error));}
    void tick() {
        long begin=System.nanoTime();ticks++;boolean warmOnEntry=nativeBody;coldNanosThisTick=0;
        try {
            readyServer=GameServer.server;readyName=world.equals(GameServer.serverName);
            readyCell=IsoWorld.instance!=null&&IsoWorld.instance.currentCell!=null;
            readyMeta=IsoWorld.instance!=null&&IsoWorld.instance.metaGrid!=null;
            readyMap=ServerMap.instance!=null&&ServerMap.instance.cellMap!=null;
            nativeWorldReady=WorldSimulation.instance.created;
            if(!readyServer||!readyName)return;
            ProbeControl.Command command=io.command.getAndSet(null);
            if(command!=null){
                commandId=command.id();
                if(command.action().equals("stop")){finishReason="operator_stop";if(vehicle!=null)phase("braking");else{cleanup();phase("stopped");}}
                else if(vehicle==null&&!PREPARING.contains(phase)) {
                    inspectionOnly=command.action().equals("inspect");
                    error="";finishReason="";distance=0;maxSpeed=0;publications=0;physicsFrames=0;chunkCursor=0;validationCursor=0;control=null;
                    warmTiming=new ProbeTiming();coldTiming=new ProbeTiming();coldOperations.clear();stepMaxNanos=0;applied=null;carLimits=null;
                    safetyScanNanos=safetyScanMaxNanos=safetyScanOver1ms=0;safetyStatus="";
                    waitingNanos=0;parkedStop=Double.POSITIVE_INFINITY;parkedBlocker=-1;bypassRequests=0;blockageDecision=null;
                    driveRoute=route;bypassUsed=false;validationShoulder=false;bypassOffroad=false;bypassesCompleted=0;bypassCandidate=null;bypassJob=null;bypassCandidates=List.of();bypassCandidateIndex=bypassTileCursor=0;
                    bypassStatus=config.bypass()?"ready":"disabled";io.bypassJob.set(null);io.bypassResult.set(null);
                    maxTiltDegrees=0;stopDistance=0;displacement=0;goalDistance=route.length;lastX=config.x();lastY=config.y();lastZ=0;
                    nativeBefore=-1;nativeAfter=-1;started=System.nanoTime();phase("loading");
                }
            }
            boolean ready=readyCell&&readyMeta&&readyMap;
            if(!ready){if(vehicle!=null||nativeTerrain)fail("world_became_unavailable");return;}
            if(nativeBody)physicsFrames=WorldSimulation.instance.getBulletFrameNo()-physicsFrameStart;
            if(phase.equals("waiting_for_world"))phase("armed");
            if(ACTIVE.contains(phase)) {
                // cellMap is allocated after ServerMap.grid is assigned: never call the
                // native method's pre-initialization sleep loop on the game thread.
                for(var anchor:route.loadingAnchors)ServerMap.instance.characterIn(anchor.x(),anchor.y(),route.requestWidth());
            }
            if(phase.equals("loading")) {
                if(System.nanoTime()-started>120_000_000_000L){fail("loaded_area_timeout");return;}
                if(!loadedCorridor())return;
                phase(inspectionOnly?"inspecting":"validating_road");
            }
            if(phase.equals("inspecting")){
                if(System.nanoTime()-phaseAt>120_000_000_000L){finishReason="inspection_complete";phase("complete");}
                return;
            }
            if(phase.equals("validating_road")){
                long deadline=System.nanoTime()+1_000_000L;int count=0;
                while(validationCursor<route.tiles.size()&&count++<48&&System.nanoTime()<deadline){
                    ProbeRoute.Tile tile=route.tiles.get(validationCursor++);String problem=tileProblem(tile.x(),tile.y(),config.roadMode(),false);
                    if(!problem.isEmpty())throw new IllegalStateException(problem+"_at_"+tile.x()+"_"+tile.y());
                }
                if(validationCursor<route.tiles.size())return;
                long coldStart=System.nanoTime();
                try{
                if(!WorldSimulation.instance.created){
                    // An empty dedicated world may never initialize Bullet through
                    // the client-only vehicle constructor. Explicit operator start
                    // initializes it after the map exists; never during premain.
                    if(Bullet.cmdBuf==null)Bullet.init();
                    // Headless vehicles already have transforms in the server's
                    // existing frame. WorldSimulation.create() changes that frame
                    // to the map minimum, shifting every preloaded parked car.
                    // Initialize only our native server world in the SAME frame;
                    // never rebase unrelated vehicles or write their positions.
                    var simulation=WorldSimulation.instance;
                    if(Bullet.isWorldInit())throw new IllegalStateException("unowned_native_world_already_initialized");
                    if(!ProbeRoute.finite(simulation.offsetX,simulation.offsetY)||
                       simulation.offsetX!=(int)simulation.offsetX||simulation.offsetY!=(int)simulation.offsetY)
                        throw new IllegalStateException("unsupported_native_coordinate_frame");
                    var meta=IsoWorld.instance.metaGrid;
                    Bullet.initWorld(meta.getMinX(),meta.getMinY(),meta.getMaxX(),meta.getMaxY(),
                        (int)simulation.offsetX,(int)simulation.offsetY,false);
                    simulation.time=GameTime.getServerTimeMills();
                    simulation.created=Bullet.isWorldInit();nativeWorldReady=simulation.created;
                    if(!nativeWorldReady||!Bullet.isWorldInit())throw new IllegalStateException("native_world_initialization_failed");
                    ownsNativeWorld=true;
                    System.out.println("[LofersVehicleProbe] native_world_initialized");
                }
                // Java remains a dedicated server. The native server-cell mode only
                // stores shapes, never activates their bodies, and excludes car/car
                // contacts. Own one ordinary native collision map instead. The
                // stock callback still reads Java ServerMap because server=true there.
                if(!ownsNativeWorld||Bullet.getVehicleCount()!=0||nativeTerrain)
                    throw new IllegalStateException("probe_requires_owned_empty_native_world");
                terrainCreated=0;terrainRemoved=0;
                terrainMinX=route.chunks.stream().mapToInt(ProbeRoute.Tile::x).min().orElseThrow();
                terrainMinY=route.chunks.stream().mapToInt(ProbeRoute.Tile::y).min().orElseThrow();
                terrainWidth=Math.max(route.chunks.stream().mapToInt(ProbeRoute.Tile::x).max().orElseThrow()-terrainMinX,
                    route.chunks.stream().mapToInt(ProbeRoute.Tile::y).max().orElseThrow()-terrainMinY)+1;
                if(terrainWidth>(route.extendedImpact?81:13))throw new IllegalStateException("native_collision_map_extent");
                }finally{coldCost("native_world",coldStart);}
                ProbeRockCollider.activate(config.impact());
                phase("preparing_terrain");
            }
            if(phase.equals("preparing_terrain")){
                long coldStart=System.nanoTime();
                try{Bullet.activateChunkMap(0,terrainMinX,terrainMinY,terrainWidth);nativeTerrain=true;terrainCreated++;}
                finally{coldCost("terrain_map",coldStart);}
                System.out.println("[LofersVehicleProbe] native_collision_map_created width="+terrainWidth);phase("preparing_physics");
                return;
            }
            if(phase.equals("preparing_physics")) {
                if(chunkCursor<route.chunks.size()) {
                    ProbeRoute.Tile xy=route.chunks.get(chunkCursor);IsoChunk chunk=ServerMap.instance.getChunk(xy.x(),xy.y());
                    if(chunk==null||!chunk.loaded){fail("physics_chunk_unloaded");return;}
                    long coldStart=System.nanoTime();try{ProbeTerrainMeshGuard.validate(chunk);Bullet.setChunkMinMaxLevel(chunk.wx,chunk.wy,chunk.minLevel,chunk.maxLevel);chunk.updatePhysicsForLevel(0);if(!ProbeRockCollider.failure().isEmpty())throw new IllegalStateException(ProbeRockCollider.failure());chunkCursor++;}finally{coldCost("chunk_upload",coldStart);}return;
                }
                long coldStart=System.nanoTime();try{create();}finally{coldCost("vehicle_create",coldStart);}phase("settling");
            }
            if(vehicle==null)return;
            if(!ProbeRockCollider.failure().isEmpty()){fail(ProbeRockCollider.failure());return;}
            worldPresent=!vehicle.isRemovedFromWorld();
            registryPresent=VehicleManager.instance.getVehicleByID((short)vehicleId)==vehicle;
            chunkPresent=vehicle.chunk!=null&&vehicle.chunk.vehicles.contains(vehicle);
            if(!worldPresent||!registryPresent||!chunkPresent){fail("probe_world_membership_lost");return;}
            if(!vehicle.isNetPlayerAuthorization(BaseVehicle.Authorization.Server)||vehicle.getNetPlayerId()!=-1){fail("native_authority_changed");return;}
            if(vehicle.hasPassenger()){fail("unexpected_passenger");return;}
            if(!loadedCorridor()){fail("road_chunks_unloaded");return;}
            double previousX=lastX,previousY=lastY;lastX=vehicle.getX();lastY=vehicle.getY();lastZ=vehicle.jniTransform.origin.y;
            speed=Math.hypot(vehicle.jniLinearVelocity.x,vehicle.jniLinearVelocity.z)*3.6;
            maxTiltDegrees=Math.max(maxTiltDegrees,Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,vehicle.jniTransform.basis.m11())))));
            distance+=Math.hypot(lastX-previousX,lastY-previousY);displacement=Math.hypot(lastX-config.x(),lastY-config.y());maxSpeed=Math.max(maxSpeed,speed);
            vehicle.getForwardVector(forward);heading=Math.toDegrees(Math.atan2(forward.x,forward.z));
            goalDistance=Math.hypot(lastX-route.points.getLast().x(),lastY-route.points.getLast().y());
            if(!Double.isFinite(speed)||!Double.isFinite(distance)||!Double.isFinite(lastZ)){fail("nonfinite_physics");return;}
            if(lastZ<-0.5||lastZ>4){fail("invalid_physics_height");return;}
            if(speed>Math.max(8,config.speed()+3)||distance>config.distance()+(bypassUsed?10:5)){fail("motion_limit_exceeded");return;}
            long now=System.nanoTime();
            if(phase.equals("waiting_obstacle"))waitingNanos+=Math.max(0,now-lastControlAt);
            if(now-bodyStarted-waitingNanos>config.deadlineSeconds()*1_000_000_000L){fail("absolute_deadline");return;}
            if(config.impact()!=null&&(phase.equals("driving")||phase.equals("settling"))&&ProbeCrashFeedback.snapshot().crashes()>0){
                finishReason="native_impact";phase("braking");
            }
            if(phase.equals("settling")&&now-phaseAt>2_000_000_000L)phase("driving");
            double delta=lastControlAt==0?0.1:(now-lastControlAt)/1_000_000_000.0;lastControlAt=now;
            float force=0,brake=80,steering=0;
            if(phase.equals("driving")||phase.equals("waiting_obstacle")){
                if(route.trajectory!=null){
                    carLimits=NativeProbeControls.limits(vehicle,speed);if(bypassOffroad)carLimits=BypassTerrain.cautious(carLimits);driver.limits(carLimits);
                    if(nativeMass!=vehicle.getMass()){nativeMass=vehicle.getMass();Bullet.setVehicleMass(vehicleId,nativeMass);}
                }
                String danger=impactObserverProblem();
                if(danger.isEmpty())danger=config.roadMode()?warmSafety():"";
                if(passGrant!=null&&danger.isEmpty()){
                    passReservations.resolve(List.of(passRequest),now/1e9);
                    if(!passReservations.mayProceed(passGrant,now/1e9))danger="passing_reservation_lost";
                }
                safetyStatus=danger;
                control=driver.step(lastX,lastY,forward.x,forward.z,speed,delta,danger,parkedStop);
                if(!control.stopReason().isEmpty()){error=control.stopReason();finishReason=error;phase("braking");}
                else if(control.arrived()){finishReason="route_arrived";phase("braking");}
                else if(waitingNanos>300_000_000_000L){error="obstacle_wait_deadline";finishReason=error;phase("braking");}
                else if(now-bodyStarted-waitingNanos>(config.deadlineSeconds()-(config.roadMode()?26:8))*1_000_000_000L){error="drive_deadline";finishReason=error;phase("braking");}
                else if(driver.waitingForObstacle()&&!phase.equals("waiting_obstacle"))phase("waiting_obstacle");
                else if(!driver.waitingForObstacle()&&phase.equals("waiting_obstacle"))phase("driving");
                force=(float)control.engineForce();brake=(float)control.brake();steering=(float)control.steering();
                if(control.stopReason().isEmpty()&&!control.arrived()){
                    blockageDecision=blockage.step(parkedBlocker,driver.waitingForObstacle(),delta);
                    if(blockageDecision.requestBypass())bypassRequests++;
                    if(config.bypass()&&!bypassUsed&&phase.equals("waiting_obstacle"))advanceBypass(now);
                    if(passGrant!=null&&control!=null&&control.progress()>bypassCandidate.rejoinProgress()&&outsidePassingCorridor()){
                        if(passReservations.release(passGrant,true,now/1e9)){passGrant=null;passRequest=null;bypassesCompleted++;bypassStatus="rejoined";}
                    }
                }
            }
            boolean horn=phase.equals("waiting_obstacle")&&blockageDecision!=null&&blockageDecision.horn();
            if(horn&&!vehicle.soundHornOn)vehicle.onHornStart();
            else if(!horn&&vehicle.soundHornOn)vehicle.onHornStop();
            if(!phase.equals("driving")&&!phase.equals("waiting_obstacle")){force=0;brake=100;steering=0;}
            if(route.trajectory!=null){
                applied=NativeProbeControls.apply(vehicle,speed,force,brake,steering);
                force=applied.force();brake=applied.brake();steering=applied.steering();
            }
            // Direct native API is intentional: BaseVehicle's server branch skips it.
            Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
            Bullet.controlVehicle(vehicleId,force,brake,steering);vehicle.setCurrentSteering(steering);
            vehicle.getController().engineForce=force;vehicle.getController().brakingForce=brake;
            vehicle.getController().acceleratorOn=force>0;vehicle.getController().brakeOn=brake>0;
            vehicle.throttle=force>0?(applied==null?.25f:(float)Math.min(1,force/Math.max(1,applied.availableForce()))):0;vehicle.setBraking(brake>0);
            vehicle.setStoplightsOn(brake>0);vehicle.updateFlags=(short)(vehicle.updateFlags|2);publications++;
            if(phase.equals("braking")){
                if(speed<0.25&&now-phaseAt>1_000_000_000L){stopDistance=distance;phase("stopped_visible");}
                else if(now-phaseAt>5_000_000_000L){fail("brake_deadline");return;}
            }
            // Give observers time to distinguish a stationary car from removal.
            // Legacy straight probes retain their shorter absolute deadline.
            long visibleNanos=config.roadMode()?20_000_000_000L:3_000_000_000L;
            if(phase.equals("stopped_visible")&&now-phaseAt>visibleNanos){cleanup();phase(error.isEmpty()?"complete":"failed");}
        }catch(Throwable t){fail(t.getClass().getSimpleName()+":"+Objects.toString(t.getMessage(),""));}
        finally {
            if(System.nanoTime()>nextStatus){publish();nextStatus=System.nanoTime()+250_000_000L;}
            long elapsed=System.nanoTime()-begin;stepMaxNanos=Math.max(stepMaxNanos,elapsed);
            if(warmOnEntry&&coldNanosThisTick==0)warmTiming.add(elapsed);
            else if(coldNanosThisTick>0)coldTiming.add(elapsed);
        }
    }
    private void coldCost(String name,long start){long elapsed=System.nanoTime()-start;coldNanosThisTick+=elapsed;coldOperations.merge(name,elapsed,Math::max);}
    private boolean loadedCorridor(){
        for(ProbeRoute.Tile c:route.chunks) {IsoChunk chunk=ServerMap.instance.getChunk(c.x(),c.y());if(chunk==null||!chunk.loaded)return false;}return true;
    }
    private static String declared(zombie.core.properties.PropertyContainer properties,String name){
        String value=properties.get(name);return value==null&&properties.has(name)?"":value;
    }
    private String tileProblem(int x,int y,boolean requireRoad,boolean actors){
        return tileProblem(x,y,requireRoad,actors,false);
    }
    private String tileProblem(int x,int y,boolean requireRoad,boolean actors,boolean shoulder){
        IsoGridSquare square=ServerMap.instance.getGridSquare(x,y,0);
        boolean target=config.impact()!=null&&config.impact().tile(x,y);
        if(square==null||square.getFloor()==null||!square.TreatAsSolidFloor()||!square.isOutside()||(!square.isFree(false)&&!target)||square.HasStairs())return "road_not_clear";
        var merged=square.getProperties();
        String physics=TrafficTileObstacle.problem(declared(merged,"PhysicsShape"),declared(merged,"PhysicsMesh"),null,merged.get("MoveType"),merged.has("StopCar"),merged.has("HitByCar"));
        if(!physics.isEmpty()&&!target)return physics;
        boolean targetFound=false;
        var objects=square.getObjects();if(objects.size()>32)return "road_object_limit";
        for(int i=0;i<objects.size();i++){
            var object=objects.get(i);var properties=object.getProperties();
            if(properties==null)return "object_properties_unavailable";
            if(target&&config.impact().matches(object)){if(targetFound)return "duplicate_impact_target";targetFound=true;continue;}
            if(target&&blockingFlags(properties))return "extra_impact_tile_obstacle";
            physics=TrafficTileObstacle.problem(declared(properties,"PhysicsShape"),declared(properties,"PhysicsMesh"),object.sprite==null?null:object.sprite.name,properties.get("MoveType"),properties.has("StopCar"),properties.has("HitByCar"));
            if(!physics.isEmpty())return physics;
        }
        if(target&&!targetFound)return "impact_target_missing";
        if(requireRoad||shoulder){
            if(!BypassTerrain.allows(square.getFloor().getProperties().get("FloorMaterial"),shoulder))return shoulder?"unsuitable_shoulder_surface":"not_asphalt_road";
            for(int i=0;i<objects.size();i++){
                var properties=objects.get(i).getProperties();String material=properties==null?null:properties.get("FloorMaterial");
                if(material!=null&&!BypassTerrain.allows(material,shoulder))return "conflicting_surface_overlay";
            }
            IsoGridSquare west=ServerMap.instance.getGridSquare(x-1,y,0),north=ServerMap.instance.getGridSquare(x,y-1,0);
            if(west==null||north==null||(square.isBlockedTo(west)&&!impactEdge(square,west))||(square.isBlockedTo(north)&&!impactEdge(square,north)))return "road_wall_edge";
        }
        if(actors){var moving=square.getMovingObjects();if(moving.size()>16)return "road_actor_limit";
            for(IsoMovingObject object:moving)if(object!=vehicle)return "actor_in_vehicle_path";}
        return "";
    }
    private static boolean blockingFlags(zombie.core.properties.PropertyContainer p){
        return p.has(IsoFlagType.solid)||p.has(IsoFlagType.solidtrans)||p.has(IsoFlagType.blocksight)||p.has(IsoFlagType.collideN)||p.has(IsoFlagType.collideW);
    }
    /** Only the exact tagged stock boulder may account for a blocked adjacent edge. */
    private boolean impactEdge(IsoGridSquare a,IsoGridSquare b){
        var target=config.impact();
        if(target==null||!ProbeImpactTarget.BOULDER.equals(target.sprite())||
            !(target.tile(a.getX(),a.getY())||target.tile(b.getX(),b.getY())))return false;
        boolean found=false;
        for(var sq:List.of(a,b)){
            if(sq.HasStairs()||!sq.getSpecialObjects().isEmpty()||sq.getObjects().size()>32)return false;
            for(int i=0;i<sq.getObjects().size();i++){
                var object=sq.getObjects().get(i);
                if(target.tile(sq.getX(),sq.getY())&&target.matches(object)){if(found)return false;found=true;continue;}
                var p=object.getProperties();if(p==null||blockingFlags(p))return false;
                if(!TrafficTileObstacle.problem(declared(p,"PhysicsShape"),declared(p,"PhysicsMesh"),object.sprite==null?null:object.sprite.name,p.get("MoveType"),p.has("StopCar"),p.has("HitByCar")).isEmpty())return false;
            }
        }
        return found;
    }
    private String impactObserverProblem(){
        if(config.impact()==null)return "";
        if(GameServer.Players.size()>16)return "impact_observer_limit";
        for(var player:GameServer.Players){
            if(player==null||!ProbeRoute.finite(player.getX(),player.getY())||
               route.project(player.getX(),player.getY()).distance()<12)return "observer_inside_impact_envelope";
        }
        return "";
    }
    private final long[] checkedRoadTiles=new long[16384];
    private int checkOriginX,checkOriginY;
    private String checkRoadOnce(int x,int y){
        int dx=x-checkOriginX,dy=y-checkOriginY;
        if(dx<0||dx>=1024||dy<0||dy>=1024)return "road_check_extent";
        int row=dy*16+(dx>>>6);long bit=1L<<(dx&63);if((checkedRoadTiles[row]&bit)!=0)return "";
        if(!safetyWork.tile())return "road_check_capacity";
        checkedRoadTiles[row]|=bit;return tileProblem(x,y,!bypassOffroad,true,bypassOffroad);
    }
    private String warmSafety(){
        long begin=System.nanoTime();safetyWork.reset();
        try{return scanSafety();}
        finally{
            safetyScanNanos=System.nanoTime()-begin;safetyScanMaxNanos=Math.max(safetyScanMaxNanos,safetyScanNanos);
            if(safetyScanNanos>1_000_000L)safetyScanOver1ms++;
        }
    }
    private String scanSafety(){
        forecastContact=Double.POSITIVE_INFINITY;forecastBlocker=-1;
        parkedStop=Double.POSITIVE_INFINITY;parkedBlocker=-1;
        parkedShapes.clear();nearbyTrafficStatic=true;
        Arrays.fill(checkedRoadTiles,0L);checkOriginX=(int)Math.floor(lastX)-512;checkOriginY=(int)Math.floor(lastY)-512;
        // The current body footprint, plus conservative stopping-space samples,
        // must remain asphalt and clear. This checks actual native pose, not
        // merely whether the planned centerline belongs to a broad road polygon.
        for(int y=(int)Math.floor(lastY-3);y<=(int)Math.floor(lastY+3);y++)for(int x=(int)Math.floor(lastX-3);x<=(int)Math.floor(lastX+3);x++){
            double dx=Math.max(Math.max(x-lastX,0),lastX-(x+1.0)),dy=Math.max(Math.max(y-lastY,0),lastY-(y+1.0));
            if(route.laneMode?!ProbeFootprint.touches(x,y,lastX,lastY,forward.x,forward.z,route.extendedImpact):Math.hypot(dx,dy)>2.25)continue;
            String problem=checkRoadOnce(x,y);if(!problem.isEmpty())return problem;
        }
        double deceleration=carLimits==null?.6:carLimits.brakingDeceleration();
        double stopping=Math.min(route.extendedImpact?320:route.trajectory!=null?64:route.laneMode?18:4,2+Math.pow(speed/3.6,2)/(2*deceleration));
        if(route.trajectory!=null){
            // The precomputed full-width swept corridor follows the curve; a
            // straight ray would falsely leave asphalt while approaching a turn.
            // The distance bound includes the braking path plus front overhang.
            double radius=stopping+3;
            for(var tile:driveRoute.tiles){
                double dx=tile.x()+.5-lastX,dy=tile.y()+.5-lastY;
                if(dx*dx+dy*dy>radius*radius||(route.extendedImpact&&dx*forward.x+dy*forward.z< -3))continue;
                String problem=checkRoadOnce(tile.x(),tile.y());if(!problem.isEmpty())return problem;
            }
        }else for(double along=2;along<=stopping;along+=0.5)for(int side=-1;side<=1;side++){
                int x=(int)Math.floor(lastX+forward.x*along+forward.z*side),y=(int)Math.floor(lastY+forward.z*along-forward.x*side);
                String problem=checkRoadOnce(x,y);if(!problem.isEmpty())return problem;
        }
        double progress=control==null?0:control.progress();
        double horizon=Math.min(route.extendedImpact?20:12,2+speed/3.6/deceleration);
        var predicted=CollisionForecast.following(driveRoute,progress,lastX,lastY,speed/3.6,horizon);
        String snapshotProblem=nativeSamples.refresh(nativeSource);if(!snapshotProblem.isEmpty())return snapshotProblem;
        if(nativeSamples.get(vehicleId)==null)return "own_native_vehicle_missing";
        // Include the loaded curve and its margin, not just the current 3x3
        // chunks. This is still a bounded probe, not a world-wide traffic census.
        for(var xy:route.chunks){
            IsoChunk chunk=ServerMap.instance.getChunk(xy.x(),xy.y());if(chunk==null||!chunk.loaded)return "nearby_chunk_unloaded";
            for(BaseVehicle other:chunk.vehicles){if(other==vehicle)continue;
                if(!safetyWork.vehicle())return "vehicle_check_capacity";
                if(other.getId()<0)return "vehicle_identity_unavailable";
                // Client packet age is not available in this adapter. Never
                // treat a stale/client-owned velocity as a fresh native sample.
                if(other.hasPassenger()||other.getNetPlayerId()!=-1||!other.isNetPlayerAuthorization(BaseVehicle.Authorization.Server))return "untracked_vehicle_near_course";
                double vx=0,vy=0;
                var motion=nativeSamples.get(other.getId());
                if(motion!=null){vx=motion.vx();vy=motion.vy();}
                else if(!Float.isFinite(other.getCurrentSpeedKmHour())||Math.abs(other.getCurrentSpeedKmHour())>.1)return "vehicle_motion_unavailable";
                VehicleScript otherScript=other.getScript();if(otherScript==null)return "vehicle_shape_unavailable";
                double radius=Math.hypot(otherScript.getExtents().x(),otherScript.getExtents().z())/2;
                boolean parked=Math.hypot(vx,vy)<=.03&&Float.isFinite(other.getCurrentSpeedKmHour())&&Math.abs(other.getCurrentSpeedKmHour())<=.1;
                if(parked){
                    TrafficFootprint shape=shape(other);parkedShapes.add(shape);
                    if(bypassUsed){
                        if(!TrafficFootprint.pathClear(driveRoute,progress,Math.min(60,stopping+3),shape(vehicle),shape))return "bypass_vehicle_clearance_lost";
                        continue;
                    }
                }else{nearbyTrafficStatic=false;if(bypassUsed)return "moving_vehicle_during_bypass";}
                var obstacle=new CollisionForecast.Obstacle(other.getX(),other.getY(),vx,vy,radius,0);
                double contact=CollisionForecast.firstContact(predicted,bodyRadius+.65,obstacle,route.extendedImpact);
                if(contact<forecastContact){forecastContact=contact;forecastBlocker=other.getId();}
                if(contact==0)return "immediate_vehicle_contact";
                if(parked){
                    double stop=ParkedObstacle.stopProgress(driveRoute,progress,lastX,lastY,bodyRadius+.65,obstacle,config.bypass()?4:temperament.stoppedGap());
                    if(stop<parkedStop){parkedStop=stop;parkedBlocker=other.getId();}
                }else if(Double.isFinite(contact))return "predicted_vehicle_contact";
            }
        }
        return "";
    }
    private TrafficFootprint shape(BaseVehicle car){
        car.getForwardVector(otherForward);var extents=car.getScript().getExtents();
        return new TrafficFootprint(car.getId(),car.getX(),car.getY(),Math.atan2(otherForward.x,otherForward.z),extents.x()/2,extents.z()/2);
    }
    private boolean outsidePassingCorridor(){
        for(var tile:bypassCandidate.corridor())if(ProbeFootprint.touches(tile.x(),tile.y(),lastX,lastY,forward.x,forward.z))return false;
        return true;
    }
    private void advanceBypass(long now){
        TrafficFootprint blocker=null;
        for(var shape:parkedShapes)if(shape.id()==parkedBlocker)blocker=shape;
        if(blocker==null||!nearbyTrafficStatic){bypassStatus="waiting_for_stable_observations";return;}
        if(bypassJob!=null&&(bypassJob.blocker().id()!=blocker.id()||Math.hypot(bypassJob.blocker().x()-blocker.x(),bypassJob.blocker().y()-blocker.y())>.1||
           Math.abs(ProbeRoute.wrap(bypassJob.blocker().heading()-blocker.heading()))>.03||
           Math.hypot(bypassJob.ego().x()-lastX,bypassJob.ego().y()-lastY)>.1||now-bypassRequestedAt>3_000_000_000L)){
            bypassJob=null;bypassCandidates=List.of();bypassStatus="candidate_observation_expired";
        }
        if(blockageDecision.requestBypass()&&bypassJob==null){
            bypassJob=new TrafficBypass.Job(commandId,++bypassRevision,route,control.progress(),shape(vehicle),blocker,List.copyOf(parkedShapes));
            bypassRequestedAt=now;io.bypassJob.set(bypassJob);bypassStatus="planning";
        }
        var result=io.bypassResult.getAndSet(null);
        if(result!=null&&bypassJob!=null&&result.job().command()==commandId&&result.job().revision()==bypassJob.revision()){
            bypassCandidates=result.candidates();bypassCandidateIndex=0;bypassTileCursor=0;validationShoulder=false;bypassStatus=result.reason();
            if(bypassCandidates.isEmpty()){bypassJob=null;return;}
        }
        if(bypassJob==null||bypassCandidates.isEmpty())return;
        var candidate=bypassCandidates.get(bypassCandidateIndex);
        long deadline=System.nanoTime()+500_000;int checked=0;
        while(bypassTileCursor<candidate.route().tiles.size()&&checked++<24&&System.nanoTime()<deadline){
            var tile=candidate.route().tiles.get(bypassTileCursor++);String reason=tileProblem(tile.x(),tile.y(),!validationShoulder,true,validationShoulder);
            if(!reason.isEmpty()){
                bypassStatus="candidate_"+reason;bypassTileCursor=0;bypassCandidateIndex++;
                if(bypassCandidateIndex>=bypassCandidates.size()){
                    if(!validationShoulder&&config.shoulder()&&blockageDecision.tryOffroad()){validationShoulder=true;bypassCandidateIndex=0;bypassStatus="considering_shoulder";}
                    else{bypassJob=null;bypassCandidates=List.of();}
                }
                return;
            }
        }
        if(bypassTileCursor<candidate.route().tiles.size()){bypassStatus="validating_road";return;}
        for(var shape:parkedShapes)if(!TrafficFootprint.pathClear(candidate.route(),0,candidate.route().length,shape(vehicle),shape)){
            bypassJob=null;bypassCandidates=List.of();bypassStatus="candidate_vehicle_clearance_lost";return;
        }
        var owner=new TrafficPassReservations.Owner(vehicleId,commandId);
        var request=new TrafficPassReservations.Request(owner,now/1e9-blockageDecision.waited(),candidate.corridor(),true);
        var grants=passReservations.resolve(List.of(request),now/1e9);
        if(grants.isEmpty()){bypassStatus="yielding_reserved_corridor";return;}
        var grant=grants.getFirst();if(!passReservations.enter(grant,now/1e9)){bypassStatus="reservation_expired";return;}
        passRequest=request;passGrant=grant;bypassCandidate=candidate;bypassUsed=true;bypassOffroad=validationShoulder;driveRoute=candidate.route();
        driver=new ProbeDriver(driveRoute,Math.min(bypassOffroad?6+2*temperament.offroadWillingness():15,config.speed()),wheelbase);
        if(bypassOffroad)carLimits=BypassTerrain.cautious(carLimits);driver.limits(carLimits);control=null;
        bypassJob=null;bypassCandidates=List.of();parkedStop=Double.POSITIVE_INFINITY;parkedBlocker=-1;
        bypassStatus="passing_"+candidate.side()+(bypassOffroad?"_shoulder":"_road");phase("driving");
    }
    private void create(){
        if(!loadedCorridor())throw new IllegalStateException("route_chunks_unloaded_before_spawn");
        String observerProblem=impactObserverProblem();if(!observerProblem.isEmpty())throw new IllegalStateException(observerProblem);
        // No body may appear on an actor that entered after the staged road scan.
        for(int y=(int)Math.floor(config.y()-3);y<=(int)Math.floor(config.y()+3);y++)for(int x=(int)Math.floor(config.x()-3);x<=(int)Math.floor(config.x()+3);x++){
            double dx=Math.max(Math.max(x-config.x(),0),config.x()-(x+1.0)),dy=Math.max(Math.max(y-config.y(),0),config.y()-(y+1.0));
            if(route.laneMode?!ProbeFootprint.touches(x,y,config.x(),config.y(),Math.sin(route.heading()),Math.cos(route.heading()),route.extendedImpact):Math.hypot(dx,dy)>2.25)continue;
            String problem=tileProblem(x,y,config.roadMode(),true);if(!problem.isEmpty())throw new IllegalStateException("spawn_"+problem);
        }
        VehicleScript script=ScriptManager.instance.getVehicle(config.vehicleScript());
        if(script==null||script.getWheelCount()!=4)throw new IllegalStateException("missing_four_wheel_vehicle_script");
        if(route.laneMode&&(script.getExtents().x()>(route.extendedImpact?1.6:1.4)||script.getExtents().z()>(route.extendedImpact?3.9:3.4)))throw new IllegalStateException("car_exceeds_lane_footprint");
        bodyRadius=Math.hypot(script.getExtents().x(),script.getExtents().z())/2;
        if(!Double.isFinite(bodyRadius)||bodyRadius<=0||bodyRadius>2.25)throw new IllegalStateException("car_exceeds_validated_corridor_radius");
        double minWheel=Double.POSITIVE_INFINITY,maxWheel=Double.NEGATIVE_INFINITY;
        for(int i=0;i<script.getWheelCount();i++){double z=script.getWheel(i).getOffset().z();minWheel=Math.min(minWheel,z);maxWheel=Math.max(maxWheel,z);}
        wheelbase=maxWheel-minWheel;driver=new ProbeDriver(route,config.speed(),wheelbase,config.stops());
        script.toBullet();nativeBefore=Bullet.getVehicleCount();
        IsoGridSquare square=ServerMap.instance.getGridSquare((int)Math.floor(config.x()),(int)Math.floor(config.y()),0);
        vehicle=new BaseVehicle(IsoWorld.instance.currentCell);vehicle.setScriptName(script.getFullName());vehicle.setScript();
        vehicle.setX((float)config.x());vehicle.setY((float)config.y());vehicle.setZ(0);
        vehicle.savedRot.rotationY((float)Math.toRadians(config.yaw()));vehicle.jniTransform.setRotation(vehicle.savedRot);
        vehicle.setPreviouslyEntered(true);vehicle.setPreviouslyMoved(true);
        vehicle.getModData().rawset("LofersVehicleProbe",epoch);
        if(!IsoChunk.doSpawnedVehiclesInInvalidPosition(vehicle))throw new IllegalStateException("engine_rejected_spawn_position");
        vehicle.setSquare(square);vehicle.chunk=square.chunk;vehicle.chunk.vehicles.add(vehicle);
        worldAdded=true;vehicle.addToWorld();vehicleId=vehicle.getId();
        temperament=TrafficTemperament.forResident(commandId);blockage=new TrafficBlockage(commandId,temperament);
        if(vehicleId<0||vehicle.getController()==null)throw new IllegalStateException("vehicle_creation_incomplete");
        vehicle.setNetPlayerAuthorization(BaseVehicle.Authorization.Server,-1);
        // Constructor does not add server bodies. Coordinates match the native
        // client's constructor contract; physical height was computed by it.
        Bullet.addVehicle(vehicleId,vehicle.getX(),vehicle.getY(),vehicle.jniTransform.origin.y,
            vehicle.savedRot.x,vehicle.savedRot.y,vehicle.savedRot.z,vehicle.savedRot.w,script.getFullName());nativeBody=true;
        vehicle.repair();vehicle.setGeneralPartCondition(1.3f,10);vehicle.setHotwired(true);
        if(config.driverModel()) {
            VehiclePart driverPart=vehicle.getPartById("LofersDriver");
            if(driverPart==null)throw new IllegalStateException("missing_original_driver_part");
            driverPart.setModelVisible("Seated",true);
        }
        VehiclePart tank=vehicle.getPartById("GasTank");if(tank!=null)tank.setContainerContentAmount(20);
        vehicle.engineDoRunning();
        nativeMass=vehicle.getMass();Bullet.setVehicleMass(vehicleId,nativeMass);
        vehicle.updateBulletStats();Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
        float[] nativeState=new float[27];
        if(Bullet.getOwnVehiclePhysics(vehicleId,nativeState)!=0||Bullet.getVehicleCount()!=nativeBefore+1)throw new IllegalStateException("native_body_registration_failed");
        ProbeCrashFeedback.register(vehicle);
        vehicle.setPhysicsActive(true);vehicle.updateFlags=(short)(vehicle.updateFlags|2|8192);
        bodyStarted=System.nanoTime();lastControlAt=bodyStarted;physicsFrameStart=WorldSimulation.instance.getBulletFrameNo();lastX=vehicle.getX();lastY=vehicle.getY();lastZ=vehicle.jniTransform.origin.y;
        System.out.println("[LofersVehicleProbe] native_body_registered id="+vehicleId+" count="+Bullet.getVehicleCount());
    }
    private void fail(String reason){
        error=reason.length()>220?reason.substring(0,220):reason;
        cleanup();phase("failed");
    }
    private void cleanup(){
        long begin=System.nanoTime();try{cleanupInternal();}finally{coldCost("cleanup",begin);}
    }
    private void cleanupInternal(){
        if(vehicle==null){releaseTerrain();return;}
        if(vehicle.soundHornOn)vehicle.onHornStop();
        ProbeCrashFeedback.unregister(vehicle);
        try{if(nativeBody){Bullet.controlVehicle(vehicleId,0,100,0);Bullet.setVehicleActive(vehicle,false);Bullet.removeVehicle(vehicleId);nativeBody=false;}}
        catch(Throwable t){error="native_cleanup_failed:"+t.getClass().getSimpleName();}
        try{
            if(worldAdded&&!vehicle.hasPassenger())vehicle.permanentlyRemove();
            else if(worldAdded)throw new IllegalStateException("occupied_probe_car_left_in_world");
            else if(vehicle.chunk!=null)vehicle.chunk.vehicles.remove(vehicle);
            if(vehicleId>=0&&VehicleManager.instance.getVehicleByID((short)vehicleId)==vehicle)VehicleManager.instance.unregisterVehicle(vehicle);
            if(passGrant!=null&&vehicle.isRemovedFromWorld()){passReservations.release(passGrant,true,System.nanoTime()/1e9);passGrant=null;passRequest=null;}
            nativeAfter=Bullet.getVehicleCount();
            if(nativeBefore>=0&&nativeAfter!=nativeBefore)error="native_body_count_not_restored";
        }catch(Throwable t){error="world_cleanup_failed:"+t.getClass().getSimpleName();}
        vehicle=null;worldAdded=false;releaseTerrain();
    }
    private void releaseTerrain(){
        ProbeRockCollider.deactivate();
        if(!nativeTerrain||nativeBody)return;
        try{
            Bullet.deactivateChunkMap(0);terrainRemoved++;
            nativeTerrain=false;
        }
        catch(Throwable t){error="terrain_cleanup_failed:"+t.getClass().getSimpleName();}
    }
    private void publish(){
        Map<String,String> s=new LinkedHashMap<>();
        var impacts=ProbeCrashFeedback.snapshot();
        s.put("native_crashes",Long.toString(impacts.crashes()));s.put("crash_sound_requests",Long.toString(impacts.soundRequests()));
        s.put("crash_feedback_failures",Long.toString(impacts.failures()));s.put("crash_feedback_error",impacts.failure());
        s.put("last_crash_amount",Float.toString(impacts.amount()));s.put("last_crash_front",Boolean.toString(impacts.front()));
        s.put("last_crash_sound",impacts.sound());s.put("last_crash_x",Float.toString(impacts.x()));s.put("last_crash_y",Float.toString(impacts.y()));
        s.put("impact_test",Boolean.toString(config.impact()!=null));
        s.put("world",world);s.put("server_epoch",epoch);s.put("phase",phase);s.put("error",error);s.put("finish_reason",finishReason);
        s.put("command_id",Long.toString(commandId));s.put("body_registered",Boolean.toString(nativeBody));s.put("vehicle_id",Integer.toString(vehicleId));
        s.put("world_present",Boolean.toString(vehicle!=null&&worldPresent));
        s.put("registry_present",Boolean.toString(vehicle!=null&&registryPresent));s.put("chunk_present",Boolean.toString(vehicle!=null&&chunkPresent));
        s.put("parked_stop_progress",Double.toString(parkedStop));s.put("parked_blocker",Integer.toString(parkedBlocker));
        s.put("obstacle_wait_seconds",Double.toString(waitingNanos/1_000_000_000.0));s.put("bypass_requests",Integer.toString(bypassRequests));
        s.put("bypass_execution",bypassStatus);s.put("bypasses_completed",Integer.toString(bypassesCompleted));
        s.put("bypass_terrain",bypassOffroad?"shoulder":"road");
        s.put("bypass_road_tiles_checked",Integer.toString(bypassTileCursor));s.put("bypass_worker_ms",Double.toString(io.bypassPlanningNanos/1e6));
        s.put("bypass_reservation_active",Boolean.toString(passGrant!=null));s.put("drive_route_length",Double.toString(driveRoute.length));
        s.put("driver_seed",Long.toString(commandId));
        s.put("horn_on",Boolean.toString(vehicle!=null&&vehicle.soundHornOn));
        if(blockageDecision!=null){s.put("blockage_state",blockageDecision.state());s.put("driver_offroad_selected",Boolean.toString(blockageDecision.tryOffroad()));}
        s.put("physics_offset_x",Float.toString(WorldSimulation.instance.offsetX));s.put("physics_offset_y",Float.toString(WorldSimulation.instance.offsetY));
        if(temperament!=null){s.put("driver_patience_seconds",Double.toString(temperament.patienceSeconds()));s.put("driver_horn_chance",Double.toString(temperament.hornChance()));s.put("driver_offroad_chance",Double.toString(temperament.offroadWillingness()));}
        s.put("x",Double.toString(lastX));s.put("y",Double.toString(lastY));s.put("physics_z",Double.toString(lastZ));
        s.put("speed_kmh",Double.toString(speed));s.put("max_speed_kmh",Double.toString(maxSpeed));s.put("distance",Double.toString(distance));s.put("stop_distance",Double.toString(stopDistance));
        s.put("route_mode",config.roadMode()?"validated_waypoints":"legacy_straight");s.put("route_length",Double.toString(route.length));
        s.put("driver_model",Boolean.toString(config.driverModel()));
        s.put("street_limit_kmh",Double.toString(config.speed()));
        if(carLimits!=null){
            s.put("vehicle_mass",Double.toString(carLimits.mass()));s.put("vehicle_max_speed_kmh",Double.toString(carLimits.maxSpeed()));
            s.put("vehicle_drive_force_limit",Double.toString(carLimits.driveForce()));
            s.put("vehicle_steering_limit",Double.toString(carLimits.steeringAngle()));s.put("vehicle_steering_rate",Double.toString(carLimits.steeringRate()));
            s.put("planned_braking_mps2",Double.toString(carLimits.brakingDeceleration()));s.put("planned_lateral_mps2",Double.toString(carLimits.lateralAcceleration()));
        }
        if(applied!=null){
            s.put("applied_engine_force",Float.toString(applied.force()));s.put("applied_brake_force",Float.toString(applied.brake()));
            s.put("native_available_engine_force",Double.toString(applied.availableForce()));s.put("native_available_brake_force",Double.toString(applied.availableBrake()));
        }
        s.put("lane_mode",Boolean.toString(route.laneMode));
        s.put("path_kind",route.trajectory==null?"polyline":"cubic_bezier");
        s.put("safety_status",safetyStatus);
        s.put("safety_scan_ms",Double.toString(safetyScanNanos/1_000_000.0));
        s.put("safety_scan_max_ms",Double.toString(safetyScanMaxNanos/1_000_000.0));
        s.put("safety_scan_over_1ms",Long.toString(safetyScanOver1ms));
        s.put("safety_scan_tiles",Integer.toString(safetyWork.tiles()));s.put("safety_scan_vehicles",Integer.toString(safetyWork.vehicles()));
        s.put("forecast_contact_seconds",Double.toString(forecastContact));s.put("forecast_blocker",Integer.toString(forecastBlocker));
        if(driver!=null){
            s.put("signed_deviation",Double.toString(driver.signedDeviation()));
            s.put("path_heading_error_degrees",Double.toString(Math.toDegrees(driver.pathHeadingError())));
            s.put("path_curvature",Double.toString(driver.pathCurvature()));
            s.put("steering_lookahead",Double.toString(driver.lookaheadDistance()));
            s.put("speed_preview",Double.toString(driver.previewDistance()));
            s.put("predicted_deviation",Double.toString(driver.predictedDeviation()));
            s.put("predicted_heading_error_degrees",Double.toString(Math.toDegrees(driver.predictedHeadingError())));
        }
        s.put("completed_stops",Integer.toString(driver==null?0:driver.completedStops()));
        s.put("stop_hold_seconds",Double.toString(driver==null?0:driver.stopHoldSeconds()));
        s.put("route_points",Integer.toString(route.points.size()));s.put("road_tiles_validated",Integer.toString(validationCursor));
        s.put("collision_chunks",Integer.toString(route.chunks.size()));s.put("displacement",Double.toString(displacement));s.put("goal_distance",Double.toString(goalDistance));
        s.put("heading_degrees",Double.toString(heading));s.put("max_tilt_degrees",Double.toString(maxTiltDegrees));s.put("rock_mesh_uploads",Long.toString(ProbeRockCollider.uploads()));
        s.put("loaded_body_radius",Double.toString(bodyRadius));s.put("loaded_wheelbase",Double.toString(wheelbase));
        if(control!=null){s.put("route_progress",Double.toString(control.progress()));s.put("cross_track",Double.toString(control.crossTrack()));
            s.put("engine_force",Double.toString(control.engineForce()));s.put("brake_force",Double.toString(control.brake()));
            s.put("steering_radians",Double.toString(control.steering()));s.put("target_speed_kmh",Double.toString(control.targetSpeed()));
            s.put("target_x",Double.toString(control.targetX()));s.put("target_y",Double.toString(control.targetY()));}
        s.put("owner",vehicle==null?"none":vehicle.getAuthorizationDescription());s.put("world_ticks",Long.toString(ticks));
        s.put("native_count_before",Integer.toString(nativeBefore));s.put("native_count_after",Integer.toString(nativeAfter));
        s.put("position_publications",Long.toString(publications));s.put("packets_measured","false");
        s.put("physics_frames",Long.toString(physicsFrames));
        s.put("max_tick_ms",Double.toString(stepMaxNanos/1_000_000.0));s.put("updated_unix_ms",Long.toString(System.currentTimeMillis()));
        s.put("warm_tick_samples",Long.toString(warmTiming.count));s.put("warm_tick_mean_ms",Double.toString(warmTiming.count==0?0:warmTiming.total/(1_000_000.0*warmTiming.count)));
        s.put("warm_tick_p50_ms_upper",Double.toString(warmTiming.percentile(0.5)));s.put("warm_tick_p95_ms_upper",Double.toString(warmTiming.percentile(0.95)));
        s.put("warm_tick_p99_ms_upper",Double.toString(warmTiming.percentile(0.99)));s.put("warm_tick_max_ms",Double.toString(warmTiming.max/1_000_000.0));
        s.put("warm_tick_over_2ms",Long.toString(warmTiming.over2));s.put("warm_tick_over_5ms",Long.toString(warmTiming.over5));
        s.put("cold_tick_samples",Long.toString(coldTiming.count));s.put("cold_tick_max_ms",Double.toString(coldTiming.max/1_000_000.0));
        for(var timing:coldOperations.entrySet())s.put("cold_"+timing.getKey()+"_max_ms",Double.toString(timing.getValue()/1_000_000.0));
        s.put("ready_server",Boolean.toString(readyServer));s.put("ready_world_name",Boolean.toString(readyName));
        s.put("ready_cell",Boolean.toString(readyCell));s.put("ready_meta",Boolean.toString(readyMeta));s.put("ready_server_map",Boolean.toString(readyMap));
        s.put("native_world_created",Boolean.toString(nativeWorldReady));
        s.put("native_library_initialized",Boolean.toString(Bullet.cmdBuf!=null));
        s.put("native_terrain_active",Boolean.toString(nativeTerrain));
        s.put("native_terrain_min_chunk_x",Integer.toString(terrainMinX));s.put("native_terrain_min_chunk_y",Integer.toString(terrainMinY));
        s.put("native_terrain_mode","owned_collision_chunk_map");s.put("native_terrain_maps_live",nativeTerrain?"1":"0");
        s.put("native_terrain_map_width",Integer.toString(terrainWidth));
        s.put("native_terrain_cells_live","0");
        s.put("native_terrain_maps_created",Integer.toString(terrainCreated));s.put("native_terrain_maps_removed",Integer.toString(terrainRemoved));
        s.put("native_terrain_cells_created","0");s.put("native_terrain_cells_removed","0");
        s.put("client_validation","not_observed");io.status.set(Collections.unmodifiableMap(s));
    }
}
