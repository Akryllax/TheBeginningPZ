"""Emit reviewed compatibility metadata; never derive approvals from installed game bytes."""

from pathlib import Path


def write_hook_contract(profile: dict, classes: Path):
    """Write the same exact hook expectations for offline checks and packaged premain."""
    hooks = profile.get("hooks", {})
    if not hooks or any(not isinstance(count, int) or count < 1 for count in hooks.values()):
        raise ValueError("Missing or invalid reviewed hooks")
    target = classes / "META-INF/akr/scenario-hooks.tsv"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("".join(f"{site}\t{count}\n" for site, count in sorted(hooks.items())))
    return target


def generate_build_profile(profile: dict, generated: Path, package: str):
    """Generate constants only from explicitly reviewed pins, never the local installation."""
    import json
    import re

    if profile.get("guard_scope") != "disposable-worlds-only":
        raise ValueError("Build profile lacks an explicit disposable-world review")
    pins = profile.get("class_pins", {})
    if len(pins) < 40 or any(not re.fullmatch(r"[a-f0-9]{64}", value) for value in pins.values()):
        raise ValueError("Incomplete reviewed class pins")

    def quoted(value):
        return json.dumps(value)

    constants = {
        "JAR_SHA256": profile["jar_sha256"],
        "ID": profile["id"],
        "BUILD": profile["build"],
        "FULL_VERSION": profile["full_version"],
        "BANDITS_SHA256": profile["dependency_pins"]["BanditUpdate.lua"],
        "PHYSICS_SHA256": profile["native_pins"]["linux64/libPZBulletNoOpenGL64.so"],
    }
    text = f"package {package};\nimport java.util.Map;\n/** Generated from the reviewed test-only profile; never edit this output. */\npublic final class BuildProfile {{\nprivate BuildProfile() {{}}\n"
    text += "".join(
        f"public static final String {key} = {quoted(value)};\n" for key, value in constants.items()
    )
    text += "public static final Map<String,String> CLASS_HASHES = Map.ofEntries(\n"
    text += ",\n".join(
        f"Map.entry({quoted(key)},{quoted(value)})" for key, value in sorted(pins.items())
    )
    text += ");\n}\n"
    target = generated / package.replace(".", "/") / "BuildProfile.java"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
    return target


def generate_protocol_schema(proto: Path, generated: Path):
    """Emit the bounded Lua/protobuf field table into the caller-owned build directory."""
    import re

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


def validate_client_profile(profile: dict, root: Path, *, upstream: bool = True):
    """Reject stale Lua closure guards before packaging or qualifying a candidate."""
    import re

    callbacks = profile["bandits_callbacks"]
    manifest = profile["bandits_client_manifest"]
    gates = (
        "mods/AKRStoryteller/42/media/lua/shared/AKRScenario/ClientGate.lua",
        "mods/AKRDevTools/42/media/lua/shared/AKRDevTools/Gate.lua",
    )
    for name in gates:
        source = (root / name).read_text()
        if f'G.manifest = "{manifest}"' not in source:
            raise ValueError(f"Client manifest differs from profile: {name}")
        for event, line in callbacks.items():
            if not re.search(rf"\b{re.escape(event)}\s*=\s*{line}\b", source):
                raise ValueError(f"Client callback differs from profile: {name}: {event}")
    config = root / "mods/AKRStoryteller/42/media/lua/shared/AKRScenario/Config.lua"
    if f'clientManifest = "{manifest}"' not in config.read_text():
        raise ValueError("Server/client manifest disagreement")
    if upstream:
        lines = (root / profile["dependencies"]["BanditUpdate.lua"]).read_text().splitlines()
        for event, line in profile["bandits_callback_declarations"].items():
            function = "OnBanditUpdate" if event == "OnZombieUpdate" else event
            if (
                line < 1
                or line > len(lines)
                or not re.match(rf"local function {re.escape(function)}\(", lines[line - 1])
            ):
                raise ValueError(f"Upstream callback declaration differs: {event}")
