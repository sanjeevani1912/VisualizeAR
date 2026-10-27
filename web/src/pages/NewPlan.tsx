import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../storeContext'
import { formatLatLng, formatRegion } from '../geo'
import type { GeoBounds } from '../types'

interface Preview {
  name: string
  assets: number
  region: string
  origin: string
  accuracy: string
}

function preview(json: any): Preview | string {
  const b = json?.regionBounds as GeoBounds | undefined
  const a = json?.anchor
  if (!b || typeof b.northLatitude !== 'number') return 'This plan has no region. Set a region in the app before exporting.'
  if (!a || typeof a.latitude !== 'number' || typeof a.longitude !== 'number') return 'This plan has no geographic origin. Set an origin in the app before exporting.'
  if (!Array.isArray(json.objects)) return 'This file has no list of assets.'
  return {
    name: typeof json.name === 'string' ? json.name : 'Untitled plan',
    assets: json.objects.length,
    region: formatRegion(b),
    origin: formatLatLng(a.latitude, a.longitude),
    accuracy: typeof a.horizontalAccuracyMeters === 'number' ? `± ${a.horizontalAccuracyMeters.toFixed(1)} m` : 'unknown',
  }
}

/** Planner: submit a layout file exported from the DhruvAR app for approval. */
export function NewPlan() {
  const { createPlan } = useStore()
  const nav = useNavigate()
  const [layout, setLayout] = useState<unknown>(null)
  const [info, setInfo] = useState<Preview | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const read = async (f: File | null) => {
    setLayout(null)
    setInfo(null)
    setError(null)
    if (!f) return
    if (f.size > 2 * 1024 * 1024) return setError('Layout file is larger than 2 MB')
    let json: unknown
    try {
      json = JSON.parse(await f.text())
    } catch {
      return setError('That file is not valid JSON')
    }
    const p = preview(json)
    if (typeof p === 'string') return setError(p)
    setLayout(json)
    setInfo(p)
  }

  return (
    <div className="page narrow">
      <header className="page-head">
        <div>
          <h1>Submit a plan</h1>
          <p className="muted">Upload the layout file exported from the DhruvAR app. It goes to the reviewers as version 1.</p>
        </div>
      </header>
      <section className="card">
        <label className="field">
          <span>Layout file (.json)</span>
          <input type="file" accept="application/json,.json" onChange={(e) => read(e.target.files?.[0] ?? null)} />
        </label>
        {info && (
          <dl className="kv preview">
            <dt>Name</dt><dd>{info.name}</dd>
            <dt>Assets</dt><dd>{info.assets}</dd>
            <dt>Region</dt><dd>{info.region}</dd>
            <dt>Origin</dt><dd>{info.origin}</dd>
            <dt>GPS accuracy</dt><dd>{info.accuracy}</dd>
          </dl>
        )}
        <label className="field">
          <span>Note to the reviewer (optional)</span>
          <textarea rows={4} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button
          className="btn btn-primary"
          disabled={!layout || busy}
          onClick={async () => {
            setBusy(true)
            const r = await createPlan(layout, note)
            setBusy(false)
            if (r.error) return setError(r.error)
            nav(`/plans/${r.id}`)
          }}
        >
          {busy ? 'Submitting…' : 'Submit for approval'}
        </button>
        <p className="small muted pad-top">After submitting, open the plan to attach photos of the location taken in the field.</p>
      </section>
    </div>
  )
}
