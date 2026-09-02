import React from 'react'

function OrbitMark({ id }) {
  return (
    <>
      <defs>
        <linearGradient id={`ringGrad-${id}`} x1="4" y1="6" x2="34" y2="34" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#f472c9" />
          <stop offset="50%" stopColor="#a855f7" />
          <stop offset="100%" stopColor="#38bdf8" />
        </linearGradient>
        <radialGradient id={`orbGrad-${id}`} cx="0.32" cy="0.28" r="0.85">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="16%" stopColor="#ffd9f5" />
          <stop offset="42%" stopColor="#f472c9" />
          <stop offset="70%" stopColor="#a855f7" />
          <stop offset="100%" stopColor="#38bdf8" />
        </radialGradient>
      </defs>
      <path
        d="M 30.8 25.2 A 13 13 0 1 1 30.8 14.8"
        fill="none"
        stroke={`url(#ringGrad-${id})`}
        strokeWidth="3.4"
        strokeLinecap="round"
      />
      <circle cx="30.8" cy="14.8" r="4.6" fill={`url(#orbGrad-${id})`} />
      <circle cx="35.4" cy="20" r="1.5" fill="#a855f7" opacity="0.55" />
    </>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false }) {
  const wordmarkFill = variant === 'dark' ? '#ffffff' : '#181925'

  if (markOnly) {
    return (
      <svg viewBox="0 0 40 40" className={className} role="img" aria-label="Smart Job Tracker">
        <OrbitMark id="mark" />
      </svg>
    )
  }

  return (
    <svg viewBox="0 0 520 100" className={className} role="img" aria-label="Smart Job Tracker">
      <g transform="translate(8,10) scale(2)">
        <OrbitMark id="full" />
      </g>
      <text
        x="110"
        y="66"
        fontFamily="'DM Sans', 'Inter', sans-serif"
        fontWeight="700"
        fontSize="38"
        letterSpacing="-1"
        fill={wordmarkFill}
      >
        Smart Job Tracker
      </text>
    </svg>
  )
}
