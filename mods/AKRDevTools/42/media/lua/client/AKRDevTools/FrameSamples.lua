-- Opt-in watched batch only. Fixed 251-bin render-interval histogram, one log per 5 s.
-- Measures render callbacks (including diagnostics), not isolated GPU timings.
local K = require("AKRCore/Core")
local S = { last = 0, start = 0, n = 0, total = 0, max = 0, bins = {}, phase = -1, wave = -1 }
local function percentile(p)
    local need = math.ceil(S.n * p)
    local seen = 0
    for i = 0, 250 do
        seen = seen + (S.bins[i] or 0)
        if seen >= need then
            return i == 250 and S.max or i + 1
        end
    end
    return S.max
end
local function flush(now, count)
    if S.n == 0 then
        return
    end
    local bins = {}
    for i = 0, 250 do
        if S.bins[i] then
            bins[#bins + 1] = i .. ":" .. S.bins[i]
        end
    end
    print(
        string.format(
            "[AKRFrameWindow] t=%d phase=%d wave=%d actors=%d n=%d total_ms=%d max_ms=%d p95_ms=%d p99_ms=%d bins=%s",
            now,
            S.phase,
            S.wave,
            count,
            S.n,
            S.total,
            S.max,
            percentile(0.95),
            percentile(0.99),
            table.concat(bins, ",")
        )
    )
end
K.Dispatch.on(K.instance().dispatch, "OnRenderTick", "AKRDevTools.frameSamples", function()
    local c = AKRPoolWatchClient
    local e = AKREncounterClient
    if e and getTimestampMs() - e.received <= 2500 then
        local stages = {
            WAIT_OBSERVER = 0,
            PREWARM = 1,
            MATERIALIZE = 2,
            FIXTURES = 3,
            POSITIONING = 4,
            COUNTDOWN = 5,
            RUNNING = 6,
            HOLD = 7,
            CLEANING = 8,
            COMPLETE = 9,
        }
        c = { epoch = e.epoch, count = e.count, phase = stages[e.stage] or -1, wave = 0 }
        if S.event ~= e.event then
            print("[AKRFrameEvent] " .. e.event)
            S.event = e.event
            S.phase = -1
        end
    end
    if not c or not c.epoch or not getSpecificPlayer(0) then
        return
    end
    local now = getTimestampMs()
    if c.phase ~= S.phase or c.wave ~= S.wave or now - S.start >= 5000 then
        flush(now, c.count)
        S.start = now
        S.n = 0
        S.total = 0
        S.max = 0
        S.bins = {}
        S.last = 0
        S.phase = c.phase
        S.wave = c.wave
    end
    if S.last > 0 and now >= S.last then
        local dt = now - S.last
        local bin = math.min(250, dt)
        S.n = S.n + 1
        S.total = S.total + dt
        S.max = math.max(S.max, dt)
        S.bins[bin] = (S.bins[bin] or 0) + 1
    end
    S.last = now
end)
