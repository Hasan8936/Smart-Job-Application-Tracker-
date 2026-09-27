import api from './axios'

export const SUPPORT_CATEGORIES = [
  { value: 'ACCOUNT', label: 'Account & sign-in' },
  { value: 'BUG', label: 'Something is broken' },
  { value: 'JOB_SEARCH', label: 'Job discovery' },
  { value: 'RESUME', label: 'Resumes & matching' },
  { value: 'BILLING', label: 'Billing' },
  { value: 'OTHER', label: 'Other' },
]

export const listMyTickets = () => api.get('/support/tickets').then(r => r.data)
export const getMyTicket = (id) => api.get(`/support/tickets/${id}`).then(r => r.data)
export const createTicket = (ticket) => api.post('/support/tickets', ticket).then(r => r.data)
export const replyToTicket = (id, body) => api.post(`/support/tickets/${id}/messages`, { body }).then(r => r.data)
