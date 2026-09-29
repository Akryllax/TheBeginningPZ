"""Domain safety and operation-budget tests run in the actual Lua 5.1 dialect."""

from pathlib import Path

import pytest
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
LUA = ROOT / "mods/AKRStoryteller/42/media/lua"


@pytest.fixture
def lua():
    runtime = LuaRuntime(unpack_returned_tuples=True)
    paths = ";".join(str(LUA / side / "?.lua") for side in ("shared", "server", "client"))
    runtime.globals().package.path = paths + ";" + runtime.globals().package.path
    runtime.execute("Core=require 'AKRStoryteller/Core'; C=require 'AKRStoryteller/Config'")
    return runtime


def test_all_sources_parse_as_lua51(lua):
    for path in LUA.rglob("*.lua"):
        lua.execute("assert(loadstring(...))", path.read_text())


def test_floors_negative_coordinates_and_cell_eviction(lua):
    lua.execute("""
        C.maxCells=2
        local s=Core.new(0)
        local a=Core.cell(s,-0.1,32,0,0)
        assert(a.x==-32 and a.y==32)
        local b=Core.cell(s,-0.1,32,1,0)
        assert(a.key~=b.key)
        Core.item(s,'old-item',a,'weapon',0)
        Core.cell(s,128,128,0,0)
        local replacement=Core.cell(s,-0.1,32,0,0)
        assert(replacement.generation~=a.generation)
        Core.item(s,'new-item',replacement,'weapon',0)
        Core.item(s,'old-item',replacement,'weapon',0)
        assert(replacement.stock.weapon==8) -- old generation cannot subtract new stock
        assert(#s.cell_order==2 and s.dropped_cells==2)
    """)


def test_items_are_deduplicated_on_repeat_transfer_and_eviction(lua):
    lua.execute("""
        C.maxItems=2
        local s=Core.new(0)
        local a=Core.cell(s,0,0,0,0)
        local b=Core.cell(s,64,0,0,0)
        Core.item(s,1,a,'food',0)
        Core.item(s,1,a,'food',0)
        assert(a.stock.food==1)
        Core.item(s,1,b,'food',1)
        assert(a.stock.food==0 and b.stock.food==1)
        Core.item(s,2,a,'weapon',1)
        Core.item(s,3,a,'medical',1)
        assert(b.stock.food==0 and #s.item_order==2 and s.items['1']==nil)
        assert(a.stock_complete==false)
    """)


def test_unloaded_evidence_decays_without_asserting_loss(lua):
    lua.execute("""
        local s=Core.new(0)
        local c=Core.presence(s,'p',0,0,0,0,100)
        Core.item(s,1,c,'weapon',0)
        local before=Core.score(c,0)
        local later=Core.score(c,72)
        assert(later.wealth>0 and later.wealth<before.wealth)
        assert(c.stock.weapon==4 and c.stock_complete==false)
        assert(s.recovery_until==0)
    """)


def test_offline_time_does_not_create_dwell_or_budget_burst(lua):
    lua.execute("""
        local s=Core.new(0)
        Core.presence(s,'p',0,0,0,0,100)
        local c=Core.presence(s,'p',0,0,0,1000,100)
        assert(c.dwell<=1/12)
        Core.evaluate(s,1000,{online=0})
        assert(s.budget==0 and #s.decisions==0)
        Core.evaluate(s,1000.1,{online=1,capability=1,wealth=1,major=0,npc_known=false})
        assert(#s.decisions==0 and s.budget<0.1)
    """)


def test_grace_recovery_cooldown_npc_cap_and_active_encounter(lua):
    lua.execute("""
        local s=Core.new(0)
        s.pressure=1;s.budget=24
        local e=Core.events[5]
        local ctx={online=2,npcs=0,npc_known=true,major=0}
        local ok,why=Core.eligibility(s,e,1,ctx)
        assert(not ok and why=='new_world_grace')
        Core.loss(s,25,'player_death')
        ok,why=Core.eligibility(s,e,26,ctx)
        assert(not ok and why=='recovery')
        s.last_hostile=50
        ok,why=Core.eligibility(s,e,52,ctx)
        assert(not ok and why=='hostile_cooldown')
        ctx.major=1
        ok,why=Core.eligibility(s,e,57,ctx)
        assert(not ok and why=='major_event_active')
        ctx.major=0;ctx.npcs=22
        ok,why=Core.eligibility(s,e,57,ctx)
        assert(not ok and why=='npc_cap')
        ctx.npc_known=false
        ok,why=Core.eligibility(s,e,57,ctx)
        assert(not ok and why=='npc_count_unknown')
        ctx.npc_known=true;ctx.npcs=0
        assert(Core.eligibility(s,e,57,ctx))
    """)


