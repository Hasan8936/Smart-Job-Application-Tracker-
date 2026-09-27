import React, { useRef } from 'react'

const PARTICLES = Array.from({ length: 7 }, (_, index) => index)

export default function TiltCard({ children, className = '', strength = 8 }) {
  const ref = useRef(null)

  function reset() {
    const el = ref.current
    if (!el) return
    el.style.setProperty('--tilt-x', '0deg')
    el.style.setProperty('--tilt-y', '0deg')
    el.style.setProperty('--spot-x', '50%')
    el.style.setProperty('--spot-y', '50%')
  }

  function handleMove(event) {
    const el = ref.current
    if (!el || event.pointerType !== 'mouse') return
    const rect = el.getBoundingClientRect()
    const x = (event.clientX - rect.left) / rect.width
    const y = (event.clientY - rect.top) / rect.height
    el.style.setProperty('--tilt-y', `${(x - 0.5) * strength}deg`)
    el.style.setProperty('--tilt-x', `${(0.5 - y) * strength}deg`)
    el.style.setProperty('--spot-x', `${x * 100}%`)
    el.style.setProperty('--spot-y', `${y * 100}%`)
  }

  return (
    <div
      ref={ref}
      className={`dashboard-tilt-card ${className}`}
      onPointerMove={handleMove}
      onPointerLeave={reset}
      onFocus={() => ref.current?.classList.add('is-focused')}
      onBlur={() => { ref.current?.classList.remove('is-focused'); reset() }}
    >
      <span className="dashboard-tilt-glow" aria-hidden="true" />
      <span className="dashboard-tilt-particles" aria-hidden="true">
        {PARTICLES.map((particle) => <i key={particle} style={{ '--particle-index': particle }} />)}
      </span>
      <div className="dashboard-tilt-content">{children}</div>
    </div>
  )
}
