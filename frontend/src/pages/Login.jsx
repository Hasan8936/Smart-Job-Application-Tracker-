import React, { useState, useContext } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'

export default function Login(){
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const auth = useContext(AuthContext)
  const nav = useNavigate()
  const location = useLocation()

  const submit = async (e) => {
    e.preventDefault()
    try{
      await auth.login(email, password)
      const dest = location.state?.from?.pathname || '/'
      nav(dest)
    }catch(err){
      alert('Login failed')
    }
  }

  return (
    <div className="max-w-md mx-auto bg-white p-6 rounded shadow">
      <h2 className="text-xl mb-4">Login</h2>
      <form onSubmit={submit}>
        <label className="block mb-2">Email</label>
        <input className="w-full p-2 border mb-4" value={email} onChange={e=>setEmail(e.target.value)} />
        <label className="block mb-2">Password</label>
        <input type="password" className="w-full p-2 border mb-4" value={password} onChange={e=>setPassword(e.target.value)} />
        <button className="bg-blue-600 text-white px-4 py-2 rounded">Login</button>
      </form>
    </div>
  )
}
