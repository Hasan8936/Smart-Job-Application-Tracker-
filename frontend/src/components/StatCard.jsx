import React from 'react'

export default function StatCard({ label, value, hint, icon: Icon, accent = false }) {
  return (
    <div className={`rounded-xl2 border p-5 shadow-card ${accent ? 'bg-ink border-ink text-white' : 'bg-surface border-line'}`}>
      <div className="flex items-center justify-between">
        <span className={`text-xs uppercase tracking-wide ${accent ? 'text-white/60' : 'text-muted'}`}>{label}</span>
        {Icon && <Icon size={16} className={accent ? 'text-accent' : 'text-muted'} />}
      </div>
      <div className={`mt-2 font-mono text-3xl font-medium ${accent ? 'text-white' : 'text-ink'}`}>{value}</div>
      {hint && <div className={`mt-1 text-xs ${accent ? 'text-white/50' : 'text-muted'}`}>{hint}</div>}
    </div>
  )
}
