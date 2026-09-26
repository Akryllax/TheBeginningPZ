"""Create an explicitly labelled demo dataset, separate from the live world."""

import random
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from observer.models import Batch
from observer.store import Store

random.seed(4)
target = Path(sys.argv[1] if len(sys.argv) > 1 else "artifacts/demo")
store = Store(target / "observer.sqlite", "Observer_Demo")
now = int(time.time() * 1000) - 45_000
seq = store.db.execute("SELECT seq FROM streams WHERE session=?", ("demo",)).fetchone()
sequence = seq[0] if seq else 0


def emit(kind="observation", **fields):
    global sequence
    sequence += 1
    store.ingest(
        Batch.model_validate(
            dict(
                version=1,
                world="Observer_Demo",
                session="demo",
                sequence=sequence,
                observed_at=now + sequence,
                observer="eric",
                kind=kind,
                **fields,
            )
        )
    )


def element(x, y, kind, id=None, **fields):
    base = {
        "id": id or f"{kind}:{x}:{y}",
        "kind": kind,
        "label": kind.title(),
        "x": x + 0.5,
        "y": y + 0.5,
        "z": 0,
        "width": 1,
        "depth": 1,
        "height": 1,
        "rotation": 0,
    }
    base.update(fields)
    return base


tiles = []
for x in range(10792, 10843):
    for y in range(9827, 9870):
        objects = [element(x, y, "floor", color="#727f64")]
        if 10814 <= x <= 10821:
            objects[0]["color"] = "#666c65"
        if x in [10813, 10822]:
            objects[0]["color"] = "#b0b097"
        if x in [10817, 10818] and y % 5 < 3:
            objects[0]["color"] = "#b9b19a"
        inside = 10800 <= x <= 10810 and 9837 <= y <= 9848
        if inside:
            objects[0]["color"] = "#b9ab8c"
            if x in [10800, 10810] or y in [9837, 9848]:
                kind = "window" if (x in [10800, 10810] and y in [9840, 9845]) else "wall"
                if x == 10810 and y == 9843:
                    kind = "door"
                objects.append(element(x, y, kind, height=2.8, rotation=90 if x in [10800, 10810] else 0))
        if x == 10804 and y in [9838, 9839, 9840]:
            objects.append(
                element(x, y, "container", label="Kitchen cabinet", state={"variant": "cabinet"}, height=1.5)
            )
        if x == 10801 and y == 9842:
            objects.append(
                element(x, y, "furniture", label="Double bed", state={"variant": "bed"}, width=2, depth=2)
            )
        if x == 10807 and y == 9845:
            objects.append(
                element(
                    x, y, "furniture", label="Dining table", state={"variant": "table"}, width=1.8, depth=1.8
                )
            )
        if x == 10807 and y in [9844, 9846]:
            objects.append(element(x, y, "furniture", label="Dining chair", state={"variant": "chair"}))
        if x > 10826 and (x + y * 3) % 13 == 0:
            objects.append(element(x, y, "tree", width=2, depth=2, height=4 + random.random() * 2))
        if x < 10798 and (x * 3 + y) % 9 == 0:
            objects.append(element(x, y, "tree", width=2, depth=2, height=4.5))
        if x == 10818 and y == 9844:
            objects.append(
                element(
                    x,
                    y,
                    "vehicle",
                    id="vehicle:2",
                    label="Citr8 Chevalier Step Van",
                    sprite="Base.StepVan_Citr8",
                    width=2.2,
                    depth=5.7,
                    height=2.5,
                    color="#dcaf62",
                )
            )
        tiles.append({"x": x, "y": y, "z": 0, "objects": objects})
        if len(tiles) >= 64:
            emit(tiles=tiles)
            tiles = []
if tiles:
    emit(tiles=tiles)
emit(
    inspections=[
        {
            "id": "vehicle:2",
            "category": "container",
            "slot": "TruckBed",
            "items": [
                {"type": "Base.Nails", "name": "Nails", "count": 42},
                {"type": "Base.Hammer", "name": "Hammer", "count": 1, "condition": 9},
            ],
        },
        {
            "id": "vehicle:2",
            "category": "mechanics",
            "parts": [{"name": "Engine", "condition": 82}, {"name": "Battery", "condition": 64}],
        },
    ]
)
emit(
    kind="heartbeat",
    players=[
        {"name": "eric", "x": 10808, "y": 9851, "z": 0},
        {"name": "akryllax", "x": 10819, "y": 9848, "z": 0},
    ],
)
emit(
    kind="markers",
    markers=[{"id": "demo-home", "author": "eric", "label": "Home", "x": 10805, "y": 9842, "public": True}],
)
store.close()
print("Demo database:", target)
