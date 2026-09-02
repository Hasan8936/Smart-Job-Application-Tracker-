import React, { useState, useContext, useEffect } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { Mail, Lock, Eye, EyeOff, ArrowRight } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'

const API_ORIGIN = (import.meta.env.VITE_API_BASE || 'http://localhost:8080/api').replace(/\/api\/?$/, '')

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
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
      await auth.login(email, password, remember)
      const dest = location.state?.from?.pathname || '/'
      nav(dest)
    } catch (err) {
      setError('That email and password combination didn\'t work.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout heading="Welcome back" copy="Sign in to continue managing your applications.">
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Email address</label>
          <div className="relative">
            <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
            <input
              type="email"
              required
              className="w-full pl-10 pr-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Password</label>
          <div className="relative">
            <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
            <input
              type={showPassword ? 'text' : 'password'}
              required
              className="w-full pl-10 pr-10 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted hover:text-ink"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
        </div>
        <div className="flex items-center justify-between">
          <label className="flex items-center gap-2 text-xs text-ink-soft">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="accent-accent h-3.5 w-3.5"
            />
            Remember me
          </label>
          <Link to="/forgot-password" className="text-xs text-accent hover:text-accent-dark font-medium">Forgot password?</Link>
        </div>
        {error && <p className="text-sm text-status-rejected">{error}</p>}
        <button
          disabled={loading}
          className="btn-gradient w-full inline-flex items-center justify-center gap-2 text-sm font-medium py-2.5 rounded-full shadow-glow disabled:opacity-50"
        >
          {loading ? 'Signing in…' : 'Sign in'} {!loading && <ArrowRight size={15} />}
        </button>
      </form>

      <div className="flex items-center gap-3 my-5">
        <div className="h-px flex-1 bg-line" />
        <span className="text-xs text-muted">or</span>
        <div className="h-px flex-1 bg-line" />
      </div>

      <a
        href={`${API_ORIGIN}/oauth2/authorization/google`}
        className="w-full border border-line text-ink text-sm font-medium py-2.5 rounded-full hover:bg-paper flex items-center justify-center gap-2"
      >
        <GoogleIcon /> Continue with Google
      </a>
      <p className="mt-6 text-sm text-muted text-center">
        New here?{' '}
        <Link to="/register" className="text-accent font-medium hover:text-accent-dark">Create an account</Link>
      </p>
    </AuthLayout>
  )
}

function GoogleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 01-1.8 2.72v2.26h2.9c1.7-1.57 2.68-3.87 2.68-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.8.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.94v2.33A9 9 0 009 18z" />
      <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 013.68 9c0-.59.1-1.16.27-1.7V4.97H.94A9 9 0 000 9c0 1.45.35 2.83.94 4.03l3.01-2.33z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.46 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 00.94 4.97L3.95 7.3C4.66 5.17 6.65 3.58 9 3.58z" />
    </svg>
  )
}
