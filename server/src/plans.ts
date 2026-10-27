import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { Router, type Request } from 'express'
import multer from 'multer'
import { z } from 'zod'
import { audit, listAudit } from './audit.js'
import { HttpError, requireAuth, requireRole, type SessionUser } from './auth.js'
import { config } from './config.js'
import { all, get, run, tx } from './db.js'
import { contentHash, parseLayout, type NormalizedLayout } from './layout.js'
import {
  canAttach,
  canComment,
  canCreatePlan,
  canDecide,
  canRemoveAttachment,
  canResubmit,
  canStartReview,
  canViewPlan,
  canWithdraw,
  reasonError,
  STATUS_FOR,
  type PlanStatus,
} from './rules.js'

interface PlanRow {
  id: string
  code: string
  name: string
  owner_id: string
  status: PlanStatus
  created_at: number
  updated_at: number
}

const uid = () => crypto.randomUUID()

function actor(req: Request): SessionUser {
  if (!req.user) throw new HttpError(401, 'Not signed in')
  return req.user
}

/** A plan the user may not see is reported as missing, so ids cannot be probed. */
function visiblePlan(req: Request, id: string): PlanRow {
  const u = actor(req)
  const p = get<PlanRow>('SELECT * FROM plans WHERE id = ?', id)
  if (!p || !canViewPlan(u, { ownerId: p.owner_id, status: p.status })) throw new HttpError(404, 'Plan not found')
  return p
}

const ref = (p: PlanRow) => ({ ownerId: p.owner_id, status: p.status })

function nextCode(): string {
  const year = new Date().getFullYear()
  const last = get<{ code: string }>('SELECT code FROM plans WHERE code LIKE ? ORDER BY code DESC LIMIT 1', `TP-${year}-%`)
  const n = last ? Number(last.code.split('-')[2]) + 1 : 1
  return `TP-${year}-${String(n).padStart(4, '0')}`
}

// ---------- loading plans in the shape the dashboard uses ----------
export function loadPlans(user: SessionUser, onlyId?: string) {
  const rows = all<PlanRow>(
    onlyId ? 'SELECT * FROM plans WHERE id = ?' : 'SELECT * FROM plans ORDER BY updated_at DESC',
    ...(onlyId ? [onlyId] : []),
  ).filter((p) => canViewPlan(user, { ownerId: p.owner_id, status: p.status }))
  const ids = new Set(rows.map((p) => p.id))
  const keep = <T extends { plan_id: string }>(list: T[]) => list.filter((x) => ids.has(x.plan_id))

  const versions = keep(all<{ plan_id: string; version: number; layout_json: string; content_hash: string; note: string; submitted_by: string; submitted_at: number }>(
    'SELECT * FROM plan_versions ORDER BY version ASC',
  ))
  const decisions = keep(all<{ id: string; plan_id: string; version: number; reviewer_id: string; decision: string; reason: string; decided_at: number }>(
    'SELECT * FROM decisions ORDER BY decided_at ASC',
  ))
  const comments = keep(all<{ id: string; plan_id: string; version: number; author_id: string; text: string; created_at: number }>(
    'SELECT * FROM comments ORDER BY created_at ASC',
  ))
  const attachments = keep(all<{ id: string; plan_id: string; version: number; file_name: string; mime: string; size: number; caption: string; uploaded_by: string; uploaded_at: number }>(
    'SELECT * FROM attachments ORDER BY uploaded_at ASC',
  ))

  return rows.map((p) => ({
    id: p.id,
    code: p.code,
    name: p.name,
    ownerId: p.owner_id,
    status: p.status,
    createdAt: p.created_at,
    updatedAt: p.updated_at,
    versions: versions
      .filter((v) => v.plan_id === p.id)
      .map((v) => ({
        version: v.version,
        submittedAt: v.submitted_at,
        submittedBy: v.submitted_by,
        note: v.note,
        contentHash: v.content_hash,
        layout: JSON.parse(v.layout_json) as NormalizedLayout,
      })),
    decisions: decisions
      .filter((d) => d.plan_id === p.id)
      .map((d) => ({ id: d.id, planId: d.plan_id, version: d.version, reviewerId: d.reviewer_id, decision: d.decision, reason: d.reason, decidedAt: d.decided_at })),
    comments: comments
      .filter((c) => c.plan_id === p.id)
      .map((c) => ({ id: c.id, planId: c.plan_id, version: c.version, authorId: c.author_id, text: c.text, createdAt: c.created_at })),
    attachments: attachments
      .filter((a) => a.plan_id === p.id)
      .map((a) => ({
        id: a.id,
        planId: a.plan_id,
        version: a.version,
        fileName: a.file_name,
        mime: a.mime,
        size: a.size,
        caption: a.caption,
        uploadedBy: a.uploaded_by,
        uploadedAt: a.uploaded_at,
        url: `/api/attachments/${a.id}`,
      })),
  }))
}

