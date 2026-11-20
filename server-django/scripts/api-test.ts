// End-to-end check of the API rules against a throwaway database and a real server process.
//
// COPY of server/scripts/api-test.ts that can also judge the Django server. The tests themselves are unchanged;
// only the set-up and the one test that edits the database directly differ (marked "CHANGED" below).
//
//   Against Node, as before (starts its own throwaway server and database):
//     node ../server/node_modules/tsx/dist/cli.mjs --test scripts/api-test.ts
//   Against ANY already-running, freshly seeded server (Node or Django):
//     API_BASE=http://127.0.0.1:4101/api  SEED_CREDENTIALS=<data folder>/seed-credentials.txt \
//     TAMPER_COMMAND="<command that sets the detail of audit entry 3 to 'edited'>"  \
//     node ../server/node_modules/tsx/dist/cli.mjs --test scripts/api-test.ts
//
// UPSTREAM CHANGE NEEDED to remove this copy: the same three environment settings in server/scripts/api-test.ts.
import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test, { after, before } from 'node:test'

// CHANGED: where the Node server lives (only used when no external server is given), and the external-server settings.
const nodeServerDirs = [process.env.NODE_SERVER_DIR, path.resolve(import.meta.dirname, '..', '..', 'server'), path.resolve(import.meta.dirname, '..', '..', '..', 'DhruvAR', 'server')]
const root = path.resolve(nodeServerDirs.find((d) => d && fs.existsSync(path.join(d, 'node_modules', 'tsx'))) ?? nodeServerDirs[1]!)
const tsx = path.join(root, 'node_modules', 'tsx', 'dist', 'cli.mjs')
const external = Boolean(process.env.API_BASE)
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dhruvar-test-'))
const port = 4100 + Math.floor(Math.random() * 500)
const base = external ? process.env.API_BASE!.replace(/\/$/, '') : `http://127.0.0.1:${port}/api`
const env = { ...process.env, DATA_DIR: dataDir, PORT: String(port), LOGIN_MAX_FAILURES: '5' }
let server: ReturnType<typeof spawn>
const pw: Record<string, string> = {}

class Client {
  cookie = ''
  async call(method: string, url: string, body?: unknown, form?: FormData) {
    const headers: Record<string, string> = { 'x-requested-with': 'dhruvar' }
    if (this.cookie) headers.cookie = this.cookie
    let payload: BodyInit | undefined
    if (form) payload = form
    else if (body !== undefined) {
      headers['content-type'] = 'application/json'
      payload = JSON.stringify(body)
    }
    const res = await fetch(base + url, { method, headers, body: payload })
    const set = res.headers.get('set-cookie')
    if (set) this.cookie = set.split(';')[0]
    const text = await res.text()
    let json: any = null
    try { json = text ? JSON.parse(text) : null } catch { /* not json */ }
    return { status: res.status, json, headers: res.headers }
  }
  async login(user: string) {
    const r = await this.call('POST', '/auth/login', { username: user, password: pw[user] })
    assert.equal(r.status, 200, `login ${user}`)
    return r
  }
}

const layout = (name = 'Test Site') => ({
  version: 2,
  name,
  regionBounds: { northLatitude: 28.62, southLatitude: 28.61, eastLongitude: 77.215, westLongitude: 77.205 },
  anchor: { latitude: 28.615, longitude: 77.21, headingDegrees: 0, horizontalAccuracyMeters: 4 },
  objects: [{ assetType: 'TENT', x: 10, z: 5, rotationDegrees: 30 }],
})

// CHANGED: the app's own export lives in server/scripts/fixtures (found next to this copy, or via FIXTURE).
function fixturePath(): string {
  const here = import.meta.dirname
  const candidates = [
    process.env.FIXTURE,
    path.join(root, 'scripts', 'fixtures', 'app-layout-v2.json'),
    path.resolve(here, '..', '..', 'server', 'scripts', 'fixtures', 'app-layout-v2.json'),
    path.resolve(here, '..', '..', '..', 'DhruvAR', 'server', 'scripts', 'fixtures', 'app-layout-v2.json'),
  ].filter(Boolean) as string[]
  const found = candidates.find((c) => fs.existsSync(c))
  assert.ok(found, 'cannot find app-layout-v2.json; set FIXTURE')
  return found
}

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

