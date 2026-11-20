"""Side-by-side check: sends the SAME scripted requests to the Node server and the Django server and diffs the answers.

Both servers must be running with their own empty temporary data folder and the same six users (see
docs/PHASE-2-PLANS.md, section "How to run the comparison"). Nothing here touches a real database.

    COMPARE_PASSWORD=... python scripts/compare_servers.py --node http://127.0.0.1:4100 --django http://127.0.0.1:4101

What is compared for every request: the status code, the JSON body (after ids and times are replaced by
placeholders, because each server makes its own), and the Cache-Control / Content-Type headers.
Python standard library only.
"""

import argparse
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


# ---------------------------------------------------------------- the script
class Step:
    def __init__(self, label, user, method, path, body=None, *, raw=None, save=None, headers=None, cookie=None):
        self.label, self.user, self.method, self.path, self.body = label, user, method, path, body
        self.raw, self.save, self.headers, self.cookie = raw, save, headers, cookie


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

    def send(self, method, path, body=None, raw=None, headers=None):
        data = raw.encode() if raw is not None else (json.dumps(body).encode() if body is not None else None)
        h = {"Content-Type": "application/json"} if data is not None else {}
        h.update(headers or {})
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=h)
        try:
            with urllib.request.urlopen(request, timeout=30) as r:
                return r.status, r.read().decode("utf-8"), r.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8"), e.headers

    def login_all(self):
        for user in USERS:
            status, text, _ = self.send("POST", "/api/auth/token", {"username": user, "password": self.password})
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
        status, text, response_headers = self.send(step.method, path, body, step.raw, headers)
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
        for header in ("Cache-Control", "Content-Type"):
            if nh.get(header) != dh.get(header):
                notes.append((header, nh.get(header), dh.get(header), step.label))
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
