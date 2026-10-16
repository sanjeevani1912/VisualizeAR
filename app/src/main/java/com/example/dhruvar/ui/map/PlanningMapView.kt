package com.example.dhruvar.ui.map

import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dhruvar.data.map.MapBasemapConfig
import com.example.dhruvar.data.map.MapStyleLoader
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.MapLayoutProjection
import com.example.dhruvar.domain.spatial.RegionWorkspaceGeometry
import androidx.compose.ui.graphics.toArgb
import com.example.dhruvar.ui.theme.Cacao
import com.example.dhruvar.ui.theme.Coal
import com.example.dhruvar.ui.theme.FieldAnchor
import com.example.dhruvar.ui.theme.Meadow
import com.example.dhruvar.ui.theme.Mist
import com.example.dhruvar.ui.theme.Moss
import com.example.dhruvar.ui.theme.Rust
import java.util.concurrent.atomic.AtomicReference
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SOURCE_REGION = "dhruvar-region"
private const val SOURCE_ANCHOR = "dhruvar-anchor"
private const val SOURCE_ASSETS = "dhruvar-assets"
private const val SOURCE_FOOTPRINTS = "dhruvar-footprints"
private const val SOURCE_MASK = "dhruvar-region-mask"
private const val SOURCE_GRID = "dhruvar-region-grid"
private const val LAYER_REGION_FILL = "dhruvar-region-fill"
private const val LAYER_REGION_LINE = "dhruvar-region-line"
private const val LAYER_MASK = "dhruvar-region-mask-fill"
private const val LAYER_GRID_MINOR = "dhruvar-region-grid-minor"
private const val LAYER_GRID_MAJOR = "dhruvar-region-grid-major"
private const val LAYER_FOOTPRINTS = "dhruvar-footprints-fill"
private const val LAYER_ASSETS = "dhruvar-assets-circle"
private const val LAYER_ANCHOR = "dhruvar-anchor-circle"
private const val CAMERA_FLY_MILLIS = 900
private const val WORKSPACE_FIT_PADDING_PX = 80
private const val DEFAULT_MAX_ZOOM = 25.5

/**
 * MapLibre planning surface backed by a PMTiles style ([MapBasemapConfig.styleUrl]).
 * Overlay GeoJSON FeatureCollections are replaced when [layout] / selection changes
 * (diff-friendly: one setGeoJson per overlay source, not per-frame rebuild loops).
 */
