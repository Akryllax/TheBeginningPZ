package net.lofers.scenario;
import java.util.*;
import static net.lofers.scenario.CivilianNavigation.*;
final class ContinuousMovementFixture {
    static final CivilianPool.Token TOKEN=new CivilianPool.Token("epoch",4096,1,"resident",1);
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Result route(long revision,int from,int to){var steps=new ArrayList<Step>();for(int i=from;i<=to;i++)steps.add(new Step(new Tile(i,0,0),Action.WALK));return new Result(new Key(TOKEN,revision,1),Status.FOUND,steps,0,0);}
    static final class Port implements CivilianTraversal.Port {
        double x=.5,allowance;int stops,frames;boolean blocked,forecastStopping;CivilianTraversal.ActionKey active;Step target;
        public void forecast(Result r,int n,Result c,boolean stopping){forecastStopping=stopping;}
        public void frame(){allowance=.4;frames++;}
        public boolean at(Step s){return Math.abs(x-s.at().x()-.5)<.001;}
        public boolean supported(Action a){return a==Action.WALK;}
        public boolean valid(Step a,Step b){return !blocked;}
        public void begin(CivilianTraversal.ActionKey key,Step from,Step to){check(active==null,"overlapping native action");active=key;target=to;}
        public CivilianTraversal.Outcome observe(CivilianTraversal.ActionKey key){
            check(key.equals(active),"wrong execution identity");if(blocked)return CivilianTraversal.Outcome.BLOCKED;
            double d=Math.min(allowance,target.at().x()+.5-x);x+=d;allowance-=d;
            if(at(target)){active=null;return CivilianTraversal.Outcome.COMPLETE;}return CivilianTraversal.Outcome.WORKING;
        }
        public void stop(CivilianTraversal.ActionKey key){stops++;active=null;}
    }
    static void tick(CivilianTraversal t){t.tick(t.view().key());}
    static void run(){
        var drift=new ReplicaDriftGate();
        check(!drift.sample(1_000_000_000L,true,4),"brief divergence is not failure");
        check(drift.sample(1_800_000_000L,true,4),"sustained replica lag fails visual machine gate");
        check(!drift.sample(2_000_000_000L,false,9),"missing samples are not positions");
        check(!drift.sample(3_000_000_000L,true,.5)&&drift.peak==4,"recovered replica and peak evidence");
        var envelope=new MovementTransition();long at=1_000_000_000L;envelope.begin(at,0);
        check(envelope.speed(at,true,4.2)==0,"run starts from rest");
        check(envelope.speed(at+175_000_000L,true,4.2)>0&&envelope.speed(at+175_000_000L,true,4.2)<4.2,"run accelerates during native blend");
        check(envelope.speed(at+350_000_000L,true,4.2)==4.2,"run reaches cruise after blend");
        envelope.begin(at+500_000_000L,4.2);
        check(envelope.speed(at+500_000_000L,false,2.18)<=2.18,"walk wire gait cannot retain running translation");
        check(envelope.speed(at+600_000_000L,false,2.18)<=2.18,"walk blend remains within native clip speed");
        envelope.reset();envelope.begin(at+1_000_000_000L,0);
        check(envelope.speed(at+1_000_000_000L,true,4.2)==0,"reaction restart has no stale cruise speed");
        var p=new Port();var t=new CivilianTraversal(p);t.start(route(1,0,4));tick(t);
        check(t.replaceAtBoundary(route(2,2,12)),"queue future replacement");check(t.buffered()<=8,"bounded combined window");
        for(int i=0;i<40&&t.view().phase()==CivilianTraversal.Phase.RUNNING;i++){double before=p.x;tick(t);check(p.x-before<=.400001,"handoff doubled frame allowance");}
        check(t.view().phase()==CivilianTraversal.Phase.COMPLETE&&p.stops==1,"no intermediate stop during handoff");
        check(Math.abs(p.x-8.5)<.001&&t.completedEdges()==8,"continuous bounded route reached expected end");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,4));tick(t);t.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);
        t.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);check(p.stops==0&&t.buffered()==1,"soft cancellation retains only current edge");
        tick(t);check(p.forecastStopping,"prediction respects deferred stop boundary");
        while(t.view().phase()==CivilianTraversal.Phase.RUNNING)tick(t);
        check(t.view().phase()==CivilianTraversal.Phase.CANCELLED&&Math.abs(p.x-1.5)<.001&&p.stops==1,"soft stop at boundary");
        check(!t.replaceAtBoundary(route(2,1,4)),"cancelled traversal rejects late candidate");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,4));tick(t);double before=p.x;p.blocked=true;tick(t);
        check(p.x==before&&t.view().phase()==CivilianTraversal.Phase.BLOCKED&&p.stops==1,"blocker overrides soft movement");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,4));tick(t);before=p.x;t.cancel();tick(t);check(p.x==before&&p.stops==1,"hard cancel immediate");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,4));tick(t);t.cancel(CivilianTraversal.Cancellation.DEFER_TO_BOUNDARY);
        check(t.replaceAtBoundary(route(2,1,5)),"new safe action replaces deferred cancellation at its boundary");
        while(t.view().phase()==CivilianTraversal.Phase.RUNNING)tick(t);check(p.stops==1&&p.x==5.5,"replacement never publishes cancellation stop");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,2));
        check(t.replaceAtBoundary(route(2,0,0)),"zero-edge terminal handoff");tick(t);
        check(t.view().phase()==CivilianTraversal.Phase.COMPLETE&&p.stops==1,"terminal handoff stops native gait");
        p=new Port();t=new CivilianTraversal(p);t.start(route(1,0,2));tick(t);
        var stale=route(2,1,3);var other=new CivilianPool.Token("epoch",4096,2,"resident",2);
        check(!t.replaceAtBoundary(new Result(new Key(other,2,1),Status.FOUND,stale.route(),0,0)),"old actor generation cannot replace route");
        double pausedAt=p.x;t.pause();check(p.x==pausedAt&&t.view().phase()==CivilianTraversal.Phase.RUNNING,"reaction pause retains route");
        while(t.view().phase()==CivilianTraversal.Phase.RUNNING)tick(t);
        check(!t.replaceAtBoundary(route(2,2,4))&&p.stops==1,"late continuation cannot restart completed movement");
        var q=new ResidentReceipts();var keys=new ArrayList<ResidentReceipts.Key>();
        for(int i=0;i<64;i++){var k=new ResidentReceipts.Key("r"+i,1,1,"a");keys.add(k);check(q.reserve(k),"receipt reservation");}
        check(!q.reserve(new ResidentReceipts.Key("overflow",1,1,"a")),"receipt backpressure");
        q.finish(keys.getFirst(),"completed","done");var receipt=q.peek();q.finish(keys.getFirst(),"completed","done");
        check(receipt.equals(q.peek())&&q.size()==64,"retry keeps immutable receipt until ack");q.acknowledge(receipt);q.acknowledge(receipt);
        check(q.size()==63&&q.pending()==0,"duplicate ack harmless");
        System.out.println("Continuous movement and bounded outcome journal fixtures passed");
    }
}
