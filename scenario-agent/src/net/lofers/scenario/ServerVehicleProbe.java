package net.lofers.scenario;

import java.util.*;
import org.joml.Vector3f;
import zombie.core.physics.Bullet;
import zombie.core.physics.WorldSimulation;
import zombie.iso.*;
import zombie.network.GameServer;
import zombie.network.ServerMap;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.*;

/** One opt-in empty car in a disposable world. Physics and game state stay on the game thread. */
final class ServerVehicleProbe {
    private static final Set<String> PREPARING=Set.of("loading","validating_road","preparing_terrain","preparing_physics");
    private static final Set<String> ACTIVE=Set.of("loading","validating_road","preparing_terrain","preparing_physics","settling","driving","braking","stopped_visible");
    private final ProbeControl.Config config;
    private final ProbeControl io;
    private final String world,epoch;
    private final ProbeRoute route;
    private final Vector3f forward=new Vector3f();
    private ProbeDriver driver;
    private ProbeDriver.Output control;
    private BaseVehicle vehicle;
    private boolean nativeBody,worldAdded,nativeTerrain,ownsNativeWorld;
    private int terrainMinX,terrainMinY;
    private int terrainCreated,terrainRemoved;
    private final ArrayList<int[]> nativeCells=new ArrayList<>();
    private int nativeBefore=-1,nativeAfter=-1,vehicleId=-1,chunkCursor,validationCursor,cellCursor;
    private long commandId,ticks,publications,started,bodyStarted,phaseAt,nextStatus,physicsFrameStart,physicsFrames;
    private String phase="waiting_for_world",error="",finishReason="";
    private double distance,displacement,maxSpeed,lastX,lastY,lastZ,speed,stopDistance,goalDistance,heading,bodyRadius,wheelbase;
    private long lastControlAt,coldNanosThisTick;
    private ProbeTiming warmTiming=new ProbeTiming(),coldTiming=new ProbeTiming();
    private final Map<String,Long> coldOperations=new LinkedHashMap<>();
    private long stepMaxNanos;
    private boolean readyServer,readyName,readyCell,readyMeta,readyMap,nativeWorldReady;
    ServerVehicleProbe(ProbeControl.Config config,String world,String epoch)throws Exception {
        this.config=config;route=config.route();this.world=world;this.epoch=epoch;io=new ProbeControl(config,epoch);
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
                    error="";finishReason="";distance=0;maxSpeed=0;publications=0;physicsFrames=0;chunkCursor=0;validationCursor=0;cellCursor=0;control=null;
                    warmTiming=new ProbeTiming();coldTiming=new ProbeTiming();coldOperations.clear();stepMaxNanos=0;
                    stopDistance=0;displacement=0;goalDistance=route.length;lastX=config.x();lastY=config.y();lastZ=0;
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
                ServerMap.instance.characterIn(route.centerChunkX,route.centerChunkY,route.requestWidth());
            }
            if(phase.equals("loading")) {
                if(System.nanoTime()-started>120_000_000_000L){fail("loaded_area_timeout");return;}
                if(!loadedCorridor())return;
                phase("validating_road");
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
                    WorldSimulation.instance.create();nativeWorldReady=WorldSimulation.instance.created;
                    if(!nativeWorldReady||!Bullet.isWorldInit())throw new IllegalStateException("native_world_initialization_failed");
                    ownsNativeWorld=true;
                    System.out.println("[LofersVehicleProbe] native_world_initialized");
                }
                // Pinned headless native server mode ignores client chunk maps.
                // Its ServerCell owns 5x5 eight-tile chunks (not Java's 64-tile
                // ServerMap cells). Own a fresh native world before using this
                // lifecycle, so cleanup cannot remove another caller's cells.
                if(!ownsNativeWorld||Bullet.getVehicleCount()!=0||!nativeCells.isEmpty())
                    throw new IllegalStateException("probe_requires_owned_empty_native_world");
                terrainCreated=0;terrainRemoved=0;
                terrainMinX=route.chunks.stream().mapToInt(ProbeRoute.Tile::x).min().orElseThrow();
                terrainMinY=route.chunks.stream().mapToInt(ProbeRoute.Tile::y).min().orElseThrow();
                }finally{coldCost("native_world",coldStart);}
                phase("preparing_terrain");
            }
            if(phase.equals("preparing_terrain")){
                if(cellCursor<route.cells.size()){
                    ProbeRoute.Tile cell=route.cells.get(cellCursor);long coldStart=System.nanoTime();
                    try{Bullet.createServerCell(cell.x(),cell.y());nativeCells.add(new int[]{cell.x(),cell.y()});nativeTerrain=true;terrainCreated++;cellCursor++;}
                    finally{coldCost("terrain_cell",coldStart);}return;
                }
                System.out.println("[LofersVehicleProbe] native_server_cells_created count="+terrainCreated);phase("preparing_physics");
            }
            if(phase.equals("preparing_physics")) {
                if(chunkCursor<route.chunks.size()) {
                    ProbeRoute.Tile xy=route.chunks.get(chunkCursor);IsoChunk chunk=ServerMap.instance.getChunk(xy.x(),xy.y());
                    if(chunk==null||!chunk.loaded){fail("physics_chunk_unloaded");return;}
                    long coldStart=System.nanoTime();try{chunk.updatePhysicsForLevel(0);chunkCursor++;}finally{coldCost("chunk_upload",coldStart);}return;
                }
                long coldStart=System.nanoTime();try{create();}finally{coldCost("vehicle_create",coldStart);}phase("settling");
            }
            if(vehicle==null)return;
            if(!vehicle.isNetPlayerAuthorization(BaseVehicle.Authorization.Server)||vehicle.getNetPlayerId()!=-1){fail("native_authority_changed");return;}
            if(vehicle.hasPassenger()){fail("unexpected_passenger");return;}
            if(!loadedCorridor()){fail("road_chunks_unloaded");return;}
            double previousX=lastX,previousY=lastY;lastX=vehicle.getX();lastY=vehicle.getY();lastZ=vehicle.jniTransform.origin.y;
            speed=Math.hypot(vehicle.jniLinearVelocity.x,vehicle.jniLinearVelocity.z)*3.6;
            distance+=Math.hypot(lastX-previousX,lastY-previousY);displacement=Math.hypot(lastX-config.x(),lastY-config.y());maxSpeed=Math.max(maxSpeed,speed);
            vehicle.getForwardVector(forward);heading=Math.toDegrees(Math.atan2(forward.x,forward.z));
            goalDistance=Math.hypot(lastX-route.points.getLast().x(),lastY-route.points.getLast().y());
            if(!Double.isFinite(speed)||!Double.isFinite(distance)||!Double.isFinite(lastZ)){fail("nonfinite_physics");return;}
            if(lastZ<-0.5||lastZ>4){fail("invalid_physics_height");return;}
            if(speed>8||distance>config.distance()+5){fail("motion_limit_exceeded");return;}
            long now=System.nanoTime();
            if(now-bodyStarted>config.deadlineSeconds()*1_000_000_000L){fail("absolute_deadline");return;}
            if(phase.equals("settling")&&now-phaseAt>2_000_000_000L)phase("driving");
            double delta=lastControlAt==0?0.1:(now-lastControlAt)/1_000_000_000.0;lastControlAt=now;
            float force=0,brake=80,steering=0;
            if(phase.equals("driving")){
                String danger=config.roadMode()?warmSafety(System.nanoTime()+1_000_000L):"";
                control=driver.step(lastX,lastY,forward.x,forward.z,speed,delta,danger);
                if(!control.stopReason().isEmpty()){error=control.stopReason();finishReason=error;phase("braking");}
                else if(control.arrived()){finishReason="route_arrived";phase("braking");}
                else if(now-bodyStarted>(config.deadlineSeconds()-8)*1_000_000_000L){error="drive_deadline";finishReason=error;phase("braking");}
                force=(float)control.engineForce();brake=(float)control.brake();steering=(float)control.steering();
            }
            if(!phase.equals("driving")){force=0;brake=100;steering=0;}
            // Direct native API is intentional: BaseVehicle's server branch skips it.
            Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
            Bullet.controlVehicle(vehicleId,force,brake,steering);vehicle.setCurrentSteering(steering);
            vehicle.getController().engineForce=force;vehicle.getController().brakingForce=brake;
            vehicle.getController().acceleratorOn=force>0;vehicle.getController().brakeOn=brake>0;
            vehicle.throttle=force>0?0.25f:0;vehicle.setBraking(brake>0);
            vehicle.setStoplightsOn(brake>0);vehicle.updateFlags=(short)(vehicle.updateFlags|2);publications++;
            if(phase.equals("braking")){
                if(speed<0.25&&now-phaseAt>1_000_000_000L){stopDistance=distance;phase("stopped_visible");}
                else if(now-phaseAt>5_000_000_000L){fail("brake_deadline");return;}
            }
            if(phase.equals("stopped_visible")&&now-phaseAt>3_000_000_000L){cleanup();phase(error.isEmpty()?"complete":"failed");}
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
    private String tileProblem(int x,int y,boolean requireRoad,boolean actors){
        IsoGridSquare square=ServerMap.instance.getGridSquare(x,y,0);
        if(square==null||square.getFloor()==null||!square.TreatAsSolidFloor()||!square.isOutside()||!square.isFree(false)||square.HasStairs())return "road_not_clear";
        if(requireRoad){
            if(!"Road_06".equals(square.getFloor().getProperties().get("FloorMaterial")))return "not_asphalt_road";
            var objects=square.getObjects();if(objects.size()>32)return "road_object_limit";
            for(int i=0;i<objects.size();i++){
                var properties=objects.get(i).getProperties();String material=properties==null?null:properties.get("FloorMaterial");
                if(material!=null&&material.startsWith("Road_")&&!material.equals("Road_06"))return "sidewalk_or_conflicting_overlay";
            }
            IsoGridSquare west=ServerMap.instance.getGridSquare(x-1,y,0),north=ServerMap.instance.getGridSquare(x,y-1,0);
            if(west==null||north==null||square.isBlockedTo(west)||square.isBlockedTo(north))return "road_wall_edge";
        }
        if(actors){var moving=square.getMovingObjects();if(moving.size()>16)return "road_actor_limit";
            for(IsoMovingObject object:moving)if(object!=vehicle)return "actor_in_vehicle_path";}
        return "";
    }
    private String warmSafety(long deadline){
        // A full current body disk, plus conservative stopping-space samples,
        // must remain asphalt and clear. This checks actual native pose, not
        // merely whether the planned centerline belongs to a broad road polygon.
        for(int y=(int)Math.floor(lastY-3);y<=(int)Math.floor(lastY+3);y++)for(int x=(int)Math.floor(lastX-3);x<=(int)Math.floor(lastX+3);x++){
            double dx=Math.max(Math.max(x-lastX,0),lastX-(x+1.0)),dy=Math.max(Math.max(y-lastY,0),lastY-(y+1.0));
            if(Math.hypot(dx,dy)>2.25)continue;
            if(System.nanoTime()>deadline)return "road_check_budget";
            String problem=tileProblem(x,y,true,true);if(!problem.isEmpty())return problem;
        }
        double stopping=Math.min(4,2+Math.pow(speed/3.6,2)/1.2);
        for(double along=2;along<=stopping;along+=0.5)for(int side=-1;side<=1;side++){
            if(System.nanoTime()>deadline)return "road_check_budget";
            int x=(int)Math.floor(lastX+forward.x*along+forward.z*side),y=(int)Math.floor(lastY+forward.z*along-forward.x*side);
            String problem=tileProblem(x,y,true,true);if(!problem.isEmpty())return problem;
        }
        int cx=(int)Math.floor(lastX/8),cy=(int)Math.floor(lastY/8),seen=0;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            IsoChunk chunk=ServerMap.instance.getChunk(cx+dx,cy+dy);if(chunk==null||!chunk.loaded)return "nearby_chunk_unloaded";
            for(BaseVehicle other:chunk.vehicles){if(other==vehicle)continue;
                if(++seen>64||System.nanoTime()>deadline)return "vehicle_check_budget";
                double ox=other.getX()-lastX,oy=other.getY()-lastY,along=ox*forward.x+oy*forward.z,lateral=Math.abs(ox*forward.z-oy*forward.x);
                if(along> -3&&along<stopping+3&&lateral<3)return "vehicle_in_path";
            }
        }
        return "";
    }
    private void create(){
        if(!loadedCorridor())throw new IllegalStateException("route_chunks_unloaded_before_spawn");
        // No body may appear on an actor that entered after the staged road scan.
        for(int y=(int)Math.floor(config.y()-3);y<=(int)Math.floor(config.y()+3);y++)for(int x=(int)Math.floor(config.x()-3);x<=(int)Math.floor(config.x()+3);x++){
            double dx=Math.max(Math.max(x-config.x(),0),config.x()-(x+1.0)),dy=Math.max(Math.max(y-config.y(),0),config.y()-(y+1.0));
            if(Math.hypot(dx,dy)>2.25)continue;
            String problem=tileProblem(x,y,config.roadMode(),true);if(!problem.isEmpty())throw new IllegalStateException("spawn_"+problem);
        }
        VehicleScript script=ScriptManager.instance.getVehicle(config.driverModel()?"Base.LofersSmallCar":"Base.SmallCar");
        if(script==null||script.getWheelCount()!=4)throw new IllegalStateException("missing_four_wheel_SmallCar_script");
        bodyRadius=Math.hypot(script.getExtents().x(),script.getExtents().z())/2;
        if(!Double.isFinite(bodyRadius)||bodyRadius<=0||bodyRadius>2.25)throw new IllegalStateException("car_exceeds_validated_corridor_radius");
        double minWheel=Double.POSITIVE_INFINITY,maxWheel=Double.NEGATIVE_INFINITY;
        for(int i=0;i<script.getWheelCount();i++){double z=script.getWheel(i).getOffset().z();minWheel=Math.min(minWheel,z);maxWheel=Math.max(maxWheel,z);}
        wheelbase=maxWheel-minWheel;driver=new ProbeDriver(route,config.speed(),wheelbase);
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
        vehicle.updateBulletStats();Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
        float[] nativeState=new float[27];
        if(Bullet.getOwnVehiclePhysics(vehicleId,nativeState)!=0||Bullet.getVehicleCount()!=nativeBefore+1)throw new IllegalStateException("native_body_registration_failed");
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
        try{if(nativeBody){Bullet.controlVehicle(vehicleId,0,100,0);Bullet.setVehicleActive(vehicle,false);Bullet.removeVehicle(vehicleId);nativeBody=false;}}
        catch(Throwable t){error="native_cleanup_failed:"+t.getClass().getSimpleName();}
        try{
            if(worldAdded&&!vehicle.hasPassenger())vehicle.permanentlyRemove();
            else if(worldAdded)throw new IllegalStateException("occupied_probe_car_left_in_world");
            else if(vehicle.chunk!=null)vehicle.chunk.vehicles.remove(vehicle);
            if(vehicleId>=0&&VehicleManager.instance.getVehicleByID((short)vehicleId)==vehicle)VehicleManager.instance.unregisterVehicle(vehicle);
            nativeAfter=Bullet.getVehicleCount();
            if(nativeBefore>=0&&nativeAfter!=nativeBefore)error="native_body_count_not_restored";
        }catch(Throwable t){error="world_cleanup_failed:"+t.getClass().getSimpleName();}
        vehicle=null;worldAdded=false;releaseTerrain();
    }
    private void releaseTerrain(){
        if(!nativeTerrain||nativeBody)return;
        try{
            while(!nativeCells.isEmpty()){
                int[] xy=nativeCells.getLast();Bullet.removeServerCell(xy[0],xy[1]);nativeCells.removeLast();terrainRemoved++;
            }
            nativeTerrain=false;
        }
        catch(Throwable t){error="terrain_cleanup_failed:"+t.getClass().getSimpleName();}
    }
    private void publish(){
        Map<String,String> s=new LinkedHashMap<>();
        s.put("world",world);s.put("server_epoch",epoch);s.put("phase",phase);s.put("error",error);s.put("finish_reason",finishReason);
        s.put("command_id",Long.toString(commandId));s.put("body_registered",Boolean.toString(nativeBody));s.put("vehicle_id",Integer.toString(vehicleId));
        s.put("x",Double.toString(lastX));s.put("y",Double.toString(lastY));s.put("physics_z",Double.toString(lastZ));
        s.put("speed_kmh",Double.toString(speed));s.put("max_speed_kmh",Double.toString(maxSpeed));s.put("distance",Double.toString(distance));s.put("stop_distance",Double.toString(stopDistance));
        s.put("route_mode",config.roadMode()?"validated_waypoints":"legacy_straight");s.put("route_length",Double.toString(route.length));
        s.put("driver_model",Boolean.toString(config.driverModel()));
        s.put("route_points",Integer.toString(route.points.size()));s.put("road_tiles_validated",Integer.toString(validationCursor));
        s.put("collision_chunks",Integer.toString(route.chunks.size()));s.put("displacement",Double.toString(displacement));s.put("goal_distance",Double.toString(goalDistance));
        s.put("heading_degrees",Double.toString(heading));
        s.put("loaded_body_radius",Double.toString(bodyRadius));s.put("loaded_wheelbase",Double.toString(wheelbase));
        if(control!=null){s.put("route_progress",Double.toString(control.progress()));s.put("cross_track",Double.toString(control.crossTrack()));
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
        s.put("native_terrain_mode","server_cells_5x5");s.put("native_terrain_cells_live",Integer.toString(nativeCells.size()));
        s.put("native_terrain_cells_created",Integer.toString(terrainCreated));s.put("native_terrain_cells_removed",Integer.toString(terrainRemoved));
        s.put("client_validation","not_observed");io.status.set(Collections.unmodifiableMap(s));
    }
}
