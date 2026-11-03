package com.example.dhruvar

import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.MapLayoutProjection
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAssetHitTest {

    @Test
    fun `a long asset can be grabbed out at its corner, not only within eight meters`() {
        val truck = LayoutObject(
            assetType = AssetType.STR_MGS,
            name = "Truck 1",
            x = 0f,
            z = 0f,
            widthMeters = AssetType.STR_MGS.defaultWidthMeters,
            lengthMeters = AssetType.STR_MGS.defaultLengthMeters,
            heightMeters = AssetType.STR_MGS.defaultHeightMeters
        )

        val reach = MapLayoutProjection.selectionRadiusMeters(truck)
        assertTrue(reach >= truck.lengthMeters / 2f)

        val paradeGround = truck.copy(widthMeters = 40f, lengthMeters = 80f)
        assertTrue(MapLayoutProjection.selectionRadiusMeters(paradeGround) > 40f)
    }
}
