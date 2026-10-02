"""Execute the actual civilian modules in Lua 5.1; no server or client is launched."""

from pathlib import Path

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
MODS = ("AKRCore", "AKRPopulation", "AKRResidents")


@pytest.fixture
def lua():
    vm = LuaRuntime(unpack_returned_tuples=True)
    vm.globals().package.path = ";".join(
        str(ROOT / "mods" / name / "42/media/lua/shared/?.lua") for name in MODS
    )
    vm.execute("""
        P=require 'AKRPopulation/Population';R=require 'AKRResidents/Residents';M=R.Model
        function resident(seed) return M.new('civilian',seed or 7,{x=5,y=5,z=0},{{x=10,y=5,z=0},{x=2,y=9,z=0}}) end
        function observation(threat)
            return {online=true,known=true,position={x=5,y=5,z=0},threats=threat and {
                {id='z1',zombie=true,visible=true,position={x=3,y=5,z=0}}
            } or {}}
        end
        function find(out,kind) for _,c in ipairs(out) do if c.kind==kind then return c end end end
    """)
    return vm


def test_lua_sources_parse_and_modules_register(lua):
    for name in MODS[1:]:
        for path in (ROOT / "mods" / name).rglob("*.lua"):
            lua.execute("assert(loadstring(...))", path.read_text())
    lua.execute("""
        R.install();local K=require 'AKRCore/Core';local core=K.instance()
        assert(K.Registry.require(core.registry,'AKRResidents',0)==R)
        assert(K.Registry.require(core.registry,'AKRPopulation',0)==P)
    """)


def test_idle_roam_arrive_and_wait(lua):
    lua.execute("""
        local r=resident();local o=observation();local out=M.tick(r,0,o)
        assert(r.state=='WALK' and find(out,'navigate').mode=='roam')
        o.path={status='found',revision=r.revision,generation=r.generation,route={{x=10,y=5,z=0}}}
        assert(find(M.tick(r,0.25,o),'follow'));o.path=nil
        o.arrivedRevision=r.revision;M.tick(r,0.5,o)
        assert(r.state=='IDLE' and r.deadline>0.5)
        assert(#M.tick(r,0.75,o)==0)
    """)


def test_defense_keeps_native_recovery_and_target_generation(lua):
    lua.execute("""
        local r=resident();local o=observation(true)
        o.threats[1].generation=77
        o.combat={known=true,escapeAssessed=true,escapeReachable=false,pathPending=false,
            knockedDown=false,attacking=false,weaponUsable=true,endurance=1}
        local out=M.tick(r,0,o);local d=find(out,'defend')
        assert(d and d.targetGeneration==77 and d.style=='melee')
        o.combat.attacking=true
        assert(#M.tick(r,1.2,o)==0 and r.state=='DEFEND')
        o.combat.attacking=false;o.combat.escapeAssessed=false
        local recovered=M.tick(r,1.5,o)
        assert(r.state=='FLEE' and find(recovered,'navigate') and not find(recovered,'defend'))
    """)


def test_pausing_shifts_defense_cooldown(lua):
    lua.execute("""
        local r=resident();r.combatDeadline=2;r.nextDefense=3
        local o=observation();o.online=false;M.tick(r,1,o)
        o.online=true;M.tick(r,11,o)
        assert(r.combatDeadline==12 and r.nextDefense==13)
    """)


def test_threat_interrupts_and_stale_path_is_ignored(lua):
    lua.execute("""
        local r=resident();M.tick(r,0,observation());local old=r.revision
        local o=observation(true);o.path={status='found',revision=old,generation=r.generation,route={}}
        local out=M.tick(r,0.25,o)
        assert(r.state=='FLEE' and find(out,'stop') and find(out,'navigate').allowClimb)
        assert(not find(out,'follow') and r.revision>old)
        local revision=r.revision;M.tick(r,0.5,o);assert(r.revision==revision)
    """)


def test_occlusion_and_recovery_hysteresis(lua):
    lua.execute("""
        local r=resident();local o=observation(true);o.threats[1].visible=false
        M.tick(r,0,o);assert(r.state=='WALK')
        o.threats[1].visible=true;M.tick(r,0.25,o);assert(r.state=='FLEE')
        o.threats={};M.tick(r,2,o);assert(r.state=='FLEE')
        M.tick(r,3.5,o);assert(r.state=='RECOVER')
        M.tick(r,6,o);assert(r.state=='RECOVER')
        M.tick(r,6.75,o);assert(r.state=='IDLE')
    """)


