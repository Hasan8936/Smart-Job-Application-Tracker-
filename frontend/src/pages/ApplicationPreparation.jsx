import React, { useEffect, useState } from 'react'
import { Check, ClipboardCheck, FileText, X } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import { prepareApplication, decideApplicationSuggestion } from '../api/applicationPreparation'

export default function ApplicationPreparation() {
  const [resumes, setResumes] = useState([])
  const [resumeId, setResumeId] = useState('')
  const [jobDescription, setJobDescription] = useState('')
  const [preparation, setPreparation] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { load() }, [])
  async function load() { try { const result = await api.get('/resume/me'); setResumes(result.data); if (result.data[0]) setResumeId(result.data[0].id) } catch (e) { console.error(e) } }

  async function prepare(e) {
    e.preventDefault(); setError('')
    if (!resumeId || !jobDescription.trim()) { setError('Select a resume and paste a job description.'); return }
    try {
      setBusy(true)
      setPreparation((await prepareApplication({ resumeId: Number(resumeId), jobDescription })).data)
    } catch (e) {
      setError(e.response?.data?.error || 'Could not prepare the application.')
      console.error(e)
    } finally { setBusy(false) }
  }

  async function decide(id, decision) {
    try {
      const updated = (await decideApplicationSuggestion(id, decision)).data
      setPreparation({ ...preparation, suggestions: preparation.suggestions.map(item => item.id === id ? updated : item) })
    } catch (e) { console.error(e) }
  }

  return <Layout title="Application preparation" subtitle="Answers pulled straight from your resume — nothing to fill in">
    <div className="grid lg:grid-cols-3 gap-6">
      <section className="bg-surface border border-line rounded-xl2 shadow-card p-5 h-fit">
        <h2 className="font-display text-[15px] text-ink mb-1">Prepare from your resume</h2>
        <p className="text-xs text-muted mb-3">Every answer is grounded in your resume's actual text. Nothing is invented, and no separate profile form is needed.</p>
        <form onSubmit={prepare} className="space-y-3">
          <select required className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm" value={resumeId} onChange={(e) => setResumeId(e.target.value)}>
            <option value="">Select a resume</option>
            {resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.fileName}</option>)}
          </select>
          <textarea required rows={10} className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm resize-none" placeholder="Paste the target job description" value={jobDescription} onChange={(e) => setJobDescription(e.target.value)} />
          {error && <p className="text-sm text-status-rejected">{error}</p>}
          <button disabled={busy} className="w-full inline-flex items-center justify-center gap-1.5 bg-accent text-accent-ink text-sm font-medium px-4 py-2.5 rounded-full hover:bg-accent-dark disabled:opacity-50"><ClipboardCheck size={15} />{busy ? 'Preparing...' : 'Prepare for review'}</button>
        </form>
      </section>
      <section className="lg:col-span-2 space-y-4">
        {!preparation ? (
          <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center text-sm text-muted">Grounded answers for name, contact info, education, experience, skills, and links will appear here. Nothing is submitted automatically.</div>
        ) : <>
          <div className="bg-surface border border-line rounded-xl2 p-5">
            <h2 className="font-display text-[15px] text-ink mb-2">Review answers</h2>
            <p className="text-xs text-muted">Every answer includes its source evidence from your resume. CAPTCHA, authentication, and submission stay with you.</p>
          </div>
          {preparation.suggestions.map((item) => (
            <article key={item.id} className="bg-surface border border-line rounded-xl2 shadow-card p-5">
              <div className="flex justify-between gap-3 mb-2"><span className="text-xs font-medium text-muted">{item.externalField} · {item.fieldType}</span><span className="text-xs text-muted">{item.decision}</span></div>
              <div className="grid md:grid-cols-2 gap-3">
                <div className="border border-line rounded-lg p-3"><div className="text-xs text-muted mb-1">Suggested answer</div><p className="text-sm text-ink whitespace-pre-wrap">{item.suggestedValue}</p></div>
                <div className="border border-status-offer/30 bg-status-offerSoft/30 rounded-lg p-3"><div className="text-xs text-muted mb-1">Source evidence</div><p className="text-sm text-ink whitespace-pre-wrap">{item.sourceEvidence}</p></div>
              </div>
              <p className="text-xs text-muted mt-3">{item.rationale}</p>
              <div className="flex gap-2 mt-3">
                <button onClick={() => decide(item.id, 'ACCEPTED')} className="inline-flex items-center gap-1 px-3 py-2 rounded-full bg-status-offerSoft text-status-offer text-xs font-medium"><Check size={14} />Accept</button>
                <button onClick={() => decide(item.id, 'REJECTED')} className="inline-flex items-center gap-1 px-3 py-2 rounded-full bg-status-rejectedSoft text-status-rejected text-xs font-medium"><X size={14} />Reject</button>
              </div>
            </article>
          ))}
          {preparation.suggestions.length === 0 && <div className="bg-surface border border-line rounded-xl2 p-5 text-sm text-muted">No verified answer could be grounded from your resume for these fields. Nothing was invented — add the missing details to your resume itself and try again.</div>}
        </>}
        <div className="bg-surface border border-line rounded-xl2 p-5 flex items-start gap-2"><FileText size={16} className="text-muted mt-0.5" /><p className="text-xs text-muted">This is a preparation and review workspace only. There is no automatic browser submission.</p></div>
      </section>
    </div>
  </Layout>
}
