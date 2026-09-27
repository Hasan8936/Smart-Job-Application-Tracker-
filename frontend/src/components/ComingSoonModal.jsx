import React, { useEffect, useRef } from 'react'
import { Sparkles, X } from 'lucide-react'

/**
 * "Coming soon" popup for features that are announced but not live yet (Auto Apply, WhatsApp).
 * Closes on Escape, backdrop click, or the button; focus moves to the primary button on open.
 */
export default function ComingSoonModal({ open, onClose, icon: Icon = Sparkles, title, children, points = [] }) {
  const closeRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    closeRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="coming-soon-title">
      <button aria-label="Close" className="absolute inset-0 bg-ink/50" onClick={onClose} />
      <div className="relative w-full max-w-md bg-surface rounded-xl2 shadow-card p-6">
        <button onClick={onClose} className="absolute top-4 right-4 text-muted hover:text-ink" aria-label="Close"><X size={16} /></button>
        <div className="h-11 w-11 rounded-full bg-violet-500/15 text-violet-600 flex items-center justify-center mb-4">
          <Icon size={20} />
        </div>
        <span className="inline-block text-[11px] font-semibold uppercase tracking-wide text-violet-600 bg-violet-500/10 border border-violet-400/40 rounded-full px-2 py-0.5 mb-2">
          Coming soon
        </span>
        <h3 id="coming-soon-title" className="font-display text-lg text-ink mb-2">{title}</h3>
        <div className="text-sm text-muted">{children}</div>
        {points.length > 0 && (
          <ul className="mt-3 space-y-1.5 text-sm text-ink-soft">
            {points.map((p) => <li key={p} className="flex gap-2"><span className="text-violet-500">•</span>{p}</li>)}
          </ul>
        )}
        <div className="flex justify-end mt-5">
          <button ref={closeRef} onClick={onClose} className="btn-gradient rounded-full px-4 py-2 text-sm font-medium">Got it</button>
        </div>
      </div>
    </div>
  )
}
