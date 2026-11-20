"""Layout checking: a port of server/src/layout.ts.

Takes the JSON the Android app writes (layout schema v2, docs/layout-schema-v2.md) and turns it into the
normalised shape the dashboard stores. Anything malformed is rejected with a message, never repaired.

Three details have to match the Node server to the last character, because other code depends on them:
  * the rejection texts (`Invalid layout file: <path> — <message>`), which the app and dashboard show;
  * the generated names, ids, defaults and the 0-360 rotation;
  * the content hash, which is sha256 of `JSON.stringify(layout)`. See jsutil.py for why that needs care.
"""

import hashlib
import uuid

from .http import MISSING, check_number, js_type
from .jsutil import js_mod, js_number_text, js_slice, js_stringify, js_trim

# Sizes are only a fallback for a file that omits them; the app always writes real dimensions.
# The first six are the app's current asset library. The last five were retired from the app but are
# kept so older plans and the demo data still load.
ASSET_DEFAULTS = {
    "GUNSHIP": (13.52, 19.7, 6.05),
    "K131": (3.45, 6.24, 5.45),
    "K131_TAKEOFF": (3.31, 6.24, 3.54),
    "MILITARY_VEHICLE": (1.72, 4.0, 3.1),
    "OFFROAD_VEHICLE": (2.43, 4.66, 3.54),
    "STR_MGS": (3.44, 7.63, 3.47),
    "TENT": (4.0, 6.0, 2.5),
    "TRUCK": (2.5, 8.0, 3.2),
    "CAR": (1.8, 4.5, 1.5),
    "ANTENNA": (1.5, 1.5, 8.0),
    "TRENCH": (1.2, 10.0, 1.8),
}
MAX_OBJECTS = 500
DEFAULT_NAME = "Untitled plan"


def new_uuid() -> str:
    """Overridable so tests can make generated ids (and therefore hashes) predictable."""
    return str(uuid.uuid4())


class _Rejected(Exception):
    def __init__(self, path: list, message: str):
        self.path, self.message = path, message


def _number(value, path, **rules) -> float:
    error, number = check_number(value, **rules)
    if error:
        raise _Rejected(path, error)
    return number


def _string(value, path, *, min_len=None, max_len=None) -> str:
    if not isinstance(value, str):
        raise _Rejected(path, f"Invalid input: expected string, received {js_type(value)}")
    if min_len is not None and len(value) < min_len:
        raise _Rejected(path, f"Too small: expected string to have >={min_len} characters")
    if max_len is not None and len(value) > max_len:
        raise _Rejected(path, f"Too big: expected string to have <={max_len} characters")
    return value


def _object(value, path) -> dict:
    if not isinstance(value, dict):
        raise _Rejected(path, f"Invalid input: expected object, received {js_type(value)}")
    return value


def _get(obj: dict, key: str):
    return obj.get(key, MISSING)


def _optional(value, check):
    """zod's .optional(): a missing key (undefined) is fine, anything else must pass."""
    return None if value is MISSING else check(value)


def _lat(value, path):
    return _number(value, path, ge=-90, le=90)


def _lng(value, path):
    return _number(value, path, ge=-180, le=180)


def _meters(value, path):
    return _number(value, path, ge=-100_000, le=100_000)


def _size(value, path):
    return _number(value, path, gt=0, le=1000)


def _nullish_number(value, path, **rules):
    """zod's .nullish(): missing or null is fine."""
    if value is MISSING or value is None:
        return None
    return _number(value, path, **rules)


def _check_object(raw, path) -> dict:
    o = _object(raw, path)
    out = {}
    if (v := _get(o, "id")) is not MISSING:
        out["id"] = _string(v, path + ["id"], max_len=80)
    asset = _get(o, "assetType")
    if not (isinstance(asset, str) and asset in ASSET_DEFAULTS):
        listed = "|".join(f'"{t}"' for t in ASSET_DEFAULTS)
        raise _Rejected(path + ["assetType"], f"Invalid option: expected one of {listed}")
    out["assetType"] = asset
    if (v := _get(o, "name")) is not MISSING:
        out["name"] = _string(v, path + ["name"], max_len=80)
    out["x"] = _meters(_get(o, "x"), path + ["x"])
    out["z"] = _meters(_get(o, "z"), path + ["z"])
    rotation = _get(o, "rotationDegrees")
    out["rotationDegrees"] = 0.0 if rotation is MISSING else _number(rotation, path + ["rotationDegrees"], ge=-3600, le=3600)
    for key in ("widthMeters", "lengthMeters", "heightMeters"):
        if (v := _get(o, key)) is not MISSING:
            out[key] = _size(v, path + [key])
    return out


