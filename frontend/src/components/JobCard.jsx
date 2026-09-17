import React, { useState } from 'react'
import { Bookmark, Bot, Check, ExternalLink, MapPin, Star } from 'lucide-react'

function matchColor(score) {
  if (score == null) return 'text-muted'
  if (score >= 70) return 'text-status-offer'
  if (score >= 40) return 'text-status-interview'
  return 'text-muted'
}

function formatSalary(job) {
  if (!job.salaryMin && !job.salaryMax) return null
  const currency = job.salaryCurrency || ''
  const min = job.salaryMin ? job.salaryMin.toLocaleString() : null
  const max = job.salaryMax ? job.salaryMax.toLocaleString() : null
  const range = min && max ? `${min} – ${max}` : min || max
  const label = job.salaryEstimated ? ' (Est.)' : ''
  return `${currency} ${range}${label}`.trim()
}

export default function JobCard({ job, action, onAction, onOpen, onAutoApply }) {
  const [logoFailed, setLogoFailed] = useState(false)
  const [applying, setApplying] = useState(false)
  const isSaved = action === 'SAVED'
  const isBookmarked = action === 'BOOKMARKED'
  const isApplied = action === 'APPLIED'

  const salaryDisplay = formatSalary(job)

  async function handleAutoApply(e) {
    e.stopPropagation()
    if (applying || !onAutoApply) return
    setApplying(true)
    try {
      await onAutoApply(job.id)
    } finally {
      setApplying(false)
    }
  }

  return (
    <article className="bg-surface border border-line rounded-xl2 p-4 sm:p-5 shadow-card hover:border-ink/25 transition-colors">
      <div className="flex items-start gap-3">
        {job.logoUrl && !logoFailed ? (
          <img
            src={job.logoUrl}
            alt=""
            onError={() => setLogoFailed(true)}
            className="h-10 w-10 shrink-0 rounded-lg object-cover border border-line bg-white"
          />
        ) : (
          <div className="h-10 w-10 shrink-0 rounded-lg bg-ink text-accent flex items-center justify-center font-display text-lg select-none">
            {(job.company || '?').slice(0, 1).toUpperCase()}
          </div>
        )}

        <div className="min-w-0 flex-1">
          <button
            onClick={() => onOpen(job.id)}
            className="text-left font-display text-base text-ink hover:text-accent-dark truncate max-w-full block"
          >
            {job.title || 'Untitled role'}
          </button>
          <div className="text-sm text-muted truncate">{job.company || 'Company unavailable'}</div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1.5 text-xs text-muted">
            <span className="inline-flex items-center gap-1"><MapPin size={12} />{job.location || 'Location unavailable'}</span>
            {job.employmentType && <span className="capitalize">{job.employmentType}</span>}
            {salaryDisplay && (
              <span className={job.salaryEstimated ? 'text-amber-500' : 'text-ink-soft'}>
                {salaryDisplay}
              </span>
            )}
          </div>
        </div>

        {/* Match score badge */}
        <div className="shrink-0 text-right">
          {job.matchScore != null ? (
            <div className={`font-mono text-lg font-bold ${matchColor(job.matchScore)}`}>
              {Math.round(job.matchScore)}%
            </div>
          ) : (
            <div className="font-mono text-lg text-muted">—</div>
          )}
          <div className="text-[11px] text-muted">match</div>
        </div>
      </div>

      {/* Description snippet */}
      {job.descriptionSnippet && (
        <p className="mt-2 text-xs text-muted line-clamp-2 leading-5">{job.descriptionSnippet}</p>
      )}

      {/* Skills row */}
      {job.skills && job.skills.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-3">
          {job.skills.slice(0, 6).map(skill => (
            <span key={skill} className="px-2 py-0.5 rounded-full bg-paper border border-line text-[11px] text-ink-soft">
              {skill}
            </span>
          ))}
          {job.skills.length > 6 && (
            <span className="px-2 py-0.5 rounded-full bg-paper border border-line text-[11px] text-muted">
              +{job.skills.length - 6} more
            </span>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 mt-3 pt-3 border-t border-line">
        <span className="text-xs text-muted">
          {job.postedAt ? new Date(job.postedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : 'Date unavailable'}
        </span>
        <div className="flex items-center gap-1.5">
          <button
            title={isSaved ? 'Saved' : 'Save job'}
            onClick={() => onAction(job.id, 'SAVED')}
            className={`h-9 w-9 rounded-full flex items-center justify-center border transition-colors ${isSaved ? 'bg-accent border-accent text-white' : 'border-line hover:border-ink/30'}`}
          >
            <Star size={15} fill={isSaved ? 'currentColor' : 'none'} />
          </button>
          <button
            title={isBookmarked ? 'Bookmarked' : 'Bookmark job'}
            onClick={() => onAction(job.id, 'BOOKMARKED')}
            className={`h-9 w-9 rounded-full flex items-center justify-center border transition-colors ${isBookmarked ? 'bg-paper border-ink text-ink' : 'border-line hover:border-ink/30'}`}
          >
            <Bookmark size={15} fill={isBookmarked ? 'currentColor' : 'none'} />
          </button>
          <button
            onClick={() => onAction(job.id, 'APPLIED')}
            className={`h-9 px-3 rounded-full text-xs font-medium inline-flex items-center gap-1.5 border transition-colors ${isApplied ? 'bg-status-offerSoft border-status-offer text-status-offer' : 'border-line text-ink hover:border-ink/30'}`}
          >
            <Check size={13} /> {isApplied ? 'Applied' : 'Mark applied'}
          </button>
          {onAutoApply && (
            <button
              title="Auto Apply with Skyvern"
              onClick={handleAutoApply}
              disabled={applying}
              className="h-9 px-3 rounded-full text-xs font-medium inline-flex items-center gap-1.5 border border-violet-400 text-violet-600 hover:bg-violet-50 dark:hover:bg-violet-900/20 transition-colors disabled:opacity-50"
            >
              <Bot size={13} /> {applying ? 'Submitting…' : 'Auto Apply'}
            </button>
          )}
          <a
            href={job.applyUrl}
            target="_blank"
            rel="noreferrer"
            title="Open official application"
            className="h-9 w-9 rounded-full flex items-center justify-center btn-gradient"
          >
            <ExternalLink size={14} />
          </a>
        </div>
      </div>
    </article>
  )
}
