import React from 'react'
import { Bookmark, Check, ExternalLink, MapPin, Star } from 'lucide-react'

export default function JobCard({ job, action, onAction, onOpen }) {
  return (
    <article className="bg-surface border border-line rounded-xl2 p-4 sm:p-5 shadow-card hover:border-ink/25 transition-colors">
      <div className="flex items-start gap-3">
        <div className="h-10 w-10 shrink-0 rounded-lg bg-ink text-accent flex items-center justify-center font-display text-lg">
          {(job.company || '?').slice(0, 1).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <button onClick={() => onOpen(job.id)} className="text-left font-display text-base text-ink hover:text-accent-dark truncate max-w-full block">
            {job.title || 'Untitled role'}
          </button>
          <div className="text-sm text-muted truncate">{job.company || 'Company unavailable'}</div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-2 text-xs text-muted">
            <span className="inline-flex items-center gap-1"><MapPin size={13} />{job.location || 'Location unavailable'}</span>
            {job.employmentType && <span>{job.employmentType}</span>}
            {job.provider && <span className="uppercase tracking-wide">{job.provider}</span>}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <div className="font-mono text-lg text-ink">{job.matchScore == null ? '—' : `${Math.round(job.matchScore)}%`}</div>
          <div className="text-[11px] text-muted">match</div>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 mt-4 pt-3 border-t border-line">
        <span className="text-xs text-muted">{job.postedAt ? new Date(job.postedAt).toLocaleDateString() : 'Posted date unavailable'}</span>
        <div className="flex items-center gap-1.5">
          <button title="Save job" onClick={() => onAction(job.id, 'SAVED')} className={`h-10 w-10 rounded-lg flex items-center justify-center border ${action === 'SAVED' ? 'bg-accent border-accent' : 'border-line'}`}>
            <Star size={16} fill={action === 'saved' ? 'currentColor' : 'none'} />
          </button>
          <button title="Bookmark job" onClick={() => onAction(job.id, 'BOOKMARKED')} className={`h-10 w-10 rounded-lg flex items-center justify-center border ${action === 'BOOKMARKED' ? 'bg-paper border-ink' : 'border-line'}`}>
            <Bookmark size={16} fill={action === 'bookmarked' ? 'currentColor' : 'none'} />
          </button>
          <button onClick={() => onAction(job.id, 'APPLIED')} className={`h-10 px-3 rounded-lg text-xs font-medium inline-flex items-center gap-1.5 border ${action === 'APPLIED' ? 'bg-status-offerSoft border-status-offer text-status-offer' : 'border-line text-ink'}`}>
            <Check size={14} /> {action === 'APPLIED' ? 'Applied' : 'Mark applied'}
          </button>
          <a href={job.applyUrl} target="_blank" rel="noreferrer" title="Open official application" className="h-10 w-10 rounded-lg flex items-center justify-center bg-ink text-white">
            <ExternalLink size={15} />
          </a>
        </div>
      </div>
    </article>
  )
}