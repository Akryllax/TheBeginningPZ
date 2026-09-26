"""Strict public-data allowlist. Internal game tables never cross this boundary."""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

Name = Annotated[str, StringConstraints(min_length=1, max_length=128)]
Coordinate = Annotated[float, Field(ge=-200_000, le=200_000, allow_inf_nan=False)]
Level = Annotated[int, Field(ge=-32, le=64)]
Kind = Literal[
    "floor",
    "wall",
    "door",
    "window",
    "roof",
    "stairs",
    "fence",
    "tree",
    "vegetation",
    "furniture",
    "container",
    "vehicle",
    "zombie",
    "animal",
    "item",
    "unknown",
]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class VisibleState(Strict):
    open: bool | None = None
    broken: bool | None = None
    material: Annotated[str, StringConstraints(max_length=48)] | None = None
    variant: Annotated[str, StringConstraints(max_length=80)] | None = None


class Element(Strict):
    id: Name
    kind: Kind
    label: Name
    sprite: Annotated[str, StringConstraints(max_length=160)] = ""
    x: Coordinate
    y: Coordinate
    z: Level
    rotation: float = Field(default=0, ge=-360, le=360)
    width: float = Field(default=1, gt=0, le=32)
    depth: float = Field(default=1, gt=0, le=32)
    height: float = Field(default=1, gt=0, le=24)
    color: Annotated[str, StringConstraints(pattern=r"^#[0-9a-fA-F]{6}$")] | None = None
    state: VisibleState = Field(default_factory=VisibleState)


class Tile(Strict):
    x: int = Field(ge=-200_000, le=200_000)
    y: int = Field(ge=-200_000, le=200_000)
    z: Level
    objects: list[Element] = Field(default_factory=list, max_length=128)

    @field_validator("objects")
    @classmethod
    def unique_ids(cls, value):
        if len({x.id for x in value}) != len(value):
            raise ValueError("duplicate element IDs in tile")
        return value


class Item(Strict):
    type: Name
    name: Name
    count: int = Field(ge=1, le=100_000)
    condition: int | None = Field(default=None, ge=0, le=100_000)


class Part(Strict):
    name: Name
    condition: float = Field(ge=0, le=100)


class Inspection(Strict):
    id: Name
    category: Literal["container", "mechanics"]
    slot: Name = "main"
    items: list[Item] = Field(default_factory=list, max_length=2048)
    parts: list[Part] = Field(default_factory=list, max_length=128)


class Player(Strict):
    name: Name
    x: Coordinate
    y: Coordinate
    z: Level
    heading: float = Field(default=0, ge=-360, le=360)
    online: bool = True


class Marker(Strict):
    id: Name
    author: Name
    label: Name
    x: Coordinate
    y: Coordinate
    z: Level = 0
    public: Literal[True]
    color: Annotated[str, StringConstraints(pattern=r"^#[0-9a-fA-F]{6}$")] = "#e1b66f"


class Bounds(Strict):
    min_x: int
    min_y: int
    max_x: int
    max_y: int
    cell_size: Literal[256] = 256
    unit_size: Literal[32] = 32
    world_version: Literal[249] = 249


class Coverage(Strict):
    x: int
    y: int
    flags: int = Field(ge=0, le=3)


class Sighting(Strict):
    x: int = Field(ge=-200_000, le=200_000)
    y: int = Field(ge=-200_000, le=200_000)
    z: Level


class Batch(Strict):
    version: Literal[1]
    world: Name
    session: Name
    sequence: int = Field(ge=1)
    observed_at: int = Field(ge=0)
    observer: Name
    kind: Literal["observation", "heartbeat", "metadata", "markers"]
    tiles: list[Tile] = Field(default_factory=list, max_length=128)
    inspections: list[Inspection] = Field(default_factory=list, max_length=8)
    players: list[Player] = Field(default_factory=list, max_length=128)
    markers: list[Marker] = Field(default_factory=list, max_length=8192)
    coverage: list[Coverage] = Field(default_factory=list, max_length=4096)
    bounds: Bounds | None = None
    seen: list[Sighting] = Field(default_factory=list, max_length=1024)
