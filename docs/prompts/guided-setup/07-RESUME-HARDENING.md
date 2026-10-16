# Prompt GS-07 — Resume, Field Guide, Hardening

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phases 01–06 complete. The guided flow works on a happy path in the working tree.
>
> This is the last phase of the series. Do **not** invent new setup steps.

## Role

You are the tech lead closing the guided-setup series: incomplete layouts resume correctly, copy matches the product, and regressions are covered.

## Objective

Implement **Guided Setup Phase 07 only**:

1. Resume / skip logic for saved layouts (including legacy)
2. Home + Field Guide copy
3. AR entry guards
4. Regression tests and a short acceptance script
5. README touch **only** if it still describes the old “Create New Plan → place assets” one-screen flow

## Functional requirements

### 1. Resume matrix (LOCKED)

On `loadLayout`:

| Saved layout | Lands on |
|--------------|----------|
| No region, no origin | FIND_LOCATION |
| Chosen location cannot be in JSON — treat as no location | FIND_LOCATION |
| Region, no origin | SET_ORIGIN (fly to region, apply mask/grid/lock) |
| Region + origin | PLACE_ASSETS (workspace) |
| Origin, no region (legacy) | SET_REGION; keep origin until a new box excludes it |

On `createNewLayout`: always FIND_LOCATION, empty session.

Saving mid-setup **is allowed** (existing Save). Reloading that draft uses the table above. Do not add schema fields for `setupStep` or search hits.

Home list: existing Origin/status chips may remain. Optional: show `Setup incomplete` when `regionBounds == null || !anchor.hasGeographicFix`. Keep it small.

### 2. AR guard

- AR top-bar is already hidden before PLACE_ASSETS.
- If anything still navigates to AR early, block with snackbar `Set region and origin before AR` and stay on Planning.
- Layouts with origin+region may visualize even with **zero** assets (existing “rendering 0 objects” is OK).
- Do not persist AR poses (unchanged rule).

### 3. Field Guide

Update `docs/FieldGuide.md` sections 1–3 to the new order:

1. Find a location (search)
2. Set region (drag or visible area)
3. Set origin inside the region (stamp or tap)
4. Place assets
5. Save
6. AR (unchanged calibration story)

Remove instructions that tell users to expand GEO ORIGIN and set region as parallel modes on one screen. Keep GPS vs relative-precision notes. Keep failure cheat sheet; add:

| Symptom | Action |
|---------|--------|
| Continue disabled on step 1 | Select a search result, not just pan |
| GPS stamp rejected | Stand inside the box or tap the map inside it |
| Cannot place assets | Finish origin (step 3) |

### 4. In-app Field Guide screen

If `FieldGuideScreen.kt` duplicates old steps, update it to match the doc. Do not write an essay; short numbered steps.

### 5. Root README

If `README.md` “Recommended flow” still says Home → Create New Plan → place assets → Save → AR as one screen, update it to the guided sequence. Do **not** rewrite the whole README. Do **not** claim “no INTERNET” if map/search still need network (fix that sentence if you touch the file).

### 6. Prompt / acceptance doc

Append a short “Guided setup” section to `docs/geo-feature-acceptance.md` **or** add `docs/guided-setup-acceptance.md` with a 12-step device script covering: new plan search → region drag → origin tap → place two assets → save → reopen at workspace → change region → AR.

### 7. Edge cases to handle (not defer)

- Configuration change / process death: step is derived from layout + session; session location may be lost → FIND_LOCATION is acceptable; committed region/origin must survive because they are in `Layout` / ViewModel.
- `Clear Canvas` / clear objects: does **not** reset region/origin/step.
- Deleting the last asset: stay on PLACE_ASSETS.
- Double Continue: idempotent.
- Antimeridian: existing GeoBounds failure messages.
- Search server down: cannot leave step 1 without a hit — keep error copy; do not invent a “skip search” backdoor in this phase.

## Tests

1. loadLayout matrix (all four rows) in ViewModel or resolver tests
2. createNewLayout always FIND_LOCATION
3. AR navigation guard if extracted as a predicate `fun canEnterAr(layout): Boolean = region != null && hasGeographicFix`
4. Clear objects does not change setupStep
5. Full existing unit suite still green: `./gradlew :app:testDebugUnitTest`

Fix any tests that still assume origin chip + asset FAB on a brand-new layout.

## Manual QA

Run the new acceptance script on a device if available; otherwise list what could not be verified.

Minimum:

1. New Plan full path to two assets
2. Save, Home, reopen → workspace, assets still there
3. Save after region-only → reopen → SET_ORIGIN with mask/grid
4. Legacy-style layout (if you can construct one in a test) with origin and no region → SET_REGION
5. AR blocked before origin; allowed after
6. Field Guide reads correctly

## Out of scope

- New NavHost wizard
- Schema v3
- Google Places skip-search
- Share/approve backend
- Production 3D models
- Committing unless the user asks

## Acceptance criteria

- [ ] Resume matrix implemented and tested
- [ ] Field Guide + in-app guide match the new flow
- [ ] AR cannot start before region+origin
- [ ] README recommended flow updated if touched
- [ ] Device/unit acceptance documented
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] No new setup steps invented
- [ ] Series complete statement included

## Deliverable summary format

1. Files changed
2. Resume matrix as shipped
3. Tests run (command + result)
4. Manual QA / what was not run on a phone
5. Remaining known limitations (GNSS error, no VPS, LAN tiles, etc.)
6. “Phase 07 complete. Guided setup series finished.”
