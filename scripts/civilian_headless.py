"""Run native civilian cases in a separate zero-client dedicated server; preserve all artifacts."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import socket
import time
import uuid

import scenario_ops as scenario

CONTAINER = "akr-civilian-headless"
PORTS = (16291, 16292, 27045)


def receipt(m):
    data = json.loads((m.ROOT / "artifacts/civilian-headless/current.json").read_text())
    target = Path(data["path"]).resolve()
    if (
        not target.is_relative_to((m.ROOT / "artifacts/civilian-headless").resolve())
        or data["container"] != CONTAINER
    ):
        raise RuntimeError("Invalid headless receipt")
    return data, target


def running(m):
    return m.run(
        [m.PODMAN, "ps", "--filter", f"name=^{CONTAINER}$", "--format", "{{.ID}}"], capture=True
    ).stdout.strip()


def prepare(m, combat_probe=False, survival=False, planner=False):
    if running(m):
        raise RuntimeError("Headless test is already running; use civilian-headless status/stop")
    with socket.socket() as test:
        test.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        test.bind(("127.0.0.1", PORTS[2]))
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime()) + "-" + uuid.uuid4().hex[:6]
    target = m.ROOT / "artifacts/civilian-headless" / stamp
    target.mkdir(parents=True, mode=0o700)
    world = "AKR_DayOne_Test_Headless_" + stamp.replace("-", "_")
    source = m.ROOT / "data/Zomboid/Server"
    server = target / "Zomboid/Server"
    changes = {
        "DefaultPort": PORTS[0],
        "UDPPort": PORTS[1],
        "RCONPort": PORTS[2],
        "Public": "false",
        "PublicName": "AKR headless native validation",
        "PauseEmpty": "false",
        "Mods": "AKRCore;AKRPopulation;AKRResidents",
        "WorkshopItems": "",
        "SpawnPoint": "10756,9856,0",
        "BackupsOnStart": "false",
        "BackupsOnVersionChange": "false",
        "BackupsPeriod": "0",
        "Open": "false",
        "AutoCreateUserInWhiteList": "false",
    }
    scenario.write_private(
        server / f"{world}.ini",
        scenario.replace_ini((source / f"{m.WORLD}.ini").read_text(), changes),
    )
    scenario.write_private(
        server / f"{world}_SandboxVars.lua",
        scenario.scenario_sandbox((source / f"{m.WORLD}_SandboxVars.lua").read_text()),
    )
    (server / f"{world}_spawnregions.lua").write_text("function SpawnRegions() return {} end\n")
    for name in ("AKRCore", "AKRPopulation", "AKRResidents"):
        shutil.copytree(m.ROOT / "mods" / name, target / "Zomboid/mods" / name)
    config = json.loads((m.ROOT / "data/game-files/ProjectZomboid64.json").read_text())
    config["vmArgs"] = [a for a in config["vmArgs"] if not a.startswith(("-Xmx", "-Xms"))] + [
        "-Xms512m",
        "-Xmx3g",
    ]
    (target / "ProjectZomboid64.json").write_text(json.dumps(config, indent=2) + "\n")
    (target / "ipc").mkdir(mode=0o700)
    (target / "agent").mkdir()
    jar = m.ROOT / "artifacts/scenario-agent/akr-scenario-agent.jar"
    manifest = json.loads(jar.with_name("manifest.json").read_text())
    if hashlib.sha256(jar.read_bytes()).hexdigest() != manifest["jar_sha256"]:
        raise RuntimeError("Agent artifact/manifest mismatch")
    shutil.copy2(jar, target / "agent" / jar.name)
    shutil.copy2(jar.with_name("manifest.json"), target / "agent/manifest.json")
    scenario.write_private(
        target / "scenario.properties",
        f"side=server\nscenario.enabled=true\nworld={world}\nsocket=/run/akr/npc.sock\n"
        "runtime.enabled=true\nruntime.socket=/run/akr/runtime.sock\n"
        "headless.enabled=true\nheadless.report=/run/akr/native-report.json\n"
        f"headless.combat_probe={str(combat_probe).lower()}\n"
        f"headless.survival={str(survival).lower()}\n"
        f"headless.planner={str(planner).lower()}\n"
        f"bandits_update_file={scenario.BANDITS_SOURCE}\n",
    )
    data = {
        "world": world,
        "path": str(target),
        "container": CONTAINER,
        "ports": PORTS,
        "jar_sha256": manifest["jar_sha256"],
        "clients": 0,
        "created_at": stamp,
    }
    scenario.write_private(target / "receipt.json", json.dumps(data, indent=2) + "\n")
    scenario.write_private(target.parent / "current.json", json.dumps(data, indent=2) + "\n")
    return data, target


def stop(m):
    _, target = receipt(m)
    if running(m):
        spec = importlib.util.spec_from_file_location("headless_rcon", m.ROOT / "game/rcon.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.rcon_command(
            "127.0.0.1", PORTS[2], m.credentials()["rcon_password"], "quit", timeout=15
        )
        deadline = time.monotonic() + 180
        while running(m):
            if time.monotonic() >= deadline:
                raise RuntimeError(
                    "Headless world did not stop gracefully; retained for inspection"
                )
            time.sleep(2)
    import resident_planner

    resident_planner.stop(target)
    logs = m.run([m.PODMAN, "logs", CONTAINER], capture=True, check=False)
    scenario.write_private(target / "container.log", m.redact(logs.stdout + logs.stderr))


def run(m, combat_probe=False, survival=False, planner=False):
    m.run([m.PYTHON, m.ROOT / "scripts/build_scenario_agent.py", "--test", "--test-layer", "unit"])
    data, target = prepare(m, combat_probe, survival, planner)
    m.run([m.PODMAN, "rm", CONTAINER], capture=True, check=False)
    m.run(
        [
            m.PODMAN,
            "run",
            "-d",
            "--name",
            CONTAINER,
            "--userns=keep-id",
            "--memory=5g",
            "--cpus=4",
            "--ulimit",
            "core=0:0",
            "-p",
            f"127.0.0.1:{PORTS[2]}:{PORTS[2]}/tcp",
            "-e",
            f"PZ_WORLD={data['world']}",
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
    if planner:
        import resident_planner

        resident_planner.start(m.ROOT, target, data["world"])
    print(f"Headless native world: {data['world']}; game UDP ports unpublished", flush=True)
    report = target / "ipc/native-report.json"
    deadline = time.monotonic() + (900 if survival else 600)
    while not report.exists():
        if not running(m):
            stop(m)
            raise RuntimeError(f"Headless server exited before report; see {target}/container.log")
        if time.monotonic() >= deadline:
            stop(m)
            raise RuntimeError(f"Headless test timed out; see {target}")
        time.sleep(2)
    result = json.loads(report.read_text())
    stop(m)
    print(json.dumps(result, indent=2), flush=True)
    print(f"Evidence: {report}", flush=True)
    if result["status"] != "passed":
        raise RuntimeError(f"Native test failed: {result['failure']}")


def dispatch(m, arguments):
    action = arguments[0] if arguments else "run"
    if len(arguments) > 1 or action not in {
        "run",
        "combat-probe",
        "survival",
        "planner",
        "status",
        "stop",
    }:
        raise RuntimeError(
            "Usage: ./dayone civilian-headless [run|combat-probe|survival|planner|status|stop]"
        )
    if action in {"run", "combat-probe", "survival", "planner"}:
        run(m, action in {"combat-probe", "survival"}, action == "survival", action == "planner")
    elif action == "stop":
        stop(m)
    else:
        data, target = receipt(m)
        report = target / "ipc/native-report.json"
        print(
            json.dumps(
                {
                    "world": data["world"],
                    "running": bool(running(m)),
                    "report": json.loads(report.read_text()) if report.exists() else "pending",
                },
                indent=2,
            )
        )
