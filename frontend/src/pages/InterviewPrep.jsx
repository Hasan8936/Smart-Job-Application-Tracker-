import React, { useEffect, useState } from 'react'
import { Download, GraduationCap, History, Link as LinkIcon, FileText } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import { generateInterviewPrep, listInterviewPrepSessions, getInterviewPrepSession, exportInterviewPrep } from '../api/interviewPrep'

const CATEGORY_LABELS = {
  BEHAVIORAL: 'Behavioral',
  TECHNICAL: 'Technical',
  ROLE_SPECIFIC: 'Role-specific',
  SITUATIONAL: 'Situational',
  COMPANY_AND_MOTIVATION: 'Company & motivation',
}
const CATEGORY_ORDER = ['BEHAVIORAL', 'TECHNICAL', 'ROLE_SPECIFIC', 'SITUATIONAL', 'COMPANY_AND_MOTIVATION']

export default function InterviewPrep() {
  const [resumes, setResumes] = useState([])
  const [resumeId, setResumeId] = useState('')
  const [applications, setApplications] = useState([])
  const [applicationId, setApplicationId] = useState('')
  const [source, setSource] = useState('TEXT')
  const [jobDescriptionText, setJobDescriptionText] = useState('')
  const [jobDescriptionUrl, setJobDescriptionUrl] = useState('')
  const [questionCount, setQuestionCount] = useState(50)
  const [session, setSession] = useState(null)
  const [pastSessions, setPastSessions] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { load() }, [])
  async function load() {
    try { const result = await api.get('/resume/me'); setResumes(result.data); if (result.data[0]) setResumeId(result.data[0].id) } catch (e) { console.error(e) }
    try { setApplications((await api.get('/applications')).data) } catch (e) { console.error(e) }
    try { setPastSessions((await listInterviewPrepSessions()).data) } catch (e) { console.error(e) }
  }

  async function generate(e) {
    e.preventDefault(); setError('')
    if (!resumeId) { setError('Select a resume first.'); return }
    if (source === 'TEXT' && !jobDescriptionText.trim()) { setError('Paste a job description.'); return }
    if (source === 'URL' && !jobDescriptionUrl.trim()) { setError('Enter a job posting URL.'); return }
    if (source === 'SAVED_APPLICATION' && !applicationId) { setError('Select a saved application.'); return }
    try {
      setBusy(true)
      const payload = { resumeId: Number(resumeId), source, questionCount: Number(questionCount) || 50 }
      if (source === 'TEXT') payload.jobDescriptionText = jobDescriptionText
      if (source === 'URL') payload.jobDescriptionUrl = jobDescriptionUrl
      if (source === 'SAVED_APPLICATION') payload.applicationId = Number(applicationId)
      const result = (await generateInterviewPrep(payload)).data
      setSession(result)
      setPastSessions((await listInterviewPrepSessions()).data)
    } catch (e) {
      setError(e.response?.data?.error || 'Could not generate interview questions.')
      console.error(e)
    } finally { setBusy(false) }
  }

  async function openPast(id) {
    try { setSession((await getInterviewPrepSession(id)).data) } catch (e) { console.error(e) }
  }

  async function exportSession() {
    if (!session) return
    try {
      const res = await exportInterviewPrep(session.id)
      const blob = new Blob([res.data], { type: 'text/markdown' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url; link.download = `interview-prep-${session.id}.md`; link.click()
      URL.revokeObjectURL(url)
    } catch (e) { setError('Could not export this session.'); console.error(e) }
  }

  const grouped = session ? CATEGORY_ORDER
    .map((category) => ({ category, items: session.questions.filter((q) => q.category === category) }))
    .filter((group) => group.items.length > 0) : []

  return <Layout title="Interview prep" subtitle="Real interview questions and model answers, grounded in your resume and the job description">
    <div className="grid lg:grid-cols-3 gap-6">
      <section className="bg-surface border border-line rounded-xl2 shadow-card p-5 h-fit space-y-4">
        <div>
          <h2 className="font-display text-[15px] text-ink mb-1">Generate questions</h2>
          <p className="text-xs text-muted">Answers are grounded in your resume where possible. Nothing is submitted anywhere automatically.</p>
        </div>
        <form onSubmit={generate} className="space-y-3">
          <select required className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm" value={resumeId} onChange={(e) => setResumeId(e.target.value)}>
            <option value="">Select a resume</option>
            {resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.fileName}</option>)}
          </select>

          <div className="flex gap-2 text-xs">
            {['TEXT', 'URL', 'SAVED_APPLICATION'].map((value) => (
              <button key={value} type="button" onClick={() => setSource(value)}
                className={`flex-1 px-2 py-2 rounded-lg border text-center font-medium ${source === value ? 'border-accent bg-accent-soft text-accent' : 'border-line text-muted'}`}>
                {value === 'TEXT' ? 'Paste JD' : value === 'URL' ? 'Job URL' : 'Saved job'}
              </button>
            ))}
          </div>

          {source === 'TEXT' && (
            <textarea required rows={8} className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm resize-none" placeholder="Paste the target job description" value={jobDescriptionText} onChange={(e) => setJobDescriptionText(e.target.value)} />
          )}
          {source === 'URL' && (
            <div className="relative">
              <LinkIcon size={14} className="absolute left-3 top-3 text-muted" />
              <input required type="url" className="w-full pl-8 px-3 py-2 rounded-lg border border-line bg-paper text-sm" placeholder="https://company.com/careers/job-posting" value={jobDescriptionUrl} onChange={(e) => setJobDescriptionUrl(e.target.value)} />
            </div>
          )}
          {source === 'SAVED_APPLICATION' && (
            <select required className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm" value={applicationId} onChange={(e) => setApplicationId(e.target.value)}>
              <option value="">Select a saved application</option>
              {applications.filter((a) => a.jobDescription).map((a) => <option key={a.id} value={a.id}>{a.roleTitle} · {a.companyName}</option>)}
            </select>
          )}

          <div>
            <label className="text-xs text-muted">Number of questions</label>
            <input type="number" min={10} max={80} className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm" value={questionCount} onChange={(e) => setQuestionCount(e.target.value)} />
          </div>

          {error && <p className="text-sm text-status-rejected">{error}</p>}
          <button disabled={busy} className="w-full inline-flex items-center justify-center gap-1.5 btn-gradient text-sm font-medium px-4 py-2.5 rounded-full disabled:opacity-50"><GraduationCap size={15} />{busy ? 'Generating...' : 'Generate interview prep'}</button>
        </form>

        {pastSessions.length > 0 && (
          <div className="pt-3 border-t border-line">
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted mb-2"><History size={13} />Past sessions</div>
            <div className="space-y-1.5 max-h-48 overflow-y-auto">
              {pastSessions.map((s) => (
                <button key={s.id} type="button" onClick={() => openPast(s.id)} className="w-full text-left text-xs px-2 py-1.5 rounded-lg hover:bg-mist text-ink-soft truncate">
                  {s.jobDescriptionPreview || `Session #${s.id}`} · {s.questionCount} Qs
                </button>
              ))}
            </div>
          </div>
        )}
      </section>

      <section className="lg:col-span-2 space-y-4">
        {!session ? (
          <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center text-sm text-muted">50 behavioral, technical, role-specific, situational, and motivation questions with model answers will appear here.</div>
        ) : <>
          <div className="bg-surface border border-line rounded-xl2 p-5 flex items-center justify-between gap-3">
            <div>
              <h2 className="font-display text-[15px] text-ink mb-1">Review your prep</h2>
              <p className="text-xs text-muted">{session.questions.length} questions · saved automatically · export anytime</p>
            </div>
            <button onClick={exportSession} className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full border border-line text-xs font-medium text-ink-soft hover:bg-mist"><Download size={14} />Export .md</button>
          </div>
          {grouped.map((group) => (
            <div key={group.category} className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">{CATEGORY_LABELS[group.category]}</h3>
              {group.items.map((item) => (
                <article key={item.id} className="bg-surface border border-line rounded-xl2 shadow-card p-5">
                  <p className="text-sm font-medium text-ink mb-2">{item.position + 1}. {item.question}</p>
                  <div className="border border-status-offer/30 bg-status-offerSoft/30 rounded-lg p-3">
                    <div className="text-xs text-muted mb-1">Model answer</div>
                    <p className="text-sm text-ink whitespace-pre-wrap">{item.suggestedAnswer}</p>
                  </div>
                  {item.sourceEvidence && <p className="text-xs text-muted mt-2">Resume evidence: {item.sourceEvidence}</p>}
                </article>
              ))}
            </div>
          ))}
          <div className="bg-surface border border-line rounded-xl2 p-5 flex items-start gap-2"><FileText size={16} className="text-muted mt-0.5" /><p className="text-xs text-muted">Model answers are a starting point -- practice them in your own words before the real interview.</p></div>
        </>}
      </section>
    </div>
  </Layout>
}
