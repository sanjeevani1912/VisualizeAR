import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { useStore } from '../storeContext'
import { fullDate } from '../util'
import type { Role } from '../types'

interface AdminUser {
  id: string
  username: string
  name: string
  rank: string
  appointment: string
  role: Role
  active: boolean
  createdAt: number
  lastLoginAt: number | null
  mustChangePassword: boolean
}

interface RankGroup {
  group: string
  ranks: string[]
}

const LABEL: Record<Role, string> = { planner: 'planner', reviewer: 'reviewer', admin: 'admin', auditor: 'auditor', superadmin: 'super admin' }

/** Roles the signed-in person may hand out. Only a super administrator may make administrators, and nobody can
 *  make a super administrator here (that is done on the server's command line). The row's current role is always listed. */
function roleOptions(isSuper: boolean, current: Role): Role[] {
  const list: Role[] = ['planner', 'reviewer', 'auditor', ...(isSuper ? (['admin'] as Role[]) : [])]
  return list.includes(current) ? list : [...list, current]
}
const APPOINTMENT_MAX = 50

/** Rank dropdown, grouped. An older free-text rank that is not in the list is still shown so it is not lost. */
function RankSelect({ groups, value, onChange, label, disabled }: { groups: RankGroup[]; value: string; onChange: (v: string) => void; label: string; disabled?: boolean }) {
  const known = groups.some((g) => g.ranks.includes(value))
  return (
    <select aria-label={label} value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
      {value === '' && <option value="" disabled>Select rank…</option>}
      {value !== '' && !known && <option value={value}>{value} (not in list)</option>}
      {groups.map((g) => (
        <optgroup key={g.group} label={g.group}>
          {g.ranks.map((r) => <option key={r} value={r}>{r}</option>)}
        </optgroup>
      ))}
    </select>
  )
}

/** A text box that saves when you leave it (and only if the text changed). */
function SaveOnBlur({ value, label, max, allowEmpty, disabled, onSave }: { value: string; label: string; max: number; allowEmpty?: boolean; disabled?: boolean; onSave: (v: string) => void }) {
  const [text, setText] = useState(value)
  useEffect(() => setText(value), [value])
  const leave = () => {
    const t = text.trim()
    if (t !== value && (t !== '' || allowEmpty)) onSave(t)
    else setText(value)
  }
  return (
    <input
      aria-label={label}
      value={text}
      disabled={disabled}
      maxLength={max}
      onChange={(e) => setText(e.target.value)}
      onBlur={leave}
      onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
    />
  )
}

function generate(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(12))
  return `${btoa(String.fromCharCode(...bytes)).replace(/[+/=]/g, 'x')}-Aa1`
}

