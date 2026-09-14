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
          boxShadow: '0 40px 100px -20px rgba(59,130,246,0.18), 0 0 0 1px rgba(255,255,255,0.04)',
        }}
      >
        {/* Left panel — Aurora orb + notification cards */}
        <div
          className="hidden lg:flex flex-col justify-between relative overflow-hidden min-h-[640px]"
          style={{ background: 'linear-gradient(145deg, #000000 0%, #04061a 55%, #08082e 100%)' }}
        >
          {/* AuroraRing fills the entire panel */}
          <div className="absolute inset-0">
            <Suspense fallback={<div style={{ background: '#000', width: '100%', height: '100%' }} />}>
              <AuroraRing height="100%" className="h-full" />
            </Suspense>
          </div>

          {/* Brand — top overlay */}
          <div className="relative z-10 flex items-center gap-2.5 p-11 pb-0">
            <BrandLogo markOnly className="w-7 h-7" />
            <span className="text-white/48 text-sm font-semibold tracking-wide">Smart Job Tracker</span>
          </div>

          {/* Bottom copy overlay */}
          <div className="relative z-10 p-11 pt-0">
            <h2 className="font-display text-[24px] font-semibold text-white mb-2.5">
              Track every application
            </h2>
            <p className="text-[14px] leading-relaxed max-w-[38ch]" style={{ color: 'rgba(255,255,255,0.45)' }}>
              Applications, resume matching, and reminders — tracked together instead of scattered across sheets and inboxes.
            </p>
          </div>
        </div>

        {/* Right panel — form */}
        <div className="flex items-center justify-center p-6 sm:p-10 lg:p-14">
          <div className="w-full max-w-sm">
            {/* Mobile logo */}
            <BrandLogo className="w-40 h-auto mb-6 lg:hidden" variant="dark" />

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
