import { useEffect, useRef, useState } from 'react'
import { useStore } from '../storeContext'
import { canAttach, canRemoveAttachment, imageError } from '../rules'
import { fullDate } from '../util'
import type { Plan } from '../types'

function size(n: number): string {
  return n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`
}

/** A picture at least this much wider than tall (for example a 2:1 phone panorama) is shown whole and wide. */
const WIDE_RATIO = 1.6
const MAX_ZOOM = 4

/** Width / height of the picture, or null until the browser has loaded it. Clamped so a thin strip stays usable. */
export function aspectOf(width: number, height: number): number | null {
  if (!width || !height) return null
  return Math.min(4, Math.max(0.5, width / height))
}

/** Field images: photos from the ground and pictures sent from the phone's AR screen (including wide panoramas). */
export function FieldImages({ plan }: { plan: Plan }) {
  const { user, addAttachment, removeAttachment, nameOf } = useStore()
  const input = useRef<HTMLInputElement>(null)
  const [caption, setCaption] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState<string | null>(null)
  const [ratios, setRatios] = useState<Record<string, number>>({})
  const [zoom, setZoom] = useState(1)
  const closeBtn = useRef<HTMLButtonElement>(null)
  const opener = useRef<HTMLElement | null>(null)
  const may = !!user && canAttach(user, { ownerId: plan.ownerId, status: plan.status })
  const shown = plan.attachments.find((a) => a.id === open)

  const show = (id: string) => {
    opener.current = document.activeElement as HTMLElement | null
    setZoom(1)
    setOpen(id)
  }
  const close = () => {
    setOpen(null)
    opener.current?.focus()
  }

  // While the viewer is open: Escape closes it, and the Close button gets the keyboard focus.
  useEffect(() => {
    if (!open) return
    closeBtn.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(null)
        opener.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const pick = (f: File | null) => {
    setError(f ? imageError(f) : null)
    setFile(f && !imageError(f) ? f : null)
  }

  return (
    <div className="images">
      {plan.attachments.length === 0 && <p className="empty">No field images attached yet.</p>}
      <ul className="thumbs">
        {plan.attachments.map((a) => {
          const ratio = ratios[a.id]
          const wide = !!ratio && ratio >= WIDE_RATIO
          return (
          <li key={a.id} className={wide ? 'wide' : undefined}>
            <button
              className="thumb"
              style={ratio ? { aspectRatio: String(wide ? ratio : Math.max(ratio, 1)) } : undefined}
              onClick={() => show(a.id)}
              aria-label={`Open image: ${a.caption || a.fileName}`}
            >
              <img
                src={a.url}
                alt={a.caption || a.fileName}
                loading="lazy"
                onLoad={(e) => {
                  const r = aspectOf(e.currentTarget.naturalWidth, e.currentTarget.naturalHeight)
                  if (r) setRatios((prev) => (prev[a.id] === r ? prev : { ...prev, [a.id]: r }))
                }}
              />
            </button>
            <div className="thumb-cap">
              <strong>{a.caption || a.fileName}</strong>
              <span className="small muted">
                {nameOf(a.uploadedBy)} · {fullDate(a.uploadedAt)} · v{a.version}
              </span>
              {user && canRemoveAttachment(user, a.uploadedBy) && (
                <button
                  className="link-btn danger small"
                  onClick={async () => {
                    if (!window.confirm('Remove this image?')) return
                    const err = await removeAttachment(plan.id, a.id)
                    if (err) setError(err)
                  }}
                >
                  Remove
                </button>
              )}
            </div>
          </li>
          )
        })}
      </ul>

      {may ? (
        <form
          className="upload"
          onSubmit={async (e) => {
            e.preventDefault()
            if (!file) return
            setBusy(true)
            const err = await addAttachment(plan.id, file, caption.trim())
            setBusy(false)
            if (err) {
              setError(err)
              return
            }
            setFile(null)
            setCaption('')
            setError(null)
            if (input.current) input.current.value = ''
          }}
        >
          <h4>Add a field image</h4>
          <p className="small muted">Photos of the location, or pictures sent from the phone's AR screen (including panoramas). JPEG, PNG or WebP, up to 10 MB each.</p>
          <div className="upload-row">
            <label className="field grow">
              <span>Image</span>
              <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => pick(e.target.files?.[0] ?? null)} />
            </label>
            <label className="field grow">
              <span>Caption (optional)</span>
              <input value={caption} maxLength={200} placeholder="e.g. North approach road" onChange={(e) => setCaption(e.target.value)} />
            </label>
            <button className="btn btn-primary" disabled={!file || busy}>{busy ? 'Uploading…' : 'Attach'}</button>
          </div>
          {file && <p className="small muted">{file.name} · {size(file.size)}</p>}
        </form>
      ) : (
        <p className="small muted pad">{user?.role === 'auditor' ? 'Auditors have read-only access.' : plan.status === 'WITHDRAWN' ? 'This plan was withdrawn.' : 'You cannot add images to this plan.'}</p>
      )}
      {error && <p className="form-error pad" role="alert">{error}</p>}

      {shown && (
        <div className="lightbox" role="dialog" aria-modal="true" aria-label={`Image viewer: ${shown.caption || shown.fileName}`} onClick={close}>
          <figure onClick={(e) => e.stopPropagation()}>
            <div className="lightbox-scroll" tabIndex={0} aria-label="Image, scroll to pan when zoomed">
              <img
                src={shown.url}
                alt={shown.caption || shown.fileName}
                style={zoom > 1 ? { width: `${zoom * 100}%`, maxWidth: 'none', maxHeight: 'none' } : undefined}
              />
            </div>
            <figcaption>
              <strong>{shown.caption || shown.fileName}</strong>
              <span className="small">
                Sent by {nameOf(shown.uploadedBy)} · {fullDate(shown.uploadedAt)} · {size(shown.size)}
              </span>
            </figcaption>
            <div className="lightbox-actions">
              <button className="btn btn-ghost btn-sm" onClick={() => setZoom((z) => Math.max(1, z - 1))} disabled={zoom <= 1} aria-label="Zoom out">−</button>
              <span className="small" aria-live="polite">{zoom === 1 ? 'Fit' : `${zoom}×`}</span>
              <button className="btn btn-ghost btn-sm" onClick={() => setZoom((z) => Math.min(MAX_ZOOM, z + 1))} disabled={zoom >= MAX_ZOOM} aria-label="Zoom in">+</button>
              <a className="btn btn-ghost btn-sm" href={shown.url} download={shown.fileName}>Download</a>
              <button ref={closeBtn} className="btn btn-ghost btn-sm lightbox-close" onClick={close}>Close</button>
            </div>
          </figure>
        </div>
      )}
    </div>
  )
}