@Composable
fun PlanningMapView(
    layout: Layout,
    selectedObjectId: String?,
    interactionMode: MapInteractionMode,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
    onStyleError: (String?) -> Unit,
    onAssetGrabbed: (objectId: String) -> Unit = {},
    onAssetMoved: (objectId: String, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> },
    setupStep: PlanningSetupStep = PlanningSetupStep.PLACE_ASSETS,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapReadyError by remember { mutableStateOf<String?>(null) }
    var styleFallbackWarning by remember { mutableStateOf<String?>(null) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var lastCameraKey by remember { mutableStateOf<String?>(null) }
    var lastWorkspaceFitKey by remember { mutableStateOf<String?>(null) }
    var lastWorkspaceGeoKey by remember { mutableStateOf<String?>(null) }
    var cameraGeneration by remember { mutableIntStateOf(0) }

    val modeRef = remember { AtomicReference(interactionMode) }
    modeRef.set(interactionMode)
    val setupStepRef = remember { AtomicReference(setupStep) }
    setupStepRef.set(setupStep)
    val tapRef = remember { AtomicReference(onMapTap) }
    tapRef.set(onMapTap)
    val layoutRef = remember { AtomicReference(layout) }
    layoutRef.set(layout)
    val grabRef = remember { AtomicReference(onAssetGrabbed) }
    grabRef.set(onAssetGrabbed)
    val moveRef = remember { AtomicReference(onAssetMoved) }
    moveRef.set(onAssetMoved)
    val assetGrab = remember { AssetGrab() }
    val bumpCamera = remember { AtomicReference<() -> Unit>({}) }
    bumpCamera.set { cameraGeneration++ }
    val errorRef = remember { AtomicReference(onStyleError) }
    errorRef.set(onStyleError)

    val mapView = remember {
        MapView(context).also { it.onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val failListener = MapView.OnDidFailLoadingMapListener { error ->
            val message = error ?: "Failed to load map tiles"
            mapReadyError = message
            errorRef.get().invoke(message)
        }
        mapView.addOnDidFailLoadingMapListener(failListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(failListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            MapViewportBoundsBridge.map = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            mapRef.value = null
        }
    }

    LaunchedEffect(mapView) {
        // The style is fetched and repointed before MapLibre sees it, because the map server
        // advertises placeholder URLs for its tiles and glyphs. Falling back to the raw URL keeps
        // an unreachable server from leaving the planning surface with no basemap at all.
        val styleBuilder = MapStyleLoader.loadRewrittenStyle().fold(
            onSuccess = { Style.Builder().fromJson(it) },
            onFailure = { cause ->
                if (MapBasemapConfig.isServerConfigured()) {
                    val reason = cause.message ?: cause.javaClass.simpleName
                    styleFallbackWarning =
                        "Map server unreachable (${MapBasemapConfig.serverBase()}): $reason"
                }
                Style.Builder().fromUri(MapBasemapConfig.styleUrl())
            }
        )
        styleFallbackWarning?.let { warning ->
            mapReadyError = warning
            errorRef.get().invoke(warning)
        }

        mapView.getMapAsync { map ->
            mapRef.value = map
            MapViewportBoundsBridge.map = map
            map.uiSettings.isTiltGesturesEnabled = false
            // The Compose attribution line below carries the same credit, and MapLibre's own
            // bottom-left logo and info button collide with it.
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            val cameraListener = MapLibreMap.OnCameraMoveListener { bumpCamera.get().invoke() }
            map.addOnCameraMoveListener(cameraListener)
            mapView.setOnTouchListener { _, event ->
                handleAssetDrag(event, map, modeRef, layoutRef, grabRef, moveRef, assetGrab)
            }
            map.addOnMapClickListener { latLng ->
                val mode = modeRef.get()
                if (mode == MapInteractionMode.NAVIGATE) {
                    false
                } else {
                    tapRef.get().invoke(latLng.latitude, latLng.longitude)
                    true
                }
            }
            map.setStyle(styleBuilder) { style ->
                ensureOverlayLayers(style)
                styleReady = true
                // A config warning outlives a successful load: the fallback style can load fine
                // and still show no tiles.
                mapReadyError = styleFallbackWarning
                onStyleError(styleFallbackWarning)
                val step = setupStepRef.get()
                val workspace = RegionWorkspaceGeometry.showsWorkspace(step, layout.regionBounds)
                syncOverlays(style, layout, selectedObjectId, workspace)
                lastWorkspaceGeoKey = syncWorkspaceOverlays(style, layout, step, lastWorkspaceGeoKey)
                if (workspace) {
                    val bounds = layout.regionBounds!!
                    applyWorkspaceCameraConstraints(map, bounds)
                    fitWorkspaceCamera(map, bounds, animate = false)
                    lastWorkspaceFitKey = workspaceFitKey(bounds)
                    lastCameraKey = cameraKey(layout)
                } else {
                    clearWorkspaceCameraConstraints(map)
                    lastWorkspaceFitKey = null
                    centerCamera(map, layout, force = true)
                    lastCameraKey = cameraKey(layout)
                }
            }
        }
    }

    LaunchedEffect(
        layout,
        selectedObjectId,
        styleReady,
        setupStep
    ) {
        if (!styleReady) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val workspace = RegionWorkspaceGeometry.showsWorkspace(setupStep, layout.regionBounds)
        syncOverlays(style, layout, selectedObjectId, workspace)
        lastWorkspaceGeoKey = syncWorkspaceOverlays(style, layout, setupStep, lastWorkspaceGeoKey)
        if (workspace) {
            val bounds = layout.regionBounds!!
            applyWorkspaceCameraConstraints(map, bounds)
            val fitKey = workspaceFitKey(bounds)
            if (fitKey != lastWorkspaceFitKey) {
                fitWorkspaceCamera(map, bounds, animate = true)
                lastWorkspaceFitKey = fitKey
            }
            lastCameraKey = cameraKey(layout)
        } else {
            clearWorkspaceCameraConstraints(map)
            lastWorkspaceFitKey = null
            val key = cameraKey(layout)
            if (key != lastCameraKey && layout.anchor.hasGeographicFix) {
                centerCamera(map, layout, force = false)
                lastCameraKey = key
            }
        }
        MapViewportBoundsBridge.map = map
    }

    // Search results and any other outside caller fly the camera through the bridge. Held until
    // the style is ready so a request made during loading is not silently dropped.
    val cameraTarget by MapCameraBridge.target.collectAsState()
    LaunchedEffect(cameraTarget, styleReady) {
        val target = cameraTarget ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                MapLatLng(target.latitude, target.longitude),
                target.zoom
            ),
            CAMERA_FLY_MILLIS
        )
        MapCameraBridge.consumed(target)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        val mapForSymbols = mapRef.value
        if (mapForSymbols != null && styleReady && layout.anchor.hasGeographicFix) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Read so a pan or zoom redraws the symbols in their new screen positions.
                @Suppress("UNUSED_EXPRESSION")
                cameraGeneration
                drawPlacedAssets(mapForSymbols, layout, selectedObjectId)
            }
        }

        // Licence notice, so it must stay legible in full rather than ellipsize. It owns the
        // bottom edge of the map; PlanningScreen's HUD is inset above it.
        Text(
            text = MapBasemapConfig.ATTRIBUTION,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )

        val error = mapReadyError
        if (error != null) {
            Text(
                text = "Map tiles unavailable: $error",
                color = Rust,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            )
        }
    }
}

