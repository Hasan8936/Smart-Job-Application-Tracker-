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

export const importResume = (resumeId) =>
  api.post(`/resume/build/import?resumeId=${resumeId}`).then(r => r.data)

export const aiEnhanceResume = (dto) =>
  api.post('/resume/build/ai-enhance', dto).then(r => r.data)

export const exportLatex = async (dto) => {
  const res = await api.post('/resume/build/export-latex', dto, { responseType: 'blob' })
  return res.data
}

export const listResumes = () => api.get('/resume/me').then(r => r.data)

export const uploadResume = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await api.post('/resume/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}
