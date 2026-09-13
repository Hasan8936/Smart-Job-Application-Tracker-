import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Cookie } from 'lucide-react'

const STORAGE_KEY = 'cookie_consent'

export default function CookieBanner() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    try {
      if (!localStorage.getItem(STORAGE_KEY)) setVisible(true)
    } catch {
      /* localStorage blocked (private mode / strict settings) — don't show banner */
    }
  }, [])

  function respond(choice) {
    try { localStorage.setItem(STORAGE_KEY, choice) } catch { /* ignore */ }
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div
      role="dialog"
      aria-label="Cookie consent"
      aria-live="polite"
      className="fixed bottom-0 left-0 right-0 z-50 flex items-start gap-4 bg-surface border-t border-line shadow-pop px-4 py-4 sm:px-6"
    >
      <Cookie size={20} className="shrink-0 mt-0.5 text-accent" aria-hidden="true" />
      <div className="flex-1 min-w-0">
        <p className="text-sm text-ink leading-snug">
          We use browser storage (localStorage) to keep you logged in and remember your preferences.
          No advertising or tracking cookies.{' '}
          <Link to="/privacy#cookies" className="text-accent hover:underline whitespace-nowrap">
            See full cookie policy
          </Link>
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          onClick={() => respond('declined')}
          className="px-3 py-1.5 text-xs rounded-full border border-line text-muted hover:text-ink hover:border-ink transition-colors"
        >
          Decline
        </button>
        <button
          onClick={() => respond('accepted')}
          className="px-3 py-1.5 text-xs rounded-full btn-gradient font-medium"
        >
          Accept
        </button>
      </div>
    </div>
  )
}
