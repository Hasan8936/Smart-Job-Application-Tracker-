import React, { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, X } from 'lucide-react'

/** Confirmation after "Mark applied": the job is now an application, and connected Gmail keeps it updated. */
export default function AppliedNotice({ notice, onClose }) {
  // Ref so a re-rendering parent (new onClose each render) doesn't restart the 8 s timer.
  const close = useRef(onClose)
  close.current = onClose
  useEffect(() => {
    if (!notice) return undefined
    const t = setTimeout(() => close.current(), 8000)
    return () => clearTimeout(t)
  }, [notice])

  if (!notice) return null
  return (
    <div role="status" className="fixed bottom-4 right-4 left-4 sm:left-auto sm:w-96 z-50 bg-surface border border-line rounded-xl2 shadow-card p-4 flex gap-3">
      <CheckCircle2 size={18} className="shrink-0 mt-0.5 text-status-offer" />
      <div className="flex-1 min-w-0 text-sm">
        <p className="text-ink font-medium truncate">Added to Applications{notice.company ? ` · ${notice.company}` : ''}</p>
        <p className="text-muted text-xs mt-0.5">If Gmail is connected, replies about this application update its status automatically.</p>
        <Link to="/applications" className="inline-block mt-2 text-xs font-medium text-accent underline underline-offset-2">View in Applications</Link>
      </div>
      <button onClick={onClose} aria-label="Dismiss" className="self-start text-muted hover:text-ink"><X size={15} /></button>
    </div>
  )
}
