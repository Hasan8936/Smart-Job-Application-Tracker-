import api from './axios'

export const getCalendarStatus = () => api.get('/google-calendar/status')
export const getCalendarConnectUrl = () => api.get('/google-calendar/connect-url')
export const disconnectCalendar = () => api.delete('/google-calendar/disconnect')
