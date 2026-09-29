"""Fail-isolated watched cases; uncertain ownership and transport errors stop advancement."""

from __future__ import annotations

TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}
CRITICAL_REASONS = (
    "observer_disconnected",
    "observer_report_stale",
    "observer_admin_on_foot",
    "observer_on_foot_loaded",
    "observer_spot_obstructed",
    "identity_changed",
    "previous_encounter_owned",
    "previous_cleanup_unverified",
    "resources_retained",
    "terminal_world_restart",
    "pool_cleanup_baseline",
    "OutOfMemoryError",
    "LinkageError",
    "VirtualMachineError",
)


def classify(state):
    """Classify a terminal reply independently from cleanup and visual acceptance."""
    if state.phase not in TERMINAL:
        raise ValueError("Cannot classify an unfinished case")
    if not state.encounter.cleanup_verified or state.resources:
        return "critical", "cleanup_not_verified"
    if any(token in state.reason for token in CRITICAL_REASONS):
        return "critical", state.reason
    if state.phase != "COMPLETED":
        return "failed", state.reason or state.phase
    outcome = state.encounter.scenario_outcome
    if outcome == "NOT_EXERCISED":
        return "not_exercised", "native behavior did not exercise the case"
    if outcome != "PASSED":
        return "failed", "missing_or_failed_scenario_outcome:" + outcome
    return "passed_visual_pending", ""


def batch_status(cases):
    statuses = [c["status"] for c in cases]
    if "critical" in statuses:
        return "critical"
    if "failed" in statuses:
        return "completed_with_failures"
    if "not_exercised" in statuses:
        return "not_exercised"
    return "passed_visual_pending"


def verify_runtime(previous, current):
    """An epoch change or unavailable runtime invalidates remaining submissions."""
    if (previous.world, previous.epoch) != (current.world, current.epoch):
        raise RuntimeError("Critical: runtime identity changed between cases")
    if current.phase != "READY":
        raise RuntimeError("Critical: runtime is not ready between cases")
