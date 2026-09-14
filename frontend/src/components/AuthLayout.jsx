import React, { lazy, Suspense } from 'react'
import BrandLogo from './BrandLogo'

const AuroraRing = lazy(() => import('./AuroraRing'))

export default function AuthLayout({ heading, copy, children, id }) {
  return (
    <div
      id={id}
      className="auth-dark min-h-screen flex items-center justify-center p-3 sm:p-6 lg:p-10"
      style={{ background: '#000000' }}
    >
      <div
        className="w-full max-w-5xl rounded-2xl overflow-hidden grid lg:grid-cols-2"
        style={{
          background: '#0a0a14',
          border: '1px solid rgba(255,255,255,0.06)',
          boxShadow: '0 40px 100px -20px rgba(124,58,237,0.22), 0 0 0 1px rgba(255,255,255,0.04)',
        }}
      >
        {/* ── Left panel — orb + branding ── */}
        <div
          className="hidden lg:flex flex-col justify-between relative overflow-hidden min-h-[640px]"
          style={{ background: 'linear-gradient(145deg, #040110 0%, #0e0330 55%, #160248 100%)' }}
        >
          {/* AuroraRing fills the entire panel, cards hidden */}
          <div className="absolute inset-0 z-0">
            <Suspense fallback={<div style={{ background: '#000', width: '100%', height: '100%' }} />}>
              <AuroraRing height="100%" className="h-full" hideCards />
            </Suspense>
          </div>

          {/* Top: logo */}
          <div className="relative z-10 flex items-center gap-3 p-11 pb-0">
            <BrandLogo markOnly variant="void" className="w-8 h-8" />
            <span className="text-white/50 text-sm font-semibold tracking-wide">Smart Job Tracker</span>
          </div>

          {/* Center: SMART JOB TRACKER text — sits in the middle, over the orb */}
          <div className="relative z-10 flex-1 flex flex-col justify-center px-11">
            {/* Dark gradient scrim so text reads cleanly over the glowing orb */}
            <div
              className="absolute inset-0 pointer-events-none"
              style={{
                background: 'linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0.48) 25%, rgba(0,0,0,0.55) 50%, rgba(0,0,0,0.48) 75%, rgba(0,0,0,0) 100%)',
              }}
            />

            <div className="relative">
              {/* Watermark monogram */}
              <div
                className="font-display font-black leading-none select-none mb-4"
                style={{ fontSize: 84, color: 'rgba(255,255,255,0.05)', letterSpacing: '-0.04em' }}
              >
                SJT
              </div>

              <h2
                className="font-display font-bold leading-[0.9] text-white mb-5"
                style={{ fontSize: 38, letterSpacing: '-0.02em' }}
              >
                SMART<br />JOB<br />TRACKER
              </h2>

              {/* Violet → cyan accent bar */}
              <div
                className="rounded-full mb-5"
                style={{
                  width: 48, height: 4,
                  background: 'linear-gradient(90deg, #a78bfa, #00f2fe)',
                }}
              />

              <p className="text-[14px] leading-relaxed max-w-[34ch]" style={{ color: 'rgba(255,255,255,0.4)' }}>
                Applications, resume matching, and reminders — tracked together instead of scattered across sheets and inboxes.
              </p>
            </div>
          </div>

          {/* Bottom: decorative pill dots */}
          <div className="relative z-10 p-11 pt-0">
            <div className="flex gap-1.5 items-center">
              {['#a78bfa', '#7c3aed', '#4c1d95', '#2e1065'].map((bg, i) => (
                <div
                  key={i}
                  className="rounded-full"
                  style={{ background: bg, width: 28 - i * 5, height: 6 }}
                />
              ))}
            </div>
          </div>
        </div>

        {/* ── Right panel — form ── */}
        <div className="flex items-center justify-center p-6 sm:p-10 lg:p-14">
          <div className="w-full max-w-sm">
            {/* Mobile: show logo above form */}
            <BrandLogo className="w-40 h-auto mb-6 lg:hidden" variant="void" />

            <h1 className="font-display text-2xl sm:text-3xl font-semibold text-white mb-1">
              {heading}
            </h1>
            <p className="text-sm mb-6" style={{ color: 'rgba(255,255,255,0.45)' }}>
              {copy}
            </p>

            {children}
          </div>
        </div>
      </div>
    </div>
  )
}
