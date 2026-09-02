import React, { useEffect, useState } from 'react'
import { Sparkles, Save, Info } from 'lucide-react'
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

const EMPTY = FIELDS.reduce((acc, f) => ({ ...acc, [f.key]: '' }), {})

function dataToFields(data) {
  const next = { ...EMPTY }
  FIELDS.forEach((f) => {
    next[f.key] = Array.isArray(data?.[f.key]) ? data[f.key].join('\n') : ''
  })
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
  return dto
}

export default function CandidateProfile() {
  const [fields, setFields] = useState(EMPTY)
  const [meta, setMeta] = useState({ sourceResumeId: null, updatedAt: null })
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
        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-accent text-accent-ink text-sm font-semibold hover:bg-accent-dark disabled:opacity-50"
      >
        <Save size={15} /> {saving ? 'Saving…' : 'Save changes'}
      </button>
    </div>
  )

  return (
    <Layout title="Candidate profile" subtitle="Structured details extracted from your resume" actions={actions}>
      <div className="max-w-4xl space-y-5">
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
          <p className="text-sm text-muted">Loading…</p>
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
