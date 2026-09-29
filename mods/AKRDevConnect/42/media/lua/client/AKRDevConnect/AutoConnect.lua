-- Developer-only: completes a "+connect host:port" launch without manual clicks.
-- The stock main menu reads the one-shot launch argument and calls BootstrapConnectPopup:connect,
-- which then waits for Steam server details that a LAN-only disposable server may never supply,
-- and its popup cannot match the saved account (numeric saved port vs string argument).
-- Instead, reuse the stock favorites connect (MultiplayerUI:connectToServer) with the account
-- already saved in ServerListSteam.db. No credentials are stored in this mod.
if isServer() or not BootstrapConnectPopup then
    return
end
local state = { armed = false }
AKRDevConnect = state

local function savedAccount(host, port)
    for _, server in ipairs(getServerList()) do
        if server:getIp() == host and tostring(server:getPort()) == port then
            local chosen = nil
            for i = 0, server:getAccounts():size() - 1 do
                local account = server:getAccounts():get(i)
                if account:isSavePwd() and account:getUserName() ~= "" then
                    chosen = account
                end
            end
            return server, chosen
        end
    end
    return nil, nil
end

-- Mirrors MultiplayerUI:connectToServer for a saved-password account.
local function connect(previous, server, account)
    print(
        "[AKRDevConnect] auto-connecting "
            .. server:getIp()
            .. ":"
            .. tostring(server:getPort())
            .. " as saved account "
            .. account:getUserName()
    )
    getCore():setAccountUsed(account)
    account:setLastLogonNow()
    updateAccountToAccountList(account)
    if getSteamModeActive() then
        steamReleaseInternetServersRequest()
    end
    stopSendSecretKey()
    getCore():setNoSave(false)
    local localIP = getSteamModeActive() and server:getLocalIP() or ""
    local relay = getSteamModeActive() and account:getUseSteamRelay()
    ConnectToServer.instance.loadingBackground = server:getServerLoadingScreen()
    ConnectToServer.instance:connect(
        previous,
        server:getName(),
        account:getUserName(),
        account:getPwd(),
        server:getIp(),
        localIP,
        tostring(server:getPort()),
        server:getServerPassword(),
        relay,
        false,
        account:getAuthType()
    )
end

local stockConnect = BootstrapConnectPopup.connect
function BootstrapConnectPopup:connect(host, port, serverPassword)
    if
        state.armed
        or isValidSteamID(host)
        or not ConnectToServer
        or not ConnectToServer.instance
    then
        return stockConnect(self, host, port, serverPassword)
    end
    state.armed = true
    local ok, server, account = pcall(savedAccount, tostring(host), tostring(port))
    if not ok or not account then
        print(
            "[AKRDevConnect] no saved account for "
                .. tostring(host)
                .. ":"
                .. tostring(port)
                .. "; stock prompt"
        )
        return stockConnect(self, host, port, serverPassword)
    end
    local done, err = pcall(connect, self, server, account)
    if not done then
        print("[AKRDevConnect] failed: " .. tostring(err) .. "; stock prompt")
        return stockConnect(self, host, port, serverPassword)
    end
end
print("[AKRDevConnect] loaded")
