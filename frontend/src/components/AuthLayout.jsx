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
          boxShadow: '0 40px 100px -20px rgba(124,58,237,0.22), 0 0 0 1px rgba(255,255,255,0.04)',
        }}
      >
        {/* ── Left panel — Brand ── */}
        <div
          className="hidden lg:flex flex-col justify-between relative overflow-hidden min-h-[640px]"
          style={{ background: 'linear-gradient(145deg, #040110 0%, #0e0330 55%, #160248 100%)' }}
        >
          {/* Ambient glow blobs */}
          <div
            className="absolute pointer-events-none"
            style={{
              top: '-20%', right: '-10%',
              width: 320, height: 320, borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(124,58,237,0.14) 0%, transparent 70%)',
            }}
          />
          <div
            className="absolute pointer-events-none"
            style={{
              bottom: '-15%', left: '-12%',
              width: 280, height: 280, borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(0,242,254,0.08) 0%, transparent 70%)',
            }}
          />

          {/* Top: logo + app name */}
          <div className="relative z-10 flex items-center gap-3 p-11 pb-0">
            <BrandLogo markOnly variant="void" className="w-8 h-8" />
            <span className="text-white/50 text-sm font-semibold tracking-wide">Smart Job Tracker</span>
          </div>

          {/* Center: SMART JOB TRACKER display text */}
          <div className="relative z-10 px-11">
            {/* Watermark monogram */}
            <div
              className="font-display font-black leading-none select-none mb-5"
              style={{ fontSize: 88, color: 'rgba(255,255,255,0.04)', letterSpacing: '-0.04em' }}
            >
              SJT
            </div>

            <h2
              className="font-display font-bold leading-[0.92] text-white mb-5"
              style={{ fontSize: 38, letterSpacing: '-0.02em' }}
            >
              SMART<br />JOB<br />TRACKER
            </h2>

            {/* Accent bar */}
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

          {/* Bottom: decorative pill strip */}
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
