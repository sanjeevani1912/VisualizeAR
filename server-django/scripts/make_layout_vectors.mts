// Runs the REAL server/src/layout.ts on many inputs and records what Node answers.
// Output: JSON array of { label, input_text, result } written to the path given as argv[2].
import crypto from 'node:crypto'
import fs from 'node:fs'

// deterministic ids so generated uuids (and therefore hashes) can be compared
let counter = 0
;(crypto as any).randomUUID = () => `gen-${String(++counter).padStart(4, '0')}`

const { parseLayout, contentHash } = await import('file:///C:/Users/itcell/AndroidStudioProjects/DhruvAR/server/src/layout.ts')

const fixture = JSON.parse(fs.readFileSync('C:/Users/itcell/AndroidStudioProjects/DhruvAR/server/scripts/fixtures/app-layout-v2.json', 'utf8'))

const base = () => ({
  version: 2,
  id: 'layout-1',
  name: 'Test plan',
  regionBounds: { northLatitude: 28.62, southLatitude: 28.6, eastLongitude: 77.22, westLongitude: 77.2 },
  anchor: { latitude: 28.61, longitude: 77.21, headingDegrees: 12.5, horizontalAccuracyMeters: 4.2 },
  objects: [{ id: 'o1', assetType: 'GUNSHIP', name: 'Gunship 1', x: 10, z: -3.5, rotationDegrees: 45, widthMeters: 13.52, lengthMeters: 19.7, heightMeters: 6.05 }],
})

const cases: { label: string; text: string }[] = []
const add = (label: string, value: unknown) => cases.push({ label, text: JSON.stringify(value) })
const addRaw = (label: string, text: string) => cases.push({ label, text })
const mut = (label: string, fn: (l: any) => void) => {
  const l = base()
  fn(l)
  add(label, l)
}

