package net.lofers.scenario;

import java.util.*;
import static net.lofers.scenario.CivilianNavigation.*;

/** Safer endpoint selection, followed by bounded A*. No straight-line flee fallback. */
public final class CivilianEscape {
    private final Set<Action> executable;private final Graph graph;private final Key key;private final Tile start;private final List<Tile> threats,goals;
    private Search search;private int candidate,used;private Result result;
    public CivilianEscape(Key key,Graph graph,Tile start,List<Tile> threats){this(key,graph,start,threats,EnumSet.allOf(Action.class));}
    public CivilianEscape(Key key,Graph graph,Tile start,List<Tile> threats,Set<Action> executable) {
        this.executable=Set.copyOf(executable);
        if(threats.isEmpty()||threats.size()>32)throw new IllegalArgumentException("threats");
        this.key=key;this.start=start;this.threats=List.copyOf(threats);
        double near=clearance(start),minimum=Math.min(1.5,near);
        var edges=new TreeMap<Tile,List<Edge>>();var danger=new HashMap<Tile,Double>();
        for(Tile tile:graph.edges().keySet())if(clearance(tile)>=minimum||tile.equals(start))edges.put(tile,List.of());
        for(Tile tile:new ArrayList<>(edges.keySet())) {
            edges.put(tile,graph.edges().get(tile).stream().filter(e->edges.containsKey(e.to())&&this.executable.contains(e.action())).toList());
            double risk=0;for(Tile threat:threats)risk+=Math.pow(Math.max(0,12-tile.distance(threat)),2)*0.25;
            danger.put(tile,risk+graph.danger().getOrDefault(tile,0d));
        }
        // Removing risky nodes can invalidate diagonals: drop diagonals whose four cardinal edges no longer exist.
        var safe=new TreeMap<Tile,List<Edge>>();
        for(var entry:edges.entrySet())safe.put(entry.getKey(),entry.getValue().stream().filter(e->safeDiagonal(edges,entry.getKey(),e)).toList());
        this.graph=new Graph(graph.revision(),safe,danger);
        goals=edges.keySet().stream().filter(t->t.distance(start)<=16&&clearance(t)>=near+2)
            .sorted(Comparator.<Tile>comparingDouble(this::clearance).reversed().thenComparingDouble(t->t.distance(start)).thenComparing(t->t))
            .toList();
        next();
    }
    private static boolean safeDiagonal(Map<Tile,List<Edge>> graph,Tile a,Edge e) {
        Tile b=e.to();if(e.action()!=Action.WALK||a.x()==b.x()||a.y()==b.y())return true;
        Tile h=new Tile(b.x(),a.y(),a.z()),v=new Tile(a.x(),b.y(),a.z());
        return walk(graph,a,h)&&walk(graph,a,v)&&walk(graph,h,b)&&walk(graph,v,b);
    }
    private static boolean walk(Map<Tile,List<Edge>> graph,Tile a,Tile b){return graph.getOrDefault(a,List.of()).stream().anyMatch(e->e.to().equals(b)&&e.action()==Action.WALK);}
    private double clearance(Tile at){return threats.stream().mapToDouble(at::distance).min().orElse(0);}
    private void next(){if(candidate>=goals.size())result=new Result(key,Status.NO_PATH,List.of(),used,0);else search=new Search(key,graph,start,goals.get(candidate++),executable);}
    public int advance(int budget,long revision) {
        int work=0;
        while(result==null&&work<budget) {
            int n=search.advance(Math.min(budget-work,MAX_EXPANSIONS-used),revision);
            // Failed candidates count too; disconnected safer endpoints cannot create an unbounded retry loop.
            int charged=Math.max(1,n);work+=charged;used+=charged;
            Result r=search.result();
            if(r.status()==Status.FOUND||r.status()==Status.STALE||r.status()==Status.CANCELLED)result=new Result(key,r.status(),r.route(),used,r.cost());
            else if(used>=MAX_EXPANSIONS)result=new Result(key,Status.BUDGET_EXHAUSTED,List.of(),used,0);
            else if(r.status()!=Status.PENDING)next();
            else if(n==0)break;
        }
        return work;
    }
    public Result result(){return result==null?new Result(key,Status.PENDING,List.of(),used,0):result;}
    /** Native solver can choose a different corridor: sample it against the same safety envelope. */
    public boolean acceptNative(List<CivilianPathRequests.Point> points) {
        if(result==null||result.status()!=Status.FOUND||points.isEmpty()||points.size()>MAX_ROUTE)return false;
        var first=points.getFirst();
        if(new Tile((int)Math.floor(first.x()),(int)Math.floor(first.y()),(int)Math.floor(first.z())).distance(start)>1)return false;
        var last=points.getLast();Tile goal=result.route().getLast().at();
        if(!new Tile((int)Math.floor(last.x()),(int)Math.floor(last.y()),(int)Math.floor(last.z())).equals(goal))return false;
        double selectedRisk=result.route().stream().mapToDouble(s->graph.danger().getOrDefault(s.at(),0d)).max().orElse(0);
        int samples=0;
        for(int i=0;i<points.size();i++) {
            var a=points.get(Math.max(0,i-1));var b=points.get(i);
            int count=Math.max(1,(int)Math.ceil(Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2))*2));
            if(samples+count>512)return false;samples+=count;
            for(int j=0;j<=count;j++) {
                double t=(double)j/count;
                Tile tile=new Tile((int)Math.floor(a.x()+(b.x()-a.x())*t),(int)Math.floor(a.y()+(b.y()-a.y())*t),(int)Math.floor(a.z()+(b.z()-a.z())*t));
                if(!graph.edges().containsKey(tile))return false;
                if(graph.danger().getOrDefault(tile,0d)>selectedRisk+1)return false;
            }
        }
        return true;
    }
}
