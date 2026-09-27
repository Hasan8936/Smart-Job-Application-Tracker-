import React, { useEffect, useState } from 'react'
import { ChevronLeft, LifeBuoy, Loader2, Plus, Send } from 'lucide-react'
import Layout from '../components/Layout'
import { createTicket, getMyTicket, listMyTickets, replyToTicket, SUPPORT_CATEGORIES } from '../api/support'

export const STATUS_STYLE = {
  OPEN: 'bg-status-appliedSoft text-status-applied',
  IN_PROGRESS: 'bg-status-interviewSoft text-status-interview',
  RESOLVED: 'bg-status-offerSoft text-status-offer',
  CLOSED: 'bg-mist text-muted',
}
export const statusLabel = s => (s || '').replace('_', ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())
const when = v => (v ? new Date(v).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '')

export function Conversation({ messages = [] }) {
  return (
    <ol className="space-y-3">
      {messages.map(m => (
        <li key={m.id} className={`rounded-xl p-3 text-sm ${m.fromAdmin ? 'bg-accent/5 border border-accent/20' : 'bg-paper border border-line'}`}>
          <div className="text-[11px] text-muted mb-1">{m.author || (m.fromAdmin ? 'Support team' : 'User')} · {when(m.createdAt)}</div>
          <p className="whitespace-pre-line text-ink">{m.body}</p>
        </li>
      ))}
    </ol>
  )
}

export default function Support() {
  const [tickets, setTickets] = useState([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ subject: '', category: 'BUG', description: '' })
  const [reply, setReply] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = () => listMyTickets().then(setTickets).catch(() => setError('Could not load your requests.')).finally(() => setLoading(false))
  useEffect(() => { load() }, [])

  async function submit(e) {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const t = await createTicket(form)
      setForm({ subject: '', category: 'BUG', description: '' })
      setCreating(false)
      await load()
      setOpen(await getMyTicket(t.id))
    } catch (err) { setError(err.response?.data?.error || 'Could not send your request.') }
    finally { setBusy(false) }
  }

  async function sendReply(e) {
    e.preventDefault()
    if (!reply.trim()) return
    setBusy(true); setError('')
    try { setOpen(await replyToTicket(open.id, reply)); setReply(''); load() }
    catch (err) { setError(err.response?.data?.error || 'Could not send your reply.') }
    finally { setBusy(false) }
  }

  const input = 'w-full px-3 py-2 rounded-lg border border-line bg-paper focus:bg-surface text-sm'

  return (
    <Layout title="Help & support" subtitle="Ask a question or report a problem — we'll reply here and by email."
      actions={!creating && !open && (
        <button onClick={() => setCreating(true)} className="inline-flex items-center gap-1.5 btn-gradient rounded-full px-4 py-2 text-sm font-medium">
          <Plus size={15} /> New request
        </button>
      )}>
      <div className="max-w-3xl space-y-4">
        {error && <p className="text-sm text-status-rejected">{error}</p>}

        {creating && (
          <form onSubmit={submit} className="bg-surface border border-line rounded-xl p-5 space-y-3">
            <h2 className="font-display text-base">New support request</h2>
            <input className={input} placeholder="Subject" maxLength={200} required value={form.subject}
              onChange={e => setForm(f => ({ ...f, subject: e.target.value }))} />
            <select className={input} value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))}>
              {SUPPORT_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
            <textarea className={input} rows={5} maxLength={5000} required placeholder="What happened? Include steps to reproduce if it's a bug."
              value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
            <div className="flex gap-2">
              <button disabled={busy} className="inline-flex items-center gap-1.5 btn-gradient rounded-full px-4 py-2 text-sm font-medium disabled:opacity-50">
                {busy && <Loader2 size={14} className="animate-spin" />} Send request
              </button>
              <button type="button" onClick={() => setCreating(false)} className="border border-line rounded-full px-4 py-2 text-sm">Cancel</button>
            </div>
          </form>
        )}

        {open ? (
          <div className="bg-surface border border-line rounded-xl p-5">
            <button onClick={() => setOpen(null)} className="inline-flex items-center gap-1 text-xs text-muted hover:text-ink mb-3"><ChevronLeft size={14} /> All requests</button>
            <div className="flex items-start justify-between gap-3 mb-4">
              <div>
                <h2 className="font-display text-lg text-ink">{open.subject}</h2>
                <p className="text-xs text-muted">#{open.id} · {SUPPORT_CATEGORIES.find(c => c.value === open.category)?.label || open.category}</p>
              </div>
              <span className={`text-[11px] font-medium px-2 py-1 rounded-full ${STATUS_STYLE[open.status] || ''}`}>{statusLabel(open.status)}</span>
            </div>
            <Conversation messages={open.messages} />
            {open.status !== 'CLOSED' ? (
              <form onSubmit={sendReply} className="mt-4 flex gap-2">
                <textarea className={input} rows={2} maxLength={5000} placeholder="Write a reply…" value={reply} onChange={e => setReply(e.target.value)} />
                <button disabled={busy || !reply.trim()} className="self-end inline-flex items-center gap-1.5 btn-gradient rounded-full px-3 py-2 text-sm disabled:opacity-50" aria-label="Send reply">
                  {busy ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
                </button>
              </form>
            ) : <p className="text-xs text-muted mt-4">This request is closed. Open a new one if you need more help.</p>}
          </div>
        ) : !creating && (
          loading ? <Loader2 size={18} className="animate-spin text-muted" /> :
          tickets.length === 0 ? (
            <div className="text-center py-12 border border-dashed border-line rounded-xl">
              <LifeBuoy size={28} className="mx-auto text-muted mb-2" />
              <p className="text-sm text-muted">No support requests yet.</p>
            </div>
          ) : (
            <ul className="bg-surface border border-line rounded-xl divide-y divide-line">
              {tickets.map(t => (
                <li key={t.id}>
                  <button onClick={() => getMyTicket(t.id).then(setOpen)} className="w-full text-left px-4 py-3 hover:bg-mist flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm text-ink truncate">{t.subject}</p>
                      <p className="text-[11px] text-muted">#{t.id} · updated {when(t.updatedAt)}</p>
                    </div>
                    <span className={`shrink-0 text-[11px] font-medium px-2 py-1 rounded-full ${STATUS_STYLE[t.status] || ''}`}>{statusLabel(t.status)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )
        )}
      </div>
    </Layout>
  )
}
