import api from './axios'

export const getAccountDetails = () => api.get('/users/me/details').then(r => r.data)
export const updateAccountDetails = (details) => api.put('/users/me/details', details).then(r => r.data)

export const uploadProfilePhoto = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/users/me/photo', form, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data)
}
export const deleteProfilePhoto = () => api.delete('/users/me/photo')

/** The photo endpoint needs the Bearer token, so an <img src> can't load it directly: fetch it as a blob URL. */
export const fetchProfilePhotoUrl = async () => {
  const res = await api.get('/users/me/photo', { responseType: 'blob' })
  return URL.createObjectURL(res.data)
}

/**
 * Emails the signed-in user a password reset link (the normal forgot-password flow). For accounts that don't know
 * their password — e.g. Google sign-ups from before passwords could be set here.
 */
export const sendPasswordResetLink = (email) => api.post('/auth/forgot-password', { email })

export const changePassword = (currentPassword, newPassword) =>
  api.post('/users/me/password', { currentPassword, newPassword })

export const exportMyData = async () => {
  const res = await api.get('/users/me/export', { responseType: 'blob' })
  return res.data
}

export const deleteMyAccount = ({ password, confirmEmail }) =>
  api.delete('/users/me', { data: { password, confirmEmail } })

/** Other components (sidebar avatar) listen for this to reload the photo. */
export const PHOTO_CHANGED_EVENT = 'sjt:profile-photo-changed'
