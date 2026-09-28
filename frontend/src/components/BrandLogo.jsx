import React, { useId } from 'react'

/* Gradient ids must be unique per instance: with two logos on a page (hidden desktop sidebar + mobile sidebar),
   a shared id resolves to the first one, and a gradient inside a display:none SVG paints nothing. */
const idsFor = (reactId) => {
  const base = `brand-${reactId.replace(/:/g, '')}`
  return { bg: `${base}-bg`, ring: `${base}-ring`, orb: `${base}-orb` }
}

/*
 * variant:
 *  "light"   — dark navy text, for light backgrounds (default / dashboard)
 *  "void"    — white text + light-violet TRACKER, for dark/void surfaces (landing, auth, dark theme)
 *  "sidebar" — deep-navy text + violet TRACKER, for the app sidebar (white bg)
 *
 * markBorder: the favicon square is near-black, so it pops on light surfaces as-is; on dark surfaces it needs a
 * light-violet outline or it merges into the navy background.
 */
const COLORS = {
  light: {
    main:       '#181925',
    accent:     '#6d28d9',   // 7.1:1 on white
    accentFg:   'rgba(109,40,217,0.45)',
    markBorder: 'rgba(109,40,217,0.25)',
  },
  void: {
    main:       '#ffffff',
    accent:     '#c4b5fd',   // 9.6:1 on the dark surface
    accentFg:   'rgba(196,181,253,0.45)',
    markBorder: 'rgba(196,181,253,0.55)',
  },
  sidebar: {
    main:       '#1e1065',
    accent:     '#6d28d9',
    accentFg:   'rgba(109,40,217,0.45)',
    markBorder: 'rgba(109,40,217,0.25)',
  },
}

/* The favicon mark (public/favicon.svg): dark rounded square, gradient orbit ring and glowing orb, in a 64×64 box. */
function FaviconMark({ ids, border }) {
  return (
    <>
      <defs>
        <linearGradient id={ids.bg} x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#1b1530" />
          <stop offset="55%" stopColor="#241a3d" />
          <stop offset="100%" stopColor="#12101c" />
        </linearGradient>
        <linearGradient id={ids.ring} x1="8" y1="10" x2="54" y2="54" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#f472c9" />
          <stop offset="50%" stopColor="#a855f7" />
          <stop offset="100%" stopColor="#38bdf8" />
        </linearGradient>
        <radialGradient id={ids.orb} cx="0.32" cy="0.28" r="0.85">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="16%" stopColor="#ffd9f5" />
          <stop offset="42%" stopColor="#f472c9" />
          <stop offset="70%" stopColor="#a855f7" />
          <stop offset="100%" stopColor="#38bdf8" />
        </radialGradient>
      </defs>
      <rect x="0.75" y="0.75" width="62.5" height="62.5" rx="14" fill={`url(#${ids.bg})`} stroke={border} strokeWidth="1.5" />
      <path d="M 46.5 39.5 A 16.5 16.5 0 1 1 46.5 24.5" fill="none" stroke={`url(#${ids.ring})`} strokeWidth="4.3" strokeLinecap="round" />
      <circle cx="46.5" cy="24.5" r="6.8" fill={`url(#${ids.orb})`} />
      <circle cx="53.5" cy="32" r="1.9" fill="#a855f7" opacity="0.55" />
    </>
  )
}

/* Mark-only: the favicon square */
function MarkOnly({ variant = 'light', className = '' }) {
  const c = COLORS[variant] || COLORS.light
  const ids = idsFor(useId())
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" className={className} role="img" aria-label="Smart Job Tracker">
      <FaviconMark ids={ids} border={c.markBorder} />
    </svg>
  )
}

/* Full logo: favicon square + "Smart Job" + "── TRACKER ──" */
function FullLogo({ variant = 'light', className = '', style, tight = false }) {
  const c = COLORS[variant] || COLORS.light
  const ids = idsFor(useId())

  /*
   * Layout (viewBox 490 × 112):
   *   Mark      : 0,14 → 82×82 (the 64×64 favicon scaled by 82/64)
   *   Wordmark  : x=104, baseline y=68, font-size=48, weight=800, -0.03em tracking
   *   Grad cap  : above "b" in "Job" — estimated center x≈318, y=20
   *   TRACKER   : y=97, between the two lines
   *   Lines     : 104→168 and 268→328 at y=91
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
      <g transform="translate(0,14) scale(1.28125)">
        <FaviconMark ids={ids} border={c.markBorder} />
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

      {/* Decorative lines + TRACKER (14px so it survives the sidebar's ~0.36× scale) */}
      <line x1="104" y1="91" x2="168" y2="91" stroke={c.accentFg} strokeWidth="1.8" strokeLinecap="round" />
      <text
        x="175"
        y="97"
        fontFamily="'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        fontSize="14"
        fontWeight="700"
        letterSpacing="0.2em"
        fill={c.accent}
      >
        TRACKER
      </text>
      <line x1="268" y1="91" x2="328" y2="91" stroke={c.accentFg} strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

export default function BrandLogo({ className = '', variant = 'light', markOnly = false, style, tight = false }) {
  if (markOnly) return <MarkOnly variant={variant} className={className} />
  return <FullLogo variant={variant} className={className} style={style} tight={tight} />
}
