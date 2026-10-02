#!/usr/bin/env python3
"""Build a private, versioned server/client handoff without copying live state."""

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODS = ("AKRCore", "AKRPopulation", "AKRResidents", "AKRStoryteller", "AKRDevTools")
SOURCE_DIRS = (
    "compatibility",
    "scripts",
    "scenario-agent",
    "npc-service",
    "protocol",
    "observer",
    "game",
    "gateway",
    "tests",
    "packaging",
)
SOURCE_FILES = (
    "dayone",
    "compose.yaml",
    "LICENSE",
    "requirements-dev.in",
    "requirements-dev.lock",
    ".ruff.toml",
    "CODE_STYLE.md",
)
REFERENCES = (
    "pinned-client.json",
    "tool-downloads.json",
    "java-decompiler.json",
    "workshop-files.json",
)
MARKER = ".akr-dist.json"


def digest(path):
    """Hash bytes without depending on timestamps or file permissions."""
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def copy_file(root, relative, destination):
    """Reject symlink escapes, including symlinked parent directories."""
    source = root / relative
    if source.resolve() != source.absolute() or not source.is_file():
        raise ValueError(f"Not an ordinary input file: {relative}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def source_names(root):
    """Only tracked authored paths enter the source/deployment handoff."""
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode()
    return [Path(name) for name in raw.split("\0") if name]


def write_checksums(folder):
    """Create the inventory before archives; the inventory excludes itself."""
    files = {
        p.relative_to(folder).as_posix(): digest(p)
        for p in sorted(folder.rglob("*"))
        if p.is_file() and p != folder / "SHA256SUMS"
    }
    (folder / "SHA256SUMS").write_text("".join(f"{sha}  {name}\n" for name, sha in files.items()))


def archive(folder, destination):
    """Use stable timestamps and permissions for repeatable ZIP bytes."""
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for path in sorted(folder.rglob("*")):
            if path.is_symlink():
                raise ValueError(f"Symlink in archive: {path}")
            directory = path.is_dir()
            info = zipfile.ZipInfo(
                f"{folder.name}/{path.relative_to(folder).as_posix()}" + ("/" if directory else ""),
                date_time=(1980, 1, 1, 0, 0, 0),
            )
            info.create_system = 3
            mode = 0o40755 if directory else (0o100755 if path.stat().st_mode & 0o111 else 0o100644)
            info.external_attr = (mode << 16) | (0x10 if directory else 0)
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, b"" if directory else path.read_bytes())


