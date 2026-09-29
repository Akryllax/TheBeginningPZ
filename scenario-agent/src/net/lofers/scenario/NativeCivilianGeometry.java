package net.lofers.scenario;

import java.util.*;
import zombie.characters.IsoPlayer;
import zombie.iso.*;
import zombie.iso.objects.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.network.ServerMap;
import zombie.pathfind.PolygonalMap2;
import static net.lofers.scenario.CivilianNavigation.*;

/** Native path classification, not proof that the connectionless Actor can execute a climb. */
public final class NativeCivilianGeometry {
    public record Passage(Action action,boolean permitted,String reason) { }
    public record Inspection(List<Step> steps,String reason) {public Inspection{steps=List.copyOf(steps);}}
    public static Passage classify(IsoPlayer actor,Tile from,Tile to) {
        GameHooks.ownThread();
        IsoGridSquare a=ServerMap.instance.getGridSquare(from.x(),from.y(),from.z());
        IsoGridSquare b=ServerMap.instance.getGridSquare(to.x(),to.y(),to.z());
        if(a==null||b==null)return new Passage(null,false,"unloaded");
        int dx=to.x()-from.x(),dy=to.y()-from.y();
        if(Math.abs(dx)>1||Math.abs(dy)>1||Math.abs(to.z()-from.z())>1)return new Passage(null,false,"nonlocal_edge");
        if(from.z()!=to.z()||a.HasStairs()||b.HasStairs()) {
            // Only a returned native path can attest this transition. Local graph capture must not invent stair edges.
            return new Passage(Action.STAIRS,a.HasStairs()||b.HasStairs(),"native_stair_execution_pending");
        }
        if(dx!=0&&dy!=0) {
            Tile h=new Tile(to.x(),from.y(),from.z()),v=new Tile(from.x(),to.y(),from.z());
            for(Tile[] pair:List.of(new Tile[]{from,h},new Tile[]{from,v},new Tile[]{h,to},new Tile[]{v,to})) {
                Passage edge=classify(actor,pair[0],pair[1]);
                if(!edge.permitted()||edge.action()!=Action.WALK)return new Passage(null,false,"corner_blocked");
            }
        } else {
            IsoObject door=a.getDoorTo(b);
            if(door instanceof IsoDoor d&&!d.isOpen())return new Passage(Action.DOOR,d.couldBeOpen(actor),"door");
            if(door instanceof IsoThumpable d&&d.isDoor()&&!d.open)return new Passage(Action.DOOR,d.couldBeOpen(actor),"door");
            IsoWindow window=a.getWindowTo(b);
            if(window!=null)return new Passage(Action.WINDOW,window.canClimbThrough(actor)&&(!window.isSmashed()||window.isGlassRemoved()),"window");
            IsoThumpable thumpable=a.getWindowThumpableTo(b);
            if(thumpable!=null)return new Passage(Action.WINDOW,!thumpable.isBarricaded(),"window");
            if(a.getWindowFrameTo(b)!=null)return new Passage(Action.WINDOW,true,"window_frame");
            IsoGridSquare boundary=dx>0||dy>0?b:a;
            if(dx!=0&&boundary.has(IsoFlagType.HoppableW)||dy!=0&&boundary.has(IsoFlagType.HoppableN))return new Passage(Action.LOW_FENCE,true,"fence");
            if(dx!=0&&(boundary.has(IsoFlagType.TallHoppableW)||boundary.has(IsoFlagType.WallW)||boundary.has(IsoFlagType.WallWTrans))
                ||dy!=0&&(boundary.has(IsoFlagType.TallHoppableN)||boundary.has(IsoFlagType.WallN)||boundary.has(IsoFlagType.WallNTrans)))
                return new Passage(Action.HIGH_WALL,true,"native_wall_outcome_pending");
        }
        boolean clear=b.TreatAsSolidFloor()&&!PolygonalMap2.instance.lineClearCollide(from.x()+.5f,from.y()+.5f,to.x()+.5f,to.y()+.5f,from.z(),null,false,true);
        return new Passage(Action.WALK,clear,clear?"":"blocked");
    }
    /** Expands solver segments at half-tile spacing so a door between distant nodes is not missed. */
    public static Inspection inspect(IsoPlayer actor,List<CivilianPathRequests.Point> points) {
        GameHooks.ownThread();if(points.isEmpty()||points.size()>MAX_ROUTE)return new Inspection(List.of(),"route_limit");
        var result=new ArrayList<Step>();Tile previous=null;int samples=0;
        for(int i=0;i<points.size();i++) {
            var a=points.get(Math.max(0,i-1));var b=points.get(i);
            int n=Math.max(1,(int)Math.ceil(Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2))*2));
            if(samples+n>512)return new Inspection(List.of(),"inspection_budget");samples+=n;
            for(int j=0;j<=n;j++) {
                float t=(float)j/n;Tile tile=new Tile((int)Math.floor(a.x()+(b.x()-a.x())*t),(int)Math.floor(a.y()+(b.y()-a.y())*t),(int)Math.floor(a.z()+(b.z()-a.z())*t));
                if(tile.equals(previous))continue;
                if(previous==null)result.add(new Step(tile,Action.WALK));
                else {
                    Passage p=classify(actor,previous,tile);if(!p.permitted())return new Inspection(List.of(),p.reason());
                    result.add(new Step(tile,p.action()));
                }
                previous=tile;if(result.size()>MAX_ROUTE)return new Inspection(List.of(),"route_limit");
            }
        }
        return new Inspection(result,"");
    }
    public static Set<Action> executableActions(){return Set.of(Action.WALK,Action.DOOR);}
    /** The caller must still walk through the open doorway using ordinary collision checks. */
    public static boolean openDoor(IsoPlayer actor,Tile from,Tile to) {
        GameHooks.ownThread();
        if(!ServerActors.isActor(actor)||actor.getSquare()==null||actor.getSquare().x!=from.x()||actor.getSquare().y!=from.y()||actor.getSquare().z!=from.z())return false;
        Passage passage=classify(actor,from,to);if(passage.action()!=Action.DOOR||!passage.permitted())return false;
        IsoGridSquare target=ServerMap.instance.getGridSquare(to.x(),to.y(),to.z());IsoObject object=actor.getSquare().getDoorTo(target);
        if(object instanceof IsoDoor door){if(!door.isOpen())door.ToggleDoor(actor);return door.isOpen();}
        if(object instanceof IsoThumpable door){if(!door.open)door.ToggleDoor(actor);return door.open;}
        return false;
    }
    private NativeCivilianGeometry() { }
}
