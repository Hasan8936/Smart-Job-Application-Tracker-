import React from 'react'
import { STATUS_META } from '../lib/status'

const previewStages = ['APPLIED', 'OA', 'INTERVIEW', 'OFFER']

export default function AuthLayout({ heading, copy, children }) {
  return (
    <div className="min-h-screen grid lg:grid-cols-2 bg-paper">
      <div className="hidden lg:flex flex-col justify-between bg-ink text-white p-10">
        <div className="flex items-center gap-2">
          <span className="h-7 w-7 rounded-md bg-accent flex items-center justify-center text-ink-soft font-display font-semibold text-sm">S</span>
          <span className="font-display text-[17px] tracking-tight">Smart Job Tracker</span>
        </div>

        <div>
          <p className="font-display text-3xl leading-snug max-w-sm">
            One place to run your entire job search.
          </p>
          <p className="text-white/60 mt-3 max-w-sm text-sm">
            Applications, resume matching, and reminders — tracked together instead of scattered across sheets and inboxes.
          </p>

          <div className="mt-8 flex items-center gap-2">
            {previewStages.map((s) => (
              <span key={s} className={`h-1.5 flex-1 rounded-full ${STATUS_META[s].dot} opacity-80`} />
            ))}
          </div>
          <div className="mt-2 flex justify-between text-xs text-white/40 font-mono">
            <span>Applied</span>
            <span>Offer</span>
          </div>
        </div>

        <p className="text-xs text-white/30">Built for people running a real job search, not a demo.</p>
      </div>

      <div className="flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-sm">
          <h1 className="font-display text-2xl text-ink mb-1">{heading}</h1>
          <p className="text-sm text-muted mb-6">{copy}</p>
          {children}
        </div>
      </div>
    </div>
  )
}