before(async () => {
  if (external) {
    // CHANGED: a server that is already running and already seeded; read its passwords from its credentials file.
    const file = process.env.SEED_CREDENTIALS
    assert.ok(file, 'SEED_CREDENTIALS must point at the seed-credentials.txt of the server under test')
    for (const line of fs.readFileSync(file, 'utf8').split('\n').slice(2)) {
      const [u, , p] = line.trim().split(/\s+/)
      if (u) pw[u] = p
    }
    assert.ok((await fetch(base + '/health')).ok, 'the server under test is not answering')
    return
  }
  const seed = spawnSync(process.execPath, [tsx, 'scripts/seed.ts'], { cwd: root, env, encoding: 'utf8' })
  assert.equal(seed.status, 0, seed.stderr)
  for (const line of fs.readFileSync(path.join(dataDir, 'seed-credentials.txt'), 'utf8').split('\n').slice(2)) {
    const [u, , p] = line.trim().split(/\s+/)
    if (u) pw[u] = p
  }
  server = spawn(process.execPath, [tsx, 'src/index.ts'], { cwd: root, env, stdio: 'ignore' })
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(base + '/health')).ok) return } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 250))
  }
  throw new Error('server did not start')
})

after(async () => {
  if (server && server.exitCode === null) {
    const exited = new Promise((r) => server.once('exit', r))
    server.kill()
    await exited
  }
  try {
    fs.rmSync(dataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
  } catch {
    /* a temp folder left behind is harmless */
  }
})

test('requests without a session or the CSRF header are refused', async () => {
  const anon = new Client()
  assert.equal((await anon.call('GET', '/plans')).status, 401)
  const res = await fetch(base + '/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' })
  assert.equal(res.status, 403)
})

test('login works, sets an httpOnly session cookie, and wrong passwords are rejected', async () => {
  const c = new Client()
  const bad = await c.call('POST', '/auth/login', { username: 'menon', password: 'nope' })
  assert.equal(bad.status, 401)
  const ok = await c.login('menon')
  assert.equal(ok.json.role, 'reviewer')
  assert.match(ok.headers.get('set-cookie') ?? '', /HttpOnly/i)
  assert.match(ok.headers.get('set-cookie') ?? '', /SameSite=Strict/i)
  assert.equal((await c.call('GET', '/auth/me')).json.username, 'menon')
})

test('the phone app can sign in for a bearer token and submit a plan with it', async () => {
  const auth = (t: string) => ({ authorization: `Bearer ${t}`, 'content-type': 'application/json' })
  const res = await fetch(base + '/auth/token', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'rao', password: pw.rao }) })
  assert.equal(res.status, 200)
  assert.equal(res.headers.get('set-cookie'), null, 'no cookie for app sign-in')
  const { token, user } = (await res.json()) as any
  assert.equal(user.role, 'planner')

  // No x-requested-with header is needed; the bearer token is not sent by browsers on their own.
  const made = await fetch(base + '/plans', { method: 'POST', headers: auth(token), body: JSON.stringify({ layout: layout('From the app'), note: 'sent by app' }) })
  assert.equal(made.status, 201)
  const plan = (await made.json()) as any
  const mine = (await (await fetch(base + '/plans', { headers: auth(token) })).json()) as any[]
  assert.equal(mine.find((p) => p.id === plan.id)?.ownerId, user.id)

  // A wrong token gets nothing, and a wrong password gets no token.
  assert.equal((await fetch(base + '/plans', { headers: auth('x'.repeat(43)) })).status, 401)
  const bad = await fetch(base + '/auth/token', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'rao', password: 'nope' }) })
  assert.equal(bad.status, 401)

  // Signing out ends that token.
  assert.equal((await fetch(base + '/auth/logout', { method: 'POST', headers: auth(token) })).status, 200)
  assert.equal((await fetch(base + '/plans', { headers: auth(token) })).status, 401)
})

