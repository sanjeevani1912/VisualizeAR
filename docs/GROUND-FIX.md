# Ground fix: assets that look like they float indoors

Branch `app/ground-fix`. **Not verified on a phone**: only unit tests and the build were run.

## Likely causes (found by reading the code, not yet confirmed on a device)

1. **No contact shadow.** Nothing under an asset says "this touches the floor", so it looks unanchored.
2. **Each asset found its own height and moved slowly.** Every asset snapped to a plane on its own and then
   eased toward it (15% per frame), so assets could sit at slightly different heights, lag, or bob when
   ARCore refined the floor.
3. **Origin set on a raised surface** (table, bed). Origin and floor are then at different heights.

## What changed

Everything is behind one constant: `GROUND_FIX_ENABLED` in `ARRenderer.kt` (companion object).

| Part | Where | What it does |
|---|---|---|
| One steady floor | `GroundFloor.kt` (pure) + `ARRenderer.updateFloor` | Each frame: lowest large (>= 0.8 m²) tracked upward plane within 1.5 m of the origin height is the floor. It is smoothed (15%/frame, snaps if it jumps > 0.4 m). All assets use this one height. No floor this frame: the **last good value is kept**. Never found yet: the origin's height is used. Assets never move sideways. |
| Contact shadow | `ContactShadowRenderer.kt` | Soft dark disc (alpha 0.45, fading to the edge) flat on the floor under each asset, size from width/length x scale x asset-size, drawn after the assets, blended, depth write off. Same shader style as the origin marker. |
| Debug line | `ARVisualizationScreen.kt` | Debug builds only (`BuildConfig.DEBUG`), shown after the origin is set. No permissions. |
| Origin hint | `ARRenderer.refreshOriginRaised` + one message | If the origin is set > 0.3 m above the lowest large plane, the message "Origin is on a raised surface; aim at the floor and set it again" replaces the usual "Origin calibrated" note. The origin is still set. |

Untouched: `ARCoordinateTransformer`, layout schema, `web/`, `server*/`, `deploy/`, the heading fix branch.

## How to switch it off

In `app/src/main/java/com/example/dhruvar/ui/ar/render/ARRenderer.kt` change

```kotlin
const val GROUND_FIX_ENABLED = true
```
to `false` and rebuild. Floor, shadows, and hint are off, and the old per-asset snapping runs exactly as before.
(The debug line is also only produced while the fix is on.)

## Field test

**A. Indoors on a floor**
1. Install the debug APK, open a layout with a few assets, scan the floor slowly until planes appear.
2. Aim at the floor, Set Origin. Expect the normal "Origin calibrated" note (no raised warning).
3. Walk around the assets. Expect: a soft dark blob under each, all sitting on the same floor level, no bobbing.
4. Debug line should say `floor FOUND` with `floor` close to `origin` (within a few cm).
5. Cover the camera briefly / move to a blank area: line may change to `floor KEPT`; assets must stay put.
6. Take a screenshot and a panorama: shadows may appear in them (expected).

**B. Origin on a table**
1. Scan until both the table top and the floor are seen (look down past the table edge).
2. Set the origin on the table. Expect the message "Origin is on a raised surface; aim at the floor and set it again".
3. Debug line: `origin` about 0.7 higher than `floor`. Assets are drawn on the floor, not on the table.
4. Reset the origin, aim at the floor, set again: message disappears.
5. If the floor was never seen, no warning appears (it cannot know) and assets use the origin height.

## Reading the debug line

`floor=-0.02 origin=0.00 planes=3 floor FOUND`

- `floor`: the smoothed floor height used for all assets (metres, ARCore world, up is +).
- `origin`: height of the origin anchor.
- `planes`: horizontal upward tracked planes currently seen.
- `floor FOUND`: a floor plane was seen this frame. `floor KEPT (last value)`: none this frame, the previous value is used.
  `NO FLOOR YET (origin height used)`: no floor ever found since the origin was set (assets sit at origin height).
- `origin` much higher than `floor` (> 0.3) means the origin is on a raised surface.

## Risks

- Visual result unverified on a device; shadow strength (alpha 0.45) and size (0.6 x footprint) may need tuning.
- If the "lowest large plane" is wrong (a pit, stairs down, a glass/mirror reflection plane), every asset follows it.
- Assets on a real ramp or terrain with several levels are now all drawn at one floor height (old code could vary per spot).
- The shadow is a flat disc: on a table plane it still appears on the floor height, not on the table.
- Smoothing is per frame, so speed depends on frame rate.
- Floor must be within 1.5 m of the origin height; a floor further below is ignored.
- Shadows appear in screenshots and panorama shots.
