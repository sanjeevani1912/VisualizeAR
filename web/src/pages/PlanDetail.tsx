import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useStore } from '../storeContext'
import { MapView } from '../components/MapView'
import { AssetIcon } from '../components/AssetIcon'
import { DecisionPanel } from '../components/DecisionPanel'
import { FieldImages } from '../components/FieldImages'
import { OwnerActions } from '../components/OwnerActions'
import { StatusBadge } from '../components/StatusBadge'
import { canComment, canStartReview } from '../rules'
import { ASSET_DEFAULTS, assetCountText, dim, distanceFromOrigin, formatLatLng, formatRegion, summarizeAssets } from '../geo'
import { fullDate, latestVersion, relTime, shortDate, waitingLabel, OPEN_STATUSES } from '../util'

type Tab = 'assets' | 'images' | 'versions' | 'comments' | 'audit'

export function PlanDetail() {
  const { id } = useParams()
  const { plans, audit, user, comment, markReviewing, nameOf, base, ready } = useStore()
  const plan = plans.find((p) => p.id === id)
  const [tab, setTab] = useState<Tab>('assets')
  const [versionNo, setVersionNo] = useState<number | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [text, setText] = useState('')
  const [commentError, setCommentError] = useState<string | null>(null)

  // Opening a submitted plan as a reviewer moves it to "under review".
  useEffect(() => {
    if (plan && user && canStartReview(user, { ownerId: plan.ownerId, status: plan.status })) markReviewing(plan.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plan?.id, plan?.status, user?.id])

  const version = useMemo(() => {
    if (!plan) return undefined
    return plan.versions.find((v) => v.version === (versionNo ?? latestVersion(plan).version)) ?? latestVersion(plan)
  }, [plan, versionNo])

  if (!plan || !version) {
    return (
      <div className="page">
        <p className="empty">
          {ready ? 'Plan not found, or you do not have access to it.' : 'Loading…'} <Link to={`${base}/plans`} className="link">Back to plans</Link>
        </p>
      </div>
    )
  }

  const layout = version.layout
  const isLatest = version.version === latestVersion(plan).version
  const events = audit.filter((e) => e.planId === plan.id)
  const accuracy = layout.anchor.horizontalAccuracyMeters
  const poorAccuracy = accuracy !== null && accuracy > 15
  const now = Date.now()
  const selectedObj = layout.objects.find((o) => o.id === selected)
  const mayComment = !!user && canComment(user)

  const tabs: [Tab, string][] = [
    ['assets', `Assets (${layout.objects.length})`],
    ['images', `Field images (${plan.attachments.length})`],
    ['versions', `Versions (${plan.versions.length})`],
    ['comments', `Comments (${plan.comments.length})`],
    ['audit', 'Audit trail'],
  ]

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Breadcrumb">
        <Link to={`${base}/plans`} className="link">Plans</Link> <span aria-hidden="true">/</span> <span>{plan.code}</span>
      </nav>
      <header className="page-head">
        <div>
          <h1>{plan.name}</h1>
          <p className="muted">
            {plan.code} · planner {nameOf(plan.ownerId)} · submitted {fullDate(latestVersion(plan).submittedAt)}
            {OPEN_STATUSES.includes(plan.status) && ` · waiting ${waitingLabel(plan, now)}`}
          </p>
        </div>
        <StatusBadge status={plan.status} />
      </header>

      {!isLatest && (
        <div className="notice" role="status">
          Viewing earlier version {version.version}. <button className="link-btn" onClick={() => setVersionNo(null)}>Show latest (v{latestVersion(plan).version})</button>
        </div>
      )}

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card flush map-card">
            <MapView layout={layout} highlightId={selected} onSelect={setSelected} />
            <div className="map-legend" aria-label="Legend">
              {summarizeAssets(layout).map(({ type: t }) => (
                <span key={t}><AssetIcon type={t} size={20} />{ASSET_DEFAULTS[t].label}</span>
              ))}
              <span><i className="lg-origin" />Origin</span>
            </div>
            {selectedObj && (
              <div className="map-selected" role="status">
                <strong>{selectedObj.name}</strong> · {ASSET_DEFAULTS[selectedObj.assetType].label} · {Math.round(distanceFromOrigin(selectedObj) * 10) / 10} m from origin
                <button className="link-btn" onClick={() => setSelected(null)}>Clear</button>
              </div>
            )}
          </section>

          <section className="card flush">
            <div className="tabs" role="tablist">
              {tabs.map(([k, label]) => (
                <button key={k} role="tab" aria-selected={tab === k} className={`tab ${tab === k ? 'on' : ''}`} onClick={() => setTab(k)}>
                  {label}
                </button>
              ))}
            </div>
            <div className="tab-body">
              {tab === 'assets' && (
                <div className="table-wrap">
                  <table className="table compact">
                    <thead>
                      <tr><th>Name</th><th>Type</th><th>X (m)</th><th>Z (m)</th><th>From origin</th><th>Rotation</th><th>Size W×L×H (m)</th></tr>
                    </thead>
                    <tbody>
                      {layout.objects.map((o) => (
                        <tr key={o.id} className={selected === o.id ? 'row-selected' : ''} onClick={() => setSelected(o.id === selected ? null : o.id)} tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter') setSelected(o.id === selected ? null : o.id) }}>
                          <td className="name-cell"><AssetIcon type={o.assetType} size={18} />{o.name}</td>
                          <td>{ASSET_DEFAULTS[o.assetType].label}</td>
                          <td>{o.x.toFixed(1)}</td>
                          <td>{o.z.toFixed(1)}</td>
                          <td>{distanceFromOrigin(o).toFixed(1)} m</td>
                          <td>{Math.round(o.rotationDegrees)}°</td>
                          <td>{dim(o.widthMeters)} × {dim(o.lengthMeters)} × {dim(o.heightMeters)}</td>
                        </tr>
                      ))}
                      {layout.objects.length === 0 && <tr><td colSpan={7} className="empty">No assets placed in this version.</td></tr>}
                    </tbody>
                  </table>
                </div>
              )}
              {tab === 'images' && <FieldImages plan={plan} />}
              {tab === 'versions' && (
                <ul className="timeline">
                  {[...plan.versions].reverse().map((v) => {
                    const decisions = plan.decisions.filter((d) => d.version === v.version)
                    return (
                      <li key={v.version}>
                        <div className="row between">
                          <strong>Version {v.version}</strong>
                          <button className="link-btn" onClick={() => { setVersionNo(v.version); setSelected(null); window.scrollTo({ top: 0, behavior: 'smooth' }) }}>View on map</button>
                        </div>
                        <div className="small muted">{nameOf(v.submittedBy)} · {fullDate(v.submittedAt)} · {v.layout.objects.length} assets · hash <code>{v.contentHash}</code></div>
                        {v.note && <p>{v.note}</p>}
                        {decisions.map((d) => (
                          <p key={d.id} className="quote"><strong>{d.decision === 'approve' ? 'Approved' : d.decision === 'reject' ? 'Rejected' : 'Changes requested'}</strong> by {nameOf(d.reviewerId)} ({shortDate(d.decidedAt)}){d.reason ? `: ${d.reason}` : ''}</p>
                        ))}
                      </li>
                    )
                  })}
                </ul>
              )}
              {tab === 'comments' && (
                <div>
                  {plan.comments.length === 0 && <p className="empty">No comments yet.</p>}
                  <ul className="comments">
                    {plan.comments.map((c) => (
                      <li key={c.id}>
                        <div className="small muted">{nameOf(c.authorId)} · {relTime(c.createdAt, now)} · v{c.version}</div>
                        <p>{c.text}</p>
                      </li>
                    ))}
                  </ul>
                  {mayComment ? (
                    <form
                      className="comment-form"
                      onSubmit={async (e) => {
                        e.preventDefault()
                        const err = await comment(plan.id, text)
                        setCommentError(err)
                        if (!err) setText('')
                      }}
                    >
                      <label className="field grow">
                        <span>Add a comment</span>
                        <textarea rows={3} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} />
                      </label>
                      {commentError && <p className="form-error" role="alert">{commentError}</p>}
                      <button className="btn btn-primary" disabled={!text.trim()}>Post comment</button>
                    </form>
                  ) : (
                    <p className="small muted">Auditors have read-only access.</p>
                  )}
                </div>
              )}
              {tab === 'audit' && (
                <ul className="timeline">
                  {events.map((e) => (
                    <li key={e.id}>
                      <div className="row between"><strong>{e.action}</strong><span className="small muted">{fullDate(e.at)}</span></div>
                      <div className="small muted">{nameOf(e.actorId)}</div>
                      <p>{e.detail}</p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </div>

        <aside className="detail-side">
          <DecisionPanel plan={plan} />
          <OwnerActions plan={plan} />

          <section className="card">
            <h3>Site summary</h3>
            <dl className="kv">
              <dt>Region size</dt><dd>{formatRegion(layout.regionBounds)}</dd>
              <dt>Origin</dt><dd>{formatLatLng(layout.anchor.latitude, layout.anchor.longitude)}</dd>
              <dt>Heading</dt><dd>{layout.anchor.headingDegrees}°</dd>
              <dt>GPS accuracy</dt>
              <dd className={poorAccuracy ? 'text-bad' : ''}>{accuracy === null ? 'Unknown' : `± ${accuracy.toFixed(1)} m`}{poorAccuracy && ' — poor'}</dd>
              <dt>Assets</dt>
              <dd>{summarizeAssets(layout).map((s) => assetCountText(s.type, s.count)).join(', ') || 'None'}</dd>
              <dt>Field images</dt><dd>{plan.attachments.length}</dd>
              <dt>Version</dt><dd>v{version.version} of {plan.versions.length}</dd>
              <dt>Content hash</dt><dd><code>{version.contentHash}</code></dd>
            </dl>
            <p className="small muted disclaimer">
              Planning visualization, not survey grade. Absolute position depends on phone GPS (see accuracy); distances between assets are exact within the plan.
            </p>
          </section>
        </aside>
      </div>
    </div>
  )
}
