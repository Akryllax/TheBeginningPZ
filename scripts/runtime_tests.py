"""Layered offline runtime tests. Never operates a game container or RCON endpoint."""

from __future__ import annotations

import fcntl
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid

PENDING_GATES = {
    "native_functional": "pending: integrated behavior and full traversal; civilian-headless supplies separate partial native evidence",
    "same_session_reload": "pending: live Java/Lua module reload not implemented",
    "native_soak": "pending: 100 real cases and 20 reloads with resource/memory/timing checks",
    "watched": "pending: visual/audio acceptance of the new resident adapter",
    "two_client_replication": "pending: requires two actual clients",
}


def steps(root: Path, selection: str):
    if selection not in {"offline", "unit", "integration", "worker"}:
        raise ValueError("runtime-test expects offline, unit, integration or worker")
    python = str(root / ".tooling/venv/bin/python")
    build = str(root / "scripts/build_scenario_agent.py")
    result = []
    if selection in {"offline", "unit"}:
        result.append(
            ("python-components", [python, "-m", "pytest", "-q", str(root / "tests")], 300)
        )
    if selection in {"offline", "unit", "integration"}:
        layer = "all" if selection == "offline" else selection
        result.append(("java-" + layer, [python, build, "--test", "--test-layer", layer], 600))
    if selection in {"offline", "worker"}:
        result.append(
            (
                "worker-components-and-wire",
                [python, str(root / "scripts/build_npc_service.py"), "--test"],
                1200,
            )
        )
    return result


def stop_process(process):
    # Every launched step gets a new process group; stop only that group and its children.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    # A child may outlive its parent or ignore SIGTERM.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def run_step(name, command, timeout, root, output):
    started = time.monotonic()
    result = {"name": name, "command": command, "status": "failed", "log": name + ".log"}
    with (output / result["log"]).open("w") as log:
        try:
            process = subprocess.Popen(
                command, cwd=root, stdout=log, stderr=subprocess.STDOUT, start_new_session=True
            )
        except OSError as error:
            result.update(returncode=None, error=str(error))
            log.write(str(error) + "\n")
        else:
            try:
                code = process.wait(timeout=timeout)
                result.update(returncode=code, status="passed" if code == 0 else "failed")
            except subprocess.TimeoutExpired:
                stop_process(process)
                result.update(status="timeout", returncode=process.returncode)
            except KeyboardInterrupt:
                stop_process(process)
                result.update(status="interrupted", returncode=process.returncode)
    result["elapsed_seconds"] = round(time.monotonic() - started, 3)
    return result


def write_report(output, report):
    temp = output / "report.json.tmp"
    temp.write_text(json.dumps(report, indent=2) + "\n")
    temp.replace(output / "report.json")
    lines = [
        "# Runtime offline test report",
        "",
        f"Status: **{report['status']}**",
        "",
        f"Source: `{report['revision']}`; working tree dirty: `{report['dirty']}`.",
        "",
        "| Step | Result | Seconds | Log |",
        "| --- | --- | --- | --- |",
    ]
    for step in report["steps"]:
        lines.append(
            f"| {step['name']} | {step['status']} | {step.get('elapsed_seconds', '')} | [{step['log']}]({step['log']}) |"
        )
    lines += ["", "## Remaining in-game gates", ""]
    lines += [f"- {name}: {reason}" for name, reason in report["in_game_gates"].items()]
    lines += [
        "",
        "Offline success does not imply in-game functional, soak or multiplayer acceptance.",
        "",
    ]
    (output / "report.md").write_text("\n".join(lines))


def run(root: Path, selection="offline", execute=run_step):
    planned = steps(root, selection)
    base = root / "artifacts/runtime-tests"
    base.mkdir(parents=True, exist_ok=True)
    with (base / "runner.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("Another runtime test run owns the build outputs") from error
        output = base / (time.strftime("%Y%m%d-%H%M%S", time.gmtime()) + "-" + uuid.uuid4().hex[:8])
        output.mkdir()
        revision = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True
        )
        dirty = subprocess.run(
            ["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True
        )
        report = {
            "schema_version": 1,
            "selection": selection,
            "status": "running",
            "revision": revision.stdout.strip() if revision.returncode == 0 else "unknown",
            "dirty": bool(dirty.stdout) if dirty.returncode == 0 else None,
            "in_game_gates": dict(PENDING_GATES),
            "steps": [],
        }
        write_report(output, report)
        for name, command, timeout in planned:
            print(f"Running {name}…", flush=True)
            result = execute(name, command, timeout, root, output)
            report["steps"].append(result)
            print(f"  {result['status']}: {output / result['log']}", flush=True)
            write_report(output, report)
            if result["status"] == "interrupted":
                break
        report["status"] = (
            "passed"
            if len(report["steps"]) == len(planned)
            and all(item["status"] == "passed" for item in report["steps"])
            else "failed"
        )
        report["not_run"] = [name for name, _, _ in planned[len(report["steps"]) :]]
        write_report(output, report)
        print(f"Report: {output / 'report.md'}", flush=True)
        return 0 if report["status"] == "passed" else 1


def dispatch(root, arguments):
    if len(arguments) > 1:
        raise RuntimeError("Usage: ./dayone runtime-test [offline|unit|integration|worker]")
    try:
        code = run(root, arguments[0] if arguments else "offline")
    except ValueError as error:
        raise RuntimeError(str(error)) from error
    if code:
        raise RuntimeError("Runtime tests failed; inspect the retained report and logs")