def test_missing_observation_and_trapped_backoff(lua):
    lua.execute("""
        local r=resident();M.tick(r,0,observation(true));local o=observation(true)
        o.path={status='no_path',revision=r.revision,generation=r.generation}
        M.tick(r,0.25,o);assert(r.state=='BLOCKED' and r.nextRepath==1.25)
        assert(not find(M.tick(r,0.5,observation(true)),'navigate'))
        assert(find(M.tick(r,1.5,observation(true)),'navigate'))
        local missing=observation();missing.known=false
        assert(find(M.tick(r,1.75,missing),'stop'));assert(r.reason=='observation_missing')
    """)


def test_cornered_civilian_defends_once_then_retries_escape(lua):
    lua.execute("""
        local r=resident();local o=observation(true)
        o.combat={known=true,escapeReachable=false,pathPending=true,weaponUsable=true,endurance=0.8}
        assert(not find(M.tick(r,0,o),'defend'))
        o.combat.pathPending=false
        assert(not find(M.tick(r,0.25,o),'defend'))
        o.combat.escapeAssessed=true
        local first=M.tick(r,0.5,o)
        assert(r.state=='DEFEND' and find(first,'defend').style=='melee')
        assert(not find(M.tick(r,0.75,o),'defend'))
        assert(not find(M.tick(r,1.35,o),'defend'))
        assert(r.state=='FLEE')
        o.combat.escapeReachable=true
        assert(not find(M.tick(r,1.6,o),'defend'))
    """)


def test_pause_has_no_backlog_and_death_is_terminal(lua):
    lua.execute("""
        local r=resident();M.tick(r,0,observation(true));local paused=observation();paused.online=false
        M.tick(r,1,paused);M.tick(r,1000,paused)
        M.tick(r,1001,observation());assert(r.state=='FLEE')
        local o=observation();o.dead=true
        assert(find(M.tick(r,1001.25,o),'stop'));assert(r.state=='TERMINAL')
        assert(#M.tick(r,1002,observation(true))==0)
    """)


def test_seeded_behavior_is_repeatable_and_inputs_detached(lua):
    lua.execute("""
        local a,b=resident(),resident()
        for i=0,1000 do
            local o=observation(i%40<12)
            local x,y=M.tick(a,i*0.25,o),M.tick(b,i*0.25,o)
            assert(P.equal(a,b) and P.equal(x,y))
            if x[1] then x[1].resident='mutated';assert(a.id=='civilian') end
        end
    """)


def test_population_state_survives_restart_without_respawning(lua):
    lua.execute("""
        local root={};local state=P.open(root,'e1');local residents=R.open(root)
        local r=R.add(residents,'one',7,{x=1,y=1,z=0},{{x=2,y=2,z=0}})
        local calls=0
        local port={reserve=function(id,g,new,body) calls=calls+1;return {epoch='e1',slot=4096,generation=1,resident=id,residentGeneration=g} end}
        local t=P.reserve(state,port,r);assert(t and calls==1)
        assert(P.observe(state,t,'ACTIVE'));assert(not P.reserve(state,port,r))
        state=P.open(root,'e2');assert(state.bindings.one.state=='UNRESOLVED')
        assert(not P.reserve(state,port,r) and calls==1)
        local unknown=P.copy(t);unknown.generation=2
        assert(not P.retired(state,r,unknown,{bytes='body'}))
    """)


def test_body_payload_preserved_and_duplicate_retirement_rejected(lua):
    lua.execute("""
        local state=P.open({},'epoch');local r=resident()
        local port={reserve=function(id,g) return {epoch='epoch',slot=4096,generation=1,resident=id,residentGeneration=g} end}
        local t=P.reserve(state,port,r);P.observe(state,t,'RETIRING')
        local body={codec=1,build='fixture',bytes='opaque-native-data',items={{id=101,contents={{id=202}}}}}
        assert(P.retired(state,r,t,body));body.items[1].id=0
        assert(not r.newBody and r.generation==2 and r.body.items[1].id==101)
        assert(not P.retired(state,r,t,body))
    """)


def test_four_civilians_use_real_model_through_ports(lua):
    lua.execute("""
        local state=R.open({});local ids={};local commands={}
        for i=1,4 do ids[i]='r'..i;R.add(state,ids[i],i,{x=5,y=5,z=0},{{x=8,y=5,z=0}}) end
        local ports={observe=function(id) return observation(id=='r2') end,intent=function(c) commands[#commands+1]=c end}
        R.tick(state,ids,0,ports)
        assert(state.residents.r2.state=='FLEE' and state.residents.r1.state=='WALK')
        assert(#commands==5)
    """)


