import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { ASSET_DEFAULTS, dim, footprintRing, gridFeatures, offsetToLatLng } from '../geo'
import { ASSET_TYPES, assetIconSvg, assetIconUrl, SELECTION_RING_SVG, svgDataUrl, UPRIGHT_TYPES } from '../assetIcons'
import type { LayoutData } from '../types'

// Optional basemap. May be absolute (http://host:8000/map/style.json) or relative (/map/style.json, same origin as the
// dashboard, e.g. behind Nginx). A relative address is made absolute here so every part of the map can use it.
// Nothing is hard-coded: with no value the map is offline.
const RAW_STYLE_URL = (import.meta.env.VITE_MAP_STYLE_URL as string | undefined)?.trim() || undefined
const STYLE_URL = RAW_STYLE_URL ? new URL(RAW_STYLE_URL, window.location.href).href : undefined
// The overlay sources this file adds; a load error from any other source means the basemap is struggling.
const OVERLAY_SOURCES = new Set(['mask', 'region', 'grid', 'footprints', 'points', 'origin'])

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

/**
 * Degrees clockwise from north that an asset's front points to: the plan heading minus the asset's own rotation.
 * (Verified against the footprint drawing: the long edge of each footprint has exactly this bearing.)
 */
export function assetBearing(layout: LayoutData, rotationDegrees: number): number {
  return (((layout.anchor.headingDegrees - rotationDegrees) % 360) + 360) % 360
}

// Extra room at the top so the legend overlay never covers the assets.
const FIT_PADDING = { top: 96, right: 56, bottom: 56, left: 56 }

const ICON_PX = 32 // icon size in CSS pixels at icon-size 1
const ICON_SCALE = 2 // drawn at 2x so icons stay sharp on high-density screens

function loadSvgImage(svg: string, size: number): Promise<ImageData> {
  return new Promise((resolve, reject) => {
    const img = new Image(size, size)
    img.onload = () => {
      const c = document.createElement('canvas')
      c.width = size
      c.height = size
      const ctx = c.getContext('2d')
      if (!ctx) return reject(new Error('no 2d canvas'))
      ctx.drawImage(img, 0, 0, size, size)
      resolve(ctx.getImageData(0, 0, size, size))
    }
    img.onerror = () => reject(new Error('icon failed to render'))
    img.src = svgDataUrl(svg)
  })
}

/** Registers one image per asset type (plus the selection ring) with the map. */
async function registerIcons(map: maplibregl.Map) {
  await Promise.all([
    ...ASSET_TYPES.map(async (t) => {
      const id = `asset-${t}`
      if (!map.hasImage(id)) map.addImage(id, await loadSvgImage(assetIconSvg(t), ICON_PX * ICON_SCALE), { pixelRatio: ICON_SCALE })
    }),
    (async () => {
      if (!map.hasImage('asset-selected')) {
        map.addImage('asset-selected', await loadSvgImage(SELECTION_RING_SVG, 40 * ICON_SCALE), { pixelRatio: ICON_SCALE })
      }
    })(),
  ])
}