/** Reads the current MapLibre visible bounds for region confirmation. */
object MapViewportBoundsBridge {
    @Volatile
    var map: MapLibreMap? = null

    fun queryVisibleBounds(): VisibleMapBounds? {
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return VisibleMapBounds(
            north = bounds.latitudeNorth,
            south = bounds.latitudeSouth,
            east = bounds.longitudeEast,
            west = bounds.longitudeWest
        )
    }

    /** Pixel in the map view to a geographic coordinate; null before the map is ready. */
    fun toLatLng(x: Float, y: Float): Pair<Double, Double>? {
        val projected = map?.projection?.fromScreenLocation(PointF(x, y)) ?: return null
        if (!projected.latitude.isFinite() || !projected.longitude.isFinite()) return null
        return projected.latitude to projected.longitude
    }

    /**
     * Belt and braces alongside the drag overlay consuming its events: with gestures off, the
     * basemap cannot drift under a draft rectangle that is being drawn in screen space.
     */
    fun setGesturesEnabled(enabled: Boolean) {
        map?.uiSettings?.setAllGesturesEnabled(enabled)
        // setAllGesturesEnabled re-enables tilt, which the planning surface never wants.
        if (enabled) map?.uiSettings?.isTiltGesturesEnabled = false
    }
}

data class VisibleMapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

private fun cameraKey(layout: Layout): String =
    "${layout.anchor.id}:${layout.anchor.latitude}:${layout.anchor.longitude}:${layout.regionBounds}"

private fun workspaceFitKey(bounds: GeoBounds): String =
    "${bounds.northLatitude}:${bounds.southLatitude}:${bounds.eastLongitude}:${bounds.westLongitude}"

