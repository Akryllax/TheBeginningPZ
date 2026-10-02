"""Versioned, offline game compatibility auditing. Never approves a build implicitly."""

from __future__ import annotations

import argparse
from collections import Counter
import datetime as dt
import fcntl
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from compat_classfile import fingerprint, parse_class

ROOT = Path(__file__).resolve().parents[1]
PENDING = ["native execution", "watched acceptance", "two-client replication"]


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_profile(root, name):
    if not name.replace("-", "").replace(".", "").isalnum():
        raise ValueError("Invalid profile name")
    profile = json.loads((root / "compatibility/profiles" / f"{name}.json").read_text())
    if profile["id"] != name:
        raise ValueError("Profile identity mismatch")
    return profile


def snapshot(root, profile):
    """Capture immutable input hashes and mapped class contracts without loading the JVM."""
    jar = root / profile["game_jar"]
    jar_hash = digest(jar)
    if jar_hash != profile["jar_sha256"]:
        raise ValueError("Game archive differs from the selected exact profile")
    result = {
        "schema": 1,
        "profile": profile["id"],
        "profile_hash": fingerprint(profile),
        "jar_sha256": jar_hash,
        "classes": {},
        "class_hashes": {},
        "native": {},
        "dependencies": {},
    }
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if entry.filename.endswith(".class"):
                data = archive.read(entry)
                name = entry.filename[:-6]
                result["class_hashes"][name] = hashlib.sha256(data).hexdigest()
                if name in profile["classes"]:
                    result["classes"][name] = parse_class(data)
    for key in profile["classes"]:
        if key not in result["classes"]:
            raise ValueError(f"Missing mapped class: {key}")
    for name, path in profile.get("native", {}).items():
        result["native"][name] = digest(root / path)
    for name, path in profile.get("dependencies", {}).items():
        result["dependencies"][name] = digest(root / path)
    directory = root / "artifacts/compat/snapshots" / profile["id"]
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / f"{fingerprint(result)}.json"
    content = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if target.exists() and target.read_text() != content:
        raise ValueError("Immutable snapshot collision")
    if not target.exists():
        target.write_text(content)
    return result, target


def compare(old, new, contracts):
    """Separate required contract breaks from behavioral and unmapped changes."""
    changes, breaks = [], []
    for contract in contracts:
        cls = new["classes"].get(contract["class"])
        member = None if cls is None else cls[contract["kind"]].get(contract["member"])
        if member is None or member["access"] & contract["access_mask"] != contract["access"]:
            breaks.append(
                {**contract, "reason": "missing member or changed required access/static flags"}
            )
    for name in sorted(old["class_hashes"].keys() | new["class_hashes"].keys()):
        if old["class_hashes"].get(name) == new["class_hashes"].get(name):
            continue
        a, b = old["classes"].get(name), new["classes"].get(name)
        classification = "outside-mapped-coverage"
        detail = []
        if a is not None and b is not None:
            aa, bb = dict(a), dict(b)
            aa.pop("sha256")
            bb.pop("sha256")
            classification = "metadata-only" if aa == bb else "behavioral-review"
            for kind in ("fields", "methods"):
                for member in sorted(a[kind].keys() | b[kind].keys()):
                    if a[kind].get(member) != b[kind].get(member):
                        detail.append(
                            {
                                "kind": kind,
                                "member": member,
                                "before": fingerprint(a[kind].get(member)),
                                "after": fingerprint(b[kind].get(member)),
                            }
                        )
        changes.append({"class": name, "classification": classification, "members": detail})
    for kind in ("native", "dependencies"):
        for name in sorted(old[kind].keys() | new[kind].keys()):
            if old[kind].get(name) != new[kind].get(name):
                changes.append(
                    {
                        "dependency": name,
                        "classification": "behavioral-review",
                        "kind": kind,
                        "before": old[kind].get(name),
                        "after": new[kind].get(name),
                    }
                )
    return {
        "breaks": breaks,
        "changes": changes,
        "counts": dict(Counter(c["classification"] for c in changes)),
    }


def source_identity(root):
    """Hash tracked and untracked authored content, including deletions and dirty edits."""
    names = (
        subprocess.check_output(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root
        )
        .decode()
        .split("\0")
    )
    hashes = {
        name: digest(root / name) if (root / name).is_file() else "missing"
        for name in sorted(set(names))
        if name
    }
    return {
        "revision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True
        ).strip(),
        "content_hash": fingerprint(hashes),
        "files": hashes,
    }


