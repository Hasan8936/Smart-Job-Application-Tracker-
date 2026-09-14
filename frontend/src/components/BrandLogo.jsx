import React from 'react'

/* Full horizontal lockup — used in sidebar, auth pages, etc. */
function FullLogo({ variant = 'light', className = '', style }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 780 180"
      className={className}
      style={style}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <defs>
        <linearGradient id="blp" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#0f62fe"/>
          <stop offset="100%" stopColor="#4589ff"/>
        </linearGradient>
        <linearGradient id="blpulse" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#0043ce"/>
          <stop offset="100%" stopColor="#0f62fe"/>
        </linearGradient>
        <linearGradient id="blacc" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#001d6c"/>
          <stop offset="100%" stopColor="#0f62fe"/>
        </linearGradient>
        <filter id="blglow" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor="#0f62fe" floodOpacity="0.18"/>
        </filter>
      </defs>

      {/* Background — transparent so it adapts to any surface */}
      <rect width="780" height="180" rx="20" fill="transparent"/>

      {/* Icon emblem */}
      <g transform="translate(18, 16)" filter="url(#blglow)">
        <rect width="148" height="148" rx="34" fill={variant === 'dark' ? 'rgba(255,255,255,0.06)' : '#f4f7fb'} stroke={variant === 'dark' ? 'rgba(255,255,255,0.12)' : '#e0e6ed'} strokeWidth="1.5"/>
        <circle cx="74" cy="74" r="54" fill="none" stroke={variant === 'dark' ? 'rgba(255,255,255,0.15)' : '#d0d7de'} strokeWidth="2" strokeDasharray="4 6"/>
        <line x1="74" y1="12" x2="74" y2="22" stroke={variant === 'dark' ? 'rgba(255,255,255,0.35)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="74" y1="126" x2="74" y2="136" stroke={variant === 'dark' ? 'rgba(255,255,255,0.35)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="12" y1="74" x2="22" y2="74" stroke={variant === 'dark' ? 'rgba(255,255,255,0.35)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="126" y1="74" x2="136" y2="74" stroke={variant === 'dark' ? 'rgba(255,255,255,0.35)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <path d="M 38 96 A 42 42 0 1 1 110 96" fill="none" stroke="url(#blacc)" strokeWidth="5" strokeLinecap="round" strokeDasharray="135 10"/>
        <path d="M 32 80 L 50 80 L 61 58 L 74 102 L 86 66 L 96 80 L 112 80" fill="none" stroke="url(#blp)" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M 86 66 L 115 37" fill="none" stroke="#0f62fe" strokeWidth="6.5" strokeLinecap="round"/>
        <polygon points="118,34 99,39 107,47 114,55" fill="#0f62fe"/>
        <circle cx="61" cy="58" r="3.5" fill="#ffffff" stroke="#0f62fe" strokeWidth="2.5"/>
        <circle cx="74" cy="102" r="3.5" fill="#ffffff" stroke="#0043ce" strokeWidth="2.5"/>
        <circle cx="86" cy="66" r="4" fill="#0f62fe" stroke="#ffffff" strokeWidth="1.8"/>
      </g>

      {/* Wordmark */}
      <text
        x="194" y="86"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="44"
        fontWeight="800"
        letterSpacing="-0.03em"
        fill={variant === 'dark' ? '#ffffff' : '#161616'}
      >
        Smart Job <tspan fill="#0f62fe">Tracker</tspan>
      </text>
      <text
        x="196" y="122"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="14"
        fontWeight="600"
        letterSpacing="0.16em"
        fill={variant === 'dark' ? 'rgba(255,255,255,0.45)' : '#525252'}
      >
        AI CAREER TELEMETRY &amp; PIPELINE SUITE
      </text>
    </svg>
  )
}

/* Mark-only icon — used where only the icon emblem is needed */
function MarkOnly({ className = '' }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 148 148"
      className={className}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <defs>
        <linearGradient id="blpm" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#0f62fe"/>
          <stop offset="100%" stopColor="#4589ff"/>
        </linearGradient>
        <linearGradient id="blaccm" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#001d6c"/>
          <stop offset="100%" stopColor="#0f62fe"/>
        </linearGradient>
        <filter id="blglowm" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor="#0f62fe" floodOpacity="0.18"/>
        </filter>
      </defs>
      <g filter="url(#blglowm)">
        <rect width="148" height="148" rx="34" fill="#f4f7fb" stroke="#e0e6ed" strokeWidth="1.5"/>
        <circle cx="74" cy="74" r="54" fill="none" stroke="#d0d7de" strokeWidth="2" strokeDasharray="4 6"/>
        <line x1="74" y1="12" x2="74" y2="22" stroke="#8c959f" strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="74" y1="126" x2="74" y2="136" stroke="#8c959f" strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="12" y1="74" x2="22" y2="74" stroke="#8c959f" strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="126" y1="74" x2="136" y2="74" stroke="#8c959f" strokeWidth="2.5" strokeLinecap="round"/>
        <path d="M 38 96 A 42 42 0 1 1 110 96" fill="none" stroke="url(#blaccm)" strokeWidth="5" strokeLinecap="round" strokeDasharray="135 10"/>
        <path d="M 32 80 L 50 80 L 61 58 L 74 102 L 86 66 L 96 80 L 112 80" fill="none" stroke="url(#blpm)" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M 86 66 L 115 37" fill="none" stroke="#0f62fe" strokeWidth="6.5" strokeLinecap="round"/>
        <polygon points="118,34 99,39 107,47 114,55" fill="#0f62fe"/>
        <circle cx="61" cy="58" r="3.5" fill="#ffffff" stroke="#0f62fe" strokeWidth="2.5"/>
        <circle cx="74" cy="102" r="3.5" fill="#ffffff" stroke="#0043ce" strokeWidth="2.5"/>
        <circle cx="86" cy="66" r="4" fill="#0f62fe" stroke="#ffffff" strokeWidth="1.8"/>
      </g>
    </svg>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false, style }) {
  if (markOnly) return <MarkOnly className={className} />
  return <FullLogo variant={variant} className={className} style={style} />
}
