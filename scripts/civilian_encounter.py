"""Same-process civilian encounter batches and immutable watched evidence."""

from __future__ import annotations
import fcntl
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid
import runtime_control as runtime
import scenario_ops as scenario
import watched_policy

TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}


def planned_cases(selection="batch", actors=1):
    if selection == "regression":
        if actors != 1:
            raise ValueError("Regression defines its own counts")
        return planned_cases("compare") + planned_cases("routine") + planned_cases("batch")
    if selection == "compare":
        if actors not in (1, 2):
            raise ValueError("Comparison requires two actors")
        return [
            {
                "scenario": "STRIDE_COMPARE",
                "actors": 2,
                "seed": 1,
                "timeout_seconds": 120,
                "hold_seconds": 8,
            }
        ]
    if actors not in (1, 4):
        raise ValueError("Encounter actors must be 1 or 4")
    names = {
        "open": "OPEN_ESCAPE",
        "injury": "INCOMING_INJURY",
        "defense": "DEFENSE_ESCAPE",
        "gait": "LOCOMOTION",
        "lifecycle": "LIFECYCLE",
        "routine": "ROUTINE",
    }
    if selection == "batch":
        if actors != 1:
            raise ValueError("The batch defines its own one/four counts")
        choices = [
            ("LOCOMOTION", 1),
            ("OPEN_ESCAPE", 1),
            ("INCOMING_INJURY", 1),
            ("DEFENSE_ESCAPE", 1),
        ] + [("DEFENSE_ESCAPE", 4)] * 3
    elif selection in names:
        if selection != "defense" and actors != 1:
            raise ValueError("Open and injury gates require one civilian")
        choices = [(names[selection], actors)]
    else:
        raise ValueError(
            "Expected regression, batch, routine, gait, compare, open, injury, defense or lifecycle"
        )
    return [
        {
            "scenario": name,
            "actors": count,
            "seed": i + 1,
            "timeout_seconds": 120,
            "hold_seconds": 8,
        }
        for i, (name, count) in enumerate(choices)
    ]


def atomic_json(path, data):
    temp = path.with_suffix(path.suffix + ".tmp")
    scenario.write_private(temp, json.dumps(data, indent=2) + "\n")
    temp.replace(path)


def context(m):
    receipt, target = scenario.test_receipt(m)
    if receipt.get("watched_combat", {}).get("scope") != "moving_encounter":
        raise RuntimeError("Current disposable server is not an encounter runtime")
    return receipt, target


def handshake(m, target):
    pb = runtime.protocol(m.ROOT)
    socket = target / "ipc/runtime.sock"
    hello = runtime.exchange(
        socket, pb.Request(version=1, request_id="encounter-hello", operation=pb.Request.HELLO), pb
    )
    if "civilian-encounter-v1" not in hello.capability:
        raise RuntimeError("Deployed runtime lacks encounter capability")
    return pb, socket, hello


def request(pb, socket, hello, operation, event="", payload=None, identity=None):
    message = pb.Request(
        version=1,
        world=hello.world,
        epoch=hello.epoch,
        request_id=identity or uuid.uuid4().hex,
        operation=getattr(pb.Request, operation),
        event_id=event,
    )
    if payload is not None:
        from google.protobuf.json_format import ParseDict

        ParseDict(payload, message.civilian_encounter)
    reply = runtime.exchange(socket, message, pb)
    if not reply.accepted:
        raise RuntimeError(f"Runtime rejected {operation}: {reply.code}")
    return reply


def as_dict(message):
    from google.protobuf.json_format import MessageToDict

    return MessageToDict(
        message, preserving_proto_field_name=True, always_print_fields_with_no_presence=True
    )


def log_boundary(path):
    if not path.exists():
        return None
    stat = path.stat()
    return {"inode": stat.st_ino, "offset": stat.st_size}


def log_slice(path, boundary):
    if not path.exists() or boundary is None:
        return None
    stat = path.stat()
    if stat.st_ino != boundary["inode"] or stat.st_size < boundary["offset"]:
        return None
    with path.open("rb") as stream:
        stream.seek(boundary["offset"])
        return stream.read()


def source_manifest(root):
    paths = set()
    for folder in (
        *(
            str(p.relative_to(root))
            for p in sorted((root / "scenario-agent").glob("src*"))
            if p.is_dir()
        ),
        "npc-service/src",
        "npc-service/rules",
        "mods/AKRCore",
        "mods/AKRDevTools",
        "mods/AKRResidents",
        "mods/AKRPopulation",
        "protocol",
        "scripts",
    ):
        paths.update(
            p for p in (root / folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts
        )
    return {
        str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)
    }


