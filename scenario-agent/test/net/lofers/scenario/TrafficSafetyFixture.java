package net.lofers.scenario;

import java.util.*;

final class TrafficSafetyFixture {
    static void check(boolean v,String why){ScenarioFixture.check(v,why);}
    static void run(){
        var budget=new ProbeSafetyBudget();
        check(budget.update("road_check_budget",.1).equals("safety_refresh_pending"),"Incomplete scan did not brake");
        check(budget.update("",.1).isEmpty()&&budget.pauses()==1,"Complete scan did not release brake");
        check(budget.update("actor_in_vehicle_path",.1).equals("actor_in_vehicle_path"),"Real blocker was made transient");
        for(int i=0;i<19;i++)budget.update("vehicle_check_budget",.1);
        check(budget.update("road_check_budget",.2).equals("safety_scan_starved"),"Repeated incomplete scans did not fail closed");
        var path=List.of(new CollisionForecast.Pose(0,0,0),new CollisionForecast.Pose(2,10,0));
        double hit=CollisionForecast.firstContact(path,1,new CollisionForecast.Obstacle(5,0,0,0,1,0));
        check(Math.abs(hit-.56)<1e-8,"Stationary blocker missed or contact time wrong");
        check(Double.isFinite(CollisionForecast.firstContact(path,1,new CollisionForecast.Obstacle(5,-10,0,10,1,0))),"Fast crossing actor tunneled between samples");
        check(Double.isInfinite(CollisionForecast.firstContact(path,1,new CollisionForecast.Obstacle(5,10,0,10,1,0))),"Receding actor incorrectly collides");
        check(CollisionForecast.firstContact(path,1,new CollisionForecast.Obstacle(5,0,0,0,1,1))==0,"Stale blocker accepted");
        check(CollisionForecast.firstContact(path,1,new CollisionForecast.Obstacle(Double.NaN,0,0,0,1,0))==0,"Invalid blocker accepted");
        var hazard=new TrafficIncidentSafety.Envelope(0,0,10,10);
        var distant=new TrafficIncidentSafety.Player(80,0,4,.1,.5);
        check(TrafficIncidentSafety.check(hazard,List.of(distant),3,true,true,true,true).allowed(),"Distant walking observer rejected");
        var driving=new TrafficIncidentSafety.Player(80,0,30,.1,2);
        check(!TrafficIncidentSafety.check(hazard,List.of(driving),3,true,true,true,true).allowed(),"Fast approaching observer admitted");
        var close=new TrafficIncidentSafety.Player(12,5,4,0,.5);
        check(!TrafficIncidentSafety.check(hazard,List.of(distant,close),3,true,true,true,true).allowed(),"Second nearby player ignored");
        check(!TrafficIncidentSafety.check(hazard,List.of(distant),3,false,true,true,true).allowed(),"Missing audience admitted");
        check(!TrafficIncidentSafety.check(hazard,List.of(distant),3,true,true,false,true).allowed(),"Player property admitted");
        check(!TrafficIncidentSafety.check(hazard,List.of(distant),3,true,true,true,false).allowed(),"Visible staging admitted");
        System.out.println("Traffic safety fixtures passed: continuous moving blocker contact, stale rejection, all-player reachable-area exclusion and scene gates; no native collision claim");
    }
}
