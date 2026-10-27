import crypto from 'node:crypto'
import { z } from 'zod'

// Accepts the JSON the Android app writes (layout schema v2, docs/layout-schema-v2.md) and
// normalises it into the shape the dashboard stores. Anything malformed is rejected, not repaired.

export const ASSET_DEFAULTS = {
  TENT: { w: 4, l: 6, h: 2.5 },
  TRUCK: { w: 2.5, l: 8, h: 3.2 },
  CAR: { w: 1.8, l: 4.5, h: 1.5 },
  ANTENNA: { w: 1.5, l: 1.5, h: 8 },
  TRENCH: { w: 1.2, l: 10, h: 1.8 },
} as const
type AssetType = keyof typeof ASSET_DEFAULTS

const lat = z.number().min(-90).max(90)
const lng = z.number().min(-180).max(180)
const meters = z.number().min(-100_000).max(100_000)
const size = z.number().positive().max(1000)

const objectSchema = z.object({
  id: z.string().max(80).optional(),
  assetType: z.enum(['TENT', 'TRUCK', 'CAR', 'ANTENNA', 'TRENCH']),
  name: z.string().max(80).optional(),
  x: meters,
  z: meters,
  rotationDegrees: z.number().min(-3600).max(3600).default(0),
  widthMeters: size.optional(),
  lengthMeters: size.optional(),
  heightMeters: size.optional(),
})

export const layoutSchema = z.object({
  version: z.number().int().optional(),
  id: z.string().max(80).optional(),
  name: z.string().min(1).max(120).optional(),
  regionBounds: z.object({
    northLatitude: lat,
    southLatitude: lat,
    eastLongitude: lng,
    westLongitude: lng,
  }),
  anchor: z.object({
    latitude: lat,
    longitude: lng,
    headingDegrees: z.number().min(-3600).max(3600).nullish(),
    horizontalAccuracyMeters: z.number().min(0).max(100_000).nullish(),
  }),
  objects: z.array(objectSchema).max(500),
})

export interface NormalizedLayout {
  version: 2
  id: string
  name: string
  regionBounds: { northLatitude: number; southLatitude: number; eastLongitude: number; westLongitude: number }
  anchor: { latitude: number; longitude: number; headingDegrees: number; horizontalAccuracyMeters: number | null }
  objects: {
    id: string
    assetType: AssetType
    name: string
    x: number
    z: number
    rotationDegrees: number
    widthMeters: number
    lengthMeters: number
    heightMeters: number
  }[]
}

export function parseLayout(input: unknown): { layout?: NormalizedLayout; error?: string } {
  const parsed = layoutSchema.safeParse(input)
  if (!parsed.success) {
    const issue = parsed.error.issues[0]
    const where = issue.path.length ? issue.path.join('.') : 'layout'
    return { error: `Invalid layout file: ${where} — ${issue.message}` }
  }
  const l = parsed.data
  const b = l.regionBounds
  if (b.northLatitude < b.southLatitude) return { error: 'Invalid layout file: region north edge is below the south edge' }
  if (b.westLongitude > b.eastLongitude) return { error: 'Invalid layout file: region crosses the antimeridian or west is east of east' }
  if (b.northLatitude === b.southLatitude || b.westLongitude === b.eastLongitude) return { error: 'Invalid layout file: region has no area' }
  const a = l.anchor
  if (a.latitude < b.southLatitude || a.latitude > b.northLatitude || a.longitude < b.westLongitude || a.longitude > b.eastLongitude) {
    return { error: 'Invalid layout file: the origin lies outside the region' }
  }
  const seen = new Set<string>()
  const objects = l.objects.map((o, i) => {
    const d = ASSET_DEFAULTS[o.assetType]
    let id = o.id && !seen.has(o.id) ? o.id : crypto.randomUUID()
    seen.add(id)
    id = id.slice(0, 80)
    return {
      id,
      assetType: o.assetType,
      name: (o.name?.trim() || `${o.assetType[0]}${o.assetType.slice(1).toLowerCase()} ${i + 1}`).slice(0, 80),
      x: o.x,
      z: o.z,
      rotationDegrees: ((o.rotationDegrees % 360) + 360) % 360,
      widthMeters: o.widthMeters ?? d.w,
      lengthMeters: o.lengthMeters ?? d.l,
      heightMeters: o.heightMeters ?? d.h,
    }
  })
  return {
    layout: {
      version: 2,
      id: l.id ?? crypto.randomUUID(),
      name: l.name ?? 'Untitled plan',
      regionBounds: b,
      anchor: {
        latitude: a.latitude,
        longitude: a.longitude,
        headingDegrees: a.headingDegrees ?? 0,
        horizontalAccuracyMeters: a.horizontalAccuracyMeters ?? null,
      },
      objects,
    },
  }
}

/** Fingerprint of the exact content a reviewer decides on. */
export function contentHash(layout: NormalizedLayout): string {
  const s = JSON.stringify(layout)
  return crypto.createHash('sha256').update(s).digest('hex').slice(0, 16)
}
