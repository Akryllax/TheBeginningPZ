package net.lofers.scenario;

import java.util.*;

/** Original bounded cubic trajectory math. No engine classes or wall-clock state. */
final class BezierPath {
    record Curve(ProbeRoute.Point p0,ProbeRoute.Point p1,ProbeRoute.Point p2,ProbeRoute.Point p3) {}
    record Sample(double x,double y,double heading,double curvature,int segment) {
        ProbeRoute.Point point(){return new ProbeRoute.Point(x,y);}
    }
    record Projection(double progress,double distance,double deviation,Sample sample) {}
    private static final int STEPS=128,MAX_SEGMENTS=15;
    final List<Curve> curves;
    final double[] ends;
    final double length;
    private final double[] arc,xs,ys,ts;
    private final int[] segments;
    final boolean extendedImpact;
    BezierPath(List<Curve> input){this(input,false);}
    BezierPath(List<Curve> input,boolean extendedImpact){
        this.extendedImpact=extendedImpact;
        if(input.isEmpty()||input.size()>MAX_SEGMENTS)throw new IllegalArgumentException("Bezier segment count outside 1..15");
        curves=List.copyOf(input);ends=new double[input.size()+1];
        int count=input.size()*STEPS+1;
        arc=new double[count];xs=new double[count];ys=new double[count];ts=new double[count];segments=new int[count];
        for(int segment=0;segment<curves.size();segment++){
            Curve c=curves.get(segment);
            for(var p:List.of(c.p0,c.p1,c.p2,c.p3))if(!ProbeRoute.finite(p.x(),p.y())||p.x()< -20000||p.x()>60000||p.y()< -20000||p.y()>60000)throw new IllegalArgumentException("Invalid Bezier control point");
            double chordX=c.p3.x()-c.p0.x(),chordY=c.p3.y()-c.p0.y();
            var controls=List.of(c.p0,c.p1,c.p2,c.p3);
            for(int i=1;i<4;i++)if((controls.get(i).x()-controls.get(i-1).x())*chordX+(controls.get(i).y()-controls.get(i-1).y())*chordY<=0)throw new IllegalArgumentException("Non-forward Bezier controls");
            double polygon=distance(c.p0,c.p1)+distance(c.p1,c.p2)+distance(c.p2,c.p3);
            if(polygon<.5||polygon>60)throw new IllegalArgumentException("Bezier segment extent");
            if(segment>0){
                Sample a=evaluate(curves.get(segment-1),1,segment-1),b=evaluate(c,0,segment);
                if(Math.hypot(a.x-b.x,a.y-b.y)>.001||Math.abs(ProbeRoute.wrap(a.heading-b.heading))>Math.toRadians(1))throw new IllegalArgumentException("Disconnected Bezier position or tangent");
            }
            for(int j=0;j<=STEPS;j++){
                int at=segment*STEPS+j;double t=(double)j/STEPS;Sample p=evaluate(c,t,segment);
                if(Math.abs(p.curvature)>.35)throw new IllegalArgumentException("Bezier exceeds bounded vehicle curvature");
                xs[at]=p.x;ys[at]=p.y;ts[at]=t;segments[at]=segment;
                if(at>0){
                    double distance=Math.hypot(xs[at]-xs[at-1],ys[at]-ys[at-1]);
                    Sample prior=evaluate(curves.get(segments[at-1]),ts[at-1],segments[at-1]);
                    if(distance+2.2*Math.abs(ProbeRoute.wrap(p.heading-prior.heading))>.45)throw new IllegalArgumentException("Bezier sampling exceeds footprint margin");
                    arc[at]=arc[at-1]+distance;
                }
            }
            ends[segment+1]=arc[(segment+1)*STEPS];
        }
        length=arc[count-1];if(length<2||length>(extendedImpact?480:60))throw new IllegalArgumentException("Bezier course exceeds bounded length");
    }
    static Curve line(ProbeRoute.Point a,ProbeRoute.Point b){return new Curve(a,lerp(a,b,1.0/3),lerp(a,b,2.0/3),b);}
    private static ProbeRoute.Point lerp(ProbeRoute.Point a,ProbeRoute.Point b,double t){return new ProbeRoute.Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t);}
    private static double distance(ProbeRoute.Point a,ProbeRoute.Point b){return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    private static Sample evaluate(Curve c,double t,int segment){
        double u=1-t;
        double x=u*u*u*c.p0.x()+3*u*u*t*c.p1.x()+3*u*t*t*c.p2.x()+t*t*t*c.p3.x();
        double y=u*u*u*c.p0.y()+3*u*u*t*c.p1.y()+3*u*t*t*c.p2.y()+t*t*t*c.p3.y();
        double dx=3*(u*u*(c.p1.x()-c.p0.x())+2*u*t*(c.p2.x()-c.p1.x())+t*t*(c.p3.x()-c.p2.x()));
        double dy=3*(u*u*(c.p1.y()-c.p0.y())+2*u*t*(c.p2.y()-c.p1.y())+t*t*(c.p3.y()-c.p2.y()));
        double ddx=6*(u*(c.p2.x()-2*c.p1.x()+c.p0.x())+t*(c.p3.x()-2*c.p2.x()+c.p1.x()));
        double ddy=6*(u*(c.p2.y()-2*c.p1.y()+c.p0.y())+t*(c.p3.y()-2*c.p2.y()+c.p1.y()));
        double norm=Math.hypot(dx,dy);if(norm<1e-5)throw new IllegalArgumentException("Bezier stationary tangent");
        return new Sample(x,y,Math.atan2(dx,dy),(dy*ddx-dx*ddy)/(norm*norm*norm),segment);
    }
    private int index(double s){int at=Arrays.binarySearch(arc,s);if(at<0)at=-at-1;return Math.max(1,Math.min(arc.length-1,at));}
    Sample at(double s){
        s=Math.max(0,Math.min(length,s));int hi=index(s),lo=hi-1;
        int segment=segments[hi];double lowT=segments[lo]==segment?ts[lo]:0;
        double t=lowT+(ts[hi]-lowT)*(s-arc[lo])/Math.max(1e-12,arc[hi]-arc[lo]);
        return evaluate(curves.get(segment),t,segment);
    }
    Projection project(double x,double y,double start,double end){
        start=Math.max(0,Math.min(length,start));end=Math.max(start,Math.min(length,end));
        int begin=index(start),finish=index(end);double best=Double.POSITIVE_INFINITY,progress=start;
        // A progress window avoids jumping onto a nearby later segment at crossings.
        for(int hi=begin;hi<=finish;hi++){
            int lo=hi-1;double dx=xs[hi]-xs[lo],dy=ys[hi]-ys[lo],den=dx*dx+dy*dy;
            double t=den>0?((x-xs[lo])*dx+(y-ys[lo])*dy)/den:0;
            double low=Math.max(0,(start-arc[lo])/(arc[hi]-arc[lo]));
            double high=Math.min(1,(end-arc[lo])/(arc[hi]-arc[lo]));
            t=Math.max(low,Math.min(high,t));double distance=Math.hypot(x-xs[lo]-t*dx,y-ys[lo]-t*dy);
            if(distance<best){best=distance;progress=arc[lo]+t*(arc[hi]-arc[lo]);}
        }
        Sample p=at(progress);double ex=x-p.x,ey=y-p.y;
        double deviation=-Math.cos(p.heading)*ex+Math.sin(p.heading)*ey;
        return new Projection(progress,Math.hypot(ex,ey),deviation,p);
    }
    /** Braking envelope, looking across segment joins with fixed bounded work. */
    double speedLimit(double progress,double horizon,double cruiseKmh){
        return speedLimit(progress,horizon,cruiseKmh,2.5,1.6);
    }
    double speedLimit(double progress,double horizon,double cruiseKmh,double lateralAcceleration,double deceleration){
        double allowed=cruiseKmh/3.6;
        for(int i=0;i<=32;i++){
            double distance=horizon*i/32;Sample p=at(progress+distance);
            double curveLimit=Math.sqrt(lateralAcceleration/Math.max(1e-6,Math.abs(p.curvature)));
            allowed=Math.min(allowed,Math.sqrt(curveLimit*curveLimit+2*deceleration*Math.max(0,distance-2)));
        }
        return allowed*3.6;
    }
    List<ProbeRoute.Point> knots(){var out=new ArrayList<ProbeRoute.Point>();out.add(curves.getFirst().p0);for(Curve c:curves)out.add(c.p3);return out;}
    List<Sample> samples(){var out=new ArrayList<Sample>(arc.length);for(int i=0;i<arc.length;i++)out.add(evaluate(curves.get(segments[i]),ts[i],segments[i]));return out;}
    static BezierPath parse(String text){return parse(text,false);}
    static BezierPath parse(String text,boolean extendedImpact){
        if(text.length()>4096)throw new IllegalArgumentException("Bezier config too long");
        var curves=new ArrayList<Curve>();
        for(String entry:text.split(";",-1)){
            String[] values=entry.strip().split(",",-1);if(values.length!=8)throw new IllegalArgumentException("Bezier requires four x/y controls");
            var p=new ArrayList<ProbeRoute.Point>();for(int i=0;i<8;i+=2)p.add(new ProbeRoute.Point(Double.parseDouble(values[i]),Double.parseDouble(values[i+1])));
            curves.add(new Curve(p.get(0),p.get(1),p.get(2),p.get(3)));
        }
        return new BezierPath(curves,extendedImpact);
    }
}
