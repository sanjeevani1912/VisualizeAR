import { useState } from 'react'
import { useStore, DECIDABLE } from '../store'
import { displayName, userById } from '../data/mock'
import { latestVersion, fullDate, STATUS_META } from '../util'
import type { DecisionKind, Plan } from '../types'

const MIN_REASON = 10

const COPY: Record<DecisionKind, { title: string; button: string; cls: string; hint: string }> = {
  approve: { title: 'Approve', button: 'Approve plan', cls: 'btn-ok', hint: 'Optional remark' },
  changes: { title: 'Request changes', button: 'Send back for changes', cls: 'btn-warn', hint: 'Tell the planner exactly what to change' },
  reject: { title: 'Reject', button: 'Reject plan', cls: 'btn-bad', hint: 'State the reason for rejection' },
}

export function DecisionPanel({ plan }: { plan: Plan }) {
  const { user, decide } = useStore()
  const [kind, setKind] = useState<DecisionKind | null>(null)
  const [reason, setReason] = useState('')
  const [confirming, setConfirming] = useState(false)
  const v = latestVersion(plan)
  const open = DECIDABLE.includes(plan.status)
  const lastDecision = plan.decisions[plan.decisions.length - 1]

  if (!open) {
    return (
      <section className="card decision">
        <h3>Decision</h3>
        <p className="muted">
          This plan is <strong>{STATUS_META[plan.status].label.toLowerCase()}</strong>.
        </p>
        {lastDecision && (
          <div className="decision-record">
            <div className="small muted">
              {displayName(userById(lastDecision.reviewerId))} · {fullDate(lastDecision.decidedAt)} · version {lastDecision.version}
            </div>
            {lastDecision.reason && <p className="quote">{lastDecision.reason}</p>}
          </div>
        )}
      </section>
    )
  }

  if (!user || user.role !== 'reviewer') {
    return (
      <section className="card decision">
        <h3>Decision</h3>
        <p className="muted">Awaiting a reviewer’s decision. Your role ({user?.role ?? 'unknown'}) can view but not decide.</p>
      </section>
    )
  }

  if (plan.ownerId === user.id) {
    return (
      <section className="card decision">
        <h3>Decision</h3>
        <p className="muted">You submitted this plan, so you cannot decide it (separation of duties).</p>
      </section>
    )
  }

  const needsReason = kind !== null && kind !== 'approve'
  const valid = kind !== null && (!needsReason || reason.trim().length >= MIN_REASON)

  return (
    <section className="card decision">
      <h3>Decision</h3>
      <p className="small muted">
        Applies to <strong>version {v.version}</strong> only · hash <code>{v.contentHash}</code>
      </p>
      <div className="seg" role="radiogroup" aria-label="Decision">
        {(['approve', 'changes', 'reject'] as DecisionKind[]).map((k) => (
          <button
            key={k}
            role="radio"
            aria-checked={kind === k}
            className={`seg-btn seg-${k} ${kind === k ? 'on' : ''}`}
            onClick={() => {
              setKind(k)
              setConfirming(false)
            }}
          >
            {COPY[k].title}
          </button>
        ))}
      </div>
      {kind && (
        <>
          <label className="field">
            <span>
              {COPY[kind].hint}
              {needsReason && <em> (required, min {MIN_REASON} characters)</em>}
            </span>
            <textarea rows={4} value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          {!confirming ? (
            <button className={`btn ${COPY[kind].cls}`} disabled={!valid} onClick={() => setConfirming(true)}>
              {COPY[kind].button}
            </button>
          ) : (
            <div className="confirm">
              <p>
                Confirm <strong>{COPY[kind].title.toLowerCase()}</strong> for {plan.code} version {v.version}? This is recorded in the audit log and
                cannot be edited.
              </p>
              <div className="row">
                <button
                  className={`btn ${COPY[kind].cls}`}
                  onClick={() => {
                    decide(plan.id, kind, reason)
                    setKind(null)
                    setReason('')
                    setConfirming(false)
                  }}
                >
                  Confirm
                </button>
                <button className="btn btn-ghost" onClick={() => setConfirming(false)}>
                  Back
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  )
}
