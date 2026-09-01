import React, { useEffect, useState } from 'react'
import { Plus, Trash2, BellRing, Settings2 } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'

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
  const [whatsapp, setWhatsapp] = useState(null)
  const [verificationCode, setVerificationCode] = useState('')
  const [deliveries, setDeliveries] = useState([])
  const [reminderError, setReminderError] = useState('')
  const [whatsappError, setWhatsappError] = useState('')

  function errorMessage(e, fallback) {
    return e?.response?.data?.error || fallback
  }

  useEffect(() => { fetchReminders(); fetchPreferences(); fetchWhatsapp(); fetchDeliveries() }, [])

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

  async function fetchWhatsapp() {
    try { const res = await api.get('/notifications/preferences'); setWhatsapp(res.status === 204 ? { phoneE164: '', whatsappOptIn: false } : res.data) } catch (e) { console.error(e) }
  }

  async function fetchDeliveries() {
    try { const res = await api.get('/notifications/history'); setDeliveries(res.data) } catch (e) { console.error(e) }
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

  async function saveWhatsapp(e) {
    e.preventDefault()
    setWhatsappError('')
    try { await api.put('/notifications/preferences', { ...whatsapp, consentSource: 'settings' }); fetchWhatsapp() }
    catch (e) { console.error(e); setWhatsappError(errorMessage(e, 'Could not save WhatsApp consent. Try again.')) }
  }

  async function startVerification() {
    setWhatsappError('')
    try { await api.post('/notifications/preferences/verify') }
    catch (e) { console.error(e); setWhatsappError(errorMessage(e, 'Could not send a verification code. Try again.')) }
  }

  async function confirmVerification(e) {
    e.preventDefault()
    setWhatsappError('')
    try { await api.post('/notifications/preferences/confirm', { code: verificationCode }); setVerificationCode(''); fetchWhatsapp() }
    catch (e) { console.error(e); setWhatsappError(errorMessage(e, 'Could not verify this code. Try again.')) }
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
              className="w-full inline-flex items-center justify-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2.5 rounded-lg disabled:opacity-50"
            >
              <Plus size={15} /> {saving ? 'Saving…' : 'Add reminder'}
            </button>
          </form>
        </section>

        <section className="lg:col-span-2">
          {loading ? (
            <div className="text-sm text-muted">Loading reminders…</div>
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
                    className="h-11 w-11 sm:h-8 sm:w-8 shrink-0 rounded-lg border border-line flex items-center justify-center text-muted hover:text-status-rejected hover:border-status-rejected/40"
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
          <button disabled={preferenceSaving} className="sm:col-span-2 lg:col-span-4 w-fit inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2.5 rounded-lg disabled:opacity-50">{preferenceSaving ? 'Saving...' : 'Save preferences'}</button>
        </form>
      </section>}
      {whatsapp && <section className="mt-6 bg-surface border border-line rounded-xl2 shadow-card p-5">
        <div className="flex items-center gap-2 mb-3"><BellRing size={16} /><h2 className="font-display text-[15px] text-ink">WhatsApp notifications</h2></div>
        <form onSubmit={saveWhatsapp} className="grid sm:grid-cols-2 gap-3">
          <label className="text-xs font-medium text-muted">Phone number (E.164)<input required pattern="\\+[1-9][0-9]{7,14}" className="mt-1 w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm text-ink" value={whatsapp.phoneE164 || ''} onChange={(e) => setWhatsapp({ ...whatsapp, phoneE164: e.target.value })} placeholder="+15551234567" /></label>
          <label className="flex items-center gap-2 text-sm text-ink sm:pt-6"><input type="checkbox" checked={Boolean(whatsapp.whatsappOptIn)} onChange={(e) => setWhatsapp({ ...whatsapp, whatsappOptIn: e.target.checked })} />I agree to receive Smart Job Tracker WhatsApp notifications.</label>
          <button className="w-fit inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2.5 rounded-lg">Save consent</button>
          {whatsappError && <p className="sm:col-span-2 text-sm text-status-rejected">{whatsappError}</p>}
        </form>
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <button type="button" onClick={startVerification} disabled={!whatsapp.whatsappOptIn} className="border border-line text-ink text-sm font-medium px-4 py-2.5 rounded-lg disabled:opacity-50">Send verification code</button>
          <form onSubmit={confirmVerification} className="flex gap-2"><input required pattern="[0-9]{6}" maxLength="6" className="w-32 px-3 py-2 rounded-lg border border-line bg-paper text-sm" placeholder="6-digit code" value={verificationCode} onChange={(e) => setVerificationCode(e.target.value)} /><button className="border border-line text-ink text-sm font-medium px-4 py-2.5 rounded-lg">Verify number</button></form>
          <span className="text-xs text-muted">{whatsapp.verifiedAt ? 'Number verified' : 'Verification required before sending'}</span>
        </div>
        {deliveries.length > 0 && <div className="mt-5 space-y-2"><h3 className="text-xs font-medium text-muted">Delivery history</h3>{deliveries.slice(0, 10).map((delivery) => <div key={delivery.id} className="flex items-center justify-between gap-3 border-t border-line pt-2 text-sm"><span className="truncate text-ink">{delivery.message}</span><span className="shrink-0 text-xs text-muted">{delivery.status === 'DELIVERED' || delivery.status === 'READ' ? delivery.status : `Not confirmed: ${delivery.status}`}</span></div>)}</div>}
      </section>}
    </Layout>
  )
}
