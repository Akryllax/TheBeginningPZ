"""Scenario authority/lifecycle tests. Mocks do not certify real multiplayer physics."""

from pathlib import Path

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
LUA = ROOT / "mods/AKRStoryteller/42/media/lua"


@pytest.fixture
def lua():
    vm = LuaRuntime(unpack_returned_tuples=True)
    vm.globals().package.path = ";".join(
        str(LUA / side / "?.lua") for side in ("shared", "server", "client")
    )
    vm.execute("M=require 'AKRScenario/Model';C=require 'AKRScenario/Config'")
    return vm


def test_sources_parse(lua):
    for path in LUA.glob("*/AKRScenario/*.lua"):
        lua.execute("assert(loadstring(...))", path.read_text())


RESIDENT = """
s=M.new('world','uuid',100)
home=M.addPlace(s,'home','home',{x=100,y=100,z=0})
M.addPlace(s,'work','work',{x=120,y=100,z=0})
r=M.resident(s,home,'worker')
function plan(kind)
    return {resident_id=r.id,based_on_revision=r.revision,generation=r.generation,
        plan_revision=r.plan_revision+1,goal='test',actions={{id='a'..(r.plan_revision+1),
        kind=kind or 'WALK',target={x=120,y=100,z=0},duration_hours=0}}}
end
"""


def test_calm_does_not_advance_collapse_and_start_is_one_shot(lua):
    lua.execute(
        RESIDENT
        + """
        M.clock(s,200,1)
        assert(s.phase=='calm' and s.elapsed_hours==0)
        assert(M.start(s,200))
        M.clock(s,200.05,1)
        assert(s.elapsed_hours>0 and s.phase=='first_cases')
        local ok,why=M.start(s,201)
        assert(not ok and why=='already_started' and s.started_hour==200)
    """
    )


def test_paused_and_empty_time_no_catchup(lua):
    lua.execute(
        RESIDENT
        + """
        M.start(s,100);s.status='paused'
        M.clock(s,500,1);assert(s.elapsed_hours==0)
        s.status='running';M.clock(s,1000,0);assert(s.elapsed_hours==0)
        M.clock(s,1000.05,1);assert(s.elapsed_hours<0.06)
    """
    )


def test_seven_day_phase_clock_and_persistent_identity(lua):
    lua.execute(
        RESIDENT
        + """
        local id=r.id;M.start(s,100)
        local expected={'first_cases','concern','emergency','disruption','evacuation','collapse','aftermath','survival'}
        for i=1,8 do s.elapsed_hours=(i-1)*24;assert(M.phase(s)==expected[i]) end
        r.materialized=true;r.generation=1
        r.materialized=false;r.generation=2
        assert(s.residents[id]==r and r.home_id=='home' and r.work_id=='work')
    """
    )


def test_contact_only_after_start_and_unloaded_is_not_dead(lua):
    lua.execute(
        RESIDENT
        + """
        assert(not M.expose(s,r,'contact'))
        M.start(s,100);assert(M.expose(s,r,'contact'));assert(not M.expose(s,r,'duplicate'))
        s.elapsed_hours=40;r.materialized=true;r.lifecycle='unresolved'
        M.advanceResident(s,r,0.1)
        assert(r.infection=='turning' and r.lifecycle=='unresolved')
        r.materialized=false;r.lifecycle='abstract';M.advanceResident(s,r,0.1)
        assert(r.lifecycle=='zombie')
    """
    )


def test_plan_staleness_and_invalid_routes_fail_closed(lua):
    lua.execute(
        RESIDENT
        + """
        local p=plan();p.based_on_revision=0
        local ok,why=M.acceptPlan(s,p);assert(not ok and why=='stale_facts')
        p=plan();p.actions[1].kind='EXEC_LUA';assert(not M.acceptPlan(s,p))
        p=plan('DRIVE');p.actions[1].route={{x=0/0,y=2}};assert(not M.acceptPlan(s,p))
        p=plan();p.actions[1].duration_hours=math.huge;assert(not M.acceptPlan(s,p))
        p=plan();assert(M.acceptPlan(s,p));assert(not M.acceptPlan(s,p))
    """
    )


def test_owner_epoch_and_generation_gate_committed_effects(lua):
    lua.execute(
        RESIDENT
        + """
        M.acceptPlan(s,plan('EAT'));r.owner_id=10;r.lease_epoch=7;r.generation=2
        local a={action_id=M.action(r).id,generation=2,plan_revision=r.plan_revision,lease_epoch=7,state='completed'}
        assert(not M.receipt(s,r,a,11));assert(r.hunger==0.15)
        M.lease(r,11,10)
        assert(not M.receipt(s,r,a,10));assert(not M.receipt(s,r,a,11))
        a.lease_epoch=r.lease_epoch
        assert(M.receipt(s,r,a,11));assert(r.hunger==0 and not r.has_food)
        assert(not M.receipt(s,r,a,11))
    """
    )


