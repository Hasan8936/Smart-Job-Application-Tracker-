import React, { useContext } from 'react'
import { LogOut } from 'lucide-react'
import Layout from '../components/Layout'
import { AuthContext } from '../context/AuthContext'

export default function Profile() {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || 'Your account'
  const email = user?.profile?.email

  return (
    <Layout title="Profile" subtitle="Your account details">
      <div className="max-w-md bg-surface border border-line rounded-xl2 shadow-card p-6">
        <div className="flex items-center gap-3 mb-6">
          <span className="h-12 w-12 rounded-full bg-ink text-white flex items-center justify-center font-display text-lg">
            {name.slice(0, 1).toUpperCase()}
          </span>
          <div>
            <div className="font-display text-lg text-ink">{name}</div>
            {email && <div className="text-sm text-muted">{email}</div>}
          </div>
        </div>

        <div className="space-y-3 text-sm mb-6">
          <div className="flex justify-between py-2 border-b border-line">
            <span className="text-muted">Signed in</span>
            <span className="text-ink font-medium">{user ? 'Yes' : 'No'}</span>
          </div>
          <div className="py-2">
            <span className="text-muted block mb-1.5">Session token</span>
            <code className="block text-xs text-ink bg-paper border border-line rounded-lg px-3 py-2 break-all">
              {user?.token}
            </code>
          </div>
        </div>

        <button
          onClick={logout}
          className="w-full inline-flex items-center justify-center gap-1.5 bg-status-rejected text-white text-sm font-medium py-2.5 rounded-lg hover:opacity-90"
        >
          <LogOut size={15} /> Log out
        </button>
      </div>
    </Layout>
  )
}
