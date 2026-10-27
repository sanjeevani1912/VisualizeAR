import { useState } from 'react'
import { useStore } from '../storeContext'
import { canResubmit, canWithdraw } from '../rules'
import type { Plan } from '../types'

/** Planner-only: withdraw an open plan, or submit a revised version after changes/rejection. */
export function OwnerActions({ plan }: { plan: Plan }) {
  const { user, withdraw, resubmit, mode } = useStore()
  const [note, setNote] = useState('')
  const [layout, setLayout] = useState<unknown>(null)
  const [fileName, setFileName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (mode !== 'live' || !user) return null
  const ref = { ownerId: plan.ownerId, status: plan.status }
  const mayWithdraw = canWithdraw(user, ref)
  const mayResubmit = canResubmit(user, ref)
  if (!mayWithdraw && !mayResubmit) return null

  const read = async (f: File | null) => {
    setError(null)
    setLayout(null)
    setFileName('')
    if (!f) return
    if (f.size > 2 * 1024 * 1024) return setError('Layout file is larger than 2 MB')
    try {
      setLayout(JSON.parse(await f.text()))
      setFileName(f.name)
    } catch {
      setError('That file is not valid JSON')
    }
  }

  return (
    <section className="card">
      <h3>Your actions</h3>
      {mayResubmit && (
        <div>
          <p className="small muted">
            {plan.status === 'CHANGES_REQUESTED' ? 'Changes were requested.' : 'This plan was rejected.'} Export the revised layout from the app and submit it as a new version.
          </p>
          <label className="field">
            <span>Revised layout file (.json)</span>
            <input type="file" accept="application/json,.json" onChange={(e) => read(e.target.files?.[0] ?? null)} />
          </label>
          <label className="field">
            <span>What changed (optional)</span>
            <textarea rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button
            className="btn btn-primary"
            disabled={!layout || busy}
            onClick={async () => {
              setBusy(true)
              const err = await resubmit(plan.id, layout, note)
              setBusy(false)
              if (err) return setError(err)
              setNote('')
              setLayout(null)
              setFileName('')
            }}
          >
            {busy ? 'Submitting…' : `Submit new version${fileName ? ` (${fileName})` : ''}`}
          </button>
        </div>
      )}
      {mayWithdraw && (
        <div>
          <p className="small muted">Withdrawing takes the plan out of review. It stays on record.</p>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button
            className="btn btn-ghost"
            onClick={async () => {
              if (!window.confirm(`Withdraw ${plan.code}?`)) return
              const err = await withdraw(plan.id)
              if (err) setError(err)
            }}
          >
            Withdraw plan
          </button>
        </div>
      )}
    </section>
  )
}
