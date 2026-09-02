import React from 'react'

const TONES = {
  violet: { chip: 'bg-accent-soft text-accent' },
  sky: { chip: 'bg-status-interviewSoft text-status-interview' },
  mint: { chip: 'bg-status-offerSoft text-status-offer' },
  ember: { chip: 'bg-status-rejectedSoft text-status-rejected' },
  amber: { chip: 'bg-status-screeningSoft text-status-screening' },
}

export default function StatCard({ label, value, hint, icon: Icon, tone = 'violet' }) {
  const t = TONES[tone] || TONES.violet
  return (
    <div className="rounded-xl2 border border-line bg-surface p-4 shadow-card">
      <div className="flex items-center justify-between">
        <span className="text-xs uppercase tracking-wide text-muted">{label}</span>
        {Icon && (
          <span className={`h-8 w-8 rounded-lg flex items-center justify-center ${t.chip}`}>
            <Icon size={15} />
          </span>
        )}
      </div>
      <div className="mt-2 font-mono text-3xl font-medium text-ink">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
    </div>
  )
}
