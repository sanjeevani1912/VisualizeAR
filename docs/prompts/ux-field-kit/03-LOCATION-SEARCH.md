# Prompt UX-03 — Expandable Location Search

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** UX-01 theme + UX-02 contextual HUD (search icon slot exists).
>
> **Do not** implement region corner-drag, origin hybrid redesign, or AR briefing changes.

## Role

You are a senior Android networking + maps engineer adding professional place search to the Planning map without cluttering the HUD.

## Objective

Implement **UX Phase 03 only**:

- Search **icon** in the Planning HUD top zone
- Tap expands a search field + results dropdown
- Query the map server `GET {BASE}/search?q=…&limit=…`
- Selecting a hit flies the MapLibre camera to that coordinate
- Collapse restores the clean icon-only chrome

## Server contract (source of truth)

Read and follow `docs/android.md` §6 (and the copy under VM share / project `docs/android.md` if present).

```http
GET {BASE}/search?q=<text>&limit=10
```

No API key headers from the app.

Example hit fields:

- `name`, `name_en`, `names[]`
- `kind`, `subtype`, `rank`
- `_geo.lat`, `_geo.lng`
- `id`

Display label: prefer `name_en` then `name`.

### Client behavior requirements

- Debounce ~250ms
- Cancel in-flight request when query changes
- Typing prefix / typo tolerance is server-side — just send `q`
- Prefer Latin/`name_en`-style queries in UI placeholder hint (“Search place (English)”)
- Optional (implement if easy): when device has a fix, add `sort=_geoPoint(lat,lng):asc`
- Optional filter for places: `filter=kind = place AND rank <= 2` — only if it improves results; document choice
- On `5xx` / network error: show inline “Search unavailable”; map still works
- Empty query: no request / clear results
- Limit default 10

## Config

Today `BuildConfig.MAP_STYLE_URL` may point at OpenFreeMap (no `/search`).

You **must** add a clean config path:

1. Add `BuildConfig` field(s), e.g.:
   - `MAP_SERVER_BASE` = `""` or a documented default for the team’s server  
   - and/or `MAP_SEARCH_URL` full template
2. Derive search endpoint as `{MAP_SERVER_BASE}/search` when base is set
3. Extend `MapBasemapConfig` with `searchUrl()` / `isSearchConfigured(): Boolean`
4. Update `docs/PMTiles-Configuration.md` **or** add `docs/Map-Search-Configuration.md` explaining how to set the base for emulator (`http://10.0.2.2:8080`) vs device LAN vs production HTTPS
5. If search is not configured: icon still visible; expanding shows a calm message “Map search server not configured” + link/text pointing at the doc — **no crash**

Do **not** hardcode a secret. Cleartext HTTP for local/dev must respect existing network security config patterns in the project (extend if needed for documented host only).

## UI / UX (locked: icon expands)

### Collapsed
- Search icon button in the UX-02 top HUD slot
- Field Kit styling (Coal icon on GoldenHour/Mist)

### Expanded
- Search `OutlinedTextField` / Material3 field with clear (X) and collapse
- Dropdown / lazy list of hits under the field (max height constrained so map remains usable)
- Each row: primary name, secondary `subtype`/`kind`, optional rank
- Tap row:
  - Animate/move map camera to `_geo` at zoom ~15 (or `MapBasemapConfig.ANCHOR_ZOOM` if appropriate)
  - Collapse search (or keep query — prefer collapse + snackbar “Centered on {name}”)
- Back / outside tap / collapse icon closes expansion and clears focus
- While expanded, do not permanently cover the entire map; keep results to top portion

### Grid surface
- Search may still be offered if map will be shown after switch, **or** hide search on Grid — prefer: allow search but switching to Map if a result is chosen (better UX). Document choice.

## Architecture

Suggested packages (adjust to project style):

- `data/map/PlaceSearchApi.kt` or `data/map/MapPlaceSearchClient.kt` — OkHttp (add dependency if not present) or existing HTTP stack
- `domain/model/PlaceSearchHit.kt` — pure data
- Parsing: `org.json` already used in project — fine
- ViewModel: either extend `PlanningViewModel` with search UI state **or** a small `PlaceSearchViewModel` scoped to Planning — prefer extending Planning with a nested UI state to avoid dual owners of camera

State to track:

- `searchExpanded: Boolean`
- `query: String`
- `results: List<PlaceSearchHit>`
- `isSearching: Boolean`
- `searchError: String?`

Camera move:

- Add a one-shot event / bridge similar to `MapViewportBoundsBridge`, e.g. `MapCameraBridge.moveTo(lat, lng, zoom)` consumed by `PlanningMapView`
- Do not rebuild the whole map on every keystroke

## Implementation constraints

- **No Google Places**
- Do not change region/origin semantics
- Do not start UX-04 drag gestures
- Use Field Kit colors; contextual HUD slots from UX-02
- Cancel coroutines on dispose / clear

## Tests

- Unit test JSON parsing of a sample search payload (use trimmed example from `docs/android.md`)
- Unit test: empty / missing `_geo` hits are skipped or marked invalid
- Optional: fake client tests for debounce cancellation logic if extracted

## Manual QA

1. With `MAP_SERVER_BASE` unset → expand search → friendly not-configured message
2. With server configured → type “delhi” (or known place) → results appear within ~1s after debounce
3. Tap a result → map flies to location; search collapses
4. Type rapidly → no crash; no pile of stale results from old responses
5. Airplane mode → error message; map still pans
6. Collapse via X / icon → top HUD returns to clean icon

## Acceptance criteria

- [ ] Search icon expands/collapses cleanly in HUD top zone
- [ ] BuildConfig + `MapBasemapConfig` expose search base/URL; documented
- [ ] Live query hits server `/search` with debounce + cancellation
- [ ] Result tap moves MapLibre camera
- [ ] Unconfigured / error states are calm and non-fatal
- [ ] Tests for parse path
- [ ] Compiles; existing tests still pass
- [ ] Did **not** implement region drag, origin hybrid, or AR collapse

## Deliverable summary format

1. Files changed
2. Config keys added + example values for emulator/device
3. How to manually test against the team map server
4. Known limitations (e.g. street names not indexed — per server docs)
