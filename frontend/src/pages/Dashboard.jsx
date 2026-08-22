import React, { useEffect, useState } from 'react'
import api from '../api/axios'
import ApplicationCard from '../components/ApplicationCard'

export default function Dashboard(){
  const [applications, setApplications] = useState([])
  const [resumes, setResumes] = useState([])
  const [file, setFile] = useState(null)
  const [jd, setJd] = useState('')
  const [matchResult, setMatchResult] = useState(null)
  const [selectedResumeId, setSelectedResumeId] = useState(null)

  const [appForm, setAppForm] = useState({ companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED' })
  const [editingAppId, setEditingAppId] = useState(null)

  useEffect(()=>{ fetchApps(); fetchResumes(); }, [])

  async function fetchApps(){
    try{ const res = await api.get('/applications'); setApplications(res.data) }catch(e){ console.error(e) }
  }

  async function fetchResumes(){
    try{ const res = await api.get('/resume/me'); setResumes(res.data) }catch(e){ console.error(e) }
  }

  async function upload(e){
    e.preventDefault()
    if (!file) return
    const form = new FormData(); form.append('file', file)
    try{ await api.post('/resume/upload', form, { headers: {'Content-Type':'multipart/form-data'} }); fetchResumes() }catch(e){ console.error(e) }
  }

  async function doMatch(){
    const rid = selectedResumeId || (resumes[0] && resumes[0].id)
    if (!rid) return alert('Upload/select a resume first')
    try{
      const res = await api.post('/match/score', { resumeId: rid, jobDescriptionText: jd })
      setMatchResult(res.data)
    }catch(e){ console.error(e) }
  }

  async function submitApp(e){
    e.preventDefault()
    try{
      if (editingAppId) {
        await api.put(`/applications/${editingAppId}`, appForm)
      } else {
        await api.post('/applications', appForm)
      }
      setAppForm({ companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED' })
      setEditingAppId(null)
      fetchApps()
    }catch(e){ console.error(e) }
  }

  function startEdit(app){
    setEditingAppId(app.id)
    setAppForm({ companyName: app.companyName, roleTitle: app.roleTitle, jobDescription: app.jobDescription, status: app.status })
  }

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-2xl mb-4">Dashboard</h1>

      <section className="mb-6">
        <h2 className="text-lg mb-2">Applications</h2>
        {applications.length === 0 ? <div>No applications yet</div> : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {applications.map(a=> <ApplicationCard key={a.id} app={a} onEdit={startEdit} />)}
          </div>
        )}
      </section>

      <section className="mb-6">
        <h2 className="text-lg mb-2">Resumes</h2>
        <form onSubmit={upload} className="mb-4">
          <input type="file" onChange={ev=>setFile(ev.target.files[0])} />
          <button className="ml-2 bg-blue-600 text-white px-3 py-1 rounded">Upload</button>
        </form>
        <div className="mb-2">Select resume for matching:</div>
        <select value={selectedResumeId || ''} onChange={e=>setSelectedResumeId(e.target.value ? Number(e.target.value) : null)} className="p-2 border mb-2">
          <option value="">-- choose resume --</option>
          {resumes.map(r => <option key={r.id} value={r.id}>{r.fileName}</option>)}
        </select>
        <ul>
          {resumes.map(r => <li key={r.id}>{r.fileName} — uploaded {new Date(r.uploadedAt).toLocaleString()}</li>)}
        </ul>
      </section>

      <section className="mb-6">
        <h2 className="text-lg mb-2">Match Resume → Job Description</h2>
        <textarea value={jd} onChange={e=>setJd(e.target.value)} className="w-full p-2 border mb-2" rows={4} />
        <button onClick={doMatch} className="bg-green-600 text-white px-3 py-1 rounded">Compute Match</button>
        {matchResult && (
          <div className="mt-4 bg-white p-3 rounded shadow">
            <div className="font-semibold">Score: {Math.round(matchResult.matchScore)}%</div>
            <div className="mt-2"><strong>Matched:</strong></div>
            <ul className="list-disc ml-6">{matchResult.matchedKeywords.map(k => <li key={k}>{k}</li>)}</ul>
            <div className="mt-2"><strong>Missing:</strong></div>
            <ul className="list-disc ml-6 text-red-600">{matchResult.missingKeywords.map(k => <li key={k}>{k}</li>)}</ul>
          </div>
        )}
      </section>

      <section className="mb-6">
        <h2 className="text-lg mb-2">Create / Edit Application</h2>
        <form onSubmit={submitApp} className="mb-4 bg-white p-4 rounded shadow">
          <input className="w-full p-2 border mb-2" placeholder="Company" value={appForm.companyName} onChange={e=>setAppForm({...appForm, companyName: e.target.value})} />
          <input className="w-full p-2 border mb-2" placeholder="Role" value={appForm.roleTitle} onChange={e=>setAppForm({...appForm, roleTitle: e.target.value})} />
          <textarea className="w-full p-2 border mb-2" placeholder="Job description" value={appForm.jobDescription} onChange={e=>setAppForm({...appForm, jobDescription: e.target.value})} />
          <select className="p-2 border mb-2" value={appForm.status} onChange={e=>setAppForm({...appForm, status: e.target.value})}>
            <option>APPLIED</option>
            <option>OA</option>
            <option>INTERVIEW</option>
            <option>OFFER</option>
            <option>REJECTED</option>
            <option>WITHDRAWN</option>
          </select>
          <div>
            <button className="bg-blue-600 text-white px-3 py-1 rounded mr-2">{editingAppId ? 'Save' : 'Create'}</button>
            {editingAppId && <button type="button" onClick={()=>{setEditingAppId(null); setAppForm({ companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED' })}} className="bg-gray-300 px-3 py-1 rounded">Cancel</button>}
          </div>
        </form>
      </section>
    </div>
  )
}
