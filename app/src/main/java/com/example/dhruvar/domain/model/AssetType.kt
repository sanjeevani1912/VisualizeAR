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
    GUNSHIP(
        displayName = "Gunship",
        defaultSpecification = AssetSpecification.DEFAULT_GUNSHIP,
        description = "Armed rotary-wing gunship"
    ),
    K131(
        displayName = "K-131",
        defaultSpecification = AssetSpecification.DEFAULT_K131,
        description = "K-131 platform"
    ),
    K131_TAKEOFF(
        displayName = "K-131 Take-Off",
        defaultSpecification = AssetSpecification.DEFAULT_K131_TAKEOFF,
        description = "K-131 platform in take-off configuration"
    ),
    MILITARY_VEHICLE(
        displayName = "Military Vehicle",
        defaultSpecification = AssetSpecification.DEFAULT_MILITARY_VEHICLE,
        description = "Light military utility vehicle"
    ),
    OFFROAD_VEHICLE(
        displayName = "Off-road Vehicle",
        defaultSpecification = AssetSpecification.DEFAULT_OFFROAD_VEHICLE,
        description = "Off-road transport vehicle"
    ),
    STR_MGS(
        displayName = "STR-MGS",
        defaultSpecification = AssetSpecification.DEFAULT_STR_MGS,
        description = "STR-MGS mobile gun system (olive drab)"
    );

    // Dimension delegate properties for seamless backward compatibility
    val defaultWidthMeters: Float
        get() = defaultSpecification.widthMeters

    val defaultLengthMeters: Float
        get() = defaultSpecification.lengthMeters

    val defaultHeightMeters: Float
        get() = defaultSpecification.heightMeters
}
