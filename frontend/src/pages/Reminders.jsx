import React, { useEffect, useState } from 'react'
import { Plus, Trash2, BellRing } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'

const TYPE_LABEL = { INTERVIEW: 'Interview', FOLLOW_UP: 'Follow up', CUSTOM: 'Custom' }

const emptyForm = { type: 'FOLLOW_UP', remindAt: '', message: '' }

export default function Reminders() {
  const [reminders, setReminders] = useState([])
  const [loading, setLoading] = useState(true)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)

  useEffect(() => { fetchReminders() }, [])

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

  async function create(e) {
    e.preventDefault()
    if (!form.remindAt) return
    try {
      setSaving(true)
      await api.post('/reminders', {
        type: form.type,
        message: form.message,
        remindAt: new Date(form.remindAt).toISOString(),
      })
      setForm(emptyForm)
      fetchReminders()
    } catch (e) {
      console.error(e)
    } finally {
      setSaving(false)
    }
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
          <h2 className="font-display text-[15px] text-ink mb-3">New reminder</h2>
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
              <label className="block text-xs font-medium text-muted mb-1.5">When</label>
              <input
                required
                type="datetime-local"
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm"
                value={form.remindAt}
                onChange={(e) => setForm({ ...form, remindAt: e.target.value })}
              />
            </div>
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
                    className="h-8 w-8 shrink-0 rounded-lg border border-line flex items-center justify-center text-muted hover:text-status-rejected hover:border-status-rejected/40"
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
    </Layout>
  )
}
