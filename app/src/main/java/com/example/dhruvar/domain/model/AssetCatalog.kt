package com.example.dhruvar.domain.model

/**
 * Placeable library shown by the planning asset drawer.
 *
 * A category is a group (Trucks, Tents). An entry is one thing you can place.
 * New variants are new entries here; they still point at an [AssetType] until
 * that variant has its own model file.
 */
enum class AssetCategory(val displayName: String) {
    AIRCRAFT("Aircraft"),
    VEHICLES("Vehicles"),
    SYSTEMS("Systems")
}

data class AssetCatalogEntry(
    val id: String,
    val displayName: String,
    val category: AssetCategory,
    val assetType: AssetType
) {
    val widthMeters: Float get() = assetType.defaultWidthMeters
    val lengthMeters: Float get() = assetType.defaultLengthMeters
    val heightMeters: Float get() = assetType.defaultHeightMeters

    fun footprintLabel(): String {
        fun meters(value: Float): String =
            if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
        return "${meters(widthMeters)} × ${meters(lengthMeters)} m"
    }
}

object AssetCatalog {
    val entries: List<AssetCatalogEntry> = listOf(
        AssetCatalogEntry("gunship", "Gunship", AssetCategory.AIRCRAFT, AssetType.GUNSHIP),
        AssetCatalogEntry("military_vehicle", "Military Vehicle", AssetCategory.VEHICLES, AssetType.MILITARY_VEHICLE),
        AssetCatalogEntry("offroad_vehicle", "Off-road Vehicle", AssetCategory.VEHICLES, AssetType.OFFROAD_VEHICLE),
        AssetCatalogEntry("k131", "K-131", AssetCategory.SYSTEMS, AssetType.K131),
        AssetCatalogEntry("k131_takeoff", "K-131 Take-Off", AssetCategory.SYSTEMS, AssetType.K131_TAKEOFF),
        AssetCatalogEntry("str_mgs", "STR-MGS", AssetCategory.SYSTEMS, AssetType.STR_MGS)
    )

    val categories: List<AssetCategory> =
        AssetCategory.entries.filter { category -> entries.any { it.category == category } }

    fun count(category: AssetCategory): Int = entries.count { it.category == category }

    fun inCategory(category: AssetCategory, query: String): List<AssetCatalogEntry> {
        val needle = query.trim()
        return entries.filter { entry ->
            entry.category == category &&
                (needle.isEmpty() || entry.displayName.contains(needle, ignoreCase = true))
        }
    }
}
