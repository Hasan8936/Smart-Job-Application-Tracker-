import React from 'react'
import { X } from 'lucide-react'
import { STATUS_ORDER, statusLabel } from '../lib/status'

export default function ApplicationDrawer({ open, onClose, form, setForm, onSubmit, isEditing }) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-ink/40" onClick={onClose} />
      <div className="relative w-full max-w-md h-full bg-surface shadow-pop flex flex-col">
        <div className="flex items-center justify-between px-6 h-16 border-b border-line">
          <h2 className="font-display text-lg text-ink">{isEditing ? 'Edit application' : 'Add application'}</h2>
          <button onClick={onClose} className="h-11 w-11 sm:h-8 sm:w-8 -mr-2 sm:mr-0 rounded-full hover:bg-paper flex items-center justify-center text-muted">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="flex-1 overflow-y-auto scroll-thin px-6 py-5 space-y-4">
          <div>
            <label className="block text-xs font-medium text-muted mb-1.5">Company</label>
            <input
              required
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
              placeholder="e.g. Google"
              value={form.companyName}
              onChange={(e) => setForm({ ...form, companyName: e.target.value })}
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-muted mb-1.5">Role</label>
            <input
              required
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
              placeholder="e.g. Software Engineer"
              value={form.roleTitle}
              onChange={(e) => setForm({ ...form, roleTitle: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-muted mb-1.5">Status</label>
              <select
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value })}
              >
                {STATUS_ORDER.map((s) => (
                  <option key={s} value={s}>{statusLabel(s)}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-muted mb-1.5">Applied on</label>
              <input
                type="date"
                className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
                value={form.appliedDate || ''}
                onChange={(e) => setForm({ ...form, appliedDate: e.target.value })}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-muted mb-1.5">Job description</label>
            <textarea
              rows={6}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm resize-none"
              placeholder="Paste the job description — this also powers resume matching"
              value={form.jobDescription}
              onChange={(e) => setForm({ ...form, jobDescription: e.target.value })}
            />
          </div>
        </form>

        <div className="px-6 py-4 border-t border-line flex gap-3">
          <button
            onClick={onClose}
            type="button"
            className="flex-1 px-4 py-2.5 rounded-full border border-line text-sm font-medium text-ink-soft hover:bg-paper"
          >
            Cancel
          </button>
          <button
            onClick={onSubmit}
            type="button"
            className="flex-1 px-4 py-2.5 rounded-full btn-gradient text-sm font-medium"
          >
            {isEditing ? 'Save changes' : 'Add application'}
          </button>
        </div>
      </div>
    </div>
  )
}
