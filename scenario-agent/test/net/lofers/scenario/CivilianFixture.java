package net.lofers.scenario;

import java.util.*;
import java.util.function.Consumer;
import static net.lofers.scenario.CivilianNavigation.*;

/** Actual pool/search/action controllers with fault-injected engine boundaries. No game initialized. */
final class CivilianFixture {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void rejects(Runnable r){try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException|IllegalStateException expected){}}
    static CivilianPool.Snapshot snapshot(int value){return CivilianPool.Snapshot.capture(240,"fixture",new byte[]{(byte)value,42,99});}
    static final class Body {boolean dead,visible,world=true,network=true;int possessions;}
    static final class Port implements CivilianPool.Port<Body> {
        int created,removals,handoffs,releases;boolean defer,deferAfterOwnership,setupFailure,removeFailure,mapReplaced,beforeOwnershipFailure,deathReleaseFails;final Map<Integer,Body> ids=new HashMap<>();
        public void materialize(CivilianPool.Token token,CivilianPool.Snapshot saved,Consumer<Body> own) throws Exception {
            if(defer)throw new CivilianPool.AdmissionDeferred("stale");
            if(beforeOwnershipFailure)throw new IllegalStateException("spawn_visible_or_unknown");
            Body b=new Body();own.accept(b);created++;b.possessions=saved==null?7:saved.bytes()[0];ids.put(token.slot(),b);
            if(deferAfterOwnership)throw new CivilianPool.AdmissionDeferred("too_late");
            if(setupFailure)throw new IllegalStateException("after_constructor");
        }
        public boolean dead(Body b){return b.dead;}
        public boolean visibleOrUnknown(Body b){return b.visible;}
        public void stop(Body b){}
        public CivilianPool.Snapshot capture(Body b){return snapshot(b.possessions);}
        public void retire(CivilianPool.Token t,Body b){removals++;ids.remove(t.slot(),b);b.network=false;if(removeFailure)throw new IllegalStateException("partial_remove");b.world=false;}
        public CivilianPool.Removal verify(CivilianPool.Token t,Body b){return new CivilianPool.Removal(!b.world,!b.network);}
        public String handoffDeath(CivilianPool.Token t,Body b){check(b.dead,"handoff must keep deceased body");handoffs++;return "corpse-"+t.generation();}
        public boolean releaseDeath(CivilianPool.Token t,Body b,String corpse){
            if(deathReleaseFails)return false;
            check(b.dead&&corpse.equals("corpse-"+t.generation()),"release follows matching corpse");
            releases++;ids.remove(t.slot(),b);return true;
        }
    }
    static void deathLifecycle() {
        Port p=new Port();var pool=new CivilianPool<Body>("epoch",p,1);
        var token=pool.reserve("original",1,true,null);pool.tick(1,0);
        Body body=pool.body(token);body.dead=true;
        pool.tick(2,100_000_000L);check(p.handoffs==0,"death handoff waits for native transition");
        pool.tick(3,600_000_000L);check(p.handoffs==1&&pool.pollDead()==null,"corpse receipt waits for quiet interval");
        check(pool.reserve("replacement",1,true,null)==null,"corpse owns slot before receipt");
        pool.tick(4,1_700_000_000L);var receipt=pool.pollDead();
        check(receipt!=null&&receipt.token().equals(token),"matching death receipt");
        check(!pool.acknowledgeDead(token,"wrong-corpse"),"wrong corpse cannot release Actor");
        p.deathReleaseFails=true;check(!pool.acknowledgeDead(token,receipt.corpseId())&&pool.occupied()==1,"failed release retains Actor");
        p.deathReleaseFails=false;check(pool.acknowledgeDead(token,receipt.corpseId())&&p.releases==1&&pool.occupied()==0,"persisted death releases Actor");
        var replacement=pool.reserve("replacement",1,true,null);
        check(replacement!=null&&replacement.generation()>token.generation(),"reused slot advances generation");
        check(!pool.acknowledgeDead(token,receipt.corpseId())&&!pool.acceptIntent(token,1),"old death and intent tokens fenced");
    }
    static void escapeCapabilities(){
        var token=new CivilianPool.Token("e",4096,1,"r",1);var start=new Tile(0,0,0);
        var one=new Tile(1,0,0);var two=new Tile(2,0,0);var three=new Tile(3,0,0);
        var graph=new Graph(1,Map.of(start,List.of(new Edge(one,Action.HIGH_WALL,1)),one,List.of(new Edge(two,Action.WALK,1)),two,List.of(new Edge(three,Action.WALK,1)),three,List.of()),Map.of());
        var blocked=new CivilianEscape(new Key(token,1,1),graph,start,List.of(new Tile(-2,0,0)),Set.of(Action.WALK,Action.DOOR));
        for(int i=0;i<100&&blocked.result().status()==Status.PENDING;i++)blocked.advance(32,1);
        check(blocked.result().status()==Status.NO_PATH,"unqualified_climb_not_repeated_as_flee_route");
        var capable=new CivilianEscape(new Key(token,1,1),graph,start,List.of(new Tile(-2,0,0)));
        for(int i=0;i<100&&capable.result().status()==Status.PENDING;i++)capable.advance(32,1);
        check(capable.result().status()==Status.FOUND,"generic_escape_can_keep_future_climbing_capability");
    }
    static void deathDuringCancel(){
        Port port=new Port();var pool=new CivilianPool<Body>("epoch",port,1);
        var token=pool.reserve("cancel-dies",1,true,null);pool.tick(1,0);
        check(pool.retire(token),"retirement_admitted");pool.body(token).dead=true;pool.tick(2,100);
        check(pool.views().getFirst().state()==CivilianPool.State.DYING&&port.removals==0,"death_before_native_removal_handed_off");
        pool.tick(3,600_000_000);pool.tick(4,1_700_000_000L);
        check(pool.pollRetired()==null&&pool.pollDead()!=null&&pool.occupied()==1,"death_not_living_snapshot");
        check(!pool.acceptIntent(token,1),"dying_no_new_actions");
        Port partial=new Port();partial.removeFailure=true;var uncertain=new CivilianPool<Body>("epoch",partial,1);
        var t=uncertain.reserve("partial",1,true,null);uncertain.tick(1,0);uncertain.retire(t);uncertain.tick(2,100);
        uncertain.body(t).dead=true;check(!uncertain.reconcile(t)&&uncertain.occupied()==1,"death_after_partial_remove_retained");
    }
    static void pool() {
        rejects(()->new CivilianPool<Body>("epoch",new Port(),0));
        rejects(()->new CivilianPool<Body>("epoch",new Port(),65));
        for(int stressCount:new int[]{32,64}) {
        var stressPort=new Port();var stress=new CivilianPool<Body>("epoch",stressPort,stressCount);
        var stressTokens=new ArrayList<CivilianPool.Token>();
        for(int i=0;i<stressCount;i++)stressTokens.add(stress.reserve("stress"+i,1,true,null));
        check(stress.reserve("overflow",1,true,null)==null,"stress capacity cap");
        for(int i=1;i<=stressCount;i++){stress.tick(i,i);check(stressPort.created==i,"stress assignments remain one per tick");}
        for(var token:stressTokens)check(stress.retire(token),"stress retirement admitted");
        stress.tick(stressCount+1,100);stress.tick(stressCount+2,2_000_000_000L);
        int receipts=0;while(stress.pollRetired()!=null)receipts++;
        check(receipts==stressCount&&stress.occupied()==0,"all retirement receipts retained");
        var formation=new CivilianFormation(stressCount);var positions=new HashSet<String>();
        for(int i=0;i<stressCount;i++) {
            check(formation.endY(i)-formation.startY(i)==150,"each formation member travels 150 tiles");
            check(Math.hypot(formation.x(i)-formation.viewX(),formation.startY(i)-10060.5)>72,"start clear of viewer");
            check(Math.hypot(formation.x(i)-formation.viewX(),formation.endY(i)-10060.5)>72,"end clear of viewer");
            positions.add(formation.x(i)+","+formation.startY(i));
        }
        check(positions.size()==stressCount&&formation.columns==8&&formation.rows==stressCount/8,"unique grid positions");
        }
        rejects(()->new CivilianFormation(33));
        Port deferred=new Port();deferred.defer=true;var waiting=new CivilianPool<Body>("epoch",deferred);
        var waitingToken=waiting.reserve("delayed",1,true,null);waiting.tick(1,0);
        check(deferred.created==0&&waiting.occupied()==1&&waiting.views().getFirst().state()==CivilianPool.State.RESERVED,"stale admission retains reservation without body or failure");
        deferred.defer=false;waiting.tick(2,1);check(waiting.body(waitingToken)!=null&&deferred.created==1,"fresh admission resumes same reservation");
        deferred.deferAfterOwnership=true;var tooLate=waiting.reserve("owned",1,true,null);waiting.tick(3,2);
        check(waiting.body(tooLate)!=null&&waiting.views().get(1).state()==CivilianPool.State.UNRESOLVED,"defer after ownership never abandons body");
        Port p=new Port();var pool=new CivilianPool<Body>("epoch",p,4);var tokens=new ArrayList<CivilianPool.Token>();
        for(int i=0;i<4;i++)tokens.add(pool.reserve("r"+i,1,true,null));
        check(pool.reserve("overflow",1,true,null)==null,"four-slot cap");
        pool.tick(1,0);pool.tick(1,0);check(p.created==1,"one spawn per tick");
        for(int i=2;i<=4;i++)pool.tick(i,0);
        check(p.created==4&&pool.occupied()==4,"four materialized");
        var first=tokens.getFirst();check(pool.acceptIntent(first,2)&&!pool.acceptIntent(first,1),"intent revisions");
        Body body=pool.body(first);body.visible=true;check(!pool.retire(first),"visible actor retained");body.visible=false;
        body.possessions=63;
        // Replacement map entry survives retirement of our exact reference.
        Body replacement=new Body();p.ids.put(first.slot(),replacement);
        check(pool.retire(first),"retire");pool.tick(5,0);check(pool.occupied()==4,"quiet interval");
        pool.tick(6,1_000_000_000L);check(pool.occupied()==3&&p.ids.get(first.slot())==replacement,"verified reference-only cleanup");
        var retired=pool.pollRetired();check(retired.snapshot().bytes()[0]==63,"inventory captured");
        var newToken=pool.reserve("other",1,false,retired.snapshot());pool.tick(7,2_000_000_000L);
        check(newToken.slot()==first.slot()&&newToken.generation()>first.generation(),"slot generation");
        check(!pool.current(first,2)&&pool.body(newToken).possessions==63,"old identity fenced and state restored");
        byte[] bytes=retired.snapshot().bytes();bytes[0]=0;check(retired.snapshot().bytes()[0]==63,"immutable body");
        rejects(()->pool.reserve("known",1,false,null));
        rejects(()->new CivilianPool.Snapshot(2,240,"fixture",new byte[]{1},"bad"));
        rejects(()->new CivilianPool.Snapshot(1,240,"fixture",new byte[]{1},"bad"));
        pool.body(newToken).dead=true;check(!pool.retire(newToken)&&pool.occupied()==4,"corpse never discarded");
        var bad=new Port();bad.setupFailure=true;var failed=new CivilianPool<Body>("epoch",bad);
        var t=failed.reserve("failure",1,true,null);failed.tick(1,0);
        check(failed.views().getFirst().state()==CivilianPool.State.UNRESOLVED&&failed.body(t)!=null,"constructed before failure");
        check(failed.reconcile(t),"partial construction recovery");failed.tick(2,0);failed.tick(3,2_000_000_000L);
        check(failed.occupied()==0,"partial body removed");
        bad=new Port();bad.beforeOwnershipFailure=true;failed=new CivilianPool<>("epoch",bad);
        t=failed.reserve("refused",1,true,null);failed.tick(1,0);
        check(failed.body(t)==null&&failed.occupied()==1&&failed.views().getFirst().state()==CivilianPool.State.UNRESOLVED,
            "pre-ownership refusal remains unresolved, not silently freed");
        check(failed.views().getFirst().reason().endsWith(":spawn_visible_or_unknown"),"materialization refusal detail retained");
        bad=new Port();bad.removeFailure=true;failed=new CivilianPool<>("epoch",bad);
        t=failed.reserve("failure",1,true,null);failed.tick(1,0);failed.retire(t);failed.tick(2,0);
        check(failed.occupied()==1&&failed.views().getFirst().state()==CivilianPool.State.UNRESOLVED,"partial removal holds capacity");
        bad.removeFailure=false;check(failed.reconcile(t),"explicit removal retry");failed.tick(3,2_000_000_000L);check(failed.occupied()==0,"retry verified");
        Port soak=new Port();var repeat=new CivilianPool<Body>("epoch",soak);
        for(int i=0;i<2000;i++) {
            t=repeat.reserve("r"+i,1,true,null);repeat.tick(i*3L,i*3_000_000_000L);
            check(repeat.retire(t),"soak retire");repeat.tick(i*3L+1,i*3_000_000_000L);
            repeat.tick(i*3L+2,i*3_000_000_000L+1_000_000_000L);check(repeat.pollRetired()!=null&&repeat.occupied()==0,"soak no leak");
        }
    }
    static Graph grid(int width,int height,Set<Tile> blocked) {
        Map<Tile,List<Edge>> graph=new HashMap<>();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(!blocked.contains(new Tile(x,y,0)))graph.put(new Tile(x,y,0),new ArrayList<>());
        for(Tile a:graph.keySet())for(int[] d:new int[][]{{1,0},{0,1},{-1,0},{0,-1}}) {
            Tile b=new Tile(a.x()+d[0],a.y()+d[1],0);if(graph.containsKey(b))graph.get(a).add(new Edge(b,Action.WALK,1));
        }
        return new Graph(1,graph,Map.of());
    }
    static Key key(int slot){return new Key(new CivilianPool.Token("epoch",4096+slot,1,"r"+slot,1),1,1);}
    static Result solve(Graph graph,Tile start,Tile goal,int budget) {
        Search s=new Search(key(0),graph,start,goal,EnumSet.allOf(Action.class));
        while(s.result().status()==Status.PENDING)s.advance(budget,1);return s.result();
    }
    static void navigation() {
        Graph graph=grid(7,7,Set.of(new Tile(3,1,0),new Tile(3,2,0),new Tile(3,3,0),new Tile(3,4,0),new Tile(3,5,0)));
        var a=solve(graph,new Tile(1,3,0),new Tile(5,3,0),1);var b=solve(graph,new Tile(1,3,0),new Tile(5,3,0),128);
        check(a.status()==Status.FOUND&&a.route().equals(b.route())&&a.cost()>4,"deterministic wall detour");
        var queue=new CivilianNavigation.Queue();
        for(int i=0;i<4;i++)check(queue.submit(new Search(key(i),graph,new Tile(1,3,0),new Tile(5,3,0),EnumSet.allOf(Action.class))),"admit");
        for(int i=0;i<10;i++)check(queue.tick(1)<=128,"global search cap");
        for(int i=0;i<4;i++)check(queue.poll(key(i).actor()).status()==Status.FOUND,"no starvation");
        Search stale=new Search(key(0),graph,new Tile(1,3,0),new Tile(5,3,0),EnumSet.allOf(Action.class));stale.advance(1,2);check(stale.result().status()==Status.STALE,"stale geometry");
        Search cancel=new Search(key(0),graph,new Tile(1,3,0),new Tile(5,3,0),EnumSet.allOf(Action.class));cancel.cancel();check(cancel.result().status()==Status.CANCELLED,"cancel");
        check(solve(graph,new Tile(-1,0,0),new Tile(5,3,0),128).status()==Status.NO_PATH,"unknown blocked");
        Tile low=new Tile(0,0,0),high=new Tile(0,0,1);
        Graph stairs=new Graph(1,Map.of(low,List.of(new Edge(high,Action.STAIRS,2)),high,List.of()),Map.of());
        check(solve(stairs,low,high,128).route().getLast().action()==Action.STAIRS,"floor transition");
        rejects(()->new Graph(1,Map.of(low,List.of(new Edge(new Tile(1,1,0),Action.WALK,2)),new Tile(1,1,0),List.of()),Map.of()));
        var capture=new CivilianGraphCapture(new CivilianGraphCapture.Source(){public long revision(){return 1;}public List<Edge> edges(Tile at){return graph.edges().get(at);}public double danger(Tile at){return 0;}},new Tile(1,3,0));
        while(!capture.complete())check(capture.advance(3)<=3,"capture budget");check(capture.result().edges().size()==graph.edges().size(),"capture reachable region");
        var missing=new CivilianGraphCapture(new CivilianGraphCapture.Source(){public long revision(){return 1;}public List<Edge> edges(Tile at){return null;}public double danger(Tile at){return 0;}},low);
        missing.advance(1);check(missing.complete()&&missing.unknown(),"missing geometry cannot prove trapped");
        var escape=new CivilianEscape(key(0),graph,new Tile(1,3,0),List.of(new Tile(0,3,0)));
        while(escape.result().status()==Status.PENDING)escape.advance(128,1);
        check(escape.result().status()==Status.FOUND,"reachable flee");
        var nativePoints=escape.result().route().stream().map(s->new CivilianPathRequests.Point(s.at().x()+.5f,s.at().y()+.5f,s.at().z(),0)).toList();
        check(escape.acceptNative(nativePoints),"safe native route accepted");
        check(!escape.acceptNative(List.of(new CivilianPathRequests.Point(0,3,0,0))),"unsafe native route rejected");
        Graph large=grid(64,64,Set.of());
        check(solve(large,new Tile(0,0,0),new Tile(63,63,0),128).status()==Status.BUDGET_EXHAUSTED,"hard per-job cap");
        var islands=grid(16,2,Set.of(new Tile(6,0,0),new Tile(6,1,0)));
        var reachableEscape=new CivilianEscape(key(0),islands,new Tile(1,0,0),List.of(new Tile(0,0,0)));
        for(int i=0;i<100&&reachableEscape.result().status()==Status.PENDING;i++)check(reachableEscape.advance(32,1)<=32,"escape work cap");
        check(reachableEscape.result().status()==Status.FOUND&&reachableEscape.result().route().getLast().at().x()<6,"unreachable far goals do not hide reachable escape");
    }
    static void requests() {
        final List<Consumer<List<CivilianPathRequests.Point>>> callbacks=new ArrayList<>();int[] requests={0},cancels={0};boolean[] valid={true};
        var driver=new CivilianPathRequests.Driver<String>() {
            public void request(String b,CivilianPathRequests.Point a,CivilianPathRequests.Point z,Consumer<List<CivilianPathRequests.Point>> ok,Runnable no){requests[0]++;callbacks.add(ok);}
            public void cancel(String b){cancels[0]++;}
        };
        var q=new CivilianPathRequests<String>(driver,k->valid[0]);var p=new CivilianPathRequests.Point(1,1,0,13);
        for(int i=0;i<4;i++)check(q.submit(key(i),"body"+i,p,p,0),"queue submit");
        q.tick(1,0);q.tick(1,0);check(requests[0]==1,"one native submit per tick");q.tick(2,1);q.tick(3,2);check(requests[0]==2&&q.outstanding()==2,"two outstanding");
        var mutable=new ArrayList<>(List.of(p));callbacks.getFirst().accept(mutable);mutable.clear();
        check(q.poll(key(0).actor()).points().getFirst().flags()==13,"copy pooled result");
        q.cancel(key(1).actor());callbacks.get(1).accept(List.of(p));check(q.outstanding()==0&&q.poll(key(1).actor())==null,"late cancellation result ignored");
        q.tick(4,3);valid[0]=false;callbacks.get(2).accept(List.of(p));check(q.poll(key(2).actor()).status()==Status.STALE,"late action fenced");
    }
    static void traversal() {
        int[] begins={0},stops={0};boolean[] clear={true},supported={true};
        var port=new CivilianTraversal.Port(){public boolean supported(Action a){return supported[0];}public boolean valid(Step a,Step b){return clear[0];}
            public void begin(CivilianTraversal.ActionKey key,Step a,Step b){begins[0]++;}public CivilianTraversal.Outcome observe(CivilianTraversal.ActionKey key){return CivilianTraversal.Outcome.COMPLETE;}
            public void stop(CivilianTraversal.ActionKey key){stops[0]++;}};
        var steps=new ArrayList<Step>();int x=0;for(Action a:Action.values())steps.add(new Step(new Tile(x++,0,0),a));
        var route=new Result(key(0),Status.FOUND,steps,1,1);var t=new CivilianTraversal(port);t.start(route);
        for(int i=0;i<10;i++)t.tick(key(0));check(begins[0]==5&&t.view().phase()==CivilianTraversal.Phase.COMPLETE,"each action exactly once");
        t.start(route);clear[0]=false;t.tick(key(0));check(t.view().phase()==CivilianTraversal.Phase.BLOCKED&&begins[0]==5,"changed obstacle blocks before effects");
        t.start(route);clear[0]=true;supported[0]=false;t.tick(key(0));check(t.view().reason().startsWith("native_traversal_unqualified"),"no silent traversal fallback");
    }
    static void perception() {
        int[] maxOffset={0};
        var source=new CivilianPerception.Source(){public CivilianPerception.Page read(Tile tile,int offset,int max){
            maxOffset[0]=Math.max(maxOffset[0],offset);
            if(tile.equals(new Tile(0,0,0)))return new CivilianPerception.Page(true,
                offset==72?List.of(new CivilianPerception.Threat("beyond-first-64",tile,true,false)):List.of(),offset<72);
            return new CivilianPerception.Page(true,List.of(),false);
        }};
        var scan=new CivilianPerception(source);CivilianPerception.Observation last=null;
        for(int i=0;i<12;i++)last=scan.sample(new Tile(0,0,0),64,i*50_000_000L);
        check(maxOffset[0]>=72&&last.known()&&last.threats().stream().anyMatch(t->t.id().equals("beyond-first-64")),"cursor reaches crowded square tail");
        last=scan.sample(new Tile(50,50,0),1,4_000_000_000L);check(!last.known()&&last.threats().isEmpty(),"changed area is unknown, not empty-safe");
    }
    static void run(){pool();deathLifecycle();deathDuringCancel();escapeCapabilities();navigation();requests();traversal();perception();System.out.println("Civilian pool, death lifecycle, navigation, stress and traversal contracts passed (detached)");}
}