function latestVersionNo(planId: string): number {
  return get<{ v: number }>('SELECT MAX(version) AS v FROM plan_versions WHERE plan_id = ?', planId)?.v ?? 1
}

function insertVersion(planId: string, version: number, layout: NormalizedLayout, note: string, userId: string, at: number): void {
  run(
    'INSERT INTO plan_versions (plan_id, version, layout_json, content_hash, note, submitted_by, submitted_at) VALUES (?,?,?,?,?,?,?)',
    planId,
    version,
    JSON.stringify(layout),
    contentHash(layout),
    note,
    userId,
    at,
  )
}

const noteSchema = z.string().trim().max(500).default('')
const createBody = z.object({ layout: z.unknown(), note: noteSchema, name: z.string().trim().min(1).max(120).optional() })
const versionBody = z.object({ layout: z.unknown(), note: noteSchema })
const decisionBody = z.object({
  decision: z.enum(['approve', 'reject', 'changes']),
  reason: z.string().trim().max(2000).default(''),
  version: z.number().int().positive(),
})
const commentBody = z.object({ text: z.string().trim().min(1).max(2000) })

function body<T>(schema: z.ZodType<T>, data: unknown): T {
  const r = schema.safeParse(data)
  if (!r.success) throw new HttpError(400, `${r.error.issues[0].path.join('.') || 'request'}: ${r.error.issues[0].message}`)
  return r.data
}

// ---------- uploads ----------
const IMAGE_TYPES: Record<string, { ext: string; magic: (b: Buffer) => boolean }> = {
  'image/jpeg': { ext: '.jpg', magic: (b) => b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff },
  'image/png': { ext: '.png', magic: (b) => b.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) },
  'image/webp': { ext: '.webp', magic: (b) => b.subarray(0, 4).toString('latin1') === 'RIFF' && b.subarray(8, 12).toString('latin1') === 'WEBP' },
}

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: config.maxUploadBytes, files: 1, fields: 4 },
})

export const planRouter = Router()
planRouter.use(requireAuth)

planRouter.get('/plans', (req, res) => {
  res.json(loadPlans(actor(req)))
})

planRouter.get('/audit', (req, res) => {
  const u = actor(req)
  const visible = u.role === 'planner' ? new Set(loadPlans(u).map((p) => p.id)) : null
  res.json(listAudit(visible))
})

planRouter.post('/plans', requireRole('planner'), (req, res) => {
  const u = actor(req)
  if (!canCreatePlan(u)) throw new HttpError(403, 'Only planners can submit plans')
  const b = body(createBody, req.body)
  const parsed = parseLayout(b.layout)
  if (!parsed.layout) throw new HttpError(400, parsed.error ?? 'Invalid layout')
  const now = Date.now()
  const id = uid()
  const name = b.name ?? parsed.layout.name
  const code = tx(() => {
    const c = nextCode()
    run('INSERT INTO plans (id, code, name, owner_id, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)', id, c, name, u.id, 'SUBMITTED', now, now)
    insertVersion(id, 1, parsed.layout!, b.note, u.id, now)
    audit(u.id, 'Submitted', id, `${c} version 1 submitted for approval`, now)
    return c
  })
  res.status(201).json({ id, code })
})

