import React from 'react'

const API_ORIGIN = (import.meta.env.VITE_API_BASE || 'http://localhost:8080/api').replace(/\/api\/?$/, '')

// Google OAuth entry point. The backend creates the account on first sign-in,
// so the same button serves both /login and /register.
export default function GoogleAuthButton() {
  return (
    <>
      <div className="flex items-center gap-3 my-5">
        <div className="h-px flex-1 bg-line" />
        <span className="text-xs text-muted">Or continue with</span>
        <div className="h-px flex-1 bg-line" />
      </div>

      <a
        href={`${API_ORIGIN}/oauth2/authorization/google`}
        className="w-full border border-line text-ink text-sm font-medium py-2.5 rounded-full hover:bg-paper flex items-center justify-center gap-2"
      >
        <GoogleIcon /> Continue with Google
      </a>
      <p className="text-[11px] leading-relaxed text-muted text-center mt-2">
        Google Sign-In shares your basic profile details only. Gmail and Calendar require separate, optional connections.{' '}
        <a href="/privacy#google-data" className="text-accent hover:underline">Privacy details</a>
      </p>
    </>
  )
}

function GoogleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 01-1.8 2.72v2.26h2.9c1.7-1.57 2.68-3.87 2.68-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.8.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.94v2.33A9 9 0 009 18z" />
      <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 013.68 9c0-.59.1-1.16.27-1.7V4.97H.94A9 9 0 000 9c0 1.45.35 2.83.94 4.03l3.01-2.33z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.46 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 00.94 4.97L3.95 7.3C4.66 5.17 6.65 3.58 9 3.58z" />
    </svg>
  )
}