def assemble(root, target):
    """Assemble checked artifacts into an unpublished staging directory."""
    names = source_names(root)
    for side in ("server", "client"):
        (target / side).mkdir()
        copy_file(root, Path("LICENSE"), target / side / "LICENSE")
        copy_file(root, Path(f"packaging/{side.upper()}.md"), target / side / "INSTALL.md")
        for mod in MODS:
            selected = [p for p in names if p.parts[:2] == ("mods", mod)]
            if not selected or Path(f"mods/{mod}/42/mod.info") not in selected:
                raise ValueError(f"Missing tracked mod: {mod}")
            for relative in selected:
                copy_file(root, relative, target / side / relative)
    # The generated driver mesh/script package contains only original authored assets.
    sys.path.insert(0, str(root / "scripts"))
    from vehicle_probe_ops import package_driver_assets

    for side in ("server", "client"):
        package_driver_assets(root, target / side / "mods/AKRDriverProbe")
    for relative in names:
        if (
            relative.parts[0] in SOURCE_DIRS
            or str(relative) in SOURCE_FILES
            or str(relative) in [f"references/{name}" for name in REFERENCES]
        ):
            copy_file(root, relative, target / "server" / relative)
    # Include new packaging files even before their first commit.
    for relative in (
        "scripts/dist.py",
        "tests/test_dist.py",
        "packaging/CLIENT.md",
        "packaging/SERVER.md",
    ):
        copy_file(root, Path(relative), target / "server" / relative)
    agent = root / "artifacts/scenario-agent"
    manifest = json.loads((agent / "manifest.json").read_text())
    jar = agent / "akr-scenario-agent.jar"
    if digest(jar) != manifest["jar_sha256"]:
        raise ValueError("Scenario JAR does not match build manifest")
    with zipfile.ZipFile(jar) as contents:
        if any(n.startswith("zombie/") for n in contents.namelist()):
            raise ValueError("Refusing to distribute engine classes")
    for name in ("akr-scenario-agent.jar", "manifest.json"):
        copy_file(
            root,
            Path("artifacts/scenario-agent") / name,
            target / "server/artifacts/scenario-agent" / name,
        )
    runtime = root / "artifacts/npc-service-runtime"
    runtime_manifest = json.loads((runtime / "opt/akr/runtime-manifest.json").read_text())
    for name, expected in runtime_manifest["files"].items():
        relative = Path(name)
        if (
            relative.is_absolute()
            or ".." in relative.parts
            or digest(runtime / relative) != expected
        ):
            raise ValueError(f"Invalid worker runtime member: {name}")
        copy_file(runtime, relative, target / "server/artifacts/npc-service-runtime" / relative)
    copy_file(
        runtime,
        Path("opt/akr/runtime-manifest.json"),
        target / "server/artifacts/npc-service-runtime/opt/akr/runtime-manifest.json",
    )
    # The agent embeds protobuf-javalite; preserve the dependency's license from the worker source.
    copy_file(
        root,
        Path(".tooling/npc-service/protobuf-36.1/LICENSE"),
        target / "server/licenses/protobuf.txt",
    )
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root).decode().strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=root).strip())
    metadata = {
        "schema": 1,
        "project": "AKR",
        "private_development_bundle": True,
        "revision": revision,
        "dirty": dirty,
        "game_build": manifest["game_build"],
        "java": manifest["java"],
        "platform": "server Linux x86_64; ordinary compatible client",
        "mods": [*MODS, "AKRDriverProbe"],
        "agent_sha256": manifest["jar_sha256"],
        "qualification": "One-client experimental checks only; not a complete First Week release",
        "upstream": {
            "Bandits2": {
                "workshop_id": "3268487204",
                "BanditUpdate.lua_sha256": manifest["bandits_update_sha256"],
            }
        },
    }
    for side in ("server", "client"):
        (target / side / "manifest.json").write_text(json.dumps(metadata, indent=2) + "\n")
        write_checksums(target / side)
        archive(target / side, target / f"AKR-{side}.zip")
    (target / MARKER).write_text(json.dumps(metadata, indent=2) + "\n")
    write_checksums(target)


def verify(target):
    """Check all payloads and reject unlisted files, malformed paths and symlinks."""
    for folder in (target, target / "server", target / "client"):
        expected = set()
        for line in (folder / "SHA256SUMS").read_text().splitlines():
            sha, name = line.split("  ", 1)
            relative = Path(name)
            if relative.is_absolute() or ".." in relative.parts or name in expected:
                raise ValueError("Invalid checksum path")
            path = folder / relative
            if path.resolve() != path.absolute() or digest(path) != sha:
                raise ValueError(f"Checksum mismatch: {name}")
            expected.add(name)
        actual = {
            p.relative_to(folder).as_posix()
            for p in folder.rglob("*")
            if p.is_file() and p != folder / "SHA256SUMS"
        }
        if actual != expected:
            raise ValueError("Distribution has missing or unlisted files")
    print(f"Verified distribution: {target}")


def build(root=ROOT):
    """Rebuild hooks/worker, stage atomically, and preserve an old bundle on failure."""
    destination = root / "dist"
    if destination.is_symlink() or (destination.exists() and not (destination / MARKER).is_file()):
        raise ValueError("Refusing to replace dist/ not owned by this packager")
    for command in (
        [sys.executable, "scripts/build_scenario_agent.py"],
        [sys.executable, "scripts/build_npc_service.py", "--bundle"],
    ):
        subprocess.run(command, cwd=root, check=True)
    with tempfile.TemporaryDirectory(prefix=".dist-", dir=root / "artifacts") as temporary:
        stage = Path(temporary) / "dist"
        stage.mkdir()
        assemble(root, stage)
        verify(stage)
        previous = Path(temporary) / "previous"
        if destination.exists():
            destination.rename(previous)
        try:
            stage.rename(destination)
        except BaseException:
            if previous.exists():
                previous.rename(destination)
            raise
    print(f"Built {destination}/server, client and AKR-server.zip / AKR-client.zip")


def dispatch(args):
    """CLI entrypoint shared by ./dayone and direct invocation."""
    if args in ([], ["build"]):
        build()
    elif args == ["verify"]:
        verify(ROOT / "dist")
    else:
        raise SystemExit("Usage: ./dayone dist [build|verify]")


if __name__ == "__main__":
    dispatch(sys.argv[1:])