planRouter.post('/plans/:id/versions', requireRole('planner'), (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  if (!canResubmit(u, ref(p))) throw new HttpError(409, 'A new version can be submitted only after changes were requested or the plan was rejected')
  const b = body(versionBody, req.body)
  const parsed = parseLayout(b.layout)
  if (!parsed.layout) throw new HttpError(400, parsed.error ?? 'Invalid layout')
  const now = Date.now()
  tx(() => {
    const v = latestVersionNo(p.id) + 1
    insertVersion(p.id, v, parsed.layout!, b.note, u.id, now)
    run("UPDATE plans SET status = 'SUBMITTED', name = ?, updated_at = ? WHERE id = ?", parsed.layout!.name === 'Untitled plan' ? p.name : parsed.layout!.name, now, p.id)
    audit(u.id, 'Submitted', p.id, `${p.code} version ${v} submitted for approval`, now)
  })
  res.status(201).json({ ok: true })
})

planRouter.post('/plans/:id/withdraw', (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  if (!canWithdraw(u, ref(p))) throw new HttpError(403, 'You cannot withdraw this plan')
  const now = Date.now()
  tx(() => {
    run("UPDATE plans SET status = 'WITHDRAWN', updated_at = ? WHERE id = ?", now, p.id)
    audit(u.id, 'Withdrawn', p.id, `${p.code} withdrawn by planner`, now)
  })
  res.json({ ok: true })
})

planRouter.post('/plans/:id/start-review', (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  if (!canStartReview(u, ref(p))) {
    res.json({ ok: true, changed: false })
    return
  }
  const now = Date.now()
  tx(() => {
    run("UPDATE plans SET status = 'UNDER_REVIEW', updated_at = ? WHERE id = ? AND status = 'SUBMITTED'", now, p.id)
    audit(u.id, 'Review started', p.id, `${p.code} opened for review`, now)
  })
  res.json({ ok: true, changed: true })
})

planRouter.post('/plans/:id/decision', (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  const b = body(decisionBody, req.body)
  if (!canDecide(u, ref(p))) {
    throw new HttpError(403, p.owner_id === u.id ? 'You submitted this plan, so you cannot decide it' : 'You cannot decide this plan')
  }
  const rErr = reasonError(b.decision, b.reason)
  if (rErr) throw new HttpError(400, rErr)
  const now = Date.now()
  tx(() => {
    const latest = latestVersionNo(p.id)
    if (b.version !== latest) throw new HttpError(409, `A newer version (v${latest}) exists. Reload the plan before deciding`)
    const action = b.decision === 'approve' ? 'Approved' : b.decision === 'reject' ? 'Rejected' : 'Changes requested'
    run('INSERT INTO decisions (id, plan_id, version, reviewer_id, decision, reason, decided_at) VALUES (?,?,?,?,?,?,?)', uid(), p.id, latest, u.id, b.decision, b.reason, now)
    run('UPDATE plans SET status = ?, updated_at = ? WHERE id = ?', STATUS_FOR[b.decision], now, p.id)
    audit(u.id, action, p.id, `${p.code} version ${latest}${b.reason ? `: ${b.reason}` : ''}`, now)
  })
  res.json({ ok: true })
})

planRouter.post('/plans/:id/comments', (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  if (!canComment(u, ref(p))) throw new HttpError(403, 'You cannot comment on this plan')
  const b = body(commentBody, req.body)
  const now = Date.now()
  tx(() => {
    run('INSERT INTO comments (id, plan_id, version, author_id, text, created_at) VALUES (?,?,?,?,?,?)', uid(), p.id, latestVersionNo(p.id), u.id, b.text, now)
    audit(u.id, 'Commented', p.id, b.text, now)
  })
  res.status(201).json({ ok: true })
})

