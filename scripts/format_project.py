"""Format authored project sources with pinned or project-local tools."""

import argparse
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORMAT = ROOT / ".tooling/format"
JAVA = ROOT / ".tooling/agent/jdk-25.0.4.1+1/bin/java"
GJF = FORMAT / "google-java-format-1.36.1-all-deps.jar"
GJF_SHA256 = "25b400f003089d23cc5320cdaf1a16cabee19b8aa3434d0ff021b3d9f42154b4"
TAPLO = FORMAT / "taplo"
STYLUA = ROOT / "observer/web/node_modules/.bin/stylua"
PRETTIER = ROOT / "observer/web/node_modules/.bin/prettier"
RUFF = ROOT / ".tooling/venv/bin/ruff"
GERSEMI = ROOT / ".tooling/venv/bin/gersemi"
CLANG_FORMAT = ROOT / ".tooling/venv/bin/clang-format"
SHFMT = FORMAT / "shfmt"


def sources() -> list[Path]:
    """Return authored files, including newly created files, but never generated outputs."""
    names = (
        subprocess.check_output(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
        )
        .decode()
        .split("\0")
    )
    excluded = ("data/", "artifacts/", ".tooling/", "references/", "vault/")
    return sorted(
        {
            ROOT / name
            for name in names
            if name
            and not name.startswith(excluded)
            and (ROOT / name).is_file()
            and not name.endswith("package-lock.json")
        }
    )


def run(command: list[str | Path]) -> None:
    subprocess.run([str(part) for part in command], check=True, cwd=ROOT)


def format_project(check: bool = False) -> None:
    """Apply or verify language-specific formatting without changing generated code."""
    if hashlib.file_digest(GJF.open("rb"), "sha256").hexdigest() != GJF_SHA256:
        raise RuntimeError("Pinned google-java-format checksum mismatch")
    files = sources()
    java = [p for p in files if p.suffix == ".java"]
    python = [p for p in files if p.suffix == ".py" and p.name != "positions_pb2.py"]
    game_lua = [p for p in files if p.suffix == ".lua" and "mods" in p.parts]
    worker_lua = [p for p in files if p.suffix == ".lua" and "npc-service" in p.parts]
    native = [p for p in files if p.suffix in {".cpp", ".hpp", ".h", ".proto"}]
    web = [
        p
        for p in files
        if p.suffix in {".ts", ".tsx", ".js", ".jsx", ".css", ".html", ".json", ".yaml", ".yml"}
    ]
    shell = [p for p in files if p.suffix == ".sh" or p.name == "dayone"]
    toml = [p for p in files if p.suffix == ".toml"]
    cmake = [p for p in files if p.suffix == ".cmake" or p.name == "CMakeLists.txt"]
    if java:
        run(
            [
                JAVA,
                "-jar",
                GJF,
                *(("--dry-run", "--set-exit-if-changed") if check else ("--replace",)),
                *java,
            ]
        )
    if python:
        run(
            [
                RUFF,
                "format",
                "--config",
                ROOT / ".ruff.toml",
                *(("--check",) if check else ()),
                *python,
            ]
        )
    for paths, syntax in ((game_lua, "Lua51"), (worker_lua, "Lua54")):
        if paths:
            run([STYLUA, "--verify", "--syntax", syntax, *(("--check",) if check else ()), *paths])
    if native:
        run(
            [
                CLANG_FORMAT,
                *(("--dry-run", "--Werror") if check else ("-i",)),
                "--style=file",
                *native,
            ]
        )
    if web:
        run([PRETTIER, "--check" if check else "--write", *web])
    if shell:
        run([SHFMT, "-d" if check else "-w", "-i", "4", *shell])
    if toml:
        run([TAPLO, "fmt", *(("--check",) if check else ()), *toml])
    if cmake:
        run([GERSEMI, "-c" if check else "-i", "-l", "100", *cmake])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    format_project(parser.parse_args().check)
