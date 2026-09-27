package net.lofers.scenario;

import java.util.Properties;

final class ProbeImpactFixture {
    static void run()throws Exception {
        var route=new ProbeRoute(BezierPath.parse("0,0,10,0,20,0,30,0;30,0,40,0,50,0,60,0"));
        var p=new Properties();
        ScenarioFixture.check(ProbeImpactTarget.read(p,route,true,false,false)==null,"Ordinary route became a crash scene");
        p.setProperty("vehicle_probe.impact_target","30,-1");
        p.setProperty("vehicle_probe.impact_token","impact-0123456789abcdef0123456789abcdef");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,route,true,false,false),"Off-center fixture accepted");
        var centered=new ProbeRoute(BezierPath.parse("0.5,0.5,10.5,0.5,20.5,0.5,30.5,0.5;30.5,0.5,40.5,0.5,50.5,0.5,60.5,0.5"));
        p.setProperty("vehicle_probe.impact_target","30,0");
        var target=ProbeImpactTarget.read(p,centered,true,false,false);
        ScenarioFixture.check(target.tile(30,0)&&!target.tile(29,0),"Impact exemption escaped exact tile");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,false,false,false),"Unreviewed legacy crash enabled");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,true,true,false),"Crash mixed with avoidance");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,true,false,true),"Crash mixed with junction stops");
        p.setProperty("vehicle_probe.impact_token","");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,true,false,false),"Unmarked target accepted");
        p.setProperty("vehicle_probe.impact_token","impact-0123456789abcdef0123456789abcdef");
        p.setProperty("vehicle_probe.impact_target","5,0");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,true,false,false),"Short approach accepted");
        p.setProperty("vehicle_probe.impact_target","55,0");
        ScenarioFixture.rejects(()->ProbeImpactTarget.read(p,centered,true,false,false),"Insufficient runout accepted");
        var curves=new java.util.ArrayList<BezierPath.Curve>();
        for(int i=0;i<8;i++)curves.add(BezierPath.line(new ProbeRoute.Point(.5+i*40,.5),new ProbeRoute.Point(.5+(i+1)*40,.5)));
        ScenarioFixture.rejects(()->new BezierPath(curves),"Ordinary road length limit bypassed");
        var extended=new ProbeRoute(new BezierPath(curves,true));
        ScenarioFixture.check(extended.length==320&&extended.loadingAnchors.size()<=11&&extended.requestWidth()==9,"Extended course loading is unbounded");
        for(var chunk:extended.chunks)ScenarioFixture.check(extended.loadingAnchors.stream().anyMatch(a->Math.abs(a.x()-chunk.x())<=4&&Math.abs(a.y()-chunk.y())<=4),"Loading anchors miss a retained chunk");
        var forecast=CollisionForecast.following(extended,0,.5,.5,80/3.6,20);
        ScenarioFixture.check(Double.isFinite(CollisionForecast.firstContact(forecast,2,new CollisionForecast.Obstacle(300,.5,0,0,2,0),true)),"Extended horizon missed blocker");
        ScenarioFixture.rejects(()->CollisionForecast.following(centered,0,.5,.5,10,20),"Normal forecast horizon silently expanded");
        var work=new ProbeSafetyWork(true);for(int i=0;i<1024;i++)ScenarioFixture.check(work.tile(),"Extended scan capacity too small");
        ScenarioFixture.check(!work.tile(),"Extended scan capacity unbounded");
        var config=new Properties();config.setProperty("vehicle_probe.enabled","true");config.setProperty("vehicle_probe.directory","/private/probe");
        config.setProperty("vehicle_probe.x",".5");config.setProperty("vehicle_probe.y",".5");config.setProperty("vehicle_probe.heading_degrees","90");
        config.setProperty("vehicle_probe.lane_mode","true");config.setProperty("vehicle_probe.waypoints",".5,.5;40.5,.5");
        var encoded=new java.util.ArrayList<String>();for(var c:curves)encoded.add(c.p0().x()+",.5,"+c.p1().x()+",.5,"+c.p2().x()+",.5,"+c.p3().x()+",.5");
        config.setProperty("vehicle_probe.beziers",String.join(";",encoded));config.setProperty("vehicle_probe.extended_impact","true");config.setProperty("vehicle_probe.speed_kmh","80");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(config,"LofersVehicleProbe_fixture",true),"Extended course without target accepted");
        config.setProperty("vehicle_probe.impact_target","160,0");config.setProperty("vehicle_probe.impact_token","impact-0123456789abcdef0123456789abcdef");
        ScenarioFixture.check(ProbeControl.Config.read(config,"LofersVehicleProbe_fixture",true).route().extendedImpact,"Reviewed extended impact rejected");
        config.setProperty("vehicle_probe.speed_kmh","81");
        ScenarioFixture.rejects(()->ProbeControl.Config.read(config,"LofersVehicleProbe_fixture",true),"Extended speed ceiling bypassed");
        System.out.println("Impact config fixtures passed: explicit target, identity token, straight route and approach/runout bounds");
    }
}
