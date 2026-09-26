package net.lofers.scenario;

import java.util.*;

/** Pure geometry/controller checks; these do not substitute for native physics validation. */
final class ProbeDriverFixture {
    static final String COURSE="10783.5,9860.5;10814.5,9860.5;10816.5,9859.964102;10817.964102,9858.5;10818.5,9856.5;10818.5,9838.5";
    static void check(boolean value,String message){ScenarioFixture.check(value,message);}
    static ProbeDriver.Output observe(ProbeDriver driver,double x,double y,double yaw,double speed,double dt){
        return driver.step(x,y,Math.sin(yaw),Math.cos(yaw),speed,dt,"");
    }
    static void run() throws Exception {
        ProbeRoute course=ProbeRoute.parse(COURSE);
        check(Math.abs(course.length-55.211657)<0.00001,"Course length changed");
        check(course.points.size()==6&&course.tiles.size()==307,"Swept asphalt corridor changed");
        check(course.cells.size()<=9&&course.chunks.size()<=96&&course.requestWidth()<=13,"Unbounded terrain coverage");
        for(var chunk:course.chunks){check(Math.abs(chunk.x()-course.centerChunkX)<=course.requestWidth()/2&&Math.abs(chunk.y()-course.centerChunkY)<=course.requestWidth()/2,"Retained chunk area misses terrain");}
        check(Math.abs(course.heading()-Math.PI/2)<1e-9,"Native +X heading convention changed");
        check(Math.abs(ProbeRoute.wrap(Math.toRadians(358))-Math.toRadians(-2))<1e-9,"Heading wrap discontinuity");
        check(Math.abs(ProbeRoute.wrap(Math.toRadians(-358))-Math.toRadians(2))<1e-9,"Reverse heading wrap discontinuity");
        ScenarioFixture.rejects(()->ProbeRoute.parse("0,0;NaN,1"),"Nonfinite route accepted");
        ScenarioFixture.rejects(()->ProbeRoute.parse("0,0;0,41"),"Unbounded segment accepted");
        ScenarioFixture.rejects(()->ProbeRoute.parse("0,0;0,31;0,62"),"Unbounded route accepted");
        ScenarioFixture.rejects(()->ProbeRoute.parse("0,0;0,5;0,0"),"Reversing route accepted");
        ScenarioFixture.rejects(()->ProbeRoute.parse("0,0;0,0.1"),"Degenerate segment accepted");
        ProbeRoute line=ProbeRoute.straight(0,0,90,10);
        ProbeDriver aligned=new ProbeDriver(line,4,2.5);
        var start=observe(aligned,0,0,Math.PI/2,0,.1);
        check(start.engineForce()>0&&start.brake()==0&&Math.abs(start.steering())<1e-9,"Aligned car does not drive straight");
        var overspeed=observe(aligned,.1,0,Math.PI/2,5,.1);
        check(overspeed.engineForce()==0&&overspeed.brake()>0,"Speed cap does not brake");
        var departure=observe(new ProbeDriver(line,4,2.5),0,1.6,Math.PI/2,2,.1);
        check(departure.stopReason().equals("route_departure")&&departure.brake()==100&&departure.engineForce()==0,"Route departure not stopped");
        var danger=new ProbeDriver(line,4,2.5).step(0,0,1,0,2,.1,"actor_in_vehicle_path");
        check(danger.stopReason().equals("actor_in_vehicle_path")&&danger.brake()==100,"Emergency did not brake");
        check(observe(new ProbeDriver(line,4,2.5),Double.NaN,0,0,0,.1).stopReason().equals("invalid_observation"),"Invalid observation accepted");
        check(observe(new ProbeDriver(line,4,2.5),0,0,Math.PI/2,0,2).stopReason().equals("invalid_observation"),"Stale tick accepted");
        check(observe(new ProbeDriver(line,4,2.5),5,0,Math.PI/2,0,.1).stopReason().equals("unexpected_route_jump"),"Route jump accepted");
        check(observe(new ProbeDriver(line,4,2.5),0,0,-Math.PI/2,0,.1).stopReason().equals("heading_diverged"),"Opposite heading accepted");
        ProbeDriver stalled=new ProbeDriver(line,4,2.5);ProbeDriver.Output held=null;
        for(int i=0;i<102;i++)held=observe(stalled,0,0,Math.PI/2,0,.1);
        check(held.stopReason().equals("no_route_progress"),"Stall deadline missing");
        ProbeDriver terminal=new ProbeDriver(line,4,2.5);ProbeDriver.Output end=null,near=null;
        for(double x=0;x<=9.6;x+=.2){var current=observe(terminal,x,0,Math.PI/2,2,.1);if(x>8&&x<8.3)near=current;end=current;}
        check(near!=null&&near.targetSpeed()<4,"Terminal approach does not slow");
        check(end.arrived()&&end.engineForce()==0&&end.brake()==100,"Arrival does not brake");
        ProbeDriver curved=new ProbeDriver(course,4,2.5);ProbeDriver.Output corner=null;
        for(double x=10783.5;x<=10813.5;x+=.25)corner=observe(curved,x,9860.5,Math.PI/2,4,.2);
        check(corner.targetSpeed()<=2.2&&corner.steering()>0&&corner.brake()>0,"Turn does not anticipate steering/slowdown");
        simulateCourse(course);
        ProbeTiming timing=new ProbeTiming();for(int i=0;i<98;i++)timing.add(100_000);timing.add(2_100_000);timing.add(7_000_000);
        check(timing.count==100&&timing.over2==2&&timing.over5==1&&timing.percentile(.95)==.2&&timing.percentile(.99)==2.2,"Fixed-space timing accounting incorrect");
        System.out.println("Vehicle route/controller fixtures passed: corridor bounds, heading/wrap, turn slowdown, departure, emergency stop, stale observation, stall, arrival, kinematic course, latency histogram");
    }
    private static void simulateCourse(ProbeRoute route){
        // Independent bicycle integration checks controller geometry/sign and
        // convergence. Native traction, collisions and replication remain live tests.
        double x=route.points.getFirst().x(),y=route.points.getFirst().y(),yaw=route.heading(),speed=0,maxCross=0;
        double dt=.05,wheelbase=2.5;ProbeDriver driver=new ProbeDriver(route,4,wheelbase);boolean arrived=false;
        for(int i=0;i<2400;i++){
            var output=observe(driver,x,y,yaw,speed*3.6,dt);
            check(output.stopReason().isEmpty(),"Kinematic route aborted: "+output.stopReason());
            maxCross=Math.max(maxCross,output.crossTrack());if(output.arrived()){arrived=true;break;}
            double acceleration=output.engineForce()/1000-output.brake()/80.0-.03*speed;
            speed=Math.max(0,speed+acceleration*dt);yaw+=speed/wheelbase*Math.tan(output.steering())*dt;
            x+=speed*Math.sin(yaw)*dt;y+=speed*Math.cos(yaw)*dt;
        }
        check(arrived,"Kinematic course failed to arrive within120seconds");
        check(maxCross<.75,"Kinematic turn cuts course beyond clearance allowance");
        System.out.println("Controller-only course simulation: max_cross_track="+maxCross+"; native steering/collision proof still required");
    }
}
