# Prompt GS-02 — Find Location Step

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phase 01 complete (`PlanningSetupStep`, HUD gating, resolver tests).
>
> **Do not** implement region Confirm-advance, camera lock, grid overlay, or origin changes. Stay on the Find Location step.

## Role

You are a senior Compose + MapLibre UX engineer turning New Plan’s first moment into a **search-first** location pick.

## Objective

Implement **Guided Setup Phase 02 only**:

On `FIND_LOCATION`, the user searches for a place, picks one result, the map flies there, and they continue to `SET_REGION`.

This is the “location search, then that location opens next” beat — implemented as chrome on the **same** map, not a new route.

## Current behavior (replace for this step only)

- Search is an expandable icon (`PlanningSearchButton` → `PlanningSearchField`)
- `onSearchResultSelected` flies the camera via `MapCameraBridge` and collapses search
- It does not store a setup session location and does not change `setupStep`

## Interaction design (LOCKED)

### Happy path

1. New Plan → `FIND_LOCATION`.
2. Map may show a default worldwide/India view (existing style). Do not require GPS.
3. Search is **the** control: prefer the field **expanded by default** on this step (not a tiny icon). If expanding immediately is awkward with the existing component, auto-expand on first entry to FIND_LOCATION.
4. User types; existing debounce + `/search` results list.
5. User taps a hit:
   - `setChosenLocation` from the hit (lat, lng, displayName, zoom = existing `SEARCH_RESULT_ZOOM`)
   - Fly camera (existing `MapCameraBridge.moveTo`)
   - Collapse the results list but keep the chosen name visible
   - Enable **Continue**
6. **Continue** → `advanceSetupStep()` → `SET_REGION` (Phase 03 will own region chrome; after this phase the user will see the gated SET_REGION UI from Phase 01).
7. Optional: tapping a hit may auto-advance. **Prefer explicit Continue** so the user sees the map land on the place first. If you auto-advance, still store `chosenLocation` and show a snackbar `"Centered on {name}"`.

### Continue rules

- Disabled until `chosenLocation != null`
- Copy: **Continue** (Moss primary)
- Helper text when disabled: `Search and select a place to continue`

### Back

- Already handled: FIND_LOCATION back → Home (Phase 01)
- Going **forward then back** from SET_REGION (Phase 01 API) must restore FIND_LOCATION **with the previous query/hit still in session** so they are not forced to retype from scratch. Preserve `chosenLocation` when stepping back.

### What this step must not do

- Do not stamp origin from the search hit
- Do not write `regionBounds`
- Do not persist the hit into JSON
- Do not show region overlay, origin chip, assets, AR

## Functional requirements

### 1. Session location

`ChosenMapLocation` from Phase 01 must be filled from `PlaceSearchHit`.

Keep search collapse behavior for the results list; show a one-line confirmation chip under the field:

`Selected: {displayName}`

### 2. HUD for FIND_LOCATION

- Full-width search field (expanded)
- Continue bar at the bottom (primary button + short hint)
- Step caption `Step 1 of 3 · Find location`
- Map still pan/zoomable so they can look around **after** a hit. Panning does **not** clear `chosenLocation`. A new hit replaces it.

### 3. No hit → no advance

Typing without selecting a result must not enable Continue. Camera pan without a hit must not enable Continue.

If search is unconfigured / errors, keep existing error copy (`SEARCH_UNAVAILABLE` etc.). Continue stays disabled.

### 4. Do not force MAP_SERVER

If `MAP_SERVER_BASE` is empty, existing fallback messaging stays. Do not add Google Places.

## Out of scope

- Region drag / Confirm
- Grid, mask, maxBounds
- Origin
- Changing `PlaceSearchGateway` protocol
- New NavHost route named “Search”

## Tests

Extend resolver/ViewModel tests:

1. `setChosenLocation` then `advanceSetupStep` → SET_REGION
2. `advanceSetupStep` from FIND_LOCATION with null chosen location → stays FIND_LOCATION + feedback
3. `goToPreviousSetupStep` from SET_REGION keeps `chosenLocation`
4. `createNewLayout` clears `chosenLocation`

UI-facing pure helpers (e.g. Continue enabled predicate) should be unit-tested if extracted.

## Manual QA

1. New Plan → search field is the obvious control; no origin/assets/AR
2. Query a known place (e.g. Delhi if the LAN search server is up) → pick hit → map flies there → name shown → Continue enabled
3. Continue → step becomes SET_REGION (Phase 01 chrome: region tools, no search field)
4. System back → FIND_LOCATION with the same selected place still shown
5. Continue without a hit is impossible
6. Search error path still shows a friendly message

## Acceptance criteria

- [ ] FIND_LOCATION is search-first; results pick stores `chosenLocation`
- [ ] Continue (or documented auto-advance) moves to SET_REGION only after a hit
- [ ] Search hit does not write origin or region JSON
- [ ] Back from SET_REGION restores FIND_LOCATION with session location kept
- [ ] No new navigation destination
- [ ] Compiles; tests pass
- [ ] Did **not** start Phase 03

## Deliverable summary format

1. Files changed
2. Find-location interaction summary (3–5 bullets)
3. Manual QA results
4. Deferred polish
5. “Phase 02 complete; do not start Phase 03 unless asked”
