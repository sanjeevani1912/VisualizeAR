"""Fills the LIVE DhruvAR stack with a small, believable, fictional demo story, through the normal HTTP API.

Run from the repository root, with the stack up (http://localhost:8000):

    python server-django/scripts/demo_data.py            # make demo passwords (first time) and load the plans
    python server-django/scripts/demo_data.py --check    # only list what exists, change nothing

What it does (nothing is wiped; stackadmin is not touched):
  1. Gives the 8 demo users (bose, khan, rao, singh, menon, sharma, iyer, nair) known passwords, kept ONLY in the
     git-ignored file deploy/demo-passwords.txt. It uses `manage.py resetpassword` with a throw-away temporary password,
     then signs in and changes it, exactly as a real person's first sign-in would. Passwords are never printed.
  2. Creates 8 plans and moves them to different states through the real routes (so the audit chain stays valid):
     SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED (with reason), CHANGES_REQUESTED, WITHDRAWN, a plan resubmitted
     as version 2, and a plan with a wide (2880x1440) panorama. Plus comments and generated pictures.
  3. Is safe to run twice: a plan whose name already exists is skipped.

All names, places and text are made up. Positions are near ordinary civilian towns, not unit locations.
Needs: Python 3.10+, Pillow (`pip install pillow`), Docker (to call resetpassword).
Dates: the API stamps everything with "now", so every demo plan is dated the day the script runs.
"""

import io
import json
import math
import random
import secrets
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = "http://localhost:8000/api"
ROOT = Path(__file__).resolve().parents[2]
PASSWORD_FILE = ROOT / "deploy" / "demo-passwords.txt"
DEPLOY = ROOT / "deploy"

DEMO_USERS = {  # username -> role
    "bose": "planner", "khan": "planner", "rao": "planner", "singh": "planner",
    "menon": "reviewer", "sharma": "reviewer", "iyer": "admin", "nair": "auditor",
}

SIZES = {  # width, length, height in metres (the app's current asset library)
    "GUNSHIP": (13.52, 19.7, 6.05), "K131": (3.45, 6.24, 5.45), "K131_TAKEOFF": (3.31, 6.24, 3.54),
    "MILITARY_VEHICLE": (1.72, 4.0, 3.1), "OFFROAD_VEHICLE": (2.43, 4.66, 3.54), "STR_MGS": (3.44, 7.63, 3.47),
}
LABEL = {"GUNSHIP": "Gunship", "K131": "K-131", "K131_TAKEOFF": "K-131 Take-Off", "MILITARY_VEHICLE": "Military Vehicle",
         "OFFROAD_VEHICLE": "Off-road Vehicle", "STR_MGS": "STR-MGS"}


