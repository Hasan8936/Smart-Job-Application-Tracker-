import React from 'react'
import BrandLogo from './BrandLogo'

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
        {/* Left panel — deep dark with animated orb ring + brand */}
        <div
          className="hidden lg:flex flex-col justify-between p-11 relative overflow-hidden min-h-[640px]"
          style={{
            background: 'linear-gradient(145deg, #000000 0%, #04061a 55%, #08082e 100%)',
          }}
        >
          {/* Ambient glow blobs */}
          <div
            className="absolute pointer-events-none"
            style={{
              width: 340, height: 340,
              top: '8%', left: '50%', transform: 'translateX(-50%)',
              background: 'radial-gradient(circle, rgba(99,102,241,0.12) 0%, transparent 70%)',
              borderRadius: '50%',
            }}
          />
          <div
            className="absolute pointer-events-none"
            style={{
              width: 220, height: 220,
              bottom: '12%', right: '5%',
              background: 'radial-gradient(circle, rgba(236,72,153,0.10) 0%, transparent 70%)',
              borderRadius: '50%',
            }}
          />

          {/* Top brand */}
          <div className="relative z-10 flex items-center gap-2.5">
            <BrandLogo markOnly className="w-7 h-7" />
            <span className="text-white/48 text-sm font-semibold tracking-wide">Smart Job Tracker</span>
          </div>

          {/* Centre: orb ring + wordmark */}
          <div className="relative z-10 my-auto flex flex-col items-center gap-8">
            {/* Iridescent ring */}
            <div
              className="orb-ring-static flex-shrink-0"
              style={{ width: 160, height: 160 }}
              aria-hidden="true"
            />
            {/* Brand wordmark */}
            <div className="text-center -mt-2">
              <div
                className="font-display font-bold text-white tracking-tight leading-[1]"
                style={{ fontSize: 46 }}
              >
                SMART JOB
              </div>
              <div
                className="font-display font-bold orb-text-gradient tracking-tight leading-[1]"
                style={{ fontSize: 46 }}
              >
                TRACKER
              </div>
            </div>
          </div>

          {/* Bottom copy */}
          <div className="relative z-10">
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
