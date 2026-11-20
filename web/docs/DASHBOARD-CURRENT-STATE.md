# Dashboard: current state (milestone C1, before any change)

Written 2026-11-20 by session C. Branch `web/dashboard-polish` (worktree `DhruvAR-web`, started from `main` at 47819c1).
No dashboard code was changed for this document.

## 1. What was tested

The phone app's own export `server/scripts/fixtures/app-layout-v2.json` (schema v2: region box, origin with heading 12.5 degrees
and accuracy 4.2 m, 6 assets: one of each current type, rotations 0 to 75 degrees) was sent as a plan by the planner `rao`
(`POST /api/plans`) to a **private** API on port 4000 with a throwaway database. A reviewer (`menon`) then opened it in the
dashboard on port 5173. The throwaway database and both servers were removed afterwards. The demo database was not touched.

## 2. Does the dashboard show the plan exactly as the app would?

**Yes, for everything that can be computed.** Checked by reading the plan back from the API and comparing:

| Check | Result |
|---|---|
| Stored layout vs the file sent: anchor, region, all 6 objects (x, z, rotation, width, length, height) | Numerically identical (largest difference 0). The server only drops fields it does not keep (see section 4) and re-orders keys. |
| Asset count, types, sizes in the table | 6 assets, correct types, sizes shown to the centimetre, same as the file |
| Region size | 488 m x 556 m (computed from the region box) |
| Origin / heading / accuracy | 28.61400 N, 77.21000 E / 12.5 degrees / 4.2 m |
| Footprint corners: dashboard `footprintRing` vs an independent re-implementation of the app's `MapLayoutProjection.objectFootprintLatLngs` + `GeographicCoordinateConverter` (including the app's 32-bit float rounding of x and z) | **Largest difference 0.0000014 m (1.4 micrometres)** over 30 corners |
| Asset centres, same comparison | Difference 0 |
| Round trip: dashboard lat/lng converted back to metres with the app's `latLngToMetersOffset` formulas | Largest error 5e-10 m |

Where the independent check lives: it is a throwaway script (not committed). It re-writes the Kotlin formulas
(`north = dLat*R`, `east = dLng*R*cos(lat)`, `x = e*cosH - n*sinH`, `z = e*sinH + n*cosH`, R = 6,371,008.7714 m, corner order
`(-w,-l) (w,-l) (w,l) (-w,l)`, rotation `rx = lx*cosR - lz*sinR`) separately from `web/src/geo.ts` and compares.
Because both are the same maths the match is expected; this confirms nobody changed one side without the other.

## 3. Heading and rotation (bearing)

The object's long axis (its "forward" direction, local +z) points at geographic bearing **heading - rotationDegrees**, i.e.
`12.5 - rotation` for this plan. I measured the bearing of the drawn footprint's length edge for all 6 assets:

| Asset | rotation | expected 12.5 - rot | drawn |
|---|---|---|---|
| Gunship | 0 | 12.5 | 12.500 |
| K-131 | 15 | 357.5 | 357.500 |
| K-131 Take-Off | 30 | 342.5 | 342.500 |
| Military Vehicle | 45 | 327.5 | 327.500 |
| Off-road Vehicle | 60 | 312.5 | 312.500 |
| STR-MGS | 75 | 297.5 | 297.500 |

So the rule used by C2 for icon rotation is: `icon-rotate = heading - rotationDegrees` (degrees clockwise from north, icon drawn
pointing up). Note the sign: a larger rotation turns the asset **anticlockwise** on the map, because the app's planning frame
is x east, z north (at heading 0) and the rotation is applied inside that frame.

