"""Disposable ordinary-client native melee probe, with placement and server announcements."""

import json
import time
import shutil
import civilian_watch as watched
import pedestrian_ops as pedestrian
import scenario_ops as scenario


def dispatch(m, args):
    import spectator_options

    args, viewers = spectator_options.parse(args)
    action = args[0] if args else "status"
    if action in {"lifecycle-create", "encounter-create", "run", "cancel", "record-feedback"}:
        import civilian_encounter

        return civilian_encounter.dispatch(m, args)
    if action in {"join", "status"}:
        current, _ = scenario.test_receipt(m)
        if current.get("watched_combat", {}).get("scope") == "moving_encounter":
            import civilian_encounter

            return civilian_encounter.dispatch(m, args or ["status"])
    if len(args) > 1 or action not in {
        "create",
        "survival-create",
        "start",
        "join",
        "status",
        "stop",
    }:
        raise RuntimeError(
            "Usage: ./dayone civilian-combat [create|survival-create|start|join|status|stop]"
        )
    if action in {"create", "survival-create"}:
        # Reuse consistent account/save copying; disable the marching harness before launch.
        watched.create(m, 4, 0)
        receipt, target = scenario.test_receipt(m)
        receipt.pop("watched_pool")
        receipt["watched_combat"] = {
            "actors": 1,
            "targets": 1,
            "weapon": "Base.Hammer",
            "observer": [10596.5, 10060.5, 0],
            "scope": "native_contact_probe",
        }
        props = target / "scenario.properties"
        text = "\n".join(
            line for line in props.read_text().splitlines() if not line.startswith("watched.")
        )
        survival = action == "survival-create"
        if survival:
            receipt["watched_combat"]["scope"] = "autonomous_defend_escape"
            ini = target / "Zomboid/Server" / (receipt["world"] + ".ini")
            scenario.write_private(
                ini,
                scenario.replace_ini(
                    ini.read_text(),
                    {"Mods": "Bandits2;AKRCore;AKRDevTools;AKRPopulation;AKRResidents"},
                ),
            )
            for mod in ("AKRPopulation", "AKRResidents"):
                shutil.copytree(m.ROOT / "mods" / mod, target / "Zomboid/mods" / mod)
        scenario.write_private(
            props,
            text
            + "\ncombat.enabled=true\ncombat.directory=/run/akr\n"
            + f"combat.survival={str(survival).lower()}\n",
        )
        for path in (target / "receipt.json", m.ROOT / "artifacts/scenario-tests/current.json"):
            scenario.write_private(path, json.dumps(receipt, indent=2) + "\n")
        print(
            "Prepared autonomous defense/escape test."
            if survival
            else "Prepared watched hammer contact probe; marching test disabled."
        )
        return
    receipt, target = scenario.test_receipt(m)
    if "watched_combat" not in receipt:
        raise RuntimeError("Current disposable world is not a watched combat test")
    report = target / "ipc/combat-report.json"
    if action == "start":
        if viewers > 1 and receipt.get("watched_combat", {}).get("scope") != "moving_encounter":
            raise ValueError("Multiple viewing spots require encounter-create or lifecycle-create")
        active = m.run(
            [m.PODMAN, "ps", "--filter", f"name=^{receipt['container']}$", "--format", "{{.ID}}"],
            capture=True,
        )
        if active.stdout.strip():
            raise RuntimeError(
                "Viewing spots are a startup setting; disposable server is already running"
            )
        props = target / "scenario.properties"
        text = "\n".join(
            line
            for line in props.read_text().splitlines()
            if not line.startswith("encounter.viewers=")
        )
        scenario.write_private(props, text + f"\nencounter.viewers={viewers}\n")
        receipt["watched_combat"]["viewing_spots"] = viewers
        for path in (target / "receipt.json", m.ROOT / "artifacts/scenario-tests/current.json"):
            scenario.write_private(path, json.dumps(receipt, indent=2) + "\n")
        watched.install_client(m, target)
        pedestrian.start(m)
        if receipt.get("watched_combat", {}).get("scope") == "moving_encounter":
            import resident_planner

            resident_planner.start(m.ROOT, target, receipt["world"])
    elif action == "join":
        deadline = time.monotonic() + 300
        while True:
            state = json.loads(report.read_text()) if report.exists() else {}
            if state.get("status") == "failed" or state.get("phase", 0) > 1:
                raise RuntimeError("Combat test already ran or failed; inspect its report")
            if state.get("phase") == 1:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Combat server warmup timed out")
            time.sleep(2)
        pedestrian.join(m)
    elif action == "stop":
        scenario.stop_test(m)
        import resident_planner

        resident_planner.stop(target)
    else:
        print(
            report.read_text()
            if report.exists()
            else json.dumps({"status": "waiting_for_boot", "world": receipt["world"]})
        )