def test_controller_wires_flee_and_rejects_previous_slot_generation(lua):
    lua.execute("""
        local C=require 'AKRResidents/Controller';local commands={};local result=nil
        local ports={pool={tick=function() end,pollRetired=function() end,
            reserve=function(id,g) return {epoch='e',slot=4096,generation=2,resident=id,residentGeneration=g} end},
            actor={observe=function() local o=observation(true);o.state='ACTIVE';return o end,
                stop=function(key) commands[#commands+1]='stop' end,follow=function() commands[#commands+1]='follow' end},
            navigation={cancel=function() end,request=function(key,intent) commands[#commands+1]=intent.mode end,
                poll=function() local r=result;result=nil;return r end}}
        local c=C.new({},'e',ports);R.add(c.residents,'r',4,{x=5,y=5,z=0},{{x=6,y=6,z=0}})
        local token=C.materialize(c,'r');C.tick(c,{'r'},0)
        assert(commands[1]=='stop' and commands[2]=='flee')
        local old=P.copy(token);old.generation=1
        result={key={token=old,revision=c.residents.residents.r.revision},status='found',route={{x=9,y=9,z=0}}}
        C.tick(c,{'r'},0.25);assert(#commands==2)
        result={key={token=token,revision=c.residents.residents.r.revision},status='found',route={{x=9,y=9,z=0}}}
        C.tick(c,{'r'},0.5);assert(commands[3]=='follow')
    """)


def test_controller_command_failure_retains_ownership(lua):
    lua.execute("""
        local C=require 'AKRResidents/Controller'
        local ports={pool={tick=function() end,pollRetired=function() end,
            reserve=function(id,g) return {epoch='e',slot=4096,generation=1,resident=id,residentGeneration=g} end},
            actor={observe=function() local o=observation();o.state='ACTIVE';return o end,stop=function() end},
            navigation={cancel=function() end,poll=function() end,request=function() error('native failed') end}}
        local c=C.new({},'e',ports);R.add(c.residents,'r',4,{x=5,y=5,z=0},{{x=6,y=6,z=0}})
        C.materialize(c,'r');C.tick(c,{'r'},0)
        assert(c.residents.residents.r.state=='UNRESOLVED' and c.population.bindings.r.state=='UNRESOLVED')
        assert(not C.materialize(c,'r'))
    """)


def test_stationary_threat_does_not_restart_a_good_escape(lua):
    lua.execute("""
        local r=resident();M.tick(r,0,observation(true));local o=observation(true)
        o.path={status='found',revision=r.revision,generation=r.generation,route={{x=10,y=10,z=0}}}
        M.tick(r,0.25,o);o.path=nil;local revision=r.revision
        for i=1,8 do assert(not find(M.tick(r,i*0.5,o),'navigate')) end
        assert(r.revision==revision)
        o.threats[1].position.x=5
        assert(find(M.tick(r,4.5,o),'navigate'))
    """)


def test_committed_escape_is_not_erased_by_a_following_threat(lua):
    lua.execute("""
        local r=resident();local o=observation(true);M.tick(r,0,o)
        o.path={status='found',revision=r.revision,generation=r.generation,route={}}
        M.tick(r,.25,o);o.path=nil;local rev=r.revision
        o.threats[1].position.x=1;o.routeSafe=true
        local out=M.tick(r,1.5,o)
        assert(r.revision==rev and r.moving and not find(out,'stop') and not find(out,'navigate'))
        o.routeSafe=false;out=M.tick(r,1.8,o)
        assert(find(out,'stop') and find(out,'navigate'))
    """)


def test_reaction_pauses_without_losing_route_or_action(lua):
    lua.execute("""
        local r=resident();local o=observation(true);M.tick(r,0,o)
        o.path={status='found',revision=r.revision,generation=r.generation,route={}}
        M.tick(r,.25,o);o.path=nil;local rev=r.revision
        o.reacting=true;assert(#M.tick(r,.5,o)==0)
        assert(r.moving and r.revision==rev)
        o.reacting=false;o.routeSafe=true;assert(not find(M.tick(r,1.5,o),'stop'))
    """)


