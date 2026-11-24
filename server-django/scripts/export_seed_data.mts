// One-off export of the dashboard's fictional sample data (web/src/data/mock.ts) to JSON, for the Django `seed` command.
// Run from DhruvAR/server (it needs that folder's tsx):
//   node_modules/.bin/tsx ../../DhruvAR-django/server-django/scripts/export_seed_data.mts <output.json>
// Times are written relative to BASE_TIME; the seed command shifts them so the newest data is "just now".
import fs from 'node:fs'
import { buildInitialData, USERS } from '../../../DhruvAR/web/src/data/mock.ts'

const BASE_TIME = 1_800_000_000_000
// Same user names as server/scripts/seed.ts
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
const { plans, audit } = buildInitialData(BASE_TIME)
const out = {
  baseTime: BASE_TIME,
  users: USERS.map((u) => ({ id: u.id, username: USERNAMES[u.id], name: u.name, rank: u.rank, role: u.role })),
  // Attachments are left out on purpose: server/scripts/seed.ts does not seed them either (the demo "photos" are inline SVGs).
  plans: plans.map(({ attachments: _a, ...p }) => p),
  audit,
}
fs.writeFileSync(process.argv[2], JSON.stringify(out, null, 1) + '\n')
console.log(`exported ${out.users.length} users, ${out.plans.length} plans, ${out.audit.length} audit events`)
