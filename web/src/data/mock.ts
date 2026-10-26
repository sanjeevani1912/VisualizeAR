import { ASSET_DEFAULTS, boundsFromCenter } from '../geo'
import type {
  AssetType,
  AuditEvent,
  Comment,
  Decision,
  LayoutData,
  LayoutObject,
  Plan,
  PlanStatus,
  User,
} from '../types'

// Entirely fictional demo data. No real units, people or sites.

export const USERS: User[] = [
  { id: 'u-rev1', name: 'A. Menon', rank: 'Brigadier', role: 'reviewer' },
  { id: 'u-rev2', name: 'R. Sharma', rank: 'Colonel', role: 'reviewer' },
  { id: 'u-adm', name: 'S. Iyer', rank: 'Lt Col', role: 'admin' },
  { id: 'u-aud', name: 'P. Nair', rank: 'Major', role: 'auditor' },
  { id: 'u-p1', name: 'V. Singh', rank: 'Major', role: 'planner' },
  { id: 'u-p2', name: 'K. Rao', rank: 'Captain', role: 'planner' },
  { id: 'u-p3', name: 'D. Bose', rank: 'Captain', role: 'planner' },
  { id: 'u-p4', name: 'M. Khan', rank: 'Lieutenant', role: 'planner' },
]

export const DEMO_LOGINS = USERS.filter((u) => u.role !== 'planner')

export function userById(id: string): User | undefined {
  return USERS.find((u) => u.id === id)
}

export function displayName(u: User | undefined): string {
  return u ? `${u.rank} ${u.name}` : 'Unknown'
}

function rng(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function contentHash(layout: LayoutData): string {
  const s = JSON.stringify(layout)
  let h = 0x811c9dc5
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0).toString(16).padStart(8, '0') + s.length.toString(16).padStart(4, '0')
}

interface SiteSpec {
  id: string
  name: string
  lat: number
  lng: number
  widthM: number
  heightM: number
  heading: number
  counts: Partial<Record<AssetType, number>>
  seed: number
  accuracy: number | null
}

function makeLayout(s: SiteSpec, extra = 0): LayoutData {
  const rand = rng(s.seed + extra * 7919)
  const objects: LayoutObject[] = []
  const types = Object.keys(s.counts) as AssetType[]
  for (const t of types) {
    const n = (s.counts[t] ?? 0) + (extra > 0 && t === 'TENT' ? extra : 0)
    for (let i = 0; i < n; i++) {
      const d = ASSET_DEFAULTS[t]
      const x = (rand() - 0.5) * s.widthM * 0.78
      const z = (rand() - 0.5) * s.heightM * 0.78
      objects.push({
        id: `${s.id}-${t.toLowerCase()}-${i + 1}`,
        assetType: t,
        name: `${d.label} ${i + 1}`,
        x: Math.round(x * 10) / 10,
        z: Math.round(z * 10) / 10,
        rotationDegrees: Math.round(rand() * 36) * 10,
        widthMeters: d.w,
        lengthMeters: d.l,
        heightMeters: d.h,
      })
    }
  }
  return {
    version: 2,
    id: s.id,
    name: s.name,
    regionBounds: boundsFromCenter(s.lat, s.lng, s.widthM, s.heightM),
    anchor: {
      latitude: s.lat,
      longitude: s.lng,
      headingDegrees: s.heading,
      horizontalAccuracyMeters: s.accuracy,
    },
    objects,
  }
}

const H = 3_600_000
const D = 24 * H

interface PlanSpec {
  n: number
  site: SiteSpec
  owner: string
  status: PlanStatus
  submittedHoursAgo: number
  versions: number
  note: string
  decision?: { by: string; hoursAgo: number; reason: string }
  comments?: { by: string; hoursAgo: number; text: string }[]
}

