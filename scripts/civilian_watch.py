"""Four watched 150-tile Actor reuse waves, in the disposable pedestrian deployment."""

from __future__ import annotations
import json
import hashlib
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import pedestrian_ops as pedestrian
import scenario_ops as scenario


def copy_test_database(source, dest, old_world, new_world):
    dest.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(f"file:{source}?mode=ro", uri=True) as src, sqlite3.connect(dest) as dst:
        src.backup(dst)
        if dest.parent.name == "db":
            dst.execute("UPDATE whitelist SET world=?", (new_world,))
        else:
            dst.execute("UPDATE networkPlayers SET world=? WHERE world=?", (new_world, old_world))
    dest.chmod(0o600)


def create(m, actors=4, hunters=0):
    if hunters not in {0, 16, 128}:
        raise ValueError("Watched hunters must be 0, 16 or 128")
    if hunters == 128 and actors != 64:
        raise ValueError("128 hunters require the 64-Actor stress case")
    if hunters and actors < 32:
        raise ValueError("Hunters require at least 32 civilian slots")
    if actors not in {4, 32, 64}:
        raise ValueError("Watched actor count must be 4, 32 or 64")
    previous, old = scenario.test_receipt(m)
    pedestrian.create(m)  # Requires the previous server stopped; its files remain intact.
    receipt, target = scenario.test_receipt(m)
    receipt["watched_pool"] = {
        "waves": 4,
        "actors": actors,
        "columns": 8 if actors >= 32 else 4,
        "rows": actors // 8 if actors >= 32 else 1,
        "tiles_each": 150,
        "speed": 2.6,
        "hunters": hunters,
        "observer": [10596.5, 10035.5, 0]
        if hunters == 128
        else [10616.5 if actors == 64 else 10596.5, 10060.5, 0],
        "previous_world": previous["world"],
    }
    ini = target / "Zomboid/Server" / (receipt["world"] + ".ini")
    scenario.write_private(
        ini,
        scenario.replace_ini(
            ini.read_text(), {"PauseEmpty": "false", "SpawnPoint": "10596,10060,0"}
        ),
    )
    properties = target / "scenario.properties"
    scenario.write_private(
        properties,
        properties.read_text()
        + f"watched.enabled=true\nwatched.directory=/run/akr\nwatched.actors={actors}\nwatched.hunters={hunters}\n",
    )
    # Keep the saved test account and character so the ordinary client can join unattended.
    for source, dest in [
        (old / f"Zomboid/db/{previous['world']}.db", target / f"Zomboid/db/{receipt['world']}.db"),
        (
            old / f"Zomboid/Saves/Multiplayer/{previous['world']}/players.db",
            target / f"Zomboid/Saves/Multiplayer/{receipt['world']}/players.db",
        ),
    ]:
        if source.exists():
            copy_test_database(source, dest, previous["world"], receipt["world"])
    for path in (target / "receipt.json", m.ROOT / "artifacts/scenario-tests/current.json"):
        scenario.write_private(path, json.dumps(receipt, indent=2) + "\n")
    print(
        f"Prepared four waves of {actors}; retained prior test world and copied its account/character."
    )


def install_client(m, target):
    if subprocess.run(["pgrep", "-f", "^./ProjectZomboid64"], capture_output=True).stdout:
        raise RuntimeError("Close the current game before installing watched diagnostics")
    installed = Path.home() / "Zomboid/mods"
    hashes = {}
    installation = str(time.time_ns())
    for name in pedestrian.mods_for(json.loads((target / "receipt.json").read_text())):
        dest = installed / name
        backup = target / "client-mod-backup" / installation / name
        if dest.exists():
            shutil.copytree(dest, backup)
        shutil.copytree(m.ROOT / "mods" / name, dest, dirs_exist_ok=True)
        for file in (m.ROOT / "mods" / name).rglob("*"):
            if file.is_file():
                relative = file.relative_to(m.ROOT / "mods")
                hashes[str(relative)] = hashlib.sha256(
                    (installed / relative).read_bytes()
                ).hexdigest()
    scenario.write_private(target / "client-mod-install.json", json.dumps(hashes, indent=2) + "\n")


def dispatch(m, args):
    action = args[0] if args else "status"
    if action not in {"create", "start", "join", "status", "stop"} or len(args) > (
        3 if action == "create" else 1
    ):
        raise RuntimeError(
            "Usage: ./dayone civilian-watch [create [4|32|64] [0|16|128]|start|join|status|stop]"
        )
    if action == "create":
        create(m, int(args[1]) if len(args) > 1 else 4, int(args[2]) if len(args) > 2 else 0)
        return
    receipt, target = scenario.test_receipt(m)
    if "watched_pool" not in receipt:
        raise RuntimeError("Current disposable world is not the watched pooling test")
    if action == "start":
        install_client(m, target)
        pedestrian.start(m)
    elif action == "join":
        # A launched container is not a listening/loaded game server yet. Avoid an
        # ordinary client's connection timeout during first-world map loading.
        deadline = time.monotonic() + 300
        report = target / "ipc/watched-report.json"
        while True:
            state = json.loads(report.read_text()) if report.exists() else {}
            if state.get("status") in {"failed", "passed"}:
                raise RuntimeError(
                    "Watched batch already finished; prepare a fresh batch before joining"
                )
            if state.get("phase", 0) >= 1:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Watched server did not finish warmup within five minutes")
            time.sleep(2)
        pedestrian.join(m)
    elif action == "stop":
        scenario.stop_test(m)
    else:
        report = target / "ipc/watched-report.json"
        print(
            report.read_text()
            if report.exists()
            else json.dumps({"status": "waiting_for_boot", "world": receipt["world"]})
        )
