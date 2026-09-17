import React, { useEffect, useState } from 'react'
import { Bot, Info, Save, Sparkles } from 'lucide-react'
import Layout from '../components/Layout'
import { getProfile, extractProfile, saveProfile } from '../api/profile'

// One line per item. Short lists get a compact box; section lists get more room.
const FIELDS = [
  { key: 'skills', label: 'Skills', rows: 3, placeholder: 'One skill per line' },
  { key: 'programmingLanguages', label: 'Programming languages', rows: 3, placeholder: 'One language per line' },
  { key: 'frameworks', label: 'Frameworks & libraries', rows: 3, placeholder: 'One framework per line' },
  { key: 'preferredRoles', label: 'Preferred roles', rows: 3, placeholder: 'One role per line' },
  { key: 'projects', label: 'Projects', rows: 5, placeholder: 'One project per line' },
  { key: 'education', label: 'Education', rows: 4, placeholder: 'One entry per line' },
  { key: 'experience', label: 'Experience', rows: 5, placeholder: 'One entry per line' },
]

const EMPTY = { ...FIELDS.reduce((acc, f) => ({ ...acc, [f.key]: '' }), {}), phone: '', linkedinUrl: '' }

function dataToFields(data) {
  const next = { ...EMPTY }
  FIELDS.forEach((f) => {
    next[f.key] = Array.isArray(data?.[f.key]) ? data[f.key].join('\n') : ''
  })
  next.phone = data?.phone || ''
  next.linkedinUrl = data?.linkedinUrl || ''
  return next
}

function fieldsToDto(fields) {
  const dto = {}
  FIELDS.forEach((f) => {
    dto[f.key] = (fields[f.key] || '')
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
  })
  dto.phone = (fields.phone || '').trim() || null
  dto.linkedinUrl = (fields.linkedinUrl || '').trim() || null
  return dto
}

