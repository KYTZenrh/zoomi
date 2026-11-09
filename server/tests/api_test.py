#!/usr/bin/env python3
"""
Black-box tests for the Zoomi sync server (run against a running instance).
  python server/tests/api_test.py [base_url]     (default http://localhost:8090)
Exit code 0 = all passed. Uses only the Python standard library.
"""
import json, sys, time, uuid, urllib.request, urllib.error

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090").rstrip("/")
results = []


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json", "User-Agent": "zoomi-server-test"})
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw.decode(errors="replace")}


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"   -> {detail}"))


def register_and_login(username, password):
    s, r = call("POST", "/api/collections/accounts/records",
                {"username": username, "password": password, "passwordConfirm": password})
    check(f"register {username}", s == 200, (s, r))
    s, r = call("POST", "/api/collections/accounts/auth-with-password", {"identity": username, "password": password})
    check(f"login {username}", s == 200 and "token" in r, (s, r))
    return r.get("token"), r.get("record", {}).get("id"), r.get("record", {})


u1, u2 = "t" + uuid.uuid4().hex[:10], "t" + uuid.uuid4().hex[:10]
pw = "derived-" + uuid.uuid4().hex  # the app sends a PBKDF2-derived password, never the real one
tok1, id1, rec1 = register_and_login(u1, pw)
time.sleep(1)
tok2, id2, _ = register_and_login(u2, pw)

check("account record has no email exposed", not rec1.get("email"), rec1)

s, r = call("POST", "/api/collections/accounts/auth-with-password", {"identity": u1, "password": "wrong-password"})
check("wrong password rejected", s == 400, (s, r))

s, r = call("POST", "/api/collections/accounts/records", {"username": "Bad Name!", "password": pw, "passwordConfirm": pw})
check("invalid username rejected", s == 400, (s, r))

# Blob lifecycle
s, blob = call("POST", "/api/collections/sync_blobs/records", {"account": id1, "ciphertext": "v1:AAAA", "version": 1}, tok1)
check("create blob v1", s == 200 and blob.get("version") == 1, (s, blob))
bid = blob.get("id")

s, r = call("POST", "/api/collections/sync_blobs/records", {"account": id1, "ciphertext": "v1:BBBB", "version": 1}, tok1)
check("second blob for same account rejected (unique)", s == 400, (s, r))

s, r = call("POST", "/api/collections/sync_blobs/records", {"account": id1, "ciphertext": "v1:EVIL", "version": 1}, tok2)
check("cannot create blob for another account", s in (400, 403), (s, r))

s, r = call("PATCH", f"/api/collections/sync_blobs/records/{bid}", {"ciphertext": "v1:CCCC", "version": 2}, tok1)
check("update to v2", s == 200 and r.get("version") == 2, (s, r))

s, r = call("PATCH", f"/api/collections/sync_blobs/records/{bid}", {"ciphertext": "v1:STALE", "version": 2}, tok1)
check("stale update (v2 again) rejected as conflict", s == 400 and "version_conflict" in json.dumps(r), (s, r))

s, r = call("PATCH", f"/api/collections/sync_blobs/records/{bid}", {"account": id2, "version": 3}, tok1)
check("cannot move blob to another account", s in (400, 403, 404), (s, r))

s, r = call("GET", f"/api/collections/sync_blobs/records/{bid}", None, tok2)
check("other account cannot read blob", s == 404, (s, r))

s, r = call("GET", "/api/collections/sync_blobs/records", None, tok2)
check("other account lists nothing", s == 200 and r.get("totalItems") == 0, (s, r))

s, r = call("GET", "/api/collections/sync_blobs/records", None, None)
check("anonymous lists nothing", s == 200 and r.get("totalItems") == 0, (s, r))

s, r = call("GET", "/api/collections/accounts/records", None, tok2)
check("accounts list shows only self", s == 200 and r.get("totalItems") == 1, (s, r))

# Export (what the server holds about me)
s, r = call("GET", f"/api/collections/accounts/records/{id1}", None, tok1)
check("read own account record", s == 200 and r.get("username") == u1, (s, r))

# Delete account -> blob cascades
s, r = call("DELETE", f"/api/collections/accounts/records/{id2}", None, tok1)
check("cannot delete someone else's account", s in (403, 404), (s, r))
s, r = call("DELETE", f"/api/collections/accounts/records/{id1}", None, tok1)
check("delete own account", s == 204, (s, r))
s, r = call("POST", "/api/collections/accounts/auth-with-password", {"identity": u1, "password": pw})
check("deleted account can't log in", s == 400, (s, r))
s, r = call("GET", f"/api/collections/sync_blobs/records/{bid}", None, tok2)
check("blob gone after account deletion (cascade)", s == 404, (s, r))
s, r = call("DELETE", f"/api/collections/accounts/records/{id2}", None, tok2)
check("cleanup second account", s == 204, (s, r))

# Rate limit on login attempts (5/min per IP)
codes = [call("POST", "/api/collections/accounts/auth-with-password", {"identity": "nobody", "password": "x" * 10})[0] for _ in range(8)]
check("login attempts are rate limited", 429 in codes, codes)

failed = [n for n, ok in results if not ok]
print(f"\n{len(results) - len(failed)}/{len(results)} passed")
sys.exit(1 if failed else 0)
