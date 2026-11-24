package com.example.dhruvar.domain.spatial

/**
 * THE ONE SWITCH for the AR heading fix (see docs/HEADING-FIX-HOWTO.md and docs/HEADING-REVIEW.md).
 *
 * `false` (default) = the behaviour that shipped in the demo build, unchanged.
 * `true`            = the candidate fix, two parts:
 *   1. the AR origin is turned so that plan +Z points at the SAVED heading: yaw = saved heading - live compass heading - camera angle
 *      (before, the saved heading was used where the live compass heading was expected);
 *   2. the plan is no longer mirrored left-to-right: the planning x is negated when placing objects in the AR world, and the
 *      object's own rotation sign is flipped with it so assets still face the way the map shows.
 *
 * To turn it on after a field test: change this value to `true`, rebuild and install. Nothing else needs to change.
 * NOT verified on a device. The maths is checked only by unit tests that run on the PC.
 */
object ArHeadingFix {
    const val ENABLED: Boolean = false
}
