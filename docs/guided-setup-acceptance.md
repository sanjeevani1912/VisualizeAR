# Guided setup acceptance

Device script for the gated New Plan flow. One MapLibre screen; the step comes from the saved layout, not from extra routes.

## Script (12 steps)

1. **New Plan** opens on Find location. Pan the map and confirm Continue stays disabled.
2. Search and select a hit. Continue opens Set region and flies to that place.
3. Drag a box about 200 m across (or use the visible area) and confirm. The camera fits the box, the outside dims, and a grid appears.
4. Confirming the box places the origin at the center. The chip reads Set and the asset workspace opens (Navigate / Place / Edit, asset picker, AR).
5. Place two assets inside the box. Drag one. A tap in the dimmed area is rejected.
6. Save. Home shows Origin and Region done, and no Incomplete chip.
7. Reopen the plan. It lands on the asset workspace with both assets.
8. Open an older plan that has a region but no origin. It lands on Set origin with the mask and grid. Use region center, a map tap, or GPS.
9. Change region on the complete plan. If the new box still contains the origin, you return to assets and that point stays. If it does not, the origin moves to the new center and assets stay on the same ground.
10. Change origin. The warning says meter offsets stay. Stamp a new point inside the box. Assets shift on the map and the workspace returns.
11. AR before origin is not offered. After origin, AR opens the existing visualize flow, including a plan with zero assets.
12. Field Guide (in-app and `docs/FieldGuide.md`) lists search → region → origin → assets → save → AR.

## Resume matrix

| Saved layout | Lands on |
|--------------|----------|
| No region, no origin | Find location |
| Region, no origin | Set origin (camera fit, mask, grid) |
| Region and origin | Asset workspace |
| Origin, no region (legacy) | Set region; a point inside the new box is kept, otherwise the origin becomes the new center |

Search hits are not saved. A process restart drops an uncommitted search choice and returns to Find location. A saved region and origin survive because they are on the layout.

## Not run on a phone in this pass

Unit tests cover the matrix, the AR predicate, clear-canvas, and double Continue. The script above still needs a device with the map server, GPS or a map tap, and ARCore.