def test_persistent_routine_survives_danger_and_pool_rebinding(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan';local saved={}
        local r=M.bind(saved,'person',7,{x=5,y=5,z=0},{{x=10,y=5,z=0}},1)
        G.fallback(r);local a=G.current(r);assert(a.kind=='WALK')
        local id=a.id;G.suspend(r,'danger');G.resume(r);assert(G.current(r).id==id)
        assert(G.complete(r,id));assert(not G.complete(r,id))
        assert(G.current(r).kind=='WAIT');G.ensure(r).waitRemaining=.8
        M.detach(r);r=M.bind(saved,'person',7,{x=7,y=5,z=0},{{x=999,y=999,z=0}},2)
        assert(G.current(r).kind=='WAIT' and r.goalPlan.waitRemaining==.8)
        assert(r.goalPlan.home.x==5 and r.goalPlan.activity.x==10)
        local old={resident_id='person',generation=1,based_on_revision=r.goalPlan.facts,plan_revision=99,goal='civilian_routine',actions={}}
        assert(not G.accept(r,old));assert(G.current(r).kind=='WAIT')
        assert(G.complete(r,G.current(r).id));assert(G.current(r).target.x==5)
        assert(G.complete(r,G.current(r).id));assert(r.goalPlan.status=='complete')
    """)


def test_worker_routine_adoption_rejects_replacement_and_stale_generation(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan';local r=M.bind({},'p',7,{x=5,y=5,z=0},{{x=10,y=5,z=0}},1)
        local p={resident_id='p',generation=1,based_on_revision=1,plan_revision=2,goal='civilian_routine',actions={
          {id='a',kind=2,target={x=10,y=5,z=0}}, {id='b',kind=1,target={x=10,y=5,z=0}}, {id='c',kind=2,target={x=5,y=5,z=0}}}}
        assert(G.accept(r,p));p.plan_revision=3;assert(not G.accept(r,p))
        assert(G.current(r).id=='a' and r.goalPlan.source=='worker')
    """)


def test_previous_route_completion_cannot_complete_new_candidate(lua):
    lua.execute("""
        local r=resident();local o=observation(true);M.tick(r,0,o)
        local old=r.revision;o.path={status='found',revision=old,generation=r.generation,route={}}
        M.tick(r,.25,o);o.path=nil;o.routeSafe=true;o.routeRefresh=true
        M.tick(r,1.5,o);local candidate=r.revision;assert(candidate>old)
        o.routeRefresh=false;o.arrivedRevision=old
        o.path={status='found',revision=candidate,generation=r.generation,route={}}
        M.tick(r,1.8,o);assert(r.moving and r.followRevision==candidate)
    """)


def test_unknown_sweep_only_preserves_recent_committed_movement(lua):
    lua.execute("""
        local r=resident();local o=observation(true);M.tick(r,0,o)
        o.path={status='found',revision=r.revision,generation=r.generation,route={}}
        M.tick(r,.25,o);o.path=nil;o.known=false;o.routeSafe=true
        assert(#M.tick(r,1,o)==0 and r.moving)
        assert(find(M.tick(r,4,o),'stop') and r.state=='BLOCKED')
    """)


def test_routine_wait_does_not_consume_offline_time(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan'
        local r=M.bind({},'p',7,{x=5,y=5,z=0},{{x=10,y=5,z=0}},1)
        G.fallback(r);G.complete(r,G.current(r).id)
        local o=observation();M.tick(r,0,o)
        o.online=false;M.tick(r,.1,o)
        o.online=true;M.tick(r,20,o)
        assert(r.goalPlan.phase==1 and r.goalPlan.waitRemaining>1.8)
    """)


def test_soft_cancel_policy_and_partial_route_does_not_complete_goal(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan'
        local r=M.bind({},'buffered',12,{x=5,y=5,z=0},{{x=20,y=5,z=0}},1)
        G.fallback(r);local o=observation();M.tick(r,0,o)
        o.path={status='found',revision=r.revision,generation=1,route={}}
        M.tick(r,.25,o);o.path=nil;o.arrivedRevision=r.followRevision;o.goalReached=false
        local out=M.tick(r,.5,o)
        assert(r.goalPlan.phase==0 and r.state=='WALK' and find(out,'navigate'))
        o.arrivedRevision=nil;o.threats=observation(true).threats;o.routeSafe=true
        out=M.tick(r,.75,o);assert(find(out,'stop').cancellation=='DEFER_TO_BOUNDARY')
        o.reacting=true;assert(#M.tick(r,1,o)==0)
    """)


