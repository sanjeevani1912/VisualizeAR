import { useState } from 'react'
import { api, errorText } from '../api'

interface Result {
  ok: boolean
  entries: number
  brokenAtSeq?: number
}

/** Asks the server to re-check the audit log's hash chain. */
export function AuditVerify() {
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  return (
    <section className="card verify">
      <div>
        <strong>Log integrity</strong>
        <p className="small muted">Each entry is chained to the one before it. Editing or deleting a past entry breaks the chain.</p>
      </div>
      <div className="verify-side">
        {result && (
          <span className={`badge ${result.ok ? 'badge-ok' : 'badge-bad'}`}>
            <span className="badge-dot" />
            {result.ok ? `Intact · ${result.entries} entries` : `Tampering detected at entry ${result.brokenAtSeq}`}
          </span>
        )}
        {error && <span className="form-error">{error}</span>}
        <button
          className="btn btn-ghost btn-sm"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            setError(null)
            try {
              setResult(await api<Result>('GET', '/audit/verify'))
            } catch (e) {
              setError(errorText(e))
            }
            setBusy(false)
          }}
        >
          {busy ? 'Checking…' : 'Verify now'}
        </button>
      </div>
    </section>
  )
}
