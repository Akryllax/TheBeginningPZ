#!/usr/bin/env python3
"""Inspect selected game classes with pinned, project-local Vineflower and javap."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tooling/decompiler"


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def setup():
    spec = json.loads((ROOT / "references/java-decompiler.json").read_text())
    TOOLS.mkdir(parents=True, exist_ok=True)
    tool = TOOLS / f"vineflower-{spec['version']}.jar"
    if not tool.exists():
        with tempfile.NamedTemporaryFile(dir=TOOLS, suffix=".partial", delete=False) as stream:
            pending = Path(stream.name)
            try:
                request = urllib.request.Request(
                    spec["url"], headers={"User-Agent": "Lofers-project-tooling"}
                )
                with urllib.request.urlopen(request, timeout=60) as response:
                    while data := response.read(1024 * 1024):
                        stream.write(data)
                stream.close()
                if sha256(pending) != spec["sha256"]:
                    raise RuntimeError("Decompiler release checksum mismatch")
                pending.replace(tool)
            finally:
                pending.unlink(missing_ok=True)
    if sha256(tool) != spec["sha256"]:
        raise RuntimeError("Installed decompiler checksum mismatch")
    java = ROOT / spec["java"]
    if not java.is_file():
        raise RuntimeError(
            "Project Java toolchain is missing; build the existing scenario agent toolchain first"
        )
    # Preserve notices directly from the verified upstream archive.
    with zipfile.ZipFile(tool) as archive:
        for name in archive.namelist():
            if name.upper() in {
                "META-INF/LICENSE",
                "META-INF/LICENSE.TXT",
                "META-INF/NOTICE",
                "META-INF/NOTICE.TXT",
            }:
                (TOOLS / Path(name).name).write_bytes(archive.read(name))
    return spec, tool, java


def selected_entries(archive, classes):
    names = set(archive.namelist())
    selected = set()
    for name in classes:
        if not re.fullmatch(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*", name, flags=re.ASCII):
            raise ValueError(f"Use a fully qualified Java class name: {name}")
        entry = name.replace(".", "/") + ".class"
        if entry not in names:
            raise ValueError(f"Class not found in input JAR: {name}")
        outer = entry.split("$", 1)[0].removesuffix(".class")
        selected.update(
            n
            for n in names
            if n == outer + ".class" or (n.startswith(outer + "$") and n.endswith(".class"))
        )
    return sorted(selected)


def decompile(jar, classes, output=None):
    jar = jar.expanduser().resolve()
    with zipfile.ZipFile(jar) as archive:
        selected = selected_entries(archive, classes)
    digest = sha256(jar)
    output = (
        output.expanduser().resolve() if output else ROOT / "artifacts/decompiled" / digest[:12]
    )
    if not output.is_relative_to(ROOT):
        raise ValueError("Decompiler output must stay inside the project")
    spec, tool, java = setup()
    output.mkdir(parents=True, exist_ok=True)
    run = output / "runs" / str(time.time_ns())
    run.mkdir(parents=True)
    inputs = run / "selected-classes.jar"
    hashes = {}
    with (
        zipfile.ZipFile(jar) as source,
        zipfile.ZipFile(inputs, "w", zipfile.ZIP_DEFLATED) as target,
    ):
        for name in selected:
            data = source.read(name)
            hashes[name] = hashlib.sha256(data).hexdigest()
            target.writestr(name, data)
    sources = output / "src"
    sources.mkdir(exist_ok=True)
    command = [
        str(java),
        "-Xmx1g",
        "-XX:ActiveProcessorCount=2",
        "-jar",
        str(tool),
        "--folder",
        "--thread-count=2",
        "--log-level=WARN",
        f"--add-external={jar}",
        *[f"--add-external={p}" for p in sorted(jar.parent.glob("*.jar")) if p != jar],
        str(inputs),
        str(sources),
    ]
    print(f"Decompiling {len(selected)} class files; output: {sources}", flush=True)
    manifest = {
        "source_jar": str(jar),
        "source_sha256": digest,
        "tool": spec,
        "classes": classes,
        "class_sha256": hashes,
        "command": command,
        "status": "running",
    }
    receipt = run / "manifest.json"
    receipt.write_text(json.dumps(manifest, indent=2) + "\n")
    try:
        with (run / "vineflower.log").open("w") as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=240)
        bytecode = output / "bytecode"
        bytecode.mkdir(exist_ok=True)
        for name in classes:
            result = subprocess.run(
                [str(java.with_name("javap")), "-p", "-c", "-s", "-classpath", str(jar), name],
                capture_output=True,
                text=True,
                check=True,
                timeout=30,
            )
            (bytecode / (name + ".txt")).write_text(result.stdout)
        expected = {name.split("$", 1)[0].replace(".", "/") + ".java" for name in classes}
        missing = [name for name in expected if not (sources / name).is_file()]
        if missing:
            raise RuntimeError("Decompiler omitted requested sources: " + ", ".join(missing))
        manifest["sources"] = {name: sha256(sources / name) for name in sorted(expected)}
        manifest["status"] = "completed"
    except BaseException:
        manifest["status"] = "failed"
        raise
    finally:
        receipt.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Java reconstruction, exact javap output and provenance: {output}")
    print("Inspect the log for warnings; source reconstruction is not runtime validation.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("classes", nargs="*")
    parser.add_argument(
        "--jar", type=Path, default=ROOT / "data/game-files/java/projectzomboid.jar"
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--setup", action="store_true", help="Download and verify the pinned decompiler"
    )
    args = parser.parse_args()
    if args.classes:
        decompile(args.jar, args.classes, args.output)
    elif args.setup:
        _, tool, _ = setup()
        print(f"Verified project-local decompiler: {tool}")
    else:
        parser.error("Supply class names or --setup")


if __name__ == "__main__":
    main()
