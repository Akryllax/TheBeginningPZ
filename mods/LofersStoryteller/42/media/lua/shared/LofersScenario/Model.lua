-- Original, pure Lua domain state. No game objects or callbacks are persisted.
local C=require "LofersScenario/Config"
local M={}
local first={"Alex","Morgan","Robin","Sam","Jamie","Casey","Taylor","Jordan","Drew","Jess","Lee","Pat"}
local last={"Miller","Hayes","Carter","Brooks","Reed","Parker","Ellis","Bennett","Cooper","Davis","Foster","Ward"}
local roles={"worker","shopkeeper","mechanic","nurse","police","firefighter","resident","driver"}
local function clamp(v,a,b) return math.max(a,math.min(b,v)) end
function M.point(p) return {x=p.x,y=p.y,z=p.z or 0} end
function M.distance(a,b) return math.sqrt((a.x-b.x)^2+(a.y-b.y)^2) end
function M.new(world,uuid,hour)
    return {schema=C.schema,world=world,world_id=uuid,status="calm",elapsed_hours=0,phase="calm",
        started_hour=nil,last_hour=hour,revision=1,next_id=1,seed=104729,
        residents={},order={},places={},place_order={},roads={},road_order={},edges={},
        receipts={},receipt_order={},events={},vehicles={},manual_steps=0}
