import api from './axios'
export async function syncCalendar() { return (await api.post('/interviews/sync-calendar')).data }
export async function getInterviewCandidates() { return (await api.get('/interviews/candidates')).data }
export async function confirmInterviewCandidate(source, id, applicationId, status) { return api.post(`/interviews/candidates/${source}/${id}/confirm`, { applicationId, status }) }
export async function dismissInterviewCandidate(source, id) { return api.post(`/interviews/candidates/${source}/${id}/dismiss`) }
