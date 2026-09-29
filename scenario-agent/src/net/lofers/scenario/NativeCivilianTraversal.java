package net.lofers.scenario;

import java.util.*;
import zombie.characters.IsoPlayer;
import static net.lofers.scenario.CivilianNavigation.*;

/** Candidate walking/door executor. Full traversal is deliberately reported incomplete, not emulated. */
public final class NativeCivilianTraversal implements CivilianTraversal.Port {
    private final CivilianPool.Token token;private final IsoPlayer body;private final NativeCivilianActors actors;
    private CivilianTraversal.ActionKey active;private Step from,to;private long lastFrame;private double remaining,speed=1.45;
    private boolean doorOpened,cautious;private double predictionDistance;
    public NativeCivilianTraversal(CivilianPool.Token token,IsoPlayer body,NativeCivilianActors actors){this.token=token;this.body=body;this.actors=actors;}
    public void speed(double value){if(value!=1.45&&value!=2.6)throw new IllegalArgumentException("unqualified_animation_speed");speed=value;}
    public void frame(){actors.beginMovementFrame(body);actors.locomotionTick(body);long now=System.nanoTime();remaining=lastFrame==0?0:Math.min(.25,(now-lastFrame)/1e9);lastFrame=now;}
    public void endFrame(){
        actors.endMovementFrame(body,Math.min(predictionDistance,actors.commandedSpeed(body)*.6));
    }
    public void forecast(Result route,int next,Result continuation,boolean stopping){
        predictionDistance=0;cautious=active!=null&&to.action()==Action.DOOR;if(active==null)return;
        double dx=to.at().x()+.5-body.getX(),dy=to.at().y()+.5-body.getY(),length=Math.hypot(dx,dy);
        if(length<.001)return;dx/=length;dy/=length;
        var previous=new Tile((int)Math.floor(body.getX()),(int)Math.floor(body.getY()),(int)Math.floor(body.getZ()));
        for(var part:new Result[]{route,continuation}){
            if(part==null)break;
            int start=part==route?next:1;
            for(int i=start;i<(stopping?Math.min(start+1,part.route().size()):part.route().size());i++){
                var target=part.route().get(i);double tx=target.at().x()+.5-body.getX(),ty=target.at().y()+.5-body.getY();
                double along=tx*dx+ty*dy;
                if(target.action()!=Action.WALK||target.at().z()!=(int)body.getZ()||along<0||Math.abs(tx*dy-ty*dx)>.02){cautious=predictionDistance<2;return;}
                // Prediction may cross collinear tile boundaries, never an unverified turn/door.
                if(!previous.equals(target.at())){var edge=NativeCivilianGeometry.classify(body,previous,target.at());if(!edge.permitted()||edge.action()!=Action.WALK){cautious=true;return;}}
                predictionDistance=along;previous=target.at();
                if(stopping||along>=actors.commandedSpeed(body)*.6)return;
            }
        }
    }
    public void pause(){lastFrame=System.nanoTime();remaining=0;actors.stop(body);actors.locomotionTick(body);}
    public boolean at(Step step){return Math.floor(body.getZ())==step.at().z()&&Math.hypot(body.getX()-step.at().x()-.5,body.getY()-step.at().y()-.5)<.01;}
    public boolean supported(Action action){return NativeCivilianGeometry.executableActions().contains(action);}
    public boolean valid(Step from,Step to){var p=NativeCivilianGeometry.classify(body,from.at(),to.at());return p.permitted()&&(p.action()==to.action()||to.action()==Action.DOOR&&p.action()==Action.WALK);}
    public void begin(CivilianTraversal.ActionKey key,Step from,Step to) {
        GameHooks.ownThread();
        if(!key.route().actor().equals(token))throw new IllegalArgumentException("stale_actor");
        if(key.equals(active))return;
        if(active!=null)throw new IllegalStateException("native_action_owned");
        if(!supported(to.action()))throw new IllegalStateException("native_traversal_execution_pending:"+to.action());
        active=key;this.from=from;this.to=to;doorOpened=to.action()!=Action.DOOR;
    }
    public CivilianTraversal.Outcome observe(CivilianTraversal.ActionKey key) {
        GameHooks.ownThread();if(!key.equals(active))throw new IllegalArgumentException("stale_action");
        if(body.isDead())return CivilianTraversal.Outcome.DEAD;
        if(!doorOpened) {
            var current=NativeCivilianGeometry.classify(body,from.at(),to.at());
            doorOpened=current.permitted()&&current.action()==Action.WALK||NativeCivilianGeometry.openDoor(body,from.at(),to.at());
            if(!doorOpened)return CivilianTraversal.Outcome.BLOCKED;
        }
        if(Math.floor(body.getZ())!=to.at().z())return CivilianTraversal.Outcome.BLOCKED;
        float tx=to.at().x()+.5f,ty=to.at().y()+.5f;
        if(Math.hypot(body.getX()-tx,body.getY()-ty)<.01){active=null;return CivilianTraversal.Outcome.COMPLETE;}
        if(remaining<=0)return CivilianTraversal.Outcome.WORKING;
        double effective=actors.movementSpeed(body,speed>1.6&&!cautious&&to.action()==Action.WALK?NativeCivilianActors.Gait.RUN:NativeCivilianActors.Gait.WALK);
        double distance=Math.hypot(body.getX()-tx,body.getY()-ty),dt=Math.min(remaining,distance/Math.max(.001,effective));
        remaining=Math.max(0,remaining-dt);
        if(!actors.walk(token,body,tx,ty,effective,dt))return CivilianTraversal.Outcome.BLOCKED;
        if(Math.hypot(body.getX()-tx,body.getY()-ty)<.01){active=null;return CivilianTraversal.Outcome.COMPLETE;}
        return CivilianTraversal.Outcome.WORKING;
    }
    public void stop(CivilianTraversal.ActionKey key){GameHooks.ownThread();if(active!=null&&!key.equals(active))throw new IllegalArgumentException("stale_action");actors.stop(body);active=null;}
    public static Map<Action,String> qualification() {
        return Map.of(Action.WALK,"candidate: prior one-Actor movement evidence; new pool unvalidated",
            Action.DOOR,"candidate: native open and collision; unvalidated",
            Action.STAIRS,"blocked: native placement/state advancement gate",
            Action.WINDOW,"blocked: native state/animation execution and exactly-once effects gate",
            Action.LOW_FENCE,"blocked: native state/animation execution and exactly-once effects gate",
            Action.HIGH_WALL,"blocked: server outcome calculation uses local-player branches");
    }
}
