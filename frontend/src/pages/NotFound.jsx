import React from 'react'
import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 text-center bg-canvas">
      <p className="text-8xl font-bold text-accent/20 select-none mb-2">404</p>
      <h1 className="text-2xl font-bold text-ink mb-2">Page not found</h1>
      <p className="text-ink-soft mb-8 max-w-sm">
        The page you're looking for doesn't exist or has been moved.
      </p>
      <div className="flex gap-3">
        <Link
          to="/"
          className="px-5 py-2.5 rounded-lg bg-accent text-white font-medium text-sm hover:bg-accent/90 transition-colors"
        >
          Go home
        </Link>
        <Link
          to="/dashboard"
          className="px-5 py-2.5 rounded-lg border border-line text-ink font-medium text-sm hover:bg-surface transition-colors"
        >
          Dashboard
        </Link>
      </div>
    </div>
  )
}
