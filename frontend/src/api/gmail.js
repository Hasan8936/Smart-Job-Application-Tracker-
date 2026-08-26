import api from './axios'
export async function getGmailStatus() { return (await api.get('/gmail/status')).data }
export async function beginGmailConnect() { return (await api.get('/gmail/connect')).data }
export async function syncGmail() { return (await api.post('/gmail/sync')).data }
export async function disconnectGmail() { return api.delete('/gmail') }
export async function getGmailReviewQueue() { return (await api.get('/gmail/review')).data }
export async function reviewGmailEmail(emailId, applicationId, status) { return api.post(`/gmail/review/${emailId}`, { applicationId, status }) }