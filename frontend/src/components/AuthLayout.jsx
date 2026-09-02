import React from 'react'
import BrandLogo from './BrandLogo'

export default function AuthLayout({ heading, copy, children, id }) {
  return (
    <div id={id} className="min-h-screen bg-hero-gradient flex items-center justify-center p-3 sm:p-6 lg:p-10">
      <div className="w-full max-w-5xl bg-surface rounded-xl3 shadow-floaty overflow-hidden grid lg:grid-cols-2">
        <div className="hidden lg:flex flex-col justify-between bg-hero-gradient p-11 relative overflow-hidden min-h-[640px]">
          <div className="flex items-center gap-2.5 text-ink/55 text-sm font-semibold">
            <BrandLogo markOnly className="w-7 h-7" />
            <span>Smart Job Tracker</span>
          </div>

          <div className="relative my-auto h-[220px]">
            <div className="hero-orb absolute z-[3]" style={{ width: 150, height: 150, left: '30%', top: 6 }} />
            <div className="font-display font-bold text-ink leading-[0.95] tracking-tight text-[clamp(44px,6vw,64px)] whitespace-nowrap">
              SMART JOB
            </div>
            <div className="text-gradient font-display font-bold leading-[0.95] tracking-tight text-[clamp(44px,6vw,64px)] whitespace-nowrap">
              TRACKER
            </div>
          </div>

          <div>
            <h2 className="font-display text-[28px] font-semibold tracking-tight text-ink mb-2.5">Track every application</h2>
            <p className="text-[15px] leading-relaxed text-ink-soft max-w-[38ch]">
              Applications, resume matching, and reminders — tracked together instead of scattered across sheets and inboxes.
            </p>
          </div>
        </div>

        <div className="flex items-center justify-center p-6 sm:p-10 lg:p-14">
          <div className="w-full max-w-sm">
            <BrandLogo className="w-40 h-auto mb-6 lg:hidden" />
            <h1 className="font-display text-2xl sm:text-3xl text-ink mb-1">{heading}</h1>
            <p className="text-sm text-muted mb-6">{copy}</p>
            {children}
          </div>
        </div>
      </div>
    </div>
  )
}
