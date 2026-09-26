import io
import json
import struct
import zipfile

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from observer.app import create_app
from observer.collector import Collector
from observer.formats import decode_coverage, decode_public_markers
from observer.models import Batch, Bounds
from observer.store import Store


@pytest.fixture
def store(tmp_path):
    result = Store(tmp_path / "db.sqlite", "AKR_Exploratory")
    yield result
    result.close()


def batch(sequence=1, observer="eric", **fields):
    return Batch.model_validate(
        dict(
            version=1,
            world="AKR_Exploratory",
            session="test",
            sequence=sequence,
            observed_at=10000 + sequence,
            observer=observer,
            kind="observation",
            **fields,
        )
    )


def obj(id="wall:1", **fields):
    return dict(id=id, kind="wall", label="Wooden wall", x=1.5, y=2.5, z=0, **fields)


def tile(objects=None, x=1, y=2):
    return {"x": x, "y": y, "z": 0, "objects": objects if objects is not None else [obj()]}


def test_last_seen_and_group_absence(store):
    store.ingest(batch(tiles=[tile()]))
    assert store.element("wall:1")["label"] == "Wooden wall"
    store.ingest(batch(2, observer="akryllax", tiles=[tile([])]))
    assert store.element("wall:1") is None
    assert store.element("wall:1", "eric") is not None
    assert store.objects((0, 0, 5, 5), 0)[0] == []
    assert store.objects((0, 0, 5, 5), 0, "eric")[0]


def test_moving_vehicle_not_duplicated_or_deleted_from_old_tile(store):
    vehicle = {**obj("vehicle:2"), "kind": "vehicle"}
    store.ingest(batch(tiles=[tile([vehicle])]))
    vehicle = {**vehicle, "x": 10.5}
    store.ingest(batch(2, observer="akryllax", tiles=[tile([vehicle], x=10)]))
    assert not store.objects((0, 0, 5, 5), 0)[0]
    assert len(store.objects((0, 0, 20, 5), 0)[0]) == 1
    store.ingest(batch(3, tiles=[tile([])]))
    assert store.element("vehicle:2")["x"] == 10.5


def test_inspection_is_separate_and_requires_observation(store):
    inspection = {
        "id": "wall:1",
        "category": "container",
        "items": [{"type": "Base.Nails", "name": "Nails", "count": 5}],
    }
    store.ingest(batch(inspections=[inspection]))
    assert store.element("wall:1") is None
    store.ingest(batch(2, tiles=[tile()], inspections=[inspection]))
    store.ingest(batch(3, tiles=[tile()]))
    assert store.element("wall:1")["inspections"][0]["items"][0]["count"] == 5
    assert store.element("wall:1")["inspections"][0]["observed_at"] == 10002


def test_reject_private_fields_and_marker_leaks():
    with pytest.raises(ValidationError):
        batch(tiles=[tile([{**obj(), "inventory": ["secret"]}])])
    with pytest.raises(ValidationError):
        batch(markers=[{"id": "1", "author": "eric", "label": "private", "x": 0, "y": 0, "public": False}])


def test_marker_revocation_cannot_be_undone_by_old_save(store):
    marker = {"id": "marker:1", "author": "eric", "label": "House", "x": 1, "y": 2, "public": True}
    store.replace_markers([marker])
    store.ingest(Batch.model_validate({**batch().model_dump(), "kind": "markers", "markers": []}))
    store.replace_markers([marker])
    assert store.status()["markers"] == []


def test_idempotence_restart_and_gap(tmp_path):
    p = tmp_path / "persist.sqlite"
    first = Store(p, "AKR_Exploratory")
    assert first.ingest(batch(tiles=[tile()]))
    assert not first.ingest(batch(tiles=[tile([])]))
    first.close()
    second = Store(p, "AKR_Exploratory")
    assert second.element("wall:1")
    second.ingest(batch(3))
    assert second.status()["gap"]["expected"] == 2
    second.close()


def test_invalid_tile_rolls_back_batch(store):
    with pytest.raises(ValueError):
        store.ingest(batch(tiles=[tile(), tile([obj("bad")], x=5)]))
    assert store.element("wall:1") is None
    assert store.ingest(batch(tiles=[tile()]))


def test_partial_journal_and_replay(store, tmp_path):
    spool = tmp_path / "spool"
    spool.mkdir()
    path = spool / "observer-0.txt"
    header = json.dumps({"format": "zomboid-observer-journal-v1", "created_at": 1, "sequence": 1}) + "\n"
    record = batch(tiles=[tile()]).model_dump_json()
    path.write_text(header + record[:40])
    collector = Collector(store, spool, None)
    collector.poll()
    assert store.element("wall:1") is None
    path.write_text(header + record + "\n")
    collector.poll()
    assert store.element("wall:1")
    revision = store.revision()
    collector.poll()
    assert store.revision() == revision
    # Rotation can retain the same inode, but a changed header identifies a new generation.
    path.write_text(
        json.dumps({"format": "zomboid-observer-journal-v1", "created_at": 2, "sequence": 2})
        + "\n"
        + batch(2, tiles=[tile([])]).model_dump_json()
        + "\n"
    )
    collector.poll()
    assert store.element("wall:1") is None


