# DhruvAR Field Guide — Geographic Planning & AR

Tactical how-to for geo-accurate layouts (MapLibre + GNSS + ARCore). Tone matches the in-app workflow language.

---

## 1. Set geographic origin (GNSS + heading)

1. Open **Planning**.
2. Expand **GEO ORIGIN**.
3. Grant location permission if prompted. If permanently denied, use **Open app settings**.
4. Wait for a fix. Status chips:
   - **NO FIX** — wait / enable GPS
   - **ACCURACY POOR (>15m)** — usable for site-level stamp; relative meters stay precise
   - **READY** — good enough to stamp
5. Tap **Use my location** to write lat / lng / accuracy onto the layout anchor.
6. Tap **Heading** to capture compass azimuth (planning +Z alignment).
7. Save the layout when ready.

**GPS vs relative precision:** Absolute GNSS error can be several meters. Object-to-object and object-to-anchor distances use planning meters and stay precise relative to the stamped origin.

---

## 2. Select region on the map

1. Stay on the **Map** surface (not Metric grid).
2. Choose mode **Region**.
3. Pan/zoom so the visible area covers your site.
4. Tap **Use visible area** to store `regionBounds`.
5. **Clear region** removes bounds.

**Policy:** Region is optional. If unset, map placement is allowed site-wide. If set, taps outside the box are **rejected** (not silently clamped).

---

## 3. Place assets (meters under the hood)

1. Set a geographic origin first (map placement is blocked without it).
2. Mode **Place** (or pick an asset from **+** — switches to Place).
3. Tap the map. Positions are stored as `(x, z)` meters relative to the geographic anchor (east / north of plan, rotated by heading).
4. Mode **Edit**: tap an asset to select; tap empty ground to move it.
5. Mode **Anchor**: tap to move the geographic origin (heading preserved or refreshed from compass).

Use **Metric grid** for precise relative nudges without map tiles. Same `Layout` / ViewModel — one source of truth.

---

## 4. Save

Use the toolbar **Save**. Layout JSON is schema **version 2** with optional geo fields. Old v1 files still load (geo null).

---

## 5. On-site AR calibration

1. Walk near the planned origin. Open **Visualize in AR**.
2. Read **GEO BRIEFING**: distance / bearing / `NEAR ORIGIN` / `TOO FAR` / `NO GEO`.
3. Detect a horizontal plane → **Set Planning Origin** (uses **saved heading** when present, else live compass).
4. Confirm “Origin calibrated — rendering N objects”.
5. Assets appear at planned meter offsets. GPS does **not** drag meshes every frame — AR tracking owns session stability.
6. Optional scale: **1:1** full field, **1:20** / **1:50** sand-table.
7. **Reset** recalibrates; relative layout should repeat.

Layouts without geo still work: local AR mode from a user-placed origin.

---

## 6. What GPS accuracy means

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
| Can’t place on map | Set GEO ORIGIN first |
| Outside region | Expand region or Clear region |
| AR far from site | Walk closer (warn only) or proceed carefully |