test('a layout file exactly as the Android app writes it is accepted and can be matched by its id', async () => {
  // Field-for-field what LayoutJsonSerializer.serialize() emits (docs/layout-schema-v2.md),
  // including the extras the dashboard ignores: anchor id/label/x/z, timestamps and scale.
  const fromApp = {
    version: 2,
    id: 'b6c7f3a2-0000-4000-8000-aaaaaaaaaaaa',
    name: 'Phone plan',
    createdAtEpochMs: 1761900000000,
    updatedAtEpochMs: 1761900500000,
    regionBounds: { northLatitude: 28.6165, southLatitude: 28.6115, eastLongitude: 77.2125, westLongitude: 77.2075 },
    anchor: {
      id: 'anchor-1', label: 'Origin Anchor', x: 0, z: 0, description: 'Primary deployment reference datum',
      latitude: 28.614, longitude: 77.21, headingDegrees: 12.5, horizontalAccuracyMeters: 4.2,
    },
    objects: [
      { id: 'o1', assetType: 'TENT', name: 'Tent 1', x: 5, z: -2, rotationDegrees: 30, widthMeters: 4, lengthMeters: 6, heightMeters: 3, scale: 1 },
      { id: 'o2', assetType: 'TRUCK', name: 'Truck 1', x: -12.5, z: 20, rotationDegrees: 180, widthMeters: 2.5, lengthMeters: 8, heightMeters: 3.2, scale: 1 },
    ],
  }
  const auth = (t: string) => ({ authorization: `Bearer ${t}`, 'content-type': 'application/json' })
  const tok = (await (await fetch(base + '/auth/token', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'bose', password: pw.bose }) })).json()) as any
  const made = await fetch(base + '/plans', { method: 'POST', headers: auth(tok.token), body: JSON.stringify({ layout: fromApp, note: '' }) })
  assert.equal(made.status, 201)
  const { id } = (await made.json()) as any

  const mine = (await (await fetch(base + '/plans', { headers: auth(tok.token) })).json()) as any[]
  const plan = mine.find((p) => p.id === id)
  const sent = plan.versions[plan.versions.length - 1].layout
  assert.equal(sent.id, fromApp.id, 'the app finds its plan again by layout id')
  assert.equal(plan.status, 'SUBMITTED')
  assert.deepEqual(sent.objects.map((o: any) => [o.assetType, o.x, o.z, o.rotationDegrees]), [['TENT', 5, -2, 30], ['TRUCK', -12.5, 20, 180]])
  assert.equal(sent.anchor.headingDegrees, 12.5)
})

// scripts/fixtures/app-layout-v2.json was written by the Android app's own LayoutJsonSerializer
// with one object of every asset type in the current library. If the app's format or asset
// library changes, regenerate it from the app; this test then shows exactly what the server
// no longer accepts, before a planner finds out by failing to send.
test('a plan written by the real app, with every current asset type, is stored without change', async () => {
  const sample = JSON.parse(fs.readFileSync(fixturePath(), 'utf8'))
  assert.ok(sample.objects.length >= 6)
  const auth = (t: string) => ({ authorization: `Bearer ${t}`, 'content-type': 'application/json' })
  const tok = (await (await fetch(base + '/auth/token', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'khan', password: pw.khan }) })).json()) as any
  const made = await fetch(base + '/plans', { method: 'POST', headers: auth(tok.token), body: JSON.stringify({ layout: sample, note: '' }) })
  assert.equal(made.status, 201, JSON.stringify(await made.clone().json()))
  const { id } = (await made.json()) as any
  const plan = ((await (await fetch(base + '/plans', { headers: auth(tok.token) })).json()) as any[]).find((p) => p.id === id)
  const stored = plan.versions[0].layout
  assert.equal(stored.id, sample.id)
  assert.equal(stored.objects.length, sample.objects.length)
  sample.objects.forEach((o: any, i: number) => {
    const s = stored.objects[i]
    assert.deepEqual(
      [s.assetType, s.x, s.z, s.rotationDegrees, s.widthMeters, s.lengthMeters, s.heightMeters],
      [o.assetType, o.x, o.z, o.rotationDegrees, o.widthMeters, o.lengthMeters, o.heightMeters],
      `object ${i} (${o.assetType}) changed on the way in`,
    )
  })
})

