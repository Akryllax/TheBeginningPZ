local M=require "LofersScenario/Model"
local W=require "LofersScenario/World"
local N={actors={},vehicleObjects={}}
local cid="lofers-scenario-civilians-v1"
local function profile(r)
    if not BanditCustom or not BanditCustom.ClanCreate then return nil end
    local clan=BanditCustom.ClanGet(cid) or BanditCustom.ClanCreate(cid)
    clan.general.name="Lofers Residents";clan.spawn.friendly=true;clan.spawn.spawnChance=0
    clan.spawn.companion=false;clan.spawn.assault=false;clan.spawn.wanderer=false
    local bid=cid..":"..r.id
    local p=BanditCustom.GetById(bid) or BanditCustom.Create(bid)
    p.cid=cid;p.general.cid=cid;p.general.bid=bid;p.general.name=r.name;p.general.female=false;p.general.health=5
    p.general.skin=1;p.general.hairType=1;p.general.hairColor=1;p.general.beardType=1
    p.weapons={melee="Base.BareHands"};p.ammo={};p.bag=nil
    p.clothing={Shirt="Base.Shirt_Denim",Pants="Base.Trousers_JeanBaggy",Shoes="Base.Shoes_Random"}
    return bid
end
function N.ready()
    return LofersNative and LofersNative.versionReady() and BanditServer and BanditServer.Spawner
        and type(BanditServer.Spawner.Individual)=="function"
end
function N.spawn(s,r,point,player,vehicle)
    if not N.ready() then return false,"native_not_ready" end
    local bid=profile(r);if not bid then return false,"profile_unavailable" end
    r.lifecycle="spawning";r.generation=r.generation+1;r.position=M.point(point)
    local original=TransmitBanditCluster;local receipt,receiptBrain
    TransmitBanditCluster=function(id)
        local cluster=GetBanditClusterData(id);local brain=cluster and cluster[id]
        if brain and brain.bid==bid and brain.fullname==r.name then receipt=id;receiptBrain=brain end
        return original(id)
    end
    local ok,err=pcall(function()
        return LofersNative.spawn(function(args)
            BanditServer.Spawner.Individual(args.player,{bid=bid,x=point.x,y=point.y,z=point.z,
                program="LofersCivilian",hostile=false,hostileP=false,fullname=r.name,permanent=false})
        end,{player=player})
    end)
    TransmitBanditCluster=original
    if not receipt then
        -- Native factory can partially fail; never retry an unreceipted spawn automatically.
        r.lifecycle="unresolved";r.last_error=ok and "spawn_no_receipt" or "spawn_exception"
        return false,r.last_error
    end
    local z=W.find(receipt,point)
    if not z then r.lifecycle="unresolved";r.outfit_id=receipt;return false,"spawn_entity_unresolved" end
    r.outfit_id=receipt;r.materialized=true;r.lifecycle="active";r.revision=r.revision+1
    r.max_native_health=(receiptBrain and receiptBrain.health) or 1
    z:setHealth(r.max_native_health*math.max(0,math.min(100,r.health))/100)
    r.owner_id=player:getOnlineID();r.lease_epoch=r.lease_epoch+1
    if not LofersNative.bind(z,r.id,r.generation,r.lease_epoch,r.owner_id) then
        r.lifecycle="unresolved";return false,"native_bind_failed"
    end
    N.actors[r.id]=z
    if vehicle then
        local car
        local created=pcall(function()
            LofersNative.spawn(function()
                local sq=z:getSquare()
                car=addVehicleDebug((r.role=="police" and "Base.CarLightsPolice") or
                    (r.role=="nurse" and "Base.VanAmbulance") or "Base.CarNormal",IsoDirections.N,nil,sq)
            end,{})
        end)
        if created and car then
            local vid=tostring(car:getId());r.has_vehicle=true;r.vehicle_id=vid
            s.vehicles[vid]={id=vid,resident_id=r.id,status="parked",position=M.point(point)}
            N.vehicleObjects[vid]=car
            local md=car:getModData();md.LofersScenario={resident_id=r.id,generation=r.generation}
            car:transmitModData()
        else r.last_error="vehicle_spawn_failed" end
    end
    M.event(s,"spawn","native_receipt",r.id);return true
end
function N.reconcile(r)
    local z=N.actors[r.id]
    if z and z:isDead() then return z,"dead" end
    if z and not z:getSquare() then N.actors[r.id]=nil;z=nil end
    if not z and r.outfit_id then z=W.find(r.outfit_id,r.position);N.actors[r.id]=z end
    if not z then return nil,"unloaded" end
    if z:isDead() then return z,"dead" end
    local sq=z:getSquare();if not sq then return nil,"unloaded" end
    r.position={x=z:getX(),y=z:getY(),z=math.floor(z:getZ())}
    r.health=math.max(0,math.min(100,z:getHealth()*100/(r.max_native_health or 1)))
    r.in_vehicle=z:getVehicle()~=nil
    return z,"active"
end
function N.dead(r)
    if r.outfit_id then
        local cluster=GetBanditClusterData(r.outfit_id)
        if cluster then cluster[r.outfit_id]=nil;TransmitBanditCluster(r.outfit_id) end
    end
    N.actors[r.id]=nil
end
function N.health(r,value)
    local z=N.actors[r.id]
    if not z or z:isDead() then return false end
    r.health=math.max(0,math.min(100,value));r.health_revision=(r.health_revision or 0)+1
    z:setHealth((r.max_native_health or 1)*r.health/100)
    return true
end
function N.remove(s,r)
    local z=N.actors[r.id] or (r.outfit_id and W.find(r.outfit_id,r.position))
    if not z then r.lifecycle="unresolved";return false,"entity_unloaded" end
    local v=z:getVehicle()
    if v and math.abs(v:getCurrentSpeedKmHour())>1 then return false,"vehicle_moving" end
    if not LofersNative.removeOwned(z) then return false,"native_removal_rejected" end
    local cluster=GetBanditClusterData(r.outfit_id)
    if cluster then cluster[r.outfit_id]=nil;TransmitBanditCluster(r.outfit_id) end
    N.actors[r.id]=nil;r.materialized=false;r.lifecycle="abstract";r.generation=r.generation+1
    r.outfit_id=nil;r.owner_id=-1;r.lease_epoch=r.lease_epoch+1;r.actions={};r.revision=r.revision+1
    r.in_vehicle=false;r.retired_hour=s.last_hour
    -- Vehicles remain real world objects; never remove a player's occupied vehicle.
    M.event(s,"remove","confirmed_owned_entity",r.id);return true
end
function N.turn(s,r)
    local z=N.actors[r.id];if not z then return false end
    if z:getVehicle() and not LofersNative.exitVehicle(z) then return false end
    local cluster=GetBanditClusterData(r.outfit_id)
    if cluster then cluster[r.outfit_id]=nil;TransmitBanditCluster(r.outfit_id) end
    z:getModData().LofersScenario=nil;z:setNoTeeth(false);z:setVariable("Bandit",false)
    z:setUseless(false);z:setWalkType("2");z:getModData().brain=nil
    r.lifecycle="zombie";r.infection="turned";r.materialized=false;r.generation=r.generation+1
    r.owner_id=-1;r.lease_epoch=r.lease_epoch+1;N.actors[r.id]=nil
    M.event(s,"conversion","confirmed_turning",r.id);return true
end
return N
