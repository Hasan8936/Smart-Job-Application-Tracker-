import React, { useState } from 'react'
import { Bot, ExternalLink, FileText, Loader2, Mail, MessageSquare, Pencil, X } from 'lucide-react'
import { autoApply, getAutoApplyStatus } from '../api/jobs'
import { formatSalary, formatPostedAt, formatFullDate, formatEmploymentType, safeHttpUrl } from '../lib/jobFormat'


export default function JobDetails({ job, onClose, onGenerate, documents = [], onSaveDocument }) {
  const [autoApplyState, setAutoApplyState] = useState({ status: null, taskId: null, error: null, loading: false })

  if (!job) return null

  const salary = formatSalary(job)
  const postedFull = formatFullDate(job.postedAt)
  const postedAgo = formatPostedAt(job.postedAt)

  async function handleAutoApply() {
    setAutoApplyState({ status: null, taskId: null, error: null, loading: true })
    try {
      const data = await autoApply(job.id)
      setAutoApplyState({ status: data.status, taskId: data.taskId, error: null, loading: false })
      // poll once after 5 s for a status update
      if (data.taskId) {
        setTimeout(async () => {
          try {
            const updated = await getAutoApplyStatus(data.taskId)
            setAutoApplyState(prev => ({ ...prev, status: updated.status }))
          } catch {}
        }, 5000)
      }
    } catch (err) {
      const msg = err?.response?.data?.error || err?.message || 'Auto-apply failed'
      setAutoApplyState({ status: null, taskId: null, error: msg, loading: false })
    }
  }

  const autoApplyLabel = (() => {
    if (autoApplyState.loading) return 'Submitting…'
    if (autoApplyState.status === 'PENDING') return 'Task queued'
    if (autoApplyState.status === 'RUNNING') return 'Applying…'
    if (autoApplyState.status === 'COMPLETED') return 'Applied!'
    if (autoApplyState.status === 'FAILED') return 'Failed — retry?'
    return 'Auto Apply'
  })()

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-5">
      <button aria-label="Close job details" onClick={onClose} className="absolute inset-0 bg-ink/50" />
      <section className="relative w-full sm:max-w-2xl max-h-[90vh] overflow-y-auto bg-surface sm:rounded-xl2 shadow-card p-5 sm:p-7">
        <button onClick={onClose} className="absolute top-4 right-4 h-10 w-10 rounded-full border border-line flex items-center justify-center" aria-label="Close"><X size={17} /></button>
        <div className="pr-12 mb-6">
          <p className="text-sm text-muted mb-1">{job.company}</p>
          <h2 className="font-display text-2xl text-ink">{job.title}</h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
          <div className="bg-paper rounded-lg p-3">
            <div className="text-xs text-muted">Location</div>
            <div className="text-sm text-ink mt-1">{job.location || 'Unavailable'}</div>
            <div className="text-[11px] text-muted mt-0.5 capitalize">
              {[job.workMode?.toLowerCase(), formatEmploymentType(job.employmentType)?.toLowerCase()].filter(Boolean).join(' · ') || 'Work mode unavailable'}
            </div>
          </div>
          <div className="bg-paper rounded-lg p-3">
            <div className="text-xs text-muted">Salary</div>
            <div className={`text-sm mt-1 ${salary?.estimated ? 'text-amber-600' : 'text-ink'}`}>
              {salary ? `${salary.estimated ? 'Est. ' : ''}${salary.text}` : 'Unavailable'}
            </div>
            <div className="text-[11px] text-muted mt-0.5">
              {salary?.note || 'Not listed, and too few similar listed salaries to estimate'}
            </div>
          </div>
          <div className="bg-paper rounded-lg p-3">
            <div className="text-xs text-muted">Posted</div>
            <div className="text-sm text-ink mt-1">{postedFull || 'Unavailable'}</div>
            {postedAgo && postedAgo !== postedFull && <div className="text-[11px] text-muted mt-0.5">{postedAgo}</div>}
          </div>
        </div>

        <h3 className="font-display text-base mb-2">Job description</h3>
        {(!job.description || job.description.trim().length < 300) && (
          <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-2">
            {job.description ? 'This listing has only a short description.' : 'The job source did not include a description.'}
            {safeHttpUrl(job.applyUrl) && <> See the <a href={safeHttpUrl(job.applyUrl) || undefined} target="_blank" rel="noreferrer" className="underline">original posting</a> for full details.</>}
          </p>
        )}
        <p className="text-sm text-muted whitespace-pre-line leading-6">{job.description || 'Description unavailable.'}</p>
        {job.requiredSkills?.length > 0 && <SkillGroup label="Required skills" skills={job.requiredSkills} />}
        {job.preferredSkills?.length > 0 && <SkillGroup label="Preferred skills" skills={job.preferredSkills} />}

        {/* Auto Apply status banner */}
        {autoApplyState.error && (
          <div className="mt-4 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-700">
            {autoApplyState.error}
          </div>
        )}
        {autoApplyState.status === 'COMPLETED' && (
          <div className="mt-4 rounded-lg bg-green-50 border border-green-200 px-3 py-2 text-sm text-green-700">
            Application submitted successfully via Skyvern!
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-2">
          <a href={safeHttpUrl(job.applyUrl) || undefined} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 btn-gradient rounded-full px-4 py-2.5 text-sm font-medium"><ExternalLink size={15} /> Open official application</a>
          <button
            onClick={handleAutoApply}
            disabled={autoApplyState.loading || autoApplyState.status === 'RUNNING' || autoApplyState.status === 'PENDING'}
            className="inline-flex items-center gap-2 border border-violet-400 text-violet-600 hover:bg-violet-50 dark:hover:bg-violet-900/20 rounded-full px-4 py-2.5 text-sm font-medium transition-colors disabled:opacity-50"
          >
            {autoApplyState.loading ? <Loader2 size={15} className="animate-spin" /> : <Bot size={15} />}
            {autoApplyLabel}
          </button>
          {onGenerate && <button onClick={() => onGenerate('COVER_LETTER')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><FileText size={15} /> Cover letter</button>}
          {onGenerate && (
            <>
              <button onClick={() => onGenerate('COLD_EMAIL')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><Mail size={15} /> Cold email</button>
              <button onClick={() => onGenerate('INTERVIEW_QUESTIONS')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><MessageSquare size={15} /> Interview questions</button>
              <button onClick={() => onGenerate('IMPROVE_RESUME')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><Pencil size={15} /> Improve resume</button>
            </>
          )}
        </div>

        {documents.length > 0 && (
          <div className="mt-7">
            <h3 className="font-display text-base mb-2">Generated drafts</h3>
            {documents.map(document => (
              <div key={document.id} className="border border-line rounded-lg p-3 mb-2">
                <div className="text-xs text-muted mb-2">{document.type.replaceAll('_', ' ')}</div>
                <textarea value={document.content} onChange={event => onSaveDocument({ ...document, content: event.target.value })} rows={6} className="w-full bg-paper rounded-lg border border-line p-3 text-sm leading-6" />
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function SkillGroup({ label, skills }) {
  return (
    <div className="mt-5">
      <h3 className="font-display text-base mb-2">{label}</h3>
      <div className="flex flex-wrap gap-1.5">
        {skills.map(skill => (
          <span key={skill} className="px-2.5 py-1 rounded-full bg-accent-soft text-accent text-xs font-medium">{skill}</span>
        ))}
      </div>
    </div>
  )
}
