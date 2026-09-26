package net.lofers.scenario;

/** Oriented rectangles for parked-vehicle clearance; no physics or ownership writes. */
record TrafficFootprint(int id,double x,double y,double heading,double halfWidth,double halfLength) {
    TrafficFootprint {
        if(id<0||!ProbeRoute.finite(x,y,heading,halfWidth,halfLength)||halfWidth<=0||halfWidth>3||halfLength<=0||halfLength>6)
            throw new IllegalArgumentException("Invalid observed vehicle footprint");
    }
    TrafficFootprint at(double x,double y,double heading){return new TrafficFootprint(id,x,y,heading,halfWidth,halfLength);}
    static boolean overlaps(TrafficFootprint a,TrafficFootprint b,double margin){
        if(!Double.isFinite(margin)||margin<0||margin>2)throw new IllegalArgumentException("Invalid footprint margin");
        double ax=Math.sin(a.heading),ay=Math.cos(a.heading),bx=Math.sin(b.heading),by=Math.cos(b.heading);
        double dx=b.x-a.x,dy=b.y-a.y;
        return onAxis(dx,dy,ax,ay,a,b,ax,ay,bx,by,margin)&&onAxis(dx,dy,ay,-ax,a,b,ax,ay,bx,by,margin)&&
               onAxis(dx,dy,bx,by,a,b,ax,ay,bx,by,margin)&&onAxis(dx,dy,by,-bx,a,b,ax,ay,bx,by,margin);
    }
    private static boolean onAxis(double dx,double dy,double nx,double ny,TrafficFootprint a,TrafficFootprint b,double ax,double ay,double bx,double by,double margin){
        double reach=(a.halfLength+margin)*Math.abs(nx*ax+ny*ay)+(a.halfWidth+margin)*Math.abs(nx*ay-ny*ax)+
                     (b.halfLength+margin)*Math.abs(nx*bx+ny*by)+(b.halfWidth+margin)*Math.abs(nx*by-ny*bx);
        return Math.abs(dx*nx+dy*ny)<=reach;
    }
    static boolean pathClear(ProbeRoute route,double from,double distance,TrafficFootprint ego,TrafficFootprint obstacle){
        if(route.trajectory==null||!ProbeRoute.finite(from,distance)||from<0||distance<0||distance>60)throw new IllegalArgumentException("Invalid bounded footprint sweep");
        if(overlaps(ego,obstacle,.25))return false;
        double end=Math.min(route.length,from+distance),span=Math.max(0,end-from);
        int steps=Math.max(1,(int)Math.ceil(span/.2));
        // Samples are at most .2 tiles apart. At maximum permitted curvature
        // the additional corner travel is bounded by .35 * body radius * .1.
        double pad=.25+.1+.035*Math.hypot(ego.halfWidth,ego.halfLength);
        for(int i=0;i<=steps;i++){
            var pose=route.trajectory.at(from+span*i/steps);
            if(overlaps(ego.at(pose.x(),pose.y(),pose.heading()),obstacle,pad))return false;
        }
        return true;
    }
}