def test_exploration_bit_order_origin_and_wrong_inner_name():
    bounds = Bounds(min_x=-1, min_y=2, max_x=-1, max_y=2)
    raw = struct.pack(">i", 249) + bytes([0b11100100]) + bytes(15)
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("eric", raw)
    cells = list(decode_coverage(out.getvalue(), "eric", bounds))
    assert cells == [(-224, 512, 1), (-192, 512, 2), (-160, 512, 3)]
    with pytest.raises(KeyError):
        list(decode_coverage(out.getvalue(), "AnitaLottaFarm", bounds))


def test_only_public_shared_markers_decode():
    data = bytes.fromhex(
        "574d5359000000f900000002000000000200000001000e416e6974614c6f7474614661726d0c010008416b72796c6c617801460a98014618f0cf3f0000003f0000003f2a7efa00000000272f7cff000200084d656443726f7373000000020008416b72796c6c617801014628372c46191be43f0000003f0000003f2a7efa00000000202020ff00020005486f757365"
    )
    result = decode_public_markers(data)
    assert len(result) == 1 and result[0]["label"] == "House"
    with pytest.raises(ValueError):
        decode_public_markers(data[:-1])


def test_public_api_read_only_and_offline_status(tmp_path):
    app = create_app(data=tmp_path, polling=False)
    with TestClient(app) as client:
        app.state.store.ingest(batch(tiles=[tile()]))
        assert client.get("/healthz").status_code == 200
        assert client.get("/api/v1/world").json()["online"] is False
        assert client.get("/api/v1/chunks?x=1&y=2").json()["objects"]
        assert client.get("/api/v1/elements/wall:1").json()["inspections"] == []
        assert client.post("/api/v1/chunks", json={}).status_code == 405
        assert client.get("/api/v1/chunks?x=1&y=2&radius=99999").status_code == 422
        assert client.get("/api/v1/elements/missing").status_code == 404


def test_heartbeat_marks_players_offline_without_deleting_observations(store):
    b = batch(tiles=[tile()], players=[{"name": "eric", "x": 1, "y": 2, "z": 0}])
    store.ingest(Batch.model_validate({**b.model_dump(), "kind": "heartbeat"}))
    assert store.status()["players"][0]["online"] is False
    assert store.element("wall:1")


def test_scene_revisions_and_separate_vehicle_compartments(store):
    store.ingest(batch(tiles=[tile()]))
    before = store.status()
    store.ingest(Batch.model_validate({**batch(2).model_dump(), "kind": "heartbeat"}))
    after = store.status()
    assert after["revision"] > before["revision"]
    assert after["scene_revision"] == before["scene_revision"]
    assert after["coverage_revision"] == before["coverage_revision"]
    store.ingest(
        batch(
            3,
            inspections=[
                {"id": "wall:1", "category": "container", "slot": "Trunk", "items": []},
                {
                    "id": "wall:1",
                    "category": "container",
                    "slot": "SeatFrontLeft",
                    "items": [{"type": "Base.Nails", "name": "Nails", "count": 3}],
                },
            ],
        )
    )
    assert len(store.element("wall:1")["inspections"]) == 2


def test_older_session_cannot_restore_revoked_markers_or_player_positions(store):
    marker = {"id": "marker:1", "author": "eric", "label": "House", "x": 1, "y": 2, "public": True}
    store.ingest(
        Batch.model_validate({**batch().model_dump(), "kind": "markers", "markers": [], "observed_at": 20000})
    )
    store.ingest(
        Batch.model_validate(
            {**batch().model_dump(), "session": "old", "kind": "markers", "markers": [marker]}
        )
    )
    assert not store.status()["markers"]
    store.ingest(
        Batch.model_validate(
            {**batch(2).model_dump(), "kind": "heartbeat", "observed_at": 20001, "players": []}
        )
    )
    store.ingest(
        Batch.model_validate(
            {
                **batch(2).model_dump(),
                "session": "old",
                "kind": "heartbeat",
                "players": [{"name": "eric", "x": 1, "y": 2, "z": 0}],
            }
        )
    )
    assert not store.status()["players"]


def test_replaced_container_does_not_inherit_old_cargo(store):
    crate = {**obj("tile:1:2:0:1"), "kind": "container", "label": "Crate"}
    store.ingest(
        batch(
            tiles=[tile([crate])],
            inspections=[
                {
                    "id": crate["id"],
                    "category": "container",
                    "items": [{"type": "Base.Nails", "name": "Nails", "count": 5}],
                }
            ],
        )
    )
    store.ingest(batch(2, observer="akryllax", tiles=[tile([])]))
    store.ingest(batch(3, observer="akryllax", tiles=[tile([crate])]))
    assert store.element(crate["id"])["inspections"] == []
    assert store.element(crate["id"], "eric")["inspections"]
