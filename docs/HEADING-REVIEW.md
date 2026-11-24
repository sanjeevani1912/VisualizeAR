# Heading review (D2): map versus AR

Status: **review only. No app code was changed.** The proof is `app/src/test/java/com/example/dhruvar/HeadingReviewTest.kt`
(runs on the PC, no phone). The compass, ARCore and placement in the real world are **not verified on a device**.

## In plain words
Every plan stores one "heading": the compass direction the plan's up-axis (+Z) points to, in degrees clockwise from north.
The **map and dashboard** use it correctly. The **AR screen** appears to use it in the wrong way, so a plan can show up
rotated in AR compared with the map. The rotation error is exactly "live compass heading + saved heading" instead of "saved heading" (zero only when the phone faces north
as you set the AR origin). Separately, the plan also looks **mirrored left-to-right** in AR (see section 3).

## The model I used (an assumption to confirm on a phone)
- ARCore world is right-handed, Y up. Seen from above: +X right, +Z toward the bottom of the page. A camera looking along -Z faces "up".
- Compass heading is clockwise from above. If the camera looks along f with live heading H, a direction d has heading H plus the
  clockwise angle from f to d.

## What I checked 

### 1. Map convention (correct, consistent)
`GeographicCoordinateConverter` says: heading h = clockwise angle from north to planning +Z. Test `mapConvention_plusZPointsAtSavedHeading`
confirms +Z lands at bearing h for h = 0, 30, 90, 200, 359.

### 2. AR origin yaw (the mismatch)
`ARVisualizationScreen.kt` ~1036 passes `anchor.headingDegrees` (the saved heading) into `setOriginAtCenterHit`, which feeds
`ARCoordinateTransformer.originYawDegreesForMagneticNorth(forward, magneticHeading)`: `yaw = heading - forwardAngle`.
Working it through, plan +Z ends up at compass heading **live + heading** (not heading).

- With the saved heading passed (what the code does today): **100 of 120** sampled combinations (4 saved headings x 6 live headings x
  5 camera yaws) disagree with the map. The 20 that agree are those where the live heading is 0.
  (`ar_currentCall_withSavedHeading_doesNotMatchTheMap`)
- Even with the live heading passed, as the function's comment intends ("canvas north points at magnetic north"), plan +Z lands at
  **2 x live heading**: right only when facing north or south. (`ar_currentCall_withLiveHeading_northIsOnlyRightWhenFacingNorth`)
- The existing `ARCalibrationTest.originYaw_alignsCanvasNorthToMagneticNorth` case "facing east" expects north at -X and calls that
  "left of the camera". For a camera looking along +Z in a right-handed Y-up world, -X is the **right**. The test encodes the same
  sign assumption as the code, so it does not catch this.

### 3. A second, bigger finding: the plan looks mirrored in AR (found while extending the test to points off the axis)
Production places each object at `originPose * translate(x, 0, z)` (`ARCoordinateTransformer.transformPlanningToWorldPose`). In the
map, +X is east and +Z is north, so +X is **clockwise** 90 degrees from +Z. In an ARCore world (right-handed, Y up, camera looks along -Z),
+X is **counter-clockwise** 90 degrees from +Z. Same numbers, opposite handedness: left and right are swapped, whatever the heading.
The existing "facing east" test comment ("north to the left", expects -X) fits a left-handed world, which ARCore is not.

Table from `objectAtXZ_mapVersusAr_table` (object at x=3, z=4; live compass 60 degrees; camera yaws 0, 90, 200 give identical rows because the
origin yaw cancels the camera direction). East/North metres from the origin:

| Saved heading | Map E,N | AR today E,N |
|---|---|---|
| 0   | 3.0, 4.0   | 2.0, 4.6   |
| 90  | 4.0, -3.0  | 4.6, -2.0  |
| 180 | -3.0, -4.0 | -2.0, -4.6 |
| 270 | -4.0, 3.0  | -4.6, 2.0  |
| 45  | 4.9, 0.7   | 4.6, 1.9   |

With the candidate fix (section 4) the AR column equals the Map column in all 15 rows (3 camera yaws per heading); the test asserts this.
The test prints the full table when run.
**AR today disagrees with the map in 15 of 15 rows.** This is what unit tests can show; it depends on ARCore being right-handed
(documented by Google, but **not verified on a device**).

### 4. Candidate fix (shown in tests only, NOT applied; needs your decision)
Two parts, both in the AR placement maths only (layout schema and API unchanged):
1. **Yaw:** `yaw = savedHeading - liveHeading - forwardAngle` (use the saved heading as the target, subtract the live heading).
   `candidateFix_agreesWithMapForAllSamples`: plan +Z axis agrees with the map for every saved heading, live heading and camera yaw tried.
2. **Mirror:** convert planning (x, z) to the AR local offset as (-x, z) (or equivalently (x, -z) with a matching yaw), and make the object
   rotation sign consistent. With both, `objectAtXZ_mapVersusAr_table` shows 0 of 15 rows disagreeing.

This is **not** "small": the same conversion is used in several places (`transformPlanningToWorldPose`, `transformPlanningToWorldPosition`,
the ground probe and drag/hit-test code in `ARRenderer.kt` ~493 and ~511, and the origin yaw), and the mirror must be applied
consistently or dragging in AR would move assets the wrong way. I recommend the field test below **before** changing anything, because part 2
rests entirely on the right-handed assumption.

### 5. Other items from the code review
| Item | Finding |
|---|---|
| Magnetic, not true north | Confirmed: no `GeomagneticField` anywhere. Magnetic north differs from true north by the local declination (about a degree in Delhi; bigger elsewhere; I did not look up values). The map treats heading as true bearing, so every plan is rotated by the declination. Not fixed. |
| Longitude not range-checked | Confirmed: `LatLng.init` checks latitude only. Longitude is wrapped later in the converter, so impact is small. Not changed. |
| 111,320 m per degree vs R = 6,371,008.77 m | Confirmed: 111,320 vs 111,195 m per degree, **0.11% apart**, under 0.3 m over a 250 m edge. Harmless; not changed. |

## What only a phone can verify (field test)
1. Build and install the debug APK (see `docs/APP-PHONE-TEST.md`). Use open ground; keep away from metal and cars.
2. Plan screen: put the origin at your position, **point the phone at a distant landmark** and note the landmark. On the map, place two
   assets: one 20 m along the direction you are facing and one 20 m to your right. Save it.
3. Open AR. Stand at the same spot, face a **different** direction than in step 2 (for example turn about 90 degrees), tap "Set Planning Origin".
4. Look at where the two assets appear. Expected if correct: the first is 20 m toward the compass direction the plan faces (the landmark side),
   not along where you are looking now.
5. Repeat step 3 facing north and facing east. Today (if my analysis is right) the first asset is right when facing north and
   rotated when facing any other way. A compass app on the phone helps you check the bearing.
6. **Mirror check:** the asset you placed 20 m to your **right** on the map should also appear to the right of the forward asset
   when you look along the plan's direction. If it appears on the left, the plan is mirrored (section 3).
7. Report: which direction each asset appeared in, and what your compass said. Photos help.

## Decision needed
Apply the candidate fix (yaw and mirror)? I recommend: field test first (it shows on a real phone whether the plan is mirrored and rotated); then apply and re-test.
