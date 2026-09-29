package net.lofers.scenario;

import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.world.moddata.GlobalModData;

/** Optional gameplay agent. Unknown builds stop scenario launch before any save is opened. */
public final class ScenarioAgent {
    static boolean enabled,server,verified;
    static final Set<String> hooks=ConcurrentHashMap.newKeySet();
    static volatile String hookFailure;
    private static KahluaTable installedEnv;
    private static PlannerTransport transport;
    private static String epoch,world;
    private static Path socket;
    private static String registryHash;
    private static Thread worker;
    private static PrimitiveCopy copy;
    private static PrimitiveWrite write;
    private static KahluaTable copySource;
    private static long lastRevision=-1,request,nextCapture,lastStatus;
    private static String status="";
    private static ServerVehicleProbe vehicleProbe;
    private static ResidentPhysics residentPhysics;
    private static RuntimeSession runtime;
    private static RuntimeSocket runtimeSocket;
    private static ServerPedestrians pedestrians;
    private static ServerActors actors;
    private static HeadlessCivilianHarness headless;
    private static WatchedCivilianHarness watched;
    private static WatchedCombatHarness combat;
    private static WatchedSurvivalHarness survival;
    private static OffscreenChaseHarness chase;
    static boolean pedestrianRuntimeEnabled() { return server && runtime != null; }
    public static void premain(String path,Instrumentation instrumentation) throws Exception {
        // JAVA_TOOL_OPTIONS is inherited by the launcher's JNI-library discovery
        // subprocess too. It has no game classpath and must print only its normal
        // launcher output. Exempt this exact pinned helper, never the game JVM.
        if(LaunchGuard.isPinnedDiscovery(System.getProperty("sun.java.command"),System.getProperty("java.class.path"),ScenarioAgent.class.getClassLoader()))return;
        Properties p=new Properties();try(var reader=Files.newBufferedReader(Path.of(path))){p.load(reader);}
        enabled=Boolean.parseBoolean(p.getProperty("scenario.enabled","false"));if(!enabled)return;
        String side=p.getProperty("side","server");if(!Set.of("server","client").contains(side))throw new IllegalArgumentException("side server/client required");server=side.equals("server");
        BuildGuard.verify(ScenarioAgent.class.getClassLoader());verified=true;
        if(server&&Boolean.parseBoolean(p.getProperty("vehicle_probe.enabled","false"))) {
            BuildGuard.verifyServerPhysics(instrumentation);
            System.out.println("[LofersScenario] Verified native server physics "+BuildGuard.SERVER_PHYSICS_SHA256);
        }
        Path bandit=Path.of(p.getProperty("bandits_update_file",""));
        if(!Files.isRegularFile(bandit)||!BuildGuard.hash(Files.readAllBytes(bandit)).equals("fb9bd559da4e0faabd2c35c41cd7d2cd74d85510ef642a7ba6e3776cb8a02192"))
            throw new IllegalStateException("Pinned Bandits 42.20 callback source required");
        instrumentation.addTransformer(new ScenarioTransformer());
        // A transformer exception is otherwise swallowed by Instrumentation. Force
        // every guarded class to load (without initializing it) before opening a save.
        for(String name:ScenarioTransformer.TARGETS)Class.forName(name.replace('/','.'),false,ScenarioAgent.class.getClassLoader());
        if(hookFailure!=null||!hooks.containsAll(ScenarioTransformer.TARGETS))throw new IllegalStateException("Incomplete scenario hooks: "+hookFailure);
        world=p.getProperty("world","");epoch=p.getProperty("server_epoch",UUID.randomUUID().toString());
        if(server){
            if(world.isBlank()||world.length()>128||epoch.length()>128)throw new IllegalArgumentException("world/epoch required and bounded");
            socket=Path.of(p.getProperty("socket","/run/lofers/npc.sock"));if(!socket.isAbsolute())throw new IllegalArgumentException("Absolute socket required");
            registryHash=p.getProperty("registry_hash","");
            startTransport();
        }
        ProbeControl.Config probeConfig=ProbeControl.Config.read(p,world,server);
        if (Boolean.parseBoolean(p.getProperty("runtime.enabled", "false"))) {
            if (!server || probeConfig != null || !world.startsWith("AKR_DayOne_Test_"))
                throw new IllegalArgumentException("runtime_requires_disposable_world_without_vehicle_probe");
            pedestrians = new ServerPedestrians();
            actors = new ServerActors();
            var backends=new HashMap<EventScheduler.Kind,RuntimeSession.Backend>();
            backends.put(EventScheduler.Kind.PEDESTRIAN,pedestrians);backends.put(EventScheduler.Kind.ACTOR,actors);
            if(Boolean.parseBoolean(p.getProperty("encounter.enabled","false"))) {
                for(String key:List.of("headless.enabled","watched.enabled","chase.enabled","combat.enabled"))
                    if(Boolean.parseBoolean(p.getProperty(key,"false")))throw new IllegalArgumentException("encounter_exclusive");
                backends.put(EventScheduler.Kind.ENCOUNTER,new NativeEncounterBackend(world,epoch,Path.of(p.getProperty("encounter.directory","/run/lofers")),Boolean.parseBoolean(p.getProperty("encounter.lifecycle","false"))));
            }
            runtime = new RuntimeSession(world, epoch, new RuntimeSession.Routed(backends));
            runtimeSocket = new RuntimeSocket(Path.of(p.getProperty("runtime.socket", "")), runtime);
        }
        if(probeConfig!=null){
            residentPhysics=new ResidentPhysics(new GamePhysicsBackend());
            vehicleProbe=new ServerVehicleProbe(probeConfig,world,epoch,residentPhysics);
        }
        if(Boolean.parseBoolean(p.getProperty("headless.enabled","false")))
            headless=new HeadlessCivilianHarness(p,world,epoch,server,runtime!=null);
        if(Boolean.parseBoolean(p.getProperty("chase.enabled","false")))
            chase=new OffscreenChaseHarness(p,world,epoch,server,runtime!=null);
        if(Boolean.parseBoolean(p.getProperty("watched.enabled","false")))
            watched=new WatchedCivilianHarness(p,world,epoch,server,runtime!=null);
        if(Boolean.parseBoolean(p.getProperty("combat.enabled","false"))) {
            if(Boolean.parseBoolean(p.getProperty("combat.survival","false")))survival=new WatchedSurvivalHarness(p,world,epoch,server,runtime!=null);
            else combat=new WatchedCombatHarness(p,world,epoch,server,runtime!=null);
        }
        System.out.println("[LofersScenario] Verified B42.20.4 gameplay agent ("+side+")");
    }
    private static void startTransport(){transport=new PlannerTransport(socket,world,epoch,registryHash);worker=new Thread(transport,"lofers-scenario-ipc");worker.setDaemon(true);worker.start();}
    public static void bootstrap(){
        if(!enabled||!verified||LuaManager.env==null||LuaManager.platform==null)return;
        if(installedEnv==LuaManager.env)return;
        try {LuaManager.env.rawset("LofersNative",GameHooks.install());
            if (runtime != null) LuaManager.env.rawset("AKRRuntime", RuntimeLua.install(runtime, pedestrians));
            installedEnv=LuaManager.env;}
        catch(Throwable error){status="bootstrap_failed";}
    }
    public static void tick(){
        if(!enabled||!server)return;bootstrap();
        try {
            GameHooks.ownThread();
            if(runtime!=null)runtime.tick();
            if(headless!=null)headless.tick();
            if(watched!=null)watched.tick();
            if(combat!=null)combat.tick();
            if(survival!=null)survival.tick();
            if(chase!=null)chase.tick();
            if(vehicleProbe!=null)vehicleProbe.tick();
            if(ResidentPlannerBridge.active()){
                ResidentPlannerBridge.tick(transport,System.nanoTime(),()->++request);return;
            }
            KahluaTable root=GlobalModData.instance.get("LofersScenario");if(root==null)return;
            long now=System.nanoTime();
            PlannerTransport.Reply reply=transport.ready.getAndSet(null);
            if(reply!=null){
                var map=reply.plans();map.put("request_id",(double)reply.request());map.put("server_epoch",epoch);map.put("health",transport.health);map.put("guard_ready",verified);
                write=new PrimitiveWrite(map);
            }
            if(write!=null&&write.step(now+500_000L)){root.rawset("bridgeIn",write.result);write=null;lastStatus=now;}
            if(now-lastStatus>1_000_000_000L){
                Object old=root.rawget("bridgeIn");KahluaTable in=old instanceof KahluaTable t?t:LuaManager.platform.newTable();
                in.rawset("health",!GameHooks.paired()?"lua_activation_missing":(!status.isEmpty()?status:transport.health));in.rawset("guard_ready",verified);in.rawset("server_epoch",epoch);in.rawset("world",world);root.rawset("bridgeIn",in);lastStatus=now;
            }
            if(!GameHooks.paired())return;
            if(copy==null&&now>=nextCapture){
                Object source=root.rawget("bridgeOut");if(!(source instanceof KahluaTable t))return;
                Object rev=t.rawget("revision");if(!(rev instanceof Number n)||n.longValue()<=lastRevision)return;
                if(t.rawget("world") instanceof String w&&!world.equals(w))throw new IllegalArgumentException("Lua world mismatch");
                if(!(t.rawget("server_epoch") instanceof String boot)||!epoch.equals(boot))return;
                copySource=t;copy=new PrimitiveCopy(t);nextCapture=now+500_000_000L;
            }
            if(copy!=null&&copy.step(now+1_000_000L)){
                long revision=ProtocolCodec.integer(copy.result.get("revision"),0,9007199254740991L);
                if(!(copySource.rawget("revision") instanceof Number n)||n.longValue()!=revision)throw new IllegalArgumentException("Publication mutated during copy");
                transport.pending.set(new PlannerTransport.Sample(++request,now,copy.result));lastRevision=revision;copy=null;copySource=null;status="";
            }
        } catch(Throwable error){copy=null;copySource=null;status="bridge_snapshot_rejected";}
    }
}
