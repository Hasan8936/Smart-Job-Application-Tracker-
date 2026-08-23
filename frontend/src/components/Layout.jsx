import React, { useState } from 'react'
import { Menu, X } from 'lucide-react'
import Sidebar from './Sidebar'

export default function Layout({ title, subtitle, actions, children }) {
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="min-h-screen bg-paper">
      <Sidebar />

      {/* mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <div className="absolute inset-0 bg-ink/50" onClick={() => setMobileOpen(false)} />
          <div className="relative w-60 h-full">
            <Sidebar />
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 right-[-44px] h-9 w-9 rounded-full bg-ink text-white flex items-center justify-center"
              aria-label="Close menu"
            >
              <X size={18} />
            </button>
          </div>
        </div>
      )}

      <div className="md:pl-60">
        <header className="sticky top-0 z-30 bg-paper/90 backdrop-blur border-b border-line">
          <div className="flex items-center gap-3 px-4 md:px-8 h-16">
            <button
              className="md:hidden h-9 w-9 flex items-center justify-center rounded-lg border border-line bg-surface"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu size={18} />
            </button>
            <div className="min-w-0">
              <h1 className="font-display text-xl leading-tight text-ink truncate">{title}</h1>
              {subtitle && <p className="text-sm text-muted truncate">{subtitle}</p>}
            </div>
            <div className="ml-auto flex items-center gap-2">{actions}</div>
          </div>
        </header>

        <main className="px-4 md:px-8 py-6 md:py-8 max-w-6xl">{children}</main>
      </div>
    </div>
  )
}
