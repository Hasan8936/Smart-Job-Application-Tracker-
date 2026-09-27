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
    // The backend sends the JWT in the URL fragment (never sent to servers); ?token= is the legacy form.
    const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || params.get('token')
    if (window.location.hash) window.history.replaceState(null, '', window.location.pathname)
    if (handled.current) return
    handled.current = true
    if (!token) return navigate('/login?error=google-login-failed', { replace: true })
    auth.loginWithToken(token).then(() => navigate('/dashboard', { replace: true })).catch((err) => {
      const detail = err?.response?.status
        ? `google-login-failed-${err.response.status}`
        : (err?.message ? `google-login-failed-network` : 'google-login-failed')
      // eslint-disable-next-line no-console
      console.error('Google sign-in failed after backend redirect:', err)
      navigate(`/login?error=${detail}`, { replace: true })
    })
  }, [auth, navigate, params])

  return <AuthLayout heading="Signing you in" copy="Finishing your Google sign-in..." />
}