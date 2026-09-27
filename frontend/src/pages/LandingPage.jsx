import React, { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { usePageMeta, useJsonLd } from '../lib/pageMeta'
import { Link, useNavigate } from 'react-router-dom'
import BrandLogo from '../components/BrandLogo'
import './landing-page.css'

// Lazy: keeps three.js out of the landing chunk so the hero text (LCP) renders without waiting for it.
const GlobeCanvas = lazy(() => import('../components/GlobeCanvas'))

/* ── Feature card data ──────────────────────────────────────── */
const FEATURES = [
  {
    icon: '🎯',
    iconBg: 'linear-gradient(135deg,rgba(124,58,237,.3),rgba(167,139,250,.15))',
    title: 'AI Resume Matching',
    desc: 'Gemini-powered semantic analysis matches your resume to any job description — ranked score, matched keywords, and actionable missing skills.',
    detail: { bg: 'rgba(124,58,237,.1)', border: 'rgba(124,58,237,.25)', text: 'Explainable score', sub: 'Matched and missing skills listed' },
  },
  {
    icon: '📋',
    iconBg: 'linear-gradient(135deg,rgba(0,242,254,.2),rgba(56,189,248,.1))',
    title: 'Smart Application Pipeline',
    desc: 'Track every application across Applied → OA → Interview → Offer — with full status history, notes, and one-click stage updates.',
    chips: ['Applied', 'OA', 'Interview', 'Offer'],
  },
  {
    icon: '🔔',
    iconBg: 'linear-gradient(135deg,rgba(16,185,129,.2),rgba(56,189,248,.1))',
    title: 'Multi-Channel Reminders',
    desc: 'Never miss a follow-up. Get interview and follow-up reminders by email and WhatsApp, with timing you set per application.',
    chips: ['Email', 'WhatsApp'],
  },
  {
    icon: '🔍',
    iconBg: 'linear-gradient(135deg,rgba(167,139,250,.2),rgba(124,58,237,.1))',
    title: 'Job Discovery Engine',
    desc: 'Live job postings from Greenhouse, Lever, and Ashby company career pages — de-duplicated, normalised, and tailored to your profile.',
    chips: ['Greenhouse', 'Lever', 'Ashby'],
  },
  {
    icon: '📧',
    iconBg: 'linear-gradient(135deg,rgba(245,158,11,.2),rgba(251,191,36,.1))',
    title: 'Gmail Auto-Sync',
    desc: 'Connect Gmail and let AI classify recruitment emails — interview invites, rejections, and status updates flow straight into your pipeline.',
    detail: { bg: 'rgba(245,158,11,.08)', border: 'rgba(245,158,11,.2)', text: 'Auto-import from inbox', sub: 'No manual entry needed' },
  },
  {
    icon: '🎤',
    iconBg: 'linear-gradient(135deg,rgba(139,92,246,.25),rgba(0,242,254,.12))',
    title: 'AI Interview Prep',
    desc: 'Role-specific question banks, answer frameworks, and Gemini feedback — tailored to the exact JD and your candidate profile.',
    detail: { bg: 'rgba(139,92,246,.1)', border: 'rgba(139,92,246,.25)', text: 'Questions from your JD', sub: 'Personalised to your resume' },
  },
]

const STEPS = [
  { num: '01', color: 'linear-gradient(135deg,#7c3aed,#a78bfa)', title: 'Upload Your Resume', desc: 'Drop your PDF or DOCX and we extract your skills, experience, and profile automatically.' },
  { num: '02', color: 'linear-gradient(135deg,#0e7490,#00f2fe)', title: 'Discover & Apply', desc: 'Browse live postings from Greenhouse, Lever, and Ashby filtered for your target roles.' },
  { num: '03', color: 'linear-gradient(135deg,#065f46,#10b981)', title: 'Track & Get Reminded', desc: 'Every stage update, follow-up, and interview lands via email or WhatsApp.' },
  { num: '04', color: 'linear-gradient(135deg,#92400e,#f59e0b)', title: 'Land Your Offer', desc: 'AI prep, resume tailoring, and a complete audit trail — so you walk in confident.' },
]

// Product facts only — no outcome claims we can't measure.
const STATS = [
  { num: '6', label: 'Pipeline Stages', sub: 'Applied to Offer, plus Rejected and Withdrawn', color: '#a78bfa' },
  { num: '3', label: 'Career-Page Sources', sub: 'Greenhouse · Lever · Ashby', color: '#00f2fe' },
  { num: '2', label: 'Reminder Channels', sub: 'Email and WhatsApp', color: '#10b981' },
  { num: 'Free', label: 'To Get Started', sub: 'No credit card required', color: '#f59e0b' },
]

export const FAQS = [
  { q: 'What is Smart Job Tracker?', a: 'Smart Job Tracker is a free web app for managing a job search. It tracks each application through Applied, OA, Interview, Offer, Rejected, or Withdrawn, matches your resume against job descriptions, finds new postings, and sends interview and follow-up reminders.' },
  { q: 'Is Smart Job Tracker free?', a: 'Yes. You can create an account with email or Google and use it without a credit card.' },
  { q: 'How is the resume match score calculated?', a: 'The score combines exact matching of skills found in your resume (required skills weigh more than preferred ones), semantic similarity between your resume and the job description, and experience and role relevance. It lists matched and missing skills, and the score is an estimate, not a hiring prediction.' },
  { q: 'Where do the job listings come from?', a: 'Listings come from public company career boards on Greenhouse, Lever, and Ashby, along with other configured job sources. Each listing links to the original posting, where you apply. Smart Job Tracker is not affiliated with these platforms.' },
  { q: 'Does Smart Job Tracker read my Gmail?', a: 'Only if you connect Gmail. It uses read-only access to find job-related emails, such as interview invites and rejections, and suggests status updates. Access tokens are encrypted at rest, and you can disconnect Gmail from the dashboard at any time.' },
  { q: 'What happens to my resume?', a: 'The text is extracted from your PDF or DOCX and stored in your account for matching and profile building. The original file is not kept, and your resume is never modified.' },
  { q: 'Will the AI invent skills or experience?', a: 'No. Resume tailoring and profile features only rephrase or re-emphasize information that already appears in your resume.' },
]

const FAQ_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'FAQPage',
  mainEntity: FAQS.map((f) => ({ '@type': 'Question', name: f.q, acceptedAnswer: { '@type': 'Answer', text: f.a } })),
}