def announce(m, message):
    # Messages here consist only of internal scenario names, counts and fixed wording.
    scenario.test_rcon(m, 'servermsg "' + message.replace('"', "'").replace("\n", " ") + '"')


def run(m, selection="batch", actors=1):
    cases = planned_cases(selection, actors)
    receipt, target = context(m)
    if (selection == "lifecycle") != bool(receipt["watched_combat"].get("lifecycle")):
        raise RuntimeError("Lifecycle requires its separate lifecycle-create session")
    lock_path = target / "encounter-run.lock"
    with lock_path.open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("An encounter batch is already running") from error
        current = target / "current-encounter-batch.json"
        if current.exists():
            previous = json.loads(current.read_text())
            if previous.get("status") in {
                "running",
                "interrupted",
                "cleanup_blocked",
                "unreconciled",
                "critical",
            }:
                raise RuntimeError(
                    "Previous batch needs status/cancel reconciliation before another run"
                )
        pb, socket, hello = handshake(m, target)
        if hello.phase != "READY":
            raise RuntimeError("Game runtime has not reached its game-thread readiness gate")
        batch_id = time.strftime("%Y%m%d-%H%M%S", time.gmtime()) + "-" + uuid.uuid4().hex[:8]
        folder = target / "encounter-batches" / batch_id
        folder.mkdir(parents=True)
        console = Path.home() / "Zomboid/console.txt"
        boundary = log_boundary(console)
        since = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        report = {
            "world": hello.world,
            "epoch": hello.epoch,
            "batch": batch_id,
            "path": str(folder),
            "status": "running",
            "selection": selection,
            "viewing_spots": receipt["watched_combat"].get("viewing_spots", 1),
            "cases": [],
            "scope": "ordinary-client "
            + ("fatal lifecycle" if selection == "lifecycle" else "encounter regression")
            + "; human acceptance and two-client qualification separate",
            "failure_policy": "continue only after verified cleanup and stable runtime",
            "client_log_boundary": boundary,
            "started_at": since,
        }
        atomic_json(folder / "source-hashes.json", source_manifest(m.ROOT))
        for filename in ("agent/manifest.json", "client-mod-install.json"):
            path = target / filename
            if path.exists():
                scenario.write_private(folder / Path(filename).name, path.read_text())

        def save():
            atomic_json(folder / "machine-report.json", report)
            atomic_json(current, report)

        save()
        event = None
        try:
            announce(
                m,
                f"BATCH START: {len(cases)} cases, {sum(c['actors'] for c in cases)} civilian assignments. Automatic placement; feedback after the batch.",
            )
            for index, definition in enumerate(cases, 1):
                _, _, fresh = handshake(m, target)
                watched_policy.verify_runtime(hello, fresh)
                hunters = (
                    0
                    if definition["scenario"] in {"STRIDE_COMPARE", "ROUTINE"}
                    else definition["actors"]
                )
                announce(
                    m,
                    f"CASE {index}/{len(cases)}: {definition['scenario']}; {definition['actors']} civilians, {hunters} fast shamblers.",
                )
                accepted = request(
                    pb, socket, hello, "SUBMIT", payload=definition, identity=f"{batch_id}-{index}"
                )
                event = accepted.event_id
                entry = {"event": event, "definition": definition, "status": "running"}
                case_boundary = log_boundary(console)
                report["cases"].append(entry)
                save()
                deadline = time.monotonic() + 450
                with (folder / f"case-{index}-samples.jsonl").open("w") as samples:
                    while True:
                        state = request(pb, socket, hello, "STATUS", event)
                        entry["result"] = as_dict(state)
                        samples.write(
                            json.dumps({"received_at": time.time(), **entry["result"]}) + "\n"
                        )
                        samples.flush()
                        save()
                        if state.phase in TERMINAL:
                            entry["phase"] = state.phase
                            entry["scenario_outcome"] = state.encounter.scenario_outcome
                            entry["status"], entry["issue"] = watched_policy.classify(state)
                            if entry["status"] == "critical":
                                report["status"] = "critical"
                                raise RuntimeError(f"Case {index}: critical: {entry['issue']}")
                            break
                        if state.phase == "CLEANUP_BLOCKED":
                            # The engine can require a few ticks for removal; retain a bounded window.
                            entry.setdefault("cleanup_blocked_since", time.monotonic())
                            if (
                                "unexpected_death_resources_retained" in state.reason
                                or time.monotonic() - entry["cleanup_blocked_since"] > 24
                            ):
                                report["status"] = "cleanup_blocked"
                                raise RuntimeError(f"Case {index}: cleanup remains blocked")
                        else:
                            entry.pop("cleanup_blocked_since", None)
                        if time.monotonic() > deadline:
                            if entry.get("timeout_cancelled"):
                                report["status"] = "critical"
                                raise RuntimeError(f"Case {index}: timed-out cleanup not verified")
                            entry["timeout_cancelled"] = True
                            entry["cancel_reply"] = as_dict(
                                request(pb, socket, hello, "CANCEL", event)
                            )
                            deadline = time.monotonic() + 24
                        time.sleep(1)
                if entry.get("timeout_cancelled"):
                    entry["status"], entry["issue"] = "failed", "operator_timeout"
                excerpt = log_slice(console, case_boundary)
                entry["client_log_available"] = excerpt is not None
                if excerpt is not None:
                    (folder / f"case-{index}-client-console.txt").write_bytes(excerpt)
                    errors = [
                        line
                        for line in excerpt.decode(errors="replace").splitlines()
                        if "ERROR" in line or "Exception" in line
                    ]
                    entry["client_error_lines"] = len(errors)
                    if errors:
                        entry["status"] = "failed"
                        entry["client_issue"] = "new_client_error_lines; inspect scoped log"
                else:
                    entry["status"] = "failed"
                    entry["client_issue"] = "client_log_unavailable"
                save()
                announce(
                    m,
                    f"CASE {index}/{len(cases)}: {entry['status']}; cleanup VERIFIED. "
                    + ("Continuing to next case." if index < len(cases) else "Last case finished."),
                )
                event = None
            report["status"] = watched_policy.batch_status(report["cases"])
            announce(
                m,
                "BATCH COMPLETE: "
                + report["status"]
                + ". All case results retained; visual/audio feedback remains separate.",
            )
        except (Exception, KeyboardInterrupt) as error:
            report["error"] = str(error) or type(error).__name__
            if report["status"] == "running":
                report["status"] = (
                    "interrupted" if isinstance(error, KeyboardInterrupt) else "unreconciled"
                )
            if event:
                try:
                    report["cancel_reply"] = as_dict(request(pb, socket, hello, "CANCEL", event))
                    cleanup_deadline = time.monotonic() + 24
                    while True:
                        cleanup = request(pb, socket, hello, "STATUS", event)
                        report["cleanup_after_stop"] = as_dict(cleanup)
                        save()
                        if cleanup.phase in TERMINAL or time.monotonic() >= cleanup_deadline:
                            break
                        time.sleep(1)
                except Exception as cancel_error:
                    report["cancel_error"] = str(cancel_error)
            try:
                announce(
                    m,
                    "BATCH STOPPED. No further cases will start; inspecting retained outcome and cleanup.",
                )
            except Exception:
                pass
            raise
        finally:
            excerpt = log_slice(console, boundary)
            report["client_log_available"] = excerpt is not None
            if excerpt is not None:
                (folder / "client-console.txt").write_bytes(excerpt)
                (folder / "client-console.txt").chmod(0o600)
                report["client_error_lines"] = sum(
                    "ERROR" in line or "Exception" in line
                    for line in excerpt.decode(errors="replace").splitlines()
                )
            result = subprocess.run(
                [str(m.PODMAN), "logs", "--since", since, receipt["container"]],
                capture_output=True,
                text=True,
            )
            scenario.write_private(folder / "server-console.txt", result.stdout + result.stderr)
            save()
            print(f"Evidence: {folder / 'machine-report.json'}")
        if report["status"] == "completed_with_failures":
            raise RuntimeError(
                "Batch finished with recorded failures; all remaining safe cases ran"
            )


