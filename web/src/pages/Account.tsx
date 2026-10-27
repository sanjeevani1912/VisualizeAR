import { useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { useStore } from '../storeContext'

export function Account() {
  const { user, nameOf } = useStore()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setDone(false)
    if (next !== again) return setError('The new passwords do not match')
    try {
      await api('POST', '/auth/change-password', { current, next })
      setDone(true)
      setCurrent('')
      setNext('')
      setAgain('')
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <div className="page narrow">
      <header className="page-head">
        <div>
          <h1>My account</h1>
          <p className="muted">{user ? `${nameOf(user.id)} · ${user.role}` : ''}</p>
        </div>
      </header>
      <form className="card" onSubmit={submit}>
        <h2>Change password</h2>
        <label className="field"><span>Current password</span><input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></label>
        <label className="field"><span>New password</span><input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required /></label>
        <label className="field"><span>Repeat new password</span><input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required /></label>
        <p className="small muted">At least 10 characters with upper case, lower case and a digit. Your other sessions are signed out.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        {done && <p className="notice" role="status">Password changed.</p>}
        <button className="btn btn-primary">Change password</button>
      </form>
    </div>
  )
}
