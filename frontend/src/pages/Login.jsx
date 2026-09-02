import React, { useState, useContext, useEffect } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'

const API_ORIGIN = (import.meta.env.VITE_API_BASE || 'http://localhost:8080/api').replace(/\/api\/?$/, '')

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const auth = useContext(AuthContext)
  const nav = useNavigate()
  const location = useLocation()

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const code = params.get('error')
    if (!code) return
    if (code === 'google-email-required') {
      setError('Your Google account did not share an email address, so we could not sign you in.')
    } else if (code === 'google-authorization-failed') {
      setError('Google could not complete the sign-in (authorization failed). Please try again.')
    } else if (code === 'google-login-failed-network') {
      setError('Signed in with Google, but could not reach the server afterward. Check your connection and try again.')
    } else if (code === 'google-login-failed-server') {
      setError('Signed in with Google, but the server hit an error finishing your sign-in. Please try again in a moment.')
    } else if (code.startsWith('google-login-failed-')) {
      const status = code.replace('google-login-failed-', '')
      setError(`Signed in with Google, but loading your account failed (server responded ${status}). Please try again.`)
    } else if (code === 'google-login-failed') {
      setError('Google sign-in did not complete. Please try again.')
    }
    nav(location.pathname, { replace: true })
  }, [location.pathname, location.search, nav])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      setLoading(true)
      await auth.login(email, password)
      const dest = location.state?.from?.pathname || '/'
      nav(dest)
    } catch (err) {
      setError('That email and password combination didn\'t work.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout heading="Welcome back" copy="Pick up your job search right where you left off.">
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Email</label>
          <input
            type="email"
            required
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Password</label>
          <input
            type="password"
            required
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />
          <div className="text-right mt-2">
            <Link to="/forgot-password" className="text-xs text-muted hover:text-ink">Forgot password?</Link>
          </div>
        </div>
        {error && <p className="text-sm text-status-rejected">{error}</p>}
        <button
          disabled={loading}
          className="w-full bg-accent text-accent-ink text-sm font-medium py-2.5 rounded-full hover:bg-accent-dark disabled:opacity-50"
        >
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <a
        href={`${API_ORIGIN}/oauth2/authorization/google`}
        className="w-full mt-3 border border-line text-ink-soft text-sm font-medium py-2.5 rounded-full hover:bg-paper flex items-center justify-center"
      >
        Continue with Google
      </a>
      <p className="mt-6 text-sm text-muted text-center">
        New here?{' '}
        <Link to="/register" className="text-ink font-medium hover:text-accent">Create an account</Link>
      </p>
    </AuthLayout>
  )
}
