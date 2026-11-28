import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useStore } from '../storeContext'
import { MapView, type MapHandle } from '../components/MapView'
import { AssetIcon } from '../components/AssetIcon'
import { DecisionPanel } from '../components/DecisionPanel'
import { FieldImages } from '../components/FieldImages'
import { OwnerActions } from '../components/OwnerActions'
import { StatusBadge } from '../components/StatusBadge'
import { canComment, canStartReview } from '../rules'
import { ASSET_DEFAULTS, assetCountText, dim, distanceFromOrigin, formatLatLng, formatRegion, summarizeAssets } from '../geo'
import { fullDate, latestVersion, relTime, shortDate, waitingLabel, OPEN_STATUSES } from '../util'

type Tab = 'assets' | 'images' | 'versions' | 'comments' | 'audit'
type SortKey = 'order' | 'name' | 'type' | 'distance' | 'rotation'

/** Natural-number-aware compare so "Tent 2" sorts before "Tent 10". */
const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })

function NorthArrow() {
  return (
    <div className="north-arrow" role="img" aria-label="North is up">
      <svg viewBox="0 0 24 32" width="22" height="30" aria-hidden="true">
        <path d="M12 2 L20 26 L12 20 L4 26 Z" fill="#231F20" />
        <path d="M12 2 L12 20 L4 26 Z" fill="#FCF9F2" stroke="#231F20" strokeWidth="1.2" strokeLinejoin="round" />
      </svg>
      <span>N</span>
    </div>
  )
}

