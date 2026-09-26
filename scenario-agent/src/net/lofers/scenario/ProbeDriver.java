package net.lofers.scenario;

/** Bounded low-speed pure pursuit. All positions are native observations, never writes. */
final class ProbeDriver {
    record Output(double engineForce,double brake,double steering,double targetSpeed,double progress,double crossTrack,
                  double remaining,double headingError,double targetX,double targetY,boolean arrived,String stopReason) {}
    private final ProbeRoute route;
    private final double speedLimit,wheelbase;
    private double progress,steering,stalled,previousProgress;
    ProbeDriver(ProbeRoute route,double speedLimit,double wheelbase){
        if(!ProbeRoute.finite(speedLimit,wheelbase)||speedLimit<1||speedLimit>5||wheelbase<1.5||wheelbase>4)throw new IllegalArgumentException("Driver configuration outside probe bounds");
        this.route=route;this.speedLimit=speedLimit;this.wheelbase=wheelbase;
    }
    Output step(double x,double y,double forwardX,double forwardY,double speed,double dt,String emergency){
        if(!ProbeRoute.finite(x,y,forwardX,forwardY,speed,dt)||speed<0||dt<=0||dt>1)return stop("invalid_observation",false,0);
        if(emergency!=null&&!emergency.isEmpty())return stop(emergency,false,0);
        if(speed>6.5)return stop("excessive_speed",false,0);
        double norm=Math.hypot(forwardX,forwardY);if(norm<0.5||norm>1.5)return stop("invalid_heading",false,0);
        var projection=route.project(x,y);
        if(projection.distance()>1.5)return stop("route_departure",false,projection.distance());
        if(projection.progress()>progress+4)return stop("unexpected_route_jump",false,projection.distance());
        progress=Math.max(progress,projection.progress());double remaining=route.length-progress;
        double endDistance=Math.hypot(route.points.getLast().x()-x,route.points.getLast().y()-y);
        if(remaining<0.8&&endDistance<0.7)return stop("",true,projection.distance());
        stalled=progress>previousProgress+0.08?0:stalled+dt;if(stalled>10)return stop("no_route_progress",false,projection.distance());
        if(progress>previousProgress+0.08)previousProgress=progress;
        double lookahead=2.3+Math.min(speed/3.6,1.4)*0.2;
        ProbeRoute.Point target=route.at(progress+lookahead);
        double dx=target.x()-x,dy=target.y()-y,targetDistance=Math.hypot(dx,dy);
        double error=ProbeRoute.wrap(Math.atan2(dx,dy)-Math.atan2(forwardX,forwardY));
        if(Math.abs(error)>Math.toRadians(110))return stop("heading_diverged",false,projection.distance());
        double curvature=2*Math.sin(error)/Math.max(1,targetDistance);
        double desired=Math.max(-0.65,Math.min(0.65,Math.atan(wheelbase*curvature)));
        double change=1.2*Math.min(dt,0.25);steering+=Math.max(-change,Math.min(change,desired-steering));
        double targetSpeed=Math.min(speedLimit,Math.abs(curvature)>0.10?2.2:speedLimit);
        // Reduce speed before sharp course changes enter the immediate lookahead.
        for(int i=1;i<route.points.size()-1;i++)if(route.cumulative[i]>progress&&route.cumulative[i]-progress<5){
            var a=route.points.get(i-1);var b=route.points.get(i);var c=route.points.get(i+1);
            double turn=Math.abs(ProbeRoute.wrap(Math.atan2(c.x()-b.x(),c.y()-b.y())-Math.atan2(b.x()-a.x(),b.y()-a.y())));
            if(turn>Math.toRadians(10))targetSpeed=Math.min(targetSpeed,2.2);
        }
        targetSpeed=Math.min(targetSpeed,Math.max(0.65,Math.sqrt(2*0.45*Math.max(0,endDistance-0.5))*3.6));
        if(Math.abs(error)>Math.toRadians(60))targetSpeed=Math.min(targetSpeed,1.2);
        double force=Math.max(0,Math.min(650,(targetSpeed-speed)*550));
        double brake=speed>targetSpeed+0.25?Math.min(80,15+(speed-targetSpeed)*15):0;
        if(brake>0)force=0;
        return new Output(force,brake,steering,targetSpeed,progress,projection.distance(),remaining,error,target.x(),target.y(),false,"");
    }
    private Output stop(String reason,boolean arrived,double cross){steering=0;return new Output(0,100,0,0,progress,cross,route.length-progress,0,0,0,arrived,reason);}
}
