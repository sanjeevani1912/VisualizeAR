package com.example.dhruvar

import com.example.dhruvar.domain.model.AssetCatalog
import com.example.dhruvar.domain.model.AssetCategory
import com.example.dhruvar.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetCatalogTest {

    @Test
    fun `every placeable type appears once`() {
        val types = AssetCatalog.entries.map { it.assetType }
        assertEquals(AssetType.entries.toSet(), types.toSet())
        assertEquals(AssetType.entries.size, types.size)
    }

    @Test
    fun `trucks drill down lists the truck and the car`() {
        val names = AssetCatalog.inCategory(AssetCategory.TRUCKS, "").map { it.displayName }
        assertEquals(listOf("Truck", "Car"), names)
    }

    @Test
    fun `search stays inside the open category`() {
        assertTrue(AssetCatalog.inCategory(AssetCategory.MASTS, "ant").any { it.displayName == "Antenna" })
        assertTrue(AssetCatalog.inCategory(AssetCategory.TRUCKS, "ant").isEmpty())
        assertEquals(
            listOf("Truck"),
            AssetCatalog.inCategory(AssetCategory.TRUCKS, "tru").map { it.displayName }
        )
    }
}
