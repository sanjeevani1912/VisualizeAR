import { useRef, useState } from 'react'
import { useStore } from '../storeContext'
import { canAttach, canRemoveAttachment, imageError } from '../rules'
import { fullDate } from '../util'
import type { Plan } from '../types'

function size(n: number): string {
  return n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`
}

/** Photos of the location taken in the field (not AR captures). */
export function FieldImages({ plan }: { plan: Plan }) {
  const { user, addAttachment, removeAttachment, nameOf } = useStore()
  const input = useRef<HTMLInputElement>(null)
  const [caption, setCaption] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState<string | null>(null)
  const may = !!user && canAttach(user, { ownerId: plan.ownerId, status: plan.status })
  const shown = plan.attachments.find((a) => a.id === open)

  const pick = (f: File | null) => {
    setError(f ? imageError(f) : null)
    setFile(f && !imageError(f) ? f : null)
  }

  return (
    <div className="images">
      {plan.attachments.length === 0 && <p className="empty">No field images attached yet.</p>}
      <ul className="thumbs">
        {plan.attachments.map((a) => (
          <li key={a.id}>
            <button className="thumb" onClick={() => setOpen(a.id)} aria-label={`Open image: ${a.caption || a.fileName}`}>
              <img src={a.url} alt={a.caption || a.fileName} loading="lazy" />
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
        ))}
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
          <p className="small muted">Photos of the location taken on the ground. JPEG, PNG or WebP, up to 10 MB each. AR captures are not used here.</p>
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
        <div className="lightbox" role="dialog" aria-modal="true" aria-label="Image viewer" onClick={() => setOpen(null)}>
          <figure onClick={(e) => e.stopPropagation()}>
            <img src={shown.url} alt={shown.caption || shown.fileName} />
            <figcaption>
              <strong>{shown.caption || shown.fileName}</strong>
              <span className="small">{nameOf(shown.uploadedBy)} · {fullDate(shown.uploadedAt)} · {size(shown.size)}</span>
            </figcaption>
            <button className="btn btn-ghost btn-sm lightbox-close" onClick={() => setOpen(null)}>Close</button>
          </figure>
        </div>
      )}
    </div>
  )
}