/** Box around every asset footprint and the origin, never smaller than about 90 m, so a small plan opens big enough to see. */
function assetBounds(layout: LayoutData): maplibregl.LngLatBoundsLike {
  const { anchor, objects } = layout
  const pts: [number, number][] = [[anchor.longitude, anchor.latitude]]
  for (const o of objects) pts.push(...footprintRing(anchor, o))
  let w = Math.min(...pts.map((p) => p[0]))
  let e = Math.max(...pts.map((p) => p[0]))
  let s = Math.min(...pts.map((p) => p[1]))
  let n = Math.max(...pts.map((p) => p[1]))
  const MIN_M = 90
  const minLat = MIN_M / 111_195
  const minLng = MIN_M / (111_195 * Math.max(Math.cos((anchor.latitude * Math.PI) / 180), 1e-6))
  if (n - s < minLat) {
    const c = (n + s) / 2
    s = c - minLat / 2
    n = c + minLat / 2
  }
  if (e - w < minLng) {
    const c = (e + w) / 2
    w = c - minLng / 2
    e = c + minLng / 2
  }
  return [[w, s], [e, n]]
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
    properties: {
      id: o.id,
      icon: `asset-${o.assetType}`,
      bearing: UPRIGHT_TYPES.has(o.assetType) ? 0 : assetBearing(layout, o.rotationDegrees),
      // Larger assets get a lower key so small ones are drawn on top and stay visible.
      sort: -(o.widthMeters * o.lengthMeters),
    },
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

/** What the plan page can ask the map to do (the "Fit" buttons). */
export interface MapHandle {
  fitAssets: () => void
  fitRegion: () => void
}

export const MapView = forwardRef<MapHandle, Props>(function MapView({ layout, highlightId, onSelect }, ref) {
  const el = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect
  // The live store re-fetches plans every few seconds and hands over a new (but identical) layout object.
  // Key the map on the layout's content so it is only rebuilt when the plan really changes; otherwise the
  // reviewer's zoom and pan would snap back to the start every few seconds.
  const layoutKey = useMemo(() => JSON.stringify(layout), [layout])
  const layoutRef = useRef(layout)
  layoutRef.current = layout

  // Which style to draw on: the basemap (once its style.json is confirmed reachable) or the offline background.
  // null = still checking. If the basemap cannot be reached the plan is still shown, with a small notice.
  const [style, setStyle] = useState<maplibregl.StyleSpecification | string | null>(STYLE_URL ? null : OFFLINE_STYLE)
  const [basemapIssue, setBasemapIssue] = useState(false)
  useEffect(() => {
    if (!STYLE_URL) return
    let cancelled = false
    const ctl = new AbortController()
    const timer = window.setTimeout(() => ctl.abort(), 8000)
    fetch(STYLE_URL, { signal: ctl.signal })
      .then(async (r) => {
        if (!r.ok) throw new Error(`style.json answered ${r.status}`)
        const j = (await r.json()) as { version?: number; layers?: unknown }
        if (j.version !== 8 || !Array.isArray(j.layers)) throw new Error('not a map style')
        if (!cancelled) setStyle(STYLE_URL)
      })
      .catch(() => {
        if (cancelled) return
        setBasemapIssue(true)
        setStyle(OFFLINE_STYLE)
      })
      .finally(() => window.clearTimeout(timer))
    return () => {
      cancelled = true
      ctl.abort()
      window.clearTimeout(timer)
    }
  }, [])

  useImperativeHandle(ref, () => {
    const fit = (box: maplibregl.LngLatBoundsLike) => mapRef.current?.fitBounds(box, { padding: FIT_PADDING, duration: 500 })
    const regionBox = (): maplibregl.LngLatBoundsLike => {
      const b = layoutRef.current.regionBounds
      return [[b.westLongitude, b.southLatitude], [b.eastLongitude, b.northLatitude]]
    }
    return {
      fitRegion: () => fit(regionBox()),
      fitAssets: () => fit(layoutRef.current.objects.length > 0 ? assetBounds(layoutRef.current) : regionBox()),
    }
  }, [])

  useEffect(() => {
    if (!el.current || !style) return
    const usingBasemap = typeof style === 'string'
    const layout = layoutRef.current
    const b = layout.regionBounds
    const map = new maplibregl.Map({
      container: el.current,
      style,
      // Open on the assets (so footprints and icons are visible); with no assets, open on the whole region.
      bounds:
        layout.objects.length > 0
          ? assetBounds(layout)
          : [
              [b.westLongitude, b.southLatitude],
              [b.eastLongitude, b.northLatitude],
            ],
      fitBoundsOptions: { padding: FIT_PADDING },
      attributionControl: false,
      maxPitch: 0,
      dragRotate: false,
      // Lets the browser print the map (a reviewer may print a plan); costs a little speed.
      preserveDrawingBuffer: true,
    })
    mapRef.current = map
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')
    map.addControl(
      new maplibregl.AttributionControl({
        compact: true,
        // With the basemap, its own tile description already carries "© OpenStreetMap contributors" and the
        // Survey of India / DataMeet credit, and MapLibre shows it; repeating it here would print it twice.
        customAttribution: usingBasemap ? undefined : 'Offline view · region, grid and assets only',
      }),
      'bottom-right',
    )

    const popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 10 })
    let removed = false

    // Printing: a WebGL map does not redraw itself to the page's print size, so the printed map would be cut off.
    // Instead, just before printing, swap in a picture of exactly what is on screen (plus the map credit as text);
    // the print stylesheet hides the live map and shows the picture. Removed again afterwards.
    let printEls: HTMLElement[] = []
    const clearPrint = () => {
      printEls.forEach((n) => n.remove())
      printEls = []
    }
    const beforePrint = () => {
      clearPrint()
      try {
        map.triggerRepaint()
        const img = document.createElement('img')
        img.className = 'map-print-img'
        img.alt = ''
        img.src = map.getCanvas().toDataURL('image/png')
        const credit = document.createElement('p')
        credit.className = 'map-print-credit'
        credit.textContent = map.getContainer().querySelector('.maplibregl-ctrl-attrib-inner')?.textContent ?? ''
        const host = map.getContainer()
        host.after(img, credit)
        printEls = [img, credit]
      } catch {
        clearPrint() // keep the live map visible in print if a picture cannot be taken
      }
    }
    window.addEventListener('beforeprint', beforePrint)
    window.addEventListener('afterprint', clearPrint)

    // Basemap trouble after the style loaded (server went away, tiles failing): keep the plan, tell the user.
    // A plain 404 for one tile is normal at the edge of the data and is ignored.
    map.on('error', (e) => {
      const sourceId = (e as unknown as { sourceId?: string }).sourceId
      if (!usingBasemap || (sourceId && OVERLAY_SOURCES.has(sourceId))) return
      const status = (e.error as { status?: number } | undefined)?.status
      if (status === undefined || status === 0 || status >= 500) setBasemapIssue(true)
    })

    map.on('load', async () => {
      try {
        await registerIcons(map)
      } catch {
        // Without icons the to-scale footprints still show the plan, so carry on.
      }
      if (removed) return
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
      map.addLayer({ id: 'highlight', type: 'line', source: 'footprints', filter: ['==', ['get', 'id'], ''], paint: { 'line-color': '#B56B37', 'line-width': 3.2 } })
      // The origin sits under the asset icons so it never hides an asset placed on top of it.
      map.addLayer({ id: 'origin-ring', type: 'circle', source: 'origin', paint: { 'circle-color': '#FCF9F2', 'circle-radius': 8, 'circle-stroke-color': '#B56B37', 'circle-stroke-width': 3 } })
      map.addLayer({ id: 'origin-dot', type: 'circle', source: 'origin', paint: { 'circle-color': '#B56B37', 'circle-radius': 3 } })
      // One symbol per asset: a small top-down icon at the asset's centre, turned to the asset's bearing.
      // Its size grows with zoom between a readable minimum and a cap, so icons never vanish or swamp the map.
      map.addLayer({
        id: 'symbols',
        type: 'symbol',
        source: 'points',
        layout: {
          'icon-image': ['get', 'icon'],
          'icon-rotate': ['get', 'bearing'],
          'icon-rotation-alignment': 'map',
          'icon-pitch-alignment': 'map',
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
          'symbol-sort-key': ['get', 'sort'],
          'icon-size': ['interpolate', ['linear'], ['zoom'], 12, 0.62, 16, 0.72, 19, 0.9, 22, 1],
        },
      })
      map.addLayer({
        id: 'highlight-ring',
        type: 'symbol',
        source: 'points',
        filter: ['==', ['get', 'id'], ''],
        layout: {
          'icon-image': 'asset-selected',
          'icon-rotate': ['get', 'bearing'],
          'icon-rotation-alignment': 'map',
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
          'icon-size': ['interpolate', ['linear'], ['zoom'], 12, 0.76, 16, 0.88, 19, 1.1, 22, 1.25],
        },
      })
      // The selection may have been set while icons were loading.
      applyHighlight(map, highlightRef.current)

      const hit = (point: maplibregl.PointLike) => map.queryRenderedFeatures(point, { layers: ['symbols', 'footprints'] })[0]
      map.on('click', (e) => {
        const f = hit(e.point)
        onSelectRef.current(f ? String(f.properties?.id) : null)
      })
      map.on('mousemove', (e) => {
        const f = hit(e.point)
        map.getCanvas().style.cursor = f ? 'pointer' : ''
        const o = f ? layout.objects.find((x) => x.id === f.properties?.id) : undefined
        if (o) {
          // Built from DOM nodes (not an HTML string) so a plan's asset name can never inject markup.
          const box = document.createElement('div')
          const head = document.createElement('div')
          head.className = 'popup-head'
          const img = document.createElement('img')
          img.src = assetIconUrl(o.assetType)
          img.width = 20
          img.height = 20
          img.alt = ''
          const name = document.createElement('strong')
          name.textContent = o.name
          head.append(img, name)
          const meta = document.createElement('div')
          meta.textContent = `${ASSET_DEFAULTS[o.assetType].label} · ${dim(o.widthMeters)}×${dim(o.lengthMeters)} m`
          box.append(head, meta)
          popup.setLngLat(e.lngLat).setDOMContent(box).addTo(map)
        } else popup.remove()
      })
      map.on('mouseout', () => popup.remove())
    })
    return () => {
      removed = true
      window.removeEventListener('beforeprint', beforePrint)
      window.removeEventListener('afterprint', clearPrint)
      clearPrint()
      popup.remove()
      map.remove()
      mapRef.current = null
    }
  }, [layoutKey, style])

  const highlightRef = useRef<string | null>(highlightId)
  highlightRef.current = highlightId

  useEffect(() => {
    const map = mapRef.current
    if (map) applyHighlight(map, highlightId)
  }, [highlightId, layoutKey])

  return (
    <>
      <div ref={el} className="map" role="img" aria-label={`Map of ${layout.name}: region, grid and ${layout.objects.length} assets`} />
      {basemapIssue && <p className="map-notice" role="status">Basemap unavailable: showing the plan only</p>}
    </>
  )
})

function applyHighlight(map: maplibregl.Map, id: string | null) {
  const f: maplibregl.FilterSpecification = ['==', ['get', 'id'], id ?? '']
  if (map.getLayer('highlight')) map.setFilter('highlight', f)
  if (map.getLayer('highlight-ring')) map.setFilter('highlight-ring', f)
}
