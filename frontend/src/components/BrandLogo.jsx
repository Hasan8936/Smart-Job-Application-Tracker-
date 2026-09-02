import React from 'react'

function HexMark({ id }) {
  return (
    <>
      <defs>
        <linearGradient id={`hexGrad-${id}`} x1="21" y1="14" x2="139" y2="150" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#6D5DF6" />
          <stop offset="0.55" stopColor="#9B5DE5" />
          <stop offset="1" stopColor="#EC5FA0" />
        </linearGradient>
      </defs>
      <polygon points="80,14 139,48 139,116 80,150 21,116 21,48" fill={`url(#hexGrad-${id})`} />
      <polygon points="80,48 109.5,65 109.5,99 80,116 50.5,99 50.5,65" fill="#ffffff" opacity="0.22" />
    </>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false }) {
  const wordmarkFill = variant === 'dark' ? '#ffffff' : '#181925'

  if (markOnly) {
    return (
      <svg viewBox="0 0 160 164" className={className} role="img" aria-label="Smart Job Tracker">
        <HexMark id="mark" />
      </svg>
    )
  }

  return (
    <svg viewBox="0 0 640 164" className={className} role="img" aria-label="Smart Job Tracker">
      <HexMark id="full" />
      <text
        x="176"
        y="98"
        fontFamily="'DM Sans', 'Inter', sans-serif"
        fontWeight="700"
        fontSize="52"
        letterSpacing="-1"
        fill={wordmarkFill}
      >
        Smart Job Tracker
      </text>
    </svg>
  )
}
