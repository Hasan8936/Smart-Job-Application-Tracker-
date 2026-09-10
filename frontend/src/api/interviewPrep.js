import api from './axios'

export const generateInterviewPrep = (payload) => api.post('/interview-prep/generate', payload)
export const listInterviewPrepSessions = () => api.get('/interview-prep/sessions')
export const getInterviewPrepSession = (id) => api.get(`/interview-prep/sessions/${id}`)
export const exportInterviewPrep = (id) => api.get(`/interview-prep/sessions/${id}/export`, { responseType: 'blob' })
