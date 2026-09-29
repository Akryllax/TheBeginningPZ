"""Disposable pedestrian feasibility deployment; no normal-world configuration changes."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import scenario_ops as scenario

MODS = ("AKRCore", "AKRDevTools")


def mods_for(receipt):
    residents = receipt.get("offscreen_chase") or receipt.get("watched_combat", {}).get(
        "scope"
    ) in {"autonomous_defend_escape", "moving_encounter"}
    return MODS + (("AKRPopulation", "AKRResidents") if residents else ())


def create(m):
    scenario.create_test(m)
    receipt, target = scenario.test_receipt(m)
    receipt["experiment"] = "pedestrian"
    receipt["client_java_required"] = False
    server = target / "Zomboid/Server"
    ini = server / (receipt["world"] + ".ini")
    scenario.write_private(
        ini,
        scenario.replace_ini(
            ini.read_text(),
            {
                "Mods": "Bandits2;AKRCore;AKRDevTools",
                "WorkshopItems": "3268487204",
                "PublicName": "AKR pedestrian feasibility",
                "PauseEmpty": "true",
                "SpawnPoint": "10756,9850,0",
            },
        ),
    )
    for mod in MODS:
        shutil.copytree(m.ROOT / "mods" / mod, target / "Zomboid/mods" / mod)
    (target / "agent").mkdir()
    scenario.write_private(
        target / "scenario.properties",
        f"side=server\nscenario.enabled=true\nworld={receipt['world']}\n"
        "socket=/run/akr/no-planner.sock\nruntime.enabled=true\nruntime.socket=/run/akr/runtime.sock\n"
        f"bandits_update_file={scenario.BANDITS_SOURCE}\n",
    )
    for path in (target / "receipt.json", m.ROOT / "artifacts/scenario-tests/current.json"):
        scenario.write_private(path, json.dumps(receipt, indent=2) + "\n")
    definition = {
        "actors": 1,
        "route": [{"x": 10756, "y": 9856, "z": 0}, {"x": 10776, "y": 9856, "z": 0}],
        "timeout_seconds": 30,
        "hold_seconds": 5,
        "seed": 1,
    }
    (target / "one-pedestrian.json").write_text(json.dumps(definition, indent=2) + "\n")
    # Actor pool gate 1 (Decision 0006): one connectionless server player, reassigned once.
    actor = dict(
        definition, timeout_seconds=90, hold_seconds=40, entity="ACTOR", reassign_after_seconds=20
    )
    (target / "one-actor.json").write_text(json.dumps(actor, indent=2) + "\n")
    print("Pedestrian experiment prepared; no actors are submitted automatically.")


def start(m):
    receipt, target = scenario.test_receipt(m)
    if receipt.get("experiment") != "pedestrian":
        raise RuntimeError("Current disposable world is not the pedestrian experiment")
    scenario.free_ports()
    # Reports from a stopped process must not satisfy the next client's warmup gate.
    import time

    for report in (target / "ipc").glob("*-report.json"):
        report.rename(report.with_name(report.stem + f".previous-{time.time_ns()}.json"))
    jar = m.ROOT / "artifacts/scenario-agent/akr-scenario-agent.jar"
    manifest = json.loads(jar.with_name("manifest.json").read_text())
    if hashlib.sha256(jar.read_bytes()).hexdigest() != manifest["jar_sha256"]:
        raise RuntimeError("Agent artifact differs from manifest")
    for mod in mods_for(receipt):
        destination = target / "Zomboid/mods" / mod
        shutil.rmtree(destination)
        shutil.copytree(m.ROOT / "mods" / mod, destination)
    shutil.copy2(jar, target / "agent" / jar.name)
    shutil.copy2(jar.with_name("manifest.json"), target / "agent/manifest.json")
    m.run([m.PODMAN, "rm", receipt["container"]], capture=True, check=False)
    m.run(
        [
            m.PODMAN,
            "run",
            "-d",
            "--name",
            receipt["container"],
            "--userns=keep-id",
            "--memory=5g",
            "--cpus=4",
            "--ulimit",
            "core=0:0",
            "-p",
            "192.168.1.132:16281:16281/udp",
            "-p",
            "192.168.1.132:16282:16282/udp",
            "-p",
            "127.0.0.1:27035:27035/tcp",
            "-e",
            f"PZ_WORLD={receipt['world']}",
            "-e",
            "JAVA_TOOL_OPTIONS=-javaagent:/opt/scenario/akr-scenario-agent.jar=/opt/scenario/scenario.properties",
            "-v",
            f"{m.ROOT}/data/game-files:/pzserver:ro,z",
            "-v",
            f"{target}/ProjectZomboid64.json:/pzserver/ProjectZomboid64.json:ro,z",
            "-v",
            f"{target}/Zomboid:/home/pzuser/Zomboid:z",
            "-v",
            f"{target}/agent/akr-scenario-agent.jar:/opt/scenario/akr-scenario-agent.jar:ro,z",
            "-v",
            f"{target}/scenario.properties:/opt/scenario/scenario.properties:ro,z",
            "-v",
            f"{target}/ipc:/run/akr:z",
            "-v",
            f"{m.ROOT}/artifacts/npc-service-runtime:/opt/npc-runtime:ro,z",
            "-v",
            f"{m.ROOT}/secrets/admin-password:/run/secrets/admin-password:ro,z",
            "-v",
            f"{m.ROOT}/game/entrypoint.sh:/home/pzuser/entrypoint.sh:ro,z",
            "localhost/zomboid-dayone_game:latest",
        ]
    )
    print(
        "Pedestrian test server starting at 192.168.1.132:16281; enabled adapters are recorded in its receipt."
    )


def _user_log_lines(target):
    lines = set()
    for log in (target / "Zomboid/Logs").glob("*_user.txt"):
        lines.update((log.name, line) for line in log.read_text(errors="replace").splitlines())
    return lines


def _wait_user_log(target, seen, marker, deadline):
    import time

    while time.time() < deadline:
        for name, line in _user_log_lines(target) - seen:
            if marker in line:
                return line
        time.sleep(2)
    return None


def join(m):
    """Relaunch the local Steam client into the disposable world with no manual steps.

    Requires the developer-only AKRDevConnect mod in the local client (it connects with the
    account already saved in the stock server list) and a saved account for this server.
    The stock "Click to Start" screen only accepts real input, so one synthetic click is sent
    to the game window with xdotool (XWayland).
    """
    import os
    import signal
    import subprocess
    import time

    receipt, target = scenario.test_receipt(m)
    if receipt.get("experiment") != "pedestrian":
        raise RuntimeError("Current disposable world is not the pedestrian experiment")
    if not (Path.home() / "Zomboid/mods/AKRDevConnect").is_dir():
        raise RuntimeError(
            "Install mods/AKRDevConnect into ~/Zomboid/mods and enable it at the main menu"
        )
    ini = target / "Zomboid/Server" / (receipt["world"] + ".ini")
    password = next(
        (
            line.split("=", 1)[1]
            for line in ini.read_text().splitlines()
            if line.startswith("Password=")
        ),
        "",
    )
    running = subprocess.run(
        ["pgrep", "-f", "^./ProjectZomboid64"], capture_output=True, text=True
    ).stdout.split()
    for pid in running:
        os.kill(int(pid), signal.SIGTERM)
    for _ in range(30):
        if not subprocess.run(["pgrep", "-f", "^./ProjectZomboid64"], capture_output=True).stdout:
            break
        time.sleep(1)
    else:
        raise RuntimeError("Client did not exit after SIGTERM")
    seen = _user_log_lines(target)
    # Exact archived client; Steam Play can silently upgrade before joining.
    args = [str(m.ROOT / "scripts/launch-pinned-client"), "+connect", "192.168.1.132:16281"]
    if password:
        args += ["+password", password]
    subprocess.Popen(
        args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True
    )
    print("Client launch requested; waiting for the server to report loading complete")
    if not _wait_user_log(target, seen, "loading time was", time.time() + 600):
        raise RuntimeError("Client did not finish loading within 10 minutes")
    env = dict(os.environ, DISPLAY=os.environ.get("DISPLAY", ":0"))
    deadline = time.time() + 90
    while time.time() < deadline:
        window = subprocess.run(
            ["xdotool", "search", "--class", "Project Zomboid"],
            capture_output=True,
            text=True,
            env=env,
        ).stdout.split()
        if window:
            subprocess.run(
                [
                    "xdotool",
                    "mousemove",
                    "--window",
                    window[0],
                    "960",
                    "540",
                    "mousedown",
                    "1",
                    "sleep",
                    "0.15",
                    "mouseup",
                    "1",
                ],
                env=env,
                check=False,
            )
        line = _wait_user_log(target, seen, "fully connected", time.time() + 6)
        if line:
            print(line)
            return
    raise RuntimeError("Client loaded but did not pass Click to Start")


def dispatch(m, command, args):
    if args:
        raise ValueError("Pedestrian deployment commands take no arguments")
    if command == "pedestrian-test-create":
        create(m)
    elif command == "pedestrian-test-start":
        start(m)
    elif command == "pedestrian-test-join":
        join(m)
    elif command == "pedestrian-test-stop":
        receipt, _ = scenario.test_receipt(m)
        if receipt.get("experiment") != "pedestrian":
            raise RuntimeError("Not a pedestrian test world")
        scenario.stop_test(m)
    else:
        raise ValueError("Unknown pedestrian operation")
