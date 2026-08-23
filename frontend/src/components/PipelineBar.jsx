import React from 'react'
import { PIPELINE_STAGES, STATUS_META } from '../lib/status'

// The pipeline is the one piece of visual identity repeated across the app:
// a proportional, ordered read of where every application currently sits.
// Order carries real information here (a real funnel), so a staged bar earns its keep.
export default function PipelineBar({ applications }) {
  const counts = PIPELINE_STAGES.map((s) => applications.filter((a) => a.status === s).length)
  const total = counts.reduce((a, b) => a + b, 0) || 1

  return (
    <div className="bg-surface border border-line rounded-xl2 shadow-card p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-display text-[15px] text-ink">Pipeline</h2>
        <span className="text-xs text-muted font-mono">{total} total</span>
      </div>

      <div className="flex h-2.5 w-full rounded-full overflow-hidden bg-line">
        {PIPELINE_STAGES.map((stage, i) => {
          const count = counts[i]
          if (!count) return null
          const meta = STATUS_META[stage]
          return (
            <div
              key={stage}
              className={`${meta.dot} h-full first:rounded-l-full last:rounded-r-full`}
              style={{ width: `${(count / total) * 100}%` }}
              title={`${meta.label}: ${count}`}
            />
          )
        })}
      </div>

      <div className="mt-4 grid grid-cols-2 sm:grid-cols-5 gap-3">
        {PIPELINE_STAGES.map((stage, i) => {
          const meta = STATUS_META[stage]
          return (
            <div key={stage} className="flex items-center gap-2">
              <span className={`h-2 w-2 rounded-full ${meta.dot} shrink-0`} />
              <div className="min-w-0">
                <div className="text-sm font-mono font-medium text-ink leading-none">{counts[i]}</div>
                <div className="text-xs text-muted truncate">{meta.label}</div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
