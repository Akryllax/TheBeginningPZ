"""Town label anchors from the installed map; never execute its annotation Lua."""

import json
import logging
import math
import re
from pathlib import Path

TOWN = re.compile(
    r'^\s*symbol\s*=\s*symbolsAPI:addUntranslatedText\(\s*"(MapLabel_[A-Za-z0-9]+)"'
    r'\s*,\s*"text-town"\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)',
    re.MULTILINE,
)


def read_city_labels(maps: Path):
    try:
        annotations = (maps / "worldmap-annotations.lua").read_text(encoding="utf-8-sig")
    except OSError:
        logging.getLogger(__name__).warning("City annotations unavailable; city labels disabled")
        return []
    try:
        translations = json.loads(
            (maps.parent.parent / "lua/shared/Translate/EN/MapLabel.json").read_text(
                encoding="utf-8-sig"
            )
        )
        if not isinstance(translations, dict):
            translations = {}
    except (OSError, ValueError):
        translations = {}
    labels = {}
    for key, x, y in TOWN.findall(annotations):
        x, y = int(x), int(y)
        if abs(x) > 200000 or abs(y) > 200000:
            continue
        fallback = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", key.removeprefix("MapLabel_"))
        name = translations.get(key, fallback)
        if not isinstance(name, str) or not name.strip() or len(name) > 100:
            name = fallback
        labels[key] = {"id": key, "label": name.title(), "x": x, "y": y}
    return list(labels.values())


def visible_city_labels(labels, known):
    # No hidden town names or coordinates are sent to the browser. Visited and
    # learned blocks both qualify, exactly as they do for terrain and places.
    return [
        label
        for label in labels
        if known.get((math.floor(label["x"] / 32) * 32, math.floor(label["y"] / 32) * 32))
    ]
