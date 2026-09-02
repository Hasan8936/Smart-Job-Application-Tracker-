import axios from 'axios'
import { getToken } from '../lib/tokenStorage'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api',
    headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use(cfg => {
    const token = getToken()
    if (token) cfg.headers.Authorization = `Bearer ${token}`
    return cfg
})

export default api