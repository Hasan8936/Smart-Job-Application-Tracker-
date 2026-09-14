import React, { useState } from 'react'
import { Menu, X } from 'lucide-react'
import Sidebar from './Sidebar'

const COLLAPSE_KEY = 'sidebar-collapsed'

export default function Layout({ title, subtitle, actions, children }) {
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
    <div className="min-h-screen bg-paper overflow-x-hidden">
      <Sidebar collapsed={collapsed} onToggleCollapse={toggleCollapsed} />

      {/* mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden flex">
          <div className="flex flex-col h-full relative">
            <Sidebar variant="mobile" onNavigate={() => setMobileOpen(false)} />
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 right-[-52px] h-11 w-11 rounded-full bg-surface border border-line text-ink shadow-pop flex items-center justify-center"
              aria-label="Close menu"
            >
              <X size={20} />
            </button>
          </div>
          <div className="flex-1" onClick={() => setMobileOpen(false)} />
        </div>
      )}

      <div className={`transition-[padding] duration-200 ${collapsed ? 'md:pl-20' : 'md:pl-60'}`}>
        <header className="sticky top-0 z-30 bg-paper/90 backdrop-blur border-b border-line">
          <div className="flex items-center gap-2 sm:gap-3 px-3 sm:px-4 md:px-8 h-16">
            <button
              className="md:hidden h-11 w-11 shrink-0 flex items-center justify-center rounded-full border border-line bg-surface"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu size={18} />
            </button>
            <div className="min-w-0">
              <h1 className="font-display text-lg sm:text-xl leading-tight text-ink truncate">{title}</h1>
              {subtitle && <p className="text-xs sm:text-sm text-muted truncate">{subtitle}</p>}
            </div>
            <div className="ml-auto flex items-center gap-2 shrink-0">{actions}</div>
          </div>
        </header>

        <main className="px-3 sm:px-4 md:px-8 py-5 md:py-8 max-w-6xl">{children}</main>
      </div>
    </div>
  )
}
