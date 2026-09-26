package net.lofers.scenario;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Operator boundary fixtures. No native body or game world is fabricated here. */
final class ProbeFixture {
    static byte[] control(String epoch,long id,String action){return ("server_epoch="+epoch+"\ncommand_id="+id+"\naction="+action+"\n").getBytes(StandardCharsets.ISO_8859_1);}
    static void run()throws Exception {
        ProbeDriverFixture.run();
        Properties p=new Properties();p.setProperty("vehicle_probe.enabled","false");
        ScenarioFixture.check(ProbeControl.Config.read(p,"AKR_DayOne",true)==null,"Disabled probe not inert");
        p.setProperty("vehicle_probe.enabled","true");p.setProperty("vehicle_probe.directory","/private/probe");
        p.setProperty("vehicle_probe.x","10756.5");p.setProperty("vehicle_probe.y","9856.5");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"AKR_DayOne",true),"Probe accepted playable world");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",false),"Probe accepted client");
        ProbeControl.Config config=ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true);
        ScenarioFixture.check(config.yaw()==90&&config.speed()<=5&&config.distance()<=12,"Unsafe defaults");
        p.setProperty("vehicle_probe.speed_kmh","80");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true),"Unbounded speed accepted");
        p.setProperty("vehicle_probe.speed_kmh","NaN");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true),"NaN speed accepted");
        p.setProperty("vehicle_probe.speed_kmh","4");p.setProperty("vehicle_probe.x","10783.5");p.setProperty("vehicle_probe.y","9860.5");
        p.setProperty("vehicle_probe.waypoints",ProbeDriverFixture.COURSE);
        var road=ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true);
        ScenarioFixture.check(road.roadMode()&&road.route().points.size()==6&&road.deadlineSeconds()<=120,"Bounded road route not enabled");
        p.setProperty("vehicle_probe.heading_degrees","0");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true),"Unaligned spawn heading accepted");
        p.setProperty("vehicle_probe.heading_degrees","90");p.setProperty("vehicle_probe.x","10780");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(p,"LofersVehicleProbe_fixture",true),"Route with wrong spawn accepted");
        ScenarioFixture.rejects(()->ProbeControl.parse(control("old",1,"start"),"new",0),"Old boot command accepted");
        ScenarioFixture.check(ProbeControl.parse(control("boot",1,"start"),"boot",1)==null,"Repeated command replayed");
        ScenarioFixture.rejects(()->ProbeControl.parse(control("boot",2,"eval"),"boot",1),"Nonallowlisted action accepted");
        ScenarioFixture.rejects(()->ProbeControl.parse(new byte[4097],"boot",0),"Unbounded control accepted");
        Path dir=Files.createTempDirectory(Path.of("artifacts/scenario-agent"),"probe-control-fixture-");
        try {
            ProbeControl io=new ProbeControl(new ProbeControl.Config(dir.toAbsolutePath(),1,1,90,10,4),"fixture-boot");
            io.status.set(Map.of("phase","armed","server_epoch","fixture-boot"));
            Files.write(dir.resolve("control.properties"),control("fixture-boot",2,"start"));
            Thread thread=new Thread(io);thread.setDaemon(true);thread.start();
            long deadline=System.nanoTime()+3_000_000_000L;
            while((io.command.get()==null||!Files.isRegularFile(dir.resolve("status.properties")))&&System.nanoTime()<deadline)Thread.sleep(5);
            ScenarioFixture.check(io.command.get()!=null&&io.command.get().id()==2,"Private-file command not delivered");
            Properties status=new Properties();try(var r=Files.newBufferedReader(dir.resolve("status.properties"))){status.load(r);}
            ScenarioFixture.check("armed".equals(status.getProperty("phase")),"Detached status not published");
            thread.interrupt();thread.join(2000);
        }finally {try(var paths=Files.list(dir)){for(Path file:paths.toList())Files.deleteIfExists(file);}Files.deleteIfExists(dir);}
        System.out.println("Vehicle probe fixtures passed: disabled/client/world gates, command epoch/replay/size bounds, detached operator I/O");
    }
}
