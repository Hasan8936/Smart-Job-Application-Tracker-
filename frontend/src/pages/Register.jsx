import React, { useState, useContext } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { User, Mail, Lock, Eye, EyeOff, ArrowRight } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'

export default function Register() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const auth = useContext(AuthContext)
  const nav = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      setLoading(true)
      await auth.register(name, email, password)
      nav('/login')
    } catch (err) {
      setError('Registration failed. That email may already be in use.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout heading="Create your account" copy="Track every application, resume, and follow-up in one place.">
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Name</label>
          <div className="relative">
            <User size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
            <input
              required
              className="w-full pl-10 pr-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Your name"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted mb-1.5">Email</label>
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
              placeholder="At least 8 characters"
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
        {error && <p className="text-sm text-status-rejected">{error}</p>}
        <button
          disabled={loading}
          className="btn-gradient w-full inline-flex items-center justify-center gap-2 text-sm font-semibold py-2.5 rounded-full shadow-glow disabled:opacity-50"
        >
          {loading ? 'Creating account…' : 'Create account'} {!loading && <ArrowRight size={15} />}
        </button>
      </form>
      <p className="mt-6 text-sm text-muted text-center">
        Already have an account?{' '}
        <Link to="/login" className="text-accent font-medium hover:text-accent-dark">Sign in</Link>
      </p>
    </AuthLayout>
  )
}
