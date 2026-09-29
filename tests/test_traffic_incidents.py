"""Persistent intent/recovery contracts; no claim of native spawning or audio."""

from pathlib import Path

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).parents[1]


@pytest.fixture
def lua():
    vm = LuaRuntime(unpack_returned_tuples=True)
    vm.globals().package.path = str(ROOT / "mods/AKRStoryteller/42/media/lua/shared/?.lua")
    vm.execute("""
        T=require 'AKRScenario/TrafficIncidents'
        s={world_id='world',status='running',phase='first_cases',elapsed_hours=2}
        c={online=2,all_players_known=true,scene_clear=true,player_exclusion=true,unseen=true,
            age_seconds=0,moving_vehicles=0}
        plan={kind='offscreen',site={x=100,y=100},seed=33,wrecks={
            {script='Base.SmallCar',position={x=100,y=100},yaw=90,parts={Engine=45},loot={{type='Base.WaterBottle',count=1}}},
            {script='Base.SmallCar',position={x=104,y=100},yaw=0,parts={Engine=35},loot={}}}}
        function checkpoint(kind)
            local token,revision=T.prepare(s,e.id,kind,c);assert(token)
            assert(T.release(s,e.id,token,revision,c));return token
        end
        function materialize()
            local token=checkpoint('materialize')
            assert(T.receipt(s,e.id,token,{world_id=s.world_id,saved_world_revision='save:1',
                wreck_ids={e.wrecks[1].id,e.wrecks[2].id}}))
        end
        function clone(t)
            if type(t)~='table' then return t end
            local o={};for k,v in pairs(t) do o[k]=clone(v) end;return o
        end
    """)
    return vm


def test_aftermath_before_sound_and_preserved_loot_after_restore(lua):
    lua.execute("""
        e=assert(T.reserve(s,plan,c))
        assert(not T.prepare(s,e.id,'sound',c))
        plan.site.x=999;plan.wrecks[1].loot[1].count=32
        assert(e.site.x==100 and e.wrecks[1].loot[1].count==1)
        materialize();local token=checkpoint('sound')
        assert(T.receipt(s,e.id,token,{world_id=s.world_id}))
        local id=e.id;s=clone(s);assert(T.bind(s,true));e=s.traffic.events[id]
        assert(e.phase=='aftermath' and e.sound=='sent')
        assert(e.wrecks[1].parts.Engine==45 and e.wrecks[1].loot[1].count==1)
        assert(not T.prepare(s,id,'sound',c));assert(not T.prepare(s,id,'materialize',c))
    """)


def test_stale_checkpoint_or_early_receipt_cannot_release_effect(lua):
    lua.execute("""
        e=assert(T.reserve(s,plan,c));local token,revision=T.prepare(s,e.id,'materialize',c)
        assert(not T.release(s,e.id,token,revision-1,c))
        assert(not T.receipt(s,e.id,token,{world_id=s.world_id}))
        c.player_exclusion=false;assert(not T.release(s,e.id,token,revision,c))
        c.player_exclusion=true;assert(T.release(s,e.id,token,revision,c))
        assert(not T.release(s,e.id,token,revision,c))
        assert(not T.receipt(s,e.id,token,{world_id=s.world_id,wreck_ids={'wrong','ids'},saved_world_revision='save:1'}))
    """)


@pytest.mark.parametrize("release", [False, True])
def test_interrupted_materialization_retains_reservation_and_never_retries(lua, release):
    lua.globals().should_release = release
    lua.execute("""
        e=assert(T.reserve(s,plan,c));local id=e.id
        local token,revision=T.prepare(s,id,'materialize',c)
        if should_release then assert(T.release(s,id,token,revision,c)) end
        s=clone(s);assert(T.bind(s,true));e=s.traffic.events[id]
        assert(e.phase=='unresolved');local events,cars=T.reservations(s);assert(events==1 and cars==2)
        assert(not T.release(s,id,token,revision,c));assert(not T.prepare(s,id,'materialize',c))
        assert(not T.cancel(s,id));assert(not T.reserve(s,plan,c))
    """)


def test_interrupted_sound_is_spent_without_replaying_or_losing_wreck(lua):
    lua.execute("""
        e=assert(T.reserve(s,plan,c));materialize()
        local token,revision=T.prepare(s,e.id,'sound',c)
        local id=e.id;s=clone(s);assert(T.bind(s,true));e=s.traffic.events[id]
        assert(e.sound=='uncertain_spent' and e.phase=='aftermath')
        assert(not T.release(s,id,token,revision,c));assert(not T.prepare(s,id,'sound',c))
        assert(e.world_receipt=='save:1')
    """)


@pytest.mark.parametrize(
    "mutation",
    [
        "s.status='calm'",
        "s.status='paused'",
        "c.online=0",
        "c.online=33",
        "c.moving_vehicles=3",
        "c.age_seconds=1",
        "c.scene_clear=false",
        "c.player_exclusion=false",
        "c.all_players_known=false",
        "c.unseen=false",
        "plan.site.x=0/0",
        "plan.wrecks[1].loot[1].count=100",
    ],
)
def test_ineligible_incidents_do_not_claim_capacity(lua, mutation):
    lua.execute(mutation + ";assert(not T.reserve(s,plan,c));assert(T.reservations(s)==0)")


def test_cooldown_history_cap_and_ids_survive_restore(lua):
    lua.execute("""
        e=assert(T.reserve(s,plan,c));assert(T.cancel(s,e.id));assert(not T.reserve(s,plan,c))
        s=clone(s);assert(T.bind(s,true));s.elapsed_hours=3
        e=assert(T.reserve(s,plan,c));assert(e.id=='world:traffic:2');assert(T.cancel(s,e.id))
        T.limit=2;s.elapsed_hours=100;assert(not T.reserve(s,plan,c))
    """)


def test_unresolved_incident_counts_against_shared_scenario_fleet(lua):
    lua.execute("""
        local M=require 'AKRScenario/Model'
        s=M.new('test','world',100);s.status='running';s.phase='first_cases'
        e=assert(T.reserve(s,plan,c));local _,_,cars=M.count(s);assert(cars==2)
        s.vehicles.normal={status='driving'};_,_,cars=M.count(s);assert(cars==3)
        assert(T.bind(s,true));_,_,cars=M.count(s);assert(cars==3)
    """)


def test_wrong_world_or_invalid_saved_state_is_rejected(lua):
    lua.execute("""
        e=assert(T.reserve(s,plan,c));s.world_id='another';assert(not T.bind(s,true))
        s.world_id='world';s.traffic.events[e.id]=nil;assert(not T.bind(s,true))
    """)
