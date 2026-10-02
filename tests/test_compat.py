"""Authored synthetic JVM fixtures; no game download or engine startup required."""

import json
from pathlib import Path
import shutil
import subprocess
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from compat import compare, source_identity
from compat_classfile import parse_class

JAVAC = Path(__file__).resolve().parents[1] / ".tooling/agent/jdk-25.0.4.1+1/bin/javac"
if not JAVAC.exists():
    JAVAC = shutil.which("javac")


def compile_fixture(tmp_path, body, debug=True):
    if not JAVAC:
        pytest.skip("Synthetic bytecode fixture needs javac")
    source = tmp_path / "Example.java"
    source.write_text("public class Example { " + body + " }")
    subprocess.run(
        [str(JAVAC), "-g" if debug else "-g:none", str(source)], check=True, capture_output=True
    )
    return parse_class((tmp_path / "Example.class").read_bytes())


def snap(cls):
    return {
        "classes": {"Example": cls},
        "class_hashes": {"Example": cls["sha256"]},
        "native": {},
        "dependencies": {},
    }


CONTRACT = [
    {"class": "Example", "kind": "methods", "member": "value()I", "access_mask": 15, "access": 1}
]


def test_debug_only_is_metadata(tmp_path):
    a = compile_fixture(tmp_path, "public int value() { return 1; }")
    b = compile_fixture(tmp_path, "public int value() { return 1; }", debug=False)
    report = compare(snap(a), snap(b), CONTRACT)
    assert not report["breaks"]
    assert report["counts"] == {"metadata-only": 1}


@pytest.mark.parametrize(
    "body",
    [
        "",
        "public long value() { return 1; }",
        "private int value() { return 1; }",
        "public static int value() { return 1; }",
    ],
)
def test_contract_break(tmp_path, body):
    a = compile_fixture(tmp_path, "public int value() { return 1; }")
    b = compile_fixture(tmp_path, body)
    assert len(compare(snap(a), snap(b), CONTRACT)["breaks"]) == 1


def test_body_change_preserves_api(tmp_path):
    a = compile_fixture(tmp_path, "public int value() { return 1; }")
    b = compile_fixture(tmp_path, "public int value() { return 2; }")
    report = compare(snap(a), snap(b), CONTRACT)
    assert not report["breaks"]
    assert report["counts"] == {"behavioral-review": 1}


def test_switch_exception_and_symbolic_calls(tmp_path):
    cls = compile_fixture(
        tmp_path,
        'public int value() { try { switch (System.nanoTime() % 3 == 0 ? 4 : 5) { case 4: return "hello".length(); default: return 5; } } catch (Exception e) { return -1; } }',
    )
    assert cls["methods"]["value()I"]["calls"]
    assert "java/lang/System" in json.dumps(cls)


def test_native_changes_and_unmapped_classes():
    a = {
        "classes": {},
        "class_hashes": {"Unknown": "a"},
        "native": {"physics": "a"},
        "dependencies": {},
    }
    b = {**a, "class_hashes": {"Unknown": "b"}, "native": {"physics": "b"}}
    assert compare(a, b, [])["counts"] == {"outside-mapped-coverage": 1, "behavioral-review": 1}


def test_dirty_content_changes_identity(tmp_path):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    subprocess.run(
        [
            "git",
            "-c",
            "user.name=Test",
            "-c",
            "user.email=test@example.invalid",
            "commit",
            "--allow-empty",
            "-qm",
            "fixture",
        ],
        cwd=tmp_path,
        check=True,
    )
    source = tmp_path / "example.py"
    source.write_text("a")
    before = source_identity(tmp_path)
    source.write_text("b")
    after = source_identity(tmp_path)
    assert before["revision"] == after["revision"]
    assert before["content_hash"] != after["content_hash"]


def test_malformed_bytecode_fails_closed():
    with pytest.raises(ValueError):
        parse_class(b"\xca\xfe\xba\xbe")


@pytest.mark.parametrize("actual", [{}, {"site": 2}, {"site": 1, "extra": 1}])
def test_missing_duplicate_and_extra_hooks_rejected(actual):
    from compat_java import verify_hooks

    assert verify_hooks({"site": 1}, actual)
    assert not verify_hooks({"site": 1}, {"site": 1})


def test_cache_invalidates_on_unmapped_edit_and_lost_evidence(tmp_path, monkeypatch):
    import runtime_tests
    from compat import run_tests

    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    subprocess.run(
        [
            "git",
            "-c",
            "user.name=Test",
            "-c",
            "user.email=test@example.invalid",
            "commit",
            "--allow-empty",
            "-qm",
            "fixture",
        ],
        cwd=tmp_path,
        check=True,
    )
    (tmp_path / ".gitignore").write_text("artifacts/\n")
    (tmp_path / "scripts").mkdir()
    (tmp_path / "scripts/compat_classfile.py").write_text("fixture")
    calls = []

    def fake_step(name, command, timeout, root, output):
        calls.append(name)
        (output / f"{name}.log").write_text("passed")
        return {"name": name, "status": "passed", "log": f"{name}.log"}

    monkeypatch.setattr(runtime_tests, "run_step", fake_step)
    profile, snapshot = {"id": "fixture"}, {"content": "first"}
    first = run_tests(tmp_path, profile, snapshot, False)
    assert all(t["status"] == "passed" for t in first)
    second = run_tests(tmp_path, profile, snapshot, False)
    assert second[0]["status"] == "cached"
    assert second[1]["status"] == "passed"  # Hooks must never be cached.
    assert len(calls) == 3
    Path(first[0]["log"]).unlink()
    assert run_tests(tmp_path, profile, snapshot, False)[0]["status"] == "passed"
    (tmp_path / "unknown-extension.lua").write_text("changed")
    assert run_tests(tmp_path, profile, snapshot, False)[0]["status"] == "passed"
    assert run_tests(tmp_path, profile, {"content": "new game"}, False)[0]["status"] == "passed"
    assert run_tests(tmp_path, profile, snapshot, True)[0]["status"] == "passed"
