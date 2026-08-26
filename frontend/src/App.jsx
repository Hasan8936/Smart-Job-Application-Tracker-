import React from 'react'
import { Routes, Route } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Applications from './pages/Applications'
import ResumeMatch from './pages/ResumeMatch'
import Reminders from './pages/Reminders'
import Profile from './pages/Profile'
import CandidateProfile from './pages/CandidateProfile'
import ProtectedRoute from './components/ProtectedRoute'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import OAuth2Callback from './pages/OAuth2Callback'
import Discovery from './pages/Discovery'
import ResumeTailoring from './pages/ResumeTailoring'
import ApplicationPreparation from './pages/ApplicationPreparation'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/oauth2/callback" element={<OAuth2Callback />} />
      <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/applications" element={<ProtectedRoute><Applications /></ProtectedRoute>} />
      <Route path="/resume-match" element={<ProtectedRoute><ResumeMatch /></ProtectedRoute>} />
      <Route path="/resume-tailoring" element={<ProtectedRoute><ResumeTailoring /></ProtectedRoute>} />
      <Route path="/application-preparation" element={<ProtectedRoute><ApplicationPreparation /></ProtectedRoute>} />
      <Route path="/candidate-profile" element={<ProtectedRoute><CandidateProfile /></ProtectedRoute>} />
      <Route path="/discovery" element={<ProtectedRoute><Discovery /></ProtectedRoute>} />
      <Route path="/reminders" element={<ProtectedRoute><Reminders /></ProtectedRoute>} />
      <Route path="/profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
    </Routes>
  )
}
