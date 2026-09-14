import React from 'react'

/*
 * variant options:
 *  "light"   — dark ink wordmark, for light backgrounds (default)
 *  "void"    — white→violet gradient wordmark, for dark/void backgrounds (landing, login)
 *  "sidebar" — deep-purple→violet gradient wordmark, for the app sidebar (light bg)
 */

function FullLogo({ variant = 'light', className = '', style }) {
  const gradId   = `blGrad-${variant}`
  const glowId   = `blGlow-${variant}`
  const arcGradId = `blArc-${variant}`
  const lineGradId = `blLine-${variant}`

  /* Wordmark gradient stops per variant */
  const gradStops = {
    light:   [{ offset: '0%', color: '#161616' }, { offset: '100%', color: '#161616' }],
    void:    [{ offset: '0%', color: '#ffffff' }, { offset: '45%', color: '#ddd6fe' }, { offset: '100%', color: '#a78bfa' }],
    sidebar: [{ offset: '0%', color: '#1e1065' }, { offset: '60%', color: '#5b21b6' }, { offset: '100%', color: '#7c3aed' }],
  }
  const stops = gradStops[variant] || gradStops.light

  /* Subtitle colour */
  const subColor = { light: '#525252', void: 'rgba(196,181,253,0.55)', sidebar: 'rgba(93,63,211,0.7)' }[variant] || '#525252'

  /* Icon box fill/stroke — dark vs light surface */
  const isDark   = variant === 'void'
  const boxFill  = isDark ? 'rgba(255,255,255,0.06)' : '#f4f7fb'
  const boxStroke= isDark ? 'rgba(255,255,255,0.12)' : '#e0e6ed'
  const ringStroke = isDark ? 'rgba(255,255,255,0.18)' : '#d0d7de'
  const tickStroke = isDark ? 'rgba(255,255,255,0.4)'  : '#8c959f'

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
        <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="0%">
          {stops.map((s, i) => <stop key={i} offset={s.offset} stopColor={s.color} />)}
        </linearGradient>
        <linearGradient id={lineGradId} x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#0f62fe"/>
          <stop offset="100%" stopColor="#4589ff"/>
        </linearGradient>
        <linearGradient id={arcGradId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#001d6c"/>
          <stop offset="100%" stopColor="#0f62fe"/>
        </linearGradient>
        <filter id={glowId} x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor="#0f62fe" floodOpacity="0.18"/>
        </filter>
      </defs>

      <rect width="780" height="180" rx="20" fill="transparent"/>

      {/* Icon emblem */}
      <g transform="translate(18, 16)" filter={`url(#${glowId})`}>
        <rect width="148" height="148" rx="34" fill={boxFill} stroke={boxStroke} strokeWidth="1.5"/>
        <circle cx="74" cy="74" r="54" fill="none" stroke={ringStroke} strokeWidth="2" strokeDasharray="4 6"/>
        <line x1="74" y1="12"  x2="74"  y2="22"  stroke={tickStroke} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="74" y1="126" x2="74"  y2="136" stroke={tickStroke} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="12" y1="74"  x2="22"  y2="74"  stroke={tickStroke} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="126" y1="74" x2="136" y2="74"  stroke={tickStroke} strokeWidth="2.5" strokeLinecap="round"/>
        <path d="M 38 96 A 42 42 0 1 1 110 96" fill="none" stroke={`url(#${arcGradId})`} strokeWidth="5" strokeLinecap="round" strokeDasharray="135 10"/>
        <path d="M 32 80 L 50 80 L 61 58 L 74 102 L 86 66 L 96 80 L 112 80" fill="none" stroke={`url(#${lineGradId})`} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M 86 66 L 115 37" fill="none" stroke="#0f62fe" strokeWidth="6.5" strokeLinecap="round"/>
        <polygon points="118,34 99,39 107,47 114,55" fill="#0f62fe"/>
        <circle cx="61" cy="58" r="3.5" fill="#ffffff" stroke="#0f62fe" strokeWidth="2.5"/>
        <circle cx="74" cy="102" r="3.5" fill="#ffffff" stroke="#0043ce" strokeWidth="2.5"/>
        <circle cx="86" cy="66" r="4"   fill="#0f62fe" stroke="#ffffff" strokeWidth="1.8"/>
      </g>

      {/* Wordmark */}
      <text
        x="194" y="86"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="44"
        fontWeight="800"
        letterSpacing="-0.03em"
        fill={`url(#${gradId})`}
      >
        Smart Job <tspan fill="#0f62fe">Tracker</tspan>
      </text>
      <text
        x="196" y="122"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="14"
        fontWeight="600"
        letterSpacing="0.16em"
        fill={subColor}
      >
        AI CAREER TELEMETRY &amp; PIPELINE SUITE
      </text>
    </svg>
  )
}