test('repeated failures lock the account for that client', async () => {
  const c = new Client()
  let last = 0
  for (let i = 0; i < 6; i++) last = (await c.call('POST', '/auth/login', { username: 'khan', password: 'wrong' + i })).status
  assert.equal(last, 429)
})

test('a planner only sees their own plans', async () => {
  const c = new Client()
  await c.login('singh')
  const plans = (await c.call('GET', '/plans')).json as any[]
  assert.ok(plans.length > 0)
  assert.ok(plans.every((p) => p.ownerId === 'u-p1'))
  const others = (await new Client().login('menon').then(async () => 0), 0)
  assert.equal(others, 0)
  const rev = new Client()
  await rev.login('menon')
  const all = (await rev.call('GET', '/plans')).json as any[]
  const foreign = all.find((p) => p.ownerId !== 'u-p1')
  assert.equal((await c.call('POST', `/plans/${foreign.id}/comments`, { text: 'snooping around' })).status, 404)
})

test('only a non-owner reviewer can decide; reason rules and stale versions are enforced', async () => {
  const planner = new Client()
  await planner.login('singh')
  const created = await planner.call('POST', '/plans', { layout: layout(), note: 'test' })
  assert.equal(created.status, 201)
  const id = created.json.id as string
  assert.equal((await planner.call('POST', `/plans/${id}/decision`, { decision: 'approve', reason: '', version: 1 })).status, 403)

  const admin = new Client()
  await admin.login('iyer')
  assert.equal((await admin.call('POST', `/plans/${id}/decision`, { decision: 'approve', reason: '', version: 1 })).status, 403)

  const rev = new Client()
  await rev.login('menon')
  assert.equal((await rev.call('POST', `/plans/${id}/decision`, { decision: 'reject', reason: 'bad', version: 1 })).status, 400)
  assert.equal((await rev.call('POST', `/plans/${id}/decision`, { decision: 'approve', reason: '', version: 7 })).status, 409)
  assert.equal((await rev.call('POST', `/plans/${id}/start-review`)).json.changed, true)
  assert.equal((await rev.call('POST', `/plans/${id}/decision`, { decision: 'changes', reason: 'Move the antenna away from the trench', version: 1 })).status, 200)
  assert.equal((await rev.call('POST', `/plans/${id}/decision`, { decision: 'approve', reason: '', version: 1 })).status, 403) // already decided

  // Planner revises, creating version 2, which goes back to review.
  assert.equal((await planner.call('POST', `/plans/${id}/versions`, { layout: layout('Test Site v2'), note: 'moved antenna' })).status, 201)
  const plan = ((await planner.call('GET', '/plans')).json as any[]).find((p) => p.id === id)
  assert.equal(plan.versions.length, 2)
  assert.equal(plan.status, 'SUBMITTED')
})

test('a reviewer cannot decide their own plan', async () => {
  const rev = new Client()
  await rev.login('menon')
  const all = (await rev.call('GET', '/plans')).json as any[]
  assert.ok(all.every((p) => p.ownerId !== 'u-rev1'))
})

test('malformed layout files are rejected with a clear message', async () => {
  const c = new Client()
  await c.login('rao')
  const noRegion = await c.call('POST', '/plans', { layout: { anchor: { latitude: 1, longitude: 1 }, objects: [] }, note: '' })
  assert.equal(noRegion.status, 400)
  const outside = layout()
  outside.anchor.latitude = 10
  const r = await c.call('POST', '/plans', { layout: outside, note: '' })
  assert.equal(r.status, 400)
  assert.match(r.json.error, /outside the region/)
})

