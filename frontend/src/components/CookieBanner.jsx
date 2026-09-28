import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Cookie } from 'lucide-react'

const STORAGE_KEY = 'cookie_consent'

/**
 * Consent popup: a floating card (bottom-left on desktop, bottom on mobile) rather than a full-width bar.
 * Non-modal on purpose — the page stays usable, which also keeps it clear of intrusive-interstitial rules.
 * Appears shortly after load so it doesn't compete with the boot loader.
 */
export default function CookieBanner() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    let timer
    try {
      if (!localStorage.getItem(STORAGE_KEY)) timer = setTimeout(() => setVisible(true), 700)
    } catch {
      /* localStorage blocked (private mode / strict settings) — don't show the popup */
    }
    return () => clearTimeout(timer)
  }, [])

  function respond(choice) {
    try { localStorage.setItem(STORAGE_KEY, choice) } catch { /* ignore */ }
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-labelledby="cookie-title"
      aria-describedby="cookie-text"
      className="popup-in fixed z-50 bottom-3 left-3 right-3 sm:right-auto sm:bottom-5 sm:left-5 sm:w-[380px] bg-surface border border-line rounded-xl2 shadow-card p-5"
    >
      <div className="flex items-start gap-3">
        <span className="h-10 w-10 shrink-0 rounded-full bg-accent-soft text-accent flex items-center justify-center" aria-hidden="true">
          <Cookie size={19} />
        </span>
        <div className="min-w-0">
          <h2 id="cookie-title" className="font-display text-[15px] text-ink leading-tight">Cookies &amp; storage</h2>
          <p id="cookie-text" className="mt-1 text-sm text-ink-soft leading-snug">
            We use browser storage to keep you signed in and remember your preferences. No advertising or tracking cookies.{' '}
            <Link to="/privacy#cookies" className="text-accent hover:underline whitespace-nowrap">Cookie policy</Link>
          </p>
        </div>
      </div>
      <div className="mt-4 flex gap-2">
        <button
          onClick={() => respond('declined')}
          className="flex-1 h-10 rounded-full border border-line text-sm font-medium text-ink-soft hover:text-ink hover:border-ink/30 transition-colors"
        >
          Decline
        </button>
        <button
          onClick={() => respond('accepted')}
          className="flex-1 h-10 rounded-full btn-gradient text-sm font-medium"
        >
          Accept
        </button>
      </div>
    </div>
  )
}
