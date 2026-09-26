import sqlite3
import struct

import pytest
from fastapi.testclient import TestClient

from observer.player_keys import decode_player_keys, item_types
from observer.saved_api import create_saved_app
from observer.saved_map import read_positions
from observer.vehicle_flags import decode_vehicle


def pack(fmt, *values):
    return struct.pack(">" + fmt, *values)


def string(value):
    data = value.encode()
    return pack("h", len(data)) + data


def vehicle(model="Base.SmallCar", key=123, hotwired=False, broken=False, x=3.0, y=3.0):
    prefix = b"\x01\x21" + pack("fffffi", 0, 0, x, y, 0, 0) + b"\0" + bytes(20)
    base = string(model) + bytes(4 + 1 + 24) + pack("i", key) + bytes(7) + pack("h", 0)
    tail = bytes([0, hotwired, broken, 0]) + bytes(26) + b"\0" + bytes(8) + string("") + bytes(8)
    tail += bytes(3) + bytes(4) + bytes(2) + pack("i", 1) + b"\0"
    return prefix + base + tail


def item(registry, payload=b"", header=0):
    # Minimal InventoryItem base; registry 10 is a car key, 20 a container.
    value = pack("hbIB", registry, -1, 456, header) + payload
    return pack("i", len(value)) + value


def inventory(items):
    return (
        string("inventory")
        + b"\1"
        + pack("h", len(items))
        + b"".join(pack("i", 1) + i for i in items)
        + b"\0"
        + pack("i", 50)
    )


def player(items):
    prefix = b"\x01\x01" + bytes(24) + bytes(2)  # No mod data or descriptor.
    visual = bytes(1 + 3 + 4) + string("") + b"\0"
    return prefix + visual + inventory(items) + bytes(16)  # Unrelated character data follows.


def test_vehicle_layout_hotwire_wreck_and_rejection():
    assert decode_vehicle(vehicle(hotwired=True), 249, 3, 3)["hotwired"]
    assert not decode_vehicle(vehicle(broken=True), 249, 3, 3)["hotwired"]
    assert decode_vehicle(vehicle("Base.CarSmashedFront"), 249, 3, 3)["wreck"]
    assert decode_vehicle(vehicle("Base.CarBurnt"), 249, 3, 3)["wreck"]
    assert decode_vehicle(vehicle(), 249, 3, 3)["key_id"] == 123
    for blob, version, x in (
        (vehicle()[:-1], 249, 3),
        (vehicle() + b"\0", 249, 3),
        (vehicle(), 250, 3),
        (vehicle(), 249, 4),
    ):
        with pytest.raises(ValueError):
            decode_vehicle(blob, version, x, 3)


def test_car_keys_nested_containers_and_not_embedded_bytes():
    key = item(10, pack("iB", 123, 1))
    unrelated = item(30, key)  # Car-key-looking bytes in another item are not a key.
    keyring = item(20, bytes(8) + inventory([key]))
    backpack = item(20, bytes(8) + inventory([keyring, unrelated]))
    types = {10: "key", 20: "container"}
    assert decode_player_keys(player([backpack]), 249, types) == {123}
    assert decode_player_keys(player([unrelated]), 249, types) == set()
    assert decode_player_keys(player([item(10, pack("iB", -1, 1))]), 249, types) == set()
    for blob in (player([backpack])[:50], player([item(10, pack("iB", 123, 1) + b"X")])):
        with pytest.raises(ValueError):
            decode_player_keys(blob, 249, types)
    with pytest.raises(ValueError):
        decode_player_keys(player([key]), 248, types)
    # Size-delimited entity components and customized keyring names are supported.
    custom_base = pack("I", 8 | 0x4000000) + string("My keys") + b"\1" + pack("i", 4) + b"ABCD"
    custom = item(20, custom_base + bytes(8) + inventory([key]), header=64)
    assert decode_player_keys(player([custom]), 249, types) == {123}


