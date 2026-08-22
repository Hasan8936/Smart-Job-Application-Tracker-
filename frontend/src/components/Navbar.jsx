import React, { useContext } from 'react'
import { Link } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'

export default function Navbar(){
  const { user, logout } = useContext(AuthContext)
  const name = user?.profile?.name || user?.profile?.email || null

  return (
    <header className="bg-white shadow">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/" className="font-bold text-lg">Smart Job Tracker</Link>
        </div>
        <div className="flex items-center gap-4">
          {user ? (
            <>
              <Link to="/profile" className="text-sm text-gray-700">{name || 'Profile'}</Link>
              <button onClick={logout} className="bg-red-500 text-white px-3 py-1 rounded text-sm">Logout</button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-sm text-gray-700">Login</Link>
              <Link to="/register" className="text-sm text-gray-700">Register</Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