// ---------- field images ----------
planRouter.post('/plans/:id/attachments', upload.single('file'), (req, res) => {
  const u = actor(req)
  const p = visiblePlan(req, String(req.params.id))
  if (!canAttach(u, ref(p))) throw new HttpError(403, 'You cannot add images to this plan')
  const file = req.file
  if (!file) throw new HttpError(400, 'No image received')
  const type = IMAGE_TYPES[file.mimetype]
  // The declared type is not trusted: the file's own first bytes must match it.
  if (!type || !type.magic(file.buffer)) throw new HttpError(415, 'Only JPEG, PNG or WebP images are accepted')
  const count = get<{ n: number }>('SELECT COUNT(*) AS n FROM attachments WHERE plan_id = ?', p.id)?.n ?? 0
  if (count >= config.maxImagesPerPlan) throw new HttpError(409, `A plan can hold at most ${config.maxImagesPerPlan} images`)
  const caption = String(req.body?.caption ?? '').trim().slice(0, 200)
  const id = uid()
  const stored = `${id}${type.ext}`
  const displayName = path.basename(file.originalname).replace(/[^\w.\- ()]/g, '_').slice(0, 100) || `image${type.ext}`
  const now = Date.now()
  fs.writeFileSync(path.join(config.dataDir, 'uploads', stored), file.buffer, { flag: 'wx' })
  try {
    tx(() => {
      run(
        'INSERT INTO attachments (id, plan_id, version, file_name, stored_name, mime, size, caption, uploaded_by, uploaded_at) VALUES (?,?,?,?,?,?,?,?,?,?)',
        id, p.id, latestVersionNo(p.id), displayName, stored, file.mimetype, file.size, caption, u.id, now,
      )
      audit(u.id, 'Image added', p.id, `${displayName}${caption ? ` — ${caption}` : ''}`, now)
    })
  } catch (e) {
    fs.rmSync(path.join(config.dataDir, 'uploads', stored), { force: true })
    throw e
  }
  res.status(201).json({ id })
})

planRouter.get('/attachments/:id', (req, res) => {
  const a = get<{ plan_id: string; stored_name: string; mime: string }>('SELECT plan_id, stored_name, mime FROM attachments WHERE id = ?', String(req.params.id))
  if (!a) throw new HttpError(404, 'Image not found')
  visiblePlan(req, a.plan_id)
  const file = path.join(config.dataDir, 'uploads', path.basename(a.stored_name))
  if (!fs.existsSync(file)) throw new HttpError(404, 'Image not found')
  res.setHeader('Content-Type', a.mime)
  res.setHeader('X-Content-Type-Options', 'nosniff')
  res.setHeader('Content-Disposition', 'inline')
  res.setHeader('Cache-Control', 'private, max-age=300')
  res.sendFile(file)
})

planRouter.delete('/attachments/:id', (req, res) => {
  const u = actor(req)
  const a = get<{ plan_id: string; stored_name: string; uploaded_by: string; file_name: string }>(
    'SELECT plan_id, stored_name, uploaded_by, file_name FROM attachments WHERE id = ?',
    String(req.params.id),
  )
  if (!a) throw new HttpError(404, 'Image not found')
  const p = visiblePlan(req, a.plan_id)
  if (!canRemoveAttachment(u, a.uploaded_by)) throw new HttpError(403, 'Only the uploader or an administrator can remove an image')
  const now = Date.now()
  tx(() => {
    run('DELETE FROM attachments WHERE id = ?', String(req.params.id))
    audit(u.id, 'Image removed', p.id, a.file_name, now)
  })
  fs.rmSync(path.join(config.dataDir, 'uploads', path.basename(a.stored_name)), { force: true })
  res.json({ ok: true })
})
