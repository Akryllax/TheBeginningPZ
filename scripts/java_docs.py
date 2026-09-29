"""Generate and validate Javadoc against the pinned, compiled scenario agent."""

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JDK = ROOT / ".tooling/agent/jdk-25.0.4.1+1/bin"
GAME = ROOT / "data/game-files/java/projectzomboid.jar"
CLASSES = ROOT / "artifacts/scenario-agent/classes"
PROTOBUF = ROOT / ".tooling/agent/protobuf-javalite-4.36.1.jar"


def main() -> None:
    """Build class references, then render source documentation with Javadoc checks."""
    subprocess.run(["python3", str(ROOT / "scripts/build_scenario_agent.py")], check=True, cwd=ROOT)
    game_jars = [GAME, *sorted(path for path in GAME.parent.glob("*.jar") if path != GAME)]
    classpath = ":".join(str(path) for path in [CLASSES, PROTOBUF, *game_jars])
    sources = sorted((ROOT / "scenario-agent/src").rglob("*.java"))
    subprocess.run(
        [
            str(JDK / "javadoc"),
            "-quiet",
            "-Xdoclint:all,-missing",
            "-Werror",
            "-encoding",
            "UTF-8",
            "--release",
            "25",
            "-classpath",
            classpath,
            "-d",
            str(ROOT / "artifacts/javadoc/scenario-agent"),
            *map(str, sources),
        ],
        check=True,
        cwd=ROOT,
    )


if __name__ == "__main__":
    main()
