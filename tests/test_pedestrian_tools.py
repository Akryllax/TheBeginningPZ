"""Disposable deployment boundary and Lua admission/presentation checks; no native claims."""
from pathlib import Path
import socket
import sys
import threading

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import runtime_control


def test_receive_handles_fragmentation_and_disconnect():
    reader, writer = socket.socketpair()
    with reader, writer:
        reader.settimeout(1)
        def send():
            for piece in (b'a', b'bc', b'd'):
                writer.sendall(piece)
            writer.shutdown(socket.SHUT_WR)
        worker = threading.Thread(target=send)
        worker.start()
        assert runtime_control.receive(reader, 4) == b'abcd'
        with pytest.raises(ConnectionError):
            runtime_control.receive(reader, 1)
        worker.join()


def test_all_devtools_lua_parses():
    lua = LuaRuntime()
    for path in (ROOT / 'mods/AKRDevTools').rglob('*.lua'):
        lua.execute('assert(loadstring(...))', path.read_text())


def server_vm():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.globals().package.path = str(ROOT / 'mods/AKRCore/42/media/lua/shared/?.lua')
    lua.execute('''
      Events=setmetatable({}, {__index=function(t,k)
        local e={callbacks={}};e.Add=function(fn) e.callbacks[#e.callbacks+1]=fn end
        rawset(t,k,e);return e end})
      next=nil -- The game's Kahlua global library does not provide Lua's next().
      data={};ModData={getOrCreate=function(k) data[k]=data[k] or {};return data[k] end}
      BanditCustom={ClanGetAll=function() return {one={spawn={spawnChance=99}}} end}
      LofersNative={spawn=function(fn) return true,fn() end}
      registered=0;AKRRuntime={world='AKR_DayOne_Test_fixture',epoch='epoch',register=function(a,b)
        registered=registered+1;factory=a;retire=b;return true end}
      print=function() end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/Pedestrians.lua').read_text())
    return lua


def test_disposable_registration_and_random_spawn_suppression():
    lua = server_vm()
    lua.execute('Events.OnServerStarted.callbacks[1]()')
    assert lua.globals().registered == 1
    assert lua.eval('BanditCustom.ClanGetAll().one.spawn.spawnChance') == 0


def test_unresolved_saved_actor_prevents_restart_spawn():
    lua = server_vm()
    lua.execute("data.AKRPedestrianExperiment={pending={old={status='spawning'}}};Events.OnServerStarted.callbacks[1]()")
    assert lua.globals().registered == 0
    assert lua.eval("data.AKRPedestrianExperiment.pending.old.status") == 'spawning'


def test_normal_world_does_not_register_executor():
    lua = server_vm()
    lua.execute("AKRRuntime.world='AKR_DayOne';Events.OnServerStarted.callbacks[1]()")
    assert lua.globals().registered == 0


def test_missing_legacy_activation_is_detected_before_observer_test():
    lua = server_vm()
    lua.execute("LofersNative.spawn=function() return false,'spawn_not_authorized' end;Events.OnServerStarted.callbacks[1]()")
    assert lua.globals().registered == 0
    assert lua.eval("AKR.dispatch.failures['OnServerStarted:AKRDevTools.init']") == 1


def test_idle_lua_reload_replaces_handlers_and_keeps_original_clan_accessor():
    lua = server_vm()
    lua.execute('Events.OnServerStarted.callbacks[1]();original=AKRPedestrianServer.clanGetAll')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/Pedestrians.lua').read_text())
    assert lua.globals().registered == 2
    assert lua.eval('#Events.OnTick.callbacks') == 1
    assert lua.eval('#AKR.dispatch.order.OnTick') == 2
    assert lua.eval('AKRPedestrianServer.clanGetAll==original')


def presentation_vm():
    lua = server_vm()
    lua.execute('''
      clock=10000;getTimestampMs=function() return clock end
      gateReady=true
      package.loaded['AKRDevTools/Gate']={ready=function() return gateReady end,
        status=function() return gateReady and 'ready' or 'missing:OnZombieUpdate' end}
      ZombiePrograms={};BanditUtils={GetCharacterID=function(a) return a.outfit end}
      bodyCalls=0;sounds=0;visualCount=0
      Bandit={GetSkinTexture=function() return "MaleBody01a" end,
        ApplyBody=function() bodyCalls=bodyCalls+1;skin="MaleBody01a" end,
        SurpressZombieSounds=function() sounds=sounds+1 end}
      ItemVisual={new=function() return {setItemType=function() end,setClothingItemName=function() end} end}
      actor={outfit=7,md={},getOnlineID=function() return 9 end,
        getModData=function(self) return self.md end,getHumanVisual=function() return {getSkinTexture=function() return skin end,
          removeBlood=function() end,removeDirt=function() end,getBodyVisuals=function() return {clear=function() end} end} end,
        setFemaleEtc=function() end,getItemVisuals=function() return {
          size=function() return visualCount end,clear=function() visualCount=0 end,add=function() visualCount=visualCount+1 end} end,
        resetModelNextFrame=function() end,setVariable=function() end,setWalkType=function() end,
        setHealth=function() error('replica changed health') end,
        getInventory=function() error('replica touched inventory') end}
      cluster={[7]={female=false,clothing={Shirt='Base.Shirt_Denim'}}}
      GetBanditClusterData=function() return cluster end
      scanned=0;listSize=10000
      getCell=function() return {getZombieList=function() return {
        size=function() return listSize end,get=function(_,i) scanned=scanned+1;return actor end} end} end
      getSpecificPlayer=function() return nil end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/client/AKRDevTools/Presentation.lua').read_text())
    lua.execute('''
      snapshot=AKR.dispatch.handlers.OnServerCommand['AKRDevTools.snapshot']
      discover=AKR.dispatch.handlers.OnTick['AKRDevTools.discover']
      present=AKR.dispatch.handlers.OnZombieUpdate['AKRDevTools.present']
      snapshot('AKRDevTools','snapshot',{epoch='a',actors={{id='a.1',outfit=7,online_id=9}}})
    ''')
    return lua


def test_remote_presentation_discovery_is_bounded_and_cosmetic_only():
    lua = presentation_vm()
    lua.execute('discover()')
    assert lua.globals().scanned == 32
    assert lua.globals().bodyCalls == 1
    assert lua.globals().visualCount == 1
    assert lua.globals().sounds == 32
    lua.execute('discover()')
    assert lua.globals().scanned == 32


def test_replica_retries_late_brain_and_suppresses_voice_after_initialization():
    lua = presentation_vm()
    lua.execute('savedCluster=cluster;cluster={};present(actor)')
    assert lua.globals().bodyCalls == 0
    lua.execute('cluster=savedCluster;present(actor);present(actor)')
    assert lua.globals().bodyCalls == 1
    assert lua.globals().sounds == 2


def test_replica_rejects_stale_network_identity_and_failed_gate():
    lua = presentation_vm()
    lua.execute('gateReady=false;present(actor);discover()')
    assert lua.globals().bodyCalls == 0
    assert lua.globals().scanned == 0
    lua.execute("gateReady=true;actor.getOnlineID=function() return 10 end;present(actor)")
    assert lua.globals().bodyCalls == 0


def test_shared_gate_reload_preserves_captured_wrappers():
    lua = LuaRuntime()
    lua.execute('isServer=function() return true end')
    source = (ROOT / 'mods/AKRDevTools/42/media/lua/shared/AKRDevTools/Gate.lua').read_text()
    lua.execute(source)
    lua.execute("original=AKRPedestrianGate;AKRPedestrianGate.callbacks.test='retained'")
    lua.execute(source)
    assert lua.eval("AKRPedestrianGate==original and AKRPedestrianGate.callbacks.test=='retained'")


def test_deferred_outfit_reset_is_repaired_without_health_or_inventory_writes():
    lua = presentation_vm()
    lua.execute('present(actor);skin="ZombieSkin";visualCount=0;clock=clock+501;present(actor)')
    assert lua.globals().bodyCalls == 2
    assert lua.globals().skin == 'MaleBody01a'
    assert lua.globals().visualCount == 1
    lua.execute('clock=clock+501;present(actor)')
    assert lua.globals().bodyCalls == 2


def test_retirement_registry_acknowledgment_is_idempotent():
    lua = server_vm()
    lua.execute('Events.OnServerStarted.callbacks[1]()')
    # No native operation should be repeated once both Lua registries are empty.
    assert lua.eval("retire({}, 'already-retired')") is True
    lua.execute("data.AKRPedestrianExperiment.pending.active={status='active'}")
    lua.execute("other={getModData=function() return {AKRPedestrian={id='different'}} end}")
    assert lua.eval("retire(other, 'active')") is False


def test_watched_telemetry_requires_configured_count_and_order():
    lua = server_vm()
    lua.execute('''
        clock=1000;getTimestampMs=function() return clock end
        getServerName=function() return 'AKR_DayOne_Test_fixture' end
        AKRWatched={actor_count=32,epoch='epoch'}
        player={getOnlineID=function() return 7 end}
        entries={};for i=1,32 do entries[i]={id=4095+i,present=true,on_screen=false,female=false} end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/PoolWatch.lua').read_text())
    lua.execute('''
        local call=Events.OnClientCommand.callbacks[1]
        call('AKRDevTools','poolView',player,{epoch='epoch',actors=entries})
        assert(AKRPoolWatch[7].actors[4127].present)
        clock=2000;entries[32].id=5000
        call('AKRDevTools','poolView',player,{epoch='epoch',actors=entries})
        assert(AKRPoolWatch[7].received_ms==1000)
        entries[32].id=4127;AKRWatched.actor_count=4
        call('AKRDevTools','poolView',player,{epoch='epoch',actors=entries})
        assert(AKRPoolWatch[7].received_ms==1000)
        AKRWatched.actor_count=32
        call('AKRDevTools','poolView',player,{epoch='old',actors=entries})
        assert(AKRPoolWatch[7].received_ms==1000)
    ''')


def test_watched_hunter_perception_is_owner_and_exact_target_scoped():
    lua = server_vm()
    lua.execute('''
        clock=1000;getTimestampMs=function() return clock end
        instanceof=function(v,k) return v.kind==k end
        actors={}
        for id=4096,4159 do
            actors[id]={kind='IsoPlayer',getOnlineID=function() return id end,getX=function() return 10 end,getY=function() return 10 end}
        end
        getPlayerByOnlineID=function(id) return actors[id] end
        zombies={};calls=0
        for i=1,20 do
            local remote=i==2
            zombies[i]={getOnlineID=function() return i end,isRemoteZombie=function() return remote end,
                getTarget=function() return actors[4130+i] end,getX=function() return 9 end,getY=function() return 9 end,
                spotted=function(z,target) assert(target==actors[4130+i]);calls=calls+1 end}
        end
        AKRPoolWatchClient={count=64,epoch='epoch',hunters={}}
        for i=1,16 do AKRPoolWatchClient.hunters[i]=i end
        local list={size=function() return #zombies end,get=function(_,i) return zombies[i+1] end}
        getCell=function() return {getZombieList=function() return list end} end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/client/AKRDevTools/HunterPerception.lua').read_text())
    lua.execute('''
        Events.OnTick.callbacks[1]()
        assert(#AKRHunterPerception.pairs==15 and calls==15)
        -- Stale pair ceases effects as soon as its owner or target changes.
        zombies[1].getTarget=function() return actors[4096] end
        clock=1100;Events.OnTick.callbacks[1]()
        assert(calls==29)
    ''')


def test_chase_reports_reject_stale_epoch_revision_malformed_and_unknown():
    lua = server_vm()
    lua.execute('''
      clock=1000;getTimestampMs=function() return clock end
      getServerName=function() return 'AKR_DayOne_Test_fixture' end
      AKRChase={epoch='epoch',revision=2,candidates={{x=10,y=10,dir=1}}}
      player={getOnlineID=function() return 7 end}
      a={epoch='epoch',revision=2,x=0,y=0,candidates={{loaded=true,hidden=true}},
         actor={present=false,hidden=true},hunter={present='unknown',hidden=false}}
    ''')
    lua.execute((ROOT/'mods/AKRDevTools/42/media/lua/server/AKRDevTools/ChaseStaging.lua').read_text())
    lua.execute('''
      local send=Events.OnClientCommand.callbacks[1]
      send('AKRDevTools','chaseView',player,a)
      assert(AKRChaseReports[7].hunter.present=='unknown' and not AKRChaseReports[7].hunter.hidden)
      clock=2000;a.revision=1;send('AKRDevTools','chaseView',player,a)
      assert(AKRChaseReports[7].received==1000)
      a.revision=2;a.epoch='old';send('AKRDevTools','chaseView',player,a)
      assert(AKRChaseReports[7].received==1000)
      a.epoch='epoch';a.candidates[1].hidden='true';send('AKRDevTools','chaseView',player,a)
      assert(AKRChaseReports[7].received==1000)
      a.candidates[1].hidden=true;a.x=0/0;send('AKRDevTools','chaseView',player,a)
      assert(AKRChaseReports[7].received==1000)
    ''')


def test_chase_client_distinguishes_projection_visibility_loading_and_absence():
    lua = server_vm()
    lua.execute('''
      clock=1000;getTimestampMs=function() return clock end
      seen=true;loaded=true;projection=100;zombieCount=0
      player={getPlayerNum=function() return 0 end,getX=function() return 0 end,getY=function() return 0 end}
      getSpecificPlayer=function() return player end
      getPlayerByOnlineID=function() return nil end
      getCore=function() return {getZoom=function() return 1 end,getScreenWidth=function() return 800 end,getScreenHeight=function() return 600 end} end
      IsoUtils={XToScreen=function() return projection end,YToScreen=function() return projection end}
      getCameraOffX=function() return 0 end;getCameraOffY=function() return 0 end
      local sq={isCanSee=function() return seen end}
      getCell=function() return {getGridSquare=function() if loaded then return sq end end,
        getZombieList=function() return {size=function() return zombieCount end,get=function() return {getOnlineID=function() return -1 end} end} end} end
      sendClientCommand=function(p,m,c,a) result=a end
    ''')
    lua.execute((ROOT/'mods/AKRDevTools/42/media/lua/client/AKRDevTools/ChaseStaging.lua').read_text())
    lua.execute('''
      Events.OnServerCommand.callbacks[1]('AKRDevTools','chaseConfig',{epoch='epoch',revision=2,candidates={{x=10,y=10,dir=1}}})
      Events.OnTick.callbacks[1]()
      assert(result.candidates[1].loaded and not result.candidates[1].hidden)
      assert(result.actor.present==false and result.hunter.present==false)
      seen=false;clock=2000;Events.OnTick.callbacks[1]()
      assert(result.candidates[1].hidden) -- same viewport, occluded by wall
      loaded=false;clock=3000;Events.OnTick.callbacks[1]()
      assert(not result.candidates[1].loaded and not result.candidates[1].hidden) -- in viewport, unknown
      projection=3000;clock=4000;Events.OnTick.callbacks[1]()
      assert(not result.candidates[1].loaded and result.candidates[1].hidden) -- cannot be simulation owner
      zombieCount=65;clock=5000;Events.OnTick.callbacks[1]()
      assert(result.hunter.present=='unknown' and not result.hunter.hidden) -- capped scan is not absence
    ''')


def test_resident_dependency_manifest_uses_game_comma_separator():
    manifest=(ROOT/'mods/AKRResidents/42/mod.info').read_text().splitlines()
    deps=next(line.split('=',1)[1] for line in manifest if line.startswith('require='))
    assert deps.split(',')==['AKRCore','AKRPopulation']


def test_crowd_telemetry_accounts_for_all_hunters_and_rejects_old_wave():
    lua = server_vm()
    lua.execute('''
        clock=1000;getTimestampMs=function() return clock end
        getServerName=function() return 'AKR_DayOne_Test_fixture' end
        AKRWatched={actor_count=64,hunter_limit=128,epoch='epoch',wave=1,hunters={}}
        player={getOnlineID=function() return 7 end}
        a={epoch='epoch',wave=1,observer_x=10,observer_y=20,stage={loaded=true,hidden=true},actors={},hunters={}}
        for i=1,64 do a.actors[i]={id=4095+i,present=true,on_screen=false,visible=false,hidden=true,loaded=true} end
        for i=1,128 do
            AKRWatched.hunters[i]=i
            a.hunters[i]={id=i,present=true,on_screen=false,visible=false,hidden=true,loaded=true}
        end
        a.hunters[128].present='unknown';a.hunters[128].hidden=false
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/PoolWatch.lua').read_text())
    lua.execute('''
        local send=Events.OnClientCommand.callbacks[1]
        send('AKRDevTools','poolView',player,a)
        assert(AKRPoolWatch[7].hunters[128].present=='unknown' and not AKRPoolWatch[7].hunters[128].hidden)
        clock=2000;a.wave=0;send('AKRDevTools','poolView',player,a)
        assert(AKRPoolWatch[7].received_ms==1000)
        a.wave=1;a.hunters[128].id=127;send('AKRDevTools','poolView',player,a)
        assert(AKRPoolWatch[7].received_ms==1000)
        a.hunters[128].id=128;a.stage.loaded='true';send('AKRDevTools','poolView',player,a)
        assert(AKRPoolWatch[7].received_ms==1000)
    ''')


def test_crowd_perception_handles_128_and_keeps_vanilla_spotting_probability():
    lua = server_vm()
    lua.execute('''
        clock=1000;getTimestampMs=function() return clock end
        instanceof=function(v,k) return v.kind==k end
        actors={};zombies={};calls=0;scans=0
        for id=4096,4159 do
            actors[id]={kind='IsoPlayer',getOnlineID=function() return id end,getX=function() return 10 end,getY=function() return 10 end}
        end
        getPlayerByOnlineID=function(id) return actors[id] end
        for i=1,256 do
            local target=actors[4096+(i-1)%64]
            zombies[i]={getOnlineID=function() return i end,isRemoteZombie=function() return i==128 end,
                getTarget=function() return target end,getX=function() return 9 end,getY=function() return 9 end,
                spotted=function(z,t,forced) assert(t==target and forced==false);calls=calls+1 end}
        end
        AKRPoolWatchClient={count=64,crowd=true,epoch='epoch',hunters={}}
        for i=1,128 do AKRPoolWatchClient.hunters[i]=i end
        local list={size=function() return 10000 end,get=function(_,i) scans=scans+1;return zombies[i+1] end}
        getCell=function() return {getZombieList=function() return list end} end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/client/AKRDevTools/HunterPerception.lua').read_text())
    lua.execute('''
        Events.OnTick.callbacks[1]()
        assert(#AKRHunterPerception.pairs==127 and calls==127 and scans==256)
        zombies[1].getTarget=function() return actors[4097] end
        clock=1100;Events.OnTick.callbacks[1]()
        assert(calls==253 and scans==256)
    ''')


def test_pool_visibility_fails_closed_for_unloaded_and_scan_overflow():
    lua = LuaRuntime()
    lua.execute('''
        seen=true;loaded=true;screen=10
        getCore=function() return {getZoom=function() return 1 end,getScreenWidth=function() return 100 end,getScreenHeight=function() return 100 end} end
        IsoUtils={XToScreen=function() return screen end,YToScreen=function() return screen end}
        getCameraOffX=function() return 0 end;getCameraOffY=function() return 0 end
        getCell=function() return {getGridSquare=function() if loaded then return {isCanSee=function() return seen end} end end} end
        player={getPlayerNum=function() return 0 end}
    ''')
    module = lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/client/AKRDevTools/PoolVisibility.lua').read_text())
    lua.globals().V = module
    lua.execute('''
        local l,h=V.footprint(1,2,player);assert(l and not h)
        loaded=false;l,h=V.footprint(1,2,player);assert(not l and not h)
        screen=1000;l,h=V.footprint(1,2,player);assert(not l and h)
        screen=10;loaded=true;seen=false;l,h=V.footprint(1,2,player);assert(l and h)
        local b=V.body(nil,player,128,true,true);assert(b.present=='unknown' and not b.hidden)
        b=V.body(nil,player,128,false,true);assert(b.present==false and b.hidden)
    ''')
