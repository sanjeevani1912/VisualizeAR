# Prompt GS-01 — Setup Step Machine + HUD Gating

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Geo 01–06 and Field Kit UX 01–06 present in the tree (`RegionDraftOverlay`, `GeoOriginChip`, search field).
>
> **Do not** implement search-first Continue UX, camera lock, region mask, map grid overlay, origin-inside-region rejection, or asset-workspace redesign. Those are later phases. This phase only adds the **step model**, **entry points**, and **hides chrome the user should not see yet**.

## Role

You are a senior Android Kotlin engineer introducing a finite setup-state machine into DhruvAR’s existing `PlanningViewModel` without splitting the Planning route.

## Objective

Implement **Guided Setup Phase 01 only**:

1. Add `PlanningSetupStep`
2. Add a pure `resolvePlanningSetupStep` helper with unit tests
3. Wire `createNewLayout` / `loadLayout` / explicit advance+back APIs
4. Gate `PlanningScreen` HUD so a **New Plan** no longer shows origin, assets, mode rail, Map/Grid, or AR on first open

After this phase the map still opens, but it is visually quiet. Search may remain visible (Phase 02 will make it the hero). Region overlay code may stay mounted **only** when the step is `SET_REGION`. Do not build new region UX here.

## Functional requirements

### 1. Domain type

Add (suggested location `domain/model/PlanningModes.kt` or a new `PlanningSetupStep.kt`):

```kotlin
enum class PlanningSetupStep {
    FIND_LOCATION,
    SET_REGION,
    SET_ORIGIN,
    PLACE_ASSETS
}
```

### 2. Pure resolver

New testable file, e.g. `domain/model/PlanningSetupResolver.kt`:

```kotlin
data class PlanningSetupSession(
    val chosenLocation: ChosenMapLocation? = null
)

data class ChosenMapLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val zoom: Double? = null
)

fun resolvePlanningSetupStep(
    layout: Layout,
    session: PlanningSetupSession,
    isBrandNewLayout: Boolean
): PlanningSetupStep
```

Implement the locked table in `00-SETUP-SHARED.md`:

- Brand-new layout → always `FIND_LOCATION` (ignore leftover session)
- Else if `regionBounds == null`:
  - chosen location present → `SET_REGION`
  - else → `FIND_LOCATION`
- Else if `!anchor.hasGeographicFix` → `SET_ORIGIN`
- Else → `PLACE_ASSETS`
- Legacy: geo fix but no region → `SET_REGION`

Do **not** put Android types in this file.

### 3. ViewModel

Extend `PlanningUiState` with:

- `setupStep: PlanningSetupStep`
- `setupSession: PlanningSetupSession` (or flatten `chosenLocation`)
- `isBrandNewLayout: Boolean` (or equivalent flag you reset on load)

APIs (names may match project style):

| Function | Behavior |
|----------|----------|
| `createNewLayout` | Existing blank layout **plus** `setupStep = FIND_LOCATION`, clear chosen location, `mapInteractionMode = NAVIGATE`, collapse search/origin |
| `loadLayout` | Copy layout in, then `setupStep = resolve...(layout, empty session, isBrandNew=false)` |
| `advanceSetupStep()` | FIND_LOCATION→SET_REGION only if chosen location exists; SET_REGION→SET_ORIGIN only if `regionBounds != null`; SET_ORIGIN→PLACE_ASSETS only if `hasGeographicFix`. Else snackbar, no-op. |
| `goToPreviousSetupStep()` | PLACE_ASSETS is **not** stepped back by this API (workspace back = Home). SET_ORIGIN→SET_REGION. SET_REGION→FIND_LOCATION. FIND_LOCATION = no-op (UI pops Home). |
| `setChosenLocation(...)` | Stores session location. Does **not** write JSON. Does **not** auto-advance in this phase (Phase 02 will). |

When `setRegionBounds` succeeds in later phases it will advance; **in this phase** you may leave region APIs as they are. Optional: if step is `SET_REGION` and bounds become non-null, do **not** auto-advance yet (Phase 03 owns Confirm → next). Document that.

`clearRegionBounds` / `clearGeographicAnchor` during setup: if the user is already past that step, drop them back via resolver (e.g. clearing origin while in PLACE_ASSETS → SET_ORIGIN). Implement this **small** consistency now so later phases cannot desync.

