package net.lofers.scenario;
import java.util.*;
import java.util.function.LongSupplier;
import static net.lofers.scenario.CivilianNavigation.*;
/** Bounded geometry-only memoization. Threat costs never contaminate shared geometry. */
final class CivilianGeometryCache implements CivilianGraphCapture.Source {
    private record Entry(List<Edge> edges,boolean complete,long at) { }
    private final CivilianGraphCapture.Source source;private final LongSupplier clock;
    private final LinkedHashMap<Tile,Entry> entries=new LinkedHashMap<>();private long revision;
    CivilianGeometryCache(CivilianGraphCapture.Source source,LongSupplier clock){this.source=source;this.clock=clock;revision=source.revision();}
    public long revision(){long current=source.revision();if(revision!=current){entries.clear();revision=current;}return revision;}
    private Entry read(Tile tile){revision();long now=clock.getAsLong();Entry e=entries.get(tile);
        if(e==null||now-e.at()>2_000_000_000L){var edges=source.edges(tile);e=new Entry(edges==null?null:List.copyOf(edges),source.complete(tile),now);entries.remove(tile);entries.put(tile,e);
            if(entries.size()>512)entries.remove(entries.keySet().iterator().next());}return e;}
    public List<Edge> edges(Tile tile){return read(tile).edges();}
    public boolean complete(Tile tile){return read(tile).complete();}
    public double danger(Tile tile){return 0;}
}