end
function M.random(s) s.seed=(s.seed*16807)%2147483647;return s.seed/2147483647 end
function M.event(s,kind,reason,id)
    if #s.events>=C.maxEvents then table.remove(s.events,1) end
    s.events[#s.events+1]={kind=kind,reason=reason,id=id or "",hour=s.elapsed_hours,revision=s.revision}
end
function M.phase(s)
    if not s.started_hour then return "calm" end
    local h=s.elapsed_hours
    if h<24 then return "first_cases" elseif h<48 then return "concern"
    elseif h<72 then return "emergency" elseif h<96 then return "disruption"
    elseif h<120 then return "evacuation" elseif h<144 then return "collapse"
    elseif h<168 then return "aftermath" else return "survival" end
end
function M.start(s,hour)
    if s.started_hour then return false,"already_started" end
    s.started_hour=hour;s.last_hour=hour;s.status="running";s.elapsed_hours=0
    s.phase=M.phase(s);s.revision=s.revision+1;M.event(s,"start","manual_admin_activation")
    return true
end
function M.clock(s,hour,online)
    local dt=clamp(hour-s.last_hour,0,0.1);s.last_hour=hour
    if online<=0 or s.status=="paused" then return 0 end
    if s.status=="running" then s.elapsed_hours=s.elapsed_hours+dt end
    s.phase=M.phase(s);return dt
end
function M.addPlace(s,id,kind,p)
    if s.places[id] then return s.places[id] end
    if #s.place_order>=C.maxPlaces then return nil end
    local place={id=id,kind=kind,position=M.point(p),available=true,revision=1}
    s.places[id]=place;s.place_order[#s.place_order+1]=id;return place
end
function M.nearest(s,p,kind)
    local best,dist
    for _,id in ipairs(s.place_order) do
        local place=s.places[id]
        if place.available and place.kind==kind then
            local d=M.distance(p,place.position)
            if not dist or d<dist then best=place;dist=d end
        end
    end
    return best
end
function M.resident(s,home,role)
    if #s.order>=C.maxResidents then return nil end
    local n=s.next_id;s.next_id=n+1
    local id=s.world_id..":resident:"..n
    local r={id=id,name=first[(n-1)%#first+1].." "..last[math.floor((n-1)/#first)%#last+1],
        role=role or roles[(n-1)%#roles+1],revision=1,generation=0,home=M.point(home.position),
        home_id=home.id,position=M.point(home.position),health=100,health_revision=0,hunger=0.15,fatigue=0.1,fear=0,
        infection="healthy",exposed_hour=-1,materialized=false,lifecycle="abstract",home_safe=true,
        work_available=true,has_food=true,has_vehicle=false,vehicle_id="",current_action="WAIT",
        plan_revision=0,lease_epoch=0,owner_id=-1,lease_until=0,action_index=1,actions={},last_hour=0}
    s.residents[id]=r;s.order[#s.order+1]=id
    M.assignPlaces(s,r);return r
end
function M.assignPlaces(s,r)
    local workKind=(r.role=="nurse" and "clinic") or (r.role=="police" and "police") or
        (r.role=="firefighter" and "firestation") or (r.role=="mechanic" and "mechanic") or
        (r.role=="shopkeeper" and "shop") or "work"
    local work=M.nearest(s,r.home,workKind) or M.nearest(s,r.home,"work")
    local shop=M.nearest(s,r.home,"shop");local clinic=M.nearest(s,r.home,"clinic")
    r.work=work and M.point(work.position) or M.point(r.home);r.work_id=work and work.id or ""
    r.work_available=work~=nil and s.elapsed_hours<96
    r.shop=shop and M.point(shop.position) or M.point(r.home)
    r.clinic=clinic and M.point(clinic.position) or M.point(r.home)
end
function M.expose(s,r,reason)
    if not s.started_hour or r.infection~="healthy" then return false end
    r.infection="exposed";r.exposed_hour=s.elapsed_hours;r.revision=r.revision+1
    M.event(s,"exposure",reason,r.id);return true
end
function M.advanceResident(s,r,dt)
    if r.lifecycle=="dead" or r.lifecycle=="zombie" then return end
    r.hunger=clamp(r.hunger+dt*0.025,0,1);r.fatigue=clamp(r.fatigue+dt*0.018,0,1)
    r.fear=clamp(r.fear+(r.threatened and dt*2 or -dt*0.1),0,1)
    if r.infection~="healthy" and r.exposed_hour>=0 then
        local age=s.elapsed_hours-r.exposed_hour
        local stage=age>=36 and "turning" or age>=20 and "severe" or age>=8 and "symptomatic" or "exposed"
        if r.infection~=stage then r.infection=stage;r.revision=r.revision+1;M.event(s,"infection",stage,r.id) end
        if stage=="turning" and not r.materialized and r.lifecycle=="abstract" then
            r.lifecycle="zombie";r.health=0;M.event(s,"outcome","unobserved_turning",r.id)
        end
    end
    r.work_available=r.work_id~="" and s.elapsed_hours<96
end
function M.acceptPlan(s,p)
    local r=s.residents[p.resident_id]
    if not r then return false,"unknown_resident" end
    if r.lifecycle=="dead" or r.lifecycle=="zombie" then return false,"terminal_resident" end
    if p.generation~=r.generation or p.based_on_revision~=r.revision then return false,"stale_facts" end
    if p.plan_revision<=r.plan_revision then return false,"stale_plan" end
    if type(p.actions)~="table" or #p.actions==0 or #p.actions>C.maxActions then return false,"invalid_actions" end
    for _,a in ipairs(p.actions) do
        local kind=C.action(a.kind)
        if not C.vehicleExecution and (kind=="DRIVE" or kind=="PARK" or kind=="ENTER_VEHICLE" or kind=="EXIT_VEHICLE") then
            return false,"vehicle_execution_unverified"
        end
        if not kind or type(a.id)~="string" or #a.id>160 or type(a.target)~="table" or
            type(a.target.x)~="number" or a.target.x~=a.target.x or type(a.target.y)~="number" or a.target.y~=a.target.y or
            math.abs(a.target.x)>100000 or math.abs(a.target.y)>100000 or
            (a.target.z or 0)<-32 or (a.target.z or 0)>64 or #(a.route or {})>256 or
            type(a.duration_hours or 0)~="number" or (a.duration_hours or 0)<0 or
            (a.duration_hours or 0)>8 or (a.duration_hours or 0)~=(a.duration_hours or 0) then return false,"invalid_action" end
        for _,point in ipairs(a.route or {}) do
            if type(point.x)~="number" or type(point.y)~="number" or point.x~=point.x or point.y~=point.y or
                math.abs(point.x)>100000 or math.abs(point.y)>100000 then return false,"invalid_route" end
        end
        a.kind=kind
    end
    r.actions=p.actions;r.action_index=1;r.plan_revision=p.plan_revision;r.goal=p.goal
    r.current_action=r.actions[1].kind;r.action_started=nil;r.action_deadline=nil
    return true
end
function M.action(r) return r.actions and r.actions[r.action_index] end
function M.receipt(s,r,args,owner)
    local key=r.id..":"..tostring(args.action_id)..":"..tostring(args.plan_revision)
    if s.receipts[key] then return false,"duplicate_receipt" end
    local action=M.action(r)
    if not action or action.id~=args.action_id or args.generation~=r.generation or
        args.plan_revision~=r.plan_revision or args.lease_epoch~=r.lease_epoch or owner~=r.owner_id then
        return false,"stale_receipt"
    end
    if args.state~="completed" and args.state~="failed" then return false,"invalid_outcome" end
    if #s.receipt_order>=C.maxReceipts then s.receipts[table.remove(s.receipt_order,1)]=nil end
    s.receipts[key]=args.state;s.receipt_order[#s.receipt_order+1]=key
    if args.state=="completed" then
        if action.kind=="EAT" then r.hunger=math.max(0,r.hunger-0.6);r.has_food=false
        elseif action.kind=="REST" then r.fatigue=math.max(0,r.fatigue-0.7)
        elseif action.kind=="SHOP" or action.kind=="SCAVENGE" then r.has_food=true
        end
        r.action_index=r.action_index+1
    else r.actions={};r.action_index=1 end
    r.revision=r.revision+1;r.action_started=nil;r.action_deadline=nil
    local nextAction=M.action(r);r.current_action=nextAction and nextAction.kind or "WAIT"
    M.event(s,"action",args.state..":"..action.kind,r.id);return true
end
function M.lease(r,owner,now)
    if owner~=r.owner_id then r.owner_id=owner;r.lease_epoch=r.lease_epoch+1;r.action_started=nil end
    r.lease_until=now+C.leaseSeconds
end
function M.count(s)
    local pedestrians,physical,vehicles=0,0,0
    for _,id in ipairs(s.order) do local r=s.residents[id]
        if r.materialized or r.lifecycle=="spawning" or r.lifecycle=="unresolved" then
            physical=physical+1;if not r.has_vehicle then pedestrians=pedestrians+1 end
        end
    end
    for _,v in pairs(s.vehicles) do if v.status~="removed" then vehicles=vehicles+1 end end
    return pedestrians,physical,vehicles
end
return M
