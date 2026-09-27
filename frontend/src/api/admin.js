import api from './axios'

export const FLAG_CATEGORIES = ['ABUSE', 'BILLING', 'BUG', 'DATA_ISSUE', 'OTHER']
export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
export const TICKET_STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']

export const searchUsers = (params) => api.get('/admin/users', { params }).then(r => r.data)
export const getUser = (id) => api.get(`/admin/users/${id}`).then(r => r.data)
export const viewUserResume = (id, resumeId) => api.post(`/admin/users/${id}/resumes/${resumeId}/view`).then(r => r.data)
export const flagUser = (id, flag) => api.post(`/admin/users/${id}/flags`, flag).then(r => r.data)
export const resolveFlag = (flagId) => api.post(`/admin/flags/${flagId}/resolve`).then(r => r.data)
export const suspendUser = (id, reason) => api.post(`/admin/users/${id}/suspend`, { reason }).then(r => r.data)
export const unsuspendUser = (id) => api.post(`/admin/users/${id}/unsuspend`).then(r => r.data)

export const listTickets = (params) => api.get('/admin/tickets', { params }).then(r => r.data)
export const getTicket = (id) => api.get(`/admin/tickets/${id}`).then(r => r.data)
export const adminReply = (id, body) => api.post(`/admin/tickets/${id}/messages`, { body }).then(r => r.data)
export const updateTicket = (id, update) => api.patch(`/admin/tickets/${id}`, update).then(r => r.data)

export const listAudit = (params) => api.get('/admin/audit', { params }).then(r => r.data)
