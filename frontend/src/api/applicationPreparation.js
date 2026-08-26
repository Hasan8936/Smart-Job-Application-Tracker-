import api from './axios'
export const getApplicationProfile = () => api.get('/application-preparation/profile')
export const saveApplicationProfile = (payload) => api.put('/application-preparation/profile', payload)
export const prepareApplication = (payload) => api.post('/application-preparation/prepare', payload)
export const decideApplicationSuggestion = (id, decision) => api.patch(`/application-preparation/suggestions/${id}`, { decision })