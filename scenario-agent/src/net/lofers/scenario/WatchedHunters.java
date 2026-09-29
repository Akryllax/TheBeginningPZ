package net.lofers.scenario;

import java.util.*;
import zombie.VirtualZombieManager;
import zombie.characters.*;
import zombie.core.raknet.UdpConnection;
import zombie.iso.*;
import zombie.network.*;
import zombie.network.packets.INetworkPacket;
import zombie.popman.NetworkZombiePacker;
import zombie.Lua.LuaManager;
import se.krka.kahlua.vm.KahluaTable;

/** Disposable chase workload: stock client-owned zombies, explicit targets, no transform driver. */
final class WatchedHunters {
    private static final Set<UdpConnection> owners=Collections.newSetFromMap(new IdentityHashMap<>());
    static boolean neighborPlayer(UdpConnection c){return c!=null&&owners.contains(c);}
    private static final class Hunter {
        final IsoZombie body;final short id;final int target;final float sx,sy;
        IsoGridSquare oldSquare;long lastControl;int controls;boolean targeted,seen,removed;
        double travel;float x,y;
        Hunter(IsoZombie z,int target){body=z;id=z.getOnlineID();this.target=target;sx=x=z.getX();sy=y=z.getY();}
    }
    final int count;private final List<Hunter> hunters=new ArrayList<>();
    int peakOwned,realTargets,controlCursor;private final KahluaTable ids=LuaManager.platform.newTable();
    WatchedHunters(int count){if(count!=0&&count!=1&&count!=16&&count!=128)throw new IllegalArgumentException("hunter_count");this.count=count;}
    boolean ready(){return hunters.size()==count;}
    boolean readyToSimulate(){return ready()&&hunters.stream().allMatch(h->h.body.getOwner()!=null);}
    KahluaTable ids(){return ids;}
    void spawnOne(CivilianFormation formation){
        if(ready())return;int i=hunters.size();float x=formation.x(i%8),y=formation.startY(0)-8-(i/8)*2;
        spawnAt(x,y,i);
    }
    int spawned(){return hunters.size();}
    List<IsoZombie> bodies(){return hunters.stream().map(h->h.body).toList();}
    int chasing(){int n=0;for(Hunter h:hunters)if(h.targeted&&Math.hypot(h.body.getX()-h.sx,h.body.getY()-h.sy)>=5)n++;return n;}
    int moving(){int n=0;for(Hunter h:hunters)if(h.targeted&&h.travel>=.6)n++;return n;}
    double closestGap(List<IsoPlayer> actors){double gap=Double.POSITIVE_INFINITY;for(Hunter h:hunters)gap=Math.min(gap,Math.hypot(h.body.getX()-actors.get(h.target).getX(),h.body.getY()-actors.get(h.target).getY()));return gap;}
    double medianMovingGap(List<IsoPlayer> actors){
        double[] gaps=new double[hunters.size()];int n=0;
        for(Hunter h:hunters)if(h.targeted&&h.travel>=.6)gaps[n++]=Math.hypot(h.body.getX()-actors.get(h.target).getX(),h.body.getY()-actors.get(h.target).getY());
        if(n==0)return 16;Arrays.sort(gaps,0,n);return gaps[n/2];
    }
    IsoZombie body(){return hunters.isEmpty()?null:hunters.getFirst().body;}
    double travel(){return hunters.isEmpty()?0:hunters.getFirst().travel;}
    boolean targeted(){return !hunters.isEmpty()&&hunters.getFirst().targeted;}
    void spawnAt(float x,float y,int i){spawnAt(x,y,i,1);}
    void spawnAt(float x,float y,int i,int direction){
        if(ready())return;
        IsoGridSquare square=ServerMap.instance.getGridSquare((int)x,(int)y,0);
        require(square!=null&&square.TreatAsSolidFloor()&&square.isFree(false),"hunter_spawn_square_unavailable");
        var factory=VirtualZombieManager.instance;var saved=new ArrayList<>(factory.choices);IsoZombie z;
        factory.choices.clear();factory.choices.add(square);
        try {z=GameHooks.withSpawnPermit(()->factory.createRealZombieAlways(direction<0?IsoDirections.N:IsoDirections.S,false));}
        finally {factory.choices.clear();factory.choices.addAll(saved);}
        require(z!=null,"hunter_spawn_refused");
        Hunter h=new Hunter(z,i);hunters.add(h); // own exact reference before any further setup
        require(h.id>=0,"hunter_network_identity_missing");
        z.setUseless(false);z.setTarget(null);z.doFastShambler();
        ids.rawset((double)hunters.size(),(double)h.id);
    }
    void tick(List<IsoPlayer> actors,long now){
        owners.clear();int owned=0,controlBudget=16,start=controlCursor;
        for(int offset=0;offset<hunters.size();offset++){
            int index=(start+offset)%hunters.size();Hunter h=hunters.get(index);
            IsoZombie z=h.body;if(h.removed)continue;
            require(!z.isDead(),"hunter_died_owned:"+h.id);
            require(z.getSquare()!=null,"hunter_unloaded:"+h.id);
            h.travel+=Math.hypot(z.getX()-h.x,z.getY()-h.y);h.x=z.getX();h.y=z.getY();
            IsoPlayer target=actors.get(h.target);
            require(!target.isDead(),"civilian_died_owned");
            double gap=Math.hypot(z.getX()-target.getX(),z.getY()-target.getY());
            // This stress case is pursuit-only. Do not invent a many-body bite/death bridge.
            require(gap>2&&!"attack".equalsIgnoreCase(z.getRealState()),"combat_contact_requires_separate_gate:"+h.id);
            var owner=z.getOwner();if(owner==null)continue;owners.add(owner);owned++;
            if(z.getTarget()==target)h.targeted=true;
            else if(z.getTarget() instanceof IsoPlayer p&&!NativeCivilianActors.owns(p))realTargets++;
            if(controlBudget>0&&now-h.lastControl>=1_000_000_000L){
                controlBudget--;controlCursor=(index+1)%hunters.size();
                INetworkPacket.send(owner,PacketTypes.PacketType.ZombieControl,z,target);h.lastControl=now;h.controls++;
            }
        }
        peakOwned=Math.max(peakOwned,owned);
    }
    void observe(KahluaTable entries){
        if(entries==null)return;
        for(Hunter h:hunters){Object v=entries.rawget((double)h.id);if(v instanceof KahluaTable t&&Boolean.TRUE.equals(t.rawget("on_screen")))h.seen=true;}
    }
    boolean clientClear(KahluaTable entries,boolean absent){return clientClear(entries,absent,false);}
    boolean clientClear(KahluaTable entries,boolean absent,boolean useHidden){
        if(entries==null)return count==0;
        for(Hunter h:hunters){
            Object v=entries.rawget((double)h.id);if(!(v instanceof KahluaTable t))return false;
            if((useHidden?!Boolean.TRUE.equals(t.rawget("hidden")):!Boolean.FALSE.equals(t.rawget("on_screen")))||absent&&!Boolean.FALSE.equals(t.rawget("present")))return false;
        }
        return true;
    }
    boolean farFromPlayers(){return farFromPlayers(72);}
    boolean farFromPlayers(double distance){
        for(Hunter h:hunters)for(IsoPlayer p:GameServer.Players)
            if(p.getSquare()==null||Math.hypot(p.getX()-h.body.getX(),p.getY()-h.body.getY())<distance)return false;
        return true;
    }
    /** One stock removal per tick, always after caller confirms off-screen clearance. */
    boolean removeOne(){
        for(Hunter h:hunters)if(!h.removed){
            IsoZombie z=h.body;require(!z.isDead(),"hunter_corpse_owned");h.oldSquare=z.getSquare();
            OffscreenZombieLease.release(z);z.setTarget(null);z.setUseless(true);
            if(z.getOnlineID()>=0)NetworkZombiePacker.getInstance().deleteZombie(z);
            z.removeFromWorld();z.removeFromSquare();NetworkZombiePacker.getInstance().setExtraUpdate();h.removed=true;return false;
        }
        var cell=IsoWorld.instance.currentCell;
        for(Hunter h:hunters){var z=h.body;
            if(h.oldSquare!=null&&h.oldSquare.getMovingObjects().contains(z)||z.getSquare()!=null
                ||cell.getZombieList().contains(z)||cell.getObjectList().contains(z)||cell.getAddList().contains(z)||cell.getRemoveList().contains(z)
                ||z.getOnlineID()>=0||ServerMap.instance.zombieMap.get(h.id)==z)return false;
        }
        owners.clear();return true;
    }
    void verifyChase(){
        for(Hunter h:hunters)require(h.targeted&&h.seen&&h.travel>=5,"hunter_chase_not_observed:"+h.id+":target="+h.targeted+":seen="+h.seen+":travel="+h.travel);
    }
    String summary(){
        int targeted=0,seen=0,controls=0;double min=Double.POSITIVE_INFINITY;
        for(Hunter h:hunters){if(h.targeted)targeted++;if(h.seen)seen++;controls+=h.controls;min=Math.min(min,h.travel);}
        return "{\"count\":"+count+",\"spawned\":"+hunters.size()+",\"peak_owned\":"+peakOwned+",\"targeted\":"+targeted+",\"seen\":"+seen+",\"controls\":"+controls+",\"real_player_targets\":"+realTargets+",\"chasing\":"+chasing()+",\"minimum_travel\":"+(hunters.isEmpty()?0:min)+"}";
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