export default function CandidateProfile() {
  const [fields, setFields] = useState(EMPTY)
  const [meta, setMeta] = useState({ sourceResumeId: null, updatedAt: null })
  const hasAutoApplyInfo = !!(fields.phone && fields.linkedinUrl)
  const [loading, setLoading] = useState(true)
  const [extracting, setExtracting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => { load() }, [])

  async function load() {
    setError('')
    try {
      const res = await getProfile()
      setFields(dataToFields(res.data))
      setMeta({ sourceResumeId: res.data.sourceResumeId, updatedAt: res.data.updatedAt })
    } catch (e) {
      // 404 simply means no profile yet — not an error to show.
      if (e.response?.status !== 404) setError('Could not load your profile. Try again in a moment.')
    } finally {
      setLoading(false)
    }
  }

  async function doExtract() {
    setError(''); setNotice('')
    try {
      setExtracting(true)
      const res = await extractProfile()
      setFields(dataToFields(res.data))
      setMeta({ sourceResumeId: res.data.sourceResumeId, updatedAt: res.data.updatedAt })
      setNotice('Extracted from your most recent resume. Review and edit below, then save.')
    } catch (e) {
      if (e.response?.status === 404) {
        setError(e.response?.data?.error || 'No resume found to analyze. Upload a resume first.')
      } else {
        setError('Extraction failed. Try again in a moment.')
      }
    } finally {
      setExtracting(false)
    }
  }

  async function doSave() {
    setError(''); setNotice('')
    try {
      setSaving(true)
      const res = await saveProfile(fieldsToDto(fields))
      setFields(dataToFields(res.data))
      setMeta({ sourceResumeId: res.data.sourceResumeId, updatedAt: res.data.updatedAt })
      setNotice('Saved.')
    } catch (e) {
      if (e.response?.status === 400) {
        setError('Some entries are too long or there are too many. Please shorten and try again.')
      } else {
        setError('Could not save your changes. Try again in a moment.')
      }
    } finally {
      setSaving(false)
    }
  }

  function updateField(key, value) {
    setFields((prev) => ({ ...prev, [key]: value }))
  }

  const actions = (
    <div className="flex items-center gap-2">
      <button
        onClick={doExtract}
        disabled={extracting || saving}
        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full border border-line bg-surface text-sm font-medium text-ink-soft hover:border-ink/30 disabled:opacity-50"
      >
        <Sparkles size={15} /> {extracting ? 'Extracting…' : 'Extract from resume'}
      </button>
      <button
        onClick={doSave}
        disabled={saving || extracting || loading}
        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full btn-gradient text-sm font-semibold disabled:opacity-50"
      >
        <Save size={15} /> {saving ? 'Saving…' : 'Save changes'}
      </button>
    </div>
  )

  return (
    <Layout title="Candidate profile" subtitle="Structured details extracted from your resume" actions={actions}>
      <div className="max-w-4xl space-y-5">

        {/* ── Auto Apply callout ── */}
        <div className={`flex items-start gap-3 rounded-xl2 p-4 border ${hasAutoApplyInfo ? 'bg-violet-50 dark:bg-violet-900/15 border-violet-200 dark:border-violet-700' : 'bg-amber-50 dark:bg-amber-900/15 border-amber-200 dark:border-amber-700'}`}>
          <Bot size={18} className={`shrink-0 mt-0.5 ${hasAutoApplyInfo ? 'text-violet-500' : 'text-amber-500'}`} />
          <div>
            <p className={`text-sm font-medium ${hasAutoApplyInfo ? 'text-violet-700 dark:text-violet-300' : 'text-amber-700 dark:text-amber-300'}`}>
              {hasAutoApplyInfo ? 'Auto Apply profile ready' : 'Complete your profile for Auto Apply'}
            </p>
            <p className={`text-xs mt-0.5 ${hasAutoApplyInfo ? 'text-violet-600/80 dark:text-violet-400' : 'text-amber-600/80 dark:text-amber-400'}`}>
              {hasAutoApplyInfo
                ? 'Your phone number and LinkedIn URL are set. Skyvern will use these when applying to jobs on your behalf.'
                : 'Add your phone number and LinkedIn URL below so Skyvern can fill job application forms automatically.'}
            </p>
          </div>
        </div>

        {/* ── Auto Apply fields ── */}
        <div className="grid sm:grid-cols-2 gap-5">
          <div className="bg-surface border border-line rounded-xl2 shadow-card p-4">
            <label className="block font-display text-[15px] text-ink mb-1">Phone number <span className="text-xs text-muted font-normal">(for Auto Apply)</span></label>
            <input
              type="tel"
              value={fields.phone}
              onChange={e => updateField('phone', e.target.value)}
              placeholder="+91 98765 43210"
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
            />
          </div>
          <div className="bg-surface border border-line rounded-xl2 shadow-card p-4">
            <label className="block font-display text-[15px] text-ink mb-1">LinkedIn URL <span className="text-xs text-muted font-normal">(for Auto Apply)</span></label>
            <input
              type="url"
              value={fields.linkedinUrl}
              onChange={e => updateField('linkedinUrl', e.target.value)}
              placeholder="https://linkedin.com/in/your-profile"
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm"
            />
          </div>
        </div>

        <div className="flex items-start gap-2 text-xs text-muted bg-surface border border-line rounded-xl2 p-3">
          <Info size={15} className="shrink-0 mt-0.5" />
          <p>
            Extraction reads only the text already in your uploaded resume — nothing is invented.
            Edit any field below (one item per line) and click <span className="font-medium text-ink">Save changes</span>.
            Your original resume is never modified.
          </p>
        </div>

        {error && <p className="text-sm text-status-rejected">{error}</p>}
        {notice && <p className="text-sm text-status-offer">{notice}</p>}

        {loading ? (
          <div className="grid sm:grid-cols-2 gap-5 animate-pulse">
            {[
              { rows: 3 }, { rows: 3 }, { rows: 3 }, { rows: 3 },
              { rows: 5, wide: true }, { rows: 4, wide: true }, { rows: 5, wide: true },
            ].map((f, i) => (
              <div key={i} className={`bg-surface border border-line rounded-xl2 shadow-card p-4 ${f.wide ? 'sm:col-span-2' : ''}`}>
                <div className="h-4 w-36 bg-paper rounded mb-3" />
                <div className={`bg-paper rounded-lg ${f.rows === 3 ? 'h-20' : f.rows === 4 ? 'h-28' : 'h-36'}`} />
              </div>
            ))}
          </div>
        ) : (
          <div className="grid sm:grid-cols-2 gap-5">
            {FIELDS.map((f) => (
              <div
                key={f.key}
                className={`bg-surface border border-line rounded-xl2 shadow-card p-4 ${f.rows >= 5 ? 'sm:col-span-2' : ''}`}
              >
                <label className="block font-display text-[15px] text-ink mb-2">{f.label}</label>
                <textarea
                  rows={f.rows}
                  value={fields[f.key]}
                  onChange={(e) => updateField(f.key, e.target.value)}
                  placeholder={f.placeholder}
                  className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm resize-none"
                />
              </div>
            ))}
          </div>
        )}

        {meta.updatedAt && (
          <p className="text-xs text-muted">
            Last updated {new Date(meta.updatedAt).toLocaleString()}
            {meta.sourceResumeId ? ` · from resume #${meta.sourceResumeId}` : ''}
          </p>
        )}
      </div>
    </Layout>
  )
}
