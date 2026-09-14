import React from 'react'

/*
 * variant:
 *  "light"   — dark navy text, for light backgrounds (default / dashboard)
 *  "void"    — white text + light-violet TRACKER, for dark/void surfaces (landing, auth)
 *  "sidebar" — deep-navy text + violet TRACKER, for the app sidebar (white bg)
 */

const COLORS = {
  light: {
    main:      '#181925',
    accent:    '#7c3aed',
    accentFg:  'rgba(124,58,237,0.28)',
    iconFg:    '#7c3aed',
    iconBg:    '#f0ebff',
    iconBdr:   '#ddd6fe',
  },
  void: {
    main:      '#ffffff',
    accent:    '#c4b5fd',
    accentFg:  'rgba(196,181,253,0.25)',
    iconFg:    '#ffffff',
    iconBg:    'rgba(255,255,255,0.09)',
    iconBdr:   'rgba(255,255,255,0.16)',
  },
  sidebar: {
    main:      '#1e1065',
    accent:    '#7c3aed',
    accentFg:  'rgba(124,58,237,0.25)',
    iconFg:    '#7c3aed',
    iconBg:    '#f0ebff',
    iconBdr:   '#ddd6fe',
  },
}

/* Paper-plane icon, centered at origin in an 80×80 box */
function PlaneIcon({ fill, bg }) {
  return (
    <g transform="rotate(-14)">
      {/* Main body – points right */}
      <path d="M-26,0 L28,0 L-26,-8 Z" fill={fill} />
      {/* Lower wing (lighter) */}
      <path d="M-26,0 L28,0 L-26,8 Z" fill={fill} opacity="0.38" />
      {/* Inner crease sheen */}
      <path d="M-10,-3.5 L28,0 L-10,3.5 Z" fill={bg} opacity="0.45" />
    </g>
  )
}

/* Mark-only: plane icon in rounded square */
function MarkOnly({ variant = 'light', className = '' }) {
  const c = COLORS[variant] || COLORS.light
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 80 80"
      className={className}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <rect width="80" height="80" rx="18" fill={c.iconBg} stroke={c.iconBdr} strokeWidth="1.5" />
      <g transform="translate(40,42)">
        <PlaneIcon fill={c.iconFg} bg={c.iconBg} />
      </g>
    </svg>
  )
}

/* Full logo: plane emblem + "Smart Job" + "── TRACKER ──" */
function FullLogo({ variant = 'light', className = '', style }) {
  const c = COLORS[variant] || COLORS.light

  /*
   * Layout (viewBox 490 × 112):
   *   Icon box  : 0,14  → 82×82, plane centered at 41,55
   *   Wordmark  : x=104, baseline y=68, font-size=48, weight=800, -0.03em tracking
   *   Grad cap  : above "b" in "Job" — estimated center x≈318, y=20
   *   TRACKER   : y=96, centered below "Smart Job" (~215px center)
   *   Lines     : 104→170 and 260→328 at y=90
   */

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 490 112"
      className={className}
      style={style}
      role="img"
      aria-label="Smart Job Tracker"
    >
      {/* Icon emblem */}
      <rect x="0" y="14" width="82" height="82" rx="18" fill={c.iconBg} stroke={c.iconBdr} strokeWidth="1.5" />
      <g transform="translate(41,55)">
        <PlaneIcon fill={c.iconFg} bg={c.iconBg} />
      </g>

      {/* Graduation cap above "b" in "Smart Job" */}
      <g transform="translate(318,20)" fill={c.accent}>
        <polygon points="0,-10 14,-4.5 0,1.5 -14,-4.5" />
        <line x1="-16" y1="-4.5" x2="16" y2="-4.5" stroke={c.accent} strokeWidth="2.2" strokeLinecap="round" />
        <line x1="12" y1="-4.5" x2="12" y2="5" stroke={c.accent} strokeWidth="1.5" />
        <circle cx="12" cy="6.5" r="3" />
      </g>

      {/* "Smart Job" wordmark */}
      <text
        x="104"
        y="68"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="48"
        fontWeight="800"
        letterSpacing="-0.03em"
        fill={c.main}
      >
        Smart Job
      </text>

      {/* Decorative lines + TRACKER */}
      <line x1="104" y1="90" x2="170" y2="90" stroke={c.accentFg} strokeWidth="1.5" strokeLinecap="round" />
      <text
        x="176"
        y="96"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="12.5"
        fontWeight="700"
        letterSpacing="0.22em"
        fill={c.accent}
      >
        TRACKER
      </text>
      <line x1="260" y1="90" x2="328" y2="90" stroke={c.accentFg} strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false, style }) {
  if (markOnly) return <MarkOnly variant={variant} className={className} />
  return <FullLogo variant={variant} className={className} style={style} />
}
