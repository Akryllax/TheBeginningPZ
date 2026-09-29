"""AKRCore contract tests in the actual Lua 5.1 dialect. Mocks do not certify in-game loading."""

from pathlib import Path

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT / "mods/AKRCore"
LUA = MOD / "42/media/lua"


@pytest.fixture
def lua():
    vm = LuaRuntime(unpack_returned_tuples=True)
    vm.globals().package.path = str(LUA / "shared" / "?.lua")
    vm.execute("""
        V=require 'AKRCore/Version';R=require 'AKRCore/Registry';D=require 'AKRCore/Dispatch'
        S=require 'AKRCore/Store';C=require 'AKRCore/Capabilities';K=require 'AKRCore/Core'
        function fakeEvents()
            local added={}
            local events=setmetatable({},{__index=function(t,name)
                local e={subs={}};e.Add=function(fn) e.subs[#e.subs+1]=fn;added[#added+1]=name end
                rawset(t,name,e);return e end})
            return events,added
        end
    """)
    return vm


def test_sources_parse_and_mod_metadata(lua):
    for path in LUA.rglob("*.lua"):
        lua.execute("assert(loadstring(...))", path.read_text())
    info = dict(
        line.split("=", 1) for line in (MOD / "42/mod.info").read_text().splitlines() if "=" in line
    )
    assert info["id"] == "AKRCore" and "require" not in info
    version = (MOD / "VERSION").read_text().strip()
    assert version in info["description"]
    assert f'version="{version}"' in (LUA / "shared/AKRCore/Core.lua").read_text()


def test_version_compatibility(lua):
    lua.execute("""
        assert(V.satisfies('1.4.2',1,'1.2.0'))
        local ok,why=V.satisfies('2.0.0',1);assert(not ok and why=='major_mismatch')
        ok,why=V.satisfies('1.1.9',1,'1.2.0');assert(not ok and why=='too_old')
        assert(V.parse('1.2')==nil and V.parse('x.1.2')==nil)
    """)


def test_registry_replaces_on_reload_and_reports_mismatch(lua):
    lua.execute("""
        local r=R.new()
        assert(R.provide(r,'Outbreak','1.0.0',{v=1}))
        assert(R.provide(r,'Outbreak','1.1.0',{v=2}))
        assert(#R.list(r)==1 and R.require(r,'Outbreak',1,'1.1.0').v==2)
        local api,why=R.require(r,'Outbreak',2);assert(api==nil and why=='major_mismatch')
        api,why=R.require(r,'Traffic',1);assert(api==nil and why=='missing')
        api,why=R.require(r,'Outbreak',1,'1.2.0');assert(api==nil and why=='too_old')
        assert(not R.provide(r,'Bad','1',{}))
    """)


def test_dispatch_subscribes_once_replaces_and_isolates_failures(lua):
    lua.execute("""
        local events,added=fakeEvents();local logs={}
        local d=D.new(events,function(m) logs[#logs+1]=m end)
        local hits={}
        assert(D.on(d,'OnTick','a',function() hits[#hits+1]='a1' end))
        assert(D.on(d,'OnTick','b',function() error('boom') end))
        assert(D.on(d,'OnTick','c',function(x) hits[#hits+1]='c'..x end))
        assert(D.on(d,'OnTick','a',function() hits[#hits+1]='a2' end))  -- reload
        assert(#added==1 and #events.OnTick.subs==1)
        events.OnTick.subs[1](7)
        assert(hits[1]=='a2' and hits[2]=='c7' and #hits==2)
        assert(d.failures['OnTick:b']==1 and #logs==1)
        assert(D.off(d,'OnTick','b'));events.OnTick.subs[1](8)
        assert(#hits==4 and d.failures['OnTick:b']==1)
        local ok,why=D.on(D.new(nil),'OnTick','x',function() end);assert(not ok and why=='unknown_event')
    """)


def test_store_creates_migrates_and_refuses_downgrade(lua):
    lua.execute("""
        local root={}
        local s=assert(S.open(root,'Outbreak',1,nil,function() return {day=0} end))
        s.day=3
        local m={[1]=function(st) st.hours=st.day*24;return true end}
        s=assert(S.open(root,'Outbreak',2,m))
        assert(s.hours==72 and root.modules.Outbreak.schema==2)
        local v,why=S.open(root,'Outbreak',1,m);assert(v==nil and why=='newer_schema')
        v,why=S.open(root,'Outbreak',3,m);assert(v==nil and why=='missing_migration_2')
        assert(root.modules.Outbreak.schema==2)
        local legacy={schema=2,residents={}}
        local adopted=assert(S.open(root,'Residents',2,nil,nil,legacy))
        assert(adopted==legacy and root.modules.Residents.adopted_legacy)
    """)


def test_capabilities_read_new_and_legacy_bridges(lua):
    lua.execute("""
        local c=C.new()
        assert(C.readNative(c,{capabilities=function() return {population='1.0.0',vehicles='0.3.0'} end})==2)
        assert(C.has(c,'native.population',1))
        local ok,why=C.has(c,'native.vehicles',1);assert(not ok and why=='major_mismatch')
        assert(C.readNative(c,{versionReady=function() return true end})==1)
        assert(C.has(c,'native.legacy',0) and not C.has(c,'native.population',1))
        assert(C.readNative(c,{versionReady=function() error('down') end})==0)
        assert(#C.list(c)==0)
    """)


def test_core_instance_survives_reload(lua):
    lua.execute("""
        local events=fakeEvents();Events=events
        local a=K.instance();a.marker=true
        package.loaded['AKRCore/Core']=nil
        local K2=require 'AKRCore/Core'
        assert(K2.instance()==a and a.marker)
        assert(R.require(a.registry,'AKRCore',0,'0.1.0')==K)
    """)
