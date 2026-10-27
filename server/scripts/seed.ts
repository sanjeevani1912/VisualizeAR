// Creates the first accounts and a few fictional sample plans so the dashboard is usable straight away.
//   npm run seed             first-time setup (refuses to run if users already exist)
//   npm run seed -- --reset  wipes ALL data (users, plans, images, audit log) and seeds again
//
// Passwords are random and written once to data/seed-credentials.txt (git-ignored). Change them after
// first sign-in. This script is for development and demos, not for loading real plans.
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { audit } from '../src/audit.js'
import { hashPassword } from '../src/auth.js'
import { config } from '../src/config.js'
import { db, get, run, tx } from '../src/db.js'
import { contentHash, type NormalizedLayout } from '../src/layout.js'
import { buildInitialData, USERS } from '../../web/src/data/mock.js'

const reset = process.argv.includes('--reset')
const existing = get<{ n: number }>('SELECT COUNT(*) AS n FROM users')?.n ?? 0
if (existing > 0 && !reset) {
  console.error('Users already exist. Use `npm run seed -- --reset` to wipe everything and start over.')
  process.exit(1)
}
if (reset) {
  for (const t of ['sessions', 'attachments', 'comments', 'decisions', 'plan_versions', 'plans', 'audit', 'users']) db.exec(`DELETE FROM ${t}`)
  db.exec("DELETE FROM sqlite_sequence WHERE name = 'audit'")
  const up = path.join(config.dataDir, 'uploads')
  for (const f of fs.readdirSync(up)) fs.rmSync(path.join(up, f), { force: true })
}

const USERNAMES: Record<string, string> = {
  'u-rev1': 'menon',
  'u-rev2': 'sharma',
  'u-adm': 'iyer',
  'u-aud': 'nair',
  'u-p1': 'singh',
  'u-p2': 'rao',
  'u-p3': 'bose',
  'u-p4': 'khan',
}

const creds: string[] = ['username   role       password', '---------  ---------  ----------------']
const { plans, audit: events } = buildInitialData(Date.now())

tx(() => {
  const now = Date.now()
  for (const u of USERS) {
    const username = USERNAMES[u.id]
    const password = `${crypto.randomBytes(9).toString('base64url')}-Aa1`
    run('INSERT INTO users (id, username, name, rank, role, password_hash, active, created_at) VALUES (?,?,?,?,?,?,1,?)', u.id, username, u.name, u.rank, u.role, hashPassword(password), now)
    creds.push(`${username.padEnd(10)} ${u.role.padEnd(10)} ${password}`)
  }
  for (const p of plans) {
    run('INSERT INTO plans (id, code, name, owner_id, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)', p.id, p.code, p.name, p.ownerId, p.status, p.createdAt, p.updatedAt)
    for (const v of p.versions) {
      run(
        'INSERT INTO plan_versions (plan_id, version, layout_json, content_hash, note, submitted_by, submitted_at) VALUES (?,?,?,?,?,?,?)',
        p.id, v.version, JSON.stringify(v.layout), contentHash(v.layout as unknown as NormalizedLayout), v.note, v.submittedBy, v.submittedAt,
      )
    }
    for (const d of p.decisions) {
      run('INSERT INTO decisions (id, plan_id, version, reviewer_id, decision, reason, decided_at) VALUES (?,?,?,?,?,?,?)', d.id, p.id, d.version, d.reviewerId, d.decision, d.reason, d.decidedAt)
    }
    for (const c of p.comments) {
      run('INSERT INTO comments (id, plan_id, version, author_id, text, created_at) VALUES (?,?,?,?,?,?)', c.id, p.id, c.version, c.authorId, c.text, c.createdAt)
    }
  }
  // Oldest first so the hash chain follows real time.
  for (const e of [...events].sort((a, b) => a.at - b.at)) audit(e.actorId, e.action, e.planId ?? null, e.detail, e.at)
})

fs.writeFileSync(path.join(config.dataDir, 'seed-credentials.txt'), creds.join('\n') + '\n', { mode: 0o600 })
console.log(`Seeded ${USERS.length} users and ${plans.length} sample plans.`)
console.log(`Passwords written to ${path.join(config.dataDir, 'seed-credentials.txt')}`)
