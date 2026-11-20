"""Side-by-side check: sends the SAME scripted requests to the Node server and the Django server and diffs the answers.

Both servers must be running with their own empty temporary data folder and the same six users (see
docs/PHASE-2-PLANS.md, section "How to run the comparison"). Nothing here touches a real database.

    COMPARE_PASSWORD=... python scripts/compare_servers.py --node http://127.0.0.1:4100 --django http://127.0.0.1:4101

What is compared for every request: the status code, the JSON body (after ids and times are replaced by
placeholders, because each server makes its own), and the Cache-Control / Content-Type headers.
Python standard library only.
"""

import argparse
import base64
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
USERS = ["pat", "pia", "rex", "rhea", "ada", "aud"]
JSON_HEADERS = {"X-Requested-With": "dhruvar"}


# ---------------------------------------------------------------- layouts
def L(name="Alpha plan", *, ids=True, **override):
    layout = {
        "version": 2,
        "name": name,
        "regionBounds": {"northLatitude": 28.62, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2},
        "anchor": {"latitude": 28.61, "longitude": 77.21, "headingDegrees": 12.5, "horizontalAccuracyMeters": 4.2},
        "objects": [
            {"assetType": "MILITARY_VEHICLE", "x": 10, "z": -3.5, "rotationDegrees": -45},
            {"assetType": "GUNSHIP", "name": "  Héli 🚁 ", "x": 1e-7, "z": 0.1 + 0.2, "rotationDegrees": 725.5, "widthMeters": 13.52, "lengthMeters": 19.7, "heightMeters": 6.05},
        ],
    }
    if ids:
        layout["id"] = "layout-1"
        layout["objects"][0]["id"] = "o1"
        layout["objects"][1]["id"] = "o2"
    layout.update(override)
    return layout


