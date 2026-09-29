"""Build the optional Linux x64 server agent with verified, pinned tools.

Uses an isolated cache; never modifies the game's bundled Java installation.
"""

import argparse
import hashlib
import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = {
    "jdk25.tar.gz": (
        (
            "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/"
            "OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
        ),
        "dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e",
    ),
    "protoc-36.1-linux-x86_64.zip": (
        "https://github.com/protocolbuffers/protobuf/releases/download/v36.1/protoc-36.1-linux-x86_64.zip",
        "c4bc672d9d49214dc8cafdceadf4df92182d6ca8e3ec65a56b2d7de5602669b4",
    ),
    "protobuf-javalite-4.36.1.jar": (
        (
            "https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-javalite/4.36.1/"
            "protobuf-javalite-4.36.1.jar"
        ),
        "91d3dba2521322103230c509d41a017da3a5e6766610adcb10c5bb127cbb26c5",
    ),
}


def build(cache):
    cache.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in TOOLS.items():
        target = cache / name
        if not target.exists():
            subprocess.run(
                ["curl", "-fLsS", "--max-time", "300", url, "-o", str(target)], check=True
            )
        if hashlib.file_digest(target.open("rb"), "sha256").hexdigest() != digest:
            raise ValueError(f"Checksum mismatch: {target}")
    jdk = cache / "jdk-25.0.4.1+1"
    if not (jdk / "bin/javac").exists():
        with tarfile.open(cache / "jdk25.tar.gz") as archive:
            archive.extractall(cache, filter="data")
    protoc = cache / "protoc/bin/protoc"
    if not protoc.exists():
        with zipfile.ZipFile(cache / "protoc-36.1-linux-x86_64.zip") as archive:
            archive.extractall(cache / "protoc")
        protoc.chmod(0o755)
    out = ROOT / "artifacts/position-agent"
    classes, generated = out / "classes", out / "generated"
    if classes.exists():
        shutil.rmtree(classes)
    if generated.exists():
        shutil.rmtree(generated)
    classes.mkdir(parents=True)
    generated.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            str(protoc),
            "-I",
            str(ROOT / "protocol"),
            f"--java_out=lite:{generated}",
            f"--python_out={ROOT / 'observer/proto'}",
            str(ROOT / "protocol/positions.proto"),
        ],
        check=True,
    )
    runtime = cache / "protobuf-javalite-4.36.1.jar"
    sources = [*ROOT.glob("server-agent/src/**/*.java"), *generated.rglob("*.java")]
    subprocess.run(
        [
            str(jdk / "bin/javac"),
            "--release",
            "25",
            "-cp",
            str(runtime),
            "-d",
            str(classes),
            *map(str, sources),
        ],
        check=True,
    )
    # A single deployable jar. Include upstream protobuf's license notices.
    jar = out / "observer-position-agent.jar"
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\r\nPremain-Class: net.akr.observer.PositionAgent\r\n\r\n",
        )
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes).as_posix())
        with zipfile.ZipFile(runtime) as upstream:
            for name in upstream.namelist():
                if not name.endswith("/") and name != "META-INF/MANIFEST.MF":
                    archive.writestr(name, upstream.read(name))
    print(jar)
    print("SHA256", hashlib.file_digest(jar.open("rb"), "sha256").hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=ROOT.parent / ".tooling/observer-agent")
    build(parser.parse_args().cache)
