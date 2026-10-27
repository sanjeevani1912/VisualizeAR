import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')

function num(name: string, fallback: number): number {
  const v = process.env[name]
  const n = v === undefined ? NaN : Number(v)
  return Number.isFinite(n) ? n : fallback
}

export const config = {
  port: num('PORT', 4000),
  dataDir: process.env.DATA_DIR ? path.resolve(process.env.DATA_DIR) : path.join(root, 'data'),
  /** Set true behind HTTPS. Cookies are then marked Secure. */
  cookieSecure: process.env.COOKIE_SECURE === 'true',
  trustProxy: process.env.TRUST_PROXY === 'true',
  sessionIdleMinutes: num('SESSION_IDLE_MINUTES', 60),
  sessionAbsoluteHours: num('SESSION_ABSOLUTE_HOURS', 12),
  maxUploadBytes: num('MAX_UPLOAD_MB', 10) * 1024 * 1024,
  maxImagesPerPlan: num('MAX_IMAGES_PER_PLAN', 30),
  loginMaxFailures: num('LOGIN_MAX_FAILURES', 5),
  loginLockMinutes: num('LOGIN_LOCK_MINUTES', 15),
  /** Optional map server origin the browser may load tiles from (added to the CSP). */
  mapOrigin: process.env.MAP_ORIGIN ?? '',
  webDist: process.env.WEB_DIST ? path.resolve(process.env.WEB_DIST) : path.resolve(root, '..', 'web', 'dist'),
}

export const COOKIE_NAME = 'dhruvar_sid'
