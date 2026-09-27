import React, { useContext } from 'react'
import { Navigate } from 'react-router-dom'
import ProtectedRoute from './ProtectedRoute'
import { AuthContext } from '../context/AuthContext'

/** Signed-in admins only. The API enforces this independently (/api/admin/** is ADMIN-only). */
export default function AdminRoute({ children }) {
  const { user } = useContext(AuthContext)
  return (
    <ProtectedRoute>
      {user?.profile && user.profile.role !== 'ADMIN' ? <Navigate to="/dashboard" replace /> : children}
    </ProtectedRoute>
  )
}