def test_receipt_ring_and_physical_counts_include_uncertain_members(lua):
    lua.execute(
        RESIDENT
        + """
        C.maxReceipts=2
        for i=1,4 do M.acceptPlan(s,plan('WAIT'));local a=M.action(r)
            assert(M.receipt(s,r,{action_id=a.id,generation=r.generation,plan_revision=r.plan_revision,
                lease_epoch=r.lease_epoch,state='completed'},r.owner_id))
        end
        assert(#s.receipt_order==2)
        r.lifecycle='unresolved';r.has_vehicle=true
        s.vehicles.car={status='parked'}
        local p,n,v=M.count(s);assert(p==1 and n==1 and v==0)
        r.in_vehicle=true;s.vehicles.car.status='unresolved'
        p,n,v=M.count(s);assert(p==0 and n==1 and v==1)
    """
    )


def test_population_place_and_event_bounds(lua):
    lua.execute(
        RESIDENT
        + """
        C.maxResidents=3;C.maxPlaces=3;C.maxEvents=2
        M.resident(s,home);M.resident(s,home);assert(not M.resident(s,home))
        M.addPlace(s,'extra','shop',{x=1,y=2});assert(not M.addPlace(s,'overflow','shop',{x=1,y=2}))
        for i=1,5 do M.event(s,'test',tostring(i)) end
        assert(#s.order==3 and #s.place_order==3 and #s.events==2 and s.events[1].reason=='4')
    """
    )


SERVER = """
SandboxVars={AKRScenario={Enabled=true}}
isClient=function() return false end
handlers={};Events=setmetatable({},{__index=function(self,k)
    local e={Add=function(f) handlers[k]=handlers[k] or {};table.insert(handlers[k],f) end}
    rawset(self,k,e);return e end})
function fire(event,...)
    for _,f in ipairs(handlers[event] or {}) do f(...) end
end
clock=100;hour=100
getTimestampMs=function() return clock*1000 end
getGameTime=function() return {getWorldAgeHours=function() return hour end,getTimeOfDay=function() return 9 end} end
getServerName=function() return 'test-world' end
uuid=0;getRandomUUID=function() uuid=uuid+1;return 'uuid'..uuid end
store={};ModData={getOrCreate=function(k) store[k]=store[k] or {};return store[k] end}
messages={};sendServerCommand=function(...) table.insert(messages,{...}) end
function makePlayer(id,access)
    return {getOnlineID=function() return id end,getAccessLevel=function() return access end,
      isDead=function() return false end,getX=function() return 100 end,getY=function() return 100 end}
end
admin=makePlayer(10,'admin');guest=makePlayer(11,'none');online={admin,guest}
getOnlinePlayers=function() return {size=function() return #online end,get=function(self,i) return online[i+1] end} end
package.loaded['AKRScenario/MapIndex']={places={}}
package.loaded['AKRScenario/World']={discover=function() end,
    candidate=function() return {x=160,y=160,z=0} end,valid=function() return true end}
spawned=0
package.loaded['AKRScenario/Native']={actors={},ready=function() return true end,
    spawn=function(s,r) spawned=spawned+1;r.materialized=true;r.lifecycle='active';return true end,
    reconcile=function() return nil,'unloaded' end,remove=function() return false,'unloaded' end}
AKRNative={versionReady=function() return true end}
Server=require 'AKRScenario/Server'
fire('OnInitGlobalModData')
root=store.AKRScenario;s=root.state
home=M.addPlace(s,'home','home',{x=100,y=100,z=0});r=M.resident(s,home,'worker')
root.bridgeIn={health='ready',guard_ready=true,server_epoch='native-epoch'}
function cmd(p,name,args) fire('OnClientCommand','AKRScenario',name,p,args or {}) end
function tick() clock=clock+0.2;hour=hour+0.001;fire('OnTick') end
function hello(p) cmd(p,'hello',{helper=true,version=C.version,integration='lua-pedestrian',
 manifest=C.clientManifest,callback_ready=true,target_shield=true}) end
hello(admin);hello(guest)
tick()
root.bridgeIn.observation_revision=root.bridgeOut.revision;root.bridgeIn.plans={}
tick()
"""


def test_admin_activation_and_java_epoch_authority(lua):
    lua.execute(
        SERVER
        + """
        assert(Server.epoch=='native-epoch')
        cmd(guest,'start');assert(s.status=='calm')
        cmd(admin,'start');assert(s.status=='running')
        cmd(admin,'pause');assert(s.status=='paused')
        cmd(admin,'step',{hours=2});assert(s.elapsed_hours>=2)
        cmd(admin,'advance',{phase='response'});assert(s.phase=='emergency')
    """
    )


