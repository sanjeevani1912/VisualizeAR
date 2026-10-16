package com.example.dhruvar

import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.spatial.MapLayoutProjection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginInsideRegionTest {

    private val region = GeoBounds(
        northLatitude = 28.62,
        southLatitude = 28.60,
        eastLongitude = 77.22,
        westLongitude = 77.19
    )

    @Test
    fun interiorPointIsUsable() {
        assertTrue(MapLayoutProjection.isUsableOrigin(region, 28.61, 77.21))
    }

    @Test
    fun cornersAreInclusive() {
        assertTrue(MapLayoutProjection.isUsableOrigin(region, 28.62, 77.19))
        assertTrue(MapLayoutProjection.isUsableOrigin(region, 28.62, 77.22))
        assertTrue(MapLayoutProjection.isUsableOrigin(region, 28.60, 77.19))
        assertTrue(MapLayoutProjection.isUsableOrigin(region, 28.60, 77.22))
    }

    @Test
    fun clearlyOutsideIsRejected() {
        assertFalse(MapLayoutProjection.isUsableOrigin(region, 28.59, 77.21))
        assertFalse(MapLayoutProjection.isUsableOrigin(region, 28.61, 77.18))
        assertFalse(MapLayoutProjection.isUsableOrigin(region, 28.70, 77.21))
    }

    @Test
    fun unboundedHelperStillAllowsMissingRegion() {
        assertTrue(MapLayoutProjection.isInsideRegionOrUnbounded(null, 28.70, 77.21))
        assertFalse(MapLayoutProjection.isInsideRegionOrUnbounded(region, 28.70, 77.21))
    }
}
