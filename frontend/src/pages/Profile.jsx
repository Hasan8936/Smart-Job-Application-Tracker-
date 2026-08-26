import React, { useContext } from 'react'
import { CheckCircle2, Link2, Loader2, LogOut, Unlink } from 'lucide-react'
import Layout from '../components/Layout'
import { AuthContext } from '../context/AuthContext'
import { beginGmailConnect, disconnectGmail, getGmailReviewQueue, getGmailStatus, reviewGmailEmail, syncGmail } from '../api/gmail'
import api from '../api/axios'

export default function Profile() {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || 'Your account'
  const email = user?.profile?.email
  const [gmail, setGmail] = React.useState({ connected: false })
  const [gmailBusy, setGmailBusy] = React.useState(false)
  const [gmailMessage, setGmailMessage] = React.useState('')
  const [reviewQueue, setReviewQueue] = React.useState([])
  const [applications, setApplications] = React.useState([])
  React.useEffect(() => { getGmailStatus().then(status => { setGmail(status); if (status.connected) getGmailReviewQueue().then(setReviewQueue).catch(() => {}) }).catch(() => {}); api.get('/applications').then(response => setApplications(response.data)).catch(() => {}) }, [])
  async function connectGmail() { const result = await beginGmailConnect(); window.location.href = result.authorizationUrl }
  async function sync() { try { setGmailBusy(true); const result = await syncGmail(); setReviewQueue(await getGmailReviewQueue()); setGmailMessage(`${result.processed} new messages processed.`) } catch { setGmailMessage('Gmail sync failed. Try again.') } finally { setGmailBusy(false) } }
  async function disconnect() { try { setGmailBusy(true); await disconnectGmail(); setGmail({ connected: false }); setGmailMessage('Gmail disconnected.') } finally { setGmailBusy(false) } }

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

        <section className="border-t border-line pt-5 mb-6">
          <div className="flex items-center justify-between mb-2"><h2 className="font-display text-base">Gmail</h2>{gmail.connected && <span className="inline-flex items-center gap-1 text-xs text-status-offer"><CheckCircle2 size={13} /> Connected</span>}</div>
          <p className="text-sm text-muted mb-3">Read-only access for job email tracking.</p>
          {!gmail.connected ? <button onClick={connectGmail} className="inline-flex items-center gap-2 border border-line rounded-lg px-3 py-2.5 text-sm"><Link2 size={15} /> Connect Gmail</button> : <div className="flex flex-wrap gap-2"><button disabled={gmailBusy} onClick={sync} className="inline-flex items-center gap-2 bg-ink text-white rounded-lg px-3 py-2.5 text-sm">{gmailBusy && <Loader2 size={14} className="animate-spin" />} Sync job emails</button><button disabled={gmailBusy} onClick={disconnect} className="inline-flex items-center gap-2 border border-line rounded-lg px-3 py-2.5 text-sm"><Unlink size={15} /> Disconnect</button></div>}
          {gmailMessage && <p className="text-xs text-muted mt-2">{gmailMessage}</p>}
          {gmail.connected && reviewQueue.length > 0 && <div className="mt-5 border-t border-line pt-4"><h3 className="font-display text-sm mb-2">Needs your review</h3>{reviewQueue.map(email => <div key={email.id} className="border border-line rounded-lg p-3 mb-2"><p className="text-sm text-ink">{email.subject || 'Job email'}</p><p className="text-xs text-muted mt-1">{email.category || 'OTHER'} · {email.company || 'Company unavailable'} · confidence {email.confidence == null ? 'unavailable' : `${Math.round(email.confidence * 100)}%`}</p><p className="text-xs text-muted mt-1">Role: {email.jobTitle || 'Unavailable'} · Status: {email.extractedStatus || 'Unavailable'}</p><p className="text-xs text-muted mt-1">Interview: {email.interviewDate || 'Unavailable'} {email.interviewTime || ''} · Deadline: {email.deadline || 'Unavailable'} · Action: {email.actionRequired || 'None identified'}</p><div className="flex gap-2 mt-2"><select id={`gmail-app-${email.id}`} className="min-w-0 flex-1 px-2 py-2 rounded-lg border border-line bg-paper text-sm"><option value="">Select application</option>{applications.map(application => <option key={application.id} value={application.id}>{application.companyName} · {application.roleTitle}</option>)}</select><button onClick={async () => { const applicationId = document.getElementById(`gmail-app-${email.id}`).value; if (!applicationId) return; await reviewGmailEmail(email.id, applicationId, email.extractedStatus || 'APPLIED'); setReviewQueue(current => current.filter(item => item.id !== email.id)) }} className="px-3 py-2 rounded-lg bg-ink text-white text-xs">Confirm</button></div></div>)}</div>}
        </section>

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