private fun workspaceGeoKey(layout: Layout, step: PlanningSetupStep): String {
    val bounds = layout.regionBounds
    val origin = if (layout.anchor.hasGeographicFix) {
        "${layout.anchor.latitude}:${layout.anchor.longitude}:${layout.anchor.headingDegrees}"
    } else {
        "sw"
    }
    return "$step:$bounds:$origin"
}

private fun centerCamera(map: MapLibreMap, layout: Layout, force: Boolean) {
    val anchor = layout.anchor
    val region = layout.regionBounds
    if (region != null && force) {
        val box = LatLngBounds.Builder()
            .include(MapLatLng(region.northLatitude, region.westLongitude))
            .include(MapLatLng(region.southLatitude, region.eastLongitude))
            .build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(box, 64))
        return
    }
    if (anchor.hasGeographicFix) {
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(MapLatLng(anchor.latitude!!, anchor.longitude!!))
                .zoom(MapBasemapConfig.ANCHOR_ZOOM)
                .build()
        )
        if (force) map.moveCamera(update) else map.animateCamera(update, 700)
        return
    }
    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(MapLatLng(MapBasemapConfig.DEFAULT_LATITUDE, MapBasemapConfig.DEFAULT_LONGITUDE))
                .zoom(MapBasemapConfig.DEFAULT_ZOOM)
                .build()
        )
    )
}

/**
 * Overlay colours come from the Field Kit tokens rather than literals so the map stays in step
 * with the rest of the chrome. Three roles must stay tellable apart on an OSM basemap: Rust marks
 * the single origin, Cacao marks placed assets, and Meadow/Moss marks the region.
 */
private fun ensureOverlayLayers(style: Style) {
    if (style.getSource(SOURCE_REGION) == null) {
        style.addSource(GeoJsonSource(SOURCE_REGION, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            FillLayer(LAYER_REGION_FILL, SOURCE_REGION).withProperties(
                PropertyFactory.fillColor(Meadow.copy(alpha = 0.22f).toArgb()),
                PropertyFactory.fillOutlineColor(Moss.toArgb())
            )
        )
        style.addLayer(
            LineLayer(LAYER_REGION_LINE, SOURCE_REGION).withProperties(
                PropertyFactory.lineColor(Moss.toArgb()),
                PropertyFactory.lineWidth(2f)
            )
        )
    }
    ensureWorkspaceLayers(style)
    if (style.getSource(SOURCE_FOOTPRINTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_FOOTPRINTS, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            FillLayer(LAYER_FOOTPRINTS, SOURCE_FOOTPRINTS).withProperties(
                // The Compose overlay draws the recognisable shape. This layer only keeps the
                // geographic footprint available for hit-testing and future style tweaks.
                PropertyFactory.fillOpacity(0f),
                PropertyFactory.fillColor(Meadow.copy(alpha = 0.40f).toArgb()),
                PropertyFactory.fillOutlineColor(Coal.toArgb())
            )
        )
    }
    if (style.getSource(SOURCE_ASSETS) == null) {
        style.addSource(GeoJsonSource(SOURCE_ASSETS, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            CircleLayer(LAYER_ASSETS, SOURCE_ASSETS).withProperties(
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.eq(Expression.get("selected"), Expression.literal(true)),
                        Expression.literal(10f),
                        Expression.literal(7f)
                    )
                ),
                // Selection is already signalled by radius; recolouring to Moss as well makes the
                // selected asset read as a second origin marker.
                PropertyFactory.circleColor(Cacao.toArgb()),
                PropertyFactory.circleOpacity(0f),
                PropertyFactory.circleStrokeWidth(0f),
                PropertyFactory.circleStrokeColor(Mist.toArgb())
            )
        )
    }
    if (style.getSource(SOURCE_ANCHOR) == null) {
        style.addSource(GeoJsonSource(SOURCE_ANCHOR, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            CircleLayer(LAYER_ANCHOR, SOURCE_ANCHOR).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(FieldAnchor.toArgb()),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Mist.toArgb())
            )
        )
    }
}