def test_large_health_drop_gives_recovery_and_small_changes_do_not(lua):
    lua.execute("""
        local s=Core.new(0)
        Core.presence(s,'p',0,0,0,0,100)
        Core.presence(s,'p',0,0,0,1,99)
        assert(s.recovery_until==0)
        Core.presence(s,'p',0,0,0,2,50)
        assert(s.recovery_until==26)
    """)


def test_gradual_serious_injury_enters_recovery_once(lua):
    lua.execute("""
        local s=Core.new(0)
        Core.presence(s,'p',0,0,0,0,70)
        Core.presence(s,'p',0,0,0,1,65)
        assert(s.recovery_until==0)
        Core.presence(s,'p',0,0,0,2,59)
        assert(s.recovery_until==26)
        Core.presence(s,'p',0,0,0,3,58)
        assert(s.recovery_until==26)
    """)


def test_phase_edges_and_deterministic_random_restart(lua):
    lua.execute("""
        local a=Core.new(100,123)
        assert(Core.phase(a,100+7*24)=='outbreak')
        assert(Core.phase(a,100+8*24)=='aftermath')
        assert(Core.phase(a,100+31*24)=='survival')
        for i=1,10 do Core.random(a) end
        local restored=Core.new(100,a.seed)
        for i=1,30 do assert(Core.random(a)==Core.random(restored)) end
    """)


def test_observation_mode_never_invokes_adapter(lua):
    lua.execute("""
        local s=Core.new(0)
        s.budget=24
        Core.events={{name='test',cost=1,npcs=0,hostile=false,
            weights={outbreak=1,aftermath=1,survival=1}}}
        local calls=0
        local ctx={online=1,npcs=0,npc_known=true,major=0,capability=1,wealth=1}
        local d=Core.evaluate(s,2,ctx,{test=function() calls=calls+1;return true end})
        assert(d.outcome=='preview' and d.reason=='observation_only' and calls==0)
        assert(s.budget==24)
        assert(next(s.active_events)==nil)
    """)


def test_uncertain_spawn_is_reserved_and_never_automatically_retried(lua):
    lua.execute("""
        local s=Core.new(0)
        s.mode='active';s.budget=24;s.pressure=1
        Core.events={{name='test',cost=1,npcs=0,hostile=true,
            weights={outbreak=1,aftermath=1,survival=1}}}
        local calls=0
        local ctx={online=1,npcs=0,npc_known=true,major=0,capability=1,wealth=1}
        local d=Core.evaluate(s,30,ctx,{test=function() calls=calls+1;error('uncertain') end})
        assert(d.reason=='adapter_error' and s.active_events[d.id].status=='uncertain')
        ctx.major=1
        local nextd=Core.evaluate(s,40,ctx,{test=function() calls=calls+1 end})
        assert(nextd.reason=='major_event_active' and calls==1)
        assert(Core.complete(s,d.id,41,'verified_removed'))
        assert(not Core.complete(s,d.id,41,'duplicate'))
    """)


def test_snapshot_bounds_and_detached_tables(lua):
    lua.execute("""
        local s=Core.new(0)
        for i=1,100 do Core.cell(s,i*32,0,0,0);Core.record(s,'event','preview','reason',0) end
        local snap=Core.snapshot(s,1,{online=2,npc_known=false},{p95=1})
        assert(#snap.cells==64 and #snap.decisions==32 and snap.npc_count==-1)
        snap.cells[1].wealth=500;snap.decisions[1].reason='changed'
        assert(s.cells[s.cell_order[1]].stock.wealth==nil)
        assert(s.decisions[1].reason=='reason')
        local rotated=Core.snapshot(s,1,{online=2,npc_known=false},{},65)
        assert(rotated.cells[1].x==65*32)
    """)


