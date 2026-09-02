import React, { useContext } from 'react'
import { NavLink } from 'react-router-dom'
import { LayoutGrid, ListChecks, FileSearch, BellRing, LogOut, Search, Sparkles, ClipboardCheck } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import BrandLogo from './BrandLogo'

const links = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/applications', label: 'Applications', icon: ListChecks },
  { to: '/resume-match', label: 'Resume matcher', icon: FileSearch },
  { to: '/resume-tailoring', label: 'Resume tailoring', icon: Sparkles },
  { to: '/application-preparation', label: 'Application prep', icon: ClipboardCheck },
  { to: '/discovery', label: 'Discover jobs', icon: Search },
  { to: '/reminders', label: 'Reminders', icon: BellRing },
]

export default function Sidebar({ variant = 'desktop', onNavigate }) {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || user?.profile?.email || 'Account'
  const isMobile = variant === 'mobile'

  return (
    <aside
      className={
        isMobile
          ? 'flex w-72 max-w-[85vw] h-full flex-col bg-surface border-r border-line'
          : 'hidden md:flex md:w-60 md:flex-col md:fixed md:inset-y-0 bg-surface border-r border-line'
      }
    >
      <div className="flex items-center gap-2 px-6 h-16 border-b border-line">
        <BrandLogo className="w-40 h-auto" />
      </div>

      <nav className="flex-1 px-3 py-5 space-y-1">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-full text-sm transition-colors ${
                isActive
                  ? 'bg-accent-soft text-accent font-medium'
                  : 'text-ink-soft hover:text-ink hover:bg-mist'
              }`
            }
          >
            <Icon size={17} strokeWidth={2} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-3 py-4 border-t border-line">
        <NavLink
          to="/profile"
          onClick={onNavigate}
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2 rounded-full text-sm mb-1 ${
              isActive ? 'bg-accent-soft text-accent' : 'text-ink-soft hover:text-ink hover:bg-mist'
            }`
          }
        >
          <span className="h-6 w-6 rounded-full bg-accent flex items-center justify-center text-[11px] font-semibold text-white">
            {name.slice(0, 1).toUpperCase()}
          </span>
          <span className="truncate">{name}</span>
        </NavLink>
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-full text-sm text-ink-soft hover:text-ink hover:bg-mist"
        >
          <LogOut size={16} />
          Log out
        </button>
      </div>
    </aside>
  )
}
