package net.lofers.scenario;

import java.util.*;
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
    private final ProbeControl.Config config;
    private final ProbeControl io;
    private final String world,epoch;
    private BaseVehicle vehicle;
    private boolean nativeBody,worldAdded;
    private int nativeBefore=-1,nativeAfter=-1,vehicleId=-1,chunkCursor;
    private long commandId,ticks,publications,started,bodyStarted,phaseAt,nextStatus,physicsFrameStart,physicsFrames;
    private String phase="waiting_for_world",error="",finishReason="";
    private final ArrayList<int[]> physicsChunks=new ArrayList<>();
    private double distance,maxSpeed,lastX,lastY,lastZ,speed,stopDistance;
    private long stepMaxNanos;
    private boolean readyServer,readyName,readyCell,readyMeta,readyMap,nativeWorldReady;
    ServerVehicleProbe(ProbeControl.Config config,String world,String epoch)throws Exception {
        this.config=config;this.world=world;this.epoch=epoch;io=new ProbeControl(config,epoch);
        publish();Thread thread=new Thread(io,"lofers-vehicle-probe-files");thread.setDaemon(true);thread.start();
    }
    private void phase(String next){phase=next;phaseAt=System.nanoTime();System.out.println("[LofersVehicleProbe] "+next+" command="+commandId+(error.isEmpty()?"":" error="+error));}
    void tick() {
        long begin=System.nanoTime();ticks++;
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
                if(command.action().equals("stop")){finishReason="operator_stop";if(vehicle!=null)phase("braking");else phase("stopped");}
                else if(vehicle==null&&!Set.of("loading","preparing_physics").contains(phase)) {
                    error="";finishReason="";distance=0;maxSpeed=0;publications=0;physicsChunks.clear();chunkCursor=0;
                    nativeBefore=-1;nativeAfter=-1;started=System.nanoTime();phase("loading");
                }
            }
            boolean ready=readyCell&&readyMeta&&readyMap;
            if(!ready){if(vehicle!=null)fail("world_became_unavailable");return;}
            physicsFrames=WorldSimulation.instance.getBulletFrameNo()-physicsFrameStart;
            if(phase.equals("waiting_for_world"))phase("armed");
            if(Set.of("loading","preparing_physics","settling","driving","braking","stopped_visible").contains(phase)) {
                // cellMap is allocated after ServerMap.grid is assigned: never call the
                // native method's pre-initialization sleep loop on the game thread.
                ServerMap.instance.characterIn((int)Math.floor(config.x()/8),(int)Math.floor(config.y()/8),13);
            }
            if(phase.equals("loading")) {
                if(System.nanoTime()-started>120_000_000_000L){fail("loaded_area_timeout");return;}
                if(!WorldSimulation.instance.created){
                    // An empty dedicated world may never initialize Bullet through
                    // the client-only vehicle constructor. Explicit operator start
                    // initializes it after the map exists; never during premain.
                    if(Bullet.cmdBuf==null)Bullet.init();
                    WorldSimulation.instance.create();nativeWorldReady=WorldSimulation.instance.created;
                    if(!nativeWorldReady||!Bullet.isWorldInit())throw new IllegalStateException("native_world_initialization_failed");
                    System.out.println("[LofersVehicleProbe] native_world_initialized");
                }
                if(!loadedCorridor())return;
                validateCorridor();prepareChunks();phase("preparing_physics");
            }
            if(phase.equals("preparing_physics")) {
                if(chunkCursor<physicsChunks.size()) {
                    int[] xy=physicsChunks.get(chunkCursor);IsoChunk chunk=ServerMap.instance.getChunk(xy[0],xy[1]);
                    if(chunk==null||!chunk.loaded){fail("physics_chunk_unloaded");return;}
                    chunk.updatePhysicsForLevel(0);chunkCursor++;return;
                }
                create();phase("settling");
            }
            if(vehicle==null)return;
            if(!vehicle.isNetPlayerAuthorization(BaseVehicle.Authorization.Server)||vehicle.getNetPlayerId()!=-1){fail("native_authority_changed");return;}
            if(vehicle.hasPassenger()){fail("unexpected_passenger");return;}
            if(!loadedCorridor()){fail("road_chunks_unloaded");return;}
            lastX=vehicle.getX();lastY=vehicle.getY();lastZ=vehicle.jniTransform.origin.y;
            speed=Math.hypot(vehicle.jniLinearVelocity.x,vehicle.jniLinearVelocity.z)*3.6;
            distance=Math.hypot(lastX-config.x(),lastY-config.y());maxSpeed=Math.max(maxSpeed,speed);
            if(!Double.isFinite(speed)||!Double.isFinite(distance)||!Double.isFinite(lastZ)){fail("nonfinite_physics");return;}
            if(lastZ<-0.5||lastZ>4){fail("invalid_physics_height");return;}
            if(speed>8||distance>config.distance()+5){fail("motion_limit_exceeded");return;}
            long now=System.nanoTime();
            if(now-bodyStarted>30_000_000_000L){fail("absolute_deadline");return;}
            if(phase.equals("settling")&&now-phaseAt>2_000_000_000L)phase("driving");
            if(phase.equals("driving")&&(distance>=config.distance()||now-phaseAt>15_000_000_000L)){
                finishReason=distance>=config.distance()?"distance_reached":"drive_deadline";phase("braking");
            }
            float force=phase.equals("driving")&&speed<config.speed()?800:0;
            float brake=phase.equals("driving")?(speed>config.speed()+0.5?20:0):80;
            // Direct native API is intentional: BaseVehicle's server branch skips it.
            Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
            Bullet.controlVehicle(vehicleId,force,brake,0);
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
            stepMaxNanos=Math.max(stepMaxNanos,System.nanoTime()-begin);
            if(System.nanoTime()>nextStatus){publish();nextStatus=System.nanoTime()+250_000_000L;}
        }
    }
    private boolean loadedCorridor(){
        for(int[] c:chunks()) {IsoChunk chunk=ServerMap.instance.getChunk(c[0],c[1]);if(chunk==null||!chunk.loaded)return false;}return true;
    }
    private ArrayList<int[]> chunks(){
        double yaw=Math.toRadians(config.yaw()),endX=config.x()+Math.sin(yaw)*(config.distance()+5),endY=config.y()+Math.cos(yaw)*(config.distance()+5);
        int minX=(int)Math.floor((Math.min(config.x(),endX)-9)/8),maxX=(int)Math.floor((Math.max(config.x(),endX)+9)/8);
        int minY=(int)Math.floor((Math.min(config.y(),endY)-9)/8),maxY=(int)Math.floor((Math.max(config.y(),endY)+9)/8);
        ArrayList<int[]> result=new ArrayList<>();for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++)result.add(new int[]{x,y});return result;
    }
    private void validateCorridor(){
        double yaw=Math.toRadians(config.yaw()),dx=Math.sin(yaw),dy=Math.cos(yaw);
        for(int along=-3;along<=config.distance()+5;along++)for(int side=-2;side<=2;side++){
            int x=(int)Math.floor(config.x()+dx*along+dy*side),y=(int)Math.floor(config.y()+dy*along-dx*side);
            IsoGridSquare square=ServerMap.instance.getGridSquare(x,y,0);
            if(square==null||square.getFloor()==null||!square.TreatAsSolidFloor()||!square.isOutside()||!square.isFree(false))
                throw new IllegalStateException("road_not_clear_at_"+x+"_"+y);
        }
    }
    private void prepareChunks(){physicsChunks.clear();physicsChunks.addAll(chunks());}
    private void create(){
        validateCorridor();
        VehicleScript script=ScriptManager.instance.getVehicle("Base.SmallCar");
        if(script==null||script.getWheelCount()!=4)throw new IllegalStateException("missing_four_wheel_SmallCar_script");
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
        vehicle.repair();vehicle.setGeneralPartCondition(1.3f,10);vehicle.setHotwired(true);
        VehiclePart tank=vehicle.getPartById("GasTank");if(tank!=null)tank.setContainerContentAmount(20);
        vehicle.engineDoRunning();vehicle.setNetPlayerAuthorization(BaseVehicle.Authorization.Server,-1);
        // Constructor does not add server bodies. Coordinates match the native
        // client's constructor contract; physical height was computed by it.
        Bullet.addVehicle(vehicleId,vehicle.getX(),vehicle.getY(),vehicle.jniTransform.origin.y,
            vehicle.savedRot.x,vehicle.savedRot.y,vehicle.savedRot.z,vehicle.savedRot.w,script.getFullName());nativeBody=true;
        vehicle.updateBulletStats();Bullet.setVehicleStatic(vehicle,false);Bullet.setVehicleActive(vehicle,true);
        float[] nativeState=new float[27];
        if(Bullet.getOwnVehiclePhysics(vehicleId,nativeState)!=0||Bullet.getVehicleCount()!=nativeBefore+1)throw new IllegalStateException("native_body_registration_failed");
        vehicle.setPhysicsActive(true);vehicle.updateFlags=(short)(vehicle.updateFlags|2|8192);
        bodyStarted=System.nanoTime();physicsFrameStart=WorldSimulation.instance.getBulletFrameNo();lastX=vehicle.getX();lastY=vehicle.getY();lastZ=vehicle.jniTransform.origin.y;
        System.out.println("[LofersVehicleProbe] native_body_registered id="+vehicleId+" count="+Bullet.getVehicleCount());
    }
    private void fail(String reason){
        error=reason.length()>220?reason.substring(0,220):reason;
        cleanup();phase("failed");
    }
    private void cleanup(){
        if(vehicle==null)return;
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
        vehicle=null;worldAdded=false;
    }
    private void publish(){
        Map<String,String> s=new LinkedHashMap<>();
        s.put("world",world);s.put("server_epoch",epoch);s.put("phase",phase);s.put("error",error);s.put("finish_reason",finishReason);
        s.put("command_id",Long.toString(commandId));s.put("body_registered",Boolean.toString(nativeBody));s.put("vehicle_id",Integer.toString(vehicleId));
        s.put("x",Double.toString(lastX));s.put("y",Double.toString(lastY));s.put("physics_z",Double.toString(lastZ));
        s.put("speed_kmh",Double.toString(speed));s.put("max_speed_kmh",Double.toString(maxSpeed));s.put("distance",Double.toString(distance));s.put("stop_distance",Double.toString(stopDistance));
        s.put("owner",vehicle==null?"none":vehicle.getAuthorizationDescription());s.put("world_ticks",Long.toString(ticks));
        s.put("native_count_before",Integer.toString(nativeBefore));s.put("native_count_after",Integer.toString(nativeAfter));
        s.put("position_publications",Long.toString(publications));s.put("packets_measured","false");
        s.put("physics_frames",Long.toString(physicsFrames));
        s.put("max_tick_ms",Double.toString(stepMaxNanos/1_000_000.0));s.put("updated_unix_ms",Long.toString(System.currentTimeMillis()));
        s.put("ready_server",Boolean.toString(readyServer));s.put("ready_world_name",Boolean.toString(readyName));
        s.put("ready_cell",Boolean.toString(readyCell));s.put("ready_meta",Boolean.toString(readyMeta));s.put("ready_server_map",Boolean.toString(readyMap));
        s.put("native_world_created",Boolean.toString(nativeWorldReady));
        s.put("native_library_initialized",Boolean.toString(Bullet.cmdBuf!=null));
        s.put("client_validation","not_observed");io.status.set(Collections.unmodifiableMap(s));
    }
}
