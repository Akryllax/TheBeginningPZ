-- Incremental, read-only inventory traversal. Caller supplies the tick deadline.
local C = require "LofersStoryteller/Config"
local Core = require "LofersStoryteller/Core"
local S = {}

function S.new()
    return {queue={},queued={},failures=0,dropped=0,completed=0,last_operations=0}
end

function S.enqueue(scanner, container, cell)
    if not container or scanner.queued[container] then return false end
    if #scanner.queue>=C.maxQueue then scanner.dropped=scanner.dropped+1;return false end
    local job={root=container,cell=cell,stack={{container=container,index=0,depth=0}},seen={},items=0}
    scanner.queued[container]=true
    scanner.queue[#scanner.queue+1]=job
    return true
end

function S.category(item)
    local category=tostring(item:getCategory())
    local fullType=tostring(item:getFullType())
    if category=="Food" then return "food" end
    if category=="Weapon" then return "weapon" end
    if category=="Medical" then return "medical" end
    if string.find(fullType,"Bullet",1,true) or string.find(fullType,"Round",1,true) then return "ammunition" end
    if category=="Tool" or string.find(fullType,"Hammer",1,true) or string.find(fullType,"Saw",1,true) then return "tool" end
    return "other"
end

function S.step(scanner, state, now, isContainer)
    local job=scanner.queue[1]
    if not job then return false end
    local frame=job.stack[#job.stack]
    if not frame or job.items>=C.maxInventoryItems or state.cells[job.cell.key]~=job.cell then
        scanner.queued[job.root]=nil
        table.remove(scanner.queue,1)
        scanner.completed=scanner.completed+1
        return true
    end
    local items=frame.container:getItems()
    if frame.index>=items:size() then table.remove(job.stack);return true end
    local item=items:get(frame.index)
    frame.index=frame.index+1
    job.items=job.items+1
    if not item then return true end
    local id=tostring(item:getID())
    if not job.seen[id] then
        job.seen[id]=true
        Core.item(state,id,job.cell,S.category(item),now)
        if frame.depth<C.inventoryDepth and isContainer(item) then
            local inner=item:getInventory()
            if inner then job.stack[#job.stack+1]={container=inner,index=0,depth=frame.depth+1} end
        end
    end
    return true
end

function S.drain(scanner,state,now,clock,deadline,isContainer,limit)
    local operations=0
    while operations<(limit or C.maxOperations) and clock()<deadline do
        if not scanner.queue[1] then break end
        local ok=pcall(S.step,scanner,state,now,isContainer)
        operations=operations+1
        if not ok then
            -- Containers can unload or change while being scanned. Unknown != empty.
            local job=table.remove(scanner.queue,1)
            if job then scanner.queued[job.root]=nil end
            scanner.failures=scanner.failures+1
        end
    end
    scanner.last_operations=operations
    return operations
end

return S
