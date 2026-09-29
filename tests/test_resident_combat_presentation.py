"""Ordinary-client presentation must be scoped and repeat once for each server action."""

from pathlib import Path
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]


def test_action_audio_repeats_without_duplicating_native_effects():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.globals().package.path = str(ROOT / "mods/AKRCore/42/media/lua/shared/?.lua")
    lua.execute("""
        Events=setmetatable({}, {__index=function(t,k)
            local e={callbacks={}};e.Add=function(fn) e.callbacks[#e.callbacks+1]=fn end
            rawset(t,k,e);return e end})
        now=1000;sounds=0;getTimestampMs=function() return now end
        player={};getSpecificPlayer=function() return player end
        actor={getOnlineID=function() return 4096 end,
            playSoundLocal=function() sounds=sounds+1 end,playerVoiceSound=function() end,
            setMeleeHitSurface=function() end}
        target={getOnlineID=function() return 7 end}
        getPlayerByOnlineID=function(id) return id==4096 and actor or nil end
        weapon={getSwingSound=function() return 'HammerSwing' end,getZombieHitSound=function() return 'HammerHit' end}
    """)
    lua.execute(
        (
            ROOT / "mods/AKRResidents/42/media/lua/client/AKRResidents/CombatPresentation.lua"
        ).read_text()
    )
    lua.execute("""
        function publish(id)
            Events.OnServerCommand.callbacks[1]('AKRResidents','combatPresentation',
                {epoch='boot',rows={{actor=4096,target=7,action=id}}})
        end
        function swing() Events.OnWeaponSwing.callbacks[1](actor,weapon) end
        function hit() Events.OnWeaponHitCharacter.callbacks[1](actor,target,weapon) end
        swing();hit();assert(sounds==0)
        publish('one');swing();hit();swing();hit();assert(sounds==2)
        publish('one');swing();hit();assert(sounds==2)
        publish('two');swing();hit();assert(sounds==4)
        publish('three');now=5001;swing();hit();assert(sounds==4)
        now=6000;publish('four');target.getOnlineID=function() return 8 end
        hit();assert(sounds==4)
        getPlayerByOnlineID=function() return {} end;swing();assert(sounds==4)
    """)
