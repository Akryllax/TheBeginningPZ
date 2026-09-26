package net.lofers.scenario;

import java.util.*;

/** Original oriented footprint for the pinned SmallCar profile, with sampling margin. */
final class ProbeFootprint {
    static final double HALF_WIDTH=.95,HALF_LENGTH=1.95;
    static boolean touches(int tx,int ty,double x,double y,double fx,double fy) {
        double rx=fy,ry=-fx,dx=tx+.5-x,dy=ty+.5-y;
        if(Math.abs(dx)>Math.abs(fx)*HALF_LENGTH+Math.abs(rx)*HALF_WIDTH+.5||
           Math.abs(dy)>Math.abs(fy)*HALF_LENGTH+Math.abs(ry)*HALF_WIDTH+.5)return false;
        return Math.abs(dx*fx+dy*fy)<=HALF_LENGTH+.5*(Math.abs(fx)+Math.abs(fy))&&
               Math.abs(dx*rx+dy*ry)<=HALF_WIDTH+.5*(Math.abs(rx)+Math.abs(ry));
    }
    private static void add(Set<ProbeRoute.Tile> cells,double x,double y,double angle) {
        double fx=Math.sin(angle),fy=Math.cos(angle);
        for(int ty=(int)Math.floor(y-3);ty<=(int)Math.floor(y+3);ty++)
            for(int tx=(int)Math.floor(x-3);tx<=(int)Math.floor(x+3);tx++)
                if(touches(tx,ty,x,y,fx,fy))cells.add(new ProbeRoute.Tile(tx,ty));
    }
    static LinkedHashSet<ProbeRoute.Tile> swept(BezierPath path){
        var cells=new LinkedHashSet<ProbeRoute.Tile>();
        for(var p:path.samples())add(cells,p.x(),p.y(),p.heading());
        return cells;
    }
    static LinkedHashSet<ProbeRoute.Tile> swept(List<ProbeRoute.Point> points) {
        var cells=new LinkedHashSet<ProbeRoute.Tile>();double previous=0;
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var b=points.get(i);double dx=b.x()-a.x(),dy=b.y()-a.y();
            double angle=Math.atan2(dx,dy),distance=Math.hypot(dx,dy);
            if(i>1){double change=ProbeRoute.wrap(angle-previous);int steps=Math.max(1,(int)Math.ceil(Math.abs(change)/Math.toRadians(2)));
                for(int j=0;j<=steps;j++)add(cells,a.x(),a.y(),previous+change*j/steps);}
            int steps=Math.max(1,(int)Math.ceil(distance/.25));
            for(int j=0;j<=steps;j++)add(cells,a.x()+dx*j/steps,a.y()+dy*j/steps,angle);
            previous=angle;
        }
        return cells;
    }
}