def test_receipt_admission_holds_wait_and_completion_is_exactly_once(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan'
        local r=M.bind({},'receipt',12,{x=5,y=5,z=0},{{x=5,y=5,z=0}},1)
        G.fallback(r);local first=G.current(r).id
        assert(G.complete(r,first));assert(not G.complete(r,first))
        assert(#r.goalPlan.outcomes==1 and r.goalPlan.outcomes[1].state=='completed')
        local o=observation();o.routineAdmission=false
        M.tick(r,0,o);M.tick(r,10,o);assert(r.goalPlan.waitRemaining==2 and r.goalPlan.phase==1)
        o.routineAdmission=true;o.admittedAction=G.current(r).id
        M.tick(r,10.5,o);M.tick(r,13,o);assert(r.goalPlan.phase==2 and #r.goalPlan.outcomes==2)
        G.cancelCurrent(r,'cancelled','test');assert(#r.goalPlan.outcomes==3 and G.current(r)==nil)
        G.cancelCurrent(r,'cancelled','test');assert(#r.goalPlan.outcomes==3)
    """)


def test_terminal_population_provenance_and_reanimation_are_idempotent(lua):
    lua.execute("""
        local state=assert(P.open({},'e'));local r=resident();r.newBody=true
        local token={epoch='e',slot=4096,generation=1,resident=r.id,residentGeneration=r.generation}
        assert(P.reserve(state,{reserve=function() return token end},r))
        assert(P.observe(state,token,'ACTIVE'))
        assert(P.deceased(state,r,token,'corpse1'))
        assert(P.deceased(state,r,token,'corpse1'))
        assert(not P.deceased(state,r,token,'corpse2'))
        assert(r.life=='DEAD' and r.state=='TERMINAL' and not r.newBody)
        M.detach(r);assert(r.state=='TERMINAL')
        local late=M.acceptPlan(r,{});assert(not late.accepted and late.reason=='terminal_resident')
        assert(P.reanimated(state,r,token,'corpse1',12))
        assert(P.reanimated(state,r,token,'corpse1',12))
        assert(P.deceased(state,r,token,'corpse1') and state.bindings[r.id].state=='REANIMATED')
        assert(not P.reanimated(state,r,token,'corpse1',13))
        assert(not P.observe(state,token,'ACTIVE'))
        assert(#M.tick(r,20,observation())==0)
        assert(not pcall(M.bind,{residents={[r.id]=r}},r.id,7,{x=1,y=1,z=0},{},r.generation))
    """)


def test_collect_requires_matching_effect_receipt_not_elapsed_time(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan'
        local r=M.bind({},'collector',7,{x=5,y=5,z=0},{{x=5,y=5,z=0}},1)
        r.goalPlan.collectItem=true;G.fallback(r)
        G.complete(r,G.current(r).id)
        local action=G.current(r);assert(action.kind=='COLLECT')
        local o=observation();M.tick(r,0,o);M.tick(r,100,o)
        assert(r.goalPlan.phase==1)
        o.interactionComplete='stale';M.tick(r,101,o);assert(r.goalPlan.phase==1)
        o.interactionComplete=action.id;M.tick(r,102,o);assert(r.goalPlan.phase==2)
        assert(not G.complete(r,action.id))
        assert(G.current(r).kind=='WALK')
    """)


def test_collect_worker_plan_rejects_wait_substitution(lua):
    lua.execute("""
        local G=require 'AKRResidents/Plan'
        local r=M.bind({},'collector',7,{x=5,y=5,z=0},{{x=10,y=5,z=0}},1)
        r.goalPlan.collectItem=true
        local p={resident_id=r.id,generation=1,based_on_revision=1,plan_revision=2,goal='civilian_routine',actions={
          {id='a',kind=2,target={x=10,y=5,z=0}}, {id='b',kind=1,target={x=10,y=5,z=0}}, {id='c',kind=2,target={x=5,y=5,z=0}}}}
        assert(not G.accept(r,p));p.actions[2].kind=19
        assert(G.accept(r,p));assert(r.goalPlan.actions[2].kind=='COLLECT')
    """)


def test_path_watchdog_requires_real_progress_and_has_absolute_limit(lua):
    lua.execute("""
        local r=resident();local o=observation();M.tick(r,0,o)
        for now=1,7 do o.pathProgress=now;M.tick(r,now,o) end
        assert(r.requestPending and r.state=='WALK')
        M.tick(r,12,o);assert(r.state=='BLOCKED' and r.reason=='path_timeout')
        r=resident();o=observation();M.tick(r,0,o)
        for now=1,19 do o.pathProgress=now;M.tick(r,now,o) end
        assert(r.requestPending)
        o.pathProgress=20;M.tick(r,20,o)
        assert(r.state=='BLOCKED' and r.reason=='path_timeout')
    """)
