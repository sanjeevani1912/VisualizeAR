package com.example.dhruvar.domain.model

/**
 * Represents the type of physical asset that can be positioned on the 2D planning
 * canvas and subsequently rendered as a 3D model in ARCore.
 *
 * Each asset type defines an authoritative [AssetSpecification] (in meters) to maintain
 * accurate real-world scale during 2D layout planning and 3D AR projection.
 */
enum class AssetType(
    val displayName: String,
    val defaultSpecification: AssetSpecification,
    val description: String
) {
    TENT(
        displayName = "Tent",
        defaultSpecification = AssetSpecification.DEFAULT_TENT,
        description = "Standard operational command or shelter tent"
    ),
    TRUCK(
        displayName = "Truck",
        defaultSpecification = AssetSpecification.DEFAULT_TRUCK,
        description = "Heavy logistics or transport vehicle"
    ),
    CAR(
        displayName = "Car",
        defaultSpecification = AssetSpecification.DEFAULT_CAR,
        description = "Light utility or passenger vehicle"
    ),
    ANTENNA(
        displayName = "Antenna",
        defaultSpecification = AssetSpecification.DEFAULT_ANTENNA,
        description = "High-gain RF communications mast"
    ),
    TRENCH(
        displayName = "Trench",
        defaultSpecification = AssetSpecification.DEFAULT_TRENCH,
        description = "Linear defensive trench or utility conduit"
    );

    // Dimension delegate properties for seamless backward compatibility
    val defaultWidthMeters: Float
        get() = defaultSpecification.widthMeters

    val defaultLengthMeters: Float
        get() = defaultSpecification.lengthMeters

    val defaultHeightMeters: Float
        get() = defaultSpecification.heightMeters
}
