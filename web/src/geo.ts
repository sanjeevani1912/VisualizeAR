import type { AssetType, GeoBounds, LayoutAnchor, LayoutData, LayoutObject } from './types'

// Same local equirectangular maths as the Android app (GeographicCoordinateConverter.kt).
export const EARTH_RADIUS_M = 6_371_008.7714
const DEG = Math.PI / 180

// The first six mirror the app's asset library (AssetType.kt, sizes from AssetSizeConfig.kt); the
// last five are retired app types kept so older plans and the demo data still draw. Sizes here
// are only a fallback: the app writes each object's real dimensions into the layout file.
export const ASSET_DEFAULTS: Record<AssetType, { w: number; l: number; h: number; label: string; plural: string; color: string }> = {
  GUNSHIP: { w: 13.52, l: 19.7, h: 6.05, label: 'Gunship', plural: 'gunships', color: '#8B2E2E' },
  K131: { w: 3.45, l: 6.24, h: 5.45, label: 'K-131', plural: 'K-131s', color: '#3E5C76' },
  K131_TAKEOFF: { w: 3.31, l: 6.24, h: 3.54, label: 'K-131 Take-Off', plural: 'K-131 take-offs', color: '#6C8FB5' },
  MILITARY_VEHICLE: { w: 1.72, l: 4, h: 3.1, label: 'Military Vehicle', plural: 'military vehicles', color: '#2F6F5E' },
  OFFROAD_VEHICLE: { w: 2.43, l: 4.66, h: 3.54, label: 'Off-road Vehicle', plural: 'off-road vehicles', color: '#C28A2C' },
  STR_MGS: { w: 3.44, l: 7.63, h: 3.47, label: 'STR-MGS', plural: 'STR-MGS', color: '#7A6B2E' },
  TENT: { w: 4, l: 6, h: 2.5, label: 'Tent', plural: 'tents', color: '#63703D' },
  TRUCK: { w: 2.5, l: 8, h: 3.2, label: 'Truck', plural: 'trucks', color: '#B56B37' },
  CAR: { w: 1.8, l: 4.5, h: 1.5, label: 'Car', plural: 'cars', color: '#603620' },
  ANTENNA: { w: 1.5, l: 1.5, h: 8, label: 'Antenna', plural: 'antennas', color: '#231F20' },
  TRENCH: { w: 1.2, l: 10, h: 1.8, label: 'Trench', plural: 'trenches', color: '#8A7A4A' },
}

/** "2 gunships", "1 K-131": the count with the right wording for it. */
export function assetCountText(type: AssetType, count: number): string {
  const d = ASSET_DEFAULTS[type]
  // Names such as K-131 and STR-MGS keep their capitals; ordinary words ("tent") are lower-cased.
  const singular = /[A-Z]{2}|\d/.test(d.label) ? d.label : d.label.toLowerCase()
  return `${count} ${count === 1 ? singular : d.plural}`
}

export function offsetToLatLng(anchor: LayoutAnchor, x: number, z: number): [number, number] {
  const h = anchor.headingDegrees * DEG
  const east = x * Math.cos(h) + z * Math.sin(h)
  const north = -x * Math.sin(h) + z * Math.cos(h)
  const lat = anchor.latitude + (north / EARTH_RADIUS_M) / DEG
  const lng =
    anchor.longitude + (east / (EARTH_RADIUS_M * Math.max(Math.cos(anchor.latitude * DEG), 1e-12))) / DEG
  return [lng, lat] // GeoJSON order
}

export function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = (lat2 - lat1) * DEG
  const dLng = (lng2 - lng1) * DEG
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * DEG) * Math.cos(lat2 * DEG) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(1 - a, 0)))
}

export function regionSizeMeters(b: GeoBounds): { width: number; height: number } {
  const midLat = (b.northLatitude + b.southLatitude) / 2
  const midLng = (b.eastLongitude + b.westLongitude) / 2
  return {
    width: haversineMeters(midLat, b.westLongitude, midLat, b.eastLongitude),
    height: haversineMeters(b.southLatitude, midLng, b.northLatitude, midLng),
  }
}

