"""Exercise scenario configuration and the archive boundary, without live services."""

import importlib.util
from pathlib import Path
from types import SimpleNamespace
import pytest

spec = importlib.util.spec_from_file_location(
    "scenario_ops", Path(__file__).parents[1] / "scripts/scenario_ops.py"
)
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


def test_calm_configuration_preserves_qol_and_controlled_spawn_mechanism():
    source = (
        "SandboxVars = {\nZombies = 4,\nGlobal = 10,\nMinutesPerPage = 0.01,\n"
        + "".join(
            f"{k} = {v},\n"
            for k, v in {
                "PopulationMultiplier": ".65",
                "PopulationStartMultiplier": "1",
                "PopulationPeakMultiplier": "1.5",
                "RespawnHours": "72",
                "Helicopter": "2",
                "MetaEvent": "2",
                "SleepingEvent": "2",
                "SurvivorHouseChance": "3",
                "VehicleStoryChance": "3",
                "ZoneStoryChance": "3",
                "Transmission": "1",
                "Mortality": "1",
            }.items()
        )
        + "}\n"
    )
    output = ops.scenario_sandbox(source)
    assert "PopulationMultiplier = 0.0," in output
    assert "Zombies = 4," in output
    assert "Global = 10," in output and "MinutesPerPage = 0.01," in output
    assert "Transmission = 4," in output and "Mortality = 7," in output
    with pytest.raises(RuntimeError, match="already contains"):
        ops.scenario_sandbox(output)


def test_missing_setting_does_not_silently_create_partial_calm_world():
    with pytest.raises(RuntimeError, match="Expected exactly one"):
        ops.scenario_sandbox("SandboxVars = {}")


def test_ini_is_updated_without_exposing_or_replacing_secrets():
    source = "# Config\nPassword=secret\nDefaultPort=16271\n"
    result = ops.replace_ini(source, {"DefaultPort": 16281, "RCONPort": 27035})
    assert result == "# Config\nPassword=secret\nDefaultPort=16281\nRCONPort=27035\n"


def test_reset_requires_actual_two_client_acceptance_before_any_stop(tmp_path):
    called = []
    manager = SimpleNamespace(ROOT=tmp_path, backup=lambda: called.append("backup"))
    with pytest.raises(RuntimeError, match="Missing recorded"):
        ops.archive_reset(manager)
    assert called == []
