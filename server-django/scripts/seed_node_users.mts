// Creates the six comparison users in the THROWAWAY Node database (DATA_DIR must point at a temp folder).
import { run } from 'file:///C:/Users/itcell/AndroidStudioProjects/DhruvAR/server/src/db.ts'
import { hashPassword } from 'file:///C:/Users/itcell/AndroidStudioProjects/DhruvAR/server/src/auth.ts'
import crypto from 'node:crypto'

if (!process.env.DATA_DIR || process.env.DATA_DIR.includes('server\\data') || process.env.DATA_DIR.includes('server/data')) {
  throw new Error('DATA_DIR must be a temporary folder')
}
const password = process.env.COMPARE_PASSWORD!
const users: [string, string, string, string][] = [
  ['pat', 'Pat Planner', 'Major', 'planner'],
  ['pia', 'Pia Planner', 'Captain', 'planner'],
  ['rex', 'Rex Reviewer', 'Colonel', 'reviewer'],
  ['rhea', 'Rhea Reviewer', 'Colonel', 'reviewer'],
  ['ada', 'Ada Admin', 'Lt Col', 'admin'],
  ['aud', 'Aud Auditor', 'Major', 'auditor'],
]
for (const [username, name, rank, role] of users) {
  run('INSERT INTO users (id, username, name, rank, role, password_hash, active, created_at) VALUES (?,?,?,?,?,?,1,?)', crypto.randomUUID(), username, name, rank, role, hashPassword(password), Date.now())
}
console.log('seeded', users.length, 'users in', process.env.DATA_DIR)