/* Mark-only icon emblem — adapts box bg for dark vs light surfaces */
function MarkOnly({ variant = 'light', className = '' }) {
  const glowId = `blGlowM-${variant}`
  const lineId = `blLineM-${variant}`
  const arcId  = `blArcM-${variant}`
  const isDark = variant === 'void'

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 148 148"
      className={className}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <defs>
        <linearGradient id={lineId} x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#0f62fe"/>
          <stop offset="100%" stopColor="#4589ff"/>
        </linearGradient>
        <linearGradient id={arcId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#001d6c"/>
          <stop offset="100%" stopColor="#0f62fe"/>
        </linearGradient>
        <filter id={glowId} x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor="#0f62fe" floodOpacity="0.18"/>
        </filter>
      </defs>
      <g filter={`url(#${glowId})`}>
        <rect width="148" height="148" rx="34"
          fill={isDark ? 'rgba(255,255,255,0.08)' : '#f4f7fb'}
          stroke={isDark ? 'rgba(139,92,246,0.4)' : '#e0e6ed'}
          strokeWidth="1.5"/>
        <circle cx="74" cy="74" r="54" fill="none"
          stroke={isDark ? 'rgba(255,255,255,0.18)' : '#d0d7de'}
          strokeWidth="2" strokeDasharray="4 6"/>
        <line x1="74" y1="12"  x2="74"  y2="22"  stroke={isDark ? 'rgba(255,255,255,0.45)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="74" y1="126" x2="74"  y2="136" stroke={isDark ? 'rgba(255,255,255,0.45)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="12" y1="74"  x2="22"  y2="74"  stroke={isDark ? 'rgba(255,255,255,0.45)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <line x1="126" y1="74" x2="136" y2="74"  stroke={isDark ? 'rgba(255,255,255,0.45)' : '#8c959f'} strokeWidth="2.5" strokeLinecap="round"/>
        <path d="M 38 96 A 42 42 0 1 1 110 96" fill="none" stroke={`url(#${arcId})`} strokeWidth="5" strokeLinecap="round" strokeDasharray="135 10"/>
        <path d="M 32 80 L 50 80 L 61 58 L 74 102 L 86 66 L 96 80 L 112 80" fill="none" stroke={`url(#${lineId})`} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M 86 66 L 115 37" fill="none" stroke="#0f62fe" strokeWidth="6.5" strokeLinecap="round"/>
        <polygon points="118,34 99,39 107,47 114,55" fill="#0f62fe"/>
        <circle cx="61" cy="58" r="3.5" fill="#ffffff" stroke="#0f62fe" strokeWidth="2.5"/>
        <circle cx="74" cy="102" r="3.5" fill="#ffffff" stroke="#0043ce" strokeWidth="2.5"/>
        <circle cx="86" cy="66" r="4"   fill="#0f62fe" stroke="#ffffff" strokeWidth="1.8"/>
      </g>
    </svg>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false, style }) {
  if (markOnly) return <MarkOnly variant={variant} className={className} />
  return <FullLogo variant={variant} className={className} style={style} />
}
