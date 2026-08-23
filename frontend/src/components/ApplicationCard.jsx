import React from 'react'
import { Pencil, Trash2, Calendar } from 'lucide-react'
import StatusBadge from './StatusBadge'

export default function ApplicationCard({ app, onEdit, onDelete }) {
  return (
    <div className="group bg-surface border border-line rounded-xl2 p-4 flex items-start justify-between gap-4 hover:border-ink/20 transition-colors">
      <div className="min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-display text-[15px] text-ink">{app.companyName}</span>
          <span className="text-muted text-sm">·</span>
          <span className="text-sm text-muted">{app.roleTitle}</span>
        </div>
        <div className="mt-2 flex items-center gap-3 flex-wrap">
          <StatusBadge status={app.status} />
          {app.appliedDate && (
            <span className="inline-flex items-center gap-1 text-xs text-muted font-mono">
              <Calendar size={12} />
              {new Date(app.appliedDate).toLocaleDateString()}
            </span>
          )}
        </div>
        {app.jobDescription && (
          <p className="mt-2 text-sm text-muted line-clamp-2">{app.jobDescription.slice(0, 180)}</p>
        )}
      </div>

      <div className="flex flex-col gap-2 shrink-0 opacity-70 group-hover:opacity-100 transition-opacity">
        {onEdit && (
          <button
            onClick={() => onEdit(app)}
            className="h-8 w-8 rounded-lg border border-line bg-surface flex items-center justify-center text-muted hover:text-ink hover:border-ink/30"
            aria-label={`Edit ${app.companyName} application`}
          >
            <Pencil size={14} />
          </button>
        )}
        {onDelete && (
          <button
            onClick={() => onDelete(app)}
            className="h-8 w-8 rounded-lg border border-line bg-surface flex items-center justify-center text-muted hover:text-status-rejected hover:border-status-rejected/40"
            aria-label={`Delete ${app.companyName} application`}
          >
            <Trash2 size={14} />
          </button>
        )}
      </div>
    </div>
  )
}
