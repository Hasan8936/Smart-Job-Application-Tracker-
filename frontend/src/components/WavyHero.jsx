import React, { useEffect, useRef } from 'react'

export default function WavyHero({ scrollTargetId }) {
  const sectionRef = useRef(null)
  const canvasRef = useRef(null)

  useEffect(() => {
    const section = sectionRef.current
    const canvas = canvasRef.current
    if (!section || !canvas) return
    const ctx = canvas.getContext('2d')
    let w = 0, h = 0, t = 0, frame = null

    const stars = []
    function makeStars() {
      stars.length = 0
      const n = Math.round((w * h) / 6000)
      for (let i = 0; i < n; i++) {
        stars.push({
          x: Math.random() * w,
          y: Math.random() * h,
          r: Math.random() * 1.3 + 0.2,
          a: Math.random() * 0.55 + 0.1,
          phase: Math.random() * Math.PI * 2,
        })
      }
    }

    function resizeCanvas() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      w = section.clientWidth
      h = section.clientHeight
      canvas.width = w * dpr
      canvas.height = h * dpr
      canvas.style.width = w + 'px'
      canvas.style.height = h + 'px'
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      makeStars()
    }

    function drawStars() {
      for (const s of stars) {
        const alpha = s.a * (0.45 + 0.55 * Math.sin(s.phase + t * 1.2))
        ctx.beginPath()
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255,255,255,${alpha.toFixed(3)})`
        ctx.fill()
      }
    }

    function drawOrb() {
      const size = Math.min(w, h)
      const cx = w / 2
      const cy = h * 0.36
      const outerR = Math.min(size * 0.25, 220)
      const ringW = outerR * 0.23
      const N = 360
      const rot = t * 0.22

      // Glow layers → crisp layer
      const passes = [
        { blur: 48, alpha: 0.18, extra: 22 },
        { blur: 22, alpha: 0.38, extra: 10 },
        { blur:  8, alpha: 0.65, extra:  4 },
        { blur:  0, alpha: 1.00, extra:  0 },
      ]

      for (const { blur, alpha, extra } of passes) {
        ctx.save()
        if (blur > 0) ctx.filter = `blur(${blur}px)`
        ctx.globalAlpha = alpha
        ctx.lineWidth = ringW + extra
        ctx.lineCap = 'round'

        for (let i = 0; i < N; i++) {
          const frac = i / N
          const angle = frac * Math.PI * 2 + rot
          const next  = ((i + 1.5) / N) * Math.PI * 2 + rot
          const hue   = (frac * 360 + 200) % 360
          const light = 52 + 10 * Math.sin(frac * Math.PI * 4 + t * 0.8)
          ctx.beginPath()
          ctx.arc(cx, cy, outerR - ringW / 2, angle, next)
          ctx.strokeStyle = `hsl(${hue.toFixed(1)},100%,${light.toFixed(1)}%)`
          ctx.stroke()
        }
        ctx.restore()
      }

      // Black centre (masks inner area so ring floats cleanly)
      ctx.save()
      ctx.beginPath()
      ctx.arc(cx, cy, outerR - ringW + 1, 0, Math.PI * 2)
      ctx.fillStyle = '#000000'
      ctx.fill()
      ctx.restore()
    }

    function draw() {
      ctx.clearRect(0, 0, w, h)
      drawStars()
      drawOrb()
      t += 0.0045
    }

    function loop() {
      draw()
      frame = requestAnimationFrame(loop)
    }

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
        className="absolute bottom-[6%] left-1/2 -translate-x-1/2 z-10 flex flex-col items-center gap-2 text-white/38 text-[12px] tracking-widest uppercase no-underline"
      >
        <span>Sign in</span>
        <span className="h-1.5 w-1.5 rounded-full bg-white/38 animate-bounce" />
      </a>
    </section>
  )
}
