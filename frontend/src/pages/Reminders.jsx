import React, { useEffect, useState } from 'react'
import { Plus, Trash2, BellRing, Settings2, Calendar, CheckCircle2, XCircle, MessageCircle } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import ComingSoonModal from '../components/ComingSoonModal'
import { getCalendarStatus, getCalendarConnectUrl, disconnectCalendar } from '../api/calendar'

const TYPE_LABEL = { INTERVIEW: 'Interview', ASSESSMENT: 'Assessment', DEADLINE: 'Deadline', FOLLOW_UP: 'Follow up' }

const emptyForm = { type: 'INTERVIEW', eventAt: '', eventKey: '', applicationId: '', message: '' }
const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

function offsetText(values) { return (values || []).join(', ') }
function parseOffsets(value) { return value.split(',').map(Number).filter((item) => Number.isInteger(item) && item >= 0) }

export default function Reminders() {
  const [reminders, setReminders] = useState([])
  const [loading, setLoading] = useState(true)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [preferences, setPreferences] = useState(null)
  const [preferenceSaving, setPreferenceSaving] = useState(false)
  const [reminderError, setReminderError] = useState('')
  const [whatsappInfo, setWhatsappInfo] = useState(false)
  const [calendarStatus, setCalendarStatus] = useState(null)
  const [calendarBusy, setCalendarBusy] = useState(false)
  const [calendarMsg, setCalendarMsg] = useState('')

  function errorMessage(e, fallback) {
    return e?.response?.data?.error || fallback
  }

  useEffect(() => {
    fetchReminders(); fetchPreferences(); fetchCalendarStatus()
    // Handle redirect back from Google OAuth
    const params = new URLSearchParams(window.location.search)
    const cal = params.get('calendar')
    if (cal === 'connected') { setCalendarMsg('Google Calendar connected! New reminders will create calendar events.'); fetchCalendarStatus() }
    else if (cal === 'error') setCalendarMsg('Could not connect Google Calendar. Please try again.')
    if (cal) window.history.replaceState({}, '', window.location.pathname)
  }, [])

  async function fetchCalendarStatus() {
    try { const res = await getCalendarStatus(); setCalendarStatus(res.data) } catch (e) { console.error(e) }
  }

  async function connectCalendar() {
    setCalendarBusy(true); setCalendarMsg('')
    try {
      const res = await getCalendarConnectUrl()
      window.location.href = res.data.url
    } catch (e) {
      setCalendarMsg(e?.response?.data?.error || 'Could not start Google Calendar connection.')
      setCalendarBusy(false)
    }
  }

  async function handleDisconnect() {
    setCalendarBusy(true); setCalendarMsg('')
    try { await disconnectCalendar(); setCalendarMsg('Google Calendar disconnected.'); fetchCalendarStatus() }
    catch (e) { setCalendarMsg('Could not disconnect.') }
    finally { setCalendarBusy(false) }
  }

  async function fetchReminders() {
    try {
      setLoading(true)
      const res = await api.get('/reminders/upcoming')
      setReminders(res.data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  async function fetchPreferences() {
    try {
      const res = await api.get('/reminders/preferences')
      setPreferences(res.data)
    } catch (e) { console.error(e) }
  }

  async function create(e) {
    e.preventDefault()
    if (!form.eventAt) return
    try {
      setSaving(true)
      setReminderError('')
      await api.post('/reminders/schedule', {
        type: form.type,
        message: form.message,
        eventAt: form.eventAt,
        timezone: preferences?.timezone || browserTimezone,
        eventKey: form.eventKey || undefined,
        applicationId: form.applicationId ? Number(form.applicationId) : undefined,
      })
      setForm(emptyForm)
      fetchReminders()
    } catch (e) {
      console.error(e)
      setReminderError(errorMessage(e, 'Could not save this reminder. Try again.'))
    } finally {
      setSaving(false)
    }
  }

  async function savePreferences(e) {
    e.preventDefault()
    try {
      setPreferenceSaving(true)
      await api.put('/reminders/preferences', preferences)
      fetchPreferences()
    } catch (e) { console.error(e) } finally { setPreferenceSaving(false) }
  }

  async function remove(id) {
    try {
      await api.delete(`/reminders/${id}`)
      fetchReminders()
    } catch (e) {
      console.error(e)
    }
  }

  return (
    <Layout title="Reminders" subtitle="Follow-ups and interviews you don't want to miss">
      <div className="grid lg:grid-cols-3 gap-6">
        <section className="bg-surface border border-line rounded-xl2 shadow-card p-5 lg:col-span-1 h-fit">
          <h2 className="font-display text-[15px] text-ink mb-3">Schedule event reminders</h2>
          <form onSubmit={create} className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-muted mb-1.5">Type</label>
              <select
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm"
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value })}
              >
                {Object.entries(TYPE_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-muted mb-1.5">Event date and time ({browserTimezone})</label>
              <input
                required
                type="datetime-local"
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm"
                value={form.eventAt}
                onChange={(e) => setForm({ ...form, eventAt: e.target.value })}
              />
            </div>
            <input className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm" placeholder="Event key (optional)" value={form.eventKey} onChange={(e) => setForm({ ...form, eventKey: e.target.value })} />
            <div>
              <label className="block text-xs font-medium text-muted mb-1.5">Note</label>
              <textarea
                rows={3}
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm resize-none"
                placeholder="e.g. Follow up with Google recruiter"
                value={form.message}
                onChange={(e) => setForm({ ...form, message: e.target.value })}
              />
            </div>
            {reminderError && <p className="text-sm text-status-rejected">{reminderError}</p>}
            <button
              disabled={saving}
              className="w-full inline-flex items-center justify-center gap-1.5 btn-gradient text-sm font-medium px-4 py-2.5 rounded-full disabled:opacity-50"
            >
              <Plus size={15} /> {saving ? 'Saving…' : 'Add reminder'}
            </button>
          </form>
        </section>

        <section className="lg:col-span-2">
          {loading ? (
            <div className="space-y-3 animate-pulse">
              {[0, 1, 2].map((i) => (
                <div key={i} className="bg-surface border border-line rounded-xl2 p-4 flex items-start justify-between gap-4">
                  <div className="flex-1 space-y-2">
                    <div className="flex items-center gap-2">
                      <div className="h-5 w-20 bg-paper rounded-full" />
                      <div className="h-3 w-28 bg-paper rounded" />
                    </div>
                    <div className="h-3 w-48 bg-paper rounded" />
                  </div>
                  <div className="h-8 w-8 rounded-full bg-paper shrink-0" />
                </div>
              ))}
            </div>
          ) : reminders.length === 0 ? (
            <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center">
              <BellRing className="mx-auto text-muted mb-2" size={22} />
              <p className="font-display text-ink text-lg mb-1">No reminders set</p>
              <p className="text-sm text-muted">Add one so a follow-up never slips through.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {reminders.map((r) => (
                <div key={r.id} className="bg-surface border border-line rounded-xl2 p-4 flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="px-2 py-0.5 rounded-full bg-status-interviewSoft text-status-interview text-xs font-medium">
                        {TYPE_LABEL[r.type] || r.type}
                      </span>
                      <span className="text-xs text-muted font-mono">
                        {new Date(r.remindAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                      </span>
                    </div>
                    {r.message && <p className="mt-2 text-sm text-ink">{r.message}</p>}
                  </div>
                  <button
                    onClick={() => remove(r.id)}
                    className="h-11 w-11 sm:h-8 sm:w-8 shrink-0 rounded-full border border-line flex items-center justify-center text-muted hover:text-status-rejected hover:border-status-rejected/40"
                    aria-label="Delete reminder"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
      {preferences && <section className="mt-6 bg-surface border border-line rounded-xl2 shadow-card p-5">
        <div className="flex items-center gap-2 mb-3"><Settings2 size={16} /><h2 className="font-display text-[15px] text-ink">Reminder preferences</h2></div>
        <form onSubmit={savePreferences} className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <label className="sm:col-span-2 text-xs font-medium text-muted">Timezone<input className="mt-1 w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm text-ink" value={preferences.timezone} onChange={(e) => setPreferences({ ...preferences, timezone: e.target.value })} /></label>
          {[['interviewsEnabled', 'Interviews'], ['assessmentsEnabled', 'Assessments'], ['deadlinesEnabled', 'Deadlines'], ['followUpsEnabled', 'Follow-ups']].map(([key, label]) => <label key={key} className="flex items-center gap-2 text-sm text-ink"><input type="checkbox" checked={preferences[key]} onChange={(e) => setPreferences({ ...preferences, [key]: e.target.checked })} />{label}</label>)}
          {[['interviewOffsetsHours', 'Interview offsets (hours)'], ['assessmentOffsetsHours', 'Assessment offsets (hours)'], ['deadlineOffsetsHours', 'Deadline offsets (hours)'], ['followUpOffsetsHours', 'Follow-up offsets (hours)']].map(([key, label]) => <label key={key} className="text-xs font-medium text-muted">{label}<input className="mt-1 w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm text-ink" value={offsetText(preferences[key])} onChange={(e) => setPreferences({ ...preferences, [key]: parseOffsets(e.target.value) })} /></label>)}
          <button disabled={preferenceSaving} className="sm:col-span-2 lg:col-span-4 w-fit inline-flex items-center gap-1.5 btn-gradient text-sm font-medium px-4 py-2.5 rounded-full disabled:opacity-50">{preferenceSaving ? 'Saving...' : 'Save preferences'}</button>
        </form>
      </section>}
      {/* WhatsApp reminders aren't live yet: explain instead of showing a form that can't deliver. */}
      <section className="mt-6 bg-surface border border-line rounded-xl2 shadow-card p-5">
        <div className="flex items-center gap-2 mb-2">
          <MessageCircle size={16} /><h2 className="font-display text-[15px] text-ink">WhatsApp notifications</h2>
          <span className="text-[11px] font-semibold uppercase tracking-wide text-violet-600 bg-violet-500/10 border border-violet-400/40 rounded-full px-2 py-0.5">Coming soon</span>
        </div>
        <p className="text-sm text-muted mb-3">Get interview and follow-up reminders on WhatsApp. Email reminders work today.</p>
        <button type="button" onClick={() => setWhatsappInfo(true)} className="border border-line text-ink-soft text-sm font-medium px-4 py-2.5 rounded-full hover:border-ink/30">Set up WhatsApp</button>
      </section>
      <ComingSoonModal open={whatsappInfo} onClose={() => setWhatsappInfo(false)} icon={MessageCircle} title="WhatsApp reminders are coming soon"
        points={['Interview, assessment and follow-up reminders on WhatsApp', 'Opt-in only, with a verified phone number', 'Turn it off any time']}>
        We're finishing WhatsApp delivery. Until then your reminders arrive by email, and you can connect Google Calendar below.
      </ComingSoonModal>

      {calendarStatus && (
        <section className="mt-6 bg-surface border border-line rounded-xl2 shadow-card p-5">
          <div className="flex items-center gap-2 mb-3">
            <Calendar size={16} />
            <h2 className="font-display text-[15px] text-ink">Google Calendar</h2>
          </div>

          {!calendarStatus.configured ? (
            <p className="text-sm text-muted">
              Google Calendar integration is not configured on this server.
              Set <code className="bg-paper px-1 py-0.5 rounded text-xs">GOOGLE_CLIENT_ID</code> and{' '}
              <code className="bg-paper px-1 py-0.5 rounded text-xs">GOOGLE_CLIENT_SECRET</code> to enable it.
            </p>
          ) : calendarStatus.connected ? (
            <div className="flex flex-wrap items-center gap-3">
              <span className="inline-flex items-center gap-1.5 text-sm text-status-offer">
                <CheckCircle2 size={15} />Connected — new reminders will add events to your Google Calendar
              </span>
              <button
                type="button"
                disabled={calendarBusy}
                onClick={handleDisconnect}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-line text-xs font-medium text-muted hover:text-status-rejected hover:border-status-rejected/40 transition-colors disabled:opacity-50"
              >
                <XCircle size={13} />Disconnect
              </button>
            </div>
          ) : (
            <div className="space-y-2">
              <p className="text-sm text-muted">
                Connect your Google Calendar to automatically create calendar events with popup and email alerts whenever you schedule a reminder here.
              </p>
              <button
                type="button"
                disabled={calendarBusy}
                onClick={connectCalendar}
                className="inline-flex items-center gap-1.5 btn-gradient text-sm font-medium px-4 py-2.5 rounded-full disabled:opacity-50"
              >
                <Calendar size={15} />{calendarBusy ? 'Redirecting…' : 'Connect Google Calendar'}
              </button>
            </div>
          )}

          {calendarMsg && (
            <p className={`mt-2 text-sm ${calendarMsg.includes('not') || calendarMsg.includes('Could') ? 'text-status-rejected' : 'text-status-offer'}`}>
              {calendarMsg}
            </p>
          )}
        </section>
      )}
    </Layout>
  )
}
