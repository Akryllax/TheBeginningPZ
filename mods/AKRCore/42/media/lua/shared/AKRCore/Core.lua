-- Entry point for AKR modules: local C=require "AKRCore/Core".
-- The shared instance survives Lua reloads, so registrations are replaced, not duplicated.
local Version=require "AKRCore/Version"
local Registry=require "AKRCore/Registry"
local Dispatch=require "AKRCore/Dispatch"
local Store=require "AKRCore/Store"
local Capabilities=require "AKRCore/Capabilities"

local K={version="0.1.0",Version=Version,Registry=Registry,Dispatch=Dispatch,Store=Store,Capabilities=Capabilities}

function K.instance()
    local existing=rawget(_G,"AKR")
    if type(existing)=="table" and existing.coreVersion==K.version then return existing end
    local instance={coreVersion=K.version,registry=Registry.new(),
        dispatch=Dispatch.new(rawget(_G,"Events")),capabilities=Capabilities.new()}
    _G.AKR=instance
    Registry.provide(instance.registry,"AKRCore",K.version,K)
    return instance
end

return K