/** Administrator: create accounts, assign roles, deactivate, reset passwords. */
export function Users() {
  const { user, refresh } = useStore()
  const [rows, setRows] = useState<AdminUser[]>([])
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const isSuper = user?.role === 'superadmin'
  const [groups, setGroups] = useState<RankGroup[]>([])
  const [form, setForm] = useState({ username: '', name: '', rank: '', appointment: '', role: 'planner' as Role, password: '' })

  const load = useCallback(async () => {
    try {
      setRows(await api<AdminUser[]>('GET', '/admin/users'))
      setGroups(await api<RankGroup[]>('GET', '/ranks'))
    } catch (e) {
      setError(errorText(e))
    }
  }, [])
  useEffect(() => {
    void load()
  }, [load])

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setError(null)
    setNotice(null)
    try {
      await fn()
      if (ok) setNotice(ok)
      await load()
      await refresh()
    } catch (e) {
      setError(errorText(e))
    }
  }

  const create = (e: FormEvent) => {
    e.preventDefault()
    if (!form.rank) return setError('Choose a rank')
    void run(async () => {
      await api('POST', '/admin/users', form)
      setNotice(`Account created. Give ${form.username} this one-time password: ${form.password}`)
      setForm({ username: '', name: '', rank: '', appointment: '', role: 'planner', password: '' })
    })
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Users</h1>
          <p className="muted">Create accounts and control who can do what. Changes to a role or status end that person’s sessions immediately.</p>
        </div>
      </header>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="notice" role="status">{notice}</p>}

      <section className="card flush">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Rank</th><th>Name</th><th>Appointment</th><th>Username</th><th>Role</th><th>Status</th><th>Last sign-in</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const self = r.id === user?.id
                // Another administrator can only be managed by a super administrator.
                const locked = !self && !isSuper && (r.role === 'admin' || r.role === 'superadmin')
                return (
                  <tr key={r.id}>
                    <td><RankSelect groups={groups} value={r.rank} label={`Rank for ${r.username}`} disabled={locked} onChange={(v) => void run(() => api('PATCH', `/admin/users/${r.id}`, { rank: v }))} /></td>
                    <td><SaveOnBlur value={r.name} label={`Name for ${r.username}`} max={80} disabled={locked} onSave={(v) => void run(() => api('PATCH', `/admin/users/${r.id}`, { name: v }))} /></td>
                    <td><SaveOnBlur value={r.appointment} label={`Appointment for ${r.username}`} max={APPOINTMENT_MAX} allowEmpty disabled={locked} onSave={(v) => void run(() => api('PATCH', `/admin/users/${r.id}`, { appointment: v }))} /></td>
                    <td>{r.username}</td>
                    <td>
                      <select aria-label={`Role for ${r.username}`} value={r.role} disabled={self || locked} onChange={(e) => void run(() => api('PATCH', `/admin/users/${r.id}`, { role: e.target.value }))}>
                        {roleOptions(isSuper, r.role).map((x) => <option key={x} value={x}>{LABEL[x]}</option>)}
                      </select>
                    </td>
                    <td><span className={`badge ${r.active ? 'badge-ok' : 'badge-muted'}`}><span className="badge-dot" />{r.active ? 'Active' : 'Disabled'}</span>{r.mustChangePassword && <div className="small muted">Must change password</div>}</td>
                    <td>{r.lastLoginAt ? fullDate(r.lastLoginAt) : 'Never'}</td>
                    <td className="actions">
                      <button className="link-btn" disabled={self || locked} onClick={() => void run(() => api('PATCH', `/admin/users/${r.id}`, { active: !r.active }))}>
                        {r.active ? 'Disable' : 'Enable'}
                      </button>
                      <button
                        className="link-btn"
                        disabled={locked}
                        onClick={() => {
                          const pw = generate()
                          void run(async () => {
                            await api('POST', `/admin/users/${r.id}/password`, { password: pw })
                            setNotice(`New one-time password for ${r.username}: ${pw}`)
                          })
                        }}
                      >
                        Reset password
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card">
        <h2>Add a user</h2>
        <form className="user-form" onSubmit={create}>
          <label className="field"><span>Username</span><input required minLength={3} pattern="[A-Za-z0-9._\-]+" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
          <label className="field"><span>Rank</span><RankSelect groups={groups} value={form.rank} label="Rank" onChange={(v) => setForm({ ...form, rank: v })} /></label>
          <label className="field"><span>Name</span><input required maxLength={80} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label className="field"><span>Appointment (optional)</span><input maxLength={APPOINTMENT_MAX} placeholder="CO, OC, Adjt…" value={form.appointment} onChange={(e) => setForm({ ...form, appointment: e.target.value })} /></label>
          <label className="field">
            <span>Role</span>
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              {roleOptions(isSuper, 'planner').map((x) => <option key={x} value={x}>{LABEL[x]}</option>)}
            </select>
          </label>
          <label className="field grow">
            <span>Initial password</span>
            <div className="row">
              <input required className="grow" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setForm({ ...form, password: generate() })}>Generate</button>
            </div>
          </label>
          <button className="btn btn-primary self-end">Create account</button>
        </form>
        <p className="small muted">At least 10 characters with upper case, lower case and a digit. Share it with the person securely. At first sign-in they must replace it with their own (a reset password works the same way).</p>
      </section>
    </div>
  )
}