private fun ensureWorkspaceLayers(style: Style) {
    if (style.getSource(SOURCE_MASK) == null) {
        style.addSource(GeoJsonSource(SOURCE_MASK, FeatureCollection.fromFeatures(emptyArray())))
        val mask = FillLayer(LAYER_MASK, SOURCE_MASK).withProperties(
            PropertyFactory.fillColor(Coal.copy(alpha = 0.42f).toArgb()),
            PropertyFactory.fillAntialias(true)
        )
        if (style.getLayer(LAYER_REGION_LINE) != null) {
            style.addLayerBelow(mask, LAYER_REGION_LINE)
        } else {
            style.addLayer(mask)
        }
    }
    if (style.getSource(SOURCE_GRID) == null) {
        style.addSource(GeoJsonSource(SOURCE_GRID, FeatureCollection.fromFeatures(emptyArray())))
        val minor = LineLayer(LAYER_GRID_MINOR, SOURCE_GRID).withProperties(
            PropertyFactory.lineColor(Coal.copy(alpha = 0.34f).toArgb()),
            PropertyFactory.lineWidth(0.9f)
        ).withFilter(
            Expression.neq(Expression.get("major"), Expression.literal(true))
        )
        val major = LineLayer(LAYER_GRID_MAJOR, SOURCE_GRID).withProperties(
            PropertyFactory.lineColor(Cacao.copy(alpha = 0.48f).toArgb()),
            PropertyFactory.lineWidth(1.5f)
        ).withFilter(
            Expression.eq(Expression.get("major"), Expression.literal(true))
        )
        val below = if (style.getLayer(LAYER_REGION_LINE) != null) {
            LAYER_REGION_LINE
        } else {
            null
        }
        if (below != null) {
            style.addLayerBelow(minor, below)
            style.addLayerBelow(major, below)
        } else {
            style.addLayer(minor)
            style.addLayer(major)
        }
    }
}

private fun syncOverlays(
    style: Style,
    layout: Layout,
    selectedObjectId: String?,
    workspace: Boolean
) {
    val regionSource = style.getSourceAs<GeoJsonSource>(SOURCE_REGION)
    val region = layout.regionBounds
    if (region != null) {
        val ring = listOf(
            Point.fromLngLat(region.westLongitude, region.northLatitude),
            Point.fromLngLat(region.eastLongitude, region.northLatitude),
            Point.fromLngLat(region.eastLongitude, region.southLatitude),
            Point.fromLngLat(region.westLongitude, region.southLatitude),
            Point.fromLngLat(region.westLongitude, region.northLatitude)
        )
        regionSource?.setGeoJson(
            FeatureCollection.fromFeature(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
        )
    } else {
        regionSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
    // Meadow interior fill fights the hole-punch; keep the Moss outline either way.
    style.getLayer(LAYER_REGION_FILL)?.setProperties(
        PropertyFactory.fillOpacity(if (workspace) 0f else 0.22f)
    )

    val anchorSource = style.getSourceAs<GeoJsonSource>(SOURCE_ANCHOR)
    if (layout.anchor.hasGeographicFix) {
        anchorSource?.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(layout.anchor.longitude!!, layout.anchor.latitude!!)
            )
        )
    } else {
        anchorSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    val assetFeatures = mutableListOf<Feature>()
    val footprintFeatures = mutableListOf<Feature>()
    for (obj in layout.objects) {
        val center = MapLayoutProjection.objectCenterLatLng(layout.anchor, obj) ?: continue
        val feature = Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude))
        feature.addStringProperty("id", obj.id)
        feature.addStringProperty("name", obj.name)
        feature.addStringProperty("kind", obj.assetType.name)
        feature.addBooleanProperty("selected", obj.id == selectedObjectId)
        assetFeatures.add(feature)

        val ringLatLngs = MapLayoutProjection.objectFootprintLatLngs(layout.anchor, obj)
        if (ringLatLngs != null && ringLatLngs.size >= 4) {
            val pts = ringLatLngs.map { Point.fromLngLat(it.longitude, it.latitude) }
            footprintFeatures.add(Feature.fromGeometry(Polygon.fromLngLats(listOf(pts))))
        }
    }
    style.getSourceAs<GeoJsonSource>(SOURCE_ASSETS)
        ?.setGeoJson(FeatureCollection.fromFeatures(assetFeatures))
    style.getSourceAs<GeoJsonSource>(SOURCE_FOOTPRINTS)
        ?.setGeoJson(FeatureCollection.fromFeatures(footprintFeatures))
}

