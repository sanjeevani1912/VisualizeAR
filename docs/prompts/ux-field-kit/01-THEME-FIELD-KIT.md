# Prompt UX-01 — Force Light Field Kit Theme + Map Overlay Contrast

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** Geo map/AR features already present. No dependency on other UX phases.
>
> **Do not** implement HUD restructure, search, region drag, origin hybrid redesign, or AR briefing collapse in this phase.

## Role

You are a senior Android Compose UI engineer establishing a permanent **military day-ops light** visual system for DhruvAR.

## Objective

Implement **UX Phase 01 only**:

1. Replace the slate/blue palette with the locked Field Kit palette
2. **Force light theme always** — never follow system dark mode
3. Pass map/AR overlay surfaces so cream panels stay readable on vector tiles and camera feed without reverting to dark-glass HUD everywhere

## Functional requirements

### 1. Color tokens

Update `app/src/main/java/com/example/dhruvar/ui/theme/Color.kt`:

- Remove or deprecate as primary brand: `Slate*`, `PrecisionBlue*`, old `TacticalGreen`/`TacticalAmber`/`AnchorOrange` **as the main system** (you may keep temporary typealiases that point to Field Kit equivalents during migration, but prefer renaming call sites).
- Add named constants:

```kotlin
val Cacao = Color(0xFF603620)
val Coal = Color(0xFF231F20)
val GoldenHour = Color(0xFFF3E4BD)
val Rust = Color(0xFFB56B37)
val Mist = Color(0xFFFCF9F2)
val Moss = Color(0xFF63703D)
val Meadow = Color(0xFFB5C37C)
```

- Add semantic aliases used by UI (examples — names can match project style):
  - `FieldBackground = Mist`
  - `FieldSurface = GoldenHour`
  - `FieldOnSurface = Coal`
  - `FieldPrimary = Moss`
  - `FieldWarning = Rust`
  - `FieldSuccess = Moss` (or Meadow only where contrast on Mist is verified)
  - `FieldAnchor = Rust` or `Cacao` for origin marker emphasis (pick one; document in summary)

### 2. Force light Material scheme

Update `ui/theme/Theme.kt`:

- Build a single `LightColorScheme` (Field Kit mapping from `00-UX-SHARED.md`)
- `DhruvARTheme` must **ignore** system dark:
  - Either remove `darkTheme` parameter, or force `val colorScheme = LightColorScheme` always
  - Do **not** call `isSystemInDarkTheme()` for scheme selection
- If a `DarkColorScheme` remains, it must be unused (prefer delete to avoid accidental use)

Update call sites (`MainActivity` / root): ensure no `darkTheme = …` override reintroduces dark.

### 3. Typography / chrome components

- Review `Type.kt` — ensure body/title colors rely on `MaterialTheme.colorScheme`, not hardcoded white-on-dark assumptions
- Update shared chrome that hardcodes old colors:
  - `AppTopBar.kt`
  - `PrimaryButton.kt`
  - Any `HomeScreen` status chips that use PrecisionBlue/Slate
- Top bars / buttons should read as **light field kit**, not dark navy bars with blue accents

### 4. Map overlay contrast pass (same phase)

Planning overlays sit on MapLibre tiles. Mist/GoldenHour panels can wash out or look illegible.

Required:

- Overlay surfaces (origin panel, mode toolbar, surface switcher, snackbars-adjacent surfaces) use:
  - `GoldenHour` or `Mist` fill at high opacity (≥ ~0.94)
  - Thin `Cacao` or `Coal` border / elevation so panels separate from map
  - Text/icons in `Coal`; selected mode uses `Moss` (not blue)
- Region polygon / fill on map (if already drawn in `PlanningMapView`): use `Meadow`/`Moss` with transparent fill + clear stroke — readable on Liberty/OSM styles
- Anchor marker color: Field Kit (Rust/Cacao), not AnchorOrange/PrecisionBlue
- Do **not** redesign layout positions/HUD zoning (that is UX-02). Only recolor / contrast existing chrome.

### 5. AR screen interim contrast

AR Visualize currently uses dark translucent pills (`Color(0xCC0F172A)` etc.).

In this phase:

- Replace those hardcoded dark glasses with Field Kit light translucent surfaces **or** Material theme surfaces so the app does not fight the new brand
- Keep layout/structure identical (collapse behavior is UX-06)
- Ensure text remains readable over camera: if pure Mist is too bright, use `GoldenHour` @ ~0.90–0.95 with Coal text and a thin Cacao border — still light, not dark mode

### 6. Home / other screens

Apply the same scheme everywhere `DhruvARTheme` wraps content (Home, Field Guide, Planning, AR). No orphan screens left on old blue/slate.

## Implementation constraints

- Do not add new dependencies
- Do not change geo logic, MapLibre style URL, or ViewModel behavior except if a color constant rename forces import updates
- Prefer `MaterialTheme.colorScheme.*` in composables over one-off hex
- Keep changes reviewable: theme files + call-site color migrations

## Tests

- No strict screenshot tests required
- Ensure project still compiles; run existing unit tests
- If any test asserts color hex values (unlikely), update them

## Manual QA

1. Set device to **system dark mode** → launch app → UI stays light Field Kit (mist/golden, moss accents)
2. Set device to system light mode → same look
3. Open Planning Map → panels readable over basemap; selected mode uses moss, not blue
4. Open AR Visualize → top chips/panels are light Field Kit, readable over camera
5. Home list / buttons match the same palette

## Acceptance criteria

- [ ] Field Kit hex tokens exist and are used as the brand system
- [ ] System dark mode cannot turn the app dark
- [ ] No primary UI still branded PrecisionBlue / slate dark surfaces (except possibly unused dead code removed)
- [ ] Map overlays have clear edges / readable Coal text on cream panels
- [ ] AR temporary overlays migrated off navy glass toward Field Kit light surfaces (structure unchanged)
- [ ] Compiles; existing tests pass
- [ ] Summary lists files changed + QA notes
- [ ] Explicitly did **not** start UX-02+

## Deliverable summary format

When done, reply with:

1. Files changed
2. Material3 role → hex mapping table you implemented
3. Manual QA results
4. Any leftover hardcoded colors deferred (must be empty or justified)