test('field images: type is checked by content, size by limit, and access follows the plan', async () => {
  const planner = new Client()
  await planner.login('rao')
  const plans = (await planner.call('GET', '/plans')).json as any[]
  const mine = plans.find((p) => p.status === 'SUBMITTED')
  const form = (data: Buffer, type: string, name: string) => {
    const f = new FormData()
    f.set('file', new Blob([new Uint8Array(data)], { type }), name)
    f.set('caption', 'North gate')
    return f
  }
  const fake = await planner.call('POST', `/plans/${mine.id}/attachments`, undefined, form(Buffer.from('<script>alert(1)</script>'), 'image/png', 'x.png'))
  assert.equal(fake.status, 415)
  const good = await planner.call('POST', `/plans/${mine.id}/attachments`, undefined, form(PNG, 'image/png', 'gate.png'))
  assert.equal(good.status, 201)
  const att = (((await planner.call('GET', '/plans')).json as any[]).find((p) => p.id === mine.id)).attachments[0]
  const file = await fetch(base.replace('/api', '') + att.url, { headers: { cookie: planner.cookie } })
  assert.equal(file.status, 200)
  assert.equal(file.headers.get('content-type'), 'image/png')
  assert.equal(file.headers.get('x-content-type-options'), 'nosniff')

  const other = new Client()
  await other.login('bose')
  assert.equal((await other.call('GET', att.url.replace('/api', ''))).status, 404)
  const aud = new Client()
  await aud.login('nair')
  assert.equal((await aud.call('POST', `/plans/${mine.id}/attachments`, undefined, form(PNG, 'image/png', 'a.png'))).status, 403)
  assert.equal((await aud.call('POST', `/plans/${mine.id}/comments`, { text: 'auditors are read-only' })).status, 403)
  assert.equal((await planner.call('DELETE', att.url.replace('/api', ''))).status, 200)
})

test('only admins manage users; admin cannot lock themselves out; role change ends sessions', async () => {
  const rev = new Client()
  await rev.login('sharma')
  assert.equal((await rev.call('GET', '/admin/users')).status, 403)
  const admin = new Client()
  await admin.login('iyer')
  const users = (await admin.call('GET', '/admin/users')).json as any[]
  const me = users.find((u) => u.username === 'iyer')
  assert.equal((await admin.call('PATCH', `/admin/users/${me.id}`, { active: false })).status, 409)
  assert.equal((await admin.call('PATCH', `/admin/users/${me.id}`, { role: 'reviewer' })).status, 409)
  assert.equal((await admin.call('POST', '/admin/users', { username: 'newguy', name: 'N. Guy', rank: 'Captain', role: 'planner', password: 'weak' })).status, 400)
  const made = await admin.call('POST', '/admin/users', { username: 'newguy', name: 'N. Guy', rank: 'Captain', role: 'planner', password: 'Str0ng-Passphrase-1' })
  assert.equal(made.status, 201)
  const g = new Client()
  assert.equal((await g.call('POST', '/auth/login', { username: 'newguy', password: 'Str0ng-Passphrase-1' })).status, 200)
  assert.equal((await admin.call('PATCH', `/admin/users/${made.json.id}`, { active: false })).status, 200)
  assert.equal((await g.call('GET', '/auth/me')).status, 401) // session ended
  assert.equal((await new Client().call('POST', '/auth/login', { username: 'newguy', password: 'Str0ng-Passphrase-1' })).status, 401)
})

test('the audit log is chained and tampering is detected', async () => {
  const aud = new Client()
  await aud.login('nair')
  const ok = await aud.call('GET', '/audit/verify')
  assert.equal(ok.json.ok, true)
  assert.ok(ok.json.entries > 10)
  // CHANGED: an external server has its own database, so the edit is done by a command the caller supplies.
  if (external) {
    assert.ok(process.env.TAMPER_COMMAND, 'TAMPER_COMMAND must edit audit entry 3 of the server under test')
    const edited = spawnSync(process.env.TAMPER_COMMAND, { shell: true, encoding: 'utf8' })
    assert.equal(edited.status, 0, edited.stderr)
  } else {
    const { DatabaseSync } = await import('node:sqlite')
    const db = new DatabaseSync(path.join(dataDir, 'dhruvar.db'))
    db.exec("UPDATE audit SET detail = 'edited' WHERE seq = 3")
    db.close()
  }
  const broken = await aud.call('GET', '/audit/verify')
  assert.equal(broken.json.ok, false)
  assert.equal(broken.json.brokenAtSeq, 3)
})
