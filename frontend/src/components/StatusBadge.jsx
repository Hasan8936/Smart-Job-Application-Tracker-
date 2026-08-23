import React from 'react'
import { STATUS_META } from '../lib/status'

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] || { label: status, text: 'text-muted', bg: 'bg-line' }
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${meta.bg} ${meta.text}`}>
      {meta.label}
    </span>
  )
}
