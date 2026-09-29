"""Ensure inspection selects bounded classes and cannot extract arbitrary paths."""

import importlib.util
import io
from pathlib import Path
import zipfile
import pytest

spec = importlib.util.spec_from_file_location(
    "java_inspection", Path(__file__).parents[1] / "scripts/decompile_java.py"
)
inspect = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspect)


def fixture_jar():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name in [
            "a/Actor.class",
            "a/Actor$State.class",
            "a/ActorFactory.class",
            "../private.class",
        ]:
            archive.writestr(name, b"fixture")
    buffer.seek(0)
    return zipfile.ZipFile(buffer)


def test_selects_exact_class_and_nested_family_only():
    with fixture_jar() as archive:
        assert inspect.selected_entries(archive, ["a.Actor$State"]) == [
            "a/Actor$State.class",
            "a/Actor.class",
        ]


def test_invalid_or_missing_class_names_are_rejected():
    with fixture_jar() as archive:
        for name in ["../private", "a/Actor", "a.Actor;anything"]:
            with pytest.raises(ValueError, match="class name"):
                inspect.selected_entries(archive, [name])
        with pytest.raises(ValueError, match="not found"):
            inspect.selected_entries(archive, ["a.Missing"])