def fixture_layout():
    path = os.path.join(os.path.dirname(__file__), "..", "..", "..", "DhruvAR", "server", "scripts", "fixtures", "app-layout-v2.json")
    with open(os.path.normpath(path), encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------- sample images
def make_png():
    import struct, zlib
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    raw = b"\x00" + bytes([200, 30, 30]) * 4
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 4, 1, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


PNG = make_png()
JPEG = base64.b64decode("/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=")
WEBP = base64.b64decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")
BOUNDARY = "----compareBoundary7MA4YWxkTrZu0gW"


def multipart(parts):
    """parts: (field name, file name or None, content type or None, bytes/text) -> body bytes."""
    out = b""
    for field, filename, ctype, content in parts:
        out += f"--{BOUNDARY}\r\n".encode()
        disposition = f'form-data; name="{field}"' + (f'; filename="{filename}"' if filename is not None else "")
        out += f"Content-Disposition: {disposition}\r\n".encode()
        if ctype:
            out += f"Content-Type: {ctype}\r\n".encode()
        out += b"\r\n" + (content if isinstance(content, bytes) else content.encode("utf-8")) + b"\r\n"
    return out + f"--{BOUNDARY}--\r\n".encode()


# ---------------------------------------------------------------- the script
class Step:
    def __init__(self, label, user, method, path, body=None, *, raw=None, save=None, headers=None, cookie=None, parts=None, rawbytes=None, binary=False):
        self.label, self.user, self.method, self.path, self.body = label, user, method, path, body
        self.raw, self.save, self.headers, self.cookie = raw, save, headers, cookie
        self.parts, self.rawbytes, self.binary = parts, rawbytes, binary


def build_script():
    S = []
    add = lambda *a, **k: S.append(Step(*a, **k))
    plans = "/api/plans"

    # --- signed out and wrong role
    add("signed out: list plans", None, "GET", plans)
    add("signed out: create plan", None, "POST", plans, {"layout": L()})
    add("signed out: audit", None, "GET", "/api/audit")
    add("signed out: audit verify", None, "GET", "/api/audit/verify")
    add("signed out: decision", None, "POST", "/api/plans/x/decision", {})
    for role in ("rex", "ada", "aud"):
        add(f"{role} cannot create a plan", role, "POST", plans, {"layout": L()})
    add("planner cannot verify the audit chain", "pat", "GET", "/api/audit/verify")
    add("reviewer cannot verify the audit chain", "rex", "GET", "/api/audit/verify")
    add("unsupported method on /plans", "pat", "DELETE", plans)
    add("unsupported method on /audit/verify", "ada", "POST", "/api/audit/verify", {})
    add("GET on a POST-only plan route", "pat", "GET", "/api/plans/x/withdraw")
    add("unknown route under /plans", "pat", "POST", "/api/plans/x/unknown", {})

    # --- creating plans
    add("create P1 (app layout, note trimmed)", "pat", "POST", plans, {"layout": L(), "note": "  first draft  "}, save=("P1", "id"))
    add("create P2 (generated ids and names, own name)", "pat", "POST", plans, {"layout": L("Layout name", ids=False), "name": "  Beta  "}, save=("P2", "id"))
    add("create P3 by another planner", "pia", "POST", plans, {"layout": L("Gamma")}, save=("P3", "id"))
    add("create P4 from the app's own export fixture", "pat", "POST", plans, {"layout": fixture_layout(), "note": ""}, save=("P4", "id"))
    add("create with no layout name", "pia", "POST", plans, {"layout": {k: v for k, v in L().items() if k != "name"}}, save=("P5", "id"))
    bad = [
        ("note too long", {"layout": L(), "note": "x" * 501}),
        ("note number", {"layout": L(), "note": 5}),
        ("note null", {"layout": L(), "note": None}),
        ("name blank", {"layout": L(), "name": "   "}),
        ("name too long", {"layout": L(), "name": "n" * 121}),
        ("name number", {"layout": L(), "name": 7}),
        ("no layout", {"note": "x"}),
        ("layout string", {"layout": "text"}),
        ("layout null", {"layout": None}),
        ("layout array", {"layout": []}),
        ("objects string", {"layout": L(objects="no")}),
        ("object unknown type", {"layout": L(objects=[{"assetType": "TANK", "x": 0, "z": 0}])}),
        ("object x text", {"layout": L(objects=[{"assetType": "CAR", "x": "a", "z": 0}])}),
        ("object width zero", {"layout": L(objects=[{"assetType": "CAR", "x": 0, "z": 0, "widthMeters": 0}])}),
        ("object x too big", {"layout": L(objects=[{"assetType": "CAR", "x": 100001, "z": 0}])}),
        ("501 objects", {"layout": L(objects=[{"assetType": "CAR", "x": 0, "z": 0}] * 501)}),
        ("anchor outside region", {"layout": L(anchor={"latitude": 40, "longitude": 77.21})}),
        ("region has no area", {"layout": L(regionBounds={"northLatitude": 28.6, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2})}),
        ("region north below south", {"layout": L(regionBounds={"northLatitude": 28.5, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2})}),
        ("heading text", {"layout": L(anchor={"latitude": 28.61, "longitude": 77.21, "headingDegrees": "north"})}),
        ("version 2.5", {"layout": L(version=2.5)}),
        ("body is an array", [1, 2]),
        ("body is a string", "text"),
    ]
    for label, body in bad:
        add(f"create rejected: {label}", "pat", "POST", plans, body)
    add("create with no body", "pat", "POST", plans, None)
    add("create with malformed JSON", "pat", "POST", plans, raw="{not json")

    # --- listing
    for u in USERS:
        add(f"list plans as {u}", u, "GET", plans)

    # --- start review
    add("start review P1 (reviewer)", "rex", "POST", "/api/plans/{P1}/start-review")
    add("start review P1 again (other reviewer)", "rhea", "POST", "/api/plans/{P1}/start-review")
    for u in ("pat", "ada", "aud"):
        add(f"start review P1 as {u}", u, "POST", "/api/plans/{P1}/start-review")
    add("start review P1 as a planner who cannot see it", "pia", "POST", "/api/plans/{P1}/start-review")
    add("start review of an unknown plan", "rex", "POST", "/api/plans/nope/start-review")

    # --- decisions
    d = lambda p, u, body, label: add(label, u, "POST", f"/api/plans/{{{p}}}/decision", body)
    d("P1", "rex", {}, "decision: empty body")
    d("P1", "rex", {"decision": "maybe", "version": 1}, "decision: unknown word")
    d("P1", "rex", {"decision": "approve"}, "decision: no version")
    d("P1", "rex", {"decision": "approve", "version": "1"}, "decision: version text")
    d("P1", "rex", {"decision": "approve", "version": 1.5}, "decision: version 1.5")
    d("P1", "rex", {"decision": "approve", "version": 0}, "decision: version 0")
    d("P1", "rex", {"decision": "approve", "version": -3}, "decision: version negative")
    d("P1", "rex", {"decision": "approve", "version": 1, "reason": 5}, "decision: reason number")
    d("P1", "rex", {"decision": "approve", "version": 1, "reason": "r" * 2001}, "decision: reason too long")
    d("P1", "rex", {"decision": "reject", "version": 1, "reason": "too short"}, "decision: reject with short reason")
    d("P1", "rex", {"decision": "changes", "version": 1, "reason": "   padded   "}, "decision: changes with padded short reason")
    d("P1", "rex", {"decision": "approve", "version": 7, "reason": ""}, "decision: wrong version")
    d("P1", "ada", {"decision": "approve", "version": 1}, "decision by admin")
    d("P1", "aud", {"decision": "approve", "version": 1}, "decision by auditor")
    d("P1", "pat", {"decision": "approve", "version": 1}, "decision by the owner")
    d("P1", "pia", {"decision": "approve", "version": 1}, "decision by a planner who cannot see it")
    d("nope", "rex", {"decision": "approve", "version": 1}, "decision on an unknown plan")
    d("P1", "ada", {"decision": "nonsense"}, "decision: bad body before permission")
    d("P1", "rex", {"decision": "changes", "version": 1, "reason": "  Please move the vehicle  "}, "decision: changes requested (P1)")
    d("P1", "rhea", {"decision": "approve", "version": 1}, "decision on a plan already decided")
    d("P2", "rex", {"decision": "reject", "version": 1, "reason": "The area is wrong"}, "decision: reject P2 without start-review")
    d("P3", "rhea", {"decision": "approve", "version": 1, "reason": "ok ✓"}, "decision: approve P3 with a reason")
    d("P4", "rex", {"decision": "approve", "version": 1}, "decision: approve P4 without a reason")

    # --- new versions
    v = lambda p, u, body, label, **k: add(label, u, "POST", f"/api/plans/{{{p}}}/versions", body, **k)
    v("P3", "pia", {"layout": L("Gamma 2")}, "version of an approved plan")
    v("P1", "pia", {"layout": L("Hijack")}, "version by a planner who cannot see it")
    for u in ("rex", "ada", "aud"):
        v("P1", u, {"layout": L()}, f"version as {u}")
    v("nope", "pat", {"layout": L()}, "version of an unknown plan")
    v("P1", "pat", {"layout": L(objects=[{"assetType": "TANK", "x": 0, "z": 0}])}, "version with a bad layout")
    v("P1", "pat", {"layout": L(), "note": "n" * 501}, "version with a long note")
    v("P1", "pat", {"layout": {k: val for k, val in L("x").items() if k != "name"}, "note": "second"}, "version without a layout name keeps the old name")
    v("P1", "pat", {"layout": L()}, "version of a plan that is already submitted")
    d("P1", "rex", {"decision": "approve", "version": 1}, "decision naming the old version")
    d("P1", "rex", {"decision": "approve", "version": 3}, "decision naming a future version")
    v("P2", "pat", {"layout": L("Beta v2", ids=False)}, "version of a rejected plan")
    d("P2", "rhea", {"decision": "changes", "version": 2, "reason": "Rotate the helicopter"}, "decision: changes on P2 v2")
    v("P2", "pat", {"layout": L("Untitled plan")}, "version with the default name keeps the plan name")
    d("P1", "rhea", {"decision": "approve", "version": 2}, "decision: approve P1 v2")

    # --- comments
    c = lambda p, u, body, label: add(label, u, "POST", f"/api/plans/{{{p}}}/comments", body)
    c("P1", "pat", {"text": "  thanks, fixed  "}, "comment by owner")
    c("P1", "rex", {"text": "unicode é — ✓ \"quotes\" \\ back"}, "comment by reviewer")
    c("P1", "ada", {"text": "admin note"}, "comment by admin")
    c("P1", "aud", {"text": "audit note"}, "comment by auditor")
    c("P1", "pia", {"text": "not mine"}, "comment by a planner who cannot see it")
    c("nope", "rex", {"text": "x"}, "comment on an unknown plan")
    for label, body in [("blank", {"text": "   "}), ("too long", {"text": "t" * 2001}), ("number", {"text": 5}), ("null", {"text": None}), ("missing", {}), ("array body", [1])]:
        c("P1", "rex", body, f"comment rejected: {label}")
    c("P2", "rex", {"text": "comment on a plan awaiting changes"}, "comment on P2")

    # --- withdraw
    w = lambda p, u, label: add(label, u, "POST", f"/api/plans/{{{p}}}/withdraw")
    w("P5", "pia", "withdraw P5 by owner")
    w("P5", "pia", "withdraw P5 again")
    w("P1", "pat", "withdraw an approved plan")
    w("P2", "pia", "withdraw by a planner who cannot see it")
    w("P4", "rex", "withdraw by a reviewer")
    w("P2", "ada", "withdraw by an admin")
    w("nope", "pat", "withdraw an unknown plan")
    v("P5", "pia", {"layout": L("After withdraw")}, "version of a withdrawn plan")
    c("P5", "pia", {"text": "comment on withdrawn"}, "comment on a withdrawn plan")
    add("start review of a withdrawn plan", "rex", "POST", "/api/plans/{P5}/start-review")
    d("P5", "rex", {"decision": "approve", "version": 1}, "decision on a withdrawn plan")

    # --- cookie sign-in and the CSRF header
    add("cookie sign-in", "pat", "POST", "/api/auth/login", {"username": "pat", "password": "{PASSWORD}"}, headers=JSON_HEADERS, cookie="new")
    add("cookie: comment without the header", "pat", "POST", "/api/plans/{P1}/comments", {"text": "x"}, cookie="use", headers={})
    add("cookie: comment with the header", "pat", "POST", "/api/plans/{P1}/comments", {"text": "via cookie"}, cookie="use", headers=JSON_HEADERS)
    add("cookie: list plans", "pat", "GET", plans, cookie="use", headers={})

    # --- field images
    up = lambda label, plan, user, parts, **k: add(label, user, "POST", f"/api/plans/{{{plan}}}/attachments", parts=parts, **k)
    png = lambda name="gate.png", ctype="image/png", data=None: ("file", name, ctype, PNG if data is None else data)
    add("create P6 for the image limit", "pat", "POST", plans, {"layout": L("Images plan")}, save=("P6", "id"))
    up("upload PNG with a caption", "P4", "pat", [png(), ("caption", None, None, "  Front gate  ")], save=("A1", "id"))
    up("upload JPEG by a reviewer (odd file name)", "P4", "rex", [("file", "Fotö 🚁 (1) a+b.jpg", "image/jpeg", JPEG)], save=("A2", "id"))
    up("upload WebP", "P4", "pat", [("file", "pic.webp", "image/webp", WEBP)], save=("A3", "id"))
    up("file name with folders", "P4", "pat", [png("../../evil name.png")], save=("A4", "id"))
    up("file name with Windows folders", "P4", "pat", [png("C:\\temp\\x y.png")], save=("A5", "id"))
    up("file name of symbols", "P4", "pat", [png("***.png")], save=("A6", "id"))
    up("very long file name", "P4", "pat", [png("n" * 150 + ".png")], save=("A7", "id"))
    up("empty file name means no file", "P4", "pat", [("file", "", "image/png", PNG)])
    up("long unicode caption", "P4", "pat", [png(), ("caption", None, None, "é" * 150 + "x" * 100)], save=("A8", "id"))
    up("uppercase declared type", "P4", "pat", [png(ctype="IMAGE/PNG")])
    up("declared type with parameter", "P4", "pat", [png(ctype="image/png; charset=binary")])
    up("upload as admin", "P4", "ada", [png()])
    up("upload as auditor", "P4", "aud", [png()])
    up("upload by a planner who cannot see the plan", "P4", "pia", [png()])
    up("upload to an unknown plan", "nope", "pat", [png()])
    up("upload to a withdrawn plan", "P5", "pia", [png()])
    up("multipart without a file", "P4", "pat", [("caption", None, None, "only text")])
    add("json body instead of a file", "pat", "POST", "/api/plans/{P4}/attachments", {"caption": "x"})
    up("PNG bytes declared as GIF", "P4", "pat", [png(ctype="image/gif")])
    up("PNG bytes declared as JPEG", "P4", "pat", [png(ctype="image/jpeg")])
    up("text file declared as PNG", "P4", "pat", [png(data=b"<html>not an image</html>")])
    up("empty file declared as PNG", "P4", "pat", [png(data=b"")])
    up("file part without a content type", "P4", "pat", [("file", "x.png", None, PNG)])
    up("RIFF that is not WebP", "P4", "pat", [("file", "x.webp", "image/webp", b"RIFF\x00\x00\x00\x00WAVEfmt ")])
    up("unexpected field name", "P4", "pat", [("photo", "x.png", "image/png", PNG)])
    up("two files", "P4", "pat", [png(), png("two.png")])
    up("five text fields", "P4", "pat", [png()] + [(f"f{i}", None, None, "v") for i in range(5)])
    up("four text fields", "P4", "pat", [png()] + [(f"f{i}", None, None, "v") for i in range(4)], save=("A9", "id"))
    up("file too large (10 MB + 1)", "P4", "pat", [png(data=PNG + b"\x00" * (10 * 1024 * 1024 + 1 - len(PNG)))])
    up("file too large, unknown plan", "nope", "pat", [png(data=PNG + b"\x00" * (10 * 1024 * 1024 + 1 - len(PNG)))])
    add("multipart without a boundary", "pat", "POST", "/api/plans/{P4}/attachments", rawbytes=b"garbage", headers={"Content-Type": "multipart/form-data"})
    add("signed out upload", None, "POST", "/api/plans/{P4}/attachments", parts=[png()])
    for n in range(31):
        up(f"image {n + 1} of the plan limit", "P6", "pat", [png(f"limit{n}.png")], **({"save": ("L1", "id")} if n == 0 else {}))

    g = lambda label, att, user, **k: add(label, user, "GET", f"/api/attachments/{{{att}}}", binary=True, **k)
    for u in ("pat", "rex", "rhea", "ada", "aud"):
        g(f"show A1 as {u}", "A1", u)
    g("show A2 (JPEG)", "A2", "pat")
    g("show A3 (WebP)", "A3", "rex")
    g("show by a planner who cannot see the plan", "A1", "pia")
    g("show an unknown image", "nope", "pat")
    g("show signed out", "A1", None)
    g("show with If-None-Match *", "A1", "pat", headers={"If-None-Match": "*"})
    g("show with an unrelated If-None-Match", "A1", "pat", headers={"If-None-Match": '"other"'})
    g("show with a far-future If-Modified-Since", "A1", "pat", headers={"If-Modified-Since": "Wed, 01 Jan 2200 00:00:00 GMT"})
    g("show with an old If-Modified-Since", "A1", "pat", headers={"If-Modified-Since": "Mon, 01 Jan 1990 00:00:00 GMT"})
    add("HEAD of an image", "pat", "HEAD", "/api/attachments/{A1}", binary=True)
    add("POST to an image address", "pat", "POST", "/api/attachments/{A1}", {})

    dl = lambda label, att, user: add(label, user, "DELETE", f"/api/attachments/{{{att}}}")
    dl("delete A1 by a planner who cannot see the plan", "A1", "pia")
    dl("delete A1 by a reviewer who is not the uploader", "A1", "rex")
    dl("delete A1 by an auditor", "A1", "aud")
    dl("delete A1 by the uploader", "A1", "pat")
    dl("delete A1 again", "A1", "pat")
    dl("delete A2 by an admin", "A2", "ada")
    dl("delete A3 by another reviewer", "A3", "rhea")
    dl("delete an unknown image", "nope", "pat")
    g("show A1 after delete", "A1", "pat")
    g("show A2 after delete", "A2", "rex")
    add("signed out delete", None, "DELETE", "/api/attachments/{A3}")

    # --- exactly what the Android app sends (OkHttp + org.json: no spaces, 1.0E-7, escaped slashes, \\u escapes)
    app_layout = (
        '{"version":2,"id":"b6c7f3a2-0000-4000-8000-aaaaaaaaaaaa","name":"Phone \\/ plan \\u00e9 \\ud83d\\ude80","createdAtEpochMs":1761900000000,'
        '"updatedAtEpochMs":1761900500000,"regionBounds":{"southLatitude":28.6115,"northLatitude":28.6165,"westLongitude":77.2075,"eastLongitude":77.2125},'
        '"anchor":{"id":"anchor-1","label":"Origin Anchor","x":0,"z":0,"description":"Primary deployment reference datum","longitude":77.21,"latitude":28.614,'
        '"horizontalAccuracyMeters":4.199999809265137,"headingDegrees":12.5},"objects":['
        '{"scale":1,"id":"o1","assetType":"GUNSHIP","name":"Gunship 1","x":1.0E-7,"z":-12.5,"rotationDegrees":359.99999999999994,"widthMeters":27.037078857421875,"lengthMeters":39.400001525878906,"heightMeters":12.101274490356445},'
        '{"scale":1,"id":"o2","assetType":"K131_TAKEOFF","name":"K-131 \\/ Take-Off","x":-20,"z":3.0E-5,"rotationDegrees":-30.0,"widthMeters":6.619999885559082,"lengthMeters":12.479999542236328,"heightMeters":7.079999923706055}]}'
    )
    add("app-style: submit plan (org.json formatting)", "pat", "POST", plans, raw='{"layout":' + app_layout + ',"note":"sent by app"}', save=("P7", "id"))
    add("app-style: new version after the plan is open", "pat", "POST", "/api/plans/{P7}/versions", raw='{"layout":' + app_layout + ',"note":"again"}')
    add("app-style: list plans as the planner", "pat", "GET", plans)

    # --- request size limit (Node: express.json limit 2 MB)
    def padded(extra_bytes):
        base = json.dumps({"layout": L(), "pad": ""})
        return json.dumps({"layout": L(), "pad": "x" * (2 * 1024 * 1024 + extra_bytes - len(base.encode()))})
    add("JSON body of exactly 2 MB", "pia", "POST", plans, raw=padded(0))
    add("JSON body 1 byte over 2 MB", "pia", "POST", plans, raw=padded(1))
    add("JSON body of 3 MB", "pia", "POST", plans, raw=padded(1024 * 1024))

    # --- final reads
    for u in USERS:
        add(f"final list plans as {u}", u, "GET", plans)
    for u in USERS:
        add(f"audit as {u}", u, "GET", "/api/audit")
    for u in ("ada", "aud"):
        add(f"audit verify as {u}", u, "GET", "/api/audit/verify")
    return S


# ---------------------------------------------------------------- running
def raw_key(text):
    """The raw answer with ids, times and fingerprints blanked, to see whether the bytes agree too."""
    text = UUID.sub("U", text)
    text = re.sub(r"(?<!\d)1\d{12}(?!\d)", "T", text)
    return re.sub(r"(?<![0-9a-f])[0-9a-f]{16}(?![0-9a-f])", "H", text)


class Server:
    def __init__(self, name, base, password):
        self.name, self.base, self.password = name, base.rstrip("/"), password
        self.tokens, self.vars, self.ids, self.cookie = {}, {}, {}, None

    def send(self, method, path, body=None, raw=None, headers=None, rawbytes=None):
        data = rawbytes if rawbytes is not None else raw.encode() if raw is not None else (json.dumps(body).encode() if body is not None else None)
        h = {"Content-Type": "application/json"} if data is not None else {}
        h.update(headers or {})
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=h)
        try:
            with urllib.request.urlopen(request, timeout=60) as r:
                return r.status, r.read(), r.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read(), e.headers

    def login_all(self):
        for user in USERS:
            status, text, _ = self.send("POST", "/api/auth/token", {"username": user, "password": self.password})
            text = text.decode("utf-8")
            if status != 200:
                sys.exit(f"{self.name}: cannot sign in {user} ({status}). Are the six users created with the COMPARE_PASSWORD password?")
            self.tokens[user] = json.loads(text)["token"]

    def run(self, step):
        path = step.path
        for key, value in self.vars.items():
            path = path.replace("{" + key + "}", value)
        body = step.body
        if isinstance(body, dict):
            body = json.loads(json.dumps(body).replace("{PASSWORD}", self.password))
        headers = dict(step.headers or {})
        if step.cookie == "use":
            headers["Cookie"] = self.cookie
        elif step.user and step.cookie is None:
            headers["Authorization"] = f"Bearer {self.tokens[step.user]}"
        rawbytes = step.rawbytes
        if step.parts is not None:
            rawbytes = multipart(step.parts)
            headers["Content-Type"] = f"multipart/form-data; boundary={BOUNDARY}"
        status, content, response_headers = self.send(step.method, path, body, step.raw, headers, rawbytes)
        if step.binary:
            text = f"<{len(content)} bytes sha256={hashlib.sha256(content).hexdigest()[:16]}>" if status == 200 else content.decode("utf-8", "replace")
        else:
            text = content.decode("utf-8", "replace")
        if step.cookie == "new":
            self.cookie = response_headers.get("Set-Cookie", "").split(";")[0]
        if step.save and status < 300:
            self.vars[step.save[0]] = json.loads(text)[step.save[1]]
        return status, text, response_headers

    def normalise(self, value):
        if isinstance(value, dict):
            out = {k: self.normalise(v) for k, v in value.items()}
            layout = out.get("layout")
            if "contentHash" in out and isinstance(layout, dict) and any(str(o.get("id", "")).startswith("<id") for o in layout.get("objects", [])):
                out["contentHash"] = "<hash of a layout with generated ids>"
            return out
        if isinstance(value, list):
            return [self.normalise(v) for v in value]
        if isinstance(value, str):
            return UUID.sub(lambda m: self.ids.setdefault(m.group(0), f"<id{len(self.ids) + 1}>"), value)
        if isinstance(value, int) and not isinstance(value, bool) and value >= 10**12:
            return "<ms>"
        return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--node", default="http://127.0.0.1:4100")
    parser.add_argument("--django", default="http://127.0.0.1:4101")
    parser.add_argument("--raw", action="store_true", help="explain every raw-text difference")
    parser.add_argument("--show", action="store_true", help="print every request, not only the differences")
    args = parser.parse_args()
    password = os.environ.get("COMPARE_PASSWORD") or sys.exit("Set COMPARE_PASSWORD to the password both servers' test users were created with.")

    node, django = Server("node", args.node, password), Server("django", args.django, password)
    node.login_all()
    django.login_all()
    same = raw_same = 0
    raw_differences = []
    notes, problems = [], []
    script = build_script()
    for step in script:
        results = {}
        for server in (node, django):
            status, text, headers = server.run(step)
            try:
                parsed = server.normalise(json.loads(text))
            except ValueError:
                parsed = text
            results[server.name] = (status, parsed, text, headers)
            time.sleep(0.006)  # keep "last changed" times apart so list order cannot tie
        (ns, nb, nt, nh), (ds, db, dt, dh) = results["node"], results["django"]
        line = f"{step.method:6} {step.label}  ->  {ns}" + ("" if ns == ds else f" vs {ds}")
        if ns != ds or nb != db:
            problems.append((line, nb, db))
            print("DIFF  " + line)
            continue
        same += 1
        if args.show:
            print("same  " + line)
        compared = ["Cache-Control", "Content-Type"] + (["X-Content-Type-Options", "Content-Disposition"] if step.binary else [])
        for header in compared:
            if nh.get(header) != dh.get(header):
                notes.append((header, nh.get(header), dh.get(header), step.label))
        if step.binary and ns == 200:
            for header in ("ETag", "Last-Modified"):
                if bool(nh.get(header)) != bool(dh.get(header)):
                    notes.append((header + " present", bool(nh.get(header)), bool(dh.get(header)), step.label))
        if raw_key(nt) == raw_key(dt):
            raw_same += 1
        else:
            raw_differences.append((step.label, raw_key(nt), raw_key(dt)))
    print(f"\n{len(script)} requests sent to each server")
    print(f"identical status + body: {same}    different: {len(problems)}")
    print(f"of those, byte-for-byte identical text (ids/times blanked): {raw_same}")
    for line, nb, db in problems:
        print(f"\n- {line}\n    node  : {json.dumps(nb, ensure_ascii=False)[:600]}\n    django: {json.dumps(db, ensure_ascii=False)[:600]}")
    if args.raw:
        for label, a, b in raw_differences:
            at = next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), min(len(a), len(b)))
            print(f"\nraw text differs: {label}\n    node  : ...{a[max(0, at - 60):at + 80]}\n    django: ...{b[max(0, at - 60):at + 80]}")
    kinds = {}
    for header, a, b, label in notes:
        kinds.setdefault((header, a, b), []).append(label)
    for (header, a, b), labels in kinds.items():
        print(f"\nheader {header}: node={a!r} django={b!r}  ({len(labels)} requests, e.g. '{labels[0]}')")
    if not notes:
        print("headers Cache-Control and Content-Type: identical on every request")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
