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
    fun `library holds exactly the six supplied assets`() {
        assertEquals(
            setOf("Gunship", "K-131", "K-131 Take-Off", "Military Vehicle", "Off-road Vehicle", "STR-MGS"),
            AssetCatalog.entries.map { it.displayName }.toSet()
        )
    }

    @Test
    fun `vehicles drill down lists both vehicles`() {
        val names = AssetCatalog.inCategory(AssetCategory.VEHICLES, "").map { it.displayName }
        assertEquals(listOf("Military Vehicle", "Off-road Vehicle"), names)
    }

    @Test
    fun `search stays inside the open category`() {
        assertTrue(AssetCatalog.inCategory(AssetCategory.SYSTEMS, "k-1").any { it.displayName == "K-131" })
        assertTrue(AssetCatalog.inCategory(AssetCategory.VEHICLES, "k-1").isEmpty())
        assertEquals(
            listOf("Gunship"),
            AssetCatalog.inCategory(AssetCategory.AIRCRAFT, "gun").map { it.displayName }
        )
    }
}
