import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import AuthLayout from '../components/AuthLayout'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/auth/forgot-password', { email })
      setSent(true)
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Unable to send the reset email.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout heading={sent ? 'Check your email' : 'Forgot your password?'} copy={sent ? `If an account exists for ${email}, a reset link is on its way.` : 'Enter your email and we will send you a secure reset link.'}>
      {sent ? <Link to="/login" className="block text-center text-sm text-ink font-medium">Back to sign in</Link> : (
        <form onSubmit={submit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-muted mb-1.5">Email</label>
            <input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm" placeholder="you@example.com" />
          </div>
          {error && <p className="text-sm text-status-rejected">{error}</p>}
          <button disabled={loading} className="w-full bg-accent text-accent-ink text-sm font-medium py-2.5 rounded-full hover:bg-accent-dark disabled:opacity-50">
            {loading ? 'Sending...' : 'Send reset link'}
          </button>
          <p className="text-center text-sm"><Link to="/login" className="text-muted hover:text-ink">Back to sign in</Link></p>
        </form>
      )}
    </AuthLayout>
  )
}