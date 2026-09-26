import json

from fastapi.testclient import TestClient

from observer.city_labels import read_city_labels
from observer.saved_api import create_saved_app


def annotation(key, x, y, style="text-town"):
    return f'symbol = symbolsAPI:addUntranslatedText("{key}", "{style}", {x}, {y})\n'


def test_installed_annotation_reader_uses_towns_and_translation_without_running_lua(tmp_path):
    maps = tmp_path / "media/maps/Muldraugh, KY"
    maps.mkdir(parents=True)
    (maps / "worldmap-annotations.lua").write_text(
        "error('This file must never be executed')\n"
        + annotation("MapLabel_WestPoint", 11654, 6864)
        + annotation("MapLabel_SaltRiver", 12511, 6734, "text-water-nofade")
        + annotation("MapLabel_Hospital", 12000, 2000, "text-building")
        + annotation("MapLabel_EchoCreek", 3589, 10952)
        + annotation("MapLabel_Invalid", 200001, 0)
    )
    translations = tmp_path / "media/lua/shared/Translate/EN/MapLabel.json"
    translations.parent.mkdir(parents=True)
    translations.write_text(json.dumps({"MapLabel_WestPoint": "WEST POINT"}))
    assert read_city_labels(maps) == [
        {"id": "MapLabel_WestPoint", "label": "West Point", "x": 11654, "y": 6864},
        {"id": "MapLabel_EchoCreek", "label": "Echo Creek", "x": 3589, "y": 10952},
    ]
    # A missing annotation file leaves custom/older maps usable, without guessed locations.
    assert read_city_labels(tmp_path) == []
    translations.write_text("invalid JSON")
    assert read_city_labels(maps)[0]["label"] == "West Point"


def test_coverage_never_exposes_unknown_city_names_and_tracks_each_observer(tmp_path, monkeypatch):
    maps = tmp_path / "maps"
    maps.mkdir()
    (maps / "worldmap-annotations.lua").write_text(
        annotation("MapLabel_Muldraugh", 31, 31)
        + annotation("MapLabel_Rosewood", 32, 32)
        + annotation("MapLabel_Ekron", -1, -1)
        + annotation("MapLabel_Louisville", 128, 128)
    )
    monkeypatch.setenv("OBSERVER_MAPS", str(maps))
    app = create_saved_app(data=tmp_path / "data", save=tmp_path / "save", polling=False)
    state = app.state.saved_map
    state.publish("coverage:akryllax", [[0, 0, 1], [-32, -32, 2]], 1, 1)
    state.publish("coverage:eric", [[32, 32, 3], [128, 128, 0]], 1, 1)

    def labels(client, observer=""):
        response = client.get("/api/v1/coverage", params={"observer": observer} if observer else {})
        assert response.status_code == 200
        snapshot = response.json()
        assert snapshot["revision"] == state.coverage_revision
        for label in snapshot["city_labels"]:
            assert any(
                flags and x <= label["x"] < x + 32 and y <= label["y"] < y + 32
                for x, y, flags in snapshot["cells"]
            )
        return {label["label"] for label in snapshot["city_labels"]}

    with TestClient(app) as client:
        assert labels(client) == {"Muldraugh", "Rosewood", "Ekron"}
        assert labels(client, "akryllax") == {"Muldraugh", "Ekron"}
        assert labels(client, "eric") == {"Rosewood"}
        assert labels(client, "unknown-player") == set()
        before = state.coverage_revision
        state.publish("coverage:akryllax", [[128, 128, 1]], 2, 2)
        assert state.coverage_revision > before
        assert labels(client, "akryllax") == {"Louisville"}
        assert labels(client) == {"Louisville", "Rosewood"}
        state.publish("coverage:akryllax", [], 3, 3)
        assert labels(client, "akryllax") == set()
