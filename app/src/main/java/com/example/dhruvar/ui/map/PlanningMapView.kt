package com.example.dhruvar.ui.map

import android.graphics.Color
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.spatial.MapLayoutProjection
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
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SOURCE_REGION = "dhruvar-region"
private const val SOURCE_ANCHOR = "dhruvar-anchor"
private const val SOURCE_ASSETS = "dhruvar-assets"
private const val SOURCE_FOOTPRINTS = "dhruvar-footprints"
private const val LAYER_REGION_FILL = "dhruvar-region-fill"
private const val LAYER_REGION_LINE = "dhruvar-region-line"
private const val LAYER_FOOTPRINTS = "dhruvar-footprints-fill"
private const val LAYER_ASSETS = "dhruvar-assets-circle"
private const val LAYER_ANCHOR = "dhruvar-anchor-circle"

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapReadyError by remember { mutableStateOf<String?>(null) }
    var styleFallbackWarning by remember { mutableStateOf<String?>(null) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var lastCameraKey by remember { mutableStateOf<String?>(null) }

    val modeRef = remember { AtomicReference(interactionMode) }
    modeRef.set(interactionMode)
    val tapRef = remember { AtomicReference(onMapTap) }
    tapRef.set(onMapTap)
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
                syncOverlays(style, layout, selectedObjectId)
                centerCamera(map, layout, force = true)
                lastCameraKey = cameraKey(layout)
            }
        }
    }

    LaunchedEffect(layout, selectedObjectId, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        syncOverlays(style, layout, selectedObjectId)
        val key = cameraKey(layout)
        if (key != lastCameraKey && layout.anchor.hasGeographicFix) {
            centerCamera(map, layout, force = false)
            lastCameraKey = key
        }
        MapViewportBoundsBridge.map = map
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

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
}

data class VisibleMapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

private fun cameraKey(layout: Layout): String =
    "${layout.anchor.id}:${layout.anchor.latitude}:${layout.anchor.longitude}:${layout.regionBounds}"

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
    if (style.getSource(SOURCE_FOOTPRINTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_FOOTPRINTS, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            FillLayer(LAYER_FOOTPRINTS, SOURCE_FOOTPRINTS).withProperties(
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
                PropertyFactory.circleStrokeWidth(2f),
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

private fun syncOverlays(style: Style, layout: Layout, selectedObjectId: String?) {
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