/**
 * Hole-punch + grid GeoJSON. Rebuilds only when the workspace key changes so a pan
 * does not rebuild line strings every frame.
 */
private fun syncWorkspaceOverlays(
    style: Style,
    layout: Layout,
    step: PlanningSetupStep,
    lastKey: String?
): String? {
    val workspace = RegionWorkspaceGeometry.showsWorkspace(step, layout.regionBounds)
    val key = if (workspace) workspaceGeoKey(layout, step) else "off"
    if (key == lastKey) return lastKey

    val maskSource = style.getSourceAs<GeoJsonSource>(SOURCE_MASK)
    val gridSource = style.getSourceAs<GeoJsonSource>(SOURCE_GRID)
    if (!workspace) {
        maskSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        gridSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return key
    }

    val bounds = layout.regionBounds!!
    maskSource?.setGeoJson(Feature.fromGeometry(regionMaskPolygon(bounds)))

    val origin = if (layout.anchor.hasGeographicFix) {
        layout.anchor.latitude!! to layout.anchor.longitude!!
    } else {
        null
    }
    val heading = if (layout.anchor.hasGeographicFix) layout.anchor.headingDegrees else null
    val lines = RegionWorkspaceGeometry.gridLines(
        bounds = bounds,
        headingDegrees = heading,
        originLatLng = origin
    )
    val features = lines.map { line ->
        val pts = line.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        Feature.fromGeometry(LineString.fromLngLats(pts)).also { feature ->
            feature.addBooleanProperty("major", line.major)
        }
    }
    gridSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    return key
}

private fun regionMaskPolygon(bounds: GeoBounds): Polygon {
    val world = listOf(
        Point.fromLngLat(-179.9, -85.0),
        Point.fromLngLat(179.9, -85.0),
        Point.fromLngLat(179.9, 85.0),
        Point.fromLngLat(-179.9, 85.0),
        Point.fromLngLat(-179.9, -85.0)
    )
    val hole = listOf(
        Point.fromLngLat(bounds.westLongitude, bounds.southLatitude),
        Point.fromLngLat(bounds.westLongitude, bounds.northLatitude),
        Point.fromLngLat(bounds.eastLongitude, bounds.northLatitude),
        Point.fromLngLat(bounds.eastLongitude, bounds.southLatitude),
        Point.fromLngLat(bounds.westLongitude, bounds.southLatitude)
    )
    return Polygon.fromLngLats(listOf(world, hole))
}