Not checked: that the app **draws** the same angle on the phone screen (I compared to the app's code, not to a phone). Also the
known risks from the shared context (AR screen heading, magnetic heading without declination) are outside this job.

## 4. What the dashboard shows today (screens)

- **Plan page, laptop (about 1400 px):** title and status badge; a line "code - planner - submitted - waiting"; map card on the
  left with a legend of coloured squares across the top-left, zoom buttons, a scale bar and an attribution line
  ("Offline view - region, grid and assets only"); on the right a Decision card (Approve / Request changes / Reject) and a Site
  summary card (region size, origin, heading, GPS accuracy, assets, field images, version, hash); below the map tabs
  (Assets, Field images, Versions, Comments, Audit trail) with the assets table (name, type, X, Z, distance from origin, rotation, size).
- **Map:** beige background, the region as a green-tinted box with a metric grid, everything outside dimmed, the origin as a
  rust ring, each asset as a coloured to-scale rectangle plus a **circular dot** that fades out between zoom 16 and 18.
- **Phone width (390 px):** navigation becomes a top bar; the page stacks in one column. The **Decision and Site summary cards
  come before the map**, so the map is far down the page.

## 5. Issues found (none changes positions; all are for later milestones)

1. **At the first view the plan is very small.** The map fits the whole region (488 x 556 m) but this plan's assets cover only
   about 60 m, so they show as a tiny cluster of dots. Zoom is needed to see footprints. C2 (icons) and C3 (a "fit to assets"
   / "fit to region" button) address this.
2. **Dots are circles, not symbols** (the owner's request): C2.
3. **Legend uses coloured squares**, the table uses coloured squares: C2.
4. **No north arrow;** the scale bar exists. The grid is axis-aligned to lat/lng (north up) while the plan may be rotated by the
   heading, which is correct but not explained on screen. C3.
5. **Phone layout:** map below the decision card (see section 4). C3.
6. **Slow first paint of the map** in the pane (the map area was blank for a few seconds before the region appeared, in this
   embedded browser). Not diagnosed; may be only the embedded pane. Not checked on a normal browser.
7. **First sign-in attempt through a script returned 403** (the dashboard login button works). This is probably the API's
   anti-forgery check (a header the page sends); not investigated, no change needed.
8. **Fields the server drops:** the app's export also contains `scale` on each object, the anchor's `id`, `label`, `description`,
   `x`, `z`, and plan `id`, `createdAtEpochMs`, `updatedAtEpochMs`. The API stores none of them (key list stored:
   `version, id, name, regionBounds, anchor, objects`). In this fixture every `scale` is 1 and width/length already contain the
   final size, so nothing is lost. **Not verified:** whether the app ever writes a `scale` other than 1; if so the dashboard
   would not know. Worth asking when the contract is next reviewed (contract is frozen; I changed nothing).

## 6. What was NOT checked

- A plan made on a real phone (only the export file, which the notes call the app's real serializer output).
- Pixel-level agreement of the drawing with the phone screen; large plans (hundreds of assets); plans with an origin outside the
  region; plans at other latitudes (only Delhi, 28.6 N).
- The map server basemap (C4), other browsers, a projector size, printing, keyboard use.
- Screenshots are described, not saved as files.

---

# Milestone C2 update: asset icons on the map (2026-11-20)

## What changed (all inside `web/`)

| File | Change |
|---|---|
| `src/assetIcons.ts` (new) | 11 top-down symbols drawn by us as SVG strings (forward = up) on a coloured rounded badge; glyph is white or near-black, whichever contrasts better with the badge colour. Own artwork, so no licence to record (`ICONS.md` not needed). |
| `src/components/AssetIcon.tsx` (new) | Small image component for legend and table (hidden from screen readers; the text beside it names the asset). |
| `src/components/MapView.tsx` | The circle layer is replaced by a **symbol layer**. Each asset has one icon at its centre, `icon-rotate = heading - rotationDegrees`, rotation aligned to the map, overlap allowed, size grows with zoom (about 20 to 32 px). The to-scale footprint polygons stay underneath (they are the truth about size). Selection is now a rust rounded-square ring around the icon plus the footprint outline. Hover popup shows the icon, name, type and size, built with DOM nodes (the old version put the asset name into HTML text, which could inject markup). Origin ring is drawn under the icons so it cannot hide an asset. |
| `src/pages/PlanDetail.tsx` | Map legend and assets table show the icon instead of a coloured square. |
| `src/styles.css` | A few lines for the icon placement. |

Two fixes beyond the icons, both small and in `MapView.tsx`:
1. **Opens on the assets.** The first view now fits the assets and origin (never tighter than about 90 m across) instead of the
   whole region, so footprints and icons are visible straight away (issue 1 in section 5). The region stays visible on zooming out.
   With no assets it still opens on the whole region. A "fit to region" button is left for C3.
2. **The map no longer resets every few seconds.** The live store re-fetches plans on a timer and gave the map a new but identical
   layout object each time, which rebuilt the map and threw away the reviewer's zoom and pan. The map is now rebuilt only when the
   layout's content really changes. (This bug existed before C2; I found it while testing zoom.)

## How it was checked
- `npm run typecheck` and `npm run build`: both pass (build prints only the usual "chunk larger than 500 kB" warning).
- Private API + dashboard, signed in as a reviewer: the app's own export (6 assets, all current types), and the sample plan
  TP-2026-0135 (21 assets, retired types tent/trench/car/truck/antenna).
- Three zoom levels on the 6-asset plan: close (scale bar 20 m), region (200 m), far (500 m). Icons stay readable at all three; at
  the far view the cluster overlaps itself, which is unavoidable at that scale.
- Click selects (ring + "Tent 7 - 211.3 m from origin" bar), hover popup shows icon and size.
- Screenshots: `web/docs/screenshots-c2/` (`before-dots-closeup.png`, `after-zoom-close.png`, `after-zoom-region.png`,
  `after-zoom-far.png`, `after-21-assets-page.jpg`, `icon-sheet.jpg` = all 11 enlarged).

## Not checked
- A real phone plan; a basemap behind the icons (C4); phone-width layout of the legend; touch use; very large plans (hundreds
  of assets, where icons will overlap heavily); keyboard selection of a map icon (table rows still work with Enter).
- Whether the icons look right on a high-density display (drawn at 2x, but only viewed in the embedded pane).
- The antenna icon is symmetric, so its rotation is not visible; the trench icon is a marker, not drawn to scale (the footprint is).
- The old circle layer and "dots" are gone; the table's selected-row highlight and the table-to-map sync still work as before.

## C2 revision (owner feedback): side-view vehicles
The military vehicle, truck and car icons were top views and looked like plain boxes. They are now **side-view silhouettes facing right**
(armoured hull, truck with cab, car). Because a side view turned upside down is unreadable, these three icons are **not rotated** on the
map (`UPRIGHT_TYPES` in `assetIcons.ts`); the to-scale footprint still shows the real orientation. All other icons keep top views and
rotate by heading minus rotation. `icon-sheet.jpg` was updated. Typecheck and build pass. Not re-checked on the full plan page after this change (only the icon sheet).

## C2 revision 2 (owner feedback): more side views
Off-road vehicle (jeep), STR-MGS (wheeled armoured vehicle with turret and long gun) and trench (cross-section: a notch cut into
the ground) are now side views too, and stay upright like military vehicle, truck and car (`UPRIGHT_TYPES`). Only the gunship, K-131,
K-131 take-off, tent and antenna remain top views that rotate with the plan. Typecheck and build pass; checked on the icon sheet only,
not again on a full plan page.

## C2 final check (after the side-view change)
Checked on the full plan page with the final icons: the app-export plan (6 assets) and sample plan TP-2026-0135 (21 assets: tents,
trenches, cars, trucks, antennas). Side-view icons stay upright and are readable at the opening zoom; legend and table show them.
Screenshots: `screenshots-c2/final-6-assets-page.jpg`, `screenshots-c2/final-21-assets-map.png`. Owner approved the icon designs.

---

# Milestone C3 update: professional plan page (2026-11-20)

Before/after screenshots: `docs/screenshots-c3/` (`before-laptop.jpg`, `after-laptop.jpg`, `before-phone.jpg`, `after-phone.jpg`,
`after-print-approximation.jpg`, `after-not-found.jpg`). No information was removed and no behaviour changed except as listed.

## What changed
| Area | Change |
|---|---|
| Header | Code, planner, submitted and waiting time are separate items instead of one run-on line; status badge and a **Print plan** button sit on the right. |
| Map card | Taller map (`clamp(380px, 58vh, 680px)`), legend now shows counts per type, **north arrow**, **Fit to assets** and **Fit to region** buttons, and a readout bar under the map (region size, origin, heading). The scale bar stays. The first view and the fit buttons leave room at the top so the legend never covers assets. A plan with no assets shows a note on the map. |
| Assets tab | Chips with counts by type; the table sorts by **name, type, distance from origin and rotation** (click a heading again to reverse; `aria-sort`, natural order so "Tent 2" comes before "Tent 10"); rows select with Enter or Space; the selected row has a rust marker and stays in sync with the map. |
| Decision area | The decision card has a moss top edge so it reads as the primary action area. |
| States | "Plan not found" and "Loading plan" are proper cards (`role=alert` / `role=status`) with a Back button. |
| Responsive | Laptop: map and tabs left, decision and summary right. At 1080 px and below one column in this order: **map, decision, summary, tabs** (the map used to sit below the decision and summary on a phone). At 1600 px and wider the page and text grow for a projector. |
| Print | A print stylesheet hides the sidebar, banner, buttons, tab bar and decision card and prints header, map (9 cm), readout, summary and the active tab (the assets table by default). The map uses `preserveDrawingBuffer` so it can be printed. |
| Accessibility | Table caption (screen readers), sort buttons with focus ring, tabpanel role, legend as a list, north arrow with a text label, focus styles unchanged. |

Files: `src/pages/PlanDetail.tsx`, `src/components/MapView.tsx` (fit handle, padding, print), `src/styles.css`.

## How it was checked
`npm run typecheck` and `npm run build` pass. Private API + dashboard, signed in as a reviewer: sample plan TP-2026-0135 (21 assets) at
1400 px and 390 px wide: layout, legend counts, north arrow, Fit to region, sorting by distance (47.6 m up to 216.7 m, arrow shown),
not-found page. Print was checked by applying the print rules on screen at about A4 width (see the screenshot), not with a real print dialog.

## Not checked
- A real print or PDF export (page breaks, ink use, whether the map redraws at print size); a projector (only the CSS rule exists).
- Contrast measured with a tool (colours reuse the existing palette; muted text is about 6:1 on white); screen-reader use; keyboard
  selection of an icon directly on the map (table rows work); other browsers; the other pages (overview, plan list) were not touched.
- Tablet width (only 1400, 1000 and 390 px were looked at); very long asset names or hundreds of assets.
- The first paint of the map is slow in the embedded browser (several seconds); not diagnosed.

## C3 audit (after finishing)
- Contrast measured (WCAG ratios): body, muted text, links, primary button, "info" and "bad" badges, chips and selected row all pass AA (4.5:1).
  The status badges "Approved" (4.2:1) and "Waiting" (3.9:1) were below AA before C3; their text colour is now darker (5.3:1 and 5.6:1;
  badge and chip classes only, backgrounds unchanged). Typecheck and build pass after this change.
- Known gaps against the C3 list: (1) no dedicated error card when the live data fails to load (the page shows "Plan not found" or the
  last data); (2) table numbers use one decimal (x, z, distance) while sizes use `dim`, so formatting is consistent inside the table but
  does not use `formatMeters` (it would show kilometres and lose precision); (3) print and projector were not tried for real.

## C3 addition: error card (owner request)
The live store now remembers when loading from the server fails (`loadError`, optional in `storeContext.ts`, set in `liveStore.tsx`;
the demo store is unchanged). On the plan page:
- if a plan cannot be loaded at all, a card "Could not load this plan" shows the reason with **Try again** and **Back to plans**
  (instead of the misleading "Plan not found");
- if a refresh fails while a plan is already shown, a red notice at the top says the data may be out of date, with **Try again**.
  It clears by itself once the server answers again.
Checked by stopping the private API while a plan was open (notice appeared after the next refresh), then restarting it and pressing
Try again (notice cleared). Screenshot: `screenshots-c3/after-error-notice.jpg`. Not checked: the full "Could not load this plan" card
(hard to trigger: it needs the sign-in check to succeed and the plan list to fail right after); other pages still ignore load errors.
This closes gap (1) in the C3 audit above.
