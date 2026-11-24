"""Load and multi-process check for a seeded Django server.

Sends many requests at the same moment to ONE OR MORE running Django processes that share one database, then checks that
the rules still held: no errors, every plan code different, exactly one decision accepted per plan, the audit chain intact.

    python scripts/load_check.py --url http://127.0.0.1:4001 --url http://127.0.0.1:4002 --creds <data folder>/seed-credentials.txt

Needs a freshly seeded database (manage.py seed). Python standard library only. It changes data, so use a throwaway database.
"""

import argparse
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request

LAYOUT = {
    "version": 2,
    "name": "Load check",
    "regionBounds": {"northLatitude": 28.62, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2},
    "anchor": {"latitude": 28.61, "longitude": 77.21, "headingDegrees": 0, "horizontalAccuracyMeters": 4},
    "objects": [{"assetType": "TENT", "x": 1, "z": 2, "rotationDegrees": 30}],
}


def call(url, method="GET", body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"} if data else {}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=120) as r:
            status, text = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    except OSError as e:  # connection reset, timeout...: counted as a failed request, never hidden
        return -1, {"transport_error": repr(e)}, time.perf_counter() - started
    try:
        body = json.loads(text) if text else None
    except ValueError:
        body = {"not_json": text[:80]}
    return status, body, time.perf_counter() - started


LIMIT = 25  # most requests in flight at once (the Django development server has a very short connection queue)


def burst(count, work):
    """Runs work(i) for i in range(count) as a crowd: at most LIMIT at the same moment, all started together.
    Returns [(result, seconds)] in order."""
    size = min(count, LIMIT)
    gate, out, room = threading.Barrier(size), [None] * count, threading.Semaphore(size)

    def runner(i):
        if i < size:
            gate.wait()  # the first crowd starts at the same instant
        with room:
            started = time.perf_counter()
            out[i] = (work(i), time.perf_counter() - started)

    threads = [threading.Thread(target=runner, args=(i,)) for i in range(count)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    return out


def summary(label, timings):
    ts = sorted(t for _, t in timings)
    p95 = ts[max(0, int(len(ts) * 0.95) - 1)]
    print(f"    {label}: median {statistics.median(ts) * 1000:.0f} ms, 95th percentile {p95 * 1000:.0f} ms, slowest {ts[-1] * 1000:.0f} ms")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", action="append", required=True, help="base URL of a Django process (repeat for several)")
    ap.add_argument("--creds", required=True)
    ap.add_argument("--reads", type=int, default=200)
    ap.add_argument("--writes", type=int, default=60)
    ap.add_argument("--concurrency", type=int, default=25, help="most requests in flight at once")
    args = ap.parse_args()
    global LIMIT
    LIMIT = args.concurrency
    urls = [u.rstrip("/") for u in args.url]
    pick = lambda i: urls[i % len(urls)]  # spreads requests over the processes
    pw = {p[0]: p[2] for p in (line.split() for line in open(args.creds, encoding="utf-8").read().splitlines()[2:] if line.strip())}
    tokens = {}
    for user in ("singh", "rao", "bose", "khan", "menon", "sharma", "nair"):
        status, body, _ = call(urls[0] + "/api/auth/token", "POST", {"username": user, "password": pw[user]})
        assert status == 200, f"cannot sign in {user}"
        tokens[user] = body["token"]
    planners, bad = ["singh", "rao", "bose", "khan"], 0
    print(f"{len(urls)} Django process(es): {', '.join(urls)}")

    def check(label, ok, detail=""):
        nonlocal bad
        print(f"  {'ok  ' if ok else 'FAIL'} {label} {detail}")
        bad += 0 if ok else 1

    print(f"1. {args.reads} simultaneous reads of the plan list")
    reads = burst(args.reads, lambda i: call(pick(i) + "/api/plans", token=tokens["menon" if i % 2 else "sharma"])[0])
    check("all answered 200", all(r == 200 for r, _ in reads), f"({sorted(set(r for r, _ in reads))})")
    summary("response time", reads)

    print(f"2. {args.writes} simultaneous plan submissions")
    subs = burst(args.writes, lambda i: call(pick(i) + "/api/plans", "POST", {"layout": {**LAYOUT, "name": f"Load {i}"}}, tokens[planners[i % 4]])[:2])
    check("all created (201)", all(r[0] == 201 for r, _ in subs), f"({sorted(set(r[0] for r, _ in subs))})")
    codes = [r[1]["code"] for r, _ in subs if r[0] == 201]
    check("every plan code different", len(set(codes)) == len(codes) == args.writes, f"({len(set(codes))} distinct of {args.writes})")
    summary("response time", subs)
    ids = [r[1]["id"] for r, _ in subs if r[0] == 201]

    print(f"3. {args.writes} simultaneous comments (reviewers) on the new plans")
    com = burst(args.writes, lambda i: call(pick(i) + f"/api/plans/{ids[i % len(ids)]}/comments", "POST", {"text": f"load comment {i}"}, tokens["menon" if i % 2 else "sharma"])[0])
    check("all created (201)", all(r == 201 for r, _ in com))
    summary("response time", com)

    print("4. 20 reviewers deciding the SAME plan at once (spread over the processes)")
    dec = burst(20, lambda i: call(pick(i) + f"/api/plans/{ids[0]}/decision", "POST", {"decision": "approve", "version": 1}, tokens["menon" if i % 2 else "sharma"])[0])
    outcomes = sorted(r for r, _ in dec)
    check("exactly one accepted, the rest refused", outcomes == [200] + [403] * 19, f"({outcomes.count(200)} accepted, {outcomes.count(403)} refused)")

    print("5. the audit chain afterwards")
    status, verdict, _ = call(urls[0] + "/api/audit/verify", token=tokens["nair"])
    expected_min = 12 + args.writes * 2 + 1
    check("chain unbroken", status == 200 and verdict["ok"], f"({verdict['entries']} entries, at least {expected_min} expected)")
    status, plans, _ = call(urls[-1] + "/api/plans", token=tokens["menon"])
    mine = [p for p in plans if p["id"] in set(ids)]
    check("plans and comments all stored", len(mine) == args.writes and sum(len(p["comments"]) for p in mine) == args.writes)
    print("\nALL CHECKS PASSED" if not bad else f"\n{bad} CHECK(S) FAILED")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
