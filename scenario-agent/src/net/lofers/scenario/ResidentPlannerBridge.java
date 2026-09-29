package net.lofers.scenario;
import java.util.*;
import se.krka.kahlua.vm.KahluaTable;
/** Four admitted residents share the existing bounded, asynchronous planner transport.
 * Durable state lives only in AKRResidents; this is a reconstructible observation adapter. */
final class ResidentPlannerBridge {
    static final ResidentReceipts outcomes=new ResidentReceipts();
    private static final Set<NativeResidentController> controllers=new LinkedHashSet<>();
    private static final Map<NativeResidentController,Long> sent=new IdentityHashMap<>();
    static String diagnostic="not_started";private static long inFlight,submitted;
    static boolean pending(){return inFlight!=0||incoming!=null;}private static PrimitiveWrite incoming;
    static void register(NativeResidentController c){if(controllers.size()>=4)throw new IllegalStateException("resident_planner_budget");controllers.add(c);}
    static void unregister(NativeResidentController c){controllers.remove(c);sent.remove(c);}
    static boolean active(){return !controllers.isEmpty()||inFlight!=0||incoming!=null;}
    private static double number(KahluaTable t,String k){Object v=t.rawget(k);return v instanceof Number n?n.doubleValue():0;}
    private static Map<Object,Object> point(KahluaTable t){return new LinkedHashMap<>(Map.of("x",number(t,"x"),"y",number(t,"y"),"z",number(t,"z")));}
    static void tick(PlannerTransport transport,long now,java.util.function.LongSupplier sequence){
        diagnostic=transport.health+":"+transport.lastError+" flight="+inFlight;
        var response=transport.ready.getAndSet(null);
        if(response!=null&&response.request()==inFlight){incoming=new PrimitiveWrite(response.plans());inFlight=0;}
        if(incoming!=null&&incoming.step(now+300_000L)){
            if(incoming.result.rawget("plans") instanceof KahluaTable plans)
                for(int i=1;i<=4;i++)if(plans.rawget((double)i) instanceof KahluaTable plan)
                    for(var c:controllers)if(Objects.equals(c.logical().rawget("id"),plan.rawget("resident_id")))c.acceptPlan(plan);
            incoming=null;
        }
        if(inFlight!=0){if(now-submitted<3_000_000_000L)return;inFlight=0;}
        if(!transport.health.equals("ready"))return;
        Map<Object,Object> residents=new LinkedHashMap<>();
        for(var c:controllers){
            var r=c.logical();if(!(r.rawget("goalPlan") instanceof KahluaTable g)||!Boolean.TRUE.equals(r.rawget("planEnabled")))continue;
            if(now-sent.getOrDefault(c,Long.MIN_VALUE/2)<1_000_000_000L)continue;
            var row=new LinkedHashMap<Object,Object>();row.put("id",r.rawget("id"));row.put("generation",r.rawget("generation"));
            row.put("revision",number(g,"facts"));row.put("plan_revision",number(g,"revision"));row.put("position",c.actualPosition());
            row.put("home",point((KahluaTable)g.rawget("home")));row.put("health",c.health());row.put("materialized",true);row.put("infection","healthy");
            var execution=new LinkedHashMap<Object,Object>();
            execution.put("routine_enabled",true);execution.put("routine_phase",number(g,"phase"));
            execution.put("activity",point((KahluaTable)g.rawget("activity")));execution.put("goal",c.goal());
            execution.put("action_id",c.actionId());execution.put("status",c.executionStatus());execution.put("interrupt_reason",c.interruptReason());
            execution.put("route_revision",(double)c.routeRevision());execution.put("completed_edges",(double)c.completedEdges());execution.put("buffered_edges",(double)c.bufferedSteps());
            row.put("execution",execution);
            residents.put((double)residents.size()+1,row);sent.put(c,now);
        }
        if(residents.isEmpty())return;
        inFlight=sequence.getAsLong();submitted=now;
        var batch=new LinkedHashMap<Object,Object>();batch.put("revision",inFlight);batch.put("online_players",1d);batch.put("phase","calm");batch.put("residents",residents);
        transport.pending.set(new PlannerTransport.Sample(inFlight,now,batch));
    }
}