INVENTORY_FIXTURE = """
Scanner=require 'AKRStoryteller/Scanner'
function container(items)
    local list={size=function() return #items end,get=function(self,i) return items[i+1] end}
    return {getItems=function() return list end}
end
function item(id,category,inside)
    return {getID=function() return id end,getCategory=function() return category end,
        getFullType=function() return 'Base.'..category end,getInventory=function() return inside end,
        is_container=inside~=nil}
end
function isContainer(i) return i.is_container end
"""


def test_nested_inventory_deduplication_and_operation_limit(lua):
    lua.execute(INVENTORY_FIXTURE)
    lua.execute("""
        local s=Core.new(0)
        local c=Core.cell(s,0,0,0,0)
        local food=item(2,'Food')
        local bag=item(1,'Other',container({food}))
        local inv=container({bag,food}) -- same item also at root: no duplicate value
        local q=Scanner.new()
        Scanner.enqueue(q,inv,c)
        assert(not Scanner.enqueue(q,inv,c))
        local n=Scanner.drain(q,s,0,function() return 0 end,1,isContainer,2)
        assert(n==2 and #q.queue==1)
        Scanner.drain(q,s,0,function() return 0 end,1,isContainer,32)
        assert(#q.queue==0 and c.stock.food==1 and c.stock.other==0.2)
    """)


def test_inventory_deadline_and_unloading_preserve_unknown_state(lua):
    lua.execute(INVENTORY_FIXTURE)
    lua.execute("""
        local s=Core.new(0)
        local c=Core.cell(s,0,0,0,0)
        Core.item(s,'known',c,'weapon',0)
        local q=Scanner.new()
        local inv={getItems=function() error('unloaded') end}
        Scanner.enqueue(q,inv,c)
        local n=Scanner.drain(q,s,1,function() return 1 end,1,isContainer,32)
        assert(n==0 and #q.queue==1)
        Scanner.drain(q,s,1,function() return 0 end,1,isContainer,32)
        assert(q.failures==1 and #q.queue==0)
        assert(c.stock.weapon==4 and c.stock_complete==false and s.recovery_until==0)
    """)


def test_native_schedulers_remain_disabled_after_clan_reload(lua):
    lua.execute("""
        local B=require 'AKRStoryteller/Bandits'
        local clans={a={spawn={spawnChance=100}}}
        BanditCustom={ClanGetAll=function() return clans end}
        assert(B.install())
        assert(BanditCustom.ClanGetAll().a.spawn.spawnChance==0)
        clans={b={spawn={spawnChance=50}}}
        assert(BanditCustom.ClanGetAll().b.spawn.spawnChance==0)
        B.maxClans=1
        clans.c={spawn={spawnChance=100}}
        assert(next(BanditCustom.ClanGetAll())==nil)
    """)


def test_wandering_zombies_companion_skips_only_bandits(lua):
    lua.execute("""
        local callback
        Events={OnGameStart={Add=function(f) callback=f end}}
        isClient=function() return true end
        local calls=0
        wzIsValidZombie=function() calls=calls+1;return true end
        require 'AKRStoryteller/WanderingGuard'
        callback()
        assert(not wzIsValidZombie({getVariableBoolean=function() return true end}))
        assert(calls==0)
        assert(wzIsValidZombie({getVariableBoolean=function() return false end}))
        assert(calls==1)
    """)


