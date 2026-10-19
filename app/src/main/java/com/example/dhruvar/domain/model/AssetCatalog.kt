package com.example.dhruvar.domain.model

/**
 * Placeable library shown by the planning asset drawer.
 *
 * A category is a group (Trucks, Tents). An entry is one thing you can place.
 * New variants are new entries here; they still point at an [AssetType] until
 * that variant has its own model file.
 */
enum class AssetCategory(val displayName: String) {
    TRUCKS("Trucks"),
    TENTS("Tents"),
    MASTS("Masts"),
    WORKS("Works")
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
        AssetCatalogEntry("truck", "Truck", AssetCategory.TRUCKS, AssetType.TRUCK),
        AssetCatalogEntry("car", "Car", AssetCategory.TRUCKS, AssetType.CAR),
        AssetCatalogEntry("tent", "Tent", AssetCategory.TENTS, AssetType.TENT),
        AssetCatalogEntry("antenna", "Antenna", AssetCategory.MASTS, AssetType.ANTENNA),
        AssetCatalogEntry("trench", "Trench", AssetCategory.WORKS, AssetType.TRENCH)
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
