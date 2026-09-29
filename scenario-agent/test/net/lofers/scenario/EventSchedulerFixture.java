package net.lofers.scenario;

import java.util.*;
import java.util.concurrent.*;

/** Detached scheduler contract only; not native physics, persistence or multiplayer evidence. */
final class EventSchedulerFixture {
    static EventScheduler.Context request(String id) { return new EventScheduler.Context("world", "epoch", id); }
    static EventScheduler.Definition definition(long seed) {
        return new EventScheduler.Definition(EventScheduler.Kind.ROUTE, seed, "module-1", "route-1");
    }
    static void check(boolean value, String reason) { ScenarioFixture.check(value, reason); }
    static void run() throws Exception {
        EventScheduler scheduler = new EventScheduler("world", "epoch", 2, 8);
        var first = scheduler.submit(request("one"), definition(1));
        check(first.accepted() && scheduler.status(first.eventId()).phase() == EventScheduler.Phase.QUEUED,
            "Accepted command falsely completed");
        check(scheduler.submit(request("one"), definition(1)).equals(first), "Retry duplicated event");
        check(scheduler.submit(request("one"), definition(2)).code().equals("request_conflict"), "Request reuse accepted");
        ScenarioFixture.rejects(() -> scheduler.submit(new EventScheduler.Context("world", "stale", "two"), definition(1)), "Stale epoch accepted");
        ScenarioFixture.rejects(() -> scheduler.submit(new EventScheduler.Context("other", "epoch", "two"), definition(1)), "Wrong world accepted");
        ScenarioFixture.rejects(() -> definitionWithInvalidVersion(), "Unbounded identifier accepted");
        var second = scheduler.submit(request("two"), definition(2));
        check(!scheduler.submit(request("three"), definition(3)).accepted(), "Queue overflow accepted");
        check(scheduler.cancel(request("cancel"), second.eventId()).accepted(), "Full queue blocked cancellation");
        check(scheduler.status(second.eventId()).phase() == EventScheduler.Phase.CANCELLED, "Queued cancellation executed");
        var third = scheduler.submit(request("three"), definition(3));
        check(third.accepted(), "Busy request could not be retried");
        check(scheduler.next().id().equals(first.eventId()), "Queue did not preserve FIFO");
        var car = new EventResources.Resource(EventResources.Kind.VEHICLE, "car-1");
        scheduler.acquire(first.eventId(), car);
        scheduler.running(first.eventId());
        check(scheduler.outcomes(0, 64).size() == 1, "Running event reported completed");
        scheduler.cancel(request("cancel-active"), first.eventId());
        check(scheduler.next().phase() == EventScheduler.Phase.STOPPING, "Cancellation did not request controlled stop");
        scheduler.cleaning(first.eventId(), false, "operator_cancel");
        check(!scheduler.release(first.eventId(), car, true, false), "Missing native removal freed ownership");
        check(!scheduler.cleaned(first.eventId(), true), "Incomplete cleanup completed event");
        check(scheduler.next().id().equals(first.eventId()), "Unresolved cleanup allowed next event");
        check(scheduler.release(first.eventId(), car, true, true), "Confirmed removal rejected");
        check(!scheduler.cleaned(first.eventId(), false), "Unverified baseline allowed completion");
        check(scheduler.cleaned(first.eventId(), true), "Verified cleanup could not recover");
        check(scheduler.status(first.eventId()).phase() == EventScheduler.Phase.CANCELLED, "Cancel outcome lost");
        check(scheduler.next().id().equals(third.eventId()), "Queue order changed after cleanup");
        scheduler.cleaning(third.eventId(), true, "partial_preparation_failure");
        check(scheduler.cleaned(third.eventId(), true), "Clean preparation failure stuck");
        check(scheduler.status(third.eventId()).phase() == EventScheduler.Phase.FAILED, "Failure falsely succeeded");
        check(scheduler.next() == null, "Unexpected queued execution");
        check(scheduler.submit(request("one"), definition(1)).equals(first), "Completed retry executed again");

        EventResources ledger = new EventResources();
        ledger.acquire(car); ledger.acquire(car);
        ledger.acquire(new EventResources.Resource(EventResources.Kind.VEHICLE, "car-2"));
        ScenarioFixture.rejects(() -> ledger.acquire(new EventResources.Resource(EventResources.Kind.VEHICLE, "car-3")), "Third vehicle accepted");
        for (int i = 0; i < 6; i++) ledger.acquire(new EventResources.Resource(EventResources.Kind.FIXTURE, "fixture-" + i));
        ScenarioFixture.rejects(() -> ledger.acquire(new EventResources.Resource(EventResources.Kind.FIXTURE, "ninth")), "Ninth entity accepted");
        ScenarioFixture.rejects(() -> ledger.release(new EventResources.Resource(EventResources.Kind.FIXTURE, "unrelated"), true, true), "Unrelated ownership released");
        try { ledger.snapshot().clear(); throw new AssertionError("Mutable ownership snapshot"); }
        catch (UnsupportedOperationException expected) { }
        check(ledger.snapshot().size() == 8, "Failed acquisition changed ownership");

        try (ExecutorService otherThread = Executors.newSingleThreadExecutor()) {
            check(otherThread.submit(() -> {
                try { scheduler.next(); return false; }
                catch (IllegalStateException expected) { return true; }
            }).get(2, TimeUnit.SECONDS), "Off-thread advancement accepted");
        }

        // Repeated detached lifecycle exercise, explicitly not the native/runtime soak gate.
        EventScheduler repeated = new EventScheduler("world", "epoch", 2, 128);
        for (int i = 0; i < 128; i++) {
            var accepted = repeated.submit(request("repeat-" + i), definition(i));
            check(accepted.accepted(), "Premature receipt saturation");
            var event = repeated.next(); repeated.acquire(event.id(), car); repeated.running(event.id());
            repeated.cleaning(event.id(), false, "route_complete");
            check(repeated.release(event.id(), car, true, true) && repeated.cleaned(event.id(), true), "Repeated cleanup failed");
            check(repeated.status(event.id()).resources().isEmpty(), "Resource survived cleanup");
        }
        check(repeated.submit(request("overflow"), definition(1)).code().equals("receipt_capacity"), "Deduplication history silently evicted");
        check(repeated.submit(request("repeat-0"), definition(0)).accepted(), "Retained retry rejected at capacity");
        check(repeated.outcomes(64, 64).size() == 64 && repeated.next() == null, "Receipt pagination or duplicate execution");
        System.out.println("Event scheduler fixtures passed: FIFO, retries, stale identity, capacity/cancel, ownership, partial failure, cleanup recovery, thread guard, 128 detached lifecycles; no live-runtime claim");
    }
    private static void definitionWithInvalidVersion() {
        new EventScheduler.Definition(EventScheduler.Kind.ROUTE, 0, "x".repeat(129), "route");
    }
}
