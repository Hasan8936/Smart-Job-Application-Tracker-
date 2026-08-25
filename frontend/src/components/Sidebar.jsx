import React, { useContext } from 'react'
import { NavLink } from 'react-router-dom'
import { LayoutGrid, ListChecks, FileSearch, BellRing, LogOut } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import BrandLogo from './BrandLogo'

const links = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/applications', label: 'Applications', icon: ListChecks },
  { to: '/resume-match', label: 'Resume matcher', icon: FileSearch },
  { to: '/reminders', label: 'Reminders', icon: BellRing },
]

export default function Sidebar({ variant = 'desktop', onNavigate }) {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || user?.profile?.email || 'Account'
  const isMobile = variant === 'mobile'

  // Desktop: permanently pinned, hidden below md (the mobile drawer below covers that case).
  // Mobile: a normal flex column that always renders — it's only ever mounted inside the drawer.
  const asideClass = isMobile
    ? 'flex w-[80vw] max-w-72 h-full flex-col bg-ink text-white'
    : 'hidden md:flex md:w-60 md:flex-col md:fixed md:inset-y-0 bg-ink text-white'

  return (
    <aside className={asideClass}>
      <div className="flex items-center gap-2 px-6 h-16 border-b border-white/10 shrink-0">
        <BrandLogo className="w-44 h-auto" />
      </div>

      <nav className="flex-1 px-3 py-5 space-y-1 overflow-y-auto scroll-thin">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-3 md:py-2.5 rounded-lg text-sm transition-colors ${
                isActive
                  ? 'bg-white/10 text-white font-medium'
                  : 'text-white/60 hover:text-white hover:bg-white/5'
              }`
            }
          >
            <Icon size={17} strokeWidth={2} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-3 py-4 border-t border-white/10 shrink-0">
        <NavLink
          to="/profile"
          onClick={onNavigate}
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2.5 md:py-2 rounded-lg text-sm mb-1 ${
              isActive ? 'bg-white/10 text-white' : 'text-white/60 hover:text-white hover:bg-white/5'
            }`
          }
        >
          <span className="h-6 w-6 rounded-full bg-accent/90 flex items-center justify-center text-[11px] font-semibold text-ink-soft shrink-0">
            {name.slice(0, 1).toUpperCase()}
          </span>
          <span className="truncate">{name}</span>
        </NavLink>
        <button
          onClick={() => { logout(); onNavigate?.() }}
          className="w-full flex items-center gap-3 px-3 py-2.5 md:py-2 rounded-lg text-sm text-white/60 hover:text-white hover:bg-white/5"
        >
          <LogOut size={16} />
          Log out
        </button>
      </div>
    </aside>
  )
}