const SPECS: PlanSpec[] = [
  {
    n: 142, owner: 'u-p1', status: 'SUBMITTED', submittedHoursAgo: 5, versions: 1,
    note: 'Initial staging layout for the exercise area.',
    site: { id: 'L142', name: 'Staging Area Alpha', lat: 28.6139, lng: 77.209, widthM: 420, heightM: 300, heading: 0, counts: { TENT: 6, TRUCK: 4, CAR: 3, ANTENNA: 1 }, seed: 142, accuracy: 4.2 },
  },
  {
    n: 141, owner: 'u-p2', status: 'SUBMITTED', submittedHoursAgo: 30, versions: 1,
    note: 'Communication node and support vehicles.',
    site: { id: 'L141', name: 'Comms Node Bravo', lat: 26.9124, lng: 75.7873, widthM: 260, heightM: 220, heading: 12, counts: { TENT: 2, ANTENNA: 3, TRUCK: 2, CAR: 2 }, seed: 141, accuracy: 6.8 },
  },
  {
    n: 140, owner: 'u-p3', status: 'UNDER_REVIEW', submittedHoursAgo: 52, versions: 2,
    note: 'Revised after earlier comments: trench line extended.',
    site: { id: 'L140', name: 'Defensive Works Charlie', lat: 30.3165, lng: 78.0322, widthM: 520, heightM: 380, heading: 340, counts: { TRENCH: 6, TENT: 3, CAR: 2, ANTENNA: 1 }, seed: 140, accuracy: 3.1 },
    comments: [{ by: 'u-rev2', hoursAgo: 20, text: 'Please confirm the trench spacing against the latest site survey.' }],
  },
  {
    n: 139, owner: 'u-p1', status: 'SUBMITTED', submittedHoursAgo: 80, versions: 1,
    note: 'Logistics dump layout, vehicle-heavy.',
    site: { id: 'L139', name: 'Logistics Point Delta', lat: 18.5204, lng: 73.8567, widthM: 600, heightM: 450, heading: 0, counts: { TRUCK: 9, TENT: 4, CAR: 4 }, seed: 139, accuracy: 8.5 },
  },
  {
    n: 138, owner: 'u-p4', status: 'APPROVED', submittedHoursAgo: 120, versions: 1,
    note: 'Medical aid post layout.',
    site: { id: 'L138', name: 'Aid Post Echo', lat: 26.1445, lng: 91.7362, widthM: 180, heightM: 150, heading: 20, counts: { TENT: 5, CAR: 3, TRUCK: 1 }, seed: 138, accuracy: 2.9 },
    decision: { by: 'u-rev1', hoursAgo: 96, reason: 'Layout is clear and within the approved footprint.' },
  },
  {
    n: 137, owner: 'u-p2', status: 'REJECTED', submittedHoursAgo: 170, versions: 1,
    note: 'Forward observation cluster.',
    site: { id: 'L137', name: 'Observation Cluster Foxtrot', lat: 32.7266, lng: 74.857, widthM: 340, heightM: 340, heading: 0, counts: { ANTENNA: 4, TENT: 2, TRENCH: 3 }, seed: 137, accuracy: 12.4 },
    decision: { by: 'u-rev2', hoursAgo: 140, reason: 'Antenna spacing too tight and origin accuracy is poor. Re-stamp the origin and revise the mast positions.' },
  },
  {
    n: 136, owner: 'u-p3', status: 'CHANGES_REQUESTED', submittedHoursAgo: 100, versions: 1,
    note: 'Vehicle park layout.',
    site: { id: 'L136', name: 'Vehicle Park Golf', lat: 23.2599, lng: 77.4126, widthM: 300, heightM: 260, heading: 5, counts: { TRUCK: 7, CAR: 5, TENT: 1 }, seed: 136, accuracy: 5.5 },
    decision: { by: 'u-rev1', hoursAgo: 70, reason: 'Provide a separate access lane for the trucks and mark it in the plan.' },
  },
  {
    n: 135, owner: 'u-p4', status: 'APPROVED', submittedHoursAgo: 260, versions: 2,
    note: 'Training ground layout, second revision.',
    site: { id: 'L135', name: 'Training Ground Hotel', lat: 15.2993, lng: 74.124, widthM: 700, heightM: 520, heading: 0, counts: { TENT: 8, TRENCH: 4, CAR: 3, TRUCK: 3, ANTENNA: 2 }, seed: 135, accuracy: 3.6 },
    decision: { by: 'u-rev2', hoursAgo: 200, reason: 'Approved. Version 2 resolves the earlier spacing concerns.' },
  },
  {
    n: 134, owner: 'u-p1', status: 'WITHDRAWN', submittedHoursAgo: 300, versions: 1,
    note: 'Superseded by TP-2026-0142.',
    site: { id: 'L134', name: 'Staging Area (old)', lat: 28.7041, lng: 77.1025, widthM: 380, heightM: 280, heading: 0, counts: { TENT: 4, TRUCK: 2 }, seed: 134, accuracy: 7.0 },
  },
  {
    n: 133, owner: 'u-p2', status: 'APPROVED', submittedHoursAgo: 340, versions: 1,
    note: 'Relay site, minimal footprint.',
    site: { id: 'L133', name: 'Relay Site India', lat: 27.3314, lng: 88.6138, widthM: 120, heightM: 120, heading: 0, counts: { ANTENNA: 2, TENT: 1, CAR: 1 }, seed: 133, accuracy: 4.0 },
    decision: { by: 'u-rev1', hoursAgo: 310, reason: 'Approved as submitted.' },
  },
  {
    n: 132, owner: 'u-p3', status: 'SUBMITTED', submittedHoursAgo: 8, versions: 1,
    note: 'Compact command post.',
    site: { id: 'L132', name: 'Command Post Juliet', lat: 19.076, lng: 72.8777, widthM: 150, heightM: 130, heading: 0, counts: { TENT: 3, CAR: 2, ANTENNA: 1 }, seed: 132, accuracy: 9.9 },
  },
  {
    n: 131, owner: 'u-p4', status: 'UNDER_REVIEW', submittedHoursAgo: 40, versions: 1,
    note: 'Assembly area, new site.',
    site: { id: 'L131', name: 'Assembly Area Kilo', lat: 22.5726, lng: 88.3639, widthM: 480, heightM: 360, heading: 8, counts: { TENT: 7, TRUCK: 5, CAR: 2 }, seed: 131, accuracy: 6.1 },
  },
]

