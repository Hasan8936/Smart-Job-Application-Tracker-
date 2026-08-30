import React, { createContext, useState, useEffect } from 'react'
import api from '../api/axios'

export const AuthContext = createContext(null)

export function AuthProvider({ children }){
  const [user, setUser] = useState(null)

  useEffect(()=>{
    const token = localStorage.getItem('token')
    if (token) {
      // set token immediately for axios interceptor, then fetch profile
      setUser({ token })
      fetchProfile(token).catch(() => {})
    }
  }, [])

  async function fetchProfile(token){
    try{
      const res = await api.get('/users/me')
      setUser({ token, profile: res.data })
    }catch(err){
      // invalid token, or /users/me itself failed (network, CORS, 404, 500, ...)
      localStorage.removeItem('token')
      setUser(null)
      throw err
    }
  }

  const login = async (email, password) => {
    const res = await api.post('/auth/login', { email, password })
    localStorage.setItem('token', res.data.token)
    await fetchProfile(res.data.token)
    return res
  }

  const loginWithToken = async (token) => {
    localStorage.setItem('token', token)
    await fetchProfile(token)
  }

  const register = async (name, email, password) => {
    return api.post('/auth/register', { name, email, password })
  }

  const logout = () => {
    localStorage.removeItem('token')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, loginWithToken, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
