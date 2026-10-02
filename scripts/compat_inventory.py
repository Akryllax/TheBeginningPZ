"""Inventory authored Java linkage without loading game classes; output is a review proposal."""

from functools import lru_cache
import json
from pathlib import Path
import zipfile
from contextlib import ExitStack

from compat_classfile import parse_class


def inventory(game_jar: Path, classes: Path, jdk_module: Path | None = None):
    """Resolve declared/inherited game symbols, retaining each authored caller as provenance."""
    with ExitStack() as stack:
        archive = stack.enter_context(zipfile.ZipFile(game_jar))
        jdk = (
            stack.enter_context(zipfile.ZipFile(jdk_module))
            if jdk_module and jdk_module.is_file()
            else None
        )
        names = {name[:-6] for name in archive.namelist() if name.endswith(".class")}

        @lru_cache(None)
        def read(name):
            if name in names:
                return parse_class(archive.read(name + ".class"))
            if jdk_module and jdk_module.is_dir() and name.startswith("java/"):
                path = jdk_module / "java.base" / (name + ".class")
                return parse_class(path.read_bytes()) if path.exists() else None
            if jdk and name.startswith("java/"):
                try:
                    return parse_class(jdk.read("classes/" + name + ".class"))
                except KeyError:
                    return None
            return None

        def resolve(name, kind, member, seen=None):
            seen = set() if seen is None else seen
            if name in seen:
                return None
            seen.add(name)
            cls = read(name)
            if cls is None:
                return None
            if member in cls[kind]:
                return name, cls[kind][member]
            # Constructors never inherit; field/method lookup can involve interfaces.
            if member.startswith("<init>("):
                return None
            parents = (
                [*cls["interfaces"], cls["parent"]]
                if kind == "fields"
                else [cls["parent"], *cls["interfaces"]]
            )
            for parent in parents:
                if parent:
                    found = resolve(parent, kind, member, seen)
                    if found:
                        return found
            return None

        contracts, unresolved, external = {}, [], []
        for path in sorted(classes.glob("net/akr/**/*.class")):
            authored = parse_class(path.read_bytes())
            for ref in authored["references"]:
                if ref["class"] not in names:
                    continue  # JDK/own/protobuf dependencies have their own build inputs.
                found = resolve(ref["class"], ref["kind"], ref["member"])
                if found is None:
                    # Object methods are inherited from the JDK rather than the game archive.
                    if ref["member"] in {
                        "toString()Ljava/lang/String;",
                        "hashCode()I",
                        "equals(Ljava/lang/Object;)Z",
                        "getClass()Ljava/lang/Class;",
                    }:
                        continue
                    unresolved.append({**ref, "caller": authored["name"]})
                    continue
                owner, declaration = found
                if owner not in names:
                    external.append({**ref, "resolved_owner": owner})
                    continue
                key = (owner, ref["kind"], ref["member"])
                contract = contracts.setdefault(
                    key,
                    {
                        "class": owner,
                        "kind": ref["kind"],
                        "member": ref["member"],
                        "access_mask": 15,
                        "access": declaration["access"] & 15,
                        "callers": [],
                    },
                )
                if authored["name"] not in contract["callers"]:
                    contract["callers"].append(authored["name"])
        return {
            "contracts": [contracts[key] for key in sorted(contracts)],
            "unresolved": unresolved,
            "external": external,
        }


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-jar", required=True, type=Path)
    parser.add_argument("--classes", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    jdk = Path(__file__).resolve().parents[1] / ".tooling/agent/jdk-25.0.4.1+1/jmods/java.base.jmod"
    if not jdk.exists():
        jdk = Path(__file__).resolve().parents[1] / "artifacts/compat/jdk-image"
    report = inventory(args.game_jar, args.classes, jdk if jdk.exists() else None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(
        f"{len(report['contracts'])} referenced declarations; {len(report['unresolved'])} unresolved references"
    )


def explicit_contracts(game_jar: Path, bindings: dict):
    """Resolve reviewed reflection/Lua names, including inherited members, without initialization."""
    with zipfile.ZipFile(game_jar) as archive:

        @lru_cache(None)
        def read(name):
            try:
                return parse_class(archive.read(name + ".class"))
            except KeyError:
                return None

        def find(owner, kind, name, seen):
            if owner in seen:
                return []
            seen.add(owner)
            cls = read(owner)
            if cls is None:
                return []
            matches = [
                (owner, key, member) for key, member in cls[kind].items() if member["name"] == name
            ]
            if matches:
                return matches
            for parent in [cls["parent"], *cls["interfaces"]]:
                if parent:
                    matches.extend(find(parent, kind, name, seen))
            return matches

        contracts = []
        for owner, kinds in bindings["classes"].items():
            for kind, names in kinds.items():
                for name in names:
                    matches = find(owner, kind, name, set())
                    if not matches:
                        raise ValueError(f"Unresolved explicit binding: {owner}.{name}")
                    for declaring, key, member in matches:
                        contracts.append(
                            {
                                "class": declaring,
                                "kind": kind,
                                "member": key,
                                "access_mask": 15,
                                "access": member["access"] & 15,
                                "callers": ["explicit-reflection-or-lua:" + owner],
                            }
                        )
        return contracts
