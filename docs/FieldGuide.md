# DhruvAR Field Guide — Geographic Planning & AR

Tactical how-to for geo-accurate layouts (MapLibre + GNSS + ARCore). Tone matches the in-app workflow language.

---

New Plan is a sequence on one map. Search, region, and origin are finished before assets or AR appear.

## 1. Find a location (search)

1. **Create New Plan**. Planning opens on **Find location**.
2. Search and **select a result**. Panning the map does not count.
3. Tap **Continue**.

If search fails, check the map server. There is no skip.

---

## 2. Set region (drag or visible area)

1. Drag a box on the map, or tap **Use visible area**.
2. Confirm a box at least 25 m on each side.
3. Confirming the box places the origin at its center and opens planning. The camera fits that box: outside is dimmed, and a grid is drawn inside it.

**Policy:** Once a region is set, taps outside the box are **rejected** (not silently clamped).

---

## 3. Origin at the center

Confirming the region places the origin at the center of the box and opens the asset workspace.

**Change origin** when the site needs a different point:

1. Open the **Origin** chip and choose **Move origin**.
2. **Tap on map** inside the region, or **Stamp GPS** when the phone has a fix inside the box.
3. **Use region center** snaps the origin back to the midpoint.
4. Accuracy worse than 15 m still stamps, with a site-level warning. Relative meters stay precise.
5. **Capture heading** is optional. Missing compass uses 0°.

A point you chose stays while a later box still contains it. If a new box leaves that point outside, the origin returns to the new center and assets stay on the same ground. A manual move keeps meter offsets, so assets shift on the map.

**GPS vs relative precision:** Absolute GNSS error can be several meters. Object-to-object and object-to-anchor distances use planning meters and stay precise relative to the stamped origin.

---

## 4. Place assets

1. Origin and region are already set. The rail is **Navigate / Place / Edit** only.
2. Pick an asset from **+** (switches to Place) and tap inside the box.
3. Positions are stored as `(x, z)` meters relative to the origin.
4. **Edit**: tap an asset to select; drag to move it. Outside taps are rejected.
5. **Change region** and **Change origin** are explicit. They do not delete assets. A new origin keeps meter offsets, so assets shift on the map.

**Metric grid** is available here for precise nudges. Same layout — one source of truth. It is hidden during setup.

---

## 5. Save

Use the toolbar **Save**. Layout JSON is schema **version 2** with optional geo fields. Old v1 files still load (geo null).

---

## 6. On-site AR calibration

1. Walk near the planned origin. Open **Visualize in AR**.
2. Read **GEO BRIEFING**: distance / bearing / `NEAR ORIGIN` / `TOO FAR` / `NO GEO`.
3. Detect a horizontal plane → **Set Planning Origin** (uses **saved heading** when present, else live compass).
4. Confirm “Origin calibrated — rendering N objects”.
5. Assets appear at planned meter offsets. GPS does **not** drag meshes every frame — AR tracking owns session stability.
6. Optional scale: **1:1** full field, **1:20** / **1:50** sand-table.
7. **Reset** recalibrates; relative layout should repeat.

AR stays closed until the plan has both a region and an origin. A plan with zero assets can still be visualized.

---

## 7. What GPS accuracy means

| Concept | Meaning |
|--------|---------|
| Horizontal accuracy (e.g. ±4 m) | Uncertainty of the stamped WGS84 origin |
| Relative meter placement | Distances between assets in the plan (precise by construction) |
| AR session | Local tracking after you set origin on the ground plane |

Site tip: stamp origin outdoors with clear sky; poor accuracy still OK for briefing if teammates understand the absolute error budget.

---

## Failure recovery cheat sheet

| Symptom | Action |
|--------|--------|
| Permission denied | Grant / Open app settings |
| GPS disabled | Enable Location in system settings → Retry |
| Map tiles unavailable | Check network / PMTiles URL (`docs/PMTiles-Configuration.md`) |
| Continue disabled on step 1 | Select a search result, not just pan |
| GPS stamp rejected | Stand inside the box or tap the map inside it |
| Cannot place assets | Finish origin (step 3) |
| Outside region | Change region, or move the asset back inside |
| AR far from site | Walk closer (warn only) or proceed carefully |
