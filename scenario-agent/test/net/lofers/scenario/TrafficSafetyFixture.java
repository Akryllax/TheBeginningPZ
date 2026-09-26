package net.lofers.scenario;

import java.util.*;

final class TrafficSafetyFixture {
    static void check(boolean v,String why){ScenarioFixture.check(v,why);}
    static void run(){
        nativeSnapshots();
        TrafficPassFixture.run();
        var budget=new ProbeSafetyWork();
        for(int i=0;i<256;i++)check(budget.tile(),"Valid tile budget rejected");
        check(!budget.tile(),"Unbounded tile scan accepted");
        for(int i=0;i<64;i++)check(budget.vehicle(),"Valid vehicle budget rejected");
        check(!budget.vehicle(),"Unbounded vehicle scan accepted");
        budget.reset();check(budget.tiles()==0&&budget.vehicles()==0,"Safety work did not reset");
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
    private static void nativeSnapshots(){
        var snapshots=new NativeVehicleSamples();
        NativeVehicleSamples.Source one=new NativeVehicleSamples.Source(){
            public int count(){return 1;}
            public int read(int offset,float[] output){
                Arrays.fill(output,0,30,0);output[0]=42.1f;output[8]=3;output[10]=5;output[13]=4.1f;return 1;
            }
        };
        check(snapshots.refresh(one).isEmpty(),"Complete native sample rejected");
        check(snapshots.get(42).vx()==3&&snapshots.get(42).vy()==5,"Native velocity axes incorrect");
        check(snapshots.get(244)==null,"Parked vehicle without a native body was fabricated");
        check(!snapshots.refresh(new NativeVehicleSamples.Source(){public int count(){return 65;}public int read(int i,float[] b){throw new AssertionError("Unbounded native scan");}}).isEmpty(),"Unbounded native count accepted");
        check(!snapshots.refresh(new NativeVehicleSamples.Source(){public int count(){return 2;}public int read(int i,float[] b){return i==0?one.read(0,b):0;}}).isEmpty(),"Incomplete native snapshot accepted");
        check(!snapshots.refresh(new NativeVehicleSamples.Source(){public int count(){return 1;}public int read(int i,float[] b){one.read(0,b);b[13]=9;return 1;}}).isEmpty(),"Malformed native wheel count accepted");
        check(!snapshots.refresh(new NativeVehicleSamples.Source(){public int count(){return 2;}public int read(int i,float[] b){return one.read(0,b);}}).isEmpty(),"Duplicate native identity accepted");
    }
}
