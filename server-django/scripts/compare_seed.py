"""Checks that the Django `seed` command produces the same data as Node's `npm run seed`.

Both servers must be freshly seeded (see run_conformance.sh). Passwords differ (they are random), so each
server's own seed-credentials.txt is read. Times differ too (each seed ran at its own moment), so every time is
replaced by "how long before the newest time in that server's plan list".

    python scripts/compare_seed.py --node-url http://127.0.0.1:4100 --node-creds <file> --django-url http://127.0.0.1:4101 --django-creds <file>
"""

import argparse
import json
import sys
import urllib.request

TIME_KEYS = {"createdAt", "updatedAt", "submittedAt", "decidedAt", "at", "lastLoginAt"}


def creds(path):
    lines = open(path, encoding="utf-8").read().splitlines()[2:]
    return {p[0]: p[2] for p in (line.split() for line in lines if line.strip())}


class Server:
    def __init__(self, url, creds_file):
        self.url, self.passwords, self.tokens = url.rstrip("/"), creds(creds_file), {}

    def call(self, path, user, method="GET", body=None):
        if user not in self.tokens:
            login = urllib.request.Request(
                self.url + "/api/auth/token", method="POST", headers={"Content-Type": "application/json"},
                data=json.dumps({"username": user, "password": self.passwords[user]}).encode(),
            )
            self.tokens[user] = json.load(urllib.request.urlopen(login))["token"]
        req = urllib.request.Request(self.url + path, method=method, headers={"Authorization": f"Bearer {self.tokens[user]}"})
        return json.load(urllib.request.urlopen(req))


def times(value, found):
    if isinstance(value, dict):
        for k, v in value.items():
            if k in TIME_KEYS and isinstance(v, int):
                found.append(v)
            times(v, found)
    elif isinstance(value, list):
        for v in value:
            times(v, found)
    return found


def relative(value, newest):
    if isinstance(value, dict):
        return {k: (f"newest-{newest - v}ms" if k in TIME_KEYS and isinstance(v, int) else relative(v, newest)) for k, v in value.items()}
    if isinstance(value, list):
        return [relative(v, newest) for v in value]
    return value


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--node-url", required=True)
    ap.add_argument("--node-creds", required=True)
    ap.add_argument("--django-url", required=True)
    ap.add_argument("--django-creds", required=True)
    args = ap.parse_args()
    node, django = Server(args.node_url, args.node_creds), Server(args.django_url, args.django_creds)

    checks = [
        ("plans as reviewer menon", "/api/plans", "menon"),
        ("plans as reviewer sharma", "/api/plans", "sharma"),
        ("plans as planner singh (own only)", "/api/plans", "singh"),
        ("plans as planner khan (own only)", "/api/plans", "khan"),
        ("plans as auditor nair", "/api/plans", "nair"),
        ("audit log as auditor nair", "/api/audit", "nair"),
        ("audit log as planner rao", "/api/audit", "rao"),
        ("audit chain check", "/api/audit/verify", "nair"),
        ("name list", "/api/users", "menon"),
        ("admin user list", "/api/admin/users", "iyer"),
    ]
    # Reference time: the newest time in each server's full plan list.
    newest = {}
    for name, server in (("node", node), ("django", django)):
        newest[name] = max(times(server.call("/api/plans", "menon"), []))
    bad = 0
    for label, path, user in checks:
        a, b = node.call(path, user), django.call(path, user)
        if path == "/api/audit":  # the sign-ins made by this very script are extra entries; compare the seeded ones only
            a = [e for e in a if e["action"] != "Signed in"]
            b = [e for e in b if e["action"] != "Signed in"]
            for e in a + b:
                e.pop("id", None)
        if path == "/api/audit/verify":
            a, b = {"ok": a["ok"]}, {"ok": b["ok"]}
        if path == "/api/admin/users":
            for row in a + b:
                row.pop("lastLoginAt", None)
                row.pop("createdAt", None)
        ra, rb = relative(a, newest["node"]), relative(b, newest["django"])
        same = json.dumps(ra, sort_keys=True) == json.dumps(rb, sort_keys=True)
        size = len(a) if isinstance(a, list) else 1
        print(("same  " if same else "DIFF  ") + f"{label}  ({size} items)")
        bad += 0 if same else 1
    print("\nNode seed and Django seed agree on everything compared." if not bad else f"\n{bad} differences")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
