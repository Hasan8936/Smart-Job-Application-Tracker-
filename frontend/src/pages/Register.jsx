import React, { useState, useContext } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'

export default function Register() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
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
          <input
            required
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Your name"
          />
        </div>
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
            placeholder="At least 8 characters"
          />
        </div>
        {error && <p className="text-sm text-status-rejected">{error}</p>}
        <button
          disabled={loading}
          className="w-full bg-accent text-accent-ink text-sm font-semibold py-2.5 rounded-lg hover:bg-accent-dark hover:text-white disabled:opacity-50"
        >
          {loading ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="mt-6 text-sm text-muted text-center">
        Already have an account?{' '}
        <Link to="/login" className="text-ink font-medium hover:text-accent-dark">Sign in</Link>
      </p>
    </AuthLayout>
  )
}
