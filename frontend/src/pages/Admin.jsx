import React, { useEffect, useState } from 'react'
import { Ban, CheckCircle2, ChevronLeft, Eye, Flag, Loader2, Search, Send, ShieldAlert, X } from 'lucide-react'
import Layout from '../components/Layout'
import { Conversation, STATUS_STYLE, statusLabel } from './Support'
import {
  adminReply, FLAG_CATEGORIES, flagUser, getTicket, getUser, listAudit, listTickets, resolveFlag, searchUsers,
  SEVERITIES, suspendUser, TICKET_STATUSES, unsuspendUser, updateTicket, viewUserResume,
} from '../api/admin'

const when = v => (v ? new Date(v).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—')
const input = 'px-3 py-2 rounded-lg border border-line bg-paper focus:bg-surface text-sm'
const SEVERITY_STYLE = { LOW: 'text-muted', MEDIUM: 'text-status-screening', HIGH: 'text-status-rejected', CRITICAL: 'text-status-rejected font-semibold' }
const err = (e, fallback) => e?.response?.data?.error || fallback

function Pager({ page, onPage }) {
  if (!page || page.totalPages <= 1) return null
  return (
    <div className="flex items-center justify-end gap-2 mt-3 text-xs text-muted">
      <button disabled={page.first} onClick={() => onPage(page.number - 1)} className="px-2 py-1 border border-line rounded disabled:opacity-40">Prev</button>
      <span>Page {page.number + 1} of {page.totalPages}</span>
      <button disabled={page.last} onClick={() => onPage(page.number + 1)} className="px-2 py-1 border border-line rounded disabled:opacity-40">Next</button>
    </div>
  )
}

// ─── Users ────────────────────────────────────────────────────────────────────

function UserDetail({ id, onBack, onOpenTicket }) {
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')
  const [flag, setFlag] = useState({ category: 'OTHER', severity: 'MEDIUM', note: '' })
  const [busy, setBusy] = useState(false)
  const [resume, setResume] = useState(null)

  const load = () => getUser(id).then(setUser).catch(e => setError(err(e, 'Could not load this user.')))
  useEffect(() => { load() }, [id])

  async function act(fn) {
    setBusy(true); setError('')
    try { await fn(); await load() } catch (e) { setError(err(e, 'Action failed.')) } finally { setBusy(false) }
  }

  if (!user) return error ? <p className="text-sm text-status-rejected">{error}</p> : <Loader2 size={18} className="animate-spin text-muted" />

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="inline-flex items-center gap-1 text-xs text-muted hover:text-ink"><ChevronLeft size={14} /> All users</button>
      {error && <p className="text-sm text-status-rejected">{error}</p>}

      <section className="bg-surface border border-line rounded-xl p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="font-display text-lg text-ink">{user.name || 'No name'} {user.role === 'ADMIN' && <span className="text-[11px] px-2 py-0.5 rounded-full bg-accent text-white align-middle">Admin</span>}</h2>
            <p className="text-sm text-muted">{user.email} · #{user.id}</p>
          </div>
          {user.role !== 'ADMIN' && (user.suspended ? (
            <button disabled={busy} onClick={() => act(() => unsuspendUser(user.id))} className="inline-flex items-center gap-1.5 border border-line rounded-full px-3 py-1.5 text-sm">
              <CheckCircle2 size={14} /> Unsuspend
            </button>
          ) : (
            <button disabled={busy} onClick={() => {
              const reason = window.prompt('Reason for suspending this account (shown to admins only):', '')
              if (reason !== null) act(() => suspendUser(user.id, reason))
            }} className="inline-flex items-center gap-1.5 bg-status-rejected text-white rounded-full px-3 py-1.5 text-sm">
              <Ban size={14} /> Suspend
            </button>
          ))}
        </div>
        {user.suspended && <p className="mt-2 text-xs text-status-rejected flex items-center gap-1"><ShieldAlert size={13} /> Suspended{user.suspendedReason ? `: ${user.suspendedReason}` : ''}</p>}
        <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-sm">
          <div><dt className="text-xs text-muted">Joined</dt><dd>{when(user.createdAt)}</dd></div>
          <div><dt className="text-xs text-muted">Last login</dt><dd>{when(user.lastLoginAt)}</dd></div>
          <div><dt className="text-xs text-muted">Applications</dt><dd>{user.applicationsCount}</dd></div>
          <div><dt className="text-xs text-muted">Resumes</dt><dd>{user.resumesCount}</dd></div>
          <div><dt className="text-xs text-muted">Sign-in</dt><dd>{user.passwordSet ? 'Password' : 'Google only'}</dd></div>
          <div><dt className="text-xs text-muted">Gmail</dt><dd>{user.gmailConnected ? 'Connected' : 'Not connected'}</dd></div>
        </dl>
      </section>

      <section className="bg-surface border border-line rounded-xl p-5">
        <h3 className="font-display text-base mb-3">Resumes</h3>
        {user.resumes.length === 0 ? <p className="text-xs text-muted">No resumes.</p> : (
          <ul className="space-y-2">
            {user.resumes.map(r => (
              <li key={r.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate">{r.fileName || `Resume ${r.id}`} <span className="text-xs text-muted">· {when(r.uploadedAt)}</span></span>
                <button onClick={() => {
                  if (window.confirm('Viewing resume text is recorded in the audit log. Continue?'))
                    viewUserResume(user.id, r.id).then(setResume).catch(e => setError(err(e, 'Could not open the resume.')))
                }} className="shrink-0 inline-flex items-center gap-1 text-xs text-accent hover:underline"><Eye size={13} /> View text</button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="bg-surface border border-line rounded-xl p-5">
        <h3 className="font-display text-base mb-3">Flags</h3>
        <form onSubmit={e => { e.preventDefault(); act(async () => { await flagUser(user.id, flag); setFlag({ category: 'OTHER', severity: 'MEDIUM', note: '' }) }) }}
          className="flex flex-wrap gap-2 mb-4">
          <select className={input} value={flag.category} onChange={e => setFlag(f => ({ ...f, category: e.target.value }))}>
            {FLAG_CATEGORIES.map(c => <option key={c}>{c}</option>)}
          </select>
          <select className={input} value={flag.severity} onChange={e => setFlag(f => ({ ...f, severity: e.target.value }))}>
            {SEVERITIES.map(s => <option key={s}>{s}</option>)}
          </select>
          <input className={`${input} flex-1 min-w-[12rem]`} placeholder="Note" maxLength={2000} value={flag.note}
            onChange={e => setFlag(f => ({ ...f, note: e.target.value }))} />
          <button disabled={busy} className="inline-flex items-center gap-1.5 btn-gradient rounded-full px-3 py-2 text-sm"><Flag size={14} /> Add flag</button>
        </form>
        {user.flags.length === 0 ? <p className="text-xs text-muted">No flags.</p> : (
          <ul className="divide-y divide-line">
            {user.flags.map(f => (
              <li key={f.id} className="py-2 flex items-start justify-between gap-3 text-sm">
                <div>
                  <span className={SEVERITY_STYLE[f.severity]}>{f.severity}</span> · {f.category}
                  <span className={`ml-2 text-[11px] px-1.5 py-0.5 rounded-full ${f.status === 'OPEN' ? 'bg-status-rejectedSoft text-status-rejected' : 'bg-mist text-muted'}`}>{f.status}</span>
                  {f.note && <p className="text-xs text-ink-soft mt-0.5">{f.note}</p>}
                  <p className="text-[11px] text-muted">{when(f.createdAt)}{f.resolvedAt ? ` · resolved ${when(f.resolvedAt)}` : ''}</p>
                </div>
                {f.status === 'OPEN' && <button disabled={busy} onClick={() => act(() => resolveFlag(f.id))} className="shrink-0 text-xs text-accent hover:underline">Resolve</button>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="bg-surface border border-line rounded-xl p-5">
        <h3 className="font-display text-base mb-3">Support requests</h3>
        {user.tickets.length === 0 ? <p className="text-xs text-muted">None.</p> : (
          <ul className="space-y-1">
            {user.tickets.map(t => (
              <li key={t.id}><button onClick={() => onOpenTicket(t.id)} className="text-sm text-left hover:underline">#{t.id} {t.subject} <span className="text-xs text-muted">· {statusLabel(t.status)}</span></button></li>
            ))}
          </ul>
        )}
      </section>

      {resume && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <button aria-label="Close" className="absolute inset-0 bg-ink/50" onClick={() => setResume(null)} />
          <div className="relative w-full max-w-2xl max-h-[85vh] overflow-y-auto bg-surface rounded-xl2 shadow-card p-6">
            <button onClick={() => setResume(null)} className="absolute top-4 right-4 text-muted" aria-label="Close"><X size={16} /></button>
            <h3 className="font-display text-base mb-1">{resume.fileName}</h3>
            <p className="text-[11px] text-muted mb-3">This view was recorded in the audit log.</p>
            <pre className="whitespace-pre-wrap text-xs text-ink-soft">{resume.extractedText || '(no text)'}</pre>
          </div>
        </div>
      )}
    </div>
  )
}

function UsersTab({ onOpenTicket }) {
  const [q, setQ] = useState('')
  const [query, setQuery] = useState({ q: '', page: 0 })
  const [page, setPage] = useState(null)
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    searchUsers({ q: query.q || undefined, page: query.page, size: 20 }).then(setPage).catch(e => setError(err(e, 'Could not load users.')))
  }, [query])

  if (selected) return <UserDetail id={selected} onBack={() => { setSelected(null); setQuery(x => ({ ...x })) }} onOpenTicket={onOpenTicket} />
  return (
    <div>
      <form onSubmit={e => { e.preventDefault(); setQuery({ q, page: 0 }) }} className="flex gap-2 mb-3">
        <div className="relative flex-1">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input className={`${input} w-full pl-9`} placeholder="Search by email or name" value={q} onChange={e => setQ(e.target.value)} />
        </div>
        <button className="btn-gradient rounded-full px-4 text-sm">Search</button>
      </form>
      {error && <p className="text-sm text-status-rejected">{error}</p>}
      {!page ? <Loader2 size={18} className="animate-spin text-muted" /> : (
        <div className="bg-surface border border-line rounded-xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-xs text-muted text-left"><tr>
              <th className="px-4 py-2">User</th><th className="px-4 py-2">Joined</th><th className="px-4 py-2">Last login</th><th className="px-4 py-2">Status</th>
            </tr></thead>
            <tbody className="divide-y divide-line">
              {page.content.map(u => (
                <tr key={u.id} onClick={() => setSelected(u.id)} className="cursor-pointer hover:bg-mist">
                  <td className="px-4 py-2"><div className="text-ink">{u.name || '—'}{u.role === 'ADMIN' && <span className="ml-1 text-[10px] text-accent">ADMIN</span>}</div><div className="text-xs text-muted">{u.email}</div></td>
                  <td className="px-4 py-2 text-xs">{when(u.createdAt)}</td>
                  <td className="px-4 py-2 text-xs">{when(u.lastLoginAt)}</td>
                  <td className="px-4 py-2 text-xs">
                    {u.suspended ? <span className="text-status-rejected">Suspended</span> : 'Active'}
                    {u.openFlags > 0 && <span className="ml-2 inline-flex items-center gap-0.5 text-status-rejected"><Flag size={11} />{u.openFlags}</span>}
                  </td>
                </tr>
              ))}
              {page.content.length === 0 && <tr><td colSpan={4} className="px-4 py-6 text-center text-xs text-muted">No users found.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      <Pager page={page} onPage={p => setQuery(x => ({ ...x, page: p }))} />
    </div>
  )
}

// ─── Tickets ──────────────────────────────────────────────────────────────────

function TicketDetail({ id, onBack }) {
  const [ticket, setTicket] = useState(null)
  const [reply, setReply] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { getTicket(id).then(setTicket).catch(e => setError(err(e, 'Could not load this request.'))) }, [id])

  async function act(fn) {
    setBusy(true); setError('')
    try { setTicket(await fn()) } catch (e) { setError(err(e, 'Action failed.')) } finally { setBusy(false) }
  }

  if (!ticket) return error ? <p className="text-sm text-status-rejected">{error}</p> : <Loader2 size={18} className="animate-spin text-muted" />
  return (
    <div className="bg-surface border border-line rounded-xl p-5">
      <button onClick={onBack} className="inline-flex items-center gap-1 text-xs text-muted hover:text-ink mb-3"><ChevronLeft size={14} /> All requests</button>
      <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
        <div>
          <h2 className="font-display text-lg">{ticket.subject}</h2>
          <p className="text-xs text-muted">#{ticket.id} · {ticket.category} · {ticket.userEmail} · assigned to {ticket.assignedAdminEmail || 'nobody'}</p>
        </div>
        <div className="flex items-center gap-2">
          <select disabled={busy} className={input} value={ticket.status} onChange={e => act(() => updateTicket(ticket.id, { status: e.target.value }))}>
            {TICKET_STATUSES.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
          <button disabled={busy} onClick={() => act(() => updateTicket(ticket.id, { assign: true }))} className="border border-line rounded-full px-3 py-2 text-xs">Assign to me</button>
        </div>
      </div>
      {error && <p className="text-sm text-status-rejected mb-2">{error}</p>}
      <Conversation messages={ticket.messages} />
      <form onSubmit={e => { e.preventDefault(); if (reply.trim()) act(async () => { const t = await adminReply(ticket.id, reply); setReply(''); return t }) }} className="mt-4 flex gap-2">
        <textarea className={`${input} w-full`} rows={3} maxLength={5000} placeholder="Reply to the user (they'll also get an email)…" value={reply} onChange={e => setReply(e.target.value)} />
        <button disabled={busy || !reply.trim()} className="self-end inline-flex items-center gap-1.5 btn-gradient rounded-full px-3 py-2 text-sm disabled:opacity-50" aria-label="Send reply">
          {busy ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
        </button>
      </form>
    </div>
  )
}

function TicketsTab({ openId, setOpenId }) {
  const [status, setStatus] = useState('OPEN')
  const [pageNo, setPageNo] = useState(0)
  const [page, setPage] = useState(null)
  useEffect(() => {
    if (!openId) listTickets({ status: status || undefined, page: pageNo, size: 20 }).then(setPage).catch(() => setPage({ content: [] }))
  }, [status, pageNo, openId])

  if (openId) return <TicketDetail id={openId} onBack={() => setOpenId(null)} />
  return (
    <div>
      <div className="flex flex-wrap gap-2 mb-3">
        {['', ...TICKET_STATUSES].map(s => (
          <button key={s || 'ALL'} onClick={() => { setStatus(s); setPageNo(0) }}
            className={`px-3 py-1.5 rounded-full border text-xs ${status === s ? 'border-accent bg-accent/10 text-accent' : 'border-line text-ink-soft'}`}>
            {s ? statusLabel(s) : 'All'}
          </button>
        ))}
      </div>
      {!page ? <Loader2 size={18} className="animate-spin text-muted" /> : (
        <ul className="bg-surface border border-line rounded-xl divide-y divide-line">
          {page.content.map(t => (
            <li key={t.id}>
              <button onClick={() => setOpenId(t.id)} className="w-full text-left px-4 py-3 hover:bg-mist flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm text-ink truncate">#{t.id} {t.subject}</p>
                  <p className="text-[11px] text-muted">{t.userEmail} · {t.category} · updated {when(t.updatedAt)}{t.assignedAdminEmail ? ` · ${t.assignedAdminEmail}` : ''}</p>
                </div>
                <span className={`shrink-0 text-[11px] font-medium px-2 py-1 rounded-full ${STATUS_STYLE[t.status] || ''}`}>{statusLabel(t.status)}</span>
              </button>
            </li>
          ))}
          {page.content.length === 0 && <li className="px-4 py-6 text-center text-xs text-muted">No requests.</li>}
        </ul>
      )}
      <Pager page={page} onPage={setPageNo} />
    </div>
  )
}

// ─── Audit ────────────────────────────────────────────────────────────────────

function AuditTab() {
  const [pageNo, setPageNo] = useState(0)
  const [page, setPage] = useState(null)
  useEffect(() => { listAudit({ page: pageNo, size: 50 }).then(setPage).catch(() => setPage({ content: [] })) }, [pageNo])
  if (!page) return <Loader2 size={18} className="animate-spin text-muted" />
  return (
    <div className="bg-surface border border-line rounded-xl overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-xs text-muted text-left"><tr>
          <th className="px-4 py-2">When</th><th className="px-4 py-2">Admin</th><th className="px-4 py-2">Action</th><th className="px-4 py-2">User</th><th className="px-4 py-2">Detail</th>
        </tr></thead>
        <tbody className="divide-y divide-line">
          {page.content.map(a => (
            <tr key={a.id}>
              <td className="px-4 py-2 text-xs whitespace-nowrap">{when(a.createdAt)}</td>
              <td className="px-4 py-2 text-xs">{a.adminEmail || `#${a.adminId ?? '—'}`}</td>
              <td className="px-4 py-2 text-xs font-medium">{a.action}</td>
              <td className="px-4 py-2 text-xs">{a.targetUserId ? `#${a.targetUserId}` : '—'}</td>
              <td className="px-4 py-2 text-xs text-muted">{a.detail || ''}</td>
            </tr>
          ))}
          {page.content.length === 0 && <tr><td colSpan={5} className="px-4 py-6 text-center text-xs text-muted">No admin actions yet.</td></tr>}
        </tbody>
      </table>
      <Pager page={page} onPage={setPageNo} />
    </div>
  )
}

export default function Admin() {
  const [tab, setTab] = useState('users')
  const [openTicket, setOpenTicket] = useState(null)
  const tabs = [['users', 'Users'], ['tickets', 'Support requests'], ['audit', 'Audit log']]
  return (
    <Layout title="Admin" subtitle="Users, support requests and the audit trail">
      <div className="max-w-5xl">
        <div className="flex gap-1 mb-4 border-b border-line">
          {tabs.map(([key, label]) => (
            <button key={key} onClick={() => { setTab(key); if (key !== 'tickets') setOpenTicket(null) }}
              className={`px-4 py-2 text-sm -mb-px border-b-2 ${tab === key ? 'border-accent text-accent' : 'border-transparent text-ink-soft hover:text-ink'}`}>
              {label}
            </button>
          ))}
        </div>
        {tab === 'users' && <UsersTab onOpenTicket={id => { setOpenTicket(id); setTab('tickets') }} />}
        {tab === 'tickets' && <TicketsTab openId={openTicket} setOpenId={setOpenTicket} />}
        {tab === 'audit' && <AuditTab />}
      </div>
    </Layout>
  )
}
