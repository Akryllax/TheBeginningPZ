"""Failure isolation, stale-session protection and honest coverage for watched regressions."""

import json
from pathlib import Path
import sys
from types import SimpleNamespace as NS

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import civilian_encounter as ops
import visual_regression
import watched_policy as policy


def state(phase="COMPLETED", outcome="PASSED", clean=True, resources=(), reason=""):
    return NS(
        phase=phase,
        reason=reason,
        resources=resources,
        encounter=NS(cleanup_verified=clean, scenario_outcome=outcome),
    )


def test_failed_case_can_continue_only_after_exact_cleanup():
    assert policy.classify(state("FAILED", reason="client_replica_divergence"))[0] == "failed"
    assert policy.classify(state("FAILED", clean=False))[0] == "critical"
    assert policy.classify(state(resources=["actor:unknown"]))[0] == "critical"
    assert policy.classify(state("FAILED", reason="observer_disconnected"))[0] == "critical"
    assert policy.classify(state(outcome="NOT_EXERCISED"))[0] == "not_exercised"
    assert policy.classify(state(outcome="PENDING"))[0] == "failed"
    with pytest.raises(ValueError):
        policy.classify(state("CLEANING"))


def test_ready_reply_from_new_epoch_cannot_advance_batch():
    previous = NS(world="world", epoch="one", phase="READY")
    policy.verify_runtime(previous, previous)
    with pytest.raises(RuntimeError, match="identity"):
        policy.verify_runtime(previous, NS(world="world", epoch="two", phase="READY"))
    with pytest.raises(RuntimeError, match="not ready"):
        policy.verify_runtime(previous, NS(world="world", epoch="one", phase="BOOTING"))


@pytest.mark.parametrize("clean,expected_count", [(True, 2), (False, 1)])
def test_real_operator_continues_clean_failure_and_stops_uncertain_cleanup(
    tmp_path, monkeypatch, clean, expected_count
):
    target = tmp_path / "world"
    target.mkdir()
    console = tmp_path / "artifacts/candidate-client/Zomboid/console.txt"
    console.parent.mkdir(parents=True)
    console.write_text("before\n")
    monkeypatch.setattr(ops.Path, "home", lambda: tmp_path)
    monkeypatch.setattr(
        ops, "context", lambda m: ({"container": "test", "watched_combat": {}}, target)
    )
    hello = NS(world="world", epoch="epoch", phase="READY")
    monkeypatch.setattr(ops, "handshake", lambda *a: (None, None, hello))
    monkeypatch.setattr(ops, "source_manifest", lambda root: {})
    monkeypatch.setattr(ops, "announce", lambda *a: None)
    monkeypatch.setattr(
        ops,
        "planned_cases",
        lambda *a: [
            {"scenario": "ROUTINE", "actors": 1},
            {"scenario": "STRIDE_COMPARE", "actors": 2},
        ],
    )
    monkeypatch.setattr(ops, "as_dict", lambda s: {"phase": getattr(s, "phase", "accepted")})
    monkeypatch.setattr(ops.subprocess, "run", lambda *a, **k: NS(stdout="", stderr=""))
    submissions = []

    def request(pb, socket, hello, operation, event="", **kw):
        if operation == "SUBMIT":
            submissions.append(kw["payload"])
            return NS(event_id=str(len(submissions)))
        if operation == "CANCEL":
            return NS()
        return state("FAILED", clean=clean, reason="route_blocked") if event == "1" else state()

    monkeypatch.setattr(ops, "request", request)
    manager = NS(ROOT=tmp_path, PODMAN="unused")
    if clean:
        with pytest.raises(RuntimeError, match="finished with recorded failures"):
            ops.run(manager, "regression")
    else:
        with pytest.raises(RuntimeError, match="critical"):
            ops.run(manager, "regression")
    assert len(submissions) == expected_count
    report = json.loads((target / "current-encounter-batch.json").read_text())
    assert report["status"] == ("completed_with_failures" if clean else "critical")
    assert report["cases"][0]["status"] == ("failed" if clean else "critical")
    if clean:
        assert report["cases"][1]["status"] == "passed_visual_pending"


def test_preparation_covers_cars_routine_combat_pooling_and_keeps_lifecycle_separate():
    cases = visual_regression.catalogue()
    assert len({c["id"] for c in cases}) == len(cases)
    assert {c["group"] for c in cases} == {"npc", "pool", "chase", "lifecycle", "vehicles"}
    runtime = [c["definition"] for c in cases if c["mode"] == "runtime"]
    assert runtime[0]["scenario"] == "STRIDE_COMPARE"
    assert runtime[1]["scenario"] == "ROUTINE"
    assert "LIFECYCLE" not in [c["scenario"] for c in runtime]
    assert max(c["actors"] for c in runtime) == 4


def test_source_capture_includes_split_java_roots(tmp_path):
    for folder in ("src", "src-vehicles", "src-runtime", "src-npc-engine"):
        file = tmp_path / "scenario-agent" / folder / "Example.java"
        file.parent.mkdir(parents=True)
        file.write_text("example")
    hashes = ops.source_manifest(tmp_path)
    assert len(hashes) == 4


def test_viewing_spots_require_explicit_current_start_option():
    import spectator_options

    assert spectator_options.parse(["start"]) == (["start"], 1)
    assert spectator_options.parse(["start", "--viewers", "2"]) == (["start"], 2)
    assert spectator_options.parse(["start"]) == (["start"], 1)
    for args in (
        ["start", "--viewers", "0"],
        ["start", "--viewers", "5"],
        ["run", "--viewers", "2"],
        ["start", "--viewers"],
    ):
        with pytest.raises(ValueError):
            spectator_options.parse(args)
