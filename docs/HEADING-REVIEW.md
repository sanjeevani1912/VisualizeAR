# Heading review (D2): map versus AR

Status: **review only. No app code was changed.** The proof is `app/src/test/java/com/example/dhruvar/HeadingReviewTest.kt`
(runs on the PC, no phone). The compass, ARCore and placement in the real world are **not verified on a device**.

## In plain words
Every plan stores one "heading": the compass direction the plan's up-axis (+Z) points to, in degrees clockwise from north.
The **map and dashboard** use it correctly. The **AR screen** appears to use it in the wrong way, so a plan can show up
rotated in AR compared with the map. The error is exactly "live compass heading + saved heading" instead of "saved heading".
It is zero only when the phone is facing north (live heading 0) at the moment you set the origin in AR.

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

### 3. Candidate fix (shown in a test only, not applied)
`yaw = savedHeading - liveHeading - forwardAngle` (i.e. use the saved heading as the target and subtract the live heading).
`candidateFix_agreesWithMapForAllSamples` shows 0 degrees error against the map for every saved heading, live heading and camera yaw tested.
It touches only `originYawDegreesForMagneticNorth` and its two callers; it does not touch the layout schema.
**Per the brief I have not applied it. Needs your decision.** Because it hangs on the right-handed assumption above, it should be
confirmed with the phone field test below before or right after applying.

### 4. Other items from the code review
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
6. Report: which direction each asset appeared in, and what your compass said. Photos help.

## Decision needed
Apply the candidate fix? I recommend: do the field test first (it shows the problem exists on a real phone); then apply and re-test.
