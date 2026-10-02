import React, { useState } from 'react'
import { Menu, X } from 'lucide-react'
import Sidebar from './Sidebar'
import ThemeToggle from './ThemeToggle'

const COLLAPSE_KEY = 'sidebar-collapsed'

export default function Layout({ title, subtitle, actions, children, className = '' }) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem(COLLAPSE_KEY) === '1' } catch { return false }
  })

  function toggleCollapsed() {
    setCollapsed((current) => {
      const next = !current
      try { localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0') } catch { /* storage unavailable */ }
      return next
    })
  }

  return (
    <div className={`dashboard-shell ${className} min-h-screen bg-paper overflow-x-hidden`}>
      <Sidebar collapsed={collapsed} onToggleCollapse={toggleCollapsed} />

      {/* mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden flex">
          <div className="flex flex-col h-full relative">
            <Sidebar variant="mobile" onNavigate={() => setMobileOpen(false)} />
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 right-[-52px] z-10 h-11 w-11 rounded-full bg-surface border border-line text-ink shadow-pop flex items-center justify-center"
              aria-label="Close menu"
            >
              <X size={20} />
            </button>
          </div>
          <div className="flex-1 bg-black/40 backdrop-blur-[1px]" onClick={() => setMobileOpen(false)} />
        </div>
      )}

      <div className={`transition-[padding] duration-200 ${collapsed ? 'md:pl-20' : 'md:pl-60'}`}>
        <header className="sticky top-0 z-30 bg-paper/90 backdrop-blur border-b border-line">
          {/* Phones: page actions drop to their own row so they never squeeze out the title or clip off-screen. */}
          <div className="flex flex-wrap sm:flex-nowrap items-center gap-x-2 sm:gap-x-3 px-3 sm:px-4 md:px-8 min-h-16 sm:h-16">
            <button
              className="md:hidden h-11 w-11 shrink-0 flex items-center justify-center rounded-full border border-line bg-surface"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu size={18} />
            </button>
            <div className="min-w-0 flex-1 py-2 sm:py-0">
              <h1 className="font-display text-lg sm:text-xl leading-tight text-ink truncate">{title}</h1>
              {subtitle && <p className="text-xs sm:text-sm text-muted truncate">{subtitle}</p>}
            </div>
            <ThemeToggle className="shrink-0" />
            {actions && (
              <div className="w-full sm:w-auto flex flex-wrap items-center gap-2 pb-3 sm:pb-0 sm:shrink-0">
                {actions}
              </div>
            )}
          </div>
        </header>

        <main className="px-3 sm:px-4 md:px-8 py-5 md:py-8 max-w-6xl">{children}</main>
      </div>
    </div>
  )
}
