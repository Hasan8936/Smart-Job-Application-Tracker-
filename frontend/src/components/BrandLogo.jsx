import React from 'react'

export default function BrandLogo({ className = '' }) {
  return (
    <img
      src="/logo.svg"
      alt="Smart Job Tracker"
      className={className}
    />
  )
}