def _check_layout(raw) -> dict:
    layout = _object(raw, [])
    out = {}
    if (v := _get(layout, "version")) is not MISSING:
        _number(v, ["version"], integer=True)
    if (v := _get(layout, "id")) is not MISSING:
        out["id"] = _string(v, ["id"], max_len=80)
    if (v := _get(layout, "name")) is not MISSING:
        out["name"] = _string(v, ["name"], min_len=1, max_len=120)

    rb = _object(_get(layout, "regionBounds"), ["regionBounds"])
    out["regionBounds"] = {
        "northLatitude": _lat(_get(rb, "northLatitude"), ["regionBounds", "northLatitude"]),
        "southLatitude": _lat(_get(rb, "southLatitude"), ["regionBounds", "southLatitude"]),
        "eastLongitude": _lng(_get(rb, "eastLongitude"), ["regionBounds", "eastLongitude"]),
        "westLongitude": _lng(_get(rb, "westLongitude"), ["regionBounds", "westLongitude"]),
    }

    an = _object(_get(layout, "anchor"), ["anchor"])
    out["anchor"] = {
        "latitude": _lat(_get(an, "latitude"), ["anchor", "latitude"]),
        "longitude": _lng(_get(an, "longitude"), ["anchor", "longitude"]),
        "headingDegrees": _nullish_number(_get(an, "headingDegrees"), ["anchor", "headingDegrees"], ge=-3600, le=3600),
        "horizontalAccuracyMeters": _nullish_number(
            _get(an, "horizontalAccuracyMeters"), ["anchor", "horizontalAccuracyMeters"], ge=0, le=100_000
        ),
    }

    objects = _get(layout, "objects")
    if not isinstance(objects, list):
        raise _Rejected(["objects"], f"Invalid input: expected array, received {js_type(objects)}")
    if len(objects) > MAX_OBJECTS:  # zod reports the size problem before any problem inside the items
        raise _Rejected(["objects"], f"Too big: expected array to have <={MAX_OBJECTS} items")
    out["objects"] = [_check_object(item, ["objects", str(i)]) for i, item in enumerate(objects)]
    return out


def parse_layout(raw) -> tuple[dict | None, str | None]:
    """Returns (normalised layout, None) or (None, message). Mirrors parseLayout() in layout.ts."""
    try:
        l = _check_layout(raw)
    except _Rejected as e:
        where = ".".join(e.path) if e.path else "layout"
        return None, f"Invalid layout file: {where} — {e.message}"

    b = l["regionBounds"]
    if b["northLatitude"] < b["southLatitude"]:
        return None, "Invalid layout file: region north edge is below the south edge"
    if b["westLongitude"] > b["eastLongitude"]:
        return None, "Invalid layout file: region crosses the antimeridian or west is east of east"
    if b["northLatitude"] == b["southLatitude"] or b["westLongitude"] == b["eastLongitude"]:
        return None, "Invalid layout file: region has no area"
    a = l["anchor"]
    if a["latitude"] < b["southLatitude"] or a["latitude"] > b["northLatitude"] or a["longitude"] < b["westLongitude"] or a["longitude"] > b["eastLongitude"]:
        return None, "Invalid layout file: the origin lies outside the region"

    seen: set[str] = set()
    objects = []
    for i, o in enumerate(l["objects"]):
        width, length, height = ASSET_DEFAULTS[o["assetType"]]
        given = o.get("id")
        obj_id = given if given and given not in seen else new_uuid()
        seen.add(obj_id)
        obj_id = js_slice(obj_id, 80)
        asset = o["assetType"]
        name = js_trim(o["name"]) if "name" in o else ""
        name = js_slice(name or f"{asset[0]}{asset[1:].lower()} {i + 1}", 80)
        objects.append(
            {
                "id": obj_id,
                "assetType": asset,
                "name": name,
                "x": o["x"],
                "z": o["z"],
                "rotationDegrees": js_mod(js_mod(o["rotationDegrees"], 360) + 360, 360),
                "widthMeters": o.get("widthMeters", width),
                "lengthMeters": o.get("lengthMeters", length),
                "heightMeters": o.get("heightMeters", height),
            }
        )
    return (
        {
            "version": 2,
            "id": l["id"] if "id" in l else new_uuid(),
            "name": l.get("name", DEFAULT_NAME),
            "regionBounds": b,
            "anchor": {
                "latitude": a["latitude"],
                "longitude": a["longitude"],
                "headingDegrees": 0.0 if a["headingDegrees"] is None else a["headingDegrees"],
                "horizontalAccuracyMeters": a["horizontalAccuracyMeters"],
            },
            "objects": objects,
        },
        None,
    )


def layout_text(layout: dict) -> str:
    """The exact text JSON.stringify gives for the layout. This is what is stored and what is hashed."""
    return js_stringify(layout)


def content_hash(layout: dict) -> str:
    """Fingerprint of the exact content a reviewer decides on: first 16 hex characters of sha256."""
    return hashlib.sha256(layout_text(layout).encode("utf-8")).hexdigest()[:16]
