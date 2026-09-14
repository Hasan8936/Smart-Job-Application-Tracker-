import React, { useEffect, useRef } from 'react'

function wrapAngle(a) {
  return ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)
}

function angDist(a, b) {
  const d = Math.abs(wrapAngle(a) - wrapAngle(b))
  return Math.min(d, Math.PI * 2 - d)
}

function ptOnEllipse(cx, cy, rx, ry, angle) {
  return [cx + rx * Math.cos(angle), cy + ry * Math.sin(angle)]
}

export default function WavyHero({ scrollTargetId }) {
  const sectionRef = useRef(null)
  const canvasRef = useRef(null)

  useEffect(() => {
    const section = sectionRef.current
    const canvas = canvasRef.current
    if (!section || !canvas) return
    const ctx = canvas.getContext('2d')
    let w = 0, h = 0, t = 0, frame = null

    // Star field
    const stars = []
    function makeStars() {
      stars.length = 0
      const n = Math.round((w * h) / 7500)
      for (let i = 0; i < n; i++) {
        stars.push({
          x: Math.random() * w, y: Math.random() * h,
          r: Math.random() * 1.1 + 0.15,
          a: Math.random() * 0.5 + 0.08,
          ph: Math.random() * Math.PI * 2,
        })
      }
    }

    function resizeCanvas() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      w = section.clientWidth; h = section.clientHeight
      canvas.width = w * dpr; canvas.height = h * dpr
      canvas.style.width = w + 'px'; canvas.style.height = h + 'px'
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      makeStars()
    }

    function drawStars() {
      for (const s of stars) {
        const alpha = s.a * (0.38 + 0.62 * Math.sin(s.ph + t * 1.05))
        ctx.beginPath()
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${alpha.toFixed(3)})`
        ctx.fill()
      }
    }

    // Draw the multi-band elliptical ring for one pass
    function drawRingPass(cx, cy, rx, ry, spotAngle, bandW, blurPx, alpha) {
      ctx.save()
      if (blurPx > 0) ctx.filter = `blur(${blurPx}px)`
      ctx.globalAlpha = alpha
      ctx.lineCap = 'round'

      const N = 480
      for (let i = 0; i < N; i++) {
        const a1 = (i / N) * Math.PI * 2
        const a2 = ((i + 1.6) / N) * Math.PI * 2

        const [x1, y1] = ptOnEllipse(cx, cy, rx, ry, a1)
        const [x2, y2] = ptOnEllipse(cx, cy, rx, ry, a2)

        // Angular distance from hot-spot (0 = at spot, π = opposite)
        const d = wrapAngle(a1 - spotAngle)        // 0..2π from spot going CW
        const normD = d / (Math.PI * 2)            // 0..1

        // Hue: cyan(190) at spot → spectrum around → back
        // 0..0.5: 190 → -70 = 290 (blue-purple going through green,yellow,red)
        // 0.5..1: 290 back to 190 through deep blue
        const hue = d <= Math.PI
          ? 190 - (d / Math.PI) * 260               // 190 → -70 (=290 purple)
          : -70 + ((d - Math.PI) / Math.PI) * 260  // -70 → 190 (cyan)

        // Lightness: bright at spot, dim opposite
        const prox = Math.max(0, 1 - d / (Math.PI * 1.1))
        const light = 8 + prox * 72

        ctx.beginPath()
        ctx.moveTo(x1, y1)
        ctx.lineTo(x2, y2)
        ctx.strokeStyle = `hsl(${((hue % 360) + 360) % 360},100%,${light.toFixed(1)}%)`
        ctx.lineWidth = bandW
        ctx.stroke()
      }
      ctx.restore()
    }

    function drawOrb() {
      const size = Math.min(w, h)
      const cx   = w / 2
      const cy   = h * 0.365
      const rx   = Math.min(size * 0.27, 230)
      const ry   = rx * 0.82          // tilt — makes circle look like tilted ring
      const BW   = rx * 0.058         // one band width
      const N_BANDS = 8
      const spot = -t * 0.36          // hot spot orbits counter-clockwise

      // ── Interior fill (deep blue radial) ──────────────────
      ctx.save()
      const gf = ctx.createRadialGradient(
        cx, cy - ry * 0.12, rx * 0.04,
        cx, cy, rx - BW * 0.5
      )
      gf.addColorStop(0,   'rgba(40,90,230,0.35)')
      gf.addColorStop(0.45,'rgba(10,25,160,0.55)')
      gf.addColorStop(1,   'rgba(0,4,45,0.82)')
      ctx.beginPath()
      ctx.ellipse(cx, cy, rx - BW, ry - BW, 0, 0, Math.PI * 2)
      ctx.fillStyle = gf
      ctx.fill()
      ctx.restore()

      // ── Glow bloom passes (wide → tight) ───────────────────
      drawRingPass(cx, cy, rx, ry, spot, BW * 4.5, 60, 0.12)
      drawRingPass(cx, cy, rx, ry, spot, BW * 2.5, 28, 0.25)
      drawRingPass(cx, cy, rx, ry, spot, BW * 1.4, 12, 0.45)
      drawRingPass(cx, cy, rx, ry, spot, BW * 0.9,  5, 0.70)

      // ── Multi-band crisp ring ───────────────────────────────
      for (let b = 0; b < N_BANDS; b++) {
        const frac = b / (N_BANDS - 1)
        const br = rx - b * BW * 0.82
        const by = ry - b * BW * 0.82
        const ba = 0.80 - frac * 0.45
        drawRingPass(cx, cy, br, by, spot, BW * 0.48, 1.2, ba)
      }

      // ── Inner radial spokes from hot-spot ─────────────────
      const [sx, sy] = ptOnEllipse(cx, cy, rx - BW * 2.2, ry - BW * 2.2, spot)
      ctx.save()
      ctx.filter = 'blur(1.2px)'
      const N_SPK = 44
      for (let i = 0; i < N_SPK; i++) {
        const spr = i / N_SPK
        // Spokes fan into the interior, angled away from spot
        const sAngle = spot + Math.PI * 0.75 + spr * Math.PI * 1.0
        const dist = 0.22 + spr * 0.68
        const ex = cx + (rx - BW * 3) * Math.cos(sAngle) * dist
        const ey = cy + (ry - BW * 3) * Math.sin(sAngle) * dist
        const a = (1 - spr) * 0.32
        ctx.beginPath()
        ctx.moveTo(sx, sy)
        ctx.lineTo(ex, ey)
        ctx.strokeStyle = `rgba(160,220,255,${a.toFixed(3)})`
        ctx.lineWidth = 0.65
        ctx.stroke()
      }
      ctx.restore()

      // ── Orbiting nodes (2 bright dots on ring inner edge) ──
      const nodeDefs = [
        { offset: 0,          r: 3.8, col: 'rgba(230,252,255,1)',   glow: 'rgba(200,245,255,0.9)', gr: 10 },
        { offset: Math.PI * 1.08, r: 2.6, col: 'rgba(180,220,255,0.92)', glow: 'rgba(140,200,255,0.7)', gr:  7 },
      ]
      for (const nd of nodeDefs) {
        const na = spot + nd.offset
        const [nx, ny] = ptOnEllipse(cx, cy, rx - BW * 1.3, ry - BW * 1.3, na)
        // Glow halo
        ctx.save()
        ctx.filter = `blur(${nd.gr}px)`
        ctx.beginPath()
        ctx.arc(nx, ny, nd.r * 2.2, 0, Math.PI * 2)
        ctx.fillStyle = nd.glow
        ctx.fill()
        ctx.restore()
        // Crisp node
        ctx.beginPath()
        ctx.arc(nx, ny, nd.r, 0, Math.PI * 2)
        ctx.fillStyle = nd.col
        ctx.fill()
      }
    }

    function draw() {
      ctx.clearRect(0, 0, w, h)
      drawStars()
      drawOrb()
      t += 0.0038
    }

    function loop() { draw(); frame = requestAnimationFrame(loop) }

    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
    loop()

    return () => {
      window.removeEventListener('resize', resizeCanvas)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <section
      ref={sectionRef}
      className="relative min-h-screen overflow-hidden flex items-end justify-center pb-[22%] sm:pb-[18%]"
      style={{ background: '#000000' }}
    >
      <canvas ref={canvasRef} className="absolute inset-0 w-full h-full" />

      <div className="relative z-10 text-center px-6 max-w-2xl mx-auto">
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