private fun applyWorkspaceCameraConstraints(map: MapLibreMap, bounds: GeoBounds) {
    val padded = RegionWorkspaceGeometry.expandBounds(bounds)
    val constraintBox = LatLngBounds.Builder()
        .include(MapLatLng(padded.northLatitude, padded.westLongitude))
        .include(MapLatLng(padded.southLatitude, padded.eastLongitude))
        .build()
    map.setLatLngBoundsForCameraTarget(constraintBox)

    val regionBox = LatLngBounds.Builder()
        .include(MapLatLng(bounds.northLatitude, bounds.westLongitude))
        .include(MapLatLng(bounds.southLatitude, bounds.eastLongitude))
        .build()
    val fitted = map.getCameraForLatLngBounds(
        regionBox,
        intArrayOf(56, WORKSPACE_FIT_PADDING_PX, 56, WORKSPACE_FIT_PADDING_PX)
    )
    val fitZoom = fitted?.zoom ?: run {
        val latSpan = (bounds.northLatitude - bounds.southLatitude).coerceAtLeast(1e-9)
        val lngSpan = (bounds.eastLongitude - bounds.westLongitude).coerceAtLeast(1e-9)
        minOf(
            RegionWorkspaceGeometry.zoomToFitSpan(latSpan),
            RegionWorkspaceGeometry.zoomToFitSpan(lngSpan)
        )
    }
    val minZoom = (fitZoom - 0.2).coerceAtLeast(0.0)
    map.setMinZoomPreference(minZoom)
    map.setMaxZoomPreference((fitZoom + 8.0).coerceIn(minZoom + 0.5, 22.0))
}

private fun fitWorkspaceCamera(map: MapLibreMap, bounds: GeoBounds, animate: Boolean) {
    val box = LatLngBounds.Builder()
        .include(MapLatLng(bounds.northLatitude, bounds.westLongitude))
        .include(MapLatLng(bounds.southLatitude, bounds.eastLongitude))
        .build()
    val update = CameraUpdateFactory.newLatLngBounds(box, WORKSPACE_FIT_PADDING_PX)
    if (animate) {
        map.animateCamera(update, CAMERA_FLY_MILLIS)
    } else {
        map.moveCamera(update)
    }
}

private fun clearWorkspaceCameraConstraints(map: MapLibreMap) {
    map.setLatLngBoundsForCameraTarget(null)
    map.setMinZoomPreference(0.0)
    map.setMaxZoomPreference(DEFAULT_MAX_ZOOM)
}

/** Finger offset so the asset stays under the point you grabbed, not snapped to its centre. */
private class AssetGrab {
    var objectId: String? = null
    var offsetX: Float = 0f
    var offsetZ: Float = 0f
}

private fun handleAssetDrag(
    event: MotionEvent,
    map: MapLibreMap,
    modeRef: AtomicReference<MapInteractionMode>,
    layoutRef: AtomicReference<Layout>,
    grabRef: AtomicReference<(String) -> Unit>,
    moveRef: AtomicReference<(String, Double, Double) -> Unit>,
    grab: AssetGrab
): Boolean {
    val mode = modeRef.get()
    if (mode != MapInteractionMode.NAVIGATE && mode != MapInteractionMode.EDIT) {
        grab.objectId = null
        return false
    }
    val layout = layoutRef.get()
    if (!layout.anchor.hasGeographicFix) return false
    val projected = map.projection.fromScreenLocation(PointF(event.x, event.y))
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            val id = MapLayoutProjection.findNearestObjectId(
                layout.anchor,
                layout.objects,
                projected.latitude,
                projected.longitude
            ) ?: return false
            val obj = layout.objects.firstOrNull { it.id == id } ?: return false
            val finger = MapLayoutProjection.metersFromAnchor(
                layout.anchor,
                projected.latitude,
                projected.longitude
            ) ?: return false
            grab.objectId = id
            grab.offsetX = finger.first - obj.x
            grab.offsetZ = finger.second - obj.z
            grabRef.get().invoke(id)
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val id = grab.objectId ?: return false
            val finger = MapLayoutProjection.metersFromAnchor(
                layout.anchor,
                projected.latitude,
                projected.longitude
            ) ?: return true
            val center = GeographicCoordinateConverter.metersOffsetToLatLng(
                layout.anchor,
                finger.first - grab.offsetX,
                finger.second - grab.offsetZ
            )
            moveRef.get().invoke(id, center.latitude, center.longitude)
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val consumed = grab.objectId != null
            grab.objectId = null
            return consumed
        }
        else -> return grab.objectId != null
    }
}

