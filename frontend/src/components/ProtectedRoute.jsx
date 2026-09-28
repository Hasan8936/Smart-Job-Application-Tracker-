import React, { useContext } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'
import PageLoader from './PageLoader'

export default function ProtectedRoute({ children }){
  const { user, loading } = useContext(AuthContext)
  const location = useLocation()
  if (loading) return <PageLoader />  // session restore: branded loader, not a blank screen
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  return children
}
