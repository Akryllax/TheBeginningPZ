package net.lofers.scenario;

import java.util.*;

/** Independent analytic expectations and bicycle integration; no native-engine claims. */
final class BezierFixture {
    static ProbeRoute.Point point(double x,double y){return new ProbeRoute.Point(x,y);}
    static BezierPath course(){
        double k=6*4.0/3*Math.tan(Math.PI/8);
        return new BezierPath(List.of(BezierPath.line(point(0,0),point(31,0)),
            new BezierPath.Curve(point(31,0),point(31+k,0),point(37,-6+k),point(37,-6)),
            BezierPath.line(point(37,-6),point(37,-23))));
    }
    static void check(boolean v,String text){ScenarioFixture.check(v,text);}
    static void run()throws Exception{
        TrafficSafetyFixture.run();
        BezierPath path=course();
        check(Math.abs(path.length-(48+3*Math.PI))<.01,"Arc length does not approximate quarter circle");
        check(Math.abs(path.at(0).heading()-Math.PI/2)<1e-9&&Math.abs(path.at(path.length).heading()-Math.PI)<1e-9,"Endpoint tangent wrong");
        var middle=path.at(31+(path.ends[2]-31)/2);
        check(Math.abs(middle.heading()-3*Math.PI/4)<1e-5&&Math.abs(middle.curvature()-1.0/6)<.004,"Curve derivative/angle wrong");
        for(double s=0;s<path.length;s+=.11){var a=path.at(s);var b=path.at(Math.min(path.length,s+.05));check(Math.hypot(a.x()-b.x(),a.y()-b.y())<.051,"Arc-length lookup discontinuity");}
        var projection=path.project(12,.4,10,14);
        check(Math.abs(projection.progress()-12)<1e-6&&Math.abs(projection.deviation()-.4)<1e-6,"Signed deviation wrong");
        check(path.project(12,0,0,2).progress()<=2.000001,"Projection skipped current progress window");
        check(path.speedLimit(30,12,15)<8&&Math.abs(path.speedLimit(0,8,15)-15)<1e-8,"Curvature preview does not slow before bend");
        ScenarioFixture.rejects(()->new BezierPath(List.of(new BezierPath.Curve(point(0,0),point(0,0),point(2,0),point(3,0)))),"Stationary tangent accepted");
        ScenarioFixture.rejects(()->new BezierPath(List.of(BezierPath.line(point(0,0),point(10,0)),BezierPath.line(point(10,0),point(10,10)))),"Sharp tangent join accepted");
        ScenarioFixture.rejects(()->BezierPath.parse("0,0,NaN,0,2,0,3,0"),"Nonfinite controls accepted");
        for(double speed:List.of(5.0,10.0,15.0))for(double offset:List.of(-.3,0.0,.3))simulate(path,speed,offset);
        // Measure detached geometry work only, not game hooks or native physics.
        long[] durations=new long[2000];double sum=0;
        for(int i=-500;i<durations.length;i++){
            long begin=System.nanoTime();double s=(i+500)%500/500.0*path.length;
            var p=path.at(s);sum+=path.project(p.x(),p.y()+.1,s-1,s+4).deviation();sum+=path.speedLimit(s,18,15);
            if(i>=0)durations[i]=System.nanoTime()-begin;
        }
        Arrays.sort(durations);check(Double.isFinite(sum),"Geometry workload failed");
        System.out.println("Bezier fixtures passed: analytic tangents/curvature, bounded projection, joins, 9 stop-and-drive simulations; detached geometry p95_ms="+durations[1900]/1e6);
    }
    private static void simulate(BezierPath path,double limit,double offset){
        var route=new ProbeRoute(path);double x=0,y=offset,yaw=Math.PI/2,velocity=0,dt=.05,wheelbase=1.94,maxDeviation=0;
        var driver=new ProbeDriver(route,limit,wheelbase,List.of(new ProbeDriver.Stop(24.75,2)));
        boolean arrived=false;double dwell=0;
        for(int i=0;i<3000;i++){
            var command=driver.step(x,y,Math.sin(yaw),Math.cos(yaw),velocity*3.6,dt,"");
            check(command.stopReason().isEmpty(),"Bezier simulation stopped: "+command.stopReason()+" at "+x+","+y);
            if(command.arrived()){arrived=true;break;}
            if(driver.stopHoldSeconds()>0&&velocity*3.6<.25)dwell+=dt;
            maxDeviation=Math.max(maxDeviation,Math.abs(driver.signedDeviation()));
            velocity=Math.max(0,velocity+(command.engineForce()/1000-command.brake()/80-.03*velocity)*dt);
            yaw+=velocity/wheelbase*Math.tan(command.steering())*dt;
            x+=velocity*Math.sin(yaw)*dt;y+=velocity*Math.cos(yaw)*dt;
        }
        check(arrived&&driver.completedStops()==1&&dwell>=1.9,"Bezier did not complete stop/course");
        check(maxDeviation<.5,"Bezier lane deviation too large");
    }
}
