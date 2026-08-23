import React, { useContext } from 'react'
import { NavLink } from 'react-router-dom'
import { LayoutGrid, ListChecks, FileSearch, BellRing, LogOut } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'

const links = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/applications', label: 'Applications', icon: ListChecks },
  { to: '/resume-match', label: 'Resume matcher', icon: FileSearch },
  { to: '/reminders', label: 'Reminders', icon: BellRing },
]

export default function Sidebar() {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || user?.profile?.email || 'Account'

  return (
    <aside className="hidden md:flex md:w-60 md:flex-col md:fixed md:inset-y-0 bg-ink text-white">
      <div className="flex items-center gap-2 px-6 h-16 border-b border-white/10">
        <span className="h-7 w-7 rounded-md bg-accent flex items-center justify-center text-ink-soft font-display font-semibold text-sm">
          S
        </span>
        <span className="font-display text-[17px] tracking-tight">Smart Job Tracker</span>
      </div>

      <nav className="flex-1 px-3 py-5 space-y-1">
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
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

      <div className="px-3 py-4 border-t border-white/10">
        <NavLink
          to="/profile"
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2 rounded-lg text-sm mb-1 ${
              isActive ? 'bg-white/10 text-white' : 'text-white/60 hover:text-white hover:bg-white/5'
            }`
          }
        >
          <span className="h-6 w-6 rounded-full bg-accent/90 flex items-center justify-center text-[11px] font-semibold text-ink-soft">
            {name.slice(0, 1).toUpperCase()}
          </span>
          <span className="truncate">{name}</span>
        </NavLink>
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-white/60 hover:text-white hover:bg-white/5"
        >
          <LogOut size={16} />
          Log out
        </button>
      </div>
    </aside>
  )
}
