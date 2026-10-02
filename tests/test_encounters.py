"""Meaningful operator and client/server encounter scope checks; no live services."""

from pathlib import Path
import sys
import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import civilian_encounter as ops  # noqa: E402 - project-local scripts path added above


def test_batch_keeps_exact_counts_and_bounded_cases():
    cases = ops.planned_cases()
    assert [c["actors"] for c in cases] == [1, 1, 1, 1, 4, 4, 4]
    assert [c["scenario"] for c in cases[1:4]] == [
        "OPEN_ESCAPE",
        "INCOMING_INJURY",
        "DEFENSE_ESCAPE",
    ]
    assert all(c["timeout_seconds"] == 120 and c["hold_seconds"] == 8 for c in cases)
    with pytest.raises(ValueError):
        ops.planned_cases("injury", 4)
    with pytest.raises(ValueError):
        ops.planned_cases("defense", 64)


def test_parallel_comparison_is_exactly_two_without_changing_survival_batch():
    assert ops.planned_cases("compare") == [
        {
            "scenario": "STRIDE_COMPARE",
            "actors": 2,
            "seed": 1,
            "timeout_seconds": 120,
            "hold_seconds": 8,
        }
    ]
    with pytest.raises(ValueError):
        ops.planned_cases("compare", 4)


def test_missing_or_rotated_log_is_not_clean_evidence(tmp_path):
    log = tmp_path / "console.txt"
    assert ops.log_slice(log, None) is None
    log.write_text("before\n")
    boundary = ops.log_boundary(log)
    with log.open("a") as stream:
        stream.write("after\n")
    assert ops.log_slice(log, boundary) == b"after\n"
    log.write_text("")
    assert ops.log_slice(log, boundary) is None
    log.unlink()
    assert ops.log_slice(log, boundary) is None


def server():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute("""
        callbacks={}
        package.preload['AKRCore/Core']=function() return {instance=function() return {} end,
            Dispatch={on=function(core,event,id,fn) callbacks[id]=fn end}} end
        now=1000;getTimestampMs=function() return now end
        getServerName=function() return 'AKR_DayOne_Test_fixture' end
        AKRRuntime={epoch='epoch'}
        AKREncounterWatch={epoch='epoch',event='event',stage='RUNNING',
            pairs={{actor=4096,generation=2,hunter=7}}}
        p={getUsername=function() return 'akr' end}
        function report(event,generation)
            callbacks['AKRDevTools.encounterView']('AKRDevTools','encounterView',p,
                {epoch='epoch',event=event,stage='RUNNING',
                    pairs={{actor=4096,generation=generation,hunter=7,actor_present=true,hunter_present=true,
                    actor_visible=true,hunter_visible=true,local_owner=true,reanimated_visible=false,
                    actor_x=1,actor_y=2,hunter_x=3,hunter_y=4,actor_state='walk',hunter_state='attack'}}})
        end
    """)
    lua.execute(
        (ROOT / "mods/AKRDevTools/42/media/lua/server/AKRDevTools/EncounterWatch.lua").read_text()
    )
    return lua


def test_readiness_rejects_previous_case_and_reused_actor_generation():
    lua = server()
    lua.execute("report('old',2);report('event',1)")
    assert lua.globals().AKREncounterReport is None
    lua.execute("report('event',2)")
    assert lua.globals().AKREncounterReport.event == "event"
    lua.execute("now=1040;report('event',2)")
    assert lua.globals().AKREncounterReport.received_ms == 1000


