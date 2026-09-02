import React from 'react'

export default function ScoreRing({ value = 0, size = 140 }) {
  const stroke = 10
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const clamped = Math.max(0, Math.min(100, value))
  const offset = circumference - (clamped / 100) * circumference

  const color = clamped >= 75 ? '#33c758' : clamped >= 50 ? '#ffa600' : '#ff3e00'
  const label = clamped >= 75 ? 'Strong match' : clamped >= 50 ? 'Fair match' : 'Needs work'

  return (
    <div className="flex flex-col items-center">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90">
          <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e8e8e8" strokeWidth={stroke} />
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth={stroke}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            style={{ transition: 'stroke-dashoffset 0.5s ease' }}
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="font-mono text-3xl font-medium text-ink">{Math.round(clamped)}%</span>
        </div>
      </div>
      <div className="mt-2 text-sm font-medium" style={{ color }}>{label}</div>
    </div>
  )
}
