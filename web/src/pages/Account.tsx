import { useState, type FormEvent } from 'react'
import { api, errorText } from '../api'
import { fullName, useStore } from '../storeContext'

/** `forced`: shown on its own, without the menu, until the temporary password has been replaced. */
export function Account({ forced = false }: { forced?: boolean }) {
  const { user, nameOf, passwordChanged, logout } = useStore()
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
      await passwordChanged?.()
    } catch (err) {
      setError(errorText(err))
    }
  }

  const form = (
    <form className="card" onSubmit={submit}>
      <h2>{forced ? 'Choose your own password' : 'Change password'}</h2>
      {forced && <p className="notice" role="status">The password you were given is temporary. Choose a new one that only you know before you continue.</p>}
      <label className="field"><span>{forced ? 'Temporary password' : 'Current password'}</span><input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></label>
      <label className="field"><span>New password</span><input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required /></label>
      <label className="field"><span>Repeat new password</span><input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required /></label>
      <p className="small muted">At least 10 characters with upper case, lower case and a digit, different from the current one. Your other sessions are signed out.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {done && !forced && <p className="notice" role="status">Password changed.</p>}
      <div className="row">
        <button className="btn btn-primary">Change password</button>
        {forced && <button type="button" className="btn btn-ghost" onClick={() => void logout()}>Sign out</button>}
      </div>
    </form>
  )

  if (forced) {
    return (
      <div className="login-wrap">
        <div className="page narrow">
          <header className="page-head"><div><h1>{user ? fullName(user) : 'Welcome'}</h1><p className="muted">First sign-in</p></div></header>
          {form}
        </div>
      </div>
    )
  }
  return (
    <div className="page narrow">
      <header className="page-head">
        <div>
          <h1>My account</h1>
          <p className="muted">{user ? `${nameOf(user.id)} · ${user.role}` : ''}</p>
        </div>
      </header>
      {form}
    </div>
  )
}
