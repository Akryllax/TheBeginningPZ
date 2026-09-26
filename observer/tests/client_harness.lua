-- Exercise the actual client mod against a controlled, non-mutating world.
-- This catches visibility/privacy mistakes before connecting it to a real save.
package.path = "mod/ZomboidObserver/42/media/lua/shared/?.lua;" .. package.path
local clock=100000
local handlers={}
local packets={}
Events=setmetatable({}, {__index=function(t,key)
    local value={Add=function(fn) handlers[key]=fn end};rawset(t,key,value);return value
end})
function isClient() return true end
function isServer() return false end
function getTimestampMs() return clock end
function instanceof(obj,name) return obj and obj.class==name end
function sendClientCommand(player,module,command,args)
    packets[#packets+1]={command=command,args=args}
end
local function list(values)
    return {size=function()return #values end,get=function(_,i)return values[i+1]end}
end
local props={get=function(_,name)if name=='CustomName' then return 'Storage crate' end end}
local crate={class='IsoObject',getName=function()return 'Storage crate'end,
    getSprite=function()return {getName=function()return 'carpentry_02_0'end,getProperties=function()return props end}end}
local items=list({{getFullType=function()return 'Base.Nails'end,getDisplayName=function()return 'Nails'end,
                  getCondition=function()return 10 end,getCount=function()return 3 end}})
local container={getParent=function()return crate end,getItems=function()return items end,getType=function()return 'crate'end}
crate.getContainer=function()return container end
local visible=true
local square={getX=function()return 10 end,getY=function()return 10 end,getZ=function()return 0 end,
    isCanSee=function()return visible end,getObjects=function()return list({crate})end,getMovingObjects=function()return list({})end}
local player={getPlayerNum=function()return 0 end,getX=function()return 10 end,getY=function()return 10 end,
    getZ=function()return 0 end,getUsername=function()return 'eric'end,isDead=function()return false end,isAsleep=function()return false end}
function getPlayer()return player end
function getCell()return {getVehicles=function()return list({})end,
    getGridSquare=function(_,x,y,z)if x==10 and y==10 and z==0 then return square end end}end
local opened=false
local loot={isVisible=function()return opened end,inventoryPane={isVisible=function()return opened end,inventory=container}}
function getPlayerLoot()return loot end
function getPlayerMechanicsUI()return nil end
ISWorldMap_instance=nil

dofile('mod/ZomboidObserver/42/media/lua/client/ZomboidObserver/Client.lua')
handlers.OnGameStart()
local function run(n)for _=1,n do clock=clock+17;handlers.OnTick() end end
run(160)
local observed,inspected=0,0
for _,p in ipairs(packets) do observed=observed+#(p.args.tiles or {});inspected=inspected+#(p.args.inspections or {}) end
assert(observed>0,'visible tile must be observed')
assert(inspected==0,'closed container contents must not be exported')

packets={};opened=true;run(160)
for _,p in ipairs(packets)do
    for _,inspection in ipairs(p.args.inspections or {})do
        assert(inspection.items[1].name=='Nails' and inspection.items[1].count==3)
        inspected=inspected+1
    end
end
assert(inspected>0,'selected container must be inspected')

packets={};container.getParent=function()return {class='IsoGameCharacter'}end;run(400)
for _,p in ipairs(packets)do assert(#(p.args.inspections or {})==0,'personal inventory leaked')end

packets={};visible=false;opened=false;run(400)
for _,p in ipairs(packets)do assert(#(p.args.tiles or {})==0 and #(p.args.seen or {})==0,'unseen tile updated')end
print('client harness: visibility, selected containers, item quantities and personal-inventory exclusion passed')
