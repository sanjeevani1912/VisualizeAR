import crypto from 'node:crypto'
import { all, get, run } from './db.js'

interface AuditRow {
  seq: number
  at: number
  actor_id: string | null
  action: string
  plan_id: string | null
  detail: string
  prev_hash: string
  hash: string
}

const GENESIS = '0'.repeat(64)

function entryHash(prev: string, at: number, actor: string | null, action: string, planId: string | null, detail: string): string {
  return crypto.createHash('sha256').update(JSON.stringify([prev, at, actor, action, planId, detail])).digest('hex')
}

/**
 * Appends one entry to the audit log. Each entry hashes the previous one, so editing or deleting a
 * past row breaks the chain and is caught by verifyAuditChain(). Call inside the same transaction
 * as the change being recorded.
 */
export function audit(actorId: string | null, action: string, planId: string | null, detail: string, at = Date.now()): void {
  const last = get<{ hash: string }>('SELECT hash FROM audit ORDER BY seq DESC LIMIT 1')
  const prev = last?.hash ?? GENESIS
  run(
    'INSERT INTO audit (at, actor_id, action, plan_id, detail, prev_hash, hash) VALUES (?,?,?,?,?,?,?)',
    at,
    actorId,
    action,
    planId,
    detail,
    prev,
    entryHash(prev, at, actorId, action, planId, detail),
  )
}

export function verifyAuditChain(): { ok: boolean; entries: number; brokenAtSeq?: number } {
  const rows = all<AuditRow>('SELECT * FROM audit ORDER BY seq ASC')
  let prev = GENESIS
  for (const r of rows) {
    if (r.prev_hash !== prev || r.hash !== entryHash(prev, r.at, r.actor_id, r.action, r.plan_id, r.detail)) {
      return { ok: false, entries: rows.length, brokenAtSeq: r.seq }
    }
    prev = r.hash
  }
  return { ok: true, entries: rows.length }
}

export function listAudit(visiblePlanIds: Set<string> | null) {
  const rows = all<AuditRow>('SELECT * FROM audit ORDER BY at DESC, seq DESC')
  return rows
    .filter((r) => visiblePlanIds === null || (r.plan_id !== null && visiblePlanIds.has(r.plan_id)))
    .map((r) => ({
      id: String(r.seq),
      at: r.at,
      actorId: r.actor_id ?? '',
      action: r.action,
      planId: r.plan_id ?? undefined,
      detail: r.detail,
    }))
}