add('base', base())
add('fixture app-layout-v2', fixture)
mut('no objects', (l) => (l.objects = []))
mut('object defaults for every type', (l) => {
  l.objects = ['GUNSHIP', 'K131', 'K131_TAKEOFF', 'MILITARY_VEHICLE', 'OFFROAD_VEHICLE', 'STR_MGS', 'TENT', 'TRUCK', 'CAR', 'ANTENNA', 'TRENCH'].map((t) => ({ assetType: t, x: 1, z: 2 }))
})
mut('duplicate ids', (l) => (l.objects = [1, 2, 3].map(() => ({ id: 'same', assetType: 'CAR', x: 0, z: 0 }))))
mut('empty id and name', (l) => (l.objects = [{ id: '', name: '', assetType: 'CAR', x: 0, z: 0 }]))
mut('whitespace name', (l) => (l.objects = [{ name: '   ', assetType: 'CAR', x: 0, z: 0 }, { name: '  padded  ', assetType: 'TENT', x: 0, z: 0 }]))
mut('id 80 chars', (l) => (l.objects[0].id = 'a'.repeat(80)))
mut('id 81 chars', (l) => (l.objects[0].id = 'a'.repeat(81)))
mut('object name 80', (l) => (l.objects[0].name = 'n'.repeat(80)))
mut('object name 81', (l) => (l.objects[0].name = 'n'.repeat(81)))
mut('object name padded to 82 trims to 80', (l) => (l.objects[0].name = ' ' + 'n'.repeat(80) + ' '))
mut('object name unicode', (l) => (l.objects[0].name = 'Café Ünïcode — 日本語 ✓ <&> "q" \\ \t\n \u0001 \u007f \u2028'))
mut('emoji name', (l) => (l.objects[0].name = 'Tank 🚀 one'))
mut('name emoji split at 80 (>80 units rejected)', (l) => (l.objects[0].name = 'a'.repeat(79) + '🚀'))
mut('name exactly 80 units with emoji', (l) => (l.objects[0].name = 'a'.repeat(78) + '🚀'))
mut('layout name 120', (l) => (l.name = 'L'.repeat(120)))
mut('layout name 121', (l) => (l.name = 'L'.repeat(121)))
mut('layout name empty', (l) => (l.name = ''))
mut('layout name whitespace only', (l) => (l.name = '   '))
mut('layout name missing', (l) => delete l.name)
mut('layout id missing', (l) => delete l.id)
mut('layout id 81', (l) => (l.id = 'i'.repeat(81)))
mut('layout name emoji', (l) => (l.name = 'Plan 😀 — ok'))
mut('lone surrogate name', (l) => (l.name = 'bad \ud800 surrogate'))
mut('x tiny', (l) => (l.objects[0].x = 1e-7))
mut('x tiny 2', (l) => (l.objects[0].x = 1.5e-7))
mut('x 1e-6', (l) => (l.objects[0].x = 0.000001))
mut('x 0.1+0.2', (l) => (l.objects[0].x = 0.1 + 0.2))
mut('x max', (l) => (l.objects[0].x = 100000))
mut('x just over', (l) => (l.objects[0].x = 100000.00001))
mut('x almost', (l) => (l.objects[0].x = 99999.99999999999))
mut('x -min', (l) => (l.objects[0].x = -100000))
mut('x 1e21 rejected', (l) => (l.objects[0].x = 1e21))
mut('x many digits', (l) => (l.objects[0].x = 123.45678901234567))
mut('x 5e-324', (l) => (l.objects[0].x = 5e-324))
mut('x 1.7976931348623157e308', (l) => (l.objects[0].x = 1.7976931348623157e308))
mut('x float 32 value', (l) => (l.objects[0].x = 4.199999809265137))
mut('z float -1234.5', (l) => (l.objects[0].z = -1234.5))
mut('rotation 725.5', (l) => (l.objects[0].rotationDegrees = 725.5))
mut('rotation -90', (l) => (l.objects[0].rotationDegrees = -90))
mut('rotation -1e-20', (l) => (l.objects[0].rotationDegrees = -1e-20))
mut('rotation -360', (l) => (l.objects[0].rotationDegrees = -360))
mut('rotation 360', (l) => (l.objects[0].rotationDegrees = 360))
mut('rotation 3600', (l) => (l.objects[0].rotationDegrees = 3600))
mut('rotation -3600', (l) => (l.objects[0].rotationDegrees = -3600))
mut('rotation 3600.0000001', (l) => (l.objects[0].rotationDegrees = 3600.0000001))
mut('rotation -0.1', (l) => (l.objects[0].rotationDegrees = -0.1))
mut('rotation missing -> 0', (l) => delete l.objects[0].rotationDegrees)
mut('rotation null', (l) => (l.objects[0].rotationDegrees = null))
mut('rotation 1e-7', (l) => (l.objects[0].rotationDegrees = 1e-7))
mut('rotation 359.99999999999994', (l) => (l.objects[0].rotationDegrees = 359.99999999999994))
mut('width 1000', (l) => (l.objects[0].widthMeters = 1000))
mut('width 1000.0001', (l) => (l.objects[0].widthMeters = 1000.0001))
mut('width 0', (l) => (l.objects[0].widthMeters = 0))
mut('width negative', (l) => (l.objects[0].widthMeters = -1))
mut('width 1e-7', (l) => (l.objects[0].widthMeters = 1e-7))
mut('width null', (l) => (l.objects[0].widthMeters = null))
mut('height string', (l) => (l.objects[0].heightMeters = '3'))
mut('length boolean', (l) => (l.objects[0].lengthMeters = true))
mut('sizes missing', (l) => {
  delete l.objects[0].widthMeters
  delete l.objects[0].lengthMeters
  delete l.objects[0].heightMeters
})
mut('anchor heading null', (l) => (l.anchor.headingDegrees = null))
mut('anchor heading missing', (l) => delete l.anchor.headingDegrees)
mut('anchor accuracy null', (l) => (l.anchor.horizontalAccuracyMeters = null))
mut('anchor accuracy missing', (l) => delete l.anchor.horizontalAccuracyMeters)
mut('anchor accuracy negative', (l) => (l.anchor.horizontalAccuracyMeters = -1))
mut('anchor accuracy 100000.5', (l) => (l.anchor.horizontalAccuracyMeters = 100000.5))
mut('anchor heading 3601', (l) => (l.anchor.headingDegrees = 3601))
mut('anchor heading -3600', (l) => (l.anchor.headingDegrees = -3600))
mut('anchor heading string', (l) => (l.anchor.headingDegrees = 'north'))
mut('anchor lat 91', (l) => (l.anchor.latitude = 91))
mut('anchor lng -181', (l) => (l.anchor.longitude = -181))
mut('anchor lat string', (l) => (l.anchor.latitude = '28.6'))
mut('anchor missing', (l) => delete l.anchor)
mut('anchor null', (l) => (l.anchor = null))
mut('anchor array', (l) => (l.anchor = []))
mut('anchor outside region lat', (l) => (l.anchor.latitude = 28.7))
mut('anchor outside region lng', (l) => (l.anchor.longitude = 77.3))
mut('anchor on edge', (l) => {
  l.anchor.latitude = 28.62
  l.anchor.longitude = 77.2
})
mut('north below south', (l) => {
  l.regionBounds.northLatitude = 28.5
})
mut('west east of east', (l) => {
  l.regionBounds.westLongitude = 77.3
})
mut('no area lat', (l) => {
  l.regionBounds.northLatitude = 28.61
  l.regionBounds.southLatitude = 28.61
})
mut('no area lng', (l) => {
  l.regionBounds.eastLongitude = 77.21
  l.regionBounds.westLongitude = 77.21
})
mut('region missing', (l) => delete l.regionBounds)
mut('region lat 90.5', (l) => (l.regionBounds.northLatitude = 90.5))
mut('region lng missing', (l) => delete l.regionBounds.eastLongitude)
mut('version 2.5', (l) => (l.version = 2.5))
mut('version string', (l) => (l.version = '2'))
mut('version null', (l) => (l.version = null))
mut('version 1e20', (l) => (l.version = 1e20))
mut('version 9007199254740991', (l) => (l.version = 9007199254740991))
mut('version 9007199254740993', (l) => (l.version = 9007199254740992 * 2))
mut('version -5', (l) => (l.version = -5))
mut('version 3.0', (l) => (l.version = 3))
mut('objects missing', (l) => delete l.objects)
mut('objects null', (l) => (l.objects = null))
mut('objects object', (l) => (l.objects = {}))
mut('objects string', (l) => (l.objects = 'none'))
mut('object null', (l) => (l.objects = [null]))
mut('object number', (l) => (l.objects = [5]))
mut('object array', (l) => (l.objects = [[]]))
mut('object unknown type', (l) => (l.objects[0].assetType = 'TANK'))
mut('object lowercase type', (l) => (l.objects[0].assetType = 'gunship'))
mut('object type missing', (l) => delete l.objects[0].assetType)
mut('object type number', (l) => (l.objects[0].assetType = 5))
mut('object x missing', (l) => delete l.objects[0].x)
mut('object z string', (l) => (l.objects[0].z = 'abc'))
mut('object x null', (l) => (l.objects[0].x = null))
mut('object x bool', (l) => (l.objects[0].x = false))
mut('object x array', (l) => (l.objects[0].x = [1]))
mut('object x object', (l) => (l.objects[0].x = {}))
mut('object id number', (l) => (l.objects[0].id = 7))
mut('object id null', (l) => (l.objects[0].id = null))
mut('object name null', (l) => (l.objects[0].name = null))
mut('second object bad', (l) => l.objects.push({ assetType: 'CAR', x: 'bad', z: 1 }))
mut('first error wins (two bad fields)', (l) => {
  l.objects[0].z = 'bad'
  l.objects[0].x = 'bad'
})
mut('error order: regionBounds before anchor', (l) => {
  l.regionBounds.northLatitude = 'bad'
  l.anchor.latitude = 'bad'
})
mut('error order: top before objects', (l) => {
  l.objects[0].x = 'bad'
  l.name = 5
})
mut('500 objects', (l) => (l.objects = Array.from({ length: 500 }, (_, i) => ({ id: `id${i}`, assetType: 'CAR', x: i, z: -i }))))
mut('501 objects', (l) => (l.objects = Array.from({ length: 501 }, (_, i) => ({ id: `id${i}`, assetType: 'CAR', x: i, z: -i }))))
mut('extra keys stripped', (l) => {
  ;(l as any).extra = 1
  ;(l.objects[0] as any).scale = 2
  ;(l.anchor as any).x = 5
  ;(l.regionBounds as any).foo = 'bar'
})
mut('key order differs in input', (l) => {
  const o = l.objects[0]
  l.objects[0] = { z: o.z, x: o.x, assetType: o.assetType, id: o.id }
})
mut('layout name number', (l) => (l.name = 5))
mut('layout name null', (l) => (l.name = null))
mut('layout id number', (l) => (l.id = 5))
mut('generated ids and names', (l) => (l.objects = [{ assetType: 'GUNSHIP', x: 0, z: 0 }, { assetType: 'K131_TAKEOFF', x: 1, z: 1 }, { assetType: 'STR_MGS', x: 2, z: 2 }]))
add('empty object', {})
add('array', [])
add('null', null)
add('string', 'text')
add('number', 42)
add('true', true)
addRaw('x is -0 (raw)', '{"version":2,"id":"l","name":"n","regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[{"id":"o","assetType":"CAR","x":-0,"z":-0.0,"rotationDegrees":-0}]}')
addRaw('x is 1e400 (raw)', '{"regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[{"assetType":"CAR","x":1e400,"z":0}]}')
addRaw('x is -1e400 (raw)', '{"regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[{"assetType":"CAR","x":-1e400,"z":0}]}')
addRaw('big integer literal (raw)', '{"version":123456789012345678901234567890,"regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[]}')
addRaw('numbers written as 10.0 and 1E2 (raw)', '{"regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[{"id":"o","assetType":"CAR","x":10.0,"z":1E2,"widthMeters":2.50,"rotationDegrees":1e1}]}')
addRaw('escaped unicode in name (raw)', '{"name":"\\u00e9\\u4e2d\\ud83d\\ude80 \\ud800 end","regionBounds":{"northLatitude":28.62,"southLatitude":28.6,"eastLongitude":77.22,"westLongitude":77.2},"anchor":{"latitude":28.61,"longitude":77.21},"objects":[]}')

