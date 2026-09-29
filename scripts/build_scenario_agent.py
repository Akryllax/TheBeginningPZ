"""Build the separate pinned B42.20.4 gameplay agent entirely inside this project."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def build(
    cache: Path,
    game_jar: Path,
    test: bool = False,
    generate_only: bool = False,
    test_layer: str = "all",
):
    if test_layer not in {"all", "unit", "integration"}:
        raise ValueError("Invalid test layer")
    cache = cache.resolve()
    jdk = cache / "jdk-25.0.4.1+1"
    protoc = cache / "protoc/bin/protoc"
    runtime = cache / "protobuf-javalite-4.36.1.jar"
    expected = "91d3dba2521322103230c509d41a017da3a5e6766610adcb10c5bb127cbb26c5"
    if hashlib.file_digest(runtime.open("rb"), "sha256").hexdigest() != expected:
        raise ValueError("Pinned protobuf runtime checksum mismatch")
    out = ROOT / "artifacts/scenario-agent"
    classes, generated = out / "classes", out / "generated"
    if generated.exists():
        shutil.rmtree(generated)
    generated.mkdir(parents=True, exist_ok=True)
    proto = ROOT / "protocol/npc_control.proto"
    subprocess.run(
        [str(protoc), "-I", str(proto.parent), f"--java_out=lite:{generated}", str(proto)],
        check=True,
    )
    runtime_proto = ROOT / "protocol/runtime_control.proto"
    subprocess.run(
        [
            str(protoc),
            "-I",
            str(proto.parent),
            f"--java_out=lite:{generated}",
            f"--python_out={generated}",
            str(runtime_proto),
        ],
        check=True,
    )
    # Generate a tiny allowlisted field schema, avoiding a second protobuf runtime.
    schema = []
    for name, body in re.findall(r"message\s+(\w+)\s*\{([^{}]*)\}", proto.read_text()):
        fields = []
        for repeat, kind, field in re.findall(r"\b(repeated\s+)?(\w+)\s+(\w+)\s*=\s*\d+\s*;", body):
            fields.append(
                f'new ProtocolCodec.Field("{field}","{kind}",{str(bool(repeat)).lower()})'
            )
        schema.append(f'Map.entry("{name}",new ProtocolCodec.Field[]{{{",".join(fields)}}})')
    schema_path = generated / "net/akr/scenario/bridge/ProtocolSchema.java"
    schema_path.parent.mkdir(parents=True, exist_ok=True)
    schema_path.write_text(
        "package net.akr.scenario.bridge;\nimport java.util.*;\nfinal class ProtocolSchema {\n"
        "static final Map<String,ProtocolCodec.Field[]> FIELDS=Map.ofEntries(\n"
        + ",\n".join(schema)
        + ");\n}\n"
    )
    if generate_only:
        # IDE source path only; leaves the deployable JAR untouched.
        print(generated)
        return
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    cp = ":".join(
        [
            str(runtime),
            str(game_jar.resolve()),
            *(str(p) for p in sorted(game_jar.parent.glob("*.jar")) if p != game_jar),
        ]
    )
    sources = [*ROOT.glob("scenario-agent/src/**/*.java"), *generated.rglob("*.java")]
    subprocess.run(
        [
            str(jdk / "bin/javac"),
            "--release",
            "25",
            "-cp",
            cp,
            "-d",
            str(classes),
            *map(str, sources),
        ],
        check=True,
    )
    jar = out / "akr-scenario-agent.jar"
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\r\nPremain-Class: net.akr.scenario.ScenarioAgent\r\n\r\n",
        )
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes).as_posix())
        with zipfile.ZipFile(runtime) as upstream:
            for name in upstream.namelist():
                if not name.endswith("/") and name != "META-INF/MANIFEST.MF":
                    archive.writestr(name, upstream.read(name))
    # Read the compiled compatibility contract. Source-text regexes silently lost every
    # guard when a Java formatter inserted spaces around Map.entry arguments.
    contract = subprocess.run(
        [str(jdk / "bin/java"), "-cp", f"{classes}:{cp}", "net.akr.scenario.compat.BuildManifest"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()
    native = [line.split("\t") for line in contract if line.startswith("native\t")]
    guarded = [line.split("\t") for line in contract if line.startswith("class\t")]
    if len(native) != 1 or len(native[0]) != 3 or len(guarded) < 40:
        raise ValueError("Incomplete compiled build guard contract")
    native_library, native_hash = native[0][1:]
    guards = {name: digest for _, name, digest in guarded}
    if len(guards) != len(guarded) or any(
        not re.fullmatch(r"[a-f0-9]{64}", digest) for digest in guards.values()
    ):
        raise ValueError("Duplicate or malformed guarded class hash")
    if not re.fullmatch(r"[a-f0-9]{64}", native_hash):
        raise ValueError("Malformed guarded native library hash")
    with zipfile.ZipFile(game_jar) as game:
        for name, expected_hash in guards.items():
            if hashlib.sha256(game.read(name + ".class")).hexdigest() != expected_hash:
                raise ValueError(f"Unsupported game build: {name}")
    (out / "manifest.json").write_text(
        json.dumps(
            {
                "game_build": "42.20.4/b0bbce05d5",
                "java": 25,
                "class_hashes": guards,
                "jar_sha256": hashlib.file_digest(jar.open("rb"), "sha256").hexdigest(),
                "server_physics_library": native_library,
                "server_physics_sha256": native_hash,
                "bandits_update_sha256": "fb9bd559da4e0faabd2c35c41cd7d2cd74d85510ef642a7ba6e3776cb8a02192",
            },
            indent=2,
        )
        + "\n"
    )
    if test:
        tests = list(ROOT.glob("scenario-agent/test/**/*.java"))
        if not tests:
            raise ValueError("No scenario agent tests")
        tests_dir = out / "test-classes"
        tests_dir.mkdir(exist_ok=True)
        subprocess.run(
            [
                str(jdk / "bin/javac"),
                "--release",
                "25",
                "-cp",
                f"{classes}:{cp}",
                "-d",
                str(tests_dir),
                *map(str, tests),
            ],
            check=True,
        )
        if test_layer in {"all", "unit"}:
            subprocess.run(
                [
                    str(jdk / "bin/java"),
                    "-ea",
                    "-cp",
                    f"{tests_dir}:{classes}:{cp}",
                    "net.akr.scenario.RuntimeUnitFixture",
                ],
                check=True,
            )
        if test_layer in {"all", "integration"}:
            subprocess.run(
                [
                    str(jdk / "bin/java"),
                    "-ea",
                    "-cp",
                    f"{tests_dir}:{classes}:{cp}",
                    "net.akr.scenario.ScenarioFixture",
                    str(game_jar.resolve()),
                ],
                check=True,
            )
            subprocess.run(
                [
                    str(jdk / "bin/java"),
                    "--enable-native-access=ALL-UNNAMED",
                    f"-Djava.library.path={game_jar.resolve().parent.parent / 'linux64'}",
                    "-cp",
                    f"{tests_dir}:{cp}",
                    "net.akr.scenario.DriverAssetFixture",
                    str(ROOT / "mods/AKRStoryteller/42/media/models_X/AKR/SeatedDriver.x"),
                ],
                check=True,
            )
            settings = out / "premain-fixture.properties"
            bandit = (
                ROOT
                / "data/game-files/steamapps/workshop/content/108600/3268487204/mods/Bandits/42.20/media/lua/client/BanditUpdate.lua"
            )
            settings.write_text(
                f"side=server\nscenario.enabled=true\nworld=AKRVehicleProbe_fixture\nsocket={out}/fixture-unused.sock\nbandits_update_file={bandit}\n"
            )
            subprocess.run(
                [
                    str(jdk / "bin/java"),
                    "-XX:-CreateCoredumpOnCrash",
                    f"-XX:ErrorFile={out}/hs_err_pid%p.log",
                    f"-javaagent:{jar}={settings}",
                    "-ea",
                    "-cp",
                    f"{tests_dir}:{jar}:{cp}",
                    "net.akr.scenario.PremainFixture",
                ],
                check=True,
            )
            subprocess.run(
                [
                    str(jdk / "bin/java"),
                    "-XX:-CreateCoredumpOnCrash",
                    f"-XX:ErrorFile={out}/hs_err_pid%p.log",
                    f"-javaagent:{jar}={settings}",
                    "-classpath",
                    "pzexe.jar",
                    "-Djava.library.path=.",
                    "zombie.pzexe",
                ],
                cwd=game_jar.parent.parent,
                check=True,
            )
    print(jar)
    print("SHA256", hashlib.file_digest(jar.open("rb"), "sha256").hexdigest())


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--cache", type=Path, default=ROOT / ".tooling/agent")
    p.add_argument(
        "--game-jar", type=Path, default=ROOT / "data/game-files/java/projectzomboid.jar"
    )
    p.add_argument("--test", action="store_true")
    p.add_argument(
        "--generate-only", action="store_true", help="only regenerate protocol sources for the IDE"
    )
    p.add_argument("--test-layer", choices=["all", "unit", "integration"], default="all")
    args = p.parse_args()
    build(args.cache, args.game_jar, args.test, args.generate_only, args.test_layer)