def test_client_only_holds_explicit_inactive_hunter():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute("""
        callbacks={};now=1000
        package.preload['AKRCore/Core']=function() return {instance=function() return {} end,
            Dispatch={on=function(core,event,id,fn) callbacks[id]=fn end}} end
        package.preload['AKRDevTools/PoolVisibility']=function() return {body=function() return {visible=true} end} end
        getTimestampMs=function() return now end
        actor={isGhostMode=function() return false end,getNetworkCharacterAI=function() error("opaque native object is not a Lua API") end,getX=function() return 1 end,getY=function() return 2 end,getActionStateName=function() return 'movement' end,getCurrentStateName=function() return 'walk' end,isRunning=function() return false end,getHitReaction=function() return '' end,getIgnoreMovement=function() return false end,getVariableFloat=function() return 0 end}
        hunter={getTarget=function() return nil end,isUseless=function() return false end,getTargetSeenTime=function() return 0 end,remote=false,held=nil,cleared=0,getOnlineID=function() return 7 end,
            getX=function() return 2 end,getY=function() return 2 end,getCurrentStateName=function() return 'walk' end}
        function hunter:isRemoteZombie() return self.remote end
        function hunter:setUseless(v) self.held=v end
        function hunter:setTarget(v) self.cleared=self.cleared+1 end
        blockedDoor=true;missingSquare=false
        GridSquareEdgeFacingDirection={EAST_WEST={}}
        getCell=function() return {getGridSquare=function() if missingSquare then return nil end return {getDoor=function(self,facing) assert(facing==GridSquareEdgeFacingDirection.EAST_WEST);return blockedDoor and {} or nil end} end,getZombieList=function() return {size=function() return 1 end,get=function() return hunter end} end} end
        facingCalls=0;getSpecificPlayer=function() return {faceLocationF=function(self,x,y) facingCalls=facingCalls+1;faceX=x;faceY=y end} end;getPlayerByOnlineID=function() return actor end
        sendClientCommand=function(p,m,c,r) lastReport=r end
        function configure(event,active)
            callbacks['AKRDevTools.encounterConfig']('AKRDevTools','encounterConfig',
                {epoch='epoch',event=event,count=1,stage='RUNNING',pairs={{actor=4096,generation=1,hunter=7,active=active}}})
        end
    """)
    lua.execute(
        (ROOT / "mods/AKRDevTools/42/media/lua/client/AKRDevTools/EncounterWatch.lua").read_text()
    )
    lua.execute("configure('first',false);callbacks['AKRDevTools.encounterWatch']()")
    assert lua.globals().hunter.held is True
    lua.execute("now=1200;configure('second',true);callbacks['AKRDevTools.encounterWatch']()")
    assert lua.globals().hunter.held is False
    assert lua.globals().hunter.cleared == 1
    lua.execute(
        "now=1400;hunter.remote=true;configure('third',false);callbacks['AKRDevTools.encounterWatch']()"
    )
    assert lua.globals().hunter.held is False
    lua.execute("""
        callbacks['AKRDevTools.encounterConfig']('AKRDevTools','encounterConfig',
            {epoch='epoch',event='doors',count=1,stage='RUNNING',pairs={{actor=4096,generation=1,hunter=7,active=false,exit_x=10,exit_y=20}}})
        now=1600;callbacks['AKRDevTools.encounterWatch']()
    """)
    assert lua.globals().lastReport.pairs[1].exit_clear is False
    lua.execute("blockedDoor=false;now=1800;callbacks['AKRDevTools.encounterWatch']()")
    assert lua.globals().lastReport.pairs[1].exit_clear is True
    lua.execute("missingSquare=true;now=2000;callbacks['AKRDevTools.encounterWatch']()")
    assert lua.globals().lastReport.pairs[1].exit_clear is False
    # Each viewing stage must orient the observer toward the actual replacement;
    # normal combat must not continually override the human observer's facing.
    for stage in ("POSITIONING", "REUSE_POSITION", "REUSE_WALK"):
        lua.globals().testStage = stage
        lua.execute("""
            callbacks['AKRDevTools.encounterConfig']('AKRDevTools','encounterConfig',
                {epoch='epoch',event='reuse',count=1,stage=testStage,pairs={{actor=4096,generation=2,hunter=7,active=false}}})
            now=now+200;callbacks['AKRDevTools.encounterWatch']()
        """)
    assert lua.globals().facingCalls == 3
    assert (lua.globals().faceX, lua.globals().faceY) == (1, 2)
    assert "present=true" in lua.globals().lastReport.pairs[1].actor_visibility
    lua.execute("now=now+200;configure('combat',true);callbacks['AKRDevTools.encounterWatch']()")
    assert lua.globals().facingCalls == 3


def test_disposable_character_copy_updates_world_without_touching_source(tmp_path):
    import sqlite3
    from civilian_watch import copy_test_database

    source = tmp_path / "source.db"
    dest = tmp_path / "new-world" / "players.db"
    with sqlite3.connect(source) as db:
        db.execute("CREATE TABLE networkPlayers(world TEXT, username TEXT, data BLOB)")
        db.executemany(
            "INSERT INTO networkPlayers VALUES(?,?,?)",
            [("old", "akr", b"character"), ("unrelated", "other", b"keep")],
        )
    copy_test_database(source, dest, "old", "new")
    with sqlite3.connect(dest) as db:
        assert db.execute("SELECT * FROM networkPlayers").fetchall() == [
            ("new", "akr", b"character"),
            ("unrelated", "other", b"keep"),
        ]
    with sqlite3.connect(source) as db:
        assert db.execute(
            "SELECT world FROM networkPlayers WHERE username=?", ("akr",)
        ).fetchone() == ("old",)


def test_lifecycle_is_single_actor_not_added_to_default_batch():
    case = ops.planned_cases("lifecycle")[0]
    assert case == dict(scenario="LIFECYCLE", actors=1, seed=1, timeout_seconds=120, hold_seconds=8)
    with pytest.raises(ValueError):
        ops.planned_cases("lifecycle", 4)
    assert not any(c["scenario"] == "LIFECYCLE" for c in ops.planned_cases())


def test_each_viewer_has_independent_freshness_and_connection_identity():
    lua = server()
    lua.execute("""
        AKREncounterWatch.viewers={akr=1,eric=2}
        primary=p
        report('event',2)
        p={getUsername=function() return 'eric' end}
        guest=p
        report('event',2)
    """)
    assert lua.eval("AKREncounterReports.akr.player == primary")
    assert lua.eval("AKREncounterReports.eric.player == guest")
    assert lua.eval("AKREncounterReport.player == primary")
    lua.execute("""
        p={getUsername=function() return 'uninvited' end}
        report('event',2)
    """)
    assert lua.eval("AKREncounterReports.uninvited == nil")
    lua.execute("""
        p={getUsername=function() return 'eric' end}
        report('event',2)
    """)
    assert lua.eval("AKREncounterReports.eric.player == p")
    assert lua.eval("AKREncounterReports.eric.player ~= guest")


def test_house_batch_is_incremental_and_keeps_existing_regression():
    import civilian_encounter as encounters

    cases = encounters.planned_cases("house-batch")
    assert [(c["scenario"], c["actors"]) for c in cases] == [
        ("HOUSE_ROUTINE", 1),
        ("HOUSE_BLOCKED", 1),
        ("HOUSE_ROUTINE", 4),
    ]
    assert all(c["timeout_seconds"] == 300 for c in cases)
    assert len(encounters.planned_cases("regression")) == 9
