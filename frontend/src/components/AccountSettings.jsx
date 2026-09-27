import React, { useContext, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Camera, Download, KeyRound, Loader2, Save, Trash2, X } from 'lucide-react'
import { AuthContext } from '../context/AuthContext'
import Avatar from './Avatar'
import {
  changePassword, deleteMyAccount, deleteProfilePhoto, exportMyData, getAccountDetails, sendPasswordResetLink,
  updateAccountDetails, uploadProfilePhoto, PHOTO_CHANGED_EVENT,
} from '../api/account'

const FIELDS = [
  { key: 'name', label: 'Display name', placeholder: 'Jane Doe', max: 100 },
  { key: 'headline', label: 'Headline', placeholder: 'Backend engineer · Java, Spring Boot', max: 200 },
  { key: 'location', label: 'Location', placeholder: 'Bengaluru, India', max: 200 },
  { key: 'phone', label: 'Phone', placeholder: '+91 98765 43210', max: 30 },
  { key: 'linkedinUrl', label: 'LinkedIn', placeholder: 'linkedin.com/in/janedoe', max: 500 },
  { key: 'githubUrl', label: 'GitHub', placeholder: 'github.com/janedoe', max: 500 },
  { key: 'websiteUrl', label: 'Website', placeholder: 'janedoe.dev', max: 500 },
]
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp']
const MAX_PHOTO_BYTES = 2 * 1024 * 1024

function Section({ title, icon: Icon, children, danger }) {
  return (
    <section className={`border rounded-xl p-5 ${danger ? 'border-status-rejected/40 bg-status-rejectedSoft/40' : 'border-line bg-surface'}`}>
      <h2 className={`font-display text-base mb-3 flex items-center gap-2 ${danger ? 'text-status-rejected' : 'text-ink'}`}>
        {Icon && <Icon size={16} />} {title}
      </h2>
      {children}
    </section>
  )
}

function Status({ message }) {
  if (!message) return null
  return <p role="status" className={`text-xs mt-2 ${message.error ? 'text-status-rejected' : 'text-status-offer'}`}>{message.text}</p>
}

const errorText = (e, fallback) => e?.response?.data?.error || fallback

