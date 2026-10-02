"""Prepare a reproducible cross-harness watched regression without launching a game."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import uuid

import civilian_encounter as encounters


def catalogue():
    """Keep implemented cases distinct from planned features and historical experiments."""
    cases = []
    for n, definition in enumerate(encounters.planned_cases("regression"), 1):
        cases.append(
            {
                "id": f"npc-{n:02}",
                "group": "npc",
                "mode": "runtime",
                "definition": definition,
                "observe": {
                    "STRIDE_COMPARE": "Two civilians travel 50 tiles: runner faster; both smooth, human appearance, footsteps.",
                    "ROUTINE": "One civilian walks out, waits at activity, returns to its starting point; no teleport or stale goal.",
                    "LOCOMOTION": "Walk/run, changes of direction, soft cancellation and resumption; no wall clipping or position jumps.",
                    "OPEN_ESCAPE": "Civilian notices native zombie and escapes using native movement.",
                    "INCOMING_INJURY": "Native hit reaction and injury, then escape; no forced hit when zombie does not engage.",
                    "DEFENSE_ESCAPE": "Shove/strike animation, contact sound, native damage, recovery and escape; independent participants.",
                }[definition["scenario"]],
            }
        )
    cases += [
        {
            "id": "pool-four",
            "group": "pool",
            "mode": "startup_harness",
            "commands": [
                "civilian-watch create 4 0",
                "civilian-watch start",
                "civilian-watch join",
            ],
            "observe": "Four southbound 150-tile waves, four Actors each; changed identities on the same four initialized bodies, footsteps and exact cleanup.",
        },
        {
            "id": "offscreen-chase",
            "group": "chase",
            "mode": "startup_harness",
            "commands": ["civilian-chase create", "civilian-chase start", "civilian-chase join"],
            "observe": "One civilian and one native zombie enter view already moving; no visible spawn.",
        },
        {
            "id": "fatal-lifecycle",
            "group": "lifecycle",
            "mode": "runtime_separate_world",
            "commands": [
                "civilian-combat lifecycle-create",
                "civilian-combat start",
                "civilian-combat join",
                "civilian-combat run lifecycle",
            ],
            "observe": "Defense, native death, corpse contents, reanimation, new identity on original pooled Actor, replacement walk and cleanup. No engagement is NOT_EXERCISED.",
        },
        {
            "id": "car-turn-stop",
            "group": "vehicles",
            "mode": "operator_probe",
            "route": "vehicle-lane-course.json",
            "observe": "Smooth continuous turn, correct lane, stop sign, realistic speed and acceleration, stock physics and vehicle cleanup.",
        },
        {
            "id": "car-blockage-pass",
            "group": "vehicles",
            "mode": "operator_probe",
            "route": "vehicle-bypass-course.json",
            "observe": "Gradual stop behind exactly tracked parked car, wait, personality-dependent honk, safe pass and lane return.",
        },
        {
            "id": "car-shoulder-pole",
            "group": "vehicles",
            "mode": "operator_probe",
            "route": "vehicle-shoulder-course.json",
            "observe": "Road blockage and pole reject unsafe shoulder route; car remains visible. Remove only the owned denial fixture for allowed bypass if geometry permits.",
        },
        {
            "id": "car-impact",
            "group": "vehicles",
            "mode": "operator_probe",
            "route": "vehicle-high-speed-impact.json",
            "observe": "Real pole collision, native damage and impact sound; observer outside path, obstacle held for 3 seconds after impact.",
        },
        {
            "id": "car-brake-failure",
            "group": "vehicles",
            "mode": "operator_probe",
            "route": "vehicle-stove-impact.json",
            "observe": "Car attempts braking with damaged stock brake parts, collides with tagged stove; native damage/audio, obstacle held for 3 seconds. Rollover is not required.",
        },
    ]
    return cases


def prepare(root, viewers=1):
    """Freeze source hashes, available route artifacts and the operator checklist, with no deployment."""
    root = Path(root)
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime()) + "-" + uuid.uuid4().hex[:8]
    target = root / "artifacts/visual-regression" / stamp
    target.mkdir(parents=True, mode=0o700)
    plan = {
        "viewing_spots": viewers,
        "id": stamp,
        "status": "prepared_not_run",
        "path": str(target),
        "source_revision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True
        ).strip(),
        "source_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=root)),
        "target": "disposable 192.168.1.132:16301; pinned ordinary 42.21.0 client",
        "policy": {
            "case_failure": "record failure, cancel if active, verify exact-resource cleanup, then continue",
            "critical": "stop on uncertain cleanup/ownership, epoch change, transport failure, observer loss/stale reports, invalid protection or engine failure",
            "feedback": "separate per-case human verdict after each group; never infer visuals from telemetry",
            "waits": "2 second countdown, 8 second encounter viewing hold; readiness is condition-based",
        },
        "not_implemented_or_not_in_this_batch": [
            "Scheduled ground-floor homes and workday routines; only existing short out/wait/home routine is exercised",
            "Controlled resident restart and crash/aftermath recovery",
            "Two-client qualification and crowd capacity/64 civilians plus 128 hunters",
            "Opposing-car crashes and guaranteed rollover; incomplete historical experiments",
            "Generic hot-reload vehicle batch API and automatic fixture bridge; current car harness requires stopped-only route configuration",
        ],
        "cases": catalogue(),
    }
    hashes = encounters.source_manifest(root)
    for case in plan["cases"]:
        case["viewing_spots"] = viewers
        case["status"] = "not_run"
        if viewers > 1 and case["mode"] in {"startup_harness", "operator_probe"}:
            case["status"] = "blocked_multi_viewer_harness"
        if "commands" in case and case["mode"] == "runtime_separate_world":
            case["commands"] = [
                c + f" --viewers {viewers}" if c == "civilian-combat start" else c
                for c in case["commands"]
            ]
        case["human_verdict"] = "pending"
        if "route" in case:
            source = root / "artifacts/scenario-map" / case["route"]
            case["fixture_gate"] = (
                "operator must reconcile exact fixture IDs/tokens, current AKR APIs, spectator setup and clean baseline before execution; historical reloadlua scripts are not auto-run"
            )
            if source.is_file():
                dest = target / "routes" / source.name
                dest.parent.mkdir(exist_ok=True)
                shutil.copy2(source, dest)
                case["route_copy"] = str(dest.relative_to(target))
                case["route_sha256"] = hashlib.sha256(dest.read_bytes()).hexdigest()
            else:
                case["status"] = "blocked_missing_route"
    encounters.atomic_json(target / "source-hashes.json", hashes)
    manifest = root / "artifacts/scenario-agent/manifest.json"
    if manifest.exists():
        shutil.copy2(manifest, target / "agent-manifest.json")
    encounters.atomic_json(target / "plan.json", plan)
    encounters.atomic_json(target.parent / "current.json", {"path": str(target)})
    lines = [
        "# Prepared visual regression",
        "",
        "Preparation only. No server/client has been launched.",
        f"Spectator spots: {viewers}. Start encounter/lifecycle worlds with ./dayone civilian-combat start --viewers {viewers}.",
        "",
        "Run groups in order: NPC runtime → four-Actor reuse → off-screen chase → lifecycle → vehicle probes.",
        "Harness boundaries require a controlled disposable-server switch and fresh in-world readiness; never reuse an old ready reply.",
        "",
        "Before every case: automatic clear viewing position/facing, admin/god/invisible/ghost, noon/clear weather, loaded 9mm and two loaded spare magazines.",
        "",
        "Record machine outcome, cleanup, scoped logs and timing independently of human animation/audio feedback.",
        "",
        "| Case | Harness | What to watch |",
        "| --- | --- | --- |",
    ]
    lines += [f"| {c['id']} | {c['mode']} | {c['observe']} |" for c in plan["cases"]]
    lines += [
        "",
        "## Execution",
        "",
        "See vault/Runbooks/Visual Regression Batch.md for group commands and vehicle fixture gates.",
        "",
        "An ordinary failed case may continue only after verified cleanup. Critical failure stops the entire batch, including later groups.",
        "No failed, blocked, missing-evidence or unexercised case counts as a pass.",
    ]
    (target / "CHECKLIST.md").write_text("\n".join(lines) + "\n")
    print(f"Prepared {len(plan['cases'])} cases: {target / 'CHECKLIST.md'}")
    return target


def dispatch(m, args):
    import spectator_options

    args, viewers = spectator_options.parse(args)
    if args == ["prepare"]:
        return prepare(m.ROOT, viewers)
    if args == ["status"]:
        pointer = json.loads((m.ROOT / "artifacts/visual-regression/current.json").read_text())
        target = Path(pointer["path"]).resolve()
        if not target.is_relative_to((m.ROOT / "artifacts/visual-regression").resolve()):
            raise RuntimeError("Visual plan path outside project")
        print((target / "plan.json").read_text())
        return
    raise ValueError(
        "Usage: ./dayone visual-test prepare|status; see the prepared checklist for execution groups"
    )
