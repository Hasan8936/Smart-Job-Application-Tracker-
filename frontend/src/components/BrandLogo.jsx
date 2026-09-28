import React, { useId } from 'react'

/* Gradient ids must be unique per instance: with two logos on a page (hidden desktop sidebar + mobile sidebar),
   a shared id resolves to the first one, and a gradient inside a display:none SVG paints nothing. */
const gradientIdFor = (reactId, kind) => `brand-${kind}-${reactId.replace(/:/g, '')}`

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
  const gradientId = gradientIdFor(useId(), 'mark')
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 80 80"
      className={className}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <defs>
        <linearGradient id={gradientId} x1="8" y1="72" x2="72" y2="8" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#7c3aed" />
          <stop offset="0.58" stopColor="#d946ef" />
          <stop offset="1" stopColor="#67e8f9" />
        </linearGradient>
      </defs>
      <rect width="80" height="80" rx="18" fill={c.iconBg} stroke={c.iconBdr} strokeWidth="1.5" />
      <path d="M8 58C22 69 41 75 68 68" fill="none" stroke={`url(#${gradientId})`} strokeWidth="4" strokeLinecap="round" opacity=".75" />
      <g transform="translate(40,42)">
        <PlaneIcon fill={`url(#${gradientId})`} bg={c.iconBg} />
      </g>
      <circle cx="61" cy="18" r="4" fill="#f0abfc" />
      <circle cx="69" cy="18" r="2.5" fill="#67e8f9" />
    </svg>
  )
}

/* Full logo: plane emblem + "Smart Job" + "── TRACKER ──" */
function FullLogo({ variant = 'light', className = '', style, tight = false }) {
  const c = COLORS[variant] || COLORS.light
  const gradientId = gradientIdFor(useId(), 'full')

  /*
   * Layout (viewBox 490 × 112):
   *   Icon box  : 0,14  → 82×82, plane centered at 41,55
   *   Wordmark  : x=104, baseline y=68, font-size=48, weight=800, -0.03em tracking
   *   Grad cap  : above "b" in "Job" — estimated center x≈318, y=20
   *   TRACKER   : y=96, centered below "Smart Job" (~215px center)
   *   Lines     : 104→170 and 260→328 at y=90
   * tight crops the empty right edge (content ends at x≈336) for narrow spots like the app sidebar.
   */

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={tight ? '0 0 340 112' : '0 0 490 112'}
      className={className}
      style={style}
      role="img"
      aria-label="Smart Job Tracker"
    >
      <defs>
        <linearGradient id={gradientId} x1="8" y1="96" x2="74" y2="14" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#7c3aed" />
          <stop offset="0.58" stopColor="#d946ef" />
          <stop offset="1" stopColor="#67e8f9" />
        </linearGradient>
      </defs>
      {/* Icon emblem */}
      <rect x="0" y="14" width="82" height="82" rx="18" fill={c.iconBg} stroke={c.iconBdr} strokeWidth="1.5" />
      <path d="M8 72C23 84 45 90 73 79" fill="none" stroke={`url(#${gradientId})`} strokeWidth="4" strokeLinecap="round" opacity=".75" />
      <g transform="translate(41,55)">
        <PlaneIcon fill={`url(#${gradientId})`} bg={c.iconBg} />
      </g>
      <circle cx="62" cy="27" r="4" fill="#f0abfc" />
      <circle cx="70" cy="27" r="2.5" fill="#67e8f9" />

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

export default function BrandLogo({ className = '', variant = 'light', markOnly = false, style, tight = false }) {
  if (markOnly) return <MarkOnly variant={variant} className={className} />
  return <FullLogo variant={variant} className={className} style={style} tight={tight} />
}