SERVER_FIXTURE = (
    INVENTORY_FIXTURE
    + """
handlers={}
Events=setmetatable({}, {__index=function(self,k)
    local event={Add=function(fn) handlers[k]=handlers[k] or {};table.insert(handlers[k],fn) end}
    rawset(self,k,event);return event
end})
function fire(name,argument)
    for _,f in ipairs(handlers[name] or {}) do f(argument) end
end
isClient=function() return false end
store={}
ModData={getOrCreate=function(key) store[key]=store[key] or {};return store[key] end}
nowMs=10000;worldHours=0;health=100;dead=false;online=true
getTimestampMs=function() return nowMs end
GameTime={getServerTime=function() return nowMs*1000000 end}
getGameTime=function() return {getWorldAgeHours=function() return worldHours end} end
BanditCustom={ClanGetAll=function() return {one={spawn={spawnChance=100}}} end}
local inventory=container({item(1,'Food'),item(2,'Weapon')})
local storage=container({item(3,'Medical')})
local generator={kind='IsoGenerator',getContainerCount=function() return 1 end,
    getContainerByIndex=function() return storage end}
local objects={size=function() return 1 end,get=function() return generator end}
local square={getX=function() return 100 end,getY=function() return 100 end,
    getZ=function() return 0 end,getObjects=function() return objects end}
local cell={getGridSquare=function() return square end}
local vehicle={getId=function() return 22 end}
player={getUsername=function() return 'test' end,isDead=function() return dead end,
    getX=function() return 100 end,getY=function() return 100 end,getZ=function() return 0 end,
    getBodyDamage=function() return {getOverallBodyHealth=function() return health end} end,
    getInventory=function() return inventory end,getVehicle=function() return vehicle end}
getOnlinePlayers=function() return {size=function() return online and 1 or 0 end,
    get=function() return player end} end
getCell=function() return cell end
instanceof=function(obj,kind) return obj.kind==kind or (kind=='InventoryContainer' and obj.is_container) end
require 'AKRStoryteller/Server'
fire('OnInitGlobalModData')
fire('OnServerStarted')
function advance(ticks)
    for i=1,ticks do nowMs=nowMs+100;worldHours=worldHours+1/3600;fire('OnTick') end
end
"""
)


def test_server_first_join_reads_loaded_inventory_and_publishes(lua):
    lua.execute(SERVER_FIXTURE)
    lua.execute("""
        advance(150)
        local root=store.AKRStoryteller
        local cell=root.state.cells['3:3:0']
        assert(math.abs(cell.stock.food-1)<0.001 and math.abs(cell.stock.weapon-4)<0.001)
        assert(math.abs(cell.stock.medical-3)<0.001)
        assert(math.abs(cell.infrastructure-1)<0.001 and math.abs(cell.vehicles-1)<0.001)
        assert(root.telemetry.health=='observing_native_spawns_disabled')
        assert(root.telemetry.online_players==1 and root.telemetry.mode=='observe')
        assert(root.telemetry.npc_count==-1 and #root.telemetry.cells==1)
        assert(next(root.state.active_events)==nil)
    """)


def test_server_death_disconnect_and_stale_inventory_do_not_erase_stock(lua):
    lua.execute(SERVER_FIXTURE)
    lua.execute("""
        advance(60)
        local state=store.AKRStoryteller.state
        local cell=state.cells['3:3:0']
        fire('OnPlayerDeath',player)
        local untilHour=state.recovery_until
        assert(untilHour>=24)
        dead=true
        advance(60)
        assert(state.recovery_until==untilHour)
        online=false
        advance(60)
        assert(store.AKRStoryteller.telemetry.online_players==0)
        assert(math.abs(cell.stock.food-1)<0.001 and cell.stock_complete==false)
    """)


def test_new_sighting_does_not_refresh_unseen_stock_or_infrastructure(lua):
    lua.execute("""
        local s=Core.new(0)
        local cell=Core.cell(s,0,0,0,0)
        Core.item(s,1,cell,'weapon',0)
        Core.signal(s,'generator',cell,'infrastructure',0)
        Core.item(s,2,cell,'food',72)
        assert(cell.stock.weapon==2 and cell.stock.food==1)
        assert(cell.infrastructure==0.5)
        -- Refreshing one signal cannot erase another signal's independent aging.
        Core.signal(s,'second-generator',cell,'infrastructure',72)
        assert(cell.infrastructure==1.5)
        Core.signal(s,'generator',cell,'infrastructure',72)
        assert(cell.infrastructure==2)
        Core.item(s,1,cell,'weapon',72)
        assert(cell.stock.weapon==4)
        assert(s.recovery_until==0)
    """)
