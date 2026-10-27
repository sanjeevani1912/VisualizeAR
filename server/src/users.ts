import crypto from 'node:crypto'
import { Router } from 'express'
import { z } from 'zod'
import { audit } from './audit.js'
import {
  HttpError,
  activeAdminCount,
  authenticate,
  checkLocked,
  clearSessionCookie,
  createSession,
  destroySession,
  destroyUserSessions,
  hashPassword,
  requireAuth,
  requireRole,
  setSessionCookie,
  verifyPassword,
} from './auth.js'
import { COOKIE_NAME } from './config.js'
import { all, get, run, tx } from './db.js'
import { passwordError } from './rules.js'

const roleEnum = z.enum(['planner', 'reviewer', 'admin', 'auditor'])
const username = z.string().trim().min(3).max(40).regex(/^[A-Za-z0-9._-]+$/, 'letters, digits, dot, dash or underscore only')

export const authRouter = Router()

authRouter.post('/auth/login', (req, res) => {
  const parsed = z.object({ username: z.string().max(80), password: z.string().max(200) }).safeParse(req.body)
  if (!parsed.success) throw new HttpError(400, 'Username and password are required')
  const ip = req.ip ?? 'unknown'
  const wait = checkLocked(parsed.data.username, ip)
  if (wait > 0) throw new HttpError(429, `Too many failed attempts. Try again in ${wait} minute${wait === 1 ? '' : 's'}`)
  const user = authenticate(parsed.data.username, parsed.data.password, ip)
  if (!user) throw new HttpError(401, 'Incorrect username or password')
  // A fresh token on every login prevents session fixation.
  const old = req.cookies?.[COOKIE_NAME]
  if (typeof old === 'string' && old) destroySession(old)
  setSessionCookie(res, createSession(user.id))
  audit(user.id, 'Signed in', null, `${user.username} signed in`)
  res.json(user)
})

authRouter.post('/auth/logout', (req, res) => {
  const token = req.cookies?.[COOKIE_NAME]
  if (typeof token === 'string' && token) destroySession(token)
  clearSessionCookie(res)
  res.json({ ok: true })
})

authRouter.get('/auth/me', (req, res) => {
  if (!req.user) throw new HttpError(401, 'Not signed in')
  res.json(req.user)
})

authRouter.post('/auth/change-password', requireAuth, (req, res) => {
  const b = z.object({ current: z.string().max(200), next: z.string().max(200) }).safeParse(req.body)
  if (!b.success) throw new HttpError(400, 'Current and new password are required')
  const u = req.user!
  const row = get<{ password_hash: string }>('SELECT password_hash FROM users WHERE id = ?', u.id)
  if (!row || !verifyPassword(b.data.current, row.password_hash)) throw new HttpError(403, 'Current password is incorrect')
  const err = passwordError(b.data.next, u.username)
  if (err) throw new HttpError(400, err)
  tx(() => {
    run('UPDATE users SET password_hash = ? WHERE id = ?', hashPassword(b.data.next), u.id)
    audit(u.id, 'Password changed', null, `${u.username} changed their password`)
  })
  // Other sessions are ended; this one continues on a new token.
  destroyUserSessions(u.id)
  setSessionCookie(res, createSession(u.id))
  res.json({ ok: true })
})

export const userRouter = Router()
userRouter.use(requireAuth)

/** Names only, so every signed-in user can see who wrote or decided something. */
userRouter.get('/users', (_req, res) => {
  res.json(all('SELECT id, name, rank, role, active FROM users ORDER BY name').map((r) => ({ ...r, active: !!r.active })))
})

export const adminRouter = Router()
adminRouter.use(requireRole('admin'))

interface AdminRow {
  id: string
  username: string
  name: string
  rank: string
  role: string
  active: number
  created_at: number
  last_login_at: number | null
}