# ---------- plain HTTP helpers ----------
def call(method, path, token=None, body=None, raw=None, content_type=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if content_type:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read().decode() or "null"
            return r.status, json.loads(text)
    except urllib.error.HTTPError as e:
        text = e.read().decode() or "null"
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, {"error": text[:200]}


def must(result, what):
    status, data = result
    if status >= 300:
        sys.exit(f"FAILED: {what}: {status} {data}")
    return data


# ---------- passwords ----------
def new_password():
    return secrets.token_urlsafe(9) + "aA1"


def read_passwords():
    out = {}
    if PASSWORD_FILE.exists():
        for line in PASSWORD_FILE.read_text().splitlines():
            parts = line.split()
            if len(parts) >= 3 and parts[0] in DEMO_USERS:
                out[parts[0]] = parts[2]
    return out


def sign_in(user, passwords):
    status, data = call("POST", "/auth/token", body={"username": user, "password": passwords[user]})
    return data["token"] if status == 200 else None


def ensure_passwords():
    passwords = read_passwords()
    changed = False
    for user in DEMO_USERS:
        if user in passwords and sign_in(user, passwords):
            continue
        final = passwords.get(user) or new_password()
        temp = new_password()
        subprocess.run(
            ["docker", "compose", "exec", "-T", "-e", "DHRUVAR_PASSWORD=" + temp, "django",
             "python", "manage.py", "resetpassword", "--username", user],
            cwd=DEPLOY, check=True, stdout=subprocess.DEVNULL,
        )
        status, data = call("POST", "/auth/token", body={"username": user, "password": temp})
        if status != 200:
            sys.exit(f"Could not sign in {user} with the temporary password: {status}")
        status, data = call("POST", "/auth/change-password", token=data["token"], body={"current": temp, "next": final})
        if status != 200:
            sys.exit(f"Could not set the demo password for {user}: {status} {data}")
        passwords[user] = final
        changed = True
    if changed or not PASSWORD_FILE.exists():
        lines = ["# DhruvAR DEMO passwords. Local only. Never commit, never send. Fictional demo users.",
                 "# username  role  password"]
        lines += [f"{u:<9} {DEMO_USERS[u]:<9} {passwords[u]}" for u in DEMO_USERS]
        PASSWORD_FILE.write_text("\n".join(lines) + "\n", newline="\n") if sys.version_info >= (3, 10) else PASSWORD_FILE.write_text("\n".join(lines) + "\n")
        try:
            PASSWORD_FILE.chmod(0o600)
        except OSError:
            pass
    return passwords


# ---------- layouts ----------
def layout(name, lat, lon, heading, objects, half_m=260):
    dlat = half_m / 111_320
    dlon = half_m / (111_320 * math.cos(math.radians(lat)))
    items = []
    counts = {}
    for kind, x, z, rot in objects:
        counts[kind] = counts.get(kind, 0) + 1
        w, l, h = SIZES[kind]
        items.append({
            "id": str(uuid.uuid4()), "assetType": kind, "name": f"{LABEL[kind]} {counts[kind]}",
            "x": x, "z": z, "rotationDegrees": rot, "widthMeters": w, "lengthMeters": l, "heightMeters": h,
        })
    return {
        "version": 2, "id": str(uuid.uuid4()), "name": name,
        "regionBounds": {"northLatitude": lat + dlat, "southLatitude": lat - dlat, "eastLongitude": lon + dlon, "westLongitude": lon - dlon},
        "anchor": {"latitude": lat, "longitude": lon, "headingDegrees": heading, "horizontalAccuracyMeters": 4.2},
        "objects": items,
    }


PLANS = [
    # key, owner, name, (lat, lon), heading, objects
    ("staging", "bose", "Exercise Sahyadri - assembly area", (18.5204, 73.8567), 20, [
        ("K131", 0, 25, 0), ("K131", 12, 25, 0), ("K131_TAKEOFF", -14, 40, 90), ("MILITARY_VEHICLE", 30, 10, 45),
        ("MILITARY_VEHICLE", 38, 12, 45), ("OFFROAD_VEHICLE", -30, 0, 180), ("STR_MGS", 45, 55, 300), ("GUNSHIP", -60, 90, 0)]),
    ("hilltop", "singh", "Hill-post resupply route", (26.9124, 75.7873), 75, [
        ("OFFROAD_VEHICLE", 0, 0, 90), ("OFFROAD_VEHICLE", 6, 20, 90), ("OFFROAD_VEHICLE", 12, 40, 90), ("MILITARY_VEHICLE", -20, 55, 120),
        ("K131", 40, 70, 200), ("STR_MGS", -45, 110, 250)]),
    ("depot", "rao", "Forward depot layout", (30.3165, 78.0322), 340, [
        ("K131", -10, 0, 0), ("K131", 10, 0, 0), ("K131", 30, 0, 0), ("MILITARY_VEHICLE", -25, -30, 90), ("MILITARY_VEHICLE", -25, -40, 90),
        ("OFFROAD_VEHICLE", 55, 25, 15), ("STR_MGS", 0, 60, 180)]),
    ("lz", "khan", "Landing zone Bravo", (21.1458, 79.0882), 130, [
        ("GUNSHIP", 0, 40, 0), ("GUNSHIP", 35, 55, 30), ("K131_TAKEOFF", -30, 20, 90), ("MILITARY_VEHICLE", 60, -10, 200)]),
    ("convoy", "bose", "Convoy halt and refuel", (12.9716, 77.5946), 200, [
        ("MILITARY_VEHICLE", 0, 0, 0), ("MILITARY_VEHICLE", 0, 12, 0), ("MILITARY_VEHICLE", 0, 24, 0), ("OFFROAD_VEHICLE", 10, 36, 0),
        ("STR_MGS", -18, 18, 90), ("K131", 25, 8, 270)]),
    ("riverside", "rao", "Riverside observation post", (26.1445, 91.7362), 45, [
        ("OFFROAD_VEHICLE", 0, 0, 45), ("STR_MGS", 20, 30, 45), ("MILITARY_VEHICLE", -15, 25, 100)]),
    ("workshop", "singh", "Field workshop site", (18.5450, 73.8000), 10, [
        ("K131", 0, 0, 0), ("K131_TAKEOFF", 15, 0, 0), ("MILITARY_VEHICLE", -20, 20, 90), ("OFFROAD_VEHICLE", -20, 32, 90),
        ("STR_MGS", 30, 40, 330)]),
    ("panorama", "khan", "Ridge-line panorama survey", (26.9300, 75.8200), 300, [
        ("OFFROAD_VEHICLE", 0, 0, 300), ("MILITARY_VEHICLE", 14, 8, 300), ("K131", -28, 30, 0), ("STR_MGS", 40, 60, 280), ("GUNSHIP", -70, 100, 10)]),
]


# ---------- generated pictures (no real photos) ----------
def make_picture(width, height, title, seed, fmt="JPEG"):
    from PIL import Image, ImageDraw, ImageFont

    rnd = random.Random(seed)
    img = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(img)
    horizon = int(height * 0.55)
    for y in range(horizon):  # sky
        t = y / horizon
        draw.line([(0, y), (width, y)], fill=(int(90 + 110 * t), int(140 + 80 * t), int(210 + 30 * t)))
    for y in range(horizon, height):  # ground
        t = (y - horizon) / (height - horizon)
        draw.line([(0, y), (width, y)], fill=(int(96 - 30 * t), int(120 - 25 * t), int(70 - 20 * t)))
    for layer, colour in enumerate([(120, 140, 150), (90, 115, 105), (70, 95, 75)]):  # ridges
        base = horizon - 40 + layer * 35
        pts, x = [(0, height)], 0
        phase = rnd.random() * 6
        while x <= width + 40:
            pts.append((x, base - 55 * math.sin(x / (width / 9) + phase) - 25 * math.sin(x / (width / 23) + layer)))
            x += 20
        pts.append((width, height))
        draw.polygon(pts, fill=colour)
    for _ in range(width // 16):  # scrub dots
        x, y = rnd.randrange(width), rnd.randrange(horizon + 20, height)
        draw.ellipse([x, y, x + rnd.randrange(4, 14), y + rnd.randrange(3, 9)], fill=(55, 85 + rnd.randrange(30), 50))
    try:
        font = ImageFont.truetype("arial.ttf", max(18, height // 22))
    except OSError:
        font = ImageFont.load_default()
    label = f"{title}  |  GENERATED DEMO PICTURE - NOT A REAL PHOTO"
    draw.rectangle([0, height - height // 14, width, height], fill=(20, 20, 20))
    draw.text((16, height - height // 14 + 6), label, fill=(240, 240, 240), font=font)
    out = io.BytesIO()
    img.save(out, fmt, quality=88)
    return out.getvalue()


def upload(token, plan_id, data, filename, mime, caption):
    boundary = "----demo" + secrets.token_hex(8)
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="caption"\r\n\r\n{caption}\r\n'.encode(),
             f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode(),
             data, f"\r\n--{boundary}--\r\n".encode()]
    return must(call("POST", f"/plans/{plan_id}/attachments", token, raw=b"".join(parts),
                     content_type=f"multipart/form-data; boundary={boundary}"), "upload " + filename)


# ---------- the story ----------
def main():
    check_only = "--check" in sys.argv
    if check_only:
        passwords = read_passwords()
        if "iyer" not in passwords:
            sys.exit("No demo passwords yet. Run without --check.")
    else:
        passwords = ensure_passwords()
    tokens = {u: sign_in(u, passwords) for u in DEMO_USERS}
    if not all(tokens.values()):
        sys.exit("A demo user could not sign in: " + ", ".join(u for u, t in tokens.items() if not t))
    existing = {p["name"]: p for p in must(call("GET", "/plans", tokens["iyer"]), "list plans")}
    if check_only:
        for p in existing.values():
            print(f"{p['code']}  {p['status']:<18} v{len(p['versions'])}  {p['name']}")
        print(must(call("GET", "/audit/verify", tokens["iyer"]), "verify"))
        return

    made = {}
    for key, owner, name, (lat, lon), heading, objs in PLANS:
        if name in existing:
            made[key] = existing[name]["id"]
            print("exists   ", name)
            continue
        result = must(call("POST", "/plans", tokens[owner], body={"layout": layout(name, lat, lon, heading, objs), "note": "Submitted for review."}), "create " + name)
        made[key] = result["id"]
        print("created  ", result["code"], name)
    fresh = {p["name"]: p for p in must(call("GET", "/plans", tokens["iyer"]), "list")}
    status = lambda key: next(p for p in fresh.values() if p["id"] == made[key])["status"]  # noqa: E731

    def decide(key, reviewer, kind, reason=""):
        plan = next(p for p in must(call("GET", "/plans", tokens[reviewer]), "list") if p["id"] == made[key])
        must(call("POST", f"/plans/{made[key]}/start-review", tokens[reviewer], body={}), "start review")
        must(call("POST", f"/plans/{made[key]}/decision", tokens[reviewer],
                  body={"decision": kind, "reason": reason, "version": len(plan["versions"])}), "decide " + key)

    def comment(key, who, text):
        must(call("POST", f"/plans/{made[key]}/comments", tokens[who], body={"text": text}), "comment")

    only_new = lambda key: status(key) == "SUBMITTED" and existing.get(next(n for k, _, n, *_ in PLANS if k == key)) is None  # noqa: E731

    if only_new("hilltop"):
        must(call("POST", f"/plans/{made['hilltop']}/start-review", tokens["menon"], body={}), "start review")
        comment("hilltop", "menon", "Route looks fine. Please confirm the fuel point before I decide.")
        comment("hilltop", "singh", "Fuel point is at the second vehicle halt. Will mark it in the next version if needed.")
    if only_new("depot"):
        comment("depot", "sharma", "Vehicle spacing is good. Approving.")
        decide("depot", "sharma", "approve", "Spacing and access lanes are acceptable.")
        comment("depot", "rao", "Thank you, sir. Plan frozen for the exercise.")
    if only_new("lz"):
        decide("lz", "menon", "reject", "Two gunships are placed closer than the safe distance. Redraw the landing zone with more room.")
    if only_new("convoy"):
        decide("convoy", "sharma", "changes", "Add a second exit for the vehicles and check the refuel point is clear of the track.")
        comment("convoy", "bose", "Understood. I will revise and resubmit.")
    if only_new("riverside"):
        must(call("POST", f"/plans/{made['riverside']}/withdraw", tokens["rao"], body={}), "withdraw")
    if only_new("workshop"):  # changes requested, then version 2, then approved
        decide("workshop", "menon", "changes", "Workshop area is too close to the vehicle line. Move it 20 m east.")
        v2 = layout("Field workshop site", 18.5450, 73.8000, 10, [
            ("K131", 22, 0, 0), ("K131_TAKEOFF", 37, 0, 0), ("MILITARY_VEHICLE", -20, 20, 90), ("OFFROAD_VEHICLE", -20, 32, 90), ("STR_MGS", 30, 40, 330)])
        must(call("POST", f"/plans/{made['workshop']}/versions", tokens["singh"], body={"layout": v2, "note": "Workshop moved 22 m east as asked."}), "version 2")
        decide("workshop", "menon", "approve", "Moved as asked. Approved.")

    # pictures: two field images and the wide panorama
    def has_pics(key):
        return bool(next(p for p in must(call("GET", "/plans", tokens["iyer"]), "list") if p["id"] == made[key])["attachments"])

    if not has_pics("depot"):
        upload(tokens["rao"], made["depot"], make_picture(1600, 1000, "Depot site, south edge", 11), "depot-south.jpg", "image/jpeg", "South edge of the depot site (generated picture)")
    if not has_pics("hilltop"):
        upload(tokens["menon"], made["hilltop"], make_picture(1600, 1000, "Hill-post approach", 22), "hilltop-approach.jpg", "image/jpeg", "Approach road to the hill post (generated picture)")
    if not has_pics("panorama"):
        upload(tokens["khan"], made["panorama"], make_picture(2880, 1440, "Ridge-line panorama", 33), "ridge-panorama.jpg", "image/jpeg", "Wide panorama along the ridge (generated, 2880x1440)")
        comment("panorama", "menon", "Panorama is clear. Please add one more point further along the ridge.")

    print("\nFinal state:")
    for p in must(call("GET", "/plans", tokens["iyer"]), "list"):
        print(f"  {p['code']}  {p['status']:<18} v{len(p['versions'])}  images={len(p['attachments'])}  {p['name']}")
    print("audit chain:", must(call("GET", "/audit/verify", tokens["iyer"]), "verify"))


if __name__ == "__main__":
    main()
