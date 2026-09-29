if isServer() then
    return
end
require("ISUI/ISCollapsableWindow")
require("ISUI/ISButton")
require("ISUI/ISTextEntryBox")
require("ISUI/ISComboBox")
require("ISUI/ISScrollingListBox")

local Panel = ISCollapsableWindow:derive("LofersScenarioAdminPanel")
local instance = nil
local function admin(player)
    return player and player.getAccessLevel and player:getAccessLevel() == "admin"
end
local function label(value)
    return tostring(value == nil and "unknown" or value)
end
local function describe(value, depth, lines, prefix)
    if #lines >= 60 then
        return
    end
    if type(value) ~= "table" then
        lines[#lines + 1] = prefix .. label(value)
        return
    end
    local keys = {}
    for key in pairs(value) do
        keys[#keys + 1] = key
    end
    table.sort(keys, function(a, b)
        return tostring(a) < tostring(b)
    end)
    for i = 1, math.min(#keys, 32) do
        local key = keys[i]
        local item = value[key]
        if type(item) == "table" and depth > 0 then
            lines[#lines + 1] = prefix .. label(key) .. ":"
            describe(item, depth - 1, lines, prefix .. "  ")
        elseif type(item) ~= "table" then
            lines[#lines + 1] = prefix .. label(key) .. ": " .. label(item)
        end
        if #lines >= 60 then
            break
        end
    end
end

function Panel:request(command, args)
    if not admin(self.player) then
        self.message = "Admin access required"
        return
    end
    self.sequence = self.sequence + 1
    args = args or {}
    args.request_id = "ui:" .. self.sequence
    args.expected_revision = self.snapshot.revision
    sendClientCommand(self.player, "LofersScenario", command, args)
end
function Panel:button(x, y, width, text, command, argument)
    local button = ISButton:new(x, y, width, 25, text, self, function(target)
        if command == "debug_toggle" then
            target.debugEnabled = not target.debugEnabled
            target.debugToggle:setTitle(
                target.debugEnabled and "Disable debug controls" or "Enable debug controls"
            )
            for _, b in ipairs(target.debugButtons) do
                b:setEnable(target.debugEnabled)
            end
            return
        end
        local args = {}
        if argument then
            for k, v in pairs(argument) do
                args[k] = v
            end
        end
        if command == "inspect" or command == "remove_owned" then
            args.id = target.residentId:getText():match("^%s*(.-)%s*$")
            if args.id == "" then
                target.message = "Select or enter a resident ID first"
                return
            end
        elseif command == "advance" then
            args.phase = target.phases[target.phaseBox.selected]
        elseif command == "test_spawn" then
            args.role = target.roles[target.roleBox.selected]
        end
        target:request(command, args)
    end)
    button:initialise()
    self:addChild(button)
    return button
end
function Panel:createChildren()
    ISCollapsableWindow.createChildren(self)
    self:button(14, 150, 95, "Start outbreak", "start")
    self:button(116, 150, 90, "Pause", "pause")
    self:button(213, 150, 90, "Resume", "resume")
    self:button(310, 150, 90, "Refresh", "state")
    self.residentId = ISTextEntryBox:new("", 14, 190, 330, 25)
    self.residentId:initialise()
    self.residentId:instantiate()
    self:addChild(self.residentId)
    self:button(351, 190, 110, "Inspect resident", "inspect")
    self.list = ISScrollingListBox:new(14, 225, self.width - 28, 180)
    self.list:initialise()
    self.list:instantiate()
    self.list.itemheight = 22
    self.list.doDrawItem = function(list, y, item, alt)
        if item.index == list.selected then
            list:drawRect(0, y, list.width, list.itemheight, 0.35, 0.2, 0.6, 0.7)
        end
        list:drawText(item.text, 5, y + 3, 0.9, 0.92, 0.92, 1, UIFont.Small)
        return y + list.itemheight
    end
    self.list.onMouseDown = function(list, x, y)
        ISScrollingListBox.onMouseDown(list, x, y)
        local entry = list.items[list.selected]
        if entry then
            self.residentId:setText(entry.item.id)
        end
    end
    self:addChild(self.list)
    self.debugToggle = self:button(14, 420, 195, "Enable debug controls", "debug_toggle")
    self.roles = { "resident", "shopkeeper", "mechanic", "police", "medic" }
    self.roleBox = ISComboBox:new(218, 420, 125, 25, self, nil)
    self.roleBox:initialise()
    for _, v in ipairs(self.roles) do
        self.roleBox:addOption(v)
    end
    self:addChild(self.roleBox)
    self.phases = { "initial_cases", "response", "disruption", "collapse", "survival" }
    self.phaseBox = ISComboBox:new(350, 420, 145, 25, self, nil)
    self.phaseBox:initialise()
    for _, v in ipairs(self.phases) do
        self.phaseBox:addOption(v)
    end
    self:addChild(self.phaseBox)
    self.debugButtons = {
        self:button(14, 454, 135, "Step one game hour", "step", { hours = 1 }),
        self:button(156, 454, 100, "Set phase", "advance"),
        self:button(263, 454, 110, "Spawn test NPC", "test_spawn"),
        self:button(380, 454, 125, "Remove selected", "remove_owned"),
    }
    for _, button in ipairs(self.debugButtons) do
        button:setEnable(false)
    end
    self.details = ISScrollingListBox:new(14, 498, self.width - 28, self.height - 535)
    self.details:initialise()
    self.details:instantiate()
    self.details.itemheight = 18
    self.details.doDrawItem = function(list, y, item, alt)
        list:drawText(item.text, 4, y + 1, 0.86, 0.9, 0.92, 1, UIFont.Small)
        return y + list.itemheight
    end
    self:addChild(self.details)
    self:request("state")
end
function Panel:prerender()
    ISCollapsableWindow.prerender(self)
    local s = self.snapshot
    self:drawText(
        "First Week  |  " .. label(s.status) .. "  |  " .. label(s.phase),
        14,
        34,
        0.85,
        0.95,
        1,
        1,
        UIFont.Medium
    )
    self:drawText(
        "Scenario hours: "
            .. string.format("%.2f", tonumber(s.elapsed_hours) or 0)
            .. "   Online: "
            .. label(s.online)
            .. "   Revision: "
            .. label(s.revision),
        14,
        62,
        0.9,
        0.9,
        0.9,
        1,
        UIFont.Small
    )
    self:drawText(
        "Residents: "
            .. label(s.residents)
            .. "   Physical: "
            .. label(s.materialized)
            .. "   Moving cars: "
            .. label(s.vehicles),
        14,
        82,
        0.9,
        0.9,
        0.9,
        1,
        UIFont.Small
    )
    self:drawText(
        "Worker: " .. label(s.bridge_health) .. "   Native: " .. label(s.native_health),
        14,
        102,
        0.9,
        0.9,
        0.9,
        1,
        UIFont.Small
    )
    self:drawText(
        string.sub(self.message or s.last_error or "", 1, 100),
        14,
        125,
        1,
        0.8,
        0.45,
        1,
        UIFont.Small
    )
    self:drawText(
        "Debug actions affect the shared world. Ordinary player permissions are checked by the server.",
        14,
        self.height - 28,
        0.75,
        0.8,
        0.85,
        1,
        UIFont.Small
    )
    local now = getTimestampMs()
    if now - self.lastPoll >= 2000 and self:getIsVisible() then
        self.lastPoll = now
        self:request("state")
    end
end
function Panel:accept(state)
    if type(state) ~= "table" then
        return
    end
    if
        state.epoch == self.snapshot.epoch
        and tonumber(state.revision or 0) < tonumber(self.snapshot.revision or 0)
    then
        return
    end
    self.snapshot = state
    if self.list then
        self.list:clear()
        for i, row in ipairs(state.resident_rows or {}) do
            if i > 64 then
                break
            end
            self.list:addItem(
                label(row.name)
                    .. " | "
                    .. label(row.role)
                    .. " | "
                    .. label(row.action)
                    .. " | "
                    .. label(row.id),
                row
            )
        end
    end
end
function Panel.open(player)
    if not admin(player) then
        return
    end
    if instance then
        instance:setVisible(true)
        instance:bringToTop()
        instance:request("state")
        return
    end
    local width = math.min(740, getCore():getScreenWidth() - 30)
    local height = math.min(790, getCore():getScreenHeight() - 30)
    instance = Panel:new(30, 30, width, height)
    instance.player = player
    instance.snapshot = {}
    instance.sequence = 0
    instance.lastPoll = getTimestampMs()
    instance.title = "First Week administration"
    instance.debugEnabled = false
    instance.resizable = false
    instance:initialise()
    instance:addToUIManager()
end
Events.OnFillWorldObjectContextMenu.Add(function(index, context, objects, test)
    if test then
        return
    end
    local player = getSpecificPlayer(index)
    if
        admin(player)
        and SandboxVars
        and SandboxVars.LofersScenario
        and SandboxVars.LofersScenario.Enabled
    then
        context:addOption("First Week controls", player, Panel.open)
    end
end)
Events.OnServerCommand.Add(function(module, command, args)
    if module ~= "LofersScenario" or not instance or type(args) ~= "table" then
        return
    end
    if command == "state" then
        instance:accept(args)
    elseif command == "result" then
        instance.message = (args.ok and "OK: " or "Rejected: ") .. label(args.reason)
        if args.state then
            instance:accept(args.state)
        end
    end
    if command == "inspection" or args.resident then
        local lines = {}
        describe(args.resident or args, 2, lines, "")
        instance.details:clear()
        for _, line in ipairs(lines) do
            instance.details:addItem(line, line)
        end
    end
end)
return Panel