### 4. HUD gating (minimum viable)

Update `planningHudToolsFor` **or** wrap it so it also takes `PlanningSetupStep`.

Phase 01 visibility (must match the matrix, even if the remaining chrome is still ugly):

| Step | Show |
|------|------|
| FIND_LOCATION | Search control. Hide origin chip, surface switcher, mode rail, region tools, asset picker, AR button. |
| SET_REGION | Region tools + overlay path. Hide search (keep code, don’t show), origin, assets, AR, surface switcher, mode rail. |
| SET_ORIGIN | Origin chip/sheet. Hide search, region tools, assets, AR, mode rail, surface switcher. |
| PLACE_ASSETS | Current HUD (search optional hidden is OK; origin chip, rail, assets, AR, switcher as today). |

Top bar:

- FIND_LOCATION / SET_REGION / SET_ORIGIN: title from shared copy; **hide** AR `IconButton`.
- PLACE_ASSETS: existing Planning title + AR.

Add a simple step caption under the title or as subtitle, e.g. `Step 1 of 3`, only when `setupStep != PLACE_ASSETS`.

System `BackHandler` on Planning:

- If `setupStep` is SET_REGION or SET_ORIGIN → `goToPreviousSetupStep()`
- If FIND_LOCATION or PLACE_ASSETS → existing `onNavigateBack` (Home)

Do not add Continue buttons yet (Phase 02/03/05). Gating is enough.

### 5. Map interaction mode while gated

Force / clamp:

- FIND_LOCATION → `NAVIGATE` (ignore rail; rail is hidden)
- SET_REGION → `SELECT_REGION`
- SET_ORIGIN → `NAVIGATE` until Phase 05 sets `SET_ANCHOR` from the sheet
- PLACE_ASSETS → unchanged

If some other code sets an illegal mode for the current step, coerce it back. Add a small helper `fun allowedModes(step): Set<MapInteractionMode>` and test it.

## Out of scope

- Search auto-advance, Continue, or requiring a hit
- Region Confirm advancing the step
- Camera `maxBounds`, dim mask, grid layer
- Rejecting origin taps outside region (beyond today’s placement reject)
- Field Guide rewrite
- New navigation routes
- Schema changes

## Tests

Add `app/src/test/java/com/example/dhruvar/PlanningSetupResolverTest.kt` covering:

1. Brand-new layout → FIND_LOCATION
2. Empty layout, no session → FIND_LOCATION
3. Empty layout, chosen location → SET_REGION
4. Region only → SET_ORIGIN
5. Region + geo fix → PLACE_ASSETS
6. Geo fix, no region (legacy) → SET_REGION
7. `allowedModes` / illegal mode coercion if extracted

Add or extend a ViewModel unit test (see existing `MapPlanningViewModelTest` / similar) for:

- `createNewLayout` sets FIND_LOCATION
- `loadLayout` with full geo+region sets PLACE_ASSETS
- `advanceSetupStep` blocked without location / region / origin
- `goToPreviousSetupStep` SET_ORIGIN → SET_REGION → FIND_LOCATION
- clearing origin on a complete layout drops step to SET_ORIGIN

Existing tests must still compile. Update any HUD tests (`PlanningHudToolsTest`) for the new `setupStep` parameter.

## Manual QA

1. New Plan → Planning opens. **No** origin chip, **no** asset FAB, **no** mode rail, **no** AR icon. Search may be visible.
2. Open a saved layout that already has region + origin → full current HUD (PLACE_ASSETS).
3. Device back from New Plan (FIND_LOCATION) returns Home.
4. App compiles.

## Acceptance criteria

- [ ] `PlanningSetupStep` exists and is on `PlanningUiState`
- [ ] Pure resolver + unit tests match the locked table
- [ ] New Plan starts at FIND_LOCATION with chrome hidden per matrix
- [ ] Saved complete layouts open at PLACE_ASSETS
- [ ] BackHandler steps backward only during SET_REGION / SET_ORIGIN
- [ ] No new NavHost routes
- [ ] No grid / mask / Continue / origin-inside-region work
- [ ] Compiles; new tests pass
- [ ] Did **not** start Phase 02

## Deliverable summary format

1. Files changed
2. Step transition table as implemented
3. Manual QA results
4. Deferred items
5. “Phase 01 complete; do not start Phase 02 unless asked”