def dispatch(m, args):
    action = args[0] if args else "status"
    if action in {"encounter-create", "lifecycle-create"}:
        if len(args) != 1:
            raise ValueError("encounter-create takes no arguments")
        import civilian_combat

        civilian_combat.dispatch(m, ["survival-create"])
        receipt, target = scenario.test_receipt(m)
        receipt["watched_combat"]["scope"] = "moving_encounter"
        receipt["watched_combat"]["lifecycle"] = action == "lifecycle-create"
        props = target / "scenario.properties"
        text = "\n".join(
            line for line in props.read_text().splitlines() if not line.startswith("combat.")
        )
        scenario.write_private(
            props,
            text.replace("socket=/run/akr/no-planner.sock", "socket=/run/akr/npc.sock")
            + "\nencounter.enabled=true\nencounter.directory=/run/akr\n",
        )
        if action == "lifecycle-create":
            import re

            sandbox = target / "Zomboid/Server" / (receipt["world"] + "_SandboxVars.lua")
            text, count = re.subn(
                r"(?m)^(\s*Transmission\s*=\s*)[^,\n]+,", r"\g<1>1,", sandbox.read_text()
            )
            if count != 1:
                raise RuntimeError("Expected one disposable Transmission setting")
            text, count = re.subn(r"(?m)^(\s*Reanimate\s*=\s*)[^,\n]+,", r"\g<1>5,", text)
            if count != 1:
                raise RuntimeError("Expected one disposable Reanimate setting")
            for key, value in (("Strength", 1), ("Hearing", 1), ("Sight", 1)):
                # Restrict the edit to ZombieLore: XP multipliers also contain Strength.
                start = text.index("ZombieLore =")
                prefix, lore = text[:start], text[start:]
                lore, count = re.subn(
                    r"(?m)^(\s*" + key + r"\s*=\s*)[^,\n]+,",
                    lambda match: match[1] + str(value) + ",",
                    lore,
                    count=1,
                )
                if count != 1:
                    raise RuntimeError("Expected disposable ZombieLore " + key)
                text = prefix + lore
            scenario.write_private(sandbox, text)
            scenario.write_private(props, props.read_text() + "encounter.lifecycle=true\n")
            receipt["watched_combat"].update(
                actors=1,
                targets=1,
                weapon=None,
                fixture_health=100,
                movement_fixture="stationary_defense",
                zombie_strength="superhuman",
                zombie_hearing="pinpoint",
                zombie_sight="eagle",
                fixture_doors=0,
                infection_mortality="never",
                reanimation_min_hours=48,
            )
        for path in (target / "receipt.json", m.ROOT / "artifacts/scenario-tests/current.json"):
            scenario.write_private(path, json.dumps(receipt, indent=2) + "\n")
        print(
            "Prepared one-Actor fatal lifecycle runtime; no cases start until run."
            if action == "lifecycle-create"
            else "Prepared reusable encounter runtime; no cases start until run."
        )
        return
    receipt, target = context(m)
    if action == "run":
        if len(args) > 3:
            raise ValueError(
                "run [regression|batch|routine|gait|compare|open|injury|defense|lifecycle] [1|2|4]"
            )
        return run(m, args[1] if len(args) > 1 else "batch", int(args[2]) if len(args) > 2 else 1)
    current = target / "current-encounter-batch.json"
    if action == "record-feedback":
        if len(args) != 3 or args[1] not in ("passed", "failed", "partial"):
            raise ValueError('record-feedback passed|failed|partial "verbatim feedback"')
        report = json.loads(current.read_text())
        path = Path(report["path"]) / f"human-feedback-{time.time_ns()}.json"
        atomic_json(
            path,
            {
                "world": report["world"],
                "epoch": report["epoch"],
                "batch": report["batch"],
                "machine_status": report["status"],
                "verdict": args[1],
                "feedback": args[2],
                "recorded_at": time.time(),
                "scope": report["scope"],
            },
        )
        print(f"Recorded separate human feedback: {path}")
        return
    if len(args) != 1 or action not in ("status", "cancel", "join"):
        raise ValueError("Expected run, status, cancel, join or record-feedback")
    if action == "join":
        import pedestrian_ops

        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            try:
                _, _, hello = handshake(m, target)
                if hello.phase == "READY":
                    return pedestrian_ops.join(m)
            except (OSError, RuntimeError):
                pass
            time.sleep(2)
        raise RuntimeError("Encounter runtime did not become ready")
    if not current.exists():
        _, _, hello = handshake(m, target)
        print(
            json.dumps(
                {
                    "status": "ready_without_submission" if hello.phase == "READY" else "booting",
                    "runtime": as_dict(hello),
                }
            )
        )
        return
    report = json.loads(current.read_text())
    if report.get("cases"):
        pb, socket, hello = handshake(m, target)
        if hello.epoch != report["epoch"] or hello.world != report["world"]:
            if action == "cancel":
                raise RuntimeError("Cannot cancel an event from the previous runtime")
            print(
                json.dumps(
                    {
                        "status": "ready_new_epoch",
                        "runtime": as_dict(hello),
                        "previous_batch": report["batch"],
                        "previous_status": report["status"],
                        "previous_epoch": report["epoch"],
                    }
                )
            )
            return
        event = report["cases"][-1]["event"]
        if action == "cancel":
            request(pb, socket, hello, "CANCEL", event)
        state = request(pb, socket, hello, "STATUS", event)
        print(json.dumps({"batch_status": report["status"], "current": as_dict(state)}))
        # Separate reconciliation receipt; never rewrite the original machine result.
        if (
            state.phase in TERMINAL
            and state.encounter.cleanup_verified
            and report["status"] in {"interrupted", "cleanup_blocked", "unreconciled", "critical"}
        ):
            atomic_json(Path(report["path"]) / "reconciliation.json", as_dict(state))
            report["status"] = "reconciled_failed"
            atomic_json(current, report)
    else:
        print(json.dumps(report))
