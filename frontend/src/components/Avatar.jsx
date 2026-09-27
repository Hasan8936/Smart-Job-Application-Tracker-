import React, { useContext, useEffect, useState } from 'react'
import { AuthContext } from '../context/AuthContext'
import { fetchProfilePhotoUrl, PHOTO_CHANGED_EVENT } from '../api/account'

/** The signed-in user's profile photo, or their initial when there is none (or it fails to load). */
export default function Avatar({ className = 'h-6 w-6 text-[11px]' }) {
  const { user } = useContext(AuthContext)
  const profile = user?.profile
  const initial = (profile?.name || profile?.email || '?').slice(0, 1).toUpperCase()
  const [url, setUrl] = useState(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    const bump = () => setVersion(v => v + 1)
    window.addEventListener(PHOTO_CHANGED_EVENT, bump)
    return () => window.removeEventListener(PHOTO_CHANGED_EVENT, bump)
  }, [])

  useEffect(() => {
    let objectUrl = null
    let cancelled = false
    setUrl(null)
    if (profile?.hasPhoto || version > 0) {
      fetchProfilePhotoUrl()
        .then(u => { objectUrl = u; if (!cancelled) setUrl(u); else URL.revokeObjectURL(u) })
        .catch(() => { if (!cancelled) setUrl(null) })
    }
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [profile?.hasPhoto, version])

  if (url) {
    return <img src={url} alt="" className={`${className} shrink-0 rounded-full object-cover`} onError={() => setUrl(null)} />
  }
  return (
    <span className={`${className} shrink-0 rounded-full bg-accent flex items-center justify-center font-semibold text-white`} aria-hidden="true">
      {initial}
    </span>
  )
}
