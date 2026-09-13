import api from './axios'

export const getPrefill = () => api.get('/resume/build/prefill').then(r => r.data)

export const exportResume = async (dto) => {
  const res = await api.post('/resume/build/export', dto, { responseType: 'blob' })
  const resumeId = res.headers['x-resume-id']
  return { blob: res.data, resumeId }
}

export const previewResume = async (dto) => {
  const res = await api.post('/resume/build/preview', dto, { responseType: 'blob' })
  return res.data
}
