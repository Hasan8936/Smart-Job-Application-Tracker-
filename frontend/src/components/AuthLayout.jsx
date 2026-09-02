import React from 'react'
import { Briefcase, CheckCircle2, TrendingUp, Target } from 'lucide-react'
import { STATUS_META } from '../lib/status'
import BrandLogo from './BrandLogo'
import RobotMascot from './RobotMascot'

const previewStages = ['APPLIED', 'OA', 'INTERVIEW', 'OFFER']

export default function AuthLayout({ heading, copy, children }) {
  return (
    <div className="min-h-screen bg-hero-gradient flex items-center justify-center p-3 sm:p-6 lg:p-10">
      <div className="w-full max-w-5xl bg-surface rounded-xl3 shadow-floaty overflow-hidden grid lg:grid-cols-2">
        <div className="hidden lg:flex flex-col justify-between bg-hero-gradient p-10 relative overflow-hidden">
          <BrandLogo className="w-52 h-auto relative z-10" />

          <div className="relative flex-1 flex items-center justify-center my-6">
            <div className="absolute h-64 w-64 rounded-full bg-white/40 blur-2xl" />
            <RobotMascot className="relative z-10 w-56 h-auto drop-shadow-xl" />

            <div className="absolute top-2 left-0 w-36 rounded-2xl bg-white/90 backdrop-blur shadow-soft p-3 -rotate-6">
              <div className="flex items-center gap-1.5 text-accent">
                <Briefcase size={14} />
                <span className="text-[11px] font-semibold text-ink">Application</span>
              </div>
              <div className="mt-2 space-y-1">
                <div className="h-1.5 rounded-full bg-line w-full" />
                <div className="h-1.5 rounded-full bg-line w-2/3" />
              </div>
              <div className="mt-2 flex items-center gap-1 text-status-offer text-[10px] font-medium">
                <CheckCircle2 size={12} /> Tracked
              </div>
            </div>

            <div className="absolute bottom-4 left-2 w-32 rounded-2xl bg-white/90 backdrop-blur shadow-soft p-3 rotate-3">
              <div className="flex items-center gap-1.5 text-status-interview">
                <TrendingUp size={14} />
                <span className="text-[11px] font-semibold text-ink">Pipeline</span>
              </div>
              <div className="mt-2 flex items-end gap-1 h-8">
                {[40, 65, 45, 80, 60].map((h, i) => (
                  <div key={i} className="flex-1 rounded-t bg-accent/70" style={{ height: `${h}%` }} />
                ))}
              </div>
            </div>

            <div className="absolute bottom-0 right-0 w-28 rounded-2xl bg-white/90 backdrop-blur shadow-soft p-3 rotate-6">
              <div className="flex items-center gap-1.5 text-status-offer">
                <Target size={14} />
                <span className="text-[11px] font-semibold text-ink">Match</span>
              </div>
              <div className="mt-1 text-lg font-mono font-semibold text-ink">86%</div>
            </div>
          </div>

          <div>
            <p className="font-display text-3xl leading-snug max-w-sm text-ink">
              Track every application. Get hired faster.
            </p>
            <p className="text-ink-soft mt-3 max-w-sm text-sm">
              Applications, resume matching, and reminders — tracked together instead of scattered across sheets and inboxes.
            </p>

            <div className="mt-6 flex items-center gap-2">
              {previewStages.map((s) => (
                <span key={s} className={`h-1.5 flex-1 rounded-full ${STATUS_META[s].dot} opacity-80`} />
              ))}
            </div>
            <div className="mt-2 flex justify-between text-xs text-ink-soft font-mono">
              <span>Applied</span>
              <span>Offer</span>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-center p-6 sm:p-10 lg:p-12">
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
