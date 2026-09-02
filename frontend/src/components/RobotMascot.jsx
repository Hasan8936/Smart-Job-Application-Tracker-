import React from 'react'

export default function RobotMascot({ className = '' }) {
  return (
    <svg viewBox="0 0 300 340" className={className} role="img" aria-hidden="true">
      <defs>
        <linearGradient id="mascotBody" x1="60" y1="120" x2="240" y2="320" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#EDEBFC" />
          <stop offset="1" stopColor="#C9BFF7" />
        </linearGradient>
        <linearGradient id="mascotHead" x1="90" y1="30" x2="210" y2="170" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#F5F2FE" />
          <stop offset="1" stopColor="#CDC1F8" />
        </linearGradient>
        <linearGradient id="mascotVisor" x1="100" y1="70" x2="200" y2="150" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#2A2352" />
          <stop offset="1" stopColor="#120F28" />
        </linearGradient>
        <linearGradient id="mascotEye" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#B8E7FF" />
          <stop offset="1" stopColor="#6D5DF6" />
        </linearGradient>
        <radialGradient id="mascotPedestal" cx="0.5" cy="0.3" r="0.7">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#E4DEFB" />
        </radialGradient>
        <linearGradient id="mascotChest" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#6D5DF6" />
          <stop offset="1" stopColor="#EC5FA0" />
        </linearGradient>
      </defs>

      {/* pedestal */}
      <ellipse cx="150" cy="318" rx="88" ry="16" fill="url(#mascotPedestal)" opacity="0.9" />
      <ellipse cx="150" cy="312" rx="88" ry="16" fill="#FFFFFF" />

      {/* antenna */}
      <line x1="150" y1="10" x2="150" y2="36" stroke="#C9BFF7" strokeWidth="5" strokeLinecap="round" />
      <circle cx="150" cy="10" r="9" fill="url(#mascotChest)" />

      {/* head */}
      <rect x="82" y="34" width="136" height="120" rx="52" fill="url(#mascotHead)" />
      <rect x="104" y="66" width="92" height="60" rx="30" fill="url(#mascotVisor)" />
      <rect x="126" y="84" width="12" height="26" rx="6" fill="url(#mascotEye)" />
      <rect x="162" y="84" width="12" height="26" rx="6" fill="url(#mascotEye)" />

      {/* body */}
      <path d="M60 190C60 156 101 132 150 132C199 132 240 156 240 190V264C240 292 199 308 150 308C101 308 60 292 60 264Z" fill="url(#mascotBody)" />
      <circle cx="150" cy="222" r="30" fill="#FBFAFF" />
      <path d="M150 202 L166 214 L160 234 L140 234 L134 214 Z" fill="url(#mascotChest)" opacity="0.9" />

      {/* arms */}
      <rect x="38" y="176" width="26" height="70" rx="13" fill="url(#mascotBody)" />
      <rect x="236" y="176" width="26" height="70" rx="13" fill="url(#mascotBody)" />
    </svg>
  )
}