/* ── Landing Page ───────────────────────────────────────────── */
export default function LandingPage() {
  usePageMeta({ title: 'Smart Job Tracker – Free AI Job Application Tracker & Resume Matcher', description: 'Free AI job application tracker: match your resume to job descriptions, discover jobs from Greenhouse, Lever and Ashby, and get interview reminders by email and WhatsApp.', path: '/' })
  useJsonLd('ld-faq', FAQ_JSON_LD)
  const navigate = useNavigate()
  const n1Ref = useRef(null)
  const n2Ref = useRef(null)
  const n3Ref = useRef(null)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  // Redirect authenticated users
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (token) navigate('/dashboard', { replace: true })
  }, [navigate])

  // Staggered card fade-in
  useEffect(() => {
    const timers = [
      setTimeout(() => n1Ref.current?.classList.add('lp-in'), 600),
      setTimeout(() => n3Ref.current?.classList.add('lp-in'), 820),
      setTimeout(() => n2Ref.current?.classList.add('lp-in'), 1050),
    ]
    return () => timers.forEach(clearTimeout)
  }, [])

  return (
    <div className="lp">
      {/* ── Header ── */}
      <header className="lp-header">
        <div className="lp-header-inner">
          <BrandLogo className="lp-brand-logo" variant="void" />
          <nav className="lp-nav">
            <a href="#features">Features</a>
            <a href="#how-it-works">How it works</a>
            <a href="#faq">FAQ</a>
          </nav>
          <div className="lp-nav-cta">
            <Link to="/login" className="lp-sign-in">Sign in</Link>
            <Link to="/register" className="lp-btn lp-btn-primary lp-btn-sm">Get started free</Link>
          </div>
          {/* Mobile hamburger — only visible ≤600px */}
          <button
            className="lp-mobile-menu-btn"
            onClick={() => setMobileMenuOpen(true)}
            aria-label="Open menu"
          >
            <span className="lp-ham-line" />
            <span className="lp-ham-line" />
            <span className="lp-ham-line" />
          </button>
        </div>
      </header>

      {/* ── Mobile nav overlay ── */}
      {mobileMenuOpen && (
        <div className="lp-mobile-menu" role="dialog" aria-modal="true">
          <div className="lp-mobile-menu-top">
            <BrandLogo className="lp-mobile-menu-logo" variant="void" />
            <button
              className="lp-mobile-menu-close"
              onClick={() => setMobileMenuOpen(false)}
              aria-label="Close menu"
            >
              ✕
            </button>
          </div>
          <nav className="lp-mobile-nav">
            <a href="#features" onClick={() => setMobileMenuOpen(false)}>Features</a>
            <a href="#how-it-works" onClick={() => setMobileMenuOpen(false)}>How it works</a>
            <a href="#faq" onClick={() => setMobileMenuOpen(false)}>FAQ</a>
          </nav>
          <div className="lp-mobile-menu-cta">
            <Link to="/login" onClick={() => setMobileMenuOpen(false)} className="lp-btn lp-btn-ghost lp-btn-lg" style={{width:'100%',justifyContent:'center'}}>Sign in</Link>
            <Link to="/register" onClick={() => setMobileMenuOpen(false)} className="lp-btn lp-btn-primary lp-btn-lg" style={{width:'100%',justifyContent:'center'}}>Get started free</Link>
          </div>
        </div>
      )}

      {/* ── Hero ── */}
      <section className="lp-hero">
        <div className="lp-dot-grid" />
        <div className="lp-globe-wrap">
          <Suspense fallback={null}><GlobeCanvas /></Suspense>
        </div>
        <div className="lp-vignette" />

        {/* Notification card 1 — Match Score */}
        <div ref={n1Ref} className="lp-notif lp-glass lp-n1">
          <div className="lp-notif-label"><span className="lp-ndot lp-ndot-g" />AI Match Score</div>
          <div className="lp-notif-title">Software Engineer at Stripe</div>
          <div className="lp-notif-sub">Skills analysis complete</div>
          <div className="lp-mtrack"><div className="lp-mfill" style={{ width: '94%' }} /></div>
          <div className="lp-chip lp-chip-g" style={{ marginTop: 8 }}>94% match</div>
        </div>

        {/* Notification card 2 — Interview Reminder */}
        <div ref={n2Ref} className="lp-notif lp-glass lp-n2">
          <div className="lp-notif-label"><span className="lp-ndot lp-ndot-b" />Interview Reminder</div>
          <div className="lp-notif-title">Technical Round · Google</div>
          <div className="lp-notif-sub">Tomorrow 2PM</div>
          <div className="lp-chip lp-chip-b">Reminder set</div>
        </div>

        {/* Notification card 3 — Status Update */}
        <div ref={n3Ref} className="lp-notif lp-glass lp-n3">
          <div className="lp-notif-label"><span className="lp-ndot lp-ndot-v" />Application Update</div>
          <div className="lp-notif-title">Meta · Product Designer</div>
          <div className="lp-notif-sub">Status changed</div>
          <div className="lp-chip lp-chip-v">Interview Stage</div>
        </div>

        {/* Hero text */}
        <div className="lp-hero-text">
          <div className="lp-badge"><span className="lp-badge-dot" />AI-Powered Job Tracking</div>
          <h1 className="lp-h1 lp-calistoga lp-grad">
            Track smarter.<br /><em>Land faster.</em>
          </h1>
          <p className="lp-sub">
            A free AI job application tracker — from first application to final offer, with resume matching, smart reminders, and everything in between.
          </p>
          <div className="lp-ctas">
            <Link to="/register" className="lp-btn lp-btn-primary lp-btn-lg">Start for free →</Link>
            <Link to="/login" className="lp-btn lp-btn-ghost lp-btn-lg">Sign in</Link>
          </div>
        </div>

        <div className="lp-scroll-hint">
          <span>Discover</span>
          <div className="lp-bounce" />
        </div>
      </section>

      {/* ── Trust logos ── */}
      <section className="lp-logos">
        <div className="lp-logos-eyebrow">Works with jobs posted on</div>
        <div className="lp-logos-row">
          {['Greenhouse', 'Lever', 'Ashby', 'Gmail', 'Google Calendar', 'WhatsApp'].map(co => (
            <span key={co} className="lp-co">{co}</span>
          ))}
        </div>
      </section>

      {/* ── Features ── */}
      <section className="lp-features" id="features">
        <div className="lp-wrap">
          <div className="lp-feat-head">
            <div className="lp-eyebrow">Features</div>
            <h2 className="lp-section-h lp-calistoga lp-grad">Everything your job search needs</h2>
            <p className="lp-section-sub" style={{ marginInline: 'auto', marginTop: 14 }}>
              One dashboard — from discovery to offer — powered by Gemini AI.
            </p>
          </div>
          <div className="lp-grid-3">
            {FEATURES.map((f) => (
              <div key={f.title} className="lp-feat-card">
                <div className="lp-feat-icon" style={{ background: f.iconBg }}>{f.icon}</div>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
                {f.detail && (
                  <div className="lp-feat-detail" style={{ background: f.detail.bg, border: `1px solid ${f.detail.border}` }}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 700, color: '#fff' }}>{f.detail.text}</div>
                      <div style={{ fontSize: 11, color: 'rgba(196,181,253,.7)' }}>{f.detail.sub}</div>
                    </div>
                  </div>
                )}
                {f.chips && (
                  <div className="lp-chip-row">
                    {f.chips.map(c => <span key={c} className="lp-tag">{c}</span>)}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Steps ── */}
      <section className="lp-steps" id="how-it-works">
        <div className="lp-steps-wrap">
          <div style={{ textAlign: 'center', marginBottom: 8 }}>
            <div className="lp-eyebrow">How it works</div>
            <h2 className="lp-section-h lp-calistoga lp-grad">Four steps to your next offer</h2>
          </div>
          <div className="lp-step-grid">
            {STEPS.map((s) => (
              <div key={s.num} className="lp-step-card">
                <div className="lp-step-num" style={{ background: s.color }}>{s.num}</div>
                <h3>{s.title}</h3>
                <p>{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Stats ── */}
      <section className="lp-stats" id="stats">
        <div className="lp-wrap">
          <div style={{ textAlign: 'center', marginBottom: 52 }}>
            <div className="lp-eyebrow">At a glance</div>
            <h2 className="lp-section-h lp-calistoga lp-grad">What you get</h2>
          </div>
          <div className="lp-grid-4">
            {STATS.map((s) => (
              <div key={s.label} className="lp-stat-card">
                <div className="lp-stat-num lp-outfit" style={{ color: s.color }}>{s.num}</div>
                <div className="lp-stat-label">{s.label}</div>
                <div className="lp-stat-sub">{s.sub}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── FAQ ── */}
      <section className="lp-faq" id="faq">
        <div className="lp-wrap">
          <div style={{ textAlign: 'center', marginBottom: 40 }}>
            <div className="lp-eyebrow">FAQ</div>
            <h2 className="lp-section-h lp-calistoga lp-grad">Frequently asked questions</h2>
          </div>
          <div className="lp-faq-list">
            {FAQS.map((f) => (
              <details key={f.q} className="lp-faq-item">
                <summary><h3>{f.q}</h3></summary>
                <p>{f.a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section className="lp-cta">
        <div className="lp-wrap">
          <div className="lp-cta-card">
            <div className="lp-cta-glow-1" />
            <div className="lp-cta-glow-2" />
            <div className="lp-cta-inner">
              <h2 className="lp-cta-h lp-calistoga lp-grad">Ready to land faster?</h2>
              <p className="lp-cta-sub">
                Keep every application, resume match, and reminder in one place — free to use.
              </p>
              <Link to="/register" className="lp-btn lp-btn-primary lp-btn-lg">
                Create your free account →
              </Link>
              <div className="lp-trust-row">
                <span className="lp-trust-item">✓ No credit card</span>
                <span className="lp-trust-item">✓ Free to use</span>
                <span className="lp-trust-item">✓ Sign up with Google</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="lp-footer-v2">
        <div className="lp-wrap">
          <div className="lp-foot-top">
            {/* Brand + tagline */}
            <div className="lp-foot-brand">
              <BrandLogo className="lp-foot-logo" variant="void" />
              <p className="lp-foot-tagline">
                An AI-powered pipeline for job applications — discovery, tracking, and interview reminders in one board.
              </p>
            </div>

            {/* Link columns */}
            <div className="lp-foot-cols">
              <div className="lp-foot-col">
                <div className="lp-foot-col-head">Product</div>
                <a href="#features">Features</a>
                <a href="#how-it-works">How it works</a>
                <a href="#faq">FAQ</a>
              </div>
              <div className="lp-foot-col">
                <div className="lp-foot-col-head">Project</div>
                <a href="https://github.com/Hasan8936/Smart-Job-Application-Tracker-" target="_blank" rel="noreferrer">GitHub repository</a>
                <Link to="/privacy">Privacy Policy</Link>
                <Link to="/terms">Terms &amp; Conditions</Link>
              </div>
              <div className="lp-foot-col">
                <div className="lp-foot-col-head">Account</div>
                <Link to="/login">Log in</Link>
                <Link to="/register">Sign up</Link>
              </div>
            </div>
          </div>

          {/* Bottom bar */}
          <div className="lp-foot-bottom">
            <span>Built with Spring Boot, Postgres, and Gemini AI.</span>
            <span>Independent project — not affiliated with Greenhouse, Lever or Ashby.</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
