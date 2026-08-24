import React, { useContext, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'
import AuthLayout from '../components/AuthLayout'

export default function OAuth2Callback() {
  const [params] = useSearchParams()
  const auth = useContext(AuthContext)
  const navigate = useNavigate()
  const handled = useRef(false)

  useEffect(() => {
    const token = params.get('token')
    if (handled.current) return
    handled.current = true
    if (!token) return navigate('/login?error=google-login-failed', { replace: true })
    auth.loginWithToken(token).then(() => navigate('/', { replace: true })).catch(() => navigate('/login?error=google-login-failed', { replace: true }))
  }, [auth, navigate, params])

  return <AuthLayout heading="Signing you in" copy="Finishing your Google sign-in..." />
}