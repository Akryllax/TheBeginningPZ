from uuid import uuid4

import pytest
from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient

from observer.drawings import Drawings, Stroke, install_drawings


def test_shared_updates_removal_expiry_and_ownership():
    now = [1000]
    hub = Drawings(clock=lambda: now[0])
    owner, key = uuid4(), uuid4()
    value = Stroke(id=key, owner=owner, points=[(10, 20)])
    hub.put(value)
    previous, update = hub.changes({})
    assert update["strokes"][0]["points"] == [(10, 20)]
    assert "owner" not in update["strokes"][0]
    value.points.append((11, 22))
    hub.put(value)
    current, update = hub.changes(previous)
    assert len(update["strokes"][0]["points"]) == 2
    assert hub.changes({})[1]["strokes"]  # New/reconnecting viewers receive existing strokes.
    with pytest.raises(HTTPException) as exc:
        hub.put(Stroke(id=key, owner=uuid4(), points=[(1, 2)]))
    assert exc.value.status_code == 403
    hub.clear(uuid4())
    assert hub.changes(current)[1] == {"strokes": [], "removed": []}
    now[0] += 301
    assert hub.changes(current)[1]["removed"] == [str(key)]
    hub.put(value)
    current, _ = hub.changes({})
    hub.clear(owner)
    assert hub.changes(current)[1]["removed"] == [str(key)]
    assert not Drawings().strokes  # Restart starts empty.


def test_api_limits_and_origin():
    app = FastAPI()
    install_drawings(app)
    with TestClient(app) as client:
        owner = str(uuid4())
        payload = {"id": str(uuid4()), "owner": owner, "points": [[10, 20]]}
        assert client.post("/api/v1/drawings", json=payload).status_code == 200
        assert (
            client.post(
                "/api/v1/drawings", json=payload, headers={"Origin": "https://other.test"}
            ).status_code
            == 403
        )
        for points in ([], [[200001, 0]], [[0, 0]] * 1025, [["NaN", 0]]):
            assert (
                client.post("/api/v1/drawings", json={**payload, "points": points}).status_code
                == 422
            )
        assert client.post("/api/v1/drawings", content=b" " * 65537).status_code == 413
        for _ in range(31):
            payload["id"] = str(uuid4())
            assert client.post("/api/v1/drawings", json=payload).status_code == 200
        payload["id"] = str(uuid4())
        assert client.post("/api/v1/drawings", json=payload).status_code == 429
        assert client.delete("/api/v1/drawings/" + owner).status_code == 200
        assert not app.state.drawings.strokes
