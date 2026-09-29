"""Regression tests for test reporting, process failures and offline/live separation."""

import importlib.util
import json
from pathlib import Path
import sys

import pytest

spec = importlib.util.spec_from_file_location(
    "runtime_tests", Path(__file__).parents[1] / "scripts/runtime_tests.py"
)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


@pytest.mark.parametrize(
    "selection,names",
    [
        ("unit", ["python-components", "java-unit"]),
        ("integration", ["java-integration"]),
        ("worker", ["worker-components-and-wire"]),
        ("offline", ["python-components", "java-all", "worker-components-and-wire"]),
    ],
)
def test_layer_selection_never_operates_game(tmp_path, selection, names):
    steps = runner.steps(tmp_path, selection)
    assert [name for name, _, _ in steps] == names
    for _, command, timeout in steps:
        assert "rcon" not in command and "podman" not in command and "--bundle" not in command
        assert timeout > 0


def test_live_gate_cannot_be_claimed_by_offline_selection(tmp_path):
    with pytest.raises(ValueError):
        runner.steps(tmp_path, "native")


def test_failed_step_keeps_logs_runs_other_layers_and_fails_report(tmp_path):
    calls = []

    def execute(name, command, timeout, root, output):
        calls.append(name)
        (output / (name + ".log")).write_text("retained failure evidence\n")
        return {
            "name": name,
            "log": name + ".log",
            "status": "failed" if len(calls) == 1 else "passed",
        }

    assert runner.run(tmp_path, "offline", execute) == 1
    assert len(calls) == 3
    path = next(tmp_path.glob("artifacts/runtime-tests/*/report.json"))
    report = json.loads(path.read_text())
    assert report["status"] == "failed"
    assert report["steps"][0]["status"] == "failed"
    assert all(value.startswith("pending:") for value in report["in_game_gates"].values())
    assert (path.parent / "report.md").exists()
    assert (path.parent / "python-components.log").read_text() == "retained failure evidence\n"


def test_passing_unit_report_does_not_promote_game_gates(tmp_path):
    def execute(name, command, timeout, root, output):
        return {"name": name, "log": name + ".log", "status": "passed"}

    assert runner.run(tmp_path, "unit", execute) == 0
    report = json.loads(next(tmp_path.glob("artifacts/runtime-tests/*/report.json")).read_text())
    assert report["selection"] == "unit"
    assert report["status"] == "passed"
    assert report["in_game_gates"]["native_soak"].startswith("pending:")


def test_interrupt_retains_report_and_does_not_launch_later_steps(tmp_path):
    def execute(name, command, timeout, root, output):
        return {"name": name, "log": name + ".log", "status": "interrupted"}

    assert runner.run(tmp_path, "offline", execute) == 1
    report = json.loads(next(tmp_path.glob("artifacts/runtime-tests/*/report.json")).read_text())
    assert report["not_run"] == ["java-all", "worker-components-and-wire"]


def test_overlapping_run_is_rejected(tmp_path):
    base = tmp_path / "artifacts/runtime-tests"
    base.mkdir(parents=True)
    with (base / "runner.lock").open("a") as lock:
        runner.fcntl.flock(lock, runner.fcntl.LOCK_EX | runner.fcntl.LOCK_NB)
        with pytest.raises(RuntimeError, match="Another runtime test"):
            runner.run(tmp_path)


@pytest.mark.parametrize("code,status", [(0, "passed"), (7, "failed")])
def test_real_process_exit_and_log_are_retained(tmp_path, code, status):
    result = runner.run_step(
        "process",
        [sys.executable, "-c", f"print('evidence'); raise SystemExit({code})"],
        5,
        tmp_path,
        tmp_path,
    )
    assert result["status"] == status and result["returncode"] == code
    assert (tmp_path / result["log"]).read_text().strip() == "evidence"


def test_timeout_is_failure_and_process_is_reaped(tmp_path):
    result = runner.run_step(
        "timeout", [sys.executable, "-c", "import time; time.sleep(10)"], 0.05, tmp_path, tmp_path
    )
    assert result["status"] == "timeout"
    assert result["returncode"] is not None
    assert result["elapsed_seconds"] < 5


def test_missing_tool_produces_failure_receipt(tmp_path):
    result = runner.run_step("missing", [str(tmp_path / "missing")], 1, tmp_path, tmp_path)
    assert result["status"] == "failed" and result["returncode"] is None
    assert result["error"]
