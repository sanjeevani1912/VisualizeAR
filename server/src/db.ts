import fs from 'node:fs'
import path from 'node:path'
import { DatabaseSync, type SQLInputValue } from 'node:sqlite'
import { config } from './config.js'

fs.mkdirSync(config.dataDir, { recursive: true })
fs.mkdirSync(path.join(config.dataDir, 'uploads'), { recursive: true })

export const db = new DatabaseSync(path.join(config.dataDir, 'dhruvar.db'))
db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;')

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name          TEXT NOT NULL,
  rank          TEXT NOT NULL,
  role          TEXT NOT NULL CHECK (role IN ('planner','reviewer','admin','auditor')),
  password_hash TEXT NOT NULL,
  active        INTEGER NOT NULL DEFAULT 1,
  created_at    INTEGER NOT NULL,
  last_login_at INTEGER
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash  TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  INTEGER NOT NULL,
  last_seen   INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS plans (
  id         TEXT PRIMARY KEY,
  code       TEXT NOT NULL UNIQUE,
  name       TEXT NOT NULL,
  owner_id   TEXT NOT NULL REFERENCES users(id),
  status     TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS plan_versions (
  plan_id      TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  version      INTEGER NOT NULL,
  layout_json  TEXT NOT NULL,
  content_hash TEXT NOT NULL,
  note         TEXT NOT NULL,
  submitted_by TEXT NOT NULL REFERENCES users(id),
  submitted_at INTEGER NOT NULL,
  PRIMARY KEY (plan_id, version)
);
CREATE TABLE IF NOT EXISTS decisions (
  id          TEXT PRIMARY KEY,
  plan_id     TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  version     INTEGER NOT NULL,
  reviewer_id TEXT NOT NULL REFERENCES users(id),
  decision    TEXT NOT NULL CHECK (decision IN ('approve','reject','changes')),
  reason      TEXT NOT NULL,
  decided_at  INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS comments (
  id         TEXT PRIMARY KEY,
  plan_id    TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  version    INTEGER NOT NULL,
  author_id  TEXT NOT NULL REFERENCES users(id),
  text       TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS attachments (
  id          TEXT PRIMARY KEY,
  plan_id     TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  version     INTEGER NOT NULL,
  file_name   TEXT NOT NULL,
  stored_name TEXT NOT NULL,
  mime        TEXT NOT NULL,
  size        INTEGER NOT NULL,
  caption     TEXT NOT NULL,
  uploaded_by TEXT NOT NULL REFERENCES users(id),
  uploaded_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS audit (
  seq        INTEGER PRIMARY KEY AUTOINCREMENT,
  at         INTEGER NOT NULL,
  actor_id   TEXT,
  action     TEXT NOT NULL,
  plan_id    TEXT,
  detail     TEXT NOT NULL,
  prev_hash  TEXT NOT NULL,
  hash       TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_plans_owner ON plans(owner_id);
CREATE INDEX IF NOT EXISTS idx_audit_plan ON audit(plan_id);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
`)

export type Row = Record<string, SQLInputValue>

export function all<T = Row>(sql: string, ...params: SQLInputValue[]): T[] {
  return db.prepare(sql).all(...params) as unknown as T[]
}

export function get<T = Row>(sql: string, ...params: SQLInputValue[]): T | undefined {
  return db.prepare(sql).get(...params) as unknown as T | undefined
}

export function run(sql: string, ...params: SQLInputValue[]) {
  return db.prepare(sql).run(...params)
}

/** Runs fn inside one write transaction; rolls back if it throws. */
export function tx<T>(fn: () => T): T {
  db.exec('BEGIN IMMEDIATE')
  try {
    const out = fn()
    db.exec('COMMIT')
    return out
  } catch (e) {
    db.exec('ROLLBACK')
    throw e
  }
}
