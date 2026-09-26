package net.lofers.scenario;

import java.util.*;

/** Immutable bounded XY course. Geometry has no game or network dependencies. */
final class ProbeRoute {
    record Point(double x,double y) {}
    record Tile(int x,int y) {}
    record Projection(double progress,double distance,int segment) {}
    final List<Point> points;
    final List<Tile> tiles,chunks,cells;
    final double[] cumulative;
    final double length;
    final boolean laneMode;
    final BezierPath trajectory;
    final int centerChunkX,centerChunkY,widthChunks;
    ProbeRoute(List<Point> input) {this(input,false);}
    ProbeRoute(List<Point> input,boolean laneMode) {this(input,laneMode,null);}
    ProbeRoute(BezierPath trajectory){this(trajectory.knots(),true,trajectory);}
    private ProbeRoute(List<Point> input,boolean laneMode,BezierPath trajectory) {
        this.laneMode=laneMode;this.trajectory=trajectory;
        if(input.size()<2||input.size()>16)throw new IllegalArgumentException("Route requires2..16points");
        points=List.copyOf(input);cumulative=new double[input.size()];
        for(int i=0;i<points.size();i++){
            Point p=points.get(i);if(!finite(p.x,p.y)||p.x< -20000||p.x>60000||p.y< -20000||p.y>60000)throw new IllegalArgumentException("Invalid route coordinate");
            if(i>0){Point previous=points.get(i-1);double d=Math.hypot(p.x-previous.x,p.y-previous.y);
                if(d<0.5||d>40)throw new IllegalArgumentException("Route segment outside0.5..40tiles");cumulative[i]=cumulative[i-1]+d;
            }
            if(i>1){Point a=points.get(i-2),b=points.get(i-1);double turn=wrap(Math.atan2(p.x-b.x,p.y-b.y)-Math.atan2(b.x-a.x,b.y-a.y));
                if(Math.abs(turn)>Math.toRadians(100))throw new IllegalArgumentException("Route turn too sharp");}
        }
        if(trajectory!=null)System.arraycopy(trajectory.ends,0,cumulative,0,cumulative.length);
        length=cumulative[cumulative.length-1];if(length<2||length>60)throw new IllegalArgumentException("Route length outside2..60tiles");
        LinkedHashSet<Tile> swept=new LinkedHashSet<>(),coverage=new LinkedHashSet<>(),nativeCells=new LinkedHashSet<>();
        // Quarter-tile samples plus a half-sample margin conservatively include
        // every tile touched by the radius2.25 swept disk, including its ends.
        for(double s=0;s<length+0.25;s+=0.25){Point p=at(Math.min(s,length));
            for(int y=(int)Math.floor(p.y-3);y<=(int)Math.floor(p.y+3);y++)for(int x=(int)Math.floor(p.x-3);x<=(int)Math.floor(p.x+3);x++){
                double dx=Math.max(Math.max(x-p.x,0),p.x-(x+1.0)),dy=Math.max(Math.max(y-p.y,0),p.y-(y+1.0));
                if(Math.hypot(dx,dy)<=2.375)swept.add(new Tile(x,y));
            }
            for(int cy=(int)Math.floor((p.y-9)/8);cy<=(int)Math.floor((p.y+9)/8);cy++)
                for(int cx=(int)Math.floor((p.x-9)/8);cx<=(int)Math.floor((p.x+9)/8);cx++)coverage.add(new Tile(cx,cy));
        }
        if(laneMode)swept=trajectory==null?ProbeFootprint.swept(points):ProbeFootprint.swept(trajectory);
        if(swept.size()>1500||coverage.size()>96)throw new IllegalArgumentException("Route spatial work exceeds bounds");
        for(Tile t:coverage)nativeCells.add(new Tile(Math.floorDiv(t.x,5),Math.floorDiv(t.y,5)));
        if(nativeCells.size()>9)throw new IllegalArgumentException("Route native cell limit");
        tiles=List.copyOf(swept);chunks=List.copyOf(coverage);cells=List.copyOf(nativeCells);
        int minX=chunks.stream().mapToInt(Tile::x).min().orElseThrow(),maxX=chunks.stream().mapToInt(Tile::x).max().orElseThrow();
        int minY=chunks.stream().mapToInt(Tile::y).min().orElseThrow(),maxY=chunks.stream().mapToInt(Tile::y).max().orElseThrow();
        centerChunkX=Math.floorDiv(minX+maxX,2);centerChunkY=Math.floorDiv(minY+maxY,2);
        int width=Math.max(maxX-minX+1,maxY-minY+1)+2;widthChunks=width+(width%2==0?1:0);
        if(widthChunks>13)throw new IllegalArgumentException("Route loading extent exceeds13chunks");
    }
    int requestWidth(){return widthChunks;}
    static ProbeRoute parse(String text){return parse(text,false);}
    static ProbeRoute parse(String text,boolean laneMode){
        if(text.length()>2048)throw new IllegalArgumentException("Route text exceeds2048bytes");
        ArrayList<Point> result=new ArrayList<>();
        for(String entry:text.split(";",-1)){String[] xy=entry.strip().split(",",-1);if(xy.length!=2)throw new IllegalArgumentException("Route point requires x,y");
            result.add(new Point(Double.parseDouble(xy[0].strip()),Double.parseDouble(xy[1].strip())));}
        return new ProbeRoute(result,laneMode);
    }
    static ProbeRoute straight(double x,double y,double yaw,double distance){double r=Math.toRadians(yaw);return new ProbeRoute(List.of(new Point(x,y),new Point(x+Math.sin(r)*distance,y+Math.cos(r)*distance)));}
    Point at(double s){
        if(trajectory!=null)return trajectory.at(s).point();
        s=Math.max(0,Math.min(length,s));
        for(int i=1;i<points.size();i++)if(s<=cumulative[i]){Point a=points.get(i-1),b=points.get(i);double t=(s-cumulative[i-1])/(cumulative[i]-cumulative[i-1]);return new Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t);}
        return points.getLast();
    }
    Projection project(double x,double y){
        if(trajectory!=null){var p=trajectory.project(x,y,0,length);return new Projection(p.progress(),p.distance(),p.sample().segment());}
        double best=Double.POSITIVE_INFINITY,progress=0;int segment=0;
        for(int i=1;i<points.size();i++){Point a=points.get(i-1),b=points.get(i);double dx=b.x-a.x,dy=b.y-a.y;
            double t=Math.max(0,Math.min(1,((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy)));
            double d=Math.hypot(x-(a.x+t*dx),y-(a.y+t*dy));
            if(d<best){best=d;progress=cumulative[i-1]+t*(cumulative[i]-cumulative[i-1]);segment=i-1;}}
        return new Projection(progress,best,segment);
    }
    double heading(){if(trajectory!=null)return trajectory.at(0).heading();Point a=points.getFirst(),b=points.get(1);return Math.atan2(b.x-a.x,b.y-a.y);}
    static double wrap(double r){return Math.atan2(Math.sin(r),Math.cos(r));}
    static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v))return false;return true;}
}
