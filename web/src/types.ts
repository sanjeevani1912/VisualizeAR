// Mirrors the Android layout schema v2 (docs/layout-schema-v2.md). Positions are meters from the
// anchor; +Z is "forward" at the anchor heading (north when heading = 0).

/** The app's current library first, then the retired types that older plans and the demo still use. */
export type AssetType =
  | 'GUNSHIP'
  | 'K131'
  | 'K131_TAKEOFF'
  | 'MILITARY_VEHICLE'
  | 'OFFROAD_VEHICLE'
  | 'STR_MGS'
  | 'TENT'
  | 'TRUCK'
  | 'CAR'
  | 'ANTENNA'
  | 'TRENCH'

export interface LayoutObject {
  id: string
  assetType: AssetType
  name: string
  x: number
  z: number
  rotationDegrees: number
  widthMeters: number
  lengthMeters: number
  heightMeters: number
}

export interface GeoBounds {
  northLatitude: number
  southLatitude: number
  eastLongitude: number
  westLongitude: number
}

export interface LayoutAnchor {
  latitude: number
  longitude: number
  headingDegrees: number
  horizontalAccuracyMeters: number | null
}

export interface LayoutData {
  version: 2
  id: string
  name: string
  regionBounds: GeoBounds
  anchor: LayoutAnchor
  objects: LayoutObject[]
}

export type Role = 'planner' | 'reviewer' | 'admin' | 'auditor'

export interface User {
  id: string
  name: string
  rank: string
  role: Role
  username?: string
  active?: boolean
}

export type PlanStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'WITHDRAWN'

export interface PlanVersion {
  version: number
  submittedAt: number
  submittedBy: string
  note: string
  contentHash: string
  layout: LayoutData
}

export type DecisionKind = 'approve' | 'reject' | 'changes'

export interface Decision {
  id: string
  planId: string
  version: number
  reviewerId: string
  decision: DecisionKind
  reason: string
  decidedAt: number
}

export interface Comment {
  id: string
  planId: string
  version: number
  authorId: string
  text: string
  createdAt: number
}

export interface Attachment {
  id: string
  planId: string
  version: number
  fileName: string
  mime: string
  size: number
  caption: string
  uploadedBy: string
  uploadedAt: number
  url: string
}

export interface Plan {
  id: string
  code: string
  name: string
  ownerId: string
  status: PlanStatus
  createdAt: number
  updatedAt: number
  versions: PlanVersion[]
  decisions: Decision[]
  comments: Comment[]
  attachments: Attachment[]
}

export interface AuditEvent {
  id: string
  at: number
  actorId: string
  action: string
  planId?: string
  detail: string
}