export default function AccountSettings() {
  const { user, refreshProfile, logout } = useContext(AuthContext)
  const navigate = useNavigate()
  const profile = user?.profile

  // Details
  const [details, setDetails] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [savingDetails, setSavingDetails] = useState(false)
  const [detailsMsg, setDetailsMsg] = useState(null)
  useEffect(() => {
    getAccountDetails().then(d => setDetails(Object.fromEntries([['email', d.email], ...FIELDS.map(f => [f.key, d[f.key] || ''])])))
      .catch(() => setDetailsMsg({ error: true, text: 'Could not load your details.' }))
  }, [])

  async function saveDetails(e) {
    e.preventDefault()
    setSavingDetails(true); setDetailsMsg(null); setFieldErrors({})
    try {
      const saved = await updateAccountDetails(details)
      setDetails(Object.fromEntries([['email', saved.email], ...FIELDS.map(f => [f.key, saved[f.key] || ''])]))
      setDetailsMsg({ text: 'Details saved.' })
      refreshProfile().catch(() => {})
    } catch (err) {
      setFieldErrors(err.response?.data?.fields || {})
      setDetailsMsg({ error: true, text: errorText(err, 'Could not save your details.') === 'validation failed' ? 'Please fix the highlighted fields.' : errorText(err, 'Could not save your details.') })
    } finally { setSavingDetails(false) }
  }

  // Photo
  const fileRef = useRef(null)
  const [preview, setPreview] = useState(null)
  const [pendingFile, setPendingFile] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoMsg, setPhotoMsg] = useState(null)
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  function pickPhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (!ACCEPTED.includes(file.type)) { setPhotoMsg({ error: true, text: 'Choose a JPEG, PNG or WebP image.' }); return }
    if (file.size > MAX_PHOTO_BYTES) { setPhotoMsg({ error: true, text: 'Photos must be 2 MB or smaller.' }); return }
    setPhotoMsg(null); setPendingFile(file); setPreview(URL.createObjectURL(file))
  }

  async function savePhoto() {
    setPhotoBusy(true); setPhotoMsg(null)
    try {
      await uploadProfilePhoto(pendingFile)
      setPendingFile(null); setPreview(null)
      await refreshProfile().catch(() => {})
      window.dispatchEvent(new Event(PHOTO_CHANGED_EVENT))
      setPhotoMsg({ text: 'Photo updated.' })
    } catch (err) { setPhotoMsg({ error: true, text: errorText(err, 'Could not upload the photo.') }) }
    finally { setPhotoBusy(false) }
  }

  async function removePhoto() {
    setPhotoBusy(true); setPhotoMsg(null)
    try {
      await deleteProfilePhoto()
      await refreshProfile().catch(() => {})
      window.dispatchEvent(new Event(PHOTO_CHANGED_EVENT))
      setPhotoMsg({ text: 'Photo removed.' })
    } catch (err) { setPhotoMsg({ error: true, text: errorText(err, 'Could not remove the photo.') }) }
    finally { setPhotoBusy(false) }
  }

  // Password
  const passwordSet = profile?.passwordSet !== false
  const [pw, setPw] = useState({ current: '', next: '', confirm: '' })
  const [pwBusy, setPwBusy] = useState(false)
  const [pwMsg, setPwMsg] = useState(null)
  async function savePassword(e) {
    e.preventDefault()
    setPwMsg(null)
    if (pw.next.length < 8) { setPwMsg({ error: true, text: 'Use at least 8 characters.' }); return }
    if (pw.next !== pw.confirm) { setPwMsg({ error: true, text: 'The new passwords do not match.' }); return }
    setPwBusy(true)
    try {
      await changePassword(passwordSet ? pw.current : null, pw.next)
      setPw({ current: '', next: '', confirm: '' })
      setPwMsg({ text: passwordSet ? 'Password changed.' : 'Password set. You can now also sign in with your email.' })
      refreshProfile().catch(() => {})
    } catch (err) { setPwMsg({ error: true, text: errorText(err, 'Could not change your password.') }) }
    finally { setPwBusy(false) }
  }

  // Reset link: for users who don't know their password (e.g. Google sign-ups from before passwords were settable).
  const [resetBusy, setResetBusy] = useState(false)
  const [resetMsg, setResetMsg] = useState(null)
  async function emailResetLink() {
    setResetBusy(true); setResetMsg(null)
    try {
      await sendPasswordResetLink(profile?.email)
      setResetMsg({ text: `We've emailed a link to ${profile?.email}. Use it to choose a password, then come back here.` })
    } catch (err) { setResetMsg({ error: true, text: errorText(err, 'Could not send the link. Please try again.') }) }
    finally { setResetBusy(false) }
  }
  const resetLink = (
    <p className="text-xs text-muted mt-3">
      Don't know your password — for example, you signed up with Google?{' '}
      <button type="button" disabled={resetBusy} onClick={emailResetLink} className="text-accent hover:underline disabled:opacity-50">
        {resetBusy ? 'Sending…' : 'Email me a link to set one'}
      </button>
    </p>
  )

  // Export
  const [exporting, setExporting] = useState(false)
  const [exportMsg, setExportMsg] = useState(null)
  async function download() {
    setExporting(true); setExportMsg(null)
    try {
      const blob = await exportMyData()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `smart-job-tracker-data-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) { setExportMsg({ error: true, text: 'Could not prepare your data. Please try again.' }) }
    finally { setExporting(false) }
  }

  // Delete
  const [confirming, setConfirming] = useState(false)
  const [confirmValue, setConfirmValue] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [deleteMsg, setDeleteMsg] = useState(null)
  async function deleteAccount() {
    setDeleting(true); setDeleteMsg(null)
    try {
      await deleteMyAccount(passwordSet ? { password: confirmValue } : { confirmEmail: confirmValue })
      logout()
      navigate('/', { replace: true })
    } catch (err) {
      setDeleteMsg({ error: true, text: errorText(err, 'Could not delete your account.') })
      setDeleting(false)
    }
  }

  const input = 'w-full px-3 py-2 rounded-lg border border-line bg-paper focus:bg-surface text-sm'

  return (
    <div className="space-y-5">
      <Section title="Details">
        {!details ? <Loader2 size={16} className="animate-spin text-muted" /> : (
          <form onSubmit={saveDetails} className="grid gap-3 sm:grid-cols-2">
            {FIELDS.map(f => (
              <label key={f.key} className={f.key === 'headline' ? 'sm:col-span-2' : ''}>
                <span className="block text-xs font-medium text-muted mb-1">{f.label}</span>
                <input className={`${input} ${fieldErrors[f.key] ? 'border-status-rejected' : ''}`} value={details[f.key]}
                  maxLength={f.max} placeholder={f.placeholder} aria-invalid={!!fieldErrors[f.key]}
                  onChange={e => setDetails(d => ({ ...d, [f.key]: e.target.value }))} />
                {fieldErrors[f.key] && <span className="text-[11px] text-status-rejected">{fieldErrors[f.key]}</span>}
              </label>
            ))}
            <label className="sm:col-span-2">
              <span className="block text-xs font-medium text-muted mb-1">Email</span>
              <input className={`${input} opacity-70`} value={details.email || ''} disabled readOnly />
            </label>
            <div className="sm:col-span-2 flex items-center gap-3">
              <button disabled={savingDetails} className="inline-flex items-center gap-1.5 btn-gradient rounded-full px-4 py-2 text-sm font-medium disabled:opacity-50">
                {savingDetails ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} Save details
              </button>
              <Status message={detailsMsg} />
            </div>
          </form>
        )}
      </Section>

      <Section title="Photo" icon={Camera}>
        <div className="flex items-center gap-4">
          {preview ? <img src={preview} alt="New profile photo preview" className="h-16 w-16 rounded-full object-cover" />
            : <Avatar className="h-16 w-16 text-xl" />}
          <div className="flex flex-wrap gap-2">
            <input ref={fileRef} type="file" accept={ACCEPTED.join(',')} className="hidden" onChange={pickPhoto} />
            {pendingFile ? (
              <>
                <button type="button" disabled={photoBusy} onClick={savePhoto} className="inline-flex items-center gap-1.5 btn-gradient rounded-full px-3 py-2 text-sm disabled:opacity-50">
                  {photoBusy && <Loader2 size={14} className="animate-spin" />} Save photo
                </button>
                <button type="button" disabled={photoBusy} onClick={() => { setPendingFile(null); setPreview(null) }} className="border border-line rounded-full px-3 py-2 text-sm text-ink-soft">Cancel</button>
              </>
            ) : (
              <>
                <button type="button" onClick={() => fileRef.current?.click()} className="border border-line rounded-full px-3 py-2 text-sm text-ink-soft hover:text-ink">
                  {profile?.hasPhoto ? 'Replace photo' : 'Upload photo'}
                </button>
                {profile?.hasPhoto && (
                  <button type="button" disabled={photoBusy} onClick={removePhoto} className="border border-line rounded-full px-3 py-2 text-sm text-status-rejected disabled:opacity-50">Remove</button>
                )}
              </>
            )}
          </div>
        </div>
        <p className="text-[11px] text-muted mt-2">JPEG, PNG or WebP, up to 2 MB. We resize it to 512×512 and remove location and camera data.</p>
        <Status message={photoMsg} />
      </Section>

      <Section title={passwordSet ? 'Change password' : 'Set a password'} icon={KeyRound}>
        {!passwordSet && <p className="text-xs text-muted mb-3">You signed up with Google. Set a password to also sign in with your email.</p>}
        <form onSubmit={savePassword} className="grid gap-3 sm:grid-cols-3">
          {passwordSet && (
            <input type="password" autoComplete="current-password" className={input} placeholder="Current password"
              value={pw.current} onChange={e => setPw(p => ({ ...p, current: e.target.value }))} required />
          )}
          <input type="password" autoComplete="new-password" className={input} placeholder="New password (8+ characters)"
            value={pw.next} onChange={e => setPw(p => ({ ...p, next: e.target.value }))} required minLength={8} />
          <input type="password" autoComplete="new-password" className={input} placeholder="Confirm new password"
            value={pw.confirm} onChange={e => setPw(p => ({ ...p, confirm: e.target.value }))} required />
          <div className="sm:col-span-3 flex items-center gap-3">
            <button disabled={pwBusy} className="inline-flex items-center gap-1.5 border border-line rounded-full px-4 py-2 text-sm text-ink disabled:opacity-50">
              {pwBusy && <Loader2 size={14} className="animate-spin" />} {passwordSet ? 'Change password' : 'Set password'}
            </button>
            <Status message={pwMsg} />
          </div>
        </form>
        {passwordSet && <>{resetLink}<Status message={resetMsg} /></>}
      </Section>

      {/* Full JSON export is a super-admin tool; regular accounts don't see it. */}
      {profile?.role === 'ADMIN' && <Section title="Your data" icon={Download}>
        <p className="text-sm text-muted mb-3">Download everything you've stored here — profile, applications, resumes, reminders, saved jobs and support requests — as a JSON file.</p>
        <button type="button" disabled={exporting} onClick={download} className="inline-flex items-center gap-1.5 border border-line rounded-full px-4 py-2 text-sm text-ink disabled:opacity-50">
          {exporting ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />} Download my data
        </button>
        <Status message={exportMsg} />
      </Section>}

      <Section title="Danger zone" icon={AlertTriangle} danger>
        <p className="text-sm text-ink-soft mb-3">
          Permanently delete your account and all of your data, and disconnect Gmail and Google Calendar. This can't be undone.
        </p>
        <button type="button" onClick={() => { setConfirming(true); setConfirmValue(''); setDeleteMsg(null) }}
          className="inline-flex items-center gap-1.5 bg-status-rejected text-white rounded-full px-4 py-2 text-sm font-medium hover:opacity-90">
          <Trash2 size={14} /> Delete my account
        </button>
      </Section>

      {confirming && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="delete-title">
          <button aria-label="Close" className="absolute inset-0 bg-ink/50" onClick={() => !deleting && setConfirming(false)} />
          <div className="relative w-full max-w-md bg-surface rounded-xl2 shadow-card p-6">
            <button onClick={() => !deleting && setConfirming(false)} className="absolute top-4 right-4 text-muted hover:text-ink" aria-label="Cancel"><X size={16} /></button>
            <h3 id="delete-title" className="font-display text-lg text-ink mb-2">Delete your account?</h3>
            <p className="text-sm text-muted mb-4">
              This permanently removes your profile, applications, resumes, reminders and support history.
              {passwordSet ? ' Enter your password to confirm.' : ` Type ${profile?.email} to confirm.`}
            </p>
            <input autoFocus type={passwordSet ? 'password' : 'email'} className={input} value={confirmValue}
              placeholder={passwordSet ? 'Current password' : profile?.email} onChange={e => setConfirmValue(e.target.value)} />
            <Status message={deleteMsg} />
            {passwordSet && <>{resetLink}<Status message={resetMsg} /></>}
            <div className="flex justify-end gap-2 mt-4">
              <button disabled={deleting} onClick={() => setConfirming(false)} className="border border-line rounded-full px-4 py-2 text-sm">Cancel</button>
              <button disabled={deleting || !confirmValue.trim()} onClick={deleteAccount}
                className="inline-flex items-center gap-1.5 bg-status-rejected text-white rounded-full px-4 py-2 text-sm font-medium disabled:opacity-50">
                {deleting && <Loader2 size={14} className="animate-spin" />} Delete permanently
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
