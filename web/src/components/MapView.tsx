import { useEffect, useRef } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { ASSET_DEFAULTS, dim, footprintRing, gridFeatures, offsetToLatLng } from '../geo'
import type { LayoutData } from '../types'

const STYLE_URL = import.meta.env.VITE_MAP_STYLE_URL as string | undefined

// Offline style: no network access and no third-party tiles. The region, grid and assets are drawn
// as vector overlays on a plain background. Set VITE_MAP_STYLE_URL to use the map server's basemap.
const OFFLINE_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {},
  layers: [{ id: 'bg', type: 'background', paint: { 'background-color': '#EDE6D2' } }],
}

interface Props {
  layout: LayoutData
  highlightId: string | null
  onSelect: (id: string | null) => void
}

function fc(features: GeoJSON.Feature[]): GeoJSON.FeatureCollection {
  return { type: 'FeatureCollection', features }
}

function buildData(layout: LayoutData) {
  const b = layout.regionBounds
  const ring: [number, number][] = [
    [b.westLongitude, b.southLatitude],
    [b.eastLongitude, b.southLatitude],
    [b.eastLongitude, b.northLatitude],
    [b.westLongitude, b.northLatitude],
    [b.westLongitude, b.southLatitude],
  ]
  const region: GeoJSON.Feature = { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [ring] } }
  const mask: GeoJSON.Feature = {
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'Polygon',
      coordinates: [
        [[-180, -85], [180, -85], [180, 85], [-180, 85], [-180, -85]],
        [...ring].reverse(),
      ],
    },
  }
  const footprints: GeoJSON.Feature[] = layout.objects.map((o) => ({
    type: 'Feature',
    properties: { id: o.id, color: ASSET_DEFAULTS[o.assetType].color },
    geometry: { type: 'Polygon', coordinates: [footprintRing(layout.anchor, o)] },
  }))
  const points: GeoJSON.Feature[] = layout.objects.map((o) => ({
    type: 'Feature',
    properties: { id: o.id, color: ASSET_DEFAULTS[o.assetType].color },
    geometry: { type: 'Point', coordinates: offsetToLatLng(layout.anchor, o.x, o.z) },
  }))
  const origin: GeoJSON.Feature = {
    type: 'Feature',
    properties: {},
    geometry: { type: 'Point', coordinates: [layout.anchor.longitude, layout.anchor.latitude] },
  }
  return {
    region: fc([region]),
    mask: fc([mask]),
    grid: fc(gridFeatures(b)),
    footprints: fc(footprints),
    points: fc(points),
    origin: fc([origin]),
  }
}