adminRouter.get('/users', (_req, res) => {
  const rows = all<AdminRow>('SELECT id, username, name, rank, role, active, created_at, last_login_at FROM users ORDER BY name')
  res.json(rows.map((r) => ({ id: r.id, username: r.username, name: r.name, rank: r.rank, role: r.role, active: !!r.active, createdAt: r.created_at, lastLoginAt: r.last_login_at })))
})

adminRouter.post('/users', (req, res) => {
  const b = z
    .object({ username, name: z.string().trim().min(1).max(80), rank: z.string().trim().min(1).max(40), role: roleEnum, password: z.string().max(200) })
    .safeParse(req.body)
  if (!b.success) throw new HttpError(400, `${b.error.issues[0].path.join('.')}: ${b.error.issues[0].message}`)
  const err = passwordError(b.data.password, b.data.username)
  if (err) throw new HttpError(400, err)
  if (get('SELECT 1 FROM users WHERE username = ?', b.data.username)) throw new HttpError(409, 'That username is already taken')
  const id = crypto.randomUUID()
  tx(() => {
    run('INSERT INTO users (id, username, name, rank, role, password_hash, active, created_at) VALUES (?,?,?,?,?,?,1,?)', id, b.data.username, b.data.name, b.data.rank, b.data.role, hashPassword(b.data.password), Date.now())
    audit(req.user!.id, 'User created', null, `${b.data.username} created as ${b.data.role}`)
  })
  res.status(201).json({ id })
})

adminRouter.patch('/users/:id', (req, res) => {
  const b = z
    .object({ name: z.string().trim().min(1).max(80).optional(), rank: z.string().trim().min(1).max(40).optional(), role: roleEnum.optional(), active: z.boolean().optional() })
    .safeParse(req.body)
  if (!b.success) throw new HttpError(400, `${b.error.issues[0].path.join('.')}: ${b.error.issues[0].message}`)
  const id = String(req.params.id)
  const target = get<AdminRow>('SELECT id, username, name, rank, role, active, created_at, last_login_at FROM users WHERE id = ?', id)
  if (!target) throw new HttpError(404, 'User not found')
  const nextRole = b.data.role ?? target.role
  const nextActive = b.data.active ?? !!target.active
  if (id === req.user!.id && (nextRole !== 'admin' || !nextActive)) throw new HttpError(409, 'You cannot demote or deactivate yourself')
  if (target.role === 'admin' && !!target.active && (nextRole !== 'admin' || !nextActive) && activeAdminCount() <= 1) {
    throw new HttpError(409, 'There must always be at least one active administrator')
  }
  tx(() => {
    run('UPDATE users SET name = ?, rank = ?, role = ?, active = ? WHERE id = ?', b.data.name ?? target.name, b.data.rank ?? target.rank, nextRole, nextActive ? 1 : 0, id)
    const changes = [
      b.data.role && b.data.role !== target.role ? `role ${target.role} -> ${b.data.role}` : '',
      b.data.active !== undefined && b.data.active !== !!target.active ? (b.data.active ? 'activated' : 'deactivated') : '',
    ].filter(Boolean)
    audit(req.user!.id, 'User updated', null, `${target.username}${changes.length ? `: ${changes.join(', ')}` : ''}`)
  })
  // Permissions changed, so existing sessions must not keep the old ones.
  if (nextRole !== target.role || !nextActive) destroyUserSessions(id)
  res.json({ ok: true })
})

adminRouter.post('/users/:id/password', (req, res) => {
  const b = z.object({ password: z.string().max(200) }).safeParse(req.body)
  if (!b.success) throw new HttpError(400, 'A password is required')
  const id = String(req.params.id)
  const target = get<{ username: string }>('SELECT username FROM users WHERE id = ?', id)
  if (!target) throw new HttpError(404, 'User not found')
  const err = passwordError(b.data.password, target.username)
  if (err) throw new HttpError(400, err)
  tx(() => {
    run('UPDATE users SET password_hash = ? WHERE id = ?', hashPassword(b.data.password), id)
    audit(req.user!.id, 'Password reset', null, `Password reset for ${target.username}`)
  })
  destroyUserSessions(id)
  res.json({ ok: true })
})
