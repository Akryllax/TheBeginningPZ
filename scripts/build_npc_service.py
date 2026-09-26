#!/usr/bin/env python3
"""Build the pinned native planner without installing anything globally."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tooling/npc-service"
DEPENDENCIES = (
    ("lua-5.4.9", "https://www.lua.org/ftp/lua-5.4.9.tar.gz", "2335b6c582a52654f94612bf10d2f4672805d05329aa6568b1d8cd9e5c6fb8e6"),
    ("protobuf-36.1", "https://github.com/protocolbuffers/protobuf/releases/download/v36.1/protobuf-36.1.tar.gz", "dc74fa582f559cbd31614ddfefb4868f43c919d7184bde514bb47f90c6025eb8"),
    ("abseil-cpp-20250512.1", "https://github.com/abseil/abseil-cpp/archive/refs/tags/20250512.1.tar.gz", "9b7a064305e9fd94d124ffa6cc358592eb42b5da588fb4e07d09254aa40086db"),
)


def run(*args: str | Path, **kwargs):
    return subprocess.run([str(x) for x in args], cwd=ROOT, check=True, **kwargs)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dependencies():
    downloads = TOOLS / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    for name, url, sha in DEPENDENCIES:
        archive = downloads / (name + ".tar.gz")
        if not archive.exists():
            partial = archive.with_suffix(".partial")
            with urllib.request.urlopen(url, timeout=60) as source, partial.open("wb") as out:
                shutil.copyfileobj(source, out)
            if digest(partial) != sha:
                raise RuntimeError(f"Download checksum mismatch: {name}")
            partial.replace(archive)
        if digest(archive) != sha:
            raise RuntimeError(f"Cached dependency checksum mismatch: {name}")
        target = TOOLS / name
        if not target.exists():
            with tarfile.open(archive) as contents:
                contents.extractall(TOOLS, filter="data")
    protoc = ROOT / ".tooling/agent/protoc/bin/protoc"
    actual = run(protoc, "--version", capture_output=True, text=True).stdout.strip()
    if actual != "libprotoc 36.1":
        raise RuntimeError(f"Pinned protobuf C++7.36.1 requires protoc36.1; found {actual}")


def bundle(binary: Path):
    target = ROOT / "artifacts/npc-service-runtime"
    temporary = ROOT / "artifacts/npc-service-runtime.partial"
    if temporary.exists():
        shutil.rmtree(temporary)
    (temporary / "usr/local/bin").mkdir(parents=True)
    shutil.copy2(binary, temporary / "usr/local/bin/lofers-npc-service")
    (temporary / "opt/lofers").mkdir(parents=True)
    shutil.copytree(ROOT / "npc-service/rules", temporary / "opt/lofers/rules")
    # Preserve loader and library paths exactly; this bundle does not depend on
    # the libc version of a guessed container distribution.
    dependencies = run("ldd", binary, capture_output=True, text=True).stdout
    paths = set(re.findall(r"(?:=>\s*)?(/[^\s]+)\s+\(", dependencies))
    if not paths or "not found" in dependencies:
        raise RuntimeError("Cannot bundle incomplete runtime dependencies")
    for item in sorted(paths):
        source = Path(item)
        destination = temporary / source.relative_to("/")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source.resolve(), destination)
        destination.chmod(0o755)
    licenses = temporary / "usr/share/licenses/lofers-npc-service"
    licenses.mkdir(parents=True)
    for name, source in [("lua.html", TOOLS / "lua-5.4.9/doc/readme.html"),
                         ("protobuf.txt", TOOLS / "protobuf-36.1/LICENSE"),
                         ("abseil.txt", TOOLS / "abseil-cpp-20250512.1/LICENSE")]:
        shutil.copy2(source, licenses / name)
    # Local runtime bundle; system libraries retain their local package notices.
    for source in (Path("/usr/share/licenses/glibc"), Path("/usr/share/licenses/libstdc++"), Path("/usr/share/licenses/libgcc")):
        if source.is_dir():
            shutil.copytree(source, licenses / source.name, dirs_exist_ok=True)
    manifest = {
        "local_only": True,
        "dependencies": [{"name": name, "url": url, "sha256": sha} for name, url, sha in DEPENDENCIES],
        "compiler": run("g++", "--version", capture_output=True, text=True).stdout.splitlines()[0],
        "runtime": dependencies,
        "files": {str(p.relative_to(temporary)): digest(p) for p in sorted(temporary.rglob("*")) if p.is_file()},
    }
    (temporary / "opt/lofers/runtime-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    if target.exists():
        shutil.rmtree(target)
    temporary.replace(target)
    print(f"Runtime bundle: {target}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--test", action="store_true")
    parser.add_argument("--bundle", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.jobs <= 8:
        parser.error("--jobs must be 1..8")
    dependencies()
    build = TOOLS / "build"
    run("cmake", "-S", ROOT / "npc-service", "-B", build, "-G", "Ninja", "-DCMAKE_BUILD_TYPE=RelWithDebInfo")
    run("cmake", "--build", build, "--parallel", str(args.jobs))
    if args.test:
        run("ctest", "--test-dir", build, "--output-on-failure")
        run("python3", ROOT / "npc-service/tests/wire_test.py", "--binary", build / "lofers-npc-service")
    if args.bundle:
        bundle(build / "lofers-npc-service")
    print(f"Planner binary: {build / 'lofers-npc-service'}")


if __name__ == "__main__":
    main()
