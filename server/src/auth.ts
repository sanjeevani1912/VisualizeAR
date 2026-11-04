import crypto from 'node:crypto'
import type { NextFunction, Request, Response } from 'express'
import { config, COOKIE_NAME } from './config.js'
import { all, get, run } from './db.js'
import type { Role } from './rules.js'

export interface SessionUser {
  id: string
  username: string
  name: string
  rank: string
  role: Role
}

declare module 'express-serve-static-core' {
  interface Request {
    user?: SessionUser
    /** True when the caller authenticated with an Authorization header (the phone app), not a cookie. */
    viaBearer?: boolean
  }
}

export class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

// ---------- passwords (scrypt, per-user salt) ----------
const N = 16384

export function hashPassword(password: string): string {
  const salt = crypto.randomBytes(16)
  const hash = crypto.scryptSync(password, salt, 64, { N, r: 8, p: 1 })
  return `scrypt$${N}$${salt.toString('base64')}$${hash.toString('base64')}`
}

export function verifyPassword(password: string, stored: string): boolean {
  const [scheme, n, salt, hash] = stored.split('$')
  if (scheme !== 'scrypt' || !n || !salt || !hash) return false
  const expected = Buffer.from(hash, 'base64')
  const actual = crypto.scryptSync(password, Buffer.from(salt, 'base64'), expected.length, { N: Number(n), r: 8, p: 1 })
  return crypto.timingSafeEqual(expected, actual)
}

// Used so an unknown username costs the same time as a wrong password.
const DUMMY_HASH = hashPassword('dummy-password-for-timing')

// ---------- login throttling ----------
const failures = new Map<string, { count: number; lockedUntil: number }>()

function throttleKey(username: string, ip: string): string {
  return `${username.toLowerCase()}|${ip}`
}

export function checkLocked(username: string, ip: string): number {
  const f = failures.get(throttleKey(username, ip))
  return f && f.lockedUntil > Date.now() ? Math.ceil((f.lockedUntil - Date.now()) / 60000) : 0
}

function recordFailure(username: string, ip: string): void {
  const key = throttleKey(username, ip)
  const f = failures.get(key) ?? { count: 0, lockedUntil: 0 }
  f.count += 1
  if (f.count >= config.loginMaxFailures) {
    f.lockedUntil = Date.now() + config.loginLockMinutes * 60000
    f.count = 0
  }
  failures.set(key, f)
}

interface UserRow {
  id: string
  username: string
  name: string
  rank: string
  role: Role
  password_hash: string
  active: number
}

/** Returns the user on success, otherwise null. Never reveals whether the username exists. */
export function authenticate(username: string, password: string, ip: string): SessionUser | null {
  const row = get<UserRow>('SELECT * FROM users WHERE username = ?', username)
  const ok = verifyPassword(password, row?.password_hash ?? DUMMY_HASH)
  if (!row || !ok || !row.active) {
    recordFailure(username, ip)
    return null
  }
  failures.delete(throttleKey(username, ip))
  run('UPDATE users SET last_login_at = ? WHERE id = ?', Date.now(), row.id)
  return { id: row.id, username: row.username, name: row.name, rank: row.rank, role: row.role }
}

// ---------- sessions ----------
const sha = (s: string) => crypto.createHash('sha256').update(s).digest('hex')

export function createSession(userId: string): string {
  const token = crypto.randomBytes(32).toString('base64url')
  const now = Date.now()
  run('INSERT INTO sessions (token_hash, user_id, created_at, last_seen) VALUES (?,?,?,?)', sha(token), userId, now, now)
  return token
}

export function destroySession(token: string): void {
  run('DELETE FROM sessions WHERE token_hash = ?', sha(token))
}

export function destroyUserSessions(userId: string): void {
  run('DELETE FROM sessions WHERE user_id = ?', userId)
}

export function purgeExpiredSessions(): void {
  const now = Date.now()
  run('DELETE FROM sessions WHERE last_seen < ? OR created_at < ?', now - config.sessionIdleMinutes * 60000, now - config.sessionAbsoluteHours * 3600000)
}

export function setSessionCookie(res: Response, token: string): void {
  res.cookie(COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'strict',
    secure: config.cookieSecure,
    path: '/',
    maxAge: config.sessionAbsoluteHours * 3600000,
  })
}

export function clearSessionCookie(res: Response): void {
  res.clearCookie(COOKIE_NAME, { httpOnly: true, sameSite: 'strict', secure: config.cookieSecure, path: '/' })
}

function userForToken(token: string): SessionUser | null {
  const now = Date.now()
  const row = get<UserRow & { created_at: number; last_seen: number }>(
    `SELECT u.*, s.created_at, s.last_seen FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?`,
    sha(token),
  )
  if (!row || !row.active) return null
  if (now - row.last_seen > config.sessionIdleMinutes * 60000 || now - row.created_at > config.sessionAbsoluteHours * 3600000) {
    destroySession(token)
    return null
  }
  if (now - row.last_seen > 30000) run('UPDATE sessions SET last_seen = ? WHERE token_hash = ?', now, sha(token))
  return { id: row.id, username: row.username, name: row.name, rank: row.rank, role: row.role }
}

// ---------- middleware ----------
/** The session token the caller presented: an Authorization bearer token wins over the cookie. */
export function presentedToken(req: Request): string | null {
  const header = req.get('authorization')
  if (header) {
    const m = /^Bearer ([A-Za-z0-9_-]{20,100})$/.exec(header)
    return m ? m[1]! : null
  }
  const cookie = req.cookies?.[COOKIE_NAME]
  return typeof cookie === 'string' && cookie ? cookie : null
}

export function loadUser(req: Request, _res: Response, next: NextFunction): void {
  const token = presentedToken(req)
  if (token) {
    req.user = userForToken(token) ?? undefined
    req.viaBearer = !!req.get('authorization')
  }
  next()
}

/**
 * State-changing requests must carry a custom header, which cross-site forms cannot set.
 * A bearer token is never sent by a browser on its own, so those requests are not forgeable that way.
 */
export function csrfGuard(req: Request, _res: Response, next: NextFunction): void {
  if (req.viaBearer && req.user) return next()
  // App sign-in sets no cookie and needs the password, so there is nothing for a forged request to ride on.
  if (req.method === 'POST' && req.path === '/auth/token') return next()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(req.method) && req.get('x-requested-with') !== 'dhruvar') {
    throw new HttpError(403, 'Missing request header')
  }
  next()
}

export function requireAuth(req: Request, _res: Response, next: NextFunction): void {
  if (!req.user) throw new HttpError(401, 'Not signed in')
  next()
}

export function requireRole(...roles: Role[]) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.user) throw new HttpError(401, 'Not signed in')
    if (!roles.includes(req.user.role)) throw new HttpError(403, 'Your role is not allowed to do this')
    next()
  }
}

export function activeAdminCount(): number {
  return all<{ n: number }>("SELECT COUNT(*) AS n FROM users WHERE role = 'admin' AND active = 1")[0]?.n ?? 0
}