// seeded random mutations of the base layout
let seed = 12345
const rnd = () => {
  seed = (seed * 1664525 + 1013904223) % 4294967296
  return seed / 4294967296
}
const pick = <T,>(a: T[]): T => a[Math.floor(rnd() * a.length)]
const junk = () => pick<any>([null, 'text', '', ' ', 5, -5, 0, 1.5, 1e-7, 1e21, -1e21, true, false, [], {}, [1], { a: 1 }, 100001, 91, 181, -181, 3601, 1001, 0.1 + 0.2, 'x'.repeat(81), 'y'.repeat(121), 7.000000000000001, 123456789.123456789, -0.5])
function allPaths(v: any, prefix: string[] = []): string[][] {
  if (v && typeof v === 'object') return Object.keys(v).flatMap((k) => allPaths(v[k], [...prefix, k])).concat([prefix])
  return [prefix]
}
for (let i = 0; i < 400; i++) {
  const l: any = base()
  l.objects.push({ assetType: pick(['CAR', 'TENT', 'K131']), x: rnd() * 100 - 50, z: rnd() * 100 - 50, rotationDegrees: rnd() * 800 - 400 })
  const n = 1 + Math.floor(rnd() * 3)
  for (let k = 0; k < n; k++) {
    const paths = allPaths(l).filter((p) => p.length > 0)
    const p = pick(paths)
    let t: any = l
    for (let j = 0; j < p.length - 1; j++) t = t[p[j]]
    if (rnd() < 0.25) delete t[p[p.length - 1]]
    else t[p[p.length - 1]] = junk()
  }
  add(`random ${i}`, l)
}

