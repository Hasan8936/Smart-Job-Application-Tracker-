import React, { lazy, Suspense } from 'react'

const AuroraRing = lazy(() => import('./AuroraRing'))

export default function WavyHero({ scrollTargetId }) {
  return (
    <section
      className="relative min-h-screen overflow-hidden flex flex-col"
      style={{ background: '#000000' }}
    >
      {/* Full-bleed ring fills the viewport */}
      <div className="absolute inset-0">
        <Suspense fallback={<div style={{ background: '#000', width: '100%', height: '100%' }} />}>
          <AuroraRing height="100%" className="h-full" />
        </Suspense>
      </div>

      {/* Text sits at the bottom of the hero */}
      <div className="relative z-10 mt-auto pb-[20%] sm:pb-[16%] text-center px-6 max-w-2xl mx-auto w-full">
        <h1 className="font-display font-bold text-white leading-tight tracking-tight text-[clamp(28px,5vw,60px)]">
          Run your whole job&nbsp;search from one place.
        </h1>
        <p className="mt-4 text-white/55 text-[clamp(13px,1.4vw,17px)] leading-relaxed">
          Applications, resume matching, and follow-up reminders — all in one tracker.
        </p>
      </div>

      <a
        href={`#${scrollTargetId}`}
        className="absolute bottom-[6%] left-1/2 -translate-x-1/2 z-10 flex flex-col items-center gap-2 text-white/35 text-[11px] tracking-widest uppercase no-underline"
      >
        <span>Sign in</span>
        <span className="h-1.5 w-1.5 rounded-full bg-white/35 animate-bounce" />
      </a>
    </section>
  )
}
