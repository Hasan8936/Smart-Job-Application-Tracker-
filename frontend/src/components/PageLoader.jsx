import React from 'react'

/**
 * Branded full-screen loader: the same markup as the boot screen in index.html (whose <style> block owns the
 * .boot* classes), so first load, lazy pages and session restore all look identical.
 */
export default function PageLoader() {
  return (
    <div className="boot" role="status" aria-live="polite">
      <div className="boot-card">
        <img src="/favicon.svg" width="52" height="52" alt="" />
        <div className="boot-spinner" aria-hidden="true" />
        <p>Loading Smart Job Tracker…</p>
      </div>
    </div>
  )
}
