import React from 'react'

export default function BrandLogo({ className = '', variant = 'light', markOnly = false }) {
  const wordmarkFill = variant === 'dark' ? '#ffffff' : '#181925'

  if (markOnly) {
    return (
      <svg viewBox="0 0 160 160" className={className} role="img" aria-label="Smart Job Tracker">
        <circle cx="80" cy="80" r="70" fill="#918df6" />
        <path d="M50 83L69 102L112 58" stroke="#ffffff" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </svg>
    )
  }

  return (
    <svg viewBox="0 0 640 160" className={className} role="img" aria-label="Smart Job Tracker">
      <circle cx="80" cy="80" r="70" fill="#918df6" />
      <path d="M50 83L69 102L112 58" stroke="#ffffff" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round" fill="none" />
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
