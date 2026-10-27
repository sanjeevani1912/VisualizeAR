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
  role: Role
  active: boolean
  createdAt: number
  lastLoginAt: number | null
}

const ROLES: Role[] = ['planner', 'reviewer', 'admin', 'auditor']

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
  const [form, setForm] = useState({ username: '', name: '', rank: '', role: 'planner' as Role, password: '' })

  const load = useCallback(async () => {
    try {
      setRows(await api<AdminUser[]>('GET', '/admin/users'))
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
    void run(async () => {
      await api('POST', '/admin/users', form)
      setNotice(`Account created. Give ${form.username} this one-time password: ${form.password}`)
      setForm({ username: '', name: '', rank: '', role: 'planner', password: '' })
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
              <tr><th>Name</th><th>Username</th><th>Role</th><th>Status</th><th>Last sign-in</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const self = r.id === user?.id
                return (
                  <tr key={r.id}>
                    <td><strong>{r.rank} {r.name}</strong></td>
                    <td>{r.username}</td>
                    <td>
                      <select aria-label={`Role for ${r.username}`} value={r.role} disabled={self} onChange={(e) => void run(() => api('PATCH', `/admin/users/${r.id}`, { role: e.target.value }))}>
                        {ROLES.map((x) => <option key={x} value={x}>{x}</option>)}
                      </select>
                    </td>
                    <td><span className={`badge ${r.active ? 'badge-ok' : 'badge-muted'}`}><span className="badge-dot" />{r.active ? 'Active' : 'Disabled'}</span></td>
                    <td>{r.lastLoginAt ? fullDate(r.lastLoginAt) : 'Never'}</td>
                    <td className="actions">
                      <button className="link-btn" disabled={self} onClick={() => void run(() => api('PATCH', `/admin/users/${r.id}`, { active: !r.active }))}>
                        {r.active ? 'Disable' : 'Enable'}
                      </button>
                      <button
                        className="link-btn"
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
          <label className="field"><span>Rank</span><input required value={form.rank} onChange={(e) => setForm({ ...form, rank: e.target.value })} /></label>
          <label className="field"><span>Name</span><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label className="field">
            <span>Role</span>
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              {ROLES.map((x) => <option key={x} value={x}>{x}</option>)}
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
        <p className="small muted">At least 10 characters with upper case, lower case and a digit. Share it with the person securely; they should change it after first sign-in.</p>
      </section>
    </div>
  )
}