def test_all_observers_must_approve_spawn_visibility(lua):
    lua.execute(
        SERVER
        + """
        Server.pending=nil
        cmd(admin,'test_spawn',{role='worker'})
        local p=Server.pending;assert(p)
        cmd(admin,'visibility',{epoch=Server.epoch,id=p.id,safe=true})
        tick();assert(spawned==0)
        cmd(guest,'visibility',{epoch=Server.epoch,id=p.id,safe=false})
        tick();assert(spawned==0 and not Server.pending)
        cmd(admin,'test_spawn',{role='worker'});p=Server.pending
        cmd(admin,'visibility',{epoch=Server.epoch,id=p.id,safe=true})
        cmd(guest,'visibility',{epoch=Server.epoch,id=p.id,safe=true})
        tick();assert(spawned==1)
    """
    )


def test_missing_helper_blocks_materialization_and_start(lua):
    lua.execute(
        SERVER
        + """
        Server.pending=nil
        cmd(guest,'hello',{helper=false,version=C.version})
        cmd(admin,'test_spawn');assert(not Server.pending and spawned==0)
        cmd(admin,'start');assert(not s.started_hour)
    """
    )


def test_flattened_plan_batch_is_consumed_once(lua):
    lua.execute(
        SERVER
        + """
        root.bridgeIn.plans={{resident_id=r.id,based_on_revision=r.revision,generation=r.generation,
          plan_revision=1,goal='go_home',actions={{id='unique',kind=1,target=r.home,duration_hours=0.5}}}}
        clock=clock+1.1;tick()
        root.bridgeIn.observation_revision=root.bridgeOut.revision
        tick();assert(r.plan_revision==1 and M.action(r).id=='unique')
        local events=#s.events;tick();assert(#s.events==events)
    """
    )


def test_epoch_change_revokes_previous_lease(lua):
    lua.execute(
        SERVER
        + """
        r.owner_id=10;local old=r.lease_epoch
        root.bridgeIn.server_epoch='new-boot';tick()
        assert(Server.epoch=='new-boot' and r.owner_id==-1 and r.lease_epoch>old)
    """
    )


def test_disabled_scenario_does_not_create_world_state(lua):
    lua.execute(SERVER.replace("Enabled=true", "Enabled=false").split("root=store.AKRScenario")[0])
    lua.execute("assert(store.AKRScenario==nil and Server.state==nil)")


NATIVE = (
    """
"""
    + RESIDENT
    + """
clusters={};transmissions=0
TransmitBanditCluster=function(id) transmissions=transmissions+1 end
originalTransmit=TransmitBanditCluster
GetBanditClusterData=function(id) clusters[id]=clusters[id] or {};return clusters[id] end
clans={};profiles={}
BanditCustom={ClanGet=function(id) return clans[id] end,
 ClanCreate=function(id) local c={general={},spawn={}};clans[id]=c;return c end,
 GetById=function(id) return profiles[id] end,
 Create=function(id) local p={general={}};profiles[id]=p;return p end}
health=1.8;dead=false;md={};removed=0
z={getSquare=function() return {} end,getX=function() return 100 end,getY=function() return 100 end,
 getZ=function() return 0 end,getHealth=function() return health end,setHealth=function(self,h) health=h end,
 getModData=function() return md end,isDead=function() return dead end,getVehicle=function() return nil end}
package.loaded['AKRScenario/World']={find=function(id) if id==42 then return z end end}
factoryMode='normal'
BanditServer={Spawner={Individual=function(p,args)
 local profile=profiles[args.bid]
 assert(profile.cid=='akr-scenario-civilians-v1' and profile.general.bid==args.bid)
 if factoryMode=='throws' then error('factory failed') end
 if factoryMode=='unknown' then return end
 GetBanditClusterData(42)[42]={bid=args.bid,fullname=args.fullname,health=1.8}
 TransmitBanditCluster(42)
end}}
AKRNative={versionReady=function() return true end,
 spawn=function(fn,args) return pcall(fn,args) end,
 bind=function(actor,id,generation,lease,owner) md.AKRScenario={id=id,generation=generation};return true end,
 removeOwned=function(actor) assert(md.AKRScenario.id==r.id);removed=removed+1;return true end}
N=require 'AKRScenario/Native'
p={getOnlineID=function() return 10 end}
"""
)


def test_native_receipt_binds_the_created_entity_and_preserves_injury(lua):
    lua.execute(
        NATIVE
        + """
        r.health=60
        assert(N.spawn(s,r,r.position,p,false))
        assert(r.materialized and r.outfit_id==42 and r.max_native_health==1.8)
        assert(TransmitBanditCluster==originalTransmit and math.abs(health-1.08)<0.0001)
        health=0.9;local actor,state=N.reconcile(r)
        assert(actor==z and state=='active' and math.abs(r.health-50)<0.0001)
        assert(N.remove(s,r));assert(removed==1 and r.lifecycle=='abstract')
        assert(clusters[42][42]==nil and N.actors[r.id]==nil)
    """
    )


