import axios from 'axios'
import { clearToken, getToken } from '../lib/tokenStorage'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use(cfg => {
    const token = getToken()
    if (token) cfg.headers.Authorization = `Bearer ${token}`
    return cfg
})

// Clear stale token and redirect to login on 401 so users are not silently stuck
api.interceptors.response.use(
    res => res,
    err => {
        if (err.response?.status === 401) {
            clearToken()
            // Avoid redirect loop if already on auth pages
            if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
                window.location.replace('/login')
            }
        }
        return Promise.reject(err)
    }
)

export default api