private fun DrawScope.drawPlacedAssets(
    map: MapLibreMap,
    layout: Layout,
    selectedObjectId: String?
) {
    val anchor = layout.anchor
    for (obj in layout.objects) {
        val ring = MapLayoutProjection.objectFootprintLatLngs(anchor, obj) ?: continue
        if (ring.size < 4) continue
        val pts = ring.take(4).map { corner ->
            val screen = map.projection.toScreenLocation(
                org.maplibre.android.geometry.LatLng(corner.latitude, corner.longitude)
            )
            Offset(screen.x, screen.y)
        }
        val selected = obj.id == selectedObjectId
        val fill = assetBodyColor(obj.assetType).copy(alpha = if (selected) 0.78f else 0.62f)
        val stroke = if (selected) Moss else Coal
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(width = if (selected) 4f else 2.5f))
        drawAssetMark(obj.assetType, pts, Mist)
    }
}

private fun assetBodyColor(type: AssetType) = when (type) {
    AssetType.TENT -> Meadow
    AssetType.TRUCK -> Cacao
    AssetType.CAR -> Rust
    AssetType.ANTENNA -> Moss
    AssetType.TRENCH -> Coal
}

/** Top-down marks inside the ground rectangle, so a tent does not read as a truck. */
private fun DrawScope.drawAssetMark(type: AssetType, pts: List<Offset>, color: androidx.compose.ui.graphics.Color) {
    val center = Offset(
        (pts[0].x + pts[1].x + pts[2].x + pts[3].x) / 4f,
        (pts[0].y + pts[1].y + pts[2].y + pts[3].y) / 4f
    )
    val along = Offset(
        ((pts[3].x + pts[2].x) - (pts[0].x + pts[1].x)) / 4f,
        ((pts[3].y + pts[2].y) - (pts[0].y + pts[1].y)) / 4f
    )
    val across = Offset(
        ((pts[1].x + pts[2].x) - (pts[0].x + pts[3].x)) / 4f,
        ((pts[1].y + pts[2].y) - (pts[0].y + pts[3].y)) / 4f
    )
    when (type) {
        AssetType.TENT -> {
            drawLine(color, center - along, center + along, strokeWidth = 2.5f)
            drawLine(color, center - along - across, center - along * 0.55f, strokeWidth = 2f)
            drawLine(color, center - along + across, center - along * 0.55f, strokeWidth = 2f)
        }
        AssetType.TRUCK -> {
            drawLine(color, center - along * 0.35f - across, center - along * 0.35f + across, strokeWidth = 2.5f)
            drawLine(color, center, center + along, strokeWidth = 2f)
        }
        AssetType.CAR -> {
            val cabin = Path().apply {
                moveTo((center - along * 0.15f - across * 0.55f).x, (center - along * 0.15f - across * 0.55f).y)
                lineTo((center - along * 0.15f + across * 0.55f).x, (center - along * 0.15f + across * 0.55f).y)
                lineTo((center + along * 0.35f + across * 0.55f).x, (center + along * 0.35f + across * 0.55f).y)
                lineTo((center + along * 0.35f - across * 0.55f).x, (center + along * 0.35f - across * 0.55f).y)
                close()
            }
            drawPath(cabin, color.copy(alpha = 0.85f))
        }
        AssetType.ANTENNA -> {
            drawCircle(color, radius = 4f, center = center)
            drawLine(color, center - across, center + across, strokeWidth = 2f)
            drawLine(color, center - along, center + along, strokeWidth = 2f)
        }
        AssetType.TRENCH -> {
            drawLine(color, center - along - across * 0.35f, center + along - across * 0.35f, strokeWidth = 2f)
            drawLine(color, center - along + across * 0.35f, center + along + across * 0.35f, strokeWidth = 2f)
        }
    }
}
