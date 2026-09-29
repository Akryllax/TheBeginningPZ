"""Diagnostic observations are bounded and scoped to the disposable observer session."""
from pathlib import Path
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]


def fixture():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.globals().package.path = str(ROOT / 'mods/AKRCore/42/media/lua/shared/?.lua')
    lua.execute('''
        Events=setmetatable({}, {__index=function(t,k)
            local e={callbacks={}};e.Add=function(fn) e.callbacks[#e.callbacks+1]=fn end
            rawset(t,k,e);return e end})
        now=1000;getTimestampMs=function() return now end
        world="AKR_DayOne_Test_fixture";getServerName=function() return world end
        AKRRuntime={epoch="current"};AKRCombatWatch={epoch="current"}
        player={getUsername=function() return "akr" end}
        sent=0;sendServerCommand=function() sent=sent+1 end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/CombatWatch.lua').read_text())
    lua.execute('''
        function report(epoch)
            Events.OnClientCommand.callbacks[1]("AKRDevTools","combatView",player,
                {epoch=epoch,actor_present=true,target_present=true,attack_animation_flag_seen=true,saw_reaction=false,
                 actor_state=string.rep("x",500),target_state="idle",target_health=1})
        end
    ''')
    return lua


def test_report_rejects_old_epoch_wrong_observer_and_real_world():
    lua = fixture()
    lua.execute('report("previous")')
    assert lua.globals().AKRCombatReport is None
    lua.execute('world="AKR_DayOne";report("current")')
    assert lua.globals().AKRCombatReport is None
    lua.execute('world="AKR_DayOne_Test_fixture";player.getUsername=function() return "other" end;report("current")')
    assert lua.globals().AKRCombatReport is None


def test_report_is_bounded_and_rate_limited():
    lua = fixture()
    lua.execute('report("current")')
    assert len(lua.globals().AKRCombatReport.actor_state) == 80
    lua.execute('now=1040;report("current")')
    assert lua.globals().AKRCombatReport.received_ms == 1000
    lua.execute('now=1100;report("current")')
    assert lua.globals().AKRCombatReport.received_ms == 1100


def test_config_only_broadcasts_for_current_disposable_session():
    lua = fixture()
    lua.execute('world="AKR_DayOne";Events.OnTick.callbacks[1]()')
    assert lua.globals().sent == 0
    lua.execute('world="AKR_DayOne_Test_fixture";AKRRuntime.epoch="old";Events.OnTick.callbacks[1]()')
    assert lua.globals().sent == 0
    lua.execute('AKRRuntime.epoch="current";Events.OnTick.callbacks[1]();Events.OnTick.callbacks[1]()')
    assert lua.globals().sent == 1


def test_observer_loadout_is_authorized_idempotent_and_refills_next_session():
    lua = fixture()
    lua.execute('''
        AKRPedestrianClient={epoch="one"}
        items={};acks=0
        inv={getItems=function() return {size=function() return #items end,get=function(self,i) return items[i+1] end} end}
        function inv:AddItem(kind)
            local item={kind=kind,md={},ammo=0}
            function item:getFullType() return self.kind end
            function item:getModData() return self.md end
            function item:getMaxAmmo() return 15 end
            function item:getCurrentAmmoCount() return self.ammo end
            function item:setCurrentAmmoCount(n) self.ammo=n end
            function item:setContainsClip(v) self.clip=v end
            function item:setRoundChambered(v) self.chamber=v end
            function item:isRoundChambered() return self.chamber end
            function item:setJammed(v) self.jammed=v end
            function item:getMagazineType() return "Base.9mmClip" end
            items[#items+1]=item;return item
        end
        player.getAccessLevel=function() return "admin" end
        player.getInventory=function() return inv end
        player.setPrimaryHandItem=function(self,item) self.primary=item end
        player.setSecondaryHandItem=function() end
        getSpecificPlayer=function() return player end
        sendClientCommand=function() acks=acks+1 end
        print=function() end
    ''')
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/client/AKRDevTools/ObserverLoadout.lua').read_text())
    lua.execute('''function loadout(epoch)
        Events.OnServerCommand.callbacks[1]("AKRDevTools","observerLoadout",{epoch=epoch})
    end''')
    lua.execute('loadout("old")')
    assert lua.eval('#items') == 0
    lua.execute('loadout("one");items[1].ammo=2;loadout("one")')
    assert lua.eval('#items') == 4
    assert lua.eval('items[1].ammo') == 2  # No perpetual ammo cheat.
    assert lua.eval('items[1].clip and items[1].chamber and not items[1].jammed')
    lua.execute('AKRPedestrianClient.epoch="two";loadout("two")')
    assert lua.eval('#items') == 4
    assert lua.eval('items[1].ammo') == 15
    assert lua.eval('items[2].ammo+items[3].ammo') == 30
    lua.execute("""
        items[1].ammo=0
        Events.OnServerCommand.callbacks[1]("AKRDevTools","observerLoadout",{epoch="two",event="next-case"})
    """)
    assert lua.eval('items[1].ammo') == 15
    assert lua.eval('#items') == 4


def test_loadout_ack_is_scoped_to_current_player_and_event():
    lua = fixture()
    lua.execute("""
        AKREncounterWatch={epoch='current',event='case-1'}
        player.getAccessLevel=function() return 'admin' end
        player.getOnlineID=function() return 1 end
        getOnlinePlayers=function() return {size=function() return 1 end,get=function() return player end} end
        requests=0;sendServerCommand=function(p,module,command) if command=="observerLoadout" then requests=requests+1 end end
        now=3000
    """)
    lua.execute((ROOT / 'mods/AKRDevTools/42/media/lua/server/AKRDevTools/ObserverLoadout.lua').read_text())
    lua.execute("""
        function grant(event)
            Events.OnClientCommand.callbacks[1]('AKRDevTools','observerLoadoutReady',player,
                {epoch='current',event=event,loaded=true})
        end
        grant('old');Events.OnTick.callbacks[1]()
    """)
    assert lua.globals().AKRObserverLoadoutReady is None
    assert lua.globals().requests == 1
    lua.execute("grant('case-1');now=6000;Events.OnTick.callbacks[1]()")
    assert lua.globals().requests == 1
    lua.execute("AKREncounterWatch.event='case-2';now=9000;Events.OnTick.callbacks[1]()")
    assert lua.globals().requests == 2
    lua.execute("""
        grant('case-2')
        local old=player
        player={getUsername=old.getUsername,getAccessLevel=old.getAccessLevel,getOnlineID=old.getOnlineID}
        now=12000;Events.OnTick.callbacks[1]()
    """)
    assert lua.globals().requests == 3