def test_failed_native_factory_restores_hook_and_reserves_uncertain_actor(lua):
    lua.execute(
        NATIVE
        + """
        factoryMode='throws'
        assert(not N.spawn(s,r,r.position,p,false))
        assert(TransmitBanditCluster==originalTransmit and r.lifecycle=='unresolved')
        assert(r.outfit_id==nil and removed==0)
        local ped,total=M.count(s);assert(total==1)
    """
    )


def test_unknown_native_entity_is_never_removed(lua):
    lua.execute(
        NATIVE
        + """
        r.outfit_id=500;r.materialized=true
        local ok,why=N.remove(s,r)
        assert(not ok and why=='entity_unloaded' and removed==0 and r.lifecycle=='unresolved')
    """
    )


CLIENT = """
SandboxVars={AKRScenario={Enabled=true}}
handlers={};Events=setmetatable({},{__index=function(self,k)
 local e={Add=function(f) handlers[k]=handlers[k] or {};table.insert(handlers[k],f) end}
 rawset(self,k,e);return e end})
function fire(event,...)
 for _,f in ipairs(handlers[event] or {}) do f(...) end
end
clock=100;hour=100;sent={};variables={};md={};health=1.8;remote=true
walks=0;cancels=0;seats=0;controls=0;car=nil
getTimestampMs=function() return clock*1000 end
getGameTime=function() return {getWorldAgeHours=function() return hour end,getMinutesPerDay=function() return 60 end} end
p={getOnlineID=function() return 10 end}
getSpecificPlayer=function() return p end
sendClientCommand=function(player,module,cmd,args) sent[#sent+1]={cmd=cmd,args=args} end
getVehicleById=function() return car end
GetBanditClusterData=function(id) return {[42]={health=1.8}} end
BanditBrain={Update=function() end};Bandit={ApplyVisuals=function(z) z:setHealth(1.8) end}
path={cancel=function() cancels=cancels+1 end,pathToLocation=function() walks=walks+1 end,
 update=function() return 1 end}
BehaviorResult={Failed=2}
z={getPersistentOutfitID=function() return 42 end,getModData=function() return md end,
 getHealth=function() return health end,setHealth=function(self,h) health=h end,
 getX=function() return 100 end,getY=function() return 100 end,getZ=function() return 0 end,
 getVariableBoolean=function(self,k) return variables[k]==true end,
 setVariable=function(self,k,v) variables[k]=v end,setNoTeeth=function() end,setWalkType=function() end,
 setBumpType=function(self,k) variables.bump=k end,setUseless=function() end,setTarget=function() end,
 getDescriptor=function() return {setVoicePrefix=function() end} end,
 getPathFindBehavior2=function() return path end,setPath2=function() end,
 isRemoteZombie=function() return remote end,getOwnerPlayer=function() return p end,
 getVehicle=function() return z.currentVehicle end}
AKRNative=nil
package.loaded['AKRScenario/ClientGate']={ready=function() return true end,manifest=C.clientManifest}
getCell=function() return {getGridSquare=function() return nil end} end
L=require 'AKRScenario/Runtime'
r={id='resident',outfit_id=42,name='Test Resident',role='worker',generation=1,
 lifecycle='active',owner_id=10,lease_epoch=1,lease_until=200,plan_revision=1,health=50,
 infection='healthy',position={x=100,y=100,z=0},action={id='action1',kind='WAIT',target={x=100,y=100,z=0},duration_hours=0}}
function snapshot()
 fire('OnServerCommand','AKRScenario','residents',{epoch='epoch',server_seconds=clock,residents={r}})
end
function tick() clock=clock+0.2;hour=hour+0.001;fire('OnZombieUpdate',z) end
snapshot()
"""


def test_replica_displays_activity_but_never_executes_receipts(lua):
    lua.execute(
        CLIENT
        + """
        tick();tick()
        assert(variables.AKRAction=='WAIT' and #sent==0 and walks==0 and controls==0)
        assert(math.abs(health-0.9)<0.0001)
        remote=false;tick()
        assert(#sent==1 and sent[1].cmd=='receipt' and sent[1].args.lease_epoch==1)
        local before=#sent;r.lease_until=99;tick();assert(#sent==before)
    """
    )


def test_ordinary_client_rejects_unverified_vehicle_actions(lua):
    lua.execute(
        CLIENT
        + """
        remote=false;car={};r.vehicle_id='4';r.in_vehicle=true
        r.action.kind='DRIVE';snapshot();tick()
        assert(seats==0 and controls==0 and AKRNative==nil)
        assert(#sent==1 and sent[1].args.reason=='vehicle_execution_unverified')
    """
    )


