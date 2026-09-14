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

          {/* Notification cards — floating in centre of left panel */}
          <div className="relative z-10 my-auto" style={{ minHeight: 340 }}>

            {/* Subtle centre glow */}
            <div style={{
              position: 'absolute', top: '50%', left: '50%',
              transform: 'translate(-50%,-50%)',
              width: 260, height: 260,
              background: 'radial-gradient(circle, rgba(124,58,237,.18) 0%, transparent 70%)',
              borderRadius: '50%', pointerEvents: 'none',
            }} />

            {/* Card 1 — top-left: AI Recommendation */}
            <div style={{
              position: 'absolute', top: 0, left: 0,
              background: 'rgba(255,255,255,.04)',
              backdropFilter: 'blur(14px)',
              border: '1px solid rgba(139,92,246,.3)',
              borderRadius: 14, padding: '12px 14px', minWidth: 200,
              animation: 'authCardA 5.4s ease-in-out infinite',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#a78bfa', boxShadow: '0 0 8px #a78bfa', flexShrink: 0 }} />
                <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.06em', textTransform: 'uppercase', color: 'rgba(167,139,250,.8)' }}>AI Recommendation</span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#fff', marginBottom: 2 }}>3 new roles match your profile</div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 11, color: 'rgba(196,181,253,.55)' }}>Just now</span>
                <span style={{ fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: 'rgba(167,139,250,.15)', border: '1px solid rgba(167,139,250,.3)', color: '#a78bfa' }}>NEW</span>
              </div>
            </div>

            {/* Card 2 — top-right: Job Match Found */}
            <div style={{
              position: 'absolute', top: 10, right: 0,
              background: 'rgba(255,255,255,.04)',
              backdropFilter: 'blur(14px)',
              border: '1px solid rgba(16,185,129,.28)',
              borderRadius: 14, padding: '12px 14px', minWidth: 200,
              animation: 'authCardB 4.8s ease-in-out .5s infinite',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981', flexShrink: 0 }} />
                <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.06em', textTransform: 'uppercase', color: 'rgba(16,185,129,.8)' }}>Job Match Found</span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#fff', marginBottom: 6 }}>Frontend Developer at Stripe</div>
              <div style={{ height: 4, background: 'rgba(255,255,255,.08)', borderRadius: 2, marginBottom: 6, overflow: 'hidden' }}>
                <div style={{ width: '94%', height: '100%', background: 'linear-gradient(90deg,#7c3aed,#10b981)', borderRadius: 2 }} />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 12, fontWeight: 700, color: '#10b981' }}>94%</span>
                <span style={{ fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: 'rgba(16,185,129,.14)', border: '1px solid rgba(16,185,129,.3)', color: '#10b981' }}>MATCH</span>
              </div>
            </div>

            {/* Card 3 — bottom-left: Interview Reminder */}
            <div style={{
              position: 'absolute', bottom: 0, left: 0,
              background: 'rgba(255,255,255,.04)',
              backdropFilter: 'blur(14px)',
              border: '1px solid rgba(245,158,11,.28)',
              borderRadius: 14, padding: '12px 14px', minWidth: 210,
              animation: 'authCardC 5.8s ease-in-out 1s infinite',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#f59e0b', boxShadow: '0 0 8px #f59e0b', flexShrink: 0 }} />
                <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.06em', textTransform: 'uppercase', color: 'rgba(245,158,11,.8)' }}>Interview Reminder</span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#fff', marginBottom: 2 }}>Technical round tomorrow at 10:00 AM</div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 11, color: 'rgba(196,181,253,.55)' }}>Tomorrow</span>
                <span style={{ fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: 'rgba(245,158,11,.14)', border: '1px solid rgba(245,158,11,.3)', color: '#f59e0b' }}>SCHEDULED</span>
              </div>
            </div>

            {/* Card 4 — bottom-right: Application Update */}
            <div style={{
              position: 'absolute', bottom: 10, right: 0,
              background: 'rgba(255,255,255,.04)',
              backdropFilter: 'blur(14px)',
              border: '1px solid rgba(56,189,248,.28)',
              borderRadius: 14, padding: '12px 14px', minWidth: 200,
              animation: 'authCardB 5.2s ease-in-out 1.6s infinite',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#38bdf8', boxShadow: '0 0 8px #38bdf8', flexShrink: 0 }} />
                <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.06em', textTransform: 'uppercase', color: 'rgba(56,189,248,.8)' }}>Application Update</span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#fff', marginBottom: 2 }}>Moved to technical interview stage</div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 11, color: 'rgba(196,181,253,.55)' }}>1 hr ago</span>
                <span style={{ fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: 'rgba(56,189,248,.14)', border: '1px solid rgba(56,189,248,.3)', color: '#38bdf8' }}>INTERVIEW</span>
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
