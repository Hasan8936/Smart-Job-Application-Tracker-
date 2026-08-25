import api from './axios'

// Candidate profile API (Phase 1). Paths are relative to the axios baseURL (/api).

export function getProfile() {
  return api.get('/profile')
}

// Re-build the profile from a resume. Pass a resumeId to pick one, or omit to use the latest upload.
export function extractProfile(resumeId) {
  const config = resumeId ? { params: { resumeId } } : {}
  return api.post('/profile/extract', null, config)
}

export function saveProfile(dto) {
  return api.put('/profile', dto)
}
