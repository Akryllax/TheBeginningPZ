package net.lofers.scenario;

import java.util.*;

/** Bounded continuous swept-circle checks; detection only, not native collision simulation. */
final class CollisionForecast {
    record Pose(double time,double x,double y) {}
    record Obstacle(double x,double y,double vx,double vy,double radius,double ageSeconds) {}
    static double firstContact(List<Pose> path,double egoRadius,Obstacle obstacle){return firstContact(path,egoRadius,obstacle,false);}
    static double firstContact(List<Pose> path,double egoRadius,Obstacle obstacle,boolean extendedImpact){
        if(path.size()<2||path.size()>65||path.getFirst().time!=0||!ProbeRoute.finite(egoRadius,obstacle.x,obstacle.y,obstacle.vx,obstacle.vy,obstacle.radius,obstacle.ageSeconds)||
           egoRadius<=0||egoRadius>4||obstacle.radius<=0||obstacle.radius>8||obstacle.ageSeconds<0||obstacle.ageSeconds>.5)return 0;
        double speed=Math.hypot(obstacle.vx,obstacle.vy);if(speed>60)return 0;
        double radius=egoRadius+obstacle.radius+.2+speed*obstacle.ageSeconds;
        for(int i=1;i<path.size();i++){
            Pose a=path.get(i-1),b=path.get(i);double dt=b.time-a.time;
            if(!ProbeRoute.finite(a.time,a.x,a.y,b.time,b.x,b.y)||a.time<0||b.time>(extendedImpact?20:12)||dt<=0)return 0;
            double dx=a.x-obstacle.x-obstacle.vx*a.time,dy=a.y-obstacle.y-obstacle.vy*a.time;
            double vx=(b.x-a.x)/dt-obstacle.vx,vy=(b.y-a.y)/dt-obstacle.vy;
            double c=dx*dx+dy*dy-radius*radius;if(c<=0)return a.time;
            double aa=vx*vx+vy*vy;if(aa<1e-12)continue;
            double bb=2*(dx*vx+dy*vy),disc=bb*bb-4*aa*c;
            if(disc<0)continue;
            double t=(-bb-Math.sqrt(disc))/(2*aa);
            if(t>=0&&t<=dt)return a.time+t;
        }
        return Double.POSITIVE_INFINITY;
    }
    static List<Pose> following(ProbeRoute route,double progress,double x,double y,double speedMps,double horizon){
        if(!ProbeRoute.finite(progress,x,y,speedMps,horizon)||speedMps<0||speedMps>60||horizon<=0||horizon>(route.extendedImpact?20:12))throw new IllegalArgumentException("Invalid forecast");
        var out=new ArrayList<Pose>();out.add(new Pose(0,x,y));
        int steps=Math.min(64,Math.max(1,(int)Math.ceil(horizon/.2)));
        for(int i=1;i<=steps;i++){double t=horizon*i/steps;var p=route.at(progress+speedMps*t);out.add(new Pose(t,p.x(),p.y()));}
        return List.copyOf(out);
    }
}
