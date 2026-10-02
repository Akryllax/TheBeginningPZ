"""Qualify real premain ordering before loading a world, without a telemetry service."""

from pathlib import Path
import subprocess
import tempfile

from compat import ROOT, read_profile


def main():
    profile = read_profile(ROOT, "pz42.21")
    game = (ROOT / profile["game_jar"]).resolve()
    observer = ROOT / "observer/artifacts/position-agent/observer-position-agent.jar"
    scenario = ROOT / "artifacts/scenario-agent/akr-scenario-agent.jar"
    fixtures = ROOT / "artifacts/scenario-agent/test-classes"
    java = ROOT / ".tooling/agent/jdk-25.0.4.1+1/bin/java"
    output = ROOT / "artifacts/compat/dual-agent"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output, prefix="fixture-") as tmp:
        tmp = Path(tmp)
        token = tmp / "token"
        token.write_text("b" * 64)
        export = tmp / "observer.properties"
        export.write_text(
            f"world=AKR_DayOne_Test_DualAgent\nendpoint=http://127.0.0.1:1/internal/v1/positions\ntoken_file={token}\n"
        )
        agent = tmp / "scenario.properties"
        bandits = ROOT / profile["dependencies"]["BanditUpdate.lua"]
        agent.write_text(
            f"side=server\nscenario.enabled=true\nworld=AKR_DayOne_Test_DualAgent\nsocket=/run/akr-unused-compat.sock\nbandits_update_file={bandits}\n"
        )
        observer_arg, scenario_arg = (
            f"-javaagent:{observer}={export}",
            f"-javaagent:{scenario}={agent}",
        )
        for name, agents in [
            ("observer-first", [observer_arg, scenario_arg]),
            ("unsupported-reverse-order", [scenario_arg, observer_arg]),
        ]:
            result = subprocess.run(
                [
                    str(java),
                    "-XX:-CreateCoredumpOnCrash",
                    *agents,
                    "-cp",
                    f"{fixtures}:{scenario}:{observer}:{game}",
                    "net.akr.scenario.PremainFixture",
                ],
                text=True,
                capture_output=True,
                timeout=30,
            )
            text = result.stdout + result.stderr
            (output / f"{name}.log").write_text(text)
            if (
                result.returncode
                or "Scenario premain installed all verified gameplay hooks" not in text
            ):
                raise RuntimeError(f"Gameplay premain failed: {name}")
            if name == "observer-first" and "Installed position hook" not in text:
                raise RuntimeError("Observer hook did not coexist with gameplay hooks")
            # In reverse order RCON has already loaded before Observer's transformer exists.
            if name != "observer-first" and "Installed position hook" in text:
                raise RuntimeError("Unexpected retransformation in unsupported order")
            print(name, "passed")


if __name__ == "__main__":
    main()
