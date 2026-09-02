import React, { useEffect, useRef } from 'react'

const WAVE_COLORS = ['#38bdf8', '#818cf8', '#c084fc', '#e879f9', '#f472b6']

function noise(x, seed, time) {
  return Math.sin(x * 1.6 + seed * 12 + time) * 0.5
    + Math.sin(x * 3.1 - seed * 6 + time * 0.8) * 0.28
    + Math.sin(x * 0.7 + seed * 3 - time * 1.3) * 0.35
}

export default function WavyHero({ scrollTargetId }) {
  const sectionRef = useRef(null)
  const canvasRef = useRef(null)

  useEffect(() => {
    const section = sectionRef.current
    const canvas = canvasRef.current
    if (!section || !canvas) return
    const ctx = canvas.getContext('2d')
    let w = 0
    let h = 0
    let t = 0
    let frame = null

    function resizeCanvas() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      w = section.clientWidth
      h = section.clientHeight
      canvas.width = w * dpr
      canvas.height = h * dpr
      canvas.style.width = w + 'px'
      canvas.style.height = h + 'px'
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    }

    function drawWaves() {
      ctx.clearRect(0, 0, w, h)
      ctx.filter = 'blur(14px)'
      ctx.globalAlpha = 0.65
      ctx.lineWidth = 2.6

      for (let i = 0; i < WAVE_COLORS.length; i++) {
        ctx.beginPath()
        ctx.strokeStyle = WAVE_COLORS[i]
        for (let x = 0; x <= w; x += 8) {
          const nx = (x / w) * 4
          const y = h * 0.5 + noise(nx, i, t) * (h * 0.14)
          if (x === 0) ctx.moveTo(x, y)
          else ctx.lineTo(x, y)
        }
        ctx.stroke()
      }
      ctx.filter = 'none'
      t += 0.006
    }

    function loop() {
      drawWaves()
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
    <section ref={sectionRef} className="relative min-h-screen overflow-hidden bg-[#08070c] flex items-center justify-center">
      <canvas ref={canvasRef} className="absolute inset-0 w-full h-full" />
      <div className="relative z-10 max-w-3xl mx-auto px-6 text-center">
        <h1 className="font-display text-white font-bold leading-tight tracking-tight text-[clamp(32px,6vw,68px)]">
          Run your whole job search from one place.
        </h1>
        <p className="mt-4 text-white/70 text-[clamp(14px,1.6vw,18px)] leading-relaxed">
          Track applications, match your resume against job descriptions, and never miss a follow-up — all in Smart Job Tracker.
        </p>
      </div>
      <a
        href={`#${scrollTargetId}`}
        className="absolute bottom-[6%] left-1/2 -translate-x-1/2 z-10 flex flex-col items-center gap-2 text-white/55 text-[13px] tracking-wide no-underline"
      >
        <span>Scroll to sign in</span>
        <span className="h-1.5 w-1.5 rounded-full bg-white/55 animate-bounce" />
      </a>
    </section>
  )
}
