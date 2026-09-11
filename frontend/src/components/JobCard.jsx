import React, { useState } from 'react'
import { Bookmark, Check, ExternalLink, MapPin, Star } from 'lucide-react'

function matchColor(score) {
  if (score == null) return 'text-muted'
  if (score >= 70) return 'text-status-offer'
  if (score >= 40) return 'text-status-interview'
  return 'text-muted'
}

export default function JobCard({ job, action, onAction, onOpen }) {
  const [logoFailed, setLogoFailed] = useState(false)
  const isSaved = action === 'SAVED'
  const isBookmarked = action === 'BOOKMARKED'
  const isApplied = action === 'APPLIED'

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
            {job.provider && (
              <span className="px-1.5 py-0.5 rounded bg-paper border border-line uppercase tracking-wide text-[10px]">
                {job.provider}
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