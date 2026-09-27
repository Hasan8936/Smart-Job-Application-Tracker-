import React, { useState, useContext, useEffect } from 'react'
import { usePageMeta } from '../lib/pageMeta'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { Mail, Lock, Eye, EyeOff, ArrowRight } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'
import GoogleAuthButton from '../components/GoogleAuthButton'


export default function Login() {
  usePageMeta({ title: 'Sign in – Smart Job Tracker', description: 'Sign in to Smart Job Tracker to manage your job applications, resume matches, and interview reminders.', path: '/login' })
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
    } else if (code === 'account-suspended') {
      setError('Your account has been suspended. Contact support if you think this is a mistake.')
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
      const dest = location.state?.from?.pathname || '/dashboard'
      nav(dest)
    } catch (err) {
      setError(err.response?.status === 403
        ? (err.response?.data?.message || 'Your account has been suspended. Contact support if you think this is a mistake.')
        : 'That email and password combination didn\'t work.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
    <AuthLayout id="login" heading="Welcome back" copy="Sign in to continue managing your applications.">
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

      <GoogleAuthButton />
      <p className="mt-6 text-sm text-muted text-center">
        New here?{' '}
        <Link to="/register" className="text-accent font-medium hover:text-accent-dark">Create an account</Link>
      </p>
    </AuthLayout>
    </>
  )
}