def test_authoritative_snapshot_removes_stale_client_binding(lua):
    lua.execute(
        CLIENT
        + """
        tick()
        fire('OnServerCommand','AKRScenario','residents',{epoch='epoch',server_seconds=clock,residents={}})
        assert(L.residents.resident==nil and L.outfits[42]==nil and md.AKRScenario==nil)
        fire('OnServerCommand','AKRScenario','terminal',{id='resident',lifecycle='abstract'})
    """
    )


def test_stale_admin_revision_cannot_advance_scenario(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start',{expected_revision=0});assert(s.status=='running')
        local h=s.elapsed_hours
        cmd(admin,'advance',{phase='survival',expected_revision=0})
        assert(s.elapsed_hours==h and Server.status().revision==1)
    """
    )


GATE = """
SandboxVars={AKRScenario={Enabled=true}}
isServer=function() return false end
clock=100;getTimestampMs=function() return clock*1000 end
handlers={};Events=setmetatable({},{__index=function(self,k)
 local e={Add=function(f) handlers[k]=handlers[k] or {};table.insert(handlers[k],f) end,
 Remove=function(f) for i,v in ipairs(handlers[k] or {}) do if v==f then table.remove(handlers[k],i);break end end end}
 rawset(self,k,e);return e end})
function fire(event,...) for _,f in ipairs(handlers[event] or {}) do f(...) end end
sources={};lines={}
getFilenameOfClosure=function(f) return sources[f] or 'unrelated.lua' end
getFirstLineOfClosure=function(f) return lines[f] end
instanceof=function(o,k) return type(o)=='table' and o.kind==k end
function zombie(id,managed)
 local md=managed and {AKRScenario={id='resident'}} or {}
 return {kind='IsoZombie',getPersistentOutfitID=function() return id end,getModData=function() return md end}
end
z=zombie(42,true);other=zombie(99,false)
GetBanditClusterData=function() return {} end
AKRScenarioClient={outfits={[42]='resident'}}
BanditZombie={CacheLightB={[42]='managed',[99]='unrelated'},
 CacheLight={[42]='managed',[99]='unrelated'},CacheLightZ={[42]='managed',[99]='unrelated'}}
G=require 'AKRScenario/ClientGate'
function register(event,line,fn)
 sources[fn]='media/lua/client/BanditUpdate.lua';lines[fn]=line;Events[event].Add(fn);return fn
end
function rest()
 register('OnHitZombie',2328,function() end)
 register('OnZombieDead',2395,function() end)
 register('OnDeadBodySpawn',2601,function() end)
end
"""


def test_lua_gate_captures_private_callbacks_and_preserves_remove_identity(lua):
    lua.execute(
        GATE
        + """
        calls=0;local original=register('OnZombieUpdate',2062,function() calls=calls+1 end)
        assert(not G.ready());rest();assert(G.ready())
        fire('OnZombieUpdate',z);assert(calls==0)
        fire('OnZombieUpdate',other);assert(calls==1)
        Events.OnZombieUpdate.Remove(original);assert(not G.ready() and #handlers.OnZombieUpdate==0)
        Events.OnZombieUpdate.Add(original);assert(G.ready() and #handlers.OnZombieUpdate==1)
    """
    )


def test_lua_gate_manifest_change_fails_closed(lua):
    lua.execute(
        GATE
        + """
        register('OnZombieUpdate',2063,function() end);rest()
        assert(not G.ready() and G.error=='callback_manifest_mismatch:OnZombieUpdate')
    """
    )


def test_target_shield_restores_after_nested_callback_failure(lua):
    lua.execute(
        GATE
        + """
        calls=0
        register('OnZombieUpdate',2062,function(actor)
            calls=calls+1
            assert(BanditZombie.CacheLightB[42]==nil and BanditZombie.CacheLight[42]==nil)
            assert(BanditZombie.CacheLightB[99]=='unrelated')
            if calls==1 then fire('OnZombieUpdate',other) else error('upstream failure') end
        end)
        rest();local ok=pcall(fire,'OnZombieUpdate',other)
        assert(not ok and calls==2 and G.depth==0)
        assert(BanditZombie.CacheLightB[42]=='managed' and BanditZombie.CacheLight[42]=='managed')
        assert(BanditZombie.CacheLightZ[42]=='managed')
    """
    )


def test_target_shield_cannot_enqueue_managed_victim_but_unrelated_ai_runs(lua):
    lua.execute(
        GATE
        + """
        victims={}
        register('OnZombieUpdate',2062,function()
            for id in pairs(BanditZombie.CacheLightB) do victims[id]=true end
        end)
        rest();fire('OnZombieUpdate',other)
        assert(victims[99] and not victims[42])
        assert(BanditZombie.CacheLightB[42]=='managed')
    """
    )


def test_server_rejects_old_java_only_client_handshake(lua):
    lua.execute(
        SERVER
        + """
        Server.pending=nil
        cmd(guest,'hello',{helper=true,version=C.version})
        cmd(admin,'start');assert(not s.started_hour)
        cmd(admin,'test_spawn');assert(not Server.pending)
    """
    )


def test_contact_damage_requires_native_attacker_owner_geometry_and_new_receipt(lua):
    lua.execute(
        SERVER
        + """
        r.materialized=true;r.lifecycle='active';r.max_native_health=1;r.health=100
        local wall=false;local square={isSomethingTo=function() return wall end}
        local health=1
        local victim={isDead=function() return false end,getX=function() return 100 end,
          getY=function() return 100 end,getZ=function() return 0 end,getHealth=function() return health end,
          getSquare=function() return square end}
        local attacker={isDead=function() return false end,getX=function() return 100.5 end,
          getY=function() return 100 end,getZ=function() return 0 end,getOnlineID=function() return 50 end,
          getSquare=function() return square end,getVariableBoolean=function() return false end,
          getOwnerPlayer=function() return admin end,isFacingObject=function() return true end}
        local n=package.loaded['AKRScenario/Native'];n.actors[r.id]=victim
        n.health=function(r,h) r.health=h;health=h/100;return true end
        package.loaded['AKRScenario/World'].find=function(outfit) if outfit==500 then return attacker end end
        M.start(s,hour)
        local a={epoch=Server.epoch,resident_id=r.id,generation=r.generation,lease_epoch=r.lease_epoch,
          attacker_id=50,attacker_outfit=500,sequence=1}
        cmd(guest,'contact',a);assert(r.health==100)
        cmd(admin,'contact',a);assert(r.health==94 and r.infection=='exposed' and r.threatened)
        cmd(admin,'contact',a);assert(r.health==94)
        clock=clock+3;cmd(admin,'contact',a);assert(r.health==94)
        a.sequence=2;cmd(admin,'contact',a);assert(r.health==88)
        clock=clock+3;a.sequence=3;wall=true;cmd(admin,'contact',a);assert(r.health==88)
    """
    )


def test_spawn_selection_recovers_when_first_household_has_no_safe_position(lua):
    lua.execute(
        SERVER
        + """
        Server.pending=nil;Server.spawnCursor=1
        local second=M.resident(s,home,'worker')
        package.loaded['AKRScenario/World'].candidate=function(state,resident)
          if resident.id==second.id then return {x=160,y=160,z=0} end
        end
        cmd(admin,'test_spawn',{role='worker'})
        assert(Server.pending and Server.pending.resident==second.id and r.spawn_retry_at>clock)
    """
    )


def test_native_reconcile_discards_stale_unloaded_reference(lua):
    lua.execute(
        NATIVE
        + """
        assert(N.spawn(s,r,r.position,p,false))
        local stale={getSquare=function() return nil end,isDead=function() return false end}
        N.actors[r.id]=stale
        local actual,state=N.reconcile(r)
        assert(actual==z and N.actors[r.id]==z and state=='active')
    """
    )


def test_ordinary_zombie_contact_adapter_uses_attacker_owner_and_bounded_work(lua):
    lua.execute(
        CLIENT
        + """
        tick();sent={};r.owner_id=11 -- victim can have a different native owner
        local attempts=0;local walls=false
        local square={isSomethingTo=function() return walls end}
        z.isDead=function() return false end;z.isProne=function() return false end
        z.getSquare=function() return square end
        getSpecificPlayer=function() return p end
        instanceof=function(o,k) return o==p and k=='IsoPlayer' end
        function attacker(id)
          local md={}
          return {isDead=function() return false end,isRemoteZombie=function() return false end,
            getVariableBoolean=function() return false end,getOwnerPlayer=function() return p end,
            getModData=function() return md end,getX=function() return 100.5 end,getY=function() return 100 end,
            getZ=function() return 0 end,CanSee=function() return true end,getTarget=function() return nil end,
            setNoTeeth=function() end,setVariable=function() end,setTarget=function() end,
            pathToCharacter=function() attempts=attempts+1 end,getSquare=function() return square end,
            isFacingObject=function() return true end,setBumpType=function() end,
            getOnlineID=function() return id end,getPersistentOutfitID=function() return id+1000 end}
        end
        require 'AKRScenario/Contacts'
        for i=1,10 do fire('OnZombieUpdate',attacker(i)) end
        assert(attempts==4 and #sent==4)
        assert(sent[1].cmd=='contact' and sent[1].args.attacker_id==1)
        local remoteAttacker=attacker(30);remoteAttacker.isRemoteZombie=function() return true end
        clock=clock+1;fire('OnZombieUpdate',remoteAttacker);assert(#sent==4)
    """
    )


def test_native_death_confirmation_survives_entity_square_removal(lua):
    lua.execute(
        NATIVE
        + """
        assert(N.spawn(s,r,r.position,p,false))
        dead=true;z.getSquare=function() return nil end
        local actual,state=N.reconcile(r)
        assert(actual==z and state=='dead')
        N.dead(r);assert(N.actors[r.id]==nil and clusters[42][42]==nil and removed==0)
    """
    )


def test_stale_ready_batch_holds_clock_infection_and_pending_materialization(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');Server.pending=nil
        r.infection='exposed';r.exposed_hour=0;s.elapsed_hours=7.99
        r.actions={{id='old',kind='WORK',target=r.home,duration_hours=1}};r.action_index=1
        cmd(admin,'test_spawn',{role='worker'});local probe=Server.pending;assert(probe)
        cmd(admin,'visibility',{epoch=Server.epoch,id=probe.id,safe=true})
        cmd(guest,'visibility',{epoch=Server.epoch,id=probe.id,safe=true})
        local elapsed=s.elapsed_hours;local hunger=r.hunger;local rev=r.revision
        clock=clock+C.workerTimeoutSeconds+0.1;hour=hour+12;tick()
        assert(root.bridgeIn.health=='ready' and not Server.workerReady)
        assert(s.status=='running' and s.elapsed_hours==elapsed and r.hunger==hunger)
        assert(r.infection=='exposed' and not Server.pending and spawned==0)
        assert(not M.action(r) and r.revision>rev)
        assert(root.bridgeOut.paused and Server.status().planning_paused)
        assert(Server.status().planner_reason=='planner_reply_stale')
        assert(Server.status().bridge_health=='planner_reply_stale' and Server.status().bridge_reported_health=='ready')
        assert(Server.status().last_error=='planner_reply_stale')
        assert(root.telemetry.planning_paused and root.telemetry.planner_reason=='planner_reply_stale')
    """
    )


def test_paused_empty_observations_recover_without_clock_catchup(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');Server.pending=nil
        s.residents={};s.order={};root.bridgeIn.health='planner_unavailable'
        local before=s.elapsed_hours
        clock=clock+2;hour=hour+20;tick()
        assert(not Server.workerReady and s.elapsed_hours==before)
        assert(root.bridgeOut.paused and #root.bridgeOut.residents==0)
        local fresh=root.bridgeOut.revision
        -- Merely changing the old health string must not resume.
        root.bridgeIn.health='ready';tick();assert(not Server.workerReady)
        root.bridgeIn.observation_revision=fresh;root.bridgeIn.plans={}
        hour=hour+100;tick()
        assert(Server.workerReady and s.elapsed_hours==before and s.status=='running')
        tick();assert(s.elapsed_hours>before and s.elapsed_hours-before<0.002)
    """
    )


def test_explicit_missing_health_holds_before_timeout_and_manual_pause_survives_recovery(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');cmd(admin,'pause');local before=s.elapsed_hours
        root.bridgeIn.health=nil;tick()
        assert(not Server.workerReady and Server.workerReason=='planner_unavailable')
        cmd(admin,'step',{hours=2});assert(s.elapsed_hours==before)
        cmd(admin,'advance',{phase='survival'});assert(s.elapsed_hours==before)
        clock=clock+2;tick();local revision=root.bridgeOut.revision
        root.bridgeIn.health='ready';root.bridgeIn.observation_revision=revision
        tick();assert(Server.workerReady and s.status=='paused' and s.elapsed_hours==before)
    """
    )


def test_outage_keeps_native_reconciliation_and_defensive_lease_alive(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');Server.pending=nil
        r.materialized=true;r.lifecycle='active';r.owner_id=10
        local renewed=0;local reconciled=0
        package.loaded['AKRScenario/Native'].reconcile=function(resident)
          reconciled=reconciled+1;resident.position={x=111,y=100,z=0};return {},'active'
        end
        AKRNative.ownerId=function() return 10 end
        AKRNative.lease=function() renewed=renewed+1;return true end
        root.bridgeIn.health='planner_unavailable'
        local hunger=r.hunger;tick()
        assert(not Server.workerReady and reconciled>0 and renewed>0)
        assert(r.position.x==111 and r.lease_until>clock and r.hunger==hunger)
    """
    )


def test_client_planning_hold_blocks_routines_but_keeps_immediate_escape(lua):
    lua.execute(
        CLIENT
        + """
        remote=false;L.planningPaused=true
        tick();assert(#sent==0 and walks==0)
        L.runs[r.id].escapeUntil=clock+2
        L.runs[r.id].escape={x=105,y=100,z=0}
        tick();assert(walks==1 and #sent==0)
    """
    )


def test_late_pre_outage_reply_cannot_recover_until_post_hold_observation(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');Server.pending=nil
        clock=clock+1.1;tick();local oldInFlight=root.bridgeOut.revision
        root.bridgeIn.health='planner_unavailable';tick()
        assert(not Server.workerReady and Server.workerMinimum>oldInFlight)
        root.bridgeIn.health='ready';root.bridgeIn.observation_revision=oldInFlight
        tick();assert(not Server.workerReady)
        clock=clock+1.1;tick();local newPaused=root.bridgeOut.revision
        assert(newPaused>oldInFlight and root.bridgeOut.paused)
        root.bridgeIn.observation_revision=newPaused;root.bridgeIn.plans={}
        tick();assert(Server.workerReady)
    """
    )


def test_outage_contact_keeps_damage_and_defers_confirmed_infection_until_recovery(lua):
    lua.execute(
        SERVER
        + """
        cmd(admin,'start');Server.pending=nil
        r.materialized=true;r.lifecycle='active';r.max_native_health=1;r.health=100
        local square={isSomethingTo=function() return false end};local health=1
        local victim={isDead=function() return false end,getX=function() return 100 end,
          getY=function() return 100 end,getZ=function() return 0 end,getHealth=function() return health end,
          getSquare=function() return square end,getVehicle=function() return nil end}
        local attacker={isDead=function() return false end,getX=function() return 100.5 end,
          getY=function() return 100 end,getZ=function() return 0 end,getOnlineID=function() return 50 end,
          getSquare=function() return square end,getVariableBoolean=function() return false end,
          getOwnerPlayer=function() return admin end,isFacingObject=function() return true end}
        local n=package.loaded['AKRScenario/Native'];n.actors[r.id]=victim
        n.health=function(r,h) r.health=h;health=h/100;return true end
        n.reconcile=function() return victim,'active' end
        AKRNative.ownerId=function() return 10 end;AKRNative.lease=function() return true end
        package.loaded['AKRScenario/World'].find=function(outfit) if outfit==500 then return attacker end end
        root.bridgeIn.health='planner_unavailable';tick()
        local frozen=s.elapsed_hours
        cmd(admin,'contact',{epoch=Server.epoch,resident_id=r.id,generation=r.generation,
          lease_epoch=r.lease_epoch,attacker_id=50,attacker_outfit=500,sequence=1})
        assert(r.health==94 and r.infection=='healthy' and r.pending_contact_exposure)
        clock=clock+1.1;hour=hour+50;tick();assert(s.elapsed_hours==frozen)
        root.bridgeIn.health='ready';root.bridgeIn.observation_revision=root.bridgeOut.revision
        tick()
        assert(Server.workerReady and s.elapsed_hours==frozen)
        assert(r.infection=='exposed' and r.exposed_hour==frozen and not r.pending_contact_exposure)
    """
    )


def test_schedule_seed_survives_events_and_schema_migration(lua):
    lua.execute(
        RESIDENT
        + """
        local stable=s.schedule_seed
        for i=1,100 do M.random(s) end
        assert(s.schedule_seed==stable and s.seed~=104729)
        local legacy=M.new('world','uuid',100);legacy.schema=1;legacy.schedule_seed=nil
        local eventSeed=legacy.seed;assert(M.migrate(legacy))
        assert(legacy.schema==C.schema and legacy.schedule_seed==stable and legacy.seed==eventSeed)
        assert(M.migrate(legacy));assert(legacy.schedule_seed==stable)
        legacy.schema=999;assert(not M.migrate(legacy))
    """
    )


def test_car_ownership_does_not_remove_a_walker_from_population_budget(lua):
    lua.execute(
        RESIDENT
        + """
        r.materialized=true;r.has_vehicle=true;r.lifecycle='active'
        s.vehicles.car={status='parked'}
        local p,n,v=M.count(s);assert(p==1 and n==1 and v==0)
        r.travel_state='boarding';p,n,v=M.count(s);assert(p==1 and n==1)
        r.in_vehicle=true;r.travel_state='onboard';s.vehicles.car.status='driving'
        p,n,v=M.count(s);assert(p==0 and n==1 and v==1)
        s.vehicles.car.status='braking';p,n,v=M.count(s);assert(v==1)
        r.travel_state='exiting';s.vehicles.car.status='parked'
        p,n,v=M.count(s);assert(p==0 and n==1 and v==0)
    """
    )


def test_road_closures_expire_deduplicate_and_cannot_cross_navigation_identity(lua):
    lua.execute(
        RESIDENT
        + """
        local Road=require 'AKRScenario/RoadState'
        assert(Road.bind(s,'map-a'))
        assert(Road.block(s,'map-a',1,2,10,'car_in_path'))
        assert(Road.block(s,'map-a',1,2,10.1,'car_in_path'))
        assert(#Road.snapshot(s,10.2)==1)
        assert(not Road.block(s,'map-b',1,2,10.2,'car_in_path'))
        assert(#Road.snapshot(s,10.4)==0)
        assert(not Road.block(s,'map-a',1,2,0/0,'bad_clock'))
        Road.limit=1;assert(Road.block(s,'map-a',1,2,11,'blocked'))
        assert(not Road.block(s,'map-a',2,3,11,'overflow'))
        assert(Road.bind(s,'map-b') and #s.road_closures==0)
    """
    )
