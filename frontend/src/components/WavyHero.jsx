import React, { lazy, Suspense } from 'react'

const AuroraRing = lazy(() => import('./AuroraRing'))

export default function WavyHero({ scrollTargetId }) {
  return (
    <section
      className="relative min-h-screen overflow-hidden flex flex-col"
      style={{ background: 'radial-gradient(ellipse 70% 70% at 50% 50%, #1c0842 0%, #0e0330 35%, #060118 65%, #040110 100%)' }}
    >
      {/* Full-bleed ring fills the viewport */}
      <div className="absolute inset-0">
        <Suspense fallback={<div style={{ background: '#000', width: '100%', height: '100%' }} />}>
          <AuroraRing height="100%" className="h-full" />
        </Suspense>
      </div>

      {/* Text sits at the bottom of the hero */}
      <div className="relative z-10 mt-auto pb-[20%] sm:pb-[16%] text-center px-6 max-w-2xl mx-auto w-full">
        <h1
          className="leading-[1.08] tracking-[-0.02em] text-[clamp(36px,5.8vw,72px)]"
          style={{
            fontFamily: 'Calistoga, serif',
            fontWeight: 400,
            background: 'linear-gradient(140deg, #ffffff 0%, #ddd6fe 40%, #a78bfa 100%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
          }}
        >
          Track smarter.{' '}
          <em style={{ fontStyle: 'italic' }}>Land faster.</em>
        </h1>
        <p
          className="mt-5 text-[clamp(13px,1.35vw,17px)] leading-relaxed font-sans"
          style={{ color: 'rgba(196, 181, 253, 0.78)', fontFamily: '"Plus Jakarta Sans", sans-serif', fontWeight: 400 }}
        >
          From first application to final offer — AI matching, smart reminders,
          and everything in between.
        </p>
      </div>

      <a
        href={`#${scrollTargetId}`}
        className="absolute bottom-[6%] left-1/2 -translate-x-1/2 z-10 flex flex-col items-center gap-2 text-[11px] tracking-widest uppercase no-underline"
        style={{ color: 'rgba(167, 139, 250, 0.55)' }}
      >
        <span>Sign in</span>
        <span className="h-1.5 w-1.5 rounded-full animate-bounce" style={{ background: 'rgba(167, 139, 250, 0.55)' }} />
      </a>
    </section>
  )
}