export function buildInitialData(now: number): { plans: Plan[]; audit: AuditEvent[] } {
  const plans: Plan[] = []
  const audit: AuditEvent[] = []
  let aid = 0
  const ev = (at: number, actorId: string, action: string, planId: string | undefined, detail: string) =>
    audit.push({ id: `a${++aid}`, at, actorId, action, planId, detail })

  for (const s of SPECS) {
    const code = `TP-2026-${String(s.n).padStart(4, '0')}`
    const planId = `plan-${s.n}`
    const lastSubmit = now - s.submittedHoursAgo * H
    const versions = []
    for (let v = 1; v <= s.versions; v++) {
      const layout = makeLayout(s.site, v - 1)
      const at = s.versions === 1 ? lastSubmit : lastSubmit - (s.versions - v) * 2 * D
      versions.push({
        version: v,
        submittedAt: at,
        submittedBy: s.owner,
        note: v === s.versions ? s.note : 'Earlier revision.',
        contentHash: contentHash(layout),
        layout,
      })
      ev(at, s.owner, 'Submitted', planId, `${code} version ${v} submitted for approval`)
    }
    const decisions: Decision[] = []
    if (s.decision) {
      const kind = s.status === 'APPROVED' ? 'approve' : s.status === 'REJECTED' ? 'reject' : 'changes'
      decisions.push({
        id: `d-${s.n}`,
        planId,
        version: s.versions,
        reviewerId: s.decision.by,
        decision: kind,
        reason: s.decision.reason,
        decidedAt: now - s.decision.hoursAgo * H,
      })
      ev(now - s.decision.hoursAgo * H, s.decision.by, kind === 'approve' ? 'Approved' : kind === 'reject' ? 'Rejected' : 'Changes requested', planId, `${code} version ${s.versions}: ${s.decision.reason}`)
    }
    if (s.status === 'WITHDRAWN') ev(lastSubmit + 6 * H, s.owner, 'Withdrawn', planId, `${code} withdrawn by planner`)
    const comments: Comment[] = (s.comments ?? []).map((c, i) => ({
      id: `c-${s.n}-${i}`,
      planId,
      version: s.versions,
      authorId: c.by,
      text: c.text,
      createdAt: now - c.hoursAgo * H,
    }))
    for (const c of comments) ev(c.createdAt, c.authorId, 'Commented', planId, c.text)
    plans.push({
      id: planId,
      code,
      name: s.site.name,
      ownerId: s.owner,
      status: s.status,
      createdAt: versions[0].submittedAt,
      updatedAt: Math.max(lastSubmit, decisions[0]?.decidedAt ?? 0),
      versions,
      decisions,
      comments,
    })
  }
  audit.sort((a, b) => b.at - a.at)
  return { plans, audit }
}
