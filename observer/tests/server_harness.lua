package.path='mod/ZomboidObserver/42/media/lua/shared/?.lua;'..package.path
local now=100000
local handlers,files={},{}
Events=setmetatable({}, {__index=function(t,key)
 local value={Add=function(fn)handlers[key]=fn end};rawset(t,key,value);return value
end})
function isServer()return true end
function getTimestampMs()return now end
function ZombRand()return 1 end
function getFileWriter(path,create,append)
 assert(path:match('%.txt$'),'unsupported file extension')
 if not append then files[path]='' end
 return {write=function(_,s)files[path]=files[path]..s end,close=function()end}
end
function getWorld()return {getMetaGrid=function()return {
 getMinX=function()return -250 end,getMinY=function()return -250 end,
 getMaxX=function()return 250 end,getMaxY=function()return 250 end}end}end
function getOnlinePlayers()return {size=function()return 0 end}end
function getVehicleById(id)assert(id==7)return {getSqlId=function()return 2 end}end
local p={getUsername=function()return 'eric'end,getX=function()return 10 end,getY=function()return 10 end,getZ=function()return 0 end}
dofile('mod/ZomboidObserver/42/media/lua/server/ZomboidObserver/Server.lua')
handlers.OnServerStarted()
local path='ZomboidObserver/observer-0.txt'
assert(files[path]:find('metadata') and files[path]:find('heartbeat'))
handlers.OnClientCommand('ZomboidObserver','pulse',p,{})
local n=#files[path]
handlers.OnClientCommand('ZomboidObserver','observe',p,{tiles={{x=999,y=999,z=0,objects={}}}})
assert(#files[path]==n,'distant tile must be rejected')
handlers.OnClientCommand('ZomboidObserver','markers',p,{markers={{author='akryllax',public=true}}})
assert(#files[path]==n,'other player markers must be rejected')
handlers.OnClientCommand('ZomboidObserver','observe',p,{tiles={{x=10,y=10,z=0,objects={{id='vehicle-net:7',x=10.5,y=10.5,z=0}}}}})
assert(files[path]:find('vehicle:2'),'vehicle network ID must become persistent save ID')
now=now+2000
handlers.OnClientCommand('ZomboidObserver','pulse',p,{})
local _,count=files[path]:gsub('"kind":"heartbeat"','')
assert(count>=2,'client pulse must trigger server heartbeat')
print('server harness: startup, distance limits, marker authorship, persistent vehicle IDs and pulses passed')
