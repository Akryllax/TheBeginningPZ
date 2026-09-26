-- Static terrain is immutable; closures are bounded, expiring server observations.
local Road={limit=128,lifetime=0.25}
local function finite(n) return type(n)=="number" and n==n and n~=math.huge and n~=-math.huge end
local function node(n) return finite(n) and n>=0 and n<=2147483647 and n==math.floor(n) end
function Road.bind(s,identity)
    if type(identity)~="string" or #identity>128 then return false end
    if s.navigation_id~=identity then s.navigation_id=identity;s.road_closures={} end
    s.road_closures=s.road_closures or {}
    return true
end
function Road.snapshot(s,hour)
    local out={};local keep={}
    for _,c in ipairs(s.road_closures or {}) do
        if c.expires_world_hour>hour then
            keep[#keep+1]=c
            out[#out+1]={from=c.from,to=c.to,expires_world_hour=c.expires_world_hour,reason=c.reason}
        end
    end
    s.road_closures=keep;return out
end
function Road.block(s,identity,from,to,hour,reason)
    if not finite(hour) or hour<0 or type(identity)~="string" or identity=="" or
        identity~=s.navigation_id or not node(from) or not node(to) or from==to or
        type(reason)~="string" or #reason>128 then return false,"invalid_road_closure" end
    Road.snapshot(s,hour)
    for _,c in ipairs(s.road_closures) do if c.from==from and c.to==to then
        c.expires_world_hour=hour+Road.lifetime;c.reason=reason;return true
    end end
    if #s.road_closures>=Road.limit then return false,"road_closure_capacity" end
    s.road_closures[#s.road_closures+1]={from=from,to=to,expires_world_hour=hour+Road.lifetime,reason=reason}
    return true
end
return Road
