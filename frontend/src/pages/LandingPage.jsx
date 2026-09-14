import React, { useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import * as THREE from 'three'
import './landing-page.css'

/* ── Three.js Globe Component ───────────────────────────────── */
function GlobeCanvas() {
  const mountRef = useRef(null)

  useEffect(() => {
    const el = mountRef.current
    if (!el) return

    const W = el.clientWidth, H = el.clientHeight
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(W, H)
    el.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(45, W / H, 0.1, 100)
    camera.position.set(0, 0, 5.8)

    // Lights
    const amb = new THREE.AmbientLight(0x1a0533, 0.9)
    scene.add(amb)
    const d1 = new THREE.DirectionalLight(0xa78bfa, 1.4)
    d1.position.set(3, 5, 3)
    scene.add(d1)
    const d2 = new THREE.DirectionalLight(0x00f2fe, 0.5)
    d2.position.set(-4, -2, 2)
    scene.add(d2)

    // Core icosahedron
    const core = new THREE.Mesh(
      new THREE.IcosahedronGeometry(1.18, 2),
      new THREE.MeshPhongMaterial({
        color: 0x050115, emissive: 0x1a0542, specular: 0xa78bfa,
        shininess: 110, flatShading: true, transparent: true, opacity: 0.92
      })
    )
    scene.add(core)

    // Outer wireframe cage
    const cage = new THREE.Mesh(
      new THREE.IcosahedronGeometry(1.58, 2),
      new THREE.MeshBasicMaterial({ color: 0xa78bfa, wireframe: true, transparent: true, opacity: 0.32 })
    )
    scene.add(cage)

    // Inner cyan wire orb
    const inner = new THREE.Mesh(
      new THREE.SphereGeometry(0.62, 28, 28),
      new THREE.MeshBasicMaterial({ color: 0x00f2fe, wireframe: true, transparent: true, opacity: 0.18 })
    )
    scene.add(inner)

    // Torus rings helper
    function mkRing(radius, tube, rx, ry, color, opacity) {
      const m = new THREE.Mesh(
        new THREE.TorusGeometry(radius, tube, 14, 80),
        new THREE.MeshBasicMaterial({ color, transparent: true, opacity })
      )
      m.rotation.x = rx
      m.rotation.y = ry
      return m
    }
    const r1 = mkRing(2.05, 0.014, Math.PI / 3, 0.35, 0xa78bfa, 0.72)
    const r2 = mkRing(2.35, 0.012, -Math.PI / 4, 0.75, 0x7c3aed, 0.58)
    const r3 = mkRing(2.65, 0.009, Math.PI / 5, -0.6, 0x00f2fe, 0.45)
    scene.add(r1, r2, r3)

    // Orbiting nodes
    const nodeColors = [0xa78bfa, 0x7c3aed, 0x00f2fe, 0xa78bfa, 0x10b981,
      0xa78bfa, 0x00f2fe, 0x7c3aed, 0xa78bfa, 0x00f2fe,
      0x10b981, 0xa78bfa, 0x7c3aed, 0xa78bfa, 0x00f2fe]
    const nodeGroup = new THREE.Group()
    const nodeMeshes = []
    for (let i = 0; i < 15; i++) {
      const phi = Math.acos(-1 + (2 * i) / 15)
      const theta = Math.sqrt(15 * Math.PI) * phi
      const r = 2.05 + Math.random() * 0.6
      const node = new THREE.Mesh(
        new THREE.SphereGeometry(0.055, 8, 8),
        new THREE.MeshBasicMaterial({ color: nodeColors[i % nodeColors.length] })
      )
      node.position.setFromSphericalCoords(r, phi, theta)
      node.userData.baseR = r
      node.userData.phi = phi
      node.userData.theta = theta
      node.userData.speed = 0.3 + Math.random() * 0.4
      node.userData.offset = Math.random() * Math.PI * 2
      nodeMeshes.push(node)
      nodeGroup.add(node)
    }
    scene.add(nodeGroup)

    // Stars
    const starGeo = new THREE.BufferGeometry()
    const starPos = []
    for (let i = 0; i < 120; i++) {
      const phi = Math.random() * Math.PI * 2
      const cosTheta = Math.random() * 2 - 1
      const theta = Math.acos(cosTheta)
      const r = 3.8 + Math.random() * 1.8
      starPos.push(
        r * Math.sin(theta) * Math.cos(phi),
        r * Math.sin(theta) * Math.sin(phi),
        r * Math.cos(theta)
      )
    }
    starGeo.setAttribute('position', new THREE.Float32BufferAttribute(starPos, 3))
    const stars = new THREE.Points(
      starGeo,
      new THREE.PointsMaterial({ color: 0xa78bfa, size: 0.045, transparent: true, opacity: 0.7 })
    )
    scene.add(stars)

    // Mouse parallax
    let mx = 0, my = 0, tx = 0, ty = 0
    const onMouse = (e) => {
      mx = (e.clientX / window.innerWidth - 0.5) * 2
      my = (e.clientY / window.innerHeight - 0.5) * 2
    }
    window.addEventListener('mousemove', onMouse)

    // Resize
    const onResize = () => {
      const w = el.clientWidth, h = el.clientHeight
      renderer.setSize(w, h)
      camera.aspect = w / h
      camera.updateProjectionMatrix()
    }
    window.addEventListener('resize', onResize)

    // Animation loop
    let rafId
    const clock = new THREE.Clock()
    const animate = () => {
      rafId = requestAnimationFrame(animate)
      const t = clock.getElapsedTime()
      tx += (mx - tx) * 0.045
      ty += (my - ty) * 0.045

      core.rotation.y = t * 0.12
      core.rotation.x = t * 0.04 + ty * 0.3
      cage.rotation.y = -t * 0.09
      cage.rotation.x = t * 0.03
      inner.rotation.y = t * 0.18
      r1.rotation.z = t * 0.14
      r2.rotation.z = -t * 0.10
      r3.rotation.z = t * 0.08
      stars.rotation.y = t * 0.02

      nodeMeshes.forEach((n) => {
        const angle = n.userData.theta + t * n.userData.speed + n.userData.offset
        n.position.setFromSphericalCoords(n.userData.baseR, n.userData.phi, angle)
      })

      scene.rotation.y = tx * 0.18
      scene.rotation.x = -ty * 0.12

      renderer.render(scene, camera)
    }
    animate()

    return () => {
      cancelAnimationFrame(rafId)
      window.removeEventListener('mousemove', onMouse)
      window.removeEventListener('resize', onResize)
      renderer.dispose()
      if (el.contains(renderer.domElement)) el.removeChild(renderer.domElement)
    }
  }, [])

  return <div ref={mountRef} style={{ position: 'absolute', inset: 0 }} />
}

/* ── Feature card data ──────────────────────────────────────── */
const FEATURES = [
  {
    icon: '🎯',
    iconBg: 'linear-gradient(135deg,rgba(124,58,237,.3),rgba(167,139,250,.15))',
    title: 'AI Resume Matching',
    desc: 'Gemini-powered semantic analysis matches your resume to any job description — ranked score, matched keywords, and actionable missing skills.',
    detail: { bg: 'rgba(124,58,237,.1)', border: 'rgba(124,58,237,.25)', text: '94% avg match accuracy', sub: 'vs. manual keyword scan' },
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
    desc: 'Never miss a follow-up. Get interview reminders via Email, WhatsApp, and Telegram — fully customisable timing per application.',
    chips: ['Email', 'WhatsApp', 'Telegram'],
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
  { num: '03', color: 'linear-gradient(135deg,#065f46,#10b981)', title: 'Track & Get Reminded', desc: 'Every stage update, follow-up, and interview lands via email, WhatsApp, or Telegram.' },
  { num: '04', color: 'linear-gradient(135deg,#92400e,#f59e0b)', title: 'Land Your Offer', desc: 'AI prep, resume tailoring, and a complete audit trail — so you walk in confident.' },
]

const STATS = [
  { num: '94%', label: 'Match Accuracy', sub: 'Resume ↔ JD alignment', color: '#a78bfa' },
  { num: '8h', label: 'Weekly Time Saved', sub: 'vs. manual tracking', color: '#00f2fe' },
  { num: '3×', label: 'More Interviews', sub: 'with AI-optimised resume', color: '#10b981' },
  { num: 'Free', label: 'To Get Started', sub: 'No credit card required', color: '#f59e0b' },
]

/* ── Landing Page ───────────────────────────────────────────── */
export default function LandingPage() {
  const navigate = useNavigate()
  const n1Ref = useRef(null)
  const n2Ref = useRef(null)
  const n3Ref = useRef(null)

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
          <div className="lp-logo">
            <div className="lp-logo-mark">
              <svg viewBox="0 0 20 20"><path d="M10 2L3 7v6l7 5 7-5V7z"/></svg>
            </div>
            <span className="lp-logo-name lp-calistoga">Smart Job Tracker</span>
          </div>
          <nav className="lp-nav">
            <a href="#features">Features</a>
            <a href="#how-it-works">How it works</a>
            <a href="#stats">Why us</a>
          </nav>
          <div className="lp-nav-cta">
            <Link to="/login" className="lp-sign-in">Sign in</Link>
            <Link to="/register" className="lp-btn lp-btn-primary lp-btn-sm">Get started free</Link>
          </div>
        </div>
      </header>

      {/* ── Hero ── */}
      <section className="lp-hero">
        <div className="lp-dot-grid" />
        <div className="lp-globe-wrap">
          <GlobeCanvas />
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
            From first application to final offer — AI matching, smart reminders, and everything in between.
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
                <h4>{s.title}</h4>
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
            <div className="lp-eyebrow">By the numbers</div>
            <h2 className="lp-section-h lp-calistoga lp-grad">Results that speak for themselves</h2>
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

      {/* ── CTA ── */}
      <section className="lp-cta">
        <div className="lp-wrap">
          <div className="lp-cta-card">
            <div className="lp-cta-glow-1" />
            <div className="lp-cta-glow-2" />
            <div className="lp-cta-inner">
              <h2 className="lp-cta-h lp-calistoga lp-grad">Ready to land faster?</h2>
              <p className="lp-cta-sub">
                Join job seekers using AI to cut their search time in half — free, forever.
              </p>
              <Link to="/register" className="lp-btn lp-btn-primary lp-btn-lg">
                Create your free account →
              </Link>
              <div className="lp-trust-row">
                <span className="lp-trust-item">✓ No credit card</span>
                <span className="lp-trust-item">✓ Free forever tier</span>
                <span className="lp-trust-item">✓ Cancel anytime</span>
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
              <div className="lp-logo" style={{ marginBottom: 12 }}>
                <div className="lp-logo-mark" style={{ width: 30, height: 30, borderRadius: 8 }}>
                  <svg viewBox="0 0 20 20" style={{ width: 15, height: 15, fill: '#fff' }}>
                    <path d="M7 10l2 2 4-4M17 10a7 7 0 11-14 0 7 7 0 0114 0z" strokeWidth="0"/>
                    <path fill="none" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" d="M7 10l2 2 4-4"/>
                    <circle cx="10" cy="10" r="8" fill="none" stroke="#fff" strokeWidth="1.4"/>
                  </svg>
                </div>
                <span className="lp-logo-name lp-calistoga" style={{ fontSize: 15 }}>Smart Job Tracker</span>
              </div>
              <p className="lp-foot-tagline">
                An AI-powered pipeline for job applications — discovery, tracking, and interview reminders in one board.
              </p>
            </div>

            {/* Link columns */}
            <div className="lp-foot-cols">
              <div className="lp-foot-col">
                <div className="lp-foot-col-head">Product</div>
                <Link to="/discovery">Job Discovery</Link>
                <a href="#how-it-works">How it works</a>
                <Link to="/applications">Application Board</Link>
              </div>
              <div className="lp-foot-col">
                <div className="lp-foot-col-head">Project</div>
                <a href="https://github.com/Hasan8936/Smart-Job-Application-Tracker-" target="_blank" rel="noreferrer">GitHub repository</a>
                <Link to="/privacy">Privacy Policy</Link>
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
