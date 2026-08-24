import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import AuthLayout from '../components/AuthLayout'

export default function ResetPassword() {
  const token = new URLSearchParams(window.location.search).get('token') || ''
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    if (!token) return setError('This reset link is invalid. Please request a new one.')
    if (newPassword !== confirmPassword) return setError('Passwords do not match.')
    if (newPassword.length < 8) return setError('Password must be at least 8 characters.')
    setLoading(true)
    try {
      await api.post('/auth/reset-password', { token, newPassword })
      setDone(true)
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'This link may have expired. Please request a new one.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout heading={done ? 'Password updated' : 'Set a new password'} copy={done ? 'You can now sign in with your new password.' : 'Choose a new password for your account.'}>
      {done ? <Link to="/login" className="block text-center text-sm text-ink font-medium">Go to sign in</Link> : (
        <form onSubmit={submit} className="space-y-4">
          <div><label className="block text-xs font-medium text-muted mb-1.5">New password</label><input type="password" required minLength="8" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm" /></div>
          <div><label className="block text-xs font-medium text-muted mb-1.5">Confirm password</label><input type="password" required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm" /></div>
          {error && <p className="text-sm text-status-rejected">{error}</p>}
          <button disabled={loading} className="w-full bg-ink text-white text-sm font-medium py-2.5 rounded-lg hover:bg-ink-soft disabled:opacity-50">{loading ? 'Updating...' : 'Update password'}</button>
        </form>
      )}
    </AuthLayout>
  )
}