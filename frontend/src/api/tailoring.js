import api from './axios'

export const analyzeTailoring = (payload) => api.post('/resume-tailoring/analyze', payload)
export const decideSuggestion = (id, decision) => api.patch(`/resume-tailoring/suggestions/${id}`, { decision })
export const createResumeVersion = (sessionId) => api.post(`/resume-tailoring/sessions/${sessionId}/versions`)
export const getResumeVersions = () => api.get('/resume-tailoring/versions')