def write_report(root, report):
    directory = (
        root / "artifacts/compat/runs" / dt.datetime.now(dt.UTC).strftime("%Y%m%dT%H%M%S.%fZ")
    )
    directory.mkdir(parents=True)
    (directory / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    lines = [
        "# Compatibility report",
        "",
        f"Status: **{report['status']}**; exit {report['exit_code']}.",
        "",
        f"Source content: `{report['source']['content_hash']}`.",
        "",
        "Snapshotting does not approve guards or qualify gameplay.",
        "",
        "## Results",
        "",
    ]
    for issue in report.get("breaks", []):
        lines.append(f"- BREAK: `{issue['class']}.{issue['member']}` — {issue['reason']}")
    for test in report.get("tests", []):
        lines.append(f"- {test['name']}: {test['status']} ({test.get('reason', '')}).")
    lines.extend(
        [
            "",
            f"Change counts: `{report.get('counts', {})}`.",
            "",
            "Pending: " + ", ".join(PENDING) + ".",
        ]
    )
    for issue in report.get("blocked", []):
        lines.append(f"- BLOCKED: {issue}")
    (directory / "report.md").write_text("\n".join(lines) + "\n")
    print(directory / "report.md")
    return directory


def run_tests(root, profile, snap, full):
    """Share the runtime-test output lock; concurrent qualification must not overwrite builds."""
    directory = root / "artifacts/runtime-tests"
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / "runner.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return _run_tests(root, profile, snap, full)


def _run_tests(root, profile, snap, full):
    """Run code-only groups; conservative source fingerprints invalidate all cached groups."""
    from runtime_tests import run_step

    identity = source_identity(root)
    python = root / ".tooling/venv/bin/python"
    if not python.exists():
        python = Path(sys.executable)
    tools = {
        "python": digest(python.resolve()),
        "auditor": digest(root / "scripts/compat_classfile.py"),
    }
    key = fingerprint(
        {
            "source": identity["files"],
            "snapshot": fingerprint(snap),
            "profile": profile,
            "tools": tools,
        }
    )
    cache = root / "artifacts/compat/cache" / f"{key}.json"
    previous = json.loads(cache.read_text()) if not full and cache.exists() else None
    if previous and not all(
        item.get("status") == "passed"
        and Path(item["log"]).is_file()
        and digest(Path(item["log"])) == item.get("log_sha256")
        for item in previous["tests"]
    ):
        previous = None
    output = (
        root / "artifacts/compat/test-runs" / dt.datetime.now(dt.UTC).strftime("%Y%m%dT%H%M%S.%fZ")
    )
    output.mkdir(parents=True)
    if previous:
        tests = [
            {
                **previous["tests"][0],
                "status": "cached",
                "source_run": previous["run"],
                "reason": "identical authored, engine, profile and tool content",
            }
        ]
    else:
        tests = [
            run_step(
                "python-components", [str(python), "-m", "pytest", "-q", "tests"], 300, root, output
            )
        ]
        tests[0]["reason"] = (
            "conservative full group: authored or engine content changed, or --full"
        )
        tests[0]["log"] = str(output / tests[0]["log"])
    tests.append(
        run_step(
            "java-code-and-hooks",
            [str(python), "scripts/compat_java.py", "--profile", profile["id"]],
            600,
            root,
            output,
        )
    )
    tests[-1]["reason"] = (
        "always fresh: compile, structural verifier, exact hook counts, detached Java components"
    )
    tests[-1]["log"] = str(output / tests[-1]["log"])
    for item in tests:
        if item["status"] != "cached":
            item["log_sha256"] = digest(Path(item["log"]))
    if source_identity(root)["content_hash"] != identity["content_hash"]:
        tests.append(
            {
                "name": "source-stability",
                "status": "blocked",
                "reason": "authored content changed during the run; no cache entry written",
            }
        )
        return tests
    if not previous and all(t["status"] == "passed" for t in tests):
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_text(json.dumps({"run": str(output), "tests": tests}, indent=2) + "\n")
    return tests


def dispatch(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("snapshot", "test"):
        sub = commands.add_parser(command)
        sub.add_argument("--profile", required=True)
        if command == "test":
            sub.add_argument("--full", action="store_true")
    sub = commands.add_parser("compare")
    sub.add_argument("--baseline", required=True)
    sub.add_argument("--candidate", required=True)
    args = parser.parse_args(argv)
    report = {
        "source": source_identity(ROOT),
        "status": "blocked",
        "exit_code": 2,
        "blocked": [],
        "pending": PENDING,
    }
    try:
        profile = read_profile(ROOT, args.candidate if args.command == "compare" else args.profile)
        snap, path = snapshot(ROOT, profile)
        report.update(profile=profile["id"], snapshot=str(path))
        if args.command == "compare":
            baseline = read_profile(ROOT, args.baseline)
            old, old_path = snapshot(ROOT, baseline)
            report.update(compare(old, snap, baseline["contracts"]))
            report["baseline_snapshot"] = str(old_path)
            if report["breaks"]:
                report.update(status="breaking", exit_code=1)
            else:
                report["blocked"].append(
                    "Changes require explicit review; approval is never inferred from hashes"
                )
        elif args.command == "test":
            report.update(compare(snap, snap, profile["contracts"]))
            if report["breaks"]:
                report.update(status="breaking", exit_code=1)
            report["tests"] = run_tests(ROOT, profile, snap, args.full)
            if any(t["status"] not in {"passed", "cached", "blocked"} for t in report["tests"]):
                report.update(status="failed", exit_code=1)
            report["blocked"].extend(
                [
                    "Remaining engine dependency coverage and review decisions are incomplete",
                    "Profile review not yet qualified for runtime",
                ]
            )
        else:
            report["blocked"].append(
                "Snapshot captured; this operation does not approve a runtime build"
            )
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        report["blocked"].append(str(error))
    write_report(ROOT, report)
    return report["exit_code"]


if __name__ == "__main__":
    raise SystemExit(dispatch(sys.argv[1:]))
