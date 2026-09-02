import React from 'react'
import { Building2 } from 'lucide-react'

export default function TopCompanies({ applications }) {
  const counts = {}
  applications.forEach((a) => {
    const name = (a.companyName || '').trim()
    if (!name) return
    counts[name] = (counts[name] || 0) + 1
  })
  const top = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5)
  const max = top.length ? top[0][1] : 1

  return (
    <div className="bg-surface border border-line rounded-xl2 shadow-card p-5">
      <h2 className="font-display text-[15px] text-ink mb-4">Top companies</h2>
      {top.length === 0 ? (
        <p className="text-sm text-muted">No applications tracked yet.</p>
      ) : (
        <div className="space-y-3">
          {top.map(([name, count]) => (
            <div key={name} className="flex items-center gap-3">
              <span className="h-7 w-7 rounded-lg bg-accent-soft text-accent flex items-center justify-center shrink-0">
                <Building2 size={13} />
              </span>
              <span className="text-sm text-ink truncate flex-1">{name}</span>
              <div className="w-20 h-1.5 rounded-full bg-mist overflow-hidden shrink-0">
                <div className="h-full rounded-full bg-accent" style={{ width: `${(count / max) * 100}%` }} />
              </div>
              <span className="text-xs font-mono text-muted w-4 text-right shrink-0">{count}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