/** A dimension to the centimetre, without trailing zeros: 27.037078857421875 becomes "27.04". */
export function dim(n: number): string {
  return String(Math.round(n * 100) / 100)
}

export function formatMeters(m: number): string {
  return m >= 1000 ? `${(m / 1000).toFixed(1)} km` : `${Math.round(m)} m`
}

export function formatRegion(b: GeoBounds): string {
  const { width, height } = regionSizeMeters(b)
  return `${formatMeters(width)} × ${formatMeters(height)}`
}

export function boundsFromCenter(lat: number, lng: number, widthM: number, heightM: number): GeoBounds {
  const dLat = (heightM / 2 / EARTH_RADIUS_M) / DEG
  const dLng = (widthM / 2 / (EARTH_RADIUS_M * Math.cos(lat * DEG))) / DEG
  return {
    northLatitude: lat + dLat,
    southLatitude: lat - dLat,
    eastLongitude: lng + dLng,
    westLongitude: lng - dLng,
  }
}

export function footprintRing(anchor: LayoutAnchor, o: LayoutObject): [number, number][] {
  const hw = o.widthMeters / 2
  const hl = o.lengthMeters / 2
  const r = o.rotationDegrees * DEG
  const c = Math.cos(r)
  const s = Math.sin(r)
  const corners: [number, number][] = [
    [-hw, -hl],
    [hw, -hl],
    [hw, hl],
    [-hw, hl],
    [-hw, -hl],
  ]
  return corners.map(([lx, lz]) => offsetToLatLng(anchor, o.x + lx * c - lz * s, o.z + lx * s + lz * c))
}

export function distanceFromOrigin(o: LayoutObject): number {
  return Math.hypot(o.x, o.z)
}

function gridSpacing(b: GeoBounds): number {
  const { width, height } = regionSizeMeters(b)
  const shortest = Math.min(width, height)
  if (shortest < 100) return 10
  if (shortest < 500) return 25
  if (shortest < 2000) return 50
  return 100
}

/** Visual metric grid, axis-aligned in lat/lng, clipped to the region. Matches the app's spacing rule. */
export function gridFeatures(b: GeoBounds): GeoJSON.Feature<GeoJSON.LineString>[] {
  const { width, height } = regionSizeMeters(b)
  let step = gridSpacing(b)
  const longest = Math.max(width, height)
  if (longest / step > 40) step = longest / 40
  const midLat = (b.northLatitude + b.southLatitude) / 2
  const dLatStep = (step / EARTH_RADIUS_M) / DEG
  const dLngStep = (step / (EARTH_RADIUS_M * Math.cos(midLat * DEG))) / DEG
  const feats: GeoJSON.Feature<GeoJSON.LineString>[] = []
  let i = 0
  for (let lng = b.westLongitude; lng <= b.eastLongitude + 1e-12; lng += dLngStep, i++) {
    feats.push({
      type: 'Feature',
      properties: { major: i % 5 === 0 },
      geometry: {
        type: 'LineString',
        coordinates: [
          [lng, b.southLatitude],
          [lng, b.northLatitude],
        ],
      },
    })
  }
  i = 0
  for (let lat = b.southLatitude; lat <= b.northLatitude + 1e-12; lat += dLatStep, i++) {
    feats.push({
      type: 'Feature',
      properties: { major: i % 5 === 0 },
      geometry: {
        type: 'LineString',
        coordinates: [
          [b.westLongitude, lat],
          [b.eastLongitude, lat],
        ],
      },
    })
  }
  return feats
}

export function summarizeAssets(layout: LayoutData): { type: AssetType; count: number }[] {
  const m = new Map<AssetType, number>()
  for (const o of layout.objects) m.set(o.assetType, (m.get(o.assetType) ?? 0) + 1)
  return [...m.entries()].map(([type, count]) => ({ type, count }))
}

export function formatLatLng(lat: number, lng: number): string {
  const ns = lat >= 0 ? 'N' : 'S'
  const ew = lng >= 0 ? 'E' : 'W'
  return `${Math.abs(lat).toFixed(5)}° ${ns}, ${Math.abs(lng).toFixed(5)}° ${ew}`
}
