import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Analytics } from '@vercel/analytics/react'
import App from './App'
import './index.css'
import { AuthProvider } from './context/AuthContext'

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      {/* Opt into v7 behaviour now (no relative links in splat routes are affected); silences the upgrade warnings. */}
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
        <Analytics />
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>
)