mut('501 objects with a bad first object', (l) => {
  l.objects = Array.from({ length: 501 }, (_, i) => ({ id: `id${i}`, assetType: 'CAR', x: i, z: -i }))
  ;(l.objects[0] as any).x = 'bad'
})
mut('501 objects and bad anchor', (l) => {
  l.objects = Array.from({ length: 501 }, (_, i) => ({ id: `id${i}`, assetType: 'CAR', x: i, z: -i }))
  l.anchor.latitude = 'bad'
})
mut('501 objects, bad region order', (l) => {
  l.objects = Array.from({ length: 501 }, (_, i) => ({ id: `id${i}`, assetType: 'CAR', x: i, z: -i }))
  l.regionBounds.northLatitude = 28.5
})
mut('name 81 code points of emoji', (l) => (l.objects[0].name = '🚀'.repeat(81)))
mut('name 80 code points of emoji (160 units)', (l) => (l.objects[0].name = '🚀'.repeat(80)))
mut('layout name 120 emoji', (l) => (l.name = '🚀'.repeat(120)))

const out = cases.map((c) => {
  counter = 0
  let parsed: unknown
  try {
    parsed = JSON.parse(c.text)
  } catch (e) {
    return { label: c.label, input_text: c.text, result: { malformed: true } }
  }
  const r = parseLayout(parsed)
  if (r.layout) {
    return { label: c.label, input_text: c.text, result: { layout_json: JSON.stringify(r.layout), hash: contentHash(r.layout) } }
  }
  return { label: c.label, input_text: c.text, result: { error: r.error } }
})
fs.writeFileSync(process.argv[2], JSON.stringify(out, null, 0))
const ok = out.filter((o: any) => o.result.layout_json).length
console.log(`cases=${out.length} accepted=${ok} rejected=${out.length - ok}`)
