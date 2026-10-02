"""Compile and inspect authored adapters without creating a deployable agent or loading a world."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess

from compat import ROOT, read_profile
from compat_resources import (
    write_hook_contract,
    generate_build_profile,
    generate_protocol_schema,
    validate_client_profile,
)


def verify_hooks(expected, observed):
    """Require every reviewed site count, including unexpected extra injections."""
    return [
        {"site": key, "expected": expected.get(key, 0), "actual": observed.get(key, 0)}
        for key in sorted(expected.keys() | observed.keys())
        if expected.get(key, 0) != observed.get(key, 0)
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True)
    args = parser.parse_args()
    profile = read_profile(ROOT, args.profile)
    validate_client_profile(profile, ROOT)
    game = (ROOT / profile["game_jar"]).resolve()
    tools = ROOT / ".tooling/agent"
    jdk = tools / "jdk-25.0.4.1+1/bin"
    output = ROOT / "artifacts/compat/java"
    generated, classes = output / "generated", output / "classes"
    for folder in (generated, classes):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)
    protoc = tools / "protoc/bin/protoc"
    for proto in (ROOT / "protocol").glob("*.proto"):
        subprocess.run(
            [str(protoc), "-I", str(proto.parent), f"--java_out=lite:{generated}", str(proto)],
            check=True,
        )
    generate_build_profile(profile, generated, "net.akr.scenario.compat")
    generate_protocol_schema(ROOT / "protocol/npc_control.proto", generated)
    cp = ":".join(
        [
            str(game),
            str(tools / "protobuf-javalite-4.36.1.jar"),
            *[str(p) for p in game.parent.glob("*.jar") if p != game],
        ]
    )
    sources = [
        *ROOT.glob("scenario-agent/src*/**/*.java"),
        *ROOT.glob("scenario-agent/test/**/*.java"),
        *ROOT.glob("compatibility/java/**/*.java"),
        *generated.rglob("*.java"),
    ]
    subprocess.run(
        [str(jdk / "javac"), "--release", "25", "-cp", cp, "-d", str(classes), *map(str, sources)],
        check=True,
    )
    write_hook_contract(profile, classes)
    command = [str(jdk / "java"), "-ea", "-cp", f"{classes}:{cp}"]
    result = subprocess.run(
        [*command, "net.akr.scenario.HookProbe", str(game)],
        text=True,
        capture_output=True,
        check=True,
    )
    observed = {}
    for line in result.stdout.splitlines():
        site, count = line.rsplit("\t", 1)
        if site in observed:
            raise ValueError(f"Duplicate hook-probe entry: {site}")
        observed[site] = int(count)
    differences = verify_hooks(profile["hooks"], observed)
    (output / "hooks.json").write_text(
        json.dumps(
            {"expected": profile["hooks"], "observed": observed, "differences": differences},
            indent=2,
        )
        + "\n"
    )
    if differences:
        raise ValueError(f"Hook contract mismatch: {differences}")
    subprocess.run([*command, "net.akr.scenario.RuntimeUnitFixture"], cwd=ROOT, check=True)
    print(
        f"Code-only Java checks passed: {len(observed)} exact hook sites; generated test-only guards; no world initialized"
    )


if __name__ == "__main__":
    main()
