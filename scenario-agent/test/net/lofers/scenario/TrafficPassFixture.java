package net.lofers.scenario;

import java.util.*;

final class TrafficPassFixture {
    static void check(boolean result,String why){ScenarioFixture.check(result,why);}
    static TrafficPassReservations.Request request(long id,long generation,double since,int tile,boolean clear){
        return new TrafficPassReservations.Request(new TrafficPassReservations.Owner(id,generation),since,Set.of(new ProbeRoute.Tile(tile,0)),clear);
    }
    static void run(){
        for(boolean reversed:List.of(false,true)){
            var arbiter=new TrafficPassReservations();var a=request(1,1,1,0,true);var b=request(2,1,1,0,true);
            var grants=arbiter.resolve(reversed?List.of(b,a):List.of(a,b),2);
            check(grants.size()==1&&grants.getFirst().owner().resident()==1,"Same gap was granted twice or callback order decided winner");
            var first=grants.getFirst();check(arbiter.enter(first,2.1),"Valid winner could not enter");
            check(arbiter.resolve(List.of(b),5).isEmpty(),"Expired but occupied corridor granted to opposing car");
            check(!arbiter.mayProceed(first,5),"Expired driver kept driving permission");
            check(!arbiter.release(first,false,5),"Missing observation cleared physical occupancy");
            check(arbiter.resolve(List.of(request(1,2,1,0,true)),5).isEmpty(),"Replacement generation stole occupied lease");
            check(!arbiter.release(new TrafficPassReservations.Grant(new TrafficPassReservations.Owner(1,2),first.token()),true,5),"Wrong generation released lease");
            check(arbiter.release(first,true,5),"Confirmed exit could not release lease");
            check(arbiter.resolve(List.of(b),5).size()==1,"Waiting opponent starved after winner left");
        }
        var arbiter=new TrafficPassReservations();
        var oldest=request(3,1,0,0,true);var newer=request(1,1,1,0,true);
        check(arbiter.resolve(List.of(newer,oldest),2).getFirst().owner().resident()==3,"Stable-ID tie break overrode queue age");
        check(arbiter.resolve(List.of(newer),5).size()==1,"Unused lease did not expire");
        check(arbiter.resolve(List.of(request(4,1,1,1,false)),5).isEmpty(),"Unverified corridor admitted");
        check(arbiter.resolve(List.of(request(4,1,1,1,true)),5).size()==1,"Disjoint passing space blocked");
        var recovery=new TrafficPassReservations();var req=request(8,1,0,0,true);
        var lease=recovery.resolve(List.of(req),0).getFirst();check(recovery.enter(lease,.1),"Could not enter recovery fixture");
        check(!recovery.mayProceed(lease,3),"Lost heartbeat retained permission");
        check(recovery.resolve(List.of(req),3).getFirst().equals(lease)&&recovery.mayProceed(lease,3),"Fresh unchanged owner cannot reconcile and resume occupied corridor");
        System.out.println("Passing reservation fixtures passed: same-gap arbitration, queue priority, disjoint progress, generations, unentered expiry and occupied-expiry hold; native multi-car adapter remains gated");
    }
}
