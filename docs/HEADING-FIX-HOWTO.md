# AR heading fix: how to turn it on, test it, and turn it off

Written 2026-11-24, branch `app/heading-fix` (made from `app/connect-stack`). **Not merged into `integration` or `main` on purpose.**
**NOT verified on a device.** Everything below is checked only by unit tests on the PC (they assume ARCore's world is right-handed;
that is Google's documented behaviour but has not been seen on this phone). Read `docs/HEADING-REVIEW.md` first for the reasons.

## What the problem is (plain words)
- Every plan stores a "heading": the compass direction the plan's up-axis points to. The map and dashboard use it correctly.
- The AR screen used the saved heading where the phone's live compass reading was expected, so the plan can look **rotated** in AR.
- The AR screen also looks **mirrored** (left and right swapped), because the map and ARCore count sideways in opposite directions.

## What the fix does
One switch, default **OFF**: `app/src/main/java/com/example/dhruvar/domain/spatial/ArHeadingFix.kt`
```kotlin
const val ENABLED: Boolean = false   // change to true to turn the fix on
```
When it is `true`:
1. The origin is turned by `saved heading - live compass heading - camera angle` (before: `saved heading - camera angle`).
2. Planning x is negated when objects are placed in AR, and each object's own rotation sign is flipped with it so assets still face the way the map shows.
   (I assumed an object's rotation is counter-clockwise on the map, as the dashboard draws it: bearing = heading - rotation. If assets face the wrong way after the fix, that assumption is what to question.)

Files touched: `ARCoordinateTransformer.kt` (new `originYawDegreesForPlanHeading`, `headingFix` parameter on the two placement functions),
`ARRenderer.kt` (`setOriginAtCenterHit` also takes the live heading), `ARVisualizationScreen.kt` (passes the live compass heading), plus the new
`ArHeadingFix.kt` and `ArHeadingFixTest.kt`. Everything that places objects (drawing, ground snapping, distance line, tap selection) goes through the same two
functions, so the one switch covers all of it. The layout JSON and the API are unchanged.

## Field test (do this with the switch OFF first, then ON)
Open ground, away from cars and metal. Use `docs/APP-PHONE-TEST.md` for the USB set-up.
1. **Switch OFF.** Build and install (`gradlew installPlayDebug` or Run in Android Studio). On the plan screen stand at a point, put the origin there and face a landmark.
   On the map place two assets: **A** 20 m straight ahead (toward the landmark), **B** 20 m to your **right** of that line. Save.
2. Open AR. Turn about 90 degrees away from the landmark. Tap "Set Planning Origin". Write down where A and B appear (compass direction and left/right).
   Repeat facing north and facing east. Expectation if the review is right: A is only correct when you face north; B may be on the wrong side.
3. **Switch ON** (edit the constant), rebuild, install. Repeat step 2.
4. Pass: A appears toward the landmark whichever way you faced when you tapped; B is on your right when you look toward A.
   Also check an asset with a rotation (for example a vehicle turned 90 degrees on the map): it should point the same way as on the map.
5. If A is right but B is on the wrong side, or vehicles point the wrong way, the mirror/rotation part is wrong: report what you saw (photos) and do not ship the switch ON.
6. Also compare with a compass app. The heading is **magnetic** north, not true north (known gap, not fixed here).

## Turning it on for real / off again
- ON: set `ENABLED = true`, run `gradlew testDebugUnitTest assembleDebug` (the test `switchIsOffByDefault` will then fail: change it to
  expect `true` in the same commit), install, repeat the field test, commit on `app/heading-fix`, then merge deliberately.
- OFF: set `ENABLED = false` and rebuild. Behaviour is then identical to the demo build.

## Tests
`gradlew testDebugUnitTest assembleDebug` (JAVA_HOME `C:\Program Files\Android\Android Studio\jbr`). `ArHeadingFixTest` checks, with the real transformer
functions, that with the fix on 480 placements (6 saved headings x 5 live headings x 4 camera directions x 4 points) land where the map puts them,
and that with the fix off positions and rotations are exactly what they were. `HeadingReviewTest` still shows the old behaviour disagreeing with the map.
These are paper checks: **no phone, no ARCore, no compass was used.**
