package net.lofers.scenario;

import java.nio.file.*;
import java.util.*;
import zombie.characters.IsoPlayer;
import zombie.network.*;
import zombie.network.chat.ChatServer;
import zombie.iso.*;
import zombie.pathfind.PolygonalMap2;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import se.krka.kahlua.vm.KahluaTable;

/** Opt-in four-wave, one-session 150-tile reuse trial. Uses the real initialized Actor pool. */
final class WatchedCivilianHarness {
    private static final float VIEW_Y=10060.5f,SPEED=2.6f;
    private final String world,epoch;private final Path directory;
    private NativeCivilianActors actors;private CivilianPool<IsoPlayer> pool;
    private final List<CivilianPool.Token> tokens=new ArrayList<>();
    private final IdentityHashMap<IsoPlayer,CivilianPool.Token> bodies=new IdentityHashMap<>();
    private final Set<IsoPlayer> originals=Collections.newSetFromMap(new IdentityHashMap<>());
    private final CivilianFormation formation;private final int count;private final boolean[] seen;
    private KahluaTable clientConfig;private int retireCursor;
    private final int hunterCount;private final boolean crowd;private WatchedHunters hunters;private boolean cleanupPositioned;private String chaseSummary;private int readyActors,readyHunters;
    private long previousTick;private final ProbeTiming tickIntervals=new ProbeTiming();
    private final List<String> waves=new ArrayList<>();
    private final ProbeTiming work=new ProbeTiming(),movement=new ProbeTiming();
    private ProbeTiming waveWork=new ProbeTiming();
    private long started,phaseAt,last,quietAt,lastPublish,lastSample,tick;private int phase,wave,scan;
    private boolean finished;private String failure="",waiting="";private long waveStart,lastNotice,positioningAt,chasePositioningAt;
    WatchedCivilianHarness(Properties properties,String world,String epoch,boolean server,boolean runtime) {
        if(!server||!runtime||!world.startsWith("AKR_DayOne_Test_")||world.startsWith("AKR_DayOne_Test_Headless_"))throw new IllegalArgumentException("watched_requires_disposable_runtime");
        formation=new CivilianFormation(Integer.parseInt(properties.getProperty("watched.actors","4")));count=formation.count;seen=new boolean[count];
        hunterCount=Integer.parseInt(properties.getProperty("watched.hunters","0"));crowd=hunterCount==128;
        if((hunterCount!=0&&hunterCount!=16&&hunterCount!=128)||(hunterCount>0&&count<32)||(crowd&&count!=64))throw new IllegalArgumentException("watched_hunters");
        this.world=world;this.epoch=epoch;directory=Path.of(properties.getProperty("watched.directory",""));
        if(!directory.isAbsolute()||!directory.normalize().startsWith("/run/lofers"))throw new IllegalArgumentException("watched_directory");
    }
    private void next(){phase++;phaseAt=System.nanoTime();quietAt=0;System.out.println("[CivilianWatch] phase="+phase+" wave="+wave);}
    private void announce(String message) {
        String text="[Civilian pool test] "+message;
        System.out.println("[CivilianWatch] "+text);
        try {ChatServer.getInstance().sendServerAlertMessageToServerChat(text);}
        catch(Exception error){System.err.println("[CivilianWatch] chat unavailable: "+error.getClass().getSimpleName());}
    }
    private void waitFor(String reason,long now) {
        if(!reason.equals(waiting)||now-lastNotice>30_000_000_000L){announce(reason);lastNotice=now;}
        waiting=reason;
    }
    void tick() {
        if(finished)return;long now=System.nanoTime(),begin=now;
        try {
            if(!GameServer.server||!world.equals(GameServer.serverName)||IsoWorld.instance==null||IsoWorld.instance.currentCell==null
                ||ServerMap.instance==null||ServerMap.instance.cellMap==null)return;
            if(started==0){started=phaseAt=now;actors=new NativeCivilianActors(IsoWorld.getWorldVersion(),false,count,crowd?new NativeCivilianActors.StagingPolicy(){public boolean admit(CivilianPool.Token t,NativeCivilianActors.Profile p){return tokens.contains(t)&&stagingOwner()!=null;}public boolean hidden(IsoPlayer b){for(var p:GameServer.Players)if(p.getSquare()==null||Math.hypot(p.getX()-b.getX(),p.getY()-b.getY())<72)return false;return allOffscreen(currentReports(),false);}}:null);pool=new CivilianPool<>(epoch,actors,count);
                clientConfig=LuaManager.platform.newTable();clientConfig.rawset("epoch",epoch);clientConfig.rawset("actor_count",(double)count);clientConfig.rawset("hunter_limit",(double)hunterCount);if(crowd)clientConfig.rawset("stage_points",stagePoints());LuaManager.env.rawset("AKRWatched",clientConfig);}
            if(now-started>1_800_000_000_000L)throw new IllegalStateException("batch_timeout");
            if(phase>=2&&now-phaseAt>240_000_000_000L)throw new IllegalStateException("phase_timeout:"+phase);
            if(previousTick!=0)tickIntervals.add(now-previousTick);previousTick=now;
            clientConfig.rawset("phase",(double)phase);clientConfig.rawset("wave",(double)(wave+1));
            for(int y=9960;y<=10240;y+=40)ServerMap.instance.characterIn(Math.floorDiv(10589,8),Math.floorDiv(y,8),5);
            pool.tick(++tick,now);
            var telemetry=LuaManager.env==null?null:LuaManager.env.rawget("AKRPoolWatch");
            KahluaTable reports=telemetry instanceof KahluaTable table?table:null;
            switch(phase) {
                case 0 -> {
                    // Bounded preflight of each fixed parallel corridor; no terrain edits or fallback teleports.
                    for(int budget=0;budget<16&&scan<count*151;budget++,scan++) {
                        int lane=scan/151,y=(int)Math.floor(formation.startY(lane))+scan%151,x=(int)formation.x(lane);
                        var a=ServerMap.instance.getGridSquare(x,y,0);var b=ServerMap.instance.getGridSquare(x,y+1,0);
                        if(a==null||b==null)return;
                        if(!a.TreatAsSolidFloor()||a.HasStairs()||!a.isFree(false)
                            ||PolygonalMap2.instance.lineClearCollide(x+.5f,y+.5f,x+.5f,y+1.5f,0,null,false,true))
                            throw new IllegalStateException("corridor_blocked:"+x+","+y);
                    }
                    if(scan<count*151)return;
                    for(int[] point:new int[][]{{(int)viewX(),(int)viewY()},{10590,10220}}){var square=ServerMap.instance.getGridSquare(point[0],point[1],0);if(square==null)return;require(square.TreatAsSolidFloor()&&square.isFree(false),"observer_position_blocked:"+point[0]+","+point[1]);}
                    if(crowd)require(!PolygonalMap2.instance.lineClearCollide(viewX(),viewY(),10594.5f,viewY(),0,null,false,true)&&LosUtil.lineClear(IsoWorld.instance.currentCell,(int)viewX(),(int)viewY(),0,10594,(int)viewY(),0,false)==LosUtil.TestResults.Clear,"observer_los_or_egress_blocked");
                    if(actors.constructed<count){actors.prewarmOne(10587,9985,0);return;}
                    require(actors.parkedCount()==count,"warmup_incomplete");next();
                }
                case 1 -> {
                    IsoPlayer player=observer();if(player==null)return;
                    protectObserver(player);
                    GameTime.getInstance().setTimeOfDay(12);GameServer.sendWeather();
                    announce("Preparing wave 1/4. Moving you to the viewing point; please stay there during each pass.");
                    GameServer.sendTeleport(player,viewX(),viewY(),0);next();
                }
                case 2 -> {
                    IsoPlayer player=observer();if(player==null)return;
                    if(player.getVehicle()!=null)throw new IllegalStateException("observer_in_vehicle");
                    protectObserver(player);
                    if(!reportsFresh(reports)){waitFor("Waiting for fresh client visibility reports.",now);return;}
                    if(now-phaseAt<12_000_000_000L)return;
                    if(positioningAt==0||(Math.hypot(player.getX()-viewX(),player.getY()-viewY())>3&&now-positioningAt>3_000_000_000L)) {
                        if(positioningAt==0)announce("Positioning the protected observer for wave "+(wave+1)+"/4.");
                        GameServer.sendTeleport(player,viewX(),viewY(),0);positioningAt=now;return;
                    }
                    if(Math.hypot(player.getX()-viewX(),player.getY()-viewY())>3||now-positioningAt<2_000_000_000L)return;
                    if(crowd&&stagingOwner()==null){waitFor("Waiting for the entire 64-civilian / 128-zombie staging area to be loaded and unseen.",now);return;}
                    if(!crowd)for(var viewer:GameServer.Players)for(int lane=0;lane<count;lane++)
                        if(viewer.getSquare()==null||Math.hypot(viewer.getX()-formation.x(lane),viewer.getY()-formation.startY(lane))<72){waitFor("Waiting: a player is too close to the start of the route.",now);return;}
                    waiting="";
                    reserveWave();next();
                }
                case 3 -> {
                    var views=pool.views();
                    for(var token:tokens) {
                        var view=views.stream().filter(v->token.equals(v.token())).findFirst().orElseThrow();
                        require(view.state()!=CivilianPool.State.UNRESOLVED,"materialize_unresolved:"+view.reason());
                        IsoPlayer body=pool.body(token);if(body==null)return;
                        if(view.state()!=CivilianPool.State.ACTIVE)return;
                        if(hunterCount>0)require(!body.isGodMod()&&!body.isInvisible()&&!body.isGhostMode(),"civilian_must_remain_targetable");
                        bodies.put(body,token);
                        if(wave==0)originals.add(body);else require(originals.contains(body),"new_engine_body_after_warmup");
                        require(body.getInventory().getItemCount("Base.Pen")==0,"previous_resident_inventory_leaked");
                    }
                    require(actors.constructed==count&&originals.size()==count,"pool_not_expected_initialized_bodies");
                    if(crowd){
                        for(var t:tokens)actors.replicate(pool.body(t),0,0,false);
                        var owner=stagingOwner(!hunters.ready());if(owner==null){waitFor("Waiting for fresh, hidden staging observations; holding owned bodies.",now);return;}
                        for(var zombie:hunters.bodies())OffscreenZombieLease.renew(zombie,owner,remainingReport(owner));
                        if(!hunters.ready()){
                            int h=hunters.spawned(),target=h/2;hunters.spawnAt(formation.x(target),formation.startY(target)-16-(h%2),target,1);
                            OffscreenZombieLease.renew(hunters.bodies().getLast(),owner,remainingReport(owner));return;
                        }
                    }else if(!hunters.ready()){hunters.spawnOne(formation);return;}
                    if(hunterCount>0&&!crowd) {
                        IsoPlayer viewer=Objects.requireNonNull(observer());protectObserver(viewer);
                        if(chasePositioningAt==0) {
                            announce("Moving you beside the starting group so the native zombie simulation can run. The chase starts automatically.");
                            GameServer.sendTeleport(viewer,formation.viewX(),10005.5f,0);chasePositioningAt=now;return;
                        }
                        if(Math.hypot(viewer.getX()-formation.viewX(),viewer.getY()-10005.5f)>3||now-chasePositioningAt<3_000_000_000L||!hunters.readyToSimulate())return;
                    }
                    if(crowd){clientConfig.rawset("hunters",hunters.ids());if(!crowdReplicasReady(reports)){waitFor("Waiting for all 64 civilian and 128 zombie replicas before the southbound pass.",now);return;}}
                    waiting="";
                    announce("Wave "+(wave+1)+"/4 starting: "+count+" new residents in a "+formation.columns+"x"+formation.rows+" formation, running 150 tiles; "+hunterCount+" fast-shambler pursuers. Direction: original southbound (+Y).");
                    Arrays.fill(seen,false);waveStart=now;waveWork=new ProbeTiming();last=now;next();
                }
                case 4 -> {
                    require(observer()!=null,"observer_disconnected");
                    protectObserver(observer());
                    var active=tokens.stream().map(pool::body).toList();
                    if(crowd)renewCrowdOwners();
                    hunters.tick(active,now);
                    for(var player:GameServer.Players)hunters.observe(hunterObservation(clientReport(reports,player)));
                    double dt=Math.min(.2,(now-last)/1e9);last=now;boolean arrived=true;
                    if(crowd&&now-waveStart<8_000_000_000L&&hunters.moving()<116&&hunters.closestGap(active)>5){
                        for(var body:active)actors.replicate(body,0,0,false);return;
                    }
                    double movementSpeed=crowd?Math.max(.8,Math.min(2.6,1.5+(12-hunters.medianMovingGap(active))*.15)):SPEED;
                    for(int i=0;i<tokens.size();i++) {
                        var token=tokens.get(i);IsoPlayer body=pool.body(token);
                        observeClient(reports,i,body);
                        if(body.getY()<formation.endY(i)-.05) {
                            arrived=false;long moveAt=System.nanoTime();
                            require(actors.walk(token,body,formation.x(i),formation.endY(i),movementSpeed,dt),"native_collision_blocked:"+i);
                            movement.add(System.nanoTime()-moveAt);
                        } else actors.stop(body);
                    }
                    if(now-lastSample>250_000_000L){lastSample=now;sample(now);}
                    if(arrived){retireCursor=0;cleanupPositioned=false;announce("Wave "+(wave+1)+"/4 reached the finish. Waiting until everyone is off-screen before reuse.");next();}
                }
                case 5 -> {
                    if(hunterCount>0&&!crowd&&!cleanupPositioned){
                        announce("Wave finished. Moving the protected observer away for off-screen cleanup; the next wave will return you automatically.");
                        GameServer.sendTeleport(Objects.requireNonNull(observer()),10590.5f,10220.5f,0);cleanupPositioned=true;return;
                    }
                    for(int i=0;i<count;i++)require(seen[i],"client_never_saw_actor:"+i);
                    if(!allOffscreen(reports,false)||!huntersClear(reports,false)){quietAt=0;waitFor("Waiting for all residents to be off-screen and at least 72 tiles from every player.",now);return;}
                    waiting="";
                    if(quietAt==0)quietAt=now;
                    if(now-quietAt<5_000_000_000L)return;
                    if(chaseSummary==null){if(crowd)require(hunters.chasing()>=116,"chase_acquisition_below_90_percent:"+hunters.chasing()+"/128");else hunters.verifyChase();chaseSummary=hunters.summary();}
                    if(!hunters.removeOne())return;
                    if(retireCursor<count) {
                        var token=tokens.get(retireCursor);IsoPlayer body=pool.body(token);body.getInventory().AddItem("Base.Pen");
                        require(pool.retire(token),"retirement_refused:"+retireCursor);retireCursor++;
                    }
                    parkRetired();
                    if(retireCursor==count)next();
                }
                case 6 -> {
                    parkRetired();
                    if(pool.occupied()!=0||!bodies.isEmpty())return;
                    if(!allOffscreen(reports,true)||!huntersClear(reports,true)){quietAt=0;return;}
                    if(quietAt==0)quietAt=now;
                    if(now-quietAt<2_000_000_000L)return;
                    waves.add("{\"wave\":"+(wave+1)+",\"actors\":"+count+",\"tiles_each\":150,\"elapsed_s\":"+(now-waveStart)/1e9
                        +",\"work_p95_ms\":"+waveWork.percentile(.95)+",\"work_p99_ms\":"+waveWork.percentile(.99)+",\"client_seen\":"+count+",\"client_absent_before_reuse\":true,\"hunters\":"+chaseSummary+"}");
                    System.out.println("[CivilianWatch] completed wave="+(wave+1)+" constructors="+actors.constructed+" reuses="+actors.reused);
                    announce("Wave "+(wave+1)+"/4 complete: all "+count+" bodies retired and parked; client removal confirmed.");
                    if(++wave==4){actors.clearParked();require(actors.retainedBodies()==0,"retained_body_leak");finished=true;clientConfig.rawset("phase",7.0);publish();announce("TEST PASSED: 4 waves, "+(4*count)+" resident passes, only "+count+" engine bodies constructed. Cleanup complete.");}
                    else {
                        tokens.clear();phase=2;phaseAt=now;quietAt=0;waiting="";positioningAt=0;
                        IsoPlayer player=observer();require(player!=null,"observer_disconnected");
                        announce("Preparing wave "+(wave+1)+"/4. Returning you to the viewing point; next pass starts in 12 seconds.");
                        GameServer.sendTeleport(player,viewX(),viewY(),0);
                    }
                }
                default -> throw new IllegalStateException("phase");
            }
        } catch(Throwable error){failure=error.getClass().getSimpleName()+":"+Objects.toString(error.getMessage(),"");error.printStackTrace();finished=true;announce("TEST FAILED in wave "+(wave+1)+": "+failure+". Owned resources retained for diagnosis.");try{publish();}catch(Exception ignored){}}
        finally {
            if(!finished&&now-lastPublish>1_000_000_000L){lastPublish=now;try{publish();}catch(Exception error){error.printStackTrace();}}
            long elapsed=System.nanoTime()-begin;work.add(elapsed);if(phase>=3)waveWork.add(elapsed);
        }
    }
    private float viewX(){return crowd?10596.5f:formation.viewX();}
    private float viewY(){return crowd?10035.5f:VIEW_Y;}
    private KahluaTable currentReports(){Object v=LuaManager.env.rawget("AKRPoolWatch");return v instanceof KahluaTable t?t:null;}
    private KahluaTable stagePoints(){
        var points=LuaManager.platform.newTable();int n=0;
        for(int i=0;i<count;i++)for(int offset:new int[]{0,-16,-17}){var p=LuaManager.platform.newTable();p.rawset("x",(double)formation.x(i));p.rawset("y",(double)formation.startY(i)+offset);points.rawset((double)++n,p);}
        return points;
    }
    private long remainingReport(IsoPlayer p){var r=clientReport(currentReports(),p);return r==null?0:750-(System.currentTimeMillis()-((Number)r.rawget("received_ms")).longValue());}
    private IsoPlayer stagingOwner(){return stagingOwner(true);}
    private IsoPlayer stagingOwner(boolean requireHidden){
        if(clientConfig==null||GameServer.Players.isEmpty())return null;
        IsoPlayer owner=null;double best=Double.POSITIVE_INFINITY;
        for(var p:GameServer.Players){var r=clientReport(currentReports(),p);if(r==null||remainingReport(p)<=0)return null;
            if(!(r.rawget("observer_x") instanceof Number x)||!(r.rawget("observer_y") instanceof Number y)||Math.hypot(p.getX()-x.doubleValue(),p.getY()-y.doubleValue())>1)return null;
            if(!(r.rawget("stage") instanceof KahluaTable stage)||requireHidden&&!Boolean.TRUE.equals(stage.rawget("hidden")))return null;
            double d=Math.hypot(p.getX()-10590,p.getY()-9980);if(Boolean.TRUE.equals(stage.rawget("loaded"))&&d<best){owner=p;best=d;}
        }return owner;
    }
    private boolean crowdReplicasReady(KahluaTable reports){
        readyActors=readyHunters=0;if(!reportsFresh(reports))return false;
        boolean all=true;
        for(var p:GameServer.Players){
            var r=clientReport(reports,p);var hs=hunterObservation(r);int ac=0,hc=0;
            for(var z:hunters.bodies())if(hs!=null&&hs.rawget((double)z.getOnlineID()) instanceof KahluaTable h&&Boolean.TRUE.equals(h.rawget("present")))hc++;
            for(var t:tokens){var a=observation(r,pool.body(t).getOnlineID());if(a!=null&&Boolean.TRUE.equals(a.rawget("present"))&&a.rawget("clothes") instanceof Number n&&n.intValue()>0)ac++;}
            readyActors=ac;readyHunters=hc;all&=ac==64&&hc==128;
        }return all;
    }
    private void renewCrowdOwners(){
        for(var z:hunters.bodies()){
            IsoPlayer nearest=null;double best=Double.POSITIVE_INFINITY;
            for(var p:GameServer.Players){var r=clientReport(currentReports(),p);var hs=hunterObservation(r);
                Object v=hs==null?null:hs.rawget((double)z.getOnlineID());if(!(v instanceof KahluaTable h)||!Boolean.TRUE.equals(h.rawget("loaded"))||remainingReport(p)<=0)continue;
                double d=Math.hypot(p.getX()-z.getX(),p.getY()-z.getY());if(d<best){nearest=p;best=d;}
            }
            if(nearest!=null)OffscreenZombieLease.renew(z,nearest,remainingReport(nearest));
        }
    }
    private void parkRetired() throws Exception {
        // Stagger snapshot persistence/reset too; do not burst 32 retirements on one tick.
        CivilianPool.Retired retired=pool.pollRetired();if(retired==null)return;
        IsoPlayer body=null;for(var entry:bodies.entrySet())if(entry.getValue().equals(retired.token())){body=entry.getKey();break;}
        require(body!=null,"retired_reference_missing");
        Files.write(directory.resolve("resident-"+retired.token().resident()+".bin"),retired.snapshot().bytes());
        actors.park(retired.token(),body,retired.snapshot());bodies.remove(body);
    }
    private void reserveWave() {
        chasePositioningAt=0;chaseSummary=null;hunters=new WatchedHunters(hunterCount);clientConfig.rawset("hunters",crowd?LuaManager.platform.newTable():hunters.ids());
        for(int i=0;i<count;i++) {
            String id="wave"+(wave+1)+"-resident"+i;
            actors.profile(id,new NativeCivilianActors.Profile("Wave "+(wave+1)+" Resident "+(i+1),"Generic01",wave%2==1,formation.x(i),formation.startY(i),0));
            var token=pool.reserve(id,1,true,null);require(token!=null,"pool_full");tokens.add(token);
        }
    }
    private void protectObserver(IsoPlayer player) {
        require(player.getRole()!=null&&"admin".equalsIgnoreCase(player.getRole().getName()),"observer_admin_required");
        if(!player.isGodMod()||!player.isInvisible()||!player.isGhostMode()) {
            player.setGodMod(true,true);player.setInvisible(true,true);player.setGhostMode(true,true);
            GameServer.sendPlayerExtraInfo(player,null,true);
        }
        require(player.isGodMod()&&player.isInvisible()&&player.isGhostMode(),"observer_protection_failed");
    }
    private IsoPlayer observer(){for(IsoPlayer p:GameServer.Players)if(p!=null&&"akr".equals(p.getUsername())&&p.getSquare()!=null){var c=GameServer.getConnectionFromPlayer(p);if(c!=null&&c.isFullyConnected())return p;}return null;}
    private KahluaTable clientReport(KahluaTable reports,IsoPlayer player) {
        Object value=reports==null?null:reports.rawget((double)player.getOnlineID());
        if(!(value instanceof KahluaTable t)||!epoch.equals(t.rawget("epoch"))||!(t.rawget("received_ms") instanceof Number n)
            ||System.currentTimeMillis()-n.longValue()>2000)return null;
        return t;
    }
    private KahluaTable hunterObservation(KahluaTable report){Object value=report==null?null:report.rawget("hunters");return value instanceof KahluaTable t?t:null;}
    private boolean huntersClear(KahluaTable reports,boolean absent){
        if(hunterCount==0)return true;
        if(!reportsFresh(reports)||!hunters.farFromPlayers(crowd?12:72))return false;
        for(var player:GameServer.Players)if(!hunters.clientClear(hunterObservation(clientReport(reports,player)),absent,crowd))return false;
        return true;
    }
    private boolean reportsFresh(KahluaTable reports){if(GameServer.Players.isEmpty())return false;for(var player:GameServer.Players)if(clientReport(reports,player)==null)return false;return true;}
    private KahluaTable observation(KahluaTable report,int slot){Object a=report==null?null:report.rawget("actors");Object value=a instanceof KahluaTable t?t.rawget((double)slot):null;return value instanceof KahluaTable t?t:null;}
    private void observeClient(KahluaTable reports,int index,IsoPlayer body) {
        for(var player:GameServer.Players) {
            KahluaTable entry=observation(clientReport(reports,player),body.getOnlineID());
            if(entry!=null&&Boolean.TRUE.equals(entry.rawget(crowd?"visible":"on_screen"))&&Boolean.TRUE.equals(entry.rawget("present"))) {
                require(Boolean.valueOf(wave%2==1).equals(entry.rawget("female")),"client_stale_appearance:"+index);
                seen[index]=true;
            }
        }
    }
    private boolean allOffscreen(KahluaTable reports,boolean absent) {
        if(!reportsFresh(reports))return false;
        for(var player:GameServer.Players)for(int i=0;i<count;i++) {
            if(!absent&&Math.hypot(player.getX()-formation.x(i),player.getY()-formation.endY(i))<72)return false;
            KahluaTable entry=observation(clientReport(reports,player),4096+i);
            if(entry==null||(crowd?!Boolean.TRUE.equals(entry.rawget("hidden")):!Boolean.FALSE.equals(entry.rawget("on_screen")))||absent&&!Boolean.FALSE.equals(entry.rawget("present")))return false;
        }
        return true;
    }
    private void sample(long now)throws Exception {
        StringBuilder line=new StringBuilder("{\"wall_ms\":").append(System.currentTimeMillis()).append(",\"wave\":").append(wave+1).append(",\"actors\":[");
        for(int i=0;i<tokens.size();i++){if(i>0)line.append(',');var t=tokens.get(i);var b=pool.body(t);line.append("{\"id\":").append(t.slot()).append(",\"object\":").append(System.identityHashCode(b)).append(",\"x\":").append(b.getX()).append(",\"y\":").append(b.getY()).append('}');}
        Files.writeString(directory.resolve("watched-samples.jsonl"),line.append("]}\n").toString(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private String stageDiagnostic(){
        if(!crowd)return "null";
        var all=new StringJoiner(",","[","]");
        for(var p:GameServer.Players){var r=clientReport(currentReports(),p);Object value=r==null?null:r.rawget("stage");
            var stage=value instanceof KahluaTable t?t:null;
            all.add("{\"fresh_ms_remaining\":"+remainingReport(p)+",\"loaded\":"+(stage!=null&&Boolean.TRUE.equals(stage.rawget("loaded")))+",\"hidden\":"+(stage!=null&&Boolean.TRUE.equals(stage.rawget("hidden")))+"}");
        }return all.toString();
    }
    private void publish()throws Exception {
        String state=finished?(failure.isEmpty()?"passed":"failed"):"running";
        StringJoiner slots=new StringJoiner(",","[","]");
        if(pool!=null)for(var view:pool.views())slots.add("{\"state\":"+quote(view.state().toString())+",\"reason\":"+quote(view.reason())+"}");
        IsoPlayer viewer=observer();
        String json="{\"world\":\""+world+"\",\"epoch\":\""+epoch+"\",\"status\":\""+state+"\",\"phase\":"+phase+",\"wave\":"+(wave+1)
            +",\"failure\":\""+failure.replace("\\","\\\\").replace("\"","\\\"")+"\",\"constructed\":"+(actors==null?0:actors.constructed)
            +",\"hunters\":"+(hunters==null?"null":hunters.summary())+",\"observer_protected\":"+(viewer!=null&&viewer.isGodMod()&&viewer.isInvisible()&&viewer.isGhostMode())
            +",\"ready_actors\":"+readyActors+",\"ready_hunters\":"+readyHunters+",\"owned_hunters\":"+(hunters==null?0:hunters.bodies().stream().filter(z->z.getOwner()!=null).count())+",\"stage\":"+stageDiagnostic()+",\"actor_count\":"+count+",\"columns\":"+formation.columns+",\"rows\":"+formation.rows
            +",\"replication_packets\":"+(actors==null?0:actors.replicationPackets)
            +",\"server_tick_interval_max_ms\":"+tickIntervals.max/1e6
            +",\"heap_used_bytes\":"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            +",\"assignments\":"+(actors==null?0:actors.reused)+",\"occupied\":"+(pool==null?0:pool.occupied())
            +",\"parked\":"+(actors==null?0:actors.parkedCount())+",\"warm_max_ms\":"+(actors==null?0:actors.warmTiming.max/1e6)
            +",\"warm_p95_ms\":"+(actors==null?0:actors.warmTiming.percentile(.95))+",\"movement_p95_ms\":"+movement.percentile(.95)
            +",\"movement_p99_ms\":"+movement.percentile(.99)+",\"work_p95_ms\":"+work.percentile(.95)+",\"work_p99_ms\":"+work.percentile(.99)
            +",\"cold_max_ms\":"+(actors==null?0:actors.coldTiming.max/1e6)+",\"work_max_ms\":"+work.max/1e6
            +",\"waiting\":"+quote(waiting)+",\"slots\":"+slots+",\"observer\":"+(viewer==null?"null":"["+viewer.getX()+","+viewer.getY()+"]")
            +",\"waves\":["+String.join(",",waves)+"],\"two_client_validated\":false}\n";
        Path target=directory.resolve("watched-report.json"),temp=directory.resolve("watched-report.tmp");Files.writeString(temp,json);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
