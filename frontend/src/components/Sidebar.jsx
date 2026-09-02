import React, { useContext } from 'react'
import { NavLink } from 'react-router-dom'
import { LayoutGrid, ListChecks, FileSearch, BellRing, LogOut, Search, Sparkles, ClipboardCheck, ChevronsLeft, ChevronsRight } from 'lucide-react'
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

function NavPill({ to, end, onClick, collapsed, children: label, Icon }) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onClick}
      className={({ isActive }) =>
        `group relative flex items-center rounded-full text-sm transition-colors ${
          collapsed ? 'justify-center h-11 w-11 mx-auto' : 'gap-3 px-3 py-2.5'
        } ${
          isActive
            ? 'bg-accent-soft text-accent font-medium'
            : 'text-ink-soft hover:text-ink hover:bg-mist'
        }`
      }
    >
      {({ isActive }) => (
        <>
          {isActive && (
            <span
              className={`accent-bar-gradient absolute rounded-full ${
                collapsed ? '-left-1 top-1 bottom-1 w-1' : 'left-0 top-1.5 bottom-1.5 w-1'
              }`}
            />
          )}
          <Icon size={17} strokeWidth={2} className="shrink-0" />
          {collapsed ? (
            <span className="pointer-events-none absolute left-full ml-3 whitespace-nowrap rounded-lg bg-ink text-white text-xs font-medium px-2.5 py-1.5 opacity-0 scale-95 origin-left transition-all group-hover:opacity-100 group-hover:scale-100 z-50">
              {label}
            </span>
          ) : (
            <span className="truncate">{label}</span>
          )}
        </>
      )}
    </NavLink>
  )
}

export default function Sidebar({ variant = 'desktop', onNavigate, collapsed = false, onToggleCollapse }) {
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || user?.profile?.email || 'Account'
  const isMobile = variant === 'mobile'
  const isCollapsed = collapsed && !isMobile

  return (
    <aside
      className={
        isMobile
          ? 'flex w-72 max-w-[85vw] h-full flex-col bg-surface border-r border-line'
          : `hidden md:flex md:flex-col md:fixed md:inset-y-0 bg-surface border-r border-line transition-[width] duration-200 ${isCollapsed ? 'md:w-20' : 'md:w-60'}`
      }
    >
      <div className={`relative flex items-center h-16 border-b border-line shrink-0 ${isCollapsed ? 'justify-center px-2' : 'justify-between px-6'}`}>
        <BrandLogo markOnly={isCollapsed} className={isCollapsed ? 'w-8 h-8' : 'w-40 h-auto'} />
        {!isMobile && (
          <button
            onClick={onToggleCollapse}
            aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className={`h-7 w-7 shrink-0 rounded-full border border-line bg-surface flex items-center justify-center text-ink-soft hover:text-ink ${isCollapsed ? 'absolute -right-3 top-[18px] shadow-pop bg-surface' : ''}`}
          >
            {isCollapsed ? <ChevronsRight size={14} /> : <ChevronsLeft size={14} />}
          </button>
        )}
      </div>

      <nav className={`flex-1 py-5 space-y-1 overflow-x-hidden ${isCollapsed ? 'px-2' : 'px-3'}`}>
        {links.map(({ to, label, icon: Icon, end }) => (
          <NavPill key={to} to={to} end={end} onClick={onNavigate} collapsed={isCollapsed} Icon={Icon}>
            {label}
          </NavPill>
        ))}
      </nav>

      <div className={`border-t border-line ${isCollapsed ? 'px-2 py-4' : 'px-3 py-4'}`}>
        <NavLink
          to="/profile"
          onClick={onNavigate}
          className={({ isActive }) =>
            `group relative flex items-center rounded-full text-sm mb-1 ${
              isCollapsed ? 'justify-center h-11 w-11 mx-auto' : 'gap-3 px-3 py-2'
            } ${isActive ? 'bg-accent-soft text-accent' : 'text-ink-soft hover:text-ink hover:bg-mist'}`
          }
        >
          <span className="h-6 w-6 shrink-0 rounded-full bg-accent flex items-center justify-center text-[11px] font-semibold text-white">
            {name.slice(0, 1).toUpperCase()}
          </span>
          {isCollapsed ? (
            <span className="pointer-events-none absolute left-full ml-3 whitespace-nowrap rounded-lg bg-ink text-white text-xs font-medium px-2.5 py-1.5 opacity-0 scale-95 origin-left transition-all group-hover:opacity-100 group-hover:scale-100 z-50">
              {name}
            </span>
          ) : (
            <span className="truncate">{name}</span>
          )}
        </NavLink>
        <button
          onClick={logout}
          aria-label="Log out"
          className={`group relative flex items-center rounded-full text-sm text-ink-soft hover:text-ink hover:bg-mist ${
            isCollapsed ? 'justify-center h-11 w-11 mx-auto' : 'w-full gap-3 px-3 py-2'
          }`}
        >
          <LogOut size={16} className="shrink-0" />
          {isCollapsed ? (
            <span className="pointer-events-none absolute left-full ml-3 whitespace-nowrap rounded-lg bg-ink text-white text-xs font-medium px-2.5 py-1.5 opacity-0 scale-95 origin-left transition-all group-hover:opacity-100 group-hover:scale-100 z-50">
              Log out
            </span>
          ) : (
            'Log out'
          )}
        </button>
      </div>
    </aside>
  )
}
