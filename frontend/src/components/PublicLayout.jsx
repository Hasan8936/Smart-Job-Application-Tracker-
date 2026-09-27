import React, { useContext } from 'react'
import { Link } from 'react-router-dom'
import BrandLogo from './BrandLogo'
import { AuthContext } from '../context/AuthContext'

// Header/footer for public pages (legal, etc.) — no app sidebar, so logged-out
// visitors don't see Dashboard/Log out links.
export default function PublicLayout({ children }) {
  const { user } = useContext(AuthContext)

  return (
    <div className="min-h-screen bg-paper flex flex-col">
      <header className="sticky top-0 z-30 bg-paper/90 backdrop-blur border-b border-line">
        <div className="max-w-5xl mx-auto flex items-center gap-4 px-4 h-16">
          <Link to="/" aria-label="Smart Job Tracker home">
            <BrandLogo className="h-8 sm:h-9 w-auto" />
          </Link>
          <nav className="ml-auto flex items-center gap-1 sm:gap-3 text-sm">
            {user ? (
              <Link to="/dashboard" className="px-4 py-2 whitespace-nowrap rounded-full bg-accent text-white hover:opacity-90">Dashboard</Link>
            ) : (
              <>
                <Link to="/login" className="px-2 sm:px-3 py-2 whitespace-nowrap text-ink-soft hover:text-ink">Sign in</Link>
                <Link to="/register" className="px-3 sm:px-4 py-2 whitespace-nowrap rounded-full bg-accent text-white hover:opacity-90">Get started free</Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1">{children}</main>

      <footer className="border-t border-line">
        <div className="max-w-5xl mx-auto px-4 py-6 text-sm text-muted flex flex-wrap gap-4">
          <Link to="/" className="hover:text-ink">Home</Link>
          <Link to="/privacy" className="hover:text-ink">Privacy Policy</Link>
          <Link to="/terms" className="hover:text-ink">Terms &amp; Conditions</Link>
        </div>
      </footer>
    </div>
  )
}
