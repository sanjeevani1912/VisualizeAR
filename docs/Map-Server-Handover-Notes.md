# Map server — findings and handover notes

Notes for the map server owner, written from the Android development PC (`192.168.1.206`) on
2026-10-03 while the server host was running.

**Summary: the server works.** Tiles, label fonts, place search and the offline archive all respond
correctly across the LAN. One configuration item is outstanding and currently blocks use from any
machine other than the server itself. It is worked around on the Android side for now, but should be
fixed properly on the server.

## Server as observed

| Item | Value |
|------|-------|
| Host | `192.168.1.205` (Wi-Fi adapter, DHCP, gateway `192.168.1.1`) |
| Base URL in use by the app | `http://192.168.1.205:8080` |
| Front door | Caddy 2, host port 8080 |
| Tiles | `protomaps/go-pmtiles:v1.31.2` |
| Search | `getmeili/meilisearch:v1.54` |
| One-shot init | `site-init` (`alpine:3`) — exits after running, by design |

### Reachability from another machine on the LAN

| Port | State | Notes |
|------|-------|-------|
| 8080 | open | HTTP, serves everything |
| 8443 | open at TCP level, **TLS handshake fails** | no certificate configured |
| 80, 443 | closed | |
| 7700 | closed | Meilisearch not exposed to the LAN — good |
| 2019 | closed | Caddy admin API not exposed to the LAN — good |

### Endpoints verified working over the LAN

| Endpoint | Result |
|----------|--------|
| `GET /search?q=delhi` | 200, 1042 estimated hits |
| `GET /style.json` | 200, 6351 bytes |
| `GET /tiles/india.json` | 200, 8617 bytes |
| `GET /fonts/Noto%20Sans%20Regular/0-255.pbf` | 200, 76580 bytes |
| `GET /tiles/india/10/722/437.mvt` | 200, 26767 bytes |
| `HEAD /files/india.pmtiles` | 200, 2 065 188 239 bytes, `Accept-Ranges: bytes` |

## Issue 1 — base URL substitution was never applied (blocking)

`style.json` and `tiles/india.json` advertise `http://localhost:8080`. A client on any other machine
resolves `localhost` to itself, so the style loads but every tile and glyph request fails. The symptom
is a blank map with no error.

`style.json` states the intended fix in its own metadata:

```json
"metadata": {
  "note": "Replace http://localhost:8080 with the public base URL (deploy/install.sh does this with sed). No sprite: symbols are text-only."
}
```

The three affected references:

- `sources.openmaptiles.url` → `http://localhost:8080/tiles/india.json`
- `glyphs` → `http://localhost:8080/fonts/{fontstack}/{range}.pbf`
- inside `tiles/india.json`, `tiles[0]` → `http://localhost:8080/tiles/india/{z}/{x}/{y}.mvt`

**Requested fix:** re-run `deploy/install.sh` (or set whatever `PUBLIC_URL` it reads) with the real
base URL so the substitution happens, then restart the stack.

**Interim workaround on the app side:** the Android app fetches `style.json`, rewrites the
`http://localhost:8080` prefix to its configured base, and hands the corrected style to MapLibre. This
keeps working regardless of what the server advertises, so applying the server fix will not break the
app.

## Issue 2 — HTTPS is mapped but not functional

Host port 8443 is published and accepts TCP connections, but the TLS handshake fails; no certificate
appears to be configured. Caddy's own logs agree:

```text
HTTP/2 skipped because it requires TLS
HTTP/3 skipped because it requires TLS
server is listening only on the HTTP port, so no automatic HTTPS will be applied to this server
```

All traffic is therefore cleartext HTTP. The map data is public OpenStreetMap content, but **search
queries are not** — a passive observer on the same Wi-Fi can see exactly which places and areas were
looked up. For an operational planning tool that is the more sensitive signal.

Because of this, the Android app needs an explicit cleartext-traffic exception. It is scoped to the
single host `192.168.1.205` rather than enabled app-wide, and should be removed once TLS works.

**Requested fix:** finish the TLS configuration on the 8443 listener (an internal CA or self-signed
certificate is fine for LAN use, as long as the certificate can be pinned or trusted on the devices).

## Issue 3 — the host address will drift

`192.168.1.205` comes from DHCP on a Wi-Fi adapter, so it can change without warning. If it changes,
the map silently stops working and the cause is non-obvious.

**Requested fix:** a DHCP reservation or static lease for the host, or a resolvable hostname.

## Issue 4 — port 8080 collision with another container

A stopped `wikipedia` container (`kiwix/kiwix-serve:3.8.2`) publishes `8080:8080`, the same host port
the map server's Caddy uses. Starting it will break the map server. Worth re-mapping one of the two.

## API observations relevant to the Android client

These differ slightly from `docs/android.md` and are what the app is being built against.

- Default `limit` is **20**, not 10. The app sends `limit` explicitly.
- The response envelope includes `processingTimeMs`, `limit`, `offset`, `estimatedTotalHits` and
  `requestUid` alongside `hits`.
- `name_en` is present only when the local `name` is in a non-Latin script. `Delhi` returns no
  `name_en`; `Muzaffarabad` returns `name` in Urdu plus `name_en: "Muzaffarabad"`. The app therefore
  displays `name_en ?: name`.
- `_geo` carries `lat` and `lng` on every hit observed.
- `tiles` container logs `Access-Control-Allow-Origin: *`, so browser-based tooling also works.

## Questions for the server owner

1. Can the base URL substitution be applied so `style.json` advertises the real host?
2. Can TLS be completed on 8443? If so, which certificate authority should the devices trust?
3. Is the search-only credential arrangement confirmed — i.e. only `/search` is proxied and the
   Meilisearch master key is never reachable from a client? (7700 being closed suggests yes.)
4. What is the plan for field use, where this Wi-Fi network will not exist? Options appear to be
   shipping the 1.92 GB `india.pmtiles` onto devices, or running the stack on a portable host.
5. Should the server remain unauthenticated? Acceptable on a closed LAN; not acceptable if it is ever
   port-forwarded or internet-facing, since the full archive is downloadable by anyone who can reach it.

## Reproducing these checks

From any machine on the LAN:

```powershell
Invoke-WebRequest 'http://192.168.1.205:8080/search?q=delhi&limit=2' -UseBasicParsing
Invoke-WebRequest 'http://192.168.1.205:8080/style.json' -UseBasicParsing
Invoke-WebRequest 'http://192.168.1.205:8080/tiles/india.json' -UseBasicParsing
curl.exe -k -v --max-time 10 -o NUL https://192.168.1.205:8443/
```