export function MapView({ layout, highlightId, onSelect }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect

  useEffect(() => {
    if (!el.current) return
    const b = layout.regionBounds
    const map = new maplibregl.Map({
      container: el.current,
      style: STYLE_URL || OFFLINE_STYLE,
      bounds: [
        [b.westLongitude, b.southLatitude],
        [b.eastLongitude, b.northLatitude],
      ],
      fitBoundsOptions: { padding: 56 },
      attributionControl: false,
      maxPitch: 0,
      dragRotate: false,
    })
    mapRef.current = map
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')
    map.addControl(
      new maplibregl.AttributionControl({
        compact: true,
        customAttribution: STYLE_URL
          ? '© OpenStreetMap contributors · Boundaries: Survey of India via DataMeet (CC BY 4.0)'
          : 'Offline view · region, grid and assets only',
      }),
      'bottom-right',
    )

    const popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 10 })

    map.on('load', () => {
      const d = buildData(layout)
      map.addSource('mask', { type: 'geojson', data: d.mask })
      map.addSource('region', { type: 'geojson', data: d.region })
      map.addSource('grid', { type: 'geojson', data: d.grid })
      map.addSource('footprints', { type: 'geojson', data: d.footprints })
      map.addSource('points', { type: 'geojson', data: d.points })
      map.addSource('origin', { type: 'geojson', data: d.origin })
      map.addLayer({ id: 'mask', type: 'fill', source: 'mask', paint: { 'fill-color': '#231F20', 'fill-opacity': 0.14 } })
      map.addLayer({ id: 'region-fill', type: 'fill', source: 'region', paint: { 'fill-color': '#B5C37C', 'fill-opacity': 0.28 } })
      map.addLayer({ id: 'grid-minor', type: 'line', source: 'grid', filter: ['!=', ['get', 'major'], true], paint: { 'line-color': '#603620', 'line-opacity': 0.14, 'line-width': 0.6 } })
      map.addLayer({ id: 'grid-major', type: 'line', source: 'grid', filter: ['==', ['get', 'major'], true], paint: { 'line-color': '#603620', 'line-opacity': 0.34, 'line-width': 0.9 } })
      map.addLayer({ id: 'region-line', type: 'line', source: 'region', paint: { 'line-color': '#63703D', 'line-width': 2.2 } })
      map.addLayer({ id: 'footprints', type: 'fill', source: 'footprints', paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.78 } })
      map.addLayer({ id: 'footprints-line', type: 'line', source: 'footprints', paint: { 'line-color': '#231F20', 'line-width': 0.8 } })
      map.addLayer({
        id: 'dots',
        type: 'circle',
        source: 'points',
        paint: {
          'circle-color': ['get', 'color'],
          'circle-radius': 4.5,
          'circle-opacity': ['interpolate', ['linear'], ['zoom'], 16, 1, 18, 0],
          'circle-stroke-color': '#FCF9F2',
          'circle-stroke-width': 1.2,
          'circle-stroke-opacity': ['interpolate', ['linear'], ['zoom'], 16, 1, 18, 0],
        },
      })
      map.addLayer({ id: 'highlight', type: 'line', source: 'footprints', filter: ['==', ['get', 'id'], ''], paint: { 'line-color': '#B56B37', 'line-width': 3.2 } })
      map.addLayer({
        id: 'highlight-dot',
        type: 'circle',
        source: 'points',
        filter: ['==', ['get', 'id'], ''],
        paint: { 'circle-color': 'rgba(0,0,0,0)', 'circle-radius': 9, 'circle-stroke-color': '#B56B37', 'circle-stroke-width': 3 },
      })
      map.addLayer({ id: 'origin-ring', type: 'circle', source: 'origin', paint: { 'circle-color': '#FCF9F2', 'circle-radius': 8, 'circle-stroke-color': '#B56B37', 'circle-stroke-width': 3 } })
      map.addLayer({ id: 'origin-dot', type: 'circle', source: 'origin', paint: { 'circle-color': '#B56B37', 'circle-radius': 3 } })

      const hit = (point: maplibregl.PointLike) => map.queryRenderedFeatures(point, { layers: ['dots', 'footprints'] })[0]
      map.on('click', (e) => {
        const f = hit(e.point)
        onSelectRef.current(f ? String(f.properties?.id) : null)
      })
      map.on('mousemove', (e) => {
        const f = hit(e.point)
        map.getCanvas().style.cursor = f ? 'pointer' : ''
        const o = f ? layout.objects.find((x) => x.id === f.properties?.id) : undefined
        if (o) {
          popup
            .setLngLat(e.lngLat)
            .setHTML(`<strong>${o.name}</strong><br/>${ASSET_DEFAULTS[o.assetType].label} · ${dim(o.widthMeters)}×${dim(o.lengthMeters)} m`)
            .addTo(map)
        } else popup.remove()
      })
      map.on('mouseout', () => popup.remove())
    })
    return () => {
      popup.remove()
      map.remove()
      mapRef.current = null
    }
  }, [layout])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const apply = () => {
      const f: maplibregl.FilterSpecification = ['==', ['get', 'id'], highlightId ?? '']
      if (map.getLayer('highlight')) map.setFilter('highlight', f)
      if (map.getLayer('highlight-dot')) map.setFilter('highlight-dot', f)
    }
    if (map.getLayer('highlight')) apply()
    else map.once('load', apply)
  }, [highlightId, layout])

  return <div ref={el} className="map" role="img" aria-label={`Map of ${layout.name}: region, grid and ${layout.objects.length} assets`} />
}
