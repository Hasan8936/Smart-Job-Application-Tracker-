import React, { createContext, useState, useEffect } from 'react'
import api from '../api/axios'
import { getToken, setToken, clearToken } from '../lib/tokenStorage'

export const AuthContext = createContext(null)

export function AuthProvider({ children }){
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(() => !!getToken())

  useEffect(()=>{
    const token = getToken()
    if (token) {
      // set token immediately for axios interceptor, then fetch profile
      setUser({ token })
      fetchProfile(token).catch(() => {}).finally(() => setLoading(false))
    }
  }, [])

  async function fetchProfile(token){
    try{
      const res = await api.get('/users/me')
      setUser({ token, profile: res.data })
    }catch(err){
      // invalid token, or /users/me itself failed (network, CORS, 404, 500, ...)
      clearToken()
      setUser(null)
      throw err
    }
  }

  const login = async (email, password, remember = true) => {
    const res = await api.post('/auth/login', { email, password })
    setToken(res.data.token, remember)
    await fetchProfile(res.data.token)
    return res
  }

  const loginWithToken = async (token) => {
    setToken(token, true)
    await fetchProfile(token)
  }

  const register = async (name, email, password) => {
    return api.post('/auth/register', { name, email, password })
  }

  const logout = () => {
    clearToken()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, loginWithToken, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