export function PlanDetail() {
  const { id } = useParams()
  const { plans, audit, user, comment, markReviewing, nameOf, base, ready, loadError, refresh } = useStore()
  const [retrying, setRetrying] = useState(false)
  const retry = async () => {
    setRetrying(true)
    try {
      await refresh()
    } finally {
      setRetrying(false)
    }
  }
  const plan = plans.find((p) => p.id === id)
  const [tab, setTab] = useState<Tab>('assets')
  const [versionNo, setVersionNo] = useState<number | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [text, setText] = useState('')
  const [commentError, setCommentError] = useState<string | null>(null)
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: 'order', dir: 1 })
  const mapRef = useRef<MapHandle>(null)
  // Get the map's print picture ready (and loaded) first, so the printed map is never an empty box.
  const printPlan = async () => {
    try {
      await mapRef.current?.preparePrint()
    } finally {
      window.print()
    }
  }

  // Opening a submitted plan as a reviewer moves it to "under review".
  useEffect(() => {
    if (plan && user && canStartReview(user, { ownerId: plan.ownerId, status: plan.status })) markReviewing(plan.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plan?.id, plan?.status, user?.id])

  const version = useMemo(() => {
    if (!plan) return undefined
    return plan.versions.find((v) => v.version === (versionNo ?? latestVersion(plan).version)) ?? latestVersion(plan)
  }, [plan, versionNo])

  if (!plan && loadError) {
    // The server could not be reached (or refused), so "not found" would be misleading.
    return (
      <div className="page">
        <section className="card state-card" role="alert">
          <h1>Could not load this plan</h1>
          <p className="muted">{loadError}</p>
          <div className="row">
            <button type="button" className="btn btn-primary btn-sm" onClick={retry} disabled={retrying}>{retrying ? 'Trying…' : 'Try again'}</button>
            <Link to={`${base}/plans`} className="btn btn-ghost btn-sm">Back to plans</Link>
          </div>
        </section>
      </div>
    )
  }

  if (!plan || !version) {
    return (
      <div className="page">
        <section className="card state-card" role={ready ? 'alert' : 'status'}>
          <h1>{ready ? 'Plan not found' : 'Loading plan…'}</h1>
          <p className="muted">{ready ? 'This plan does not exist, or you do not have access to it.' : 'Fetching the latest data from the server.'}</p>
          <Link to={`${base}/plans`} className="btn btn-ghost btn-sm">Back to plans</Link>
        </section>
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
  const counts = summarizeAssets(layout)
  const sortedObjects = [...layout.objects].sort((a, b) => {
    let r = 0
    if (sort.key === 'name') r = collator.compare(a.name, b.name)
    else if (sort.key === 'type') r = collator.compare(ASSET_DEFAULTS[a.assetType].label, ASSET_DEFAULTS[b.assetType].label) || collator.compare(a.name, b.name)
    else if (sort.key === 'distance') r = distanceFromOrigin(a) - distanceFromOrigin(b)
    else if (sort.key === 'rotation') r = a.rotationDegrees - b.rotationDegrees
    return r * sort.dir
  })
  const sortBy = (key: SortKey) => setSort((s) => (s.key === key ? { key, dir: s.dir === 1 ? -1 : 1 } : { key, dir: 1 }))
  const ariaSort = (key: SortKey): 'ascending' | 'descending' | 'none' => (sort.key === key ? (sort.dir === 1 ? 'ascending' : 'descending') : 'none')
  // A plain function (not a component) so the header button keeps keyboard focus after a click re-renders the table.
  const sortTh = (k: Exclude<SortKey, 'order'>, children: string) => (
    <th aria-sort={ariaSort(k)}>
      <button type="button" className="th-btn" onClick={() => sortBy(k)} title={`Sort by ${children.toLowerCase()}`}>
        {children}
        <span aria-hidden="true" className="sort-mark">{sort.key === k ? (sort.dir === 1 ? ' ▲' : ' ▼') : ''}</span>
      </button>
    </th>
  )

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
        <div className="head-main">
          <h1>{plan.name}</h1>
          <p className="head-meta muted">
            <span><code>{plan.code}</code></span>
            <span>Planner <strong>{nameOf(plan.ownerId)}</strong></span>
            <span>Submitted {fullDate(latestVersion(plan).submittedAt)}</span>
            {OPEN_STATUSES.includes(plan.status) && <span>Waiting {waitingLabel(plan, now)}</span>}
          </p>
        </div>
        <div className="head-side">
          <StatusBadge status={plan.status} />
          <button type="button" className="btn btn-ghost btn-sm no-print" onClick={printPlan}>Print plan</button>
        </div>
      </header>

      {loadError && (
        <div className="notice notice-error no-print" role="alert">
          Could not refresh from the server ({loadError}). Showing the data loaded earlier, which may be out of date.{' '}
          <button type="button" className="link-btn" onClick={retry} disabled={retrying}>{retrying ? 'Trying…' : 'Try again'}</button>
        </div>
      )}

      {!isLatest && (
        <div className="notice" role="status">
          Viewing earlier version {version.version}. <button className="link-btn" onClick={() => setVersionNo(null)}>Show latest (v{latestVersion(plan).version})</button>
        </div>
      )}

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card flush map-card">
            <div className="map-stage">
              <MapView ref={mapRef} layout={layout} highlightId={selected} onSelect={setSelected} />
              <ul className="map-legend" aria-label="Legend">
                {counts.map(({ type: t, count }) => (
                  <li key={t}><AssetIcon type={t} size={20} />{ASSET_DEFAULTS[t].label}<span className="legend-count"> ×{count}</span></li>
                ))}
                <li><i className="lg-origin" aria-hidden="true" />Origin</li>
              </ul>
              <NorthArrow />
              {layout.objects.length === 0 && <p className="map-empty" role="status">No assets placed in this version.</p>}
              {selectedObj && (
                <div className="map-selected" role="status">
                  <strong>{selectedObj.name}</strong> · {ASSET_DEFAULTS[selectedObj.assetType].label} · {Math.round(distanceFromOrigin(selectedObj) * 10) / 10} m from origin
                  <button className="link-btn" onClick={() => setSelected(null)}>Clear</button>
                </div>
              )}
            </div>
            <div className="map-bar">
              <dl className="map-readout">
                <div><dt>Region</dt><dd>{formatRegion(layout.regionBounds)}</dd></div>
                <div><dt>Origin</dt><dd>{formatLatLng(layout.anchor.latitude, layout.anchor.longitude)}</dd></div>
                <div><dt>Heading</dt><dd>{layout.anchor.headingDegrees}°</dd></div>
              </dl>
              <div className="map-actions no-print">
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => mapRef.current?.fitAssets()} disabled={layout.objects.length === 0}>Fit to assets</button>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => mapRef.current?.fitRegion()}>Fit to region</button>
              </div>
            </div>
          </section>

          <section className="card flush tabs-card">
            <div className="tabs" role="tablist">
              {tabs.map(([k, label]) => (
                <button key={k} role="tab" aria-selected={tab === k} className={`tab ${tab === k ? 'on' : ''}`} onClick={() => setTab(k)}>
                  {label}
                </button>
              ))}
            </div>
            <div className="tab-body" role="tabpanel">
              {tab === 'assets' && (
                <>
                {counts.length > 0 && (
                  <p className="type-counts" aria-label="Assets by type">
                    {counts.map((s) => (
                      <span key={s.type} className="chip"><AssetIcon type={s.type} size={14} /> {assetCountText(s.type, s.count)}</span>
                    ))}
                  </p>
                )}
                <div className="table-wrap">
                  <table className="table compact">
                    <caption className="sr-only">Assets in this plan version. Select a column heading to sort. Select a row to highlight it on the map.</caption>
                    <thead>
                      <tr>{sortTh('name', 'Name')}{sortTh('type', 'Type')}<th>X (m)</th><th>Z (m)</th>{sortTh('distance', 'From origin')}{sortTh('rotation', 'Rotation')}<th>Size W×L×H (m)</th></tr>
                    </thead>
                    <tbody>
                      {sortedObjects.map((o) => (
                        <tr key={o.id} className={selected === o.id ? 'row-selected' : ''} aria-selected={selected === o.id} onClick={() => setSelected(o.id === selected ? null : o.id)} tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelected(o.id === selected ? null : o.id) } }}>
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
                </>
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

          <section className="card summary-card">
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
