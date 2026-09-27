package net.lofers.scenario;

import java.util.*;

/** Bounded pure pursuit. All positions are native observations, never writes. */
final class ProbeDriver {
    record Output(double engineForce,double brake,double steering,double targetSpeed,double progress,double crossTrack,
                  double remaining,double headingError,double targetX,double targetY,boolean arrived,String stopReason) {}
    record Stop(double progress,double holdSeconds) {}
    private final List<Stop> stops;
    private int nextStop;
    private double held;
    private boolean waitingForObstacle;
    boolean waitingForObstacle(){return waitingForObstacle;}
    int completedStops(){return nextStop;}
    double stopHoldSeconds(){return held;}
    private final ProbeRoute route;
    private final double speedLimit,wheelbase;
    private ProbeCarLimits limits=ProbeCarLimits.fixture();
    void limits(ProbeCarLimits observed){limits=Objects.requireNonNull(observed);}
    private double progress,steering,stalled,previousProgress;
    private double signedDeviation,pathHeadingError,pathCurvature,lookaheadDistance,previewDistance,predictedDeviation,predictedHeadingError;
    double signedDeviation(){return signedDeviation;}
    double pathHeadingError(){return pathHeadingError;}
    double pathCurvature(){return pathCurvature;}
    double lookaheadDistance(){return lookaheadDistance;}
    double previewDistance(){return previewDistance;}
    double predictedDeviation(){return predictedDeviation;}
    double predictedHeadingError(){return predictedHeadingError;}
    ProbeDriver(ProbeRoute route,double speedLimit,double wheelbase){this(route,speedLimit,wheelbase,List.of());}
    ProbeDriver(ProbeRoute route,double speedLimit,double wheelbase,List<Stop> stops){
        if(!ProbeRoute.finite(speedLimit,wheelbase)||speedLimit<1||speedLimit>(route.extendedImpact?80:route.trajectory!=null?50:route.laneMode?15:5)||wheelbase<1.5||wheelbase>4)throw new IllegalArgumentException("Driver configuration outside probe bounds");
        this.stops=List.copyOf(stops);
        double prev=0;if(stops.size()>4)throw new IllegalArgumentException("Too many stops");
        for(Stop stop:stops){if(!ProbeRoute.finite(stop.progress(),stop.holdSeconds())||stop.progress()<2||stop.progress()>route.length-3||stop.progress()<=prev||stop.holdSeconds()<1||stop.holdSeconds()>5)throw new IllegalArgumentException("Invalid stop");prev=stop.progress();}
        this.route=route;this.speedLimit=speedLimit;this.wheelbase=wheelbase;
    }
    Output step(double x,double y,double forwardX,double forwardY,double speed,double dt,String emergency){
        return step(x,y,forwardX,forwardY,speed,dt,emergency,Double.POSITIVE_INFINITY);
    }
    Output step(double x,double y,double forwardX,double forwardY,double speed,double dt,String emergency,double obstacleStop){
        waitingForObstacle=false;
        if(!ProbeRoute.finite(x,y,forwardX,forwardY,speed,dt)||speed<0||dt<=0||dt>1)return stop("invalid_observation",false,0);
        if(Double.isNaN(obstacleStop)||obstacleStop==Double.NEGATIVE_INFINITY)return stop("invalid_obstacle_stop",false,0);
        if(emergency!=null&&!emergency.isEmpty())return stop(emergency,false,0);
        if(speed>Math.max(6.5,speedLimit+2.5))return stop("excessive_speed",false,0);
        double norm=Math.hypot(forwardX,forwardY);if(norm<0.5||norm>1.5)return stop("invalid_heading",false,0);
        ProbeRoute.Projection projection;
        double yaw=Math.atan2(forwardX,forwardY);
        if(route.trajectory!=null){
            var p=route.trajectory.project(x,y,progress-1,progress+Math.min(4,1+speed/3.6*dt*2));
            projection=new ProbeRoute.Projection(p.progress(),p.distance(),p.sample().segment());
            signedDeviation=p.deviation();pathHeadingError=ProbeRoute.wrap(p.sample().heading()-yaw);pathCurvature=p.sample().curvature();
        } else projection=route.project(x,y);
        if(projection.distance()>(route.laneMode?.65:1.5))return stop("route_departure",false,projection.distance());
        if(projection.progress()>progress+4)return stop("unexpected_route_jump",false,projection.distance());
        progress=Math.max(progress,projection.progress());double remaining=route.length-progress;
        double stopDistance=Double.POSITIVE_INFINITY;
        double obstacleDistance=obstacleStop-progress;
        // A parked vehicle is a temporary destination, not a failed route.
        // Do not consume a scheduled stop while queued short of its line.
        if(obstacleDistance<.35){
            waitingForObstacle=speed<.25;held=0;stalled=0;previousProgress=progress;
            return stop("",false,projection.distance());
        }
        if(nextStop<stops.size()){
            Stop stop=stops.get(nextStop);stopDistance=stop.progress()-progress;
            if(stopDistance<-.75)return stop("stop_line_overrun",false,projection.distance());
            if(stopDistance<.35){
                held=speed<.25?held+dt:0;
                stalled=0;previousProgress=progress;
                if(held<stop.holdSeconds())return stop("",false,projection.distance());
                nextStop++;held=0;stopDistance=Double.POSITIVE_INFINITY;
            }
        }
        stopDistance=Math.min(stopDistance,obstacleDistance);
        double endDistance=Math.hypot(route.points.getLast().x()-x,route.points.getLast().y()-y);
        if(remaining<0.8&&endDistance<0.7)return stop("",true,projection.distance());
        stalled=progress>previousProgress+0.08?0:stalled+dt;if(stalled>10)return stop("no_route_progress",false,projection.distance());
        if(progress>previousProgress+0.08)previousProgress=progress;
        double lookahead=route.trajectory==null?2.3+Math.min(speed/3.6,4.2)*0.2:Math.max(2.3,Math.min(4,1.8+speed/3.6*.4));
        double deceleration=route.trajectory==null?.6:limits.brakingDeceleration();
        lookaheadDistance=lookahead;previewDistance=Math.min(route.extendedImpact?220:64,Math.max(10,3+Math.pow(speed/3.6,2)/(2*deceleration)));
        ProbeRoute.Point target=route.at(progress+lookahead);
        double dx=target.x()-x,dy=target.y()-y,targetDistance=Math.hypot(dx,dy);
        double error=ProbeRoute.wrap(Math.atan2(dx,dy)-Math.atan2(forwardX,forwardY));
        if(Math.abs(error)>Math.toRadians(110))return stop("heading_diverged",false,projection.distance());
        double curvature=2*Math.sin(error)/Math.max(1,targetDistance);
        double angleLimit=route.trajectory==null?.65:limits.steeringAngle();
        double rate=route.trajectory==null?1.2:limits.steeringRate();
        double desired=Math.max(-angleLimit,Math.min(angleLimit,Math.atan(wheelbase*curvature)));
        double change=rate*Math.min(dt,0.25);steering+=Math.max(-change,Math.min(change,desired-steering));
        double turnSpeed=route.laneMode?5:2.2;
        // Curved trajectories already have a continuous curvature/braking
        // envelope. The old polyline threshold would insert a second, abrupt
        // speed step as steering passed 0.10, even on a constant-radius bend.
        double targetSpeed=route.trajectory==null?Math.min(speedLimit,Math.abs(curvature)>0.10?turnSpeed:speedLimit):speedLimit;
        // Reduce speed before sharp course changes enter the immediate lookahead.
        for(int i=1;route.trajectory==null&&i<route.points.size()-1;i++)if(route.cumulative[i]>progress&&route.cumulative[i]-progress<(route.laneMode?22:5)){
            var a=route.points.get(i-1);var b=route.points.get(i);var c=route.points.get(i+1);
            double turn=Math.abs(ProbeRoute.wrap(Math.atan2(c.x()-b.x(),c.y()-b.y())-Math.atan2(b.x()-a.x(),b.y()-a.y())));
            if(turn>Math.toRadians(10))targetSpeed=Math.min(targetSpeed,route.laneMode?Math.sqrt(Math.pow(turnSpeed/3.6,2)+2*.6*Math.max(0,route.cumulative[i]-progress-3))*3.6:2.2);
        }
        if(route.trajectory!=null){
            targetSpeed=Math.min(targetSpeed,Math.min(limits.maxSpeed(),deceleration*(route.extendedImpact?18:10)*3.6));
            targetSpeed=Math.min(targetSpeed,route.trajectory.speedLimit(progress,previewDistance,targetSpeed,limits.lateralAcceleration(),deceleration));
            // Preview the steering updates across segment joins, rather than
            // extrapolating one fixed steering angle through the entire turn.
            double px=x,py=y,pyaw=yaw,ps=progress,psteer=steering;
            for(int i=0;i<10;i++){
                double v=speed/3.6;
                var ahead=route.trajectory.project(px,py,ps-1,ps+v*.1+2);ps=Math.max(ps,ahead.progress());
                var aim=route.at(ps+lookahead);
                double e=ProbeRoute.wrap(Math.atan2(aim.x()-px,aim.y()-py)-pyaw);
                double wanted=Math.max(-angleLimit,Math.min(angleLimit,Math.atan(wheelbase*2*Math.sin(e)/Math.max(1,Math.hypot(aim.x()-px,aim.y()-py)))));
                psteer+=Math.max(-rate*.1,Math.min(rate*.1,wanted-psteer));
                pyaw+=v/wheelbase*Math.tan(psteer)*.1;
                px+=v*Math.sin(pyaw)*.1;py+=v*Math.cos(pyaw)*.1;
            }
            var prediction=route.trajectory.project(px,py,progress-1,progress+speed/3.6+3);
            predictedDeviation=prediction.deviation();predictedHeadingError=ProbeRoute.wrap(prediction.sample().heading()-pyaw);
            if(Math.abs(predictedDeviation)>.5)targetSpeed=Math.min(targetSpeed,speed*Math.sqrt(.5/Math.abs(predictedDeviation)));
        }
        targetSpeed=Math.min(targetSpeed,Math.max(0.65,Math.sqrt(2*(route.trajectory==null?.45:deceleration)*Math.max(0,endDistance-0.5))*3.6));
        if(Double.isFinite(stopDistance))targetSpeed=Math.min(targetSpeed,Math.max(.4,Math.sqrt(2*deceleration*Math.max(0,stopDistance-.25))*3.6));
        if(Math.abs(error)>Math.toRadians(60))targetSpeed=Math.min(targetSpeed,1.2);
        double force=Math.max(0,Math.min(route.trajectory==null?650:limits.driveForce(),(targetSpeed-speed)*(route.trajectory==null?550:limits.mass()*.55)));
        double excess=Math.max(0,speed-targetSpeed);
        // No minimum service-brake impulse: small corrections must not apply
        // the large force used by a stop/hazard. Retain stronger braking for
        // sustained tracking error; emergency stops still use full brake.
        double brake=route.trajectory==null?(speed>targetSpeed+0.25?Math.min(80,15+excess*15):0):
            Math.min(80,6*excess+40*Math.pow(Math.max(0,excess-.3),2));
        if(brake>0)force=0;
        return new Output(force,brake,steering,targetSpeed,progress,projection.distance(),remaining,error,target.x(),target.y(),false,"");
    }
    private Output stop(String reason,boolean arrived,double cross){steering=0;return new Output(0,100,0,0,progress,cross,route.length-progress,0,0,0,arrived,reason);}
}
