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
        System.out.println("Impact config fixtures passed: explicit target, identity token, straight route and approach/runout bounds");
    }
}