def test_registry_uses_container_definitions_without_executing_lua(tmp_path):
    scripts = tmp_path / "scripts/generated/items"
    scripts.mkdir(parents=True)
    (scripts / "container.txt").write_text(
        "module Base {\n item KeyRing { ItemType = base:container, }\n item Bag { ItemType = base:container, }\n}"
    )
    dictionary = tmp_path / "WorldDictionaryReadable.lua"
    dictionary.write_text(
        'registryID = 10, fulltype = "Base.CarKey",\nregistryID = 20, fulltype = "Base.KeyRing",\nregistryID = 30, fulltype = "Base.Key1",'
    )
    assert item_types(dictionary, tmp_path / "scripts") == {10: "key", 20: "container"}


def test_api_filters_keys_hotwire_wreck_coverage_and_hides_key_ids(tmp_path):
    app = create_saved_app(
        data=tmp_path / "data", save=tmp_path / "save", frontend=tmp_path / "missing", polling=False
    )
    state = app.state.saved_map
    records = []
    for ident, model, key, hotwire, x in [
        (1, "Base.Car", 123, False, 3),
        (2, "Base.Car", 234, True, 4),
        (3, "Base.CarBurnt", 345, False, 5),
        (4, "Base.Car", 456, False, 6),
        (5, "Base.Car", 123, False, 40),
    ]:
        records.append(
            dict(
                id=ident,
                x=x,
                y=3,
                label=model,
                **decode_vehicle(vehicle(model, key, hotwire, x=x), 249, x, 3),
            )
        )
    records.append({"id": 6, "x": 7, "y": 3, "label": "Old unclassified cache entry"})
    state.publish("vehicles", records, 1, 1)
    state.publish("coverage:akryllax", [[0, 0, 3]], 1, 1)
    state.publish("car_keys", [123], 1, 1)
    with TestClient(app) as client:
        query = "/api/v1/map/features?x=16&y=16&radius=64"
        result = client.get(query).json()["vehicles"]
        assert {v["id"]: v["category"] for v in result} == {1: "keyed", 2: "hotwired", 3: "wreck"}
        assert all("key_id" not in v for v in result)
        assert "payload" not in str(client.get("/api/v1/world").json()["sources"])
        assert not client.get(query + "&observer=eric").json()["vehicles"]
        state.publish("car_keys", [], 2, 2)
        assert {v["id"] for v in client.get(query).json()["vehicles"]} == {2, 3}
        state.publish("car_keys", [123], 3, 3)
        state.read_source("car_keys", tmp_path / "missing.db", state.read_car_keys)
        assert {v["id"] for v in client.get(query).json()["vehicles"]} == {2, 3}
        assert state.sources["car_keys"]["error"]


def test_read_only_vehicle_db_and_living_player_keys(tmp_path):
    app = create_saved_app(
        data=tmp_path / "data", save=tmp_path, frontend=tmp_path / "missing", polling=False
    )
    state = app.state.saved_map
    scripts = tmp_path / "scripts/generated/items"
    scripts.mkdir(parents=True)
    (scripts / "container.txt").write_text("module Base {\n item KeyRing { ItemType = base:container, }\n}")
    state.scripts = tmp_path / "scripts"
    (tmp_path / "WorldDictionaryReadable.lua").write_text(
        'registryID = 10, fulltype = "Base.CarKey",\nregistryID = 20, fulltype = "Base.KeyRing",'
    )
    with sqlite3.connect(tmp_path / "players.db") as db:
        db.execute("CREATE TABLE networkPlayers(worldversion,data,isDead)")
        for key, dead in [(123, 0), (456, 1)]:
            db.execute(
                "INSERT INTO networkPlayers VALUES(249,?,?)", (player([item(10, pack("iB", key, 1))]), dead)
            )
    assert state.read_car_keys(tmp_path / "players.db") == [123]
    with sqlite3.connect(tmp_path / "vehicles.db") as db:
        db.execute("CREATE TABLE vehicles(id,x,y,worldversion,data)")
        db.executemany("INSERT INTO vehicles VALUES(?,3,3,249,?)", [(1, vehicle()), (2, b"unsupported")])
    before = (tmp_path / "vehicles.db").read_bytes()
    assert [r["id"] for r in read_positions(tmp_path / "vehicles.db", "vehicles")] == [1]
    assert (tmp_path / "vehicles.db").read_bytes() == before
    state.close()
