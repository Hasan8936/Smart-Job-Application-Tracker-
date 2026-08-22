import React, { useContext } from 'react'
import { AuthContext } from '../context/AuthContext'

export default function Profile(){
  const { user, logout } = useContext(AuthContext)
  return (
    <div className="max-w-md mx-auto bg-white p-6 rounded shadow">
      <h2 className="text-xl mb-4">Profile</h2>
      <div className="mb-4">Logged in: {user ? 'Yes' : 'No'}</div>
      <div className="mb-4">Token: <code className="break-all">{user?.token}</code></div>
      <button className="bg-red-600 text-white px-4 py-2 rounded" onClick={logout}>Logout</button>
    </div>
  )
}
