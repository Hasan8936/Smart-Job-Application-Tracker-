import React, { useEffect, useState } from 'react'
import { Check, FileText, History, Sparkles, X } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import { analyzeTailoring, decideSuggestion, createResumeVersion, getResumeVersions } from '../api/tailoring'

export default function ResumeTailoring() {
  const [resumes, setResumes] = useState([])
  const [resumeId, setResumeId] = useState('')
  const [jobDescription, setJobDescription] = useState('')
  const [analysis, setAnalysis] = useState(null)
  const [versions, setVersions] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { loadResumes(); loadVersions() }, [])
  async function loadResumes() { try { const result = await api.get('/resume/me'); setResumes(result.data); if (result.data[0]) setResumeId(result.data[0].id) } catch (e) { console.error(e) } }
  async function loadVersions() { try { setVersions((await getResumeVersions()).data) } catch (e) { console.error(e) } }
  async function analyze(e) { e.preventDefault(); setError(''); if (!resumeId || !jobDescription.trim()) { setError('Select an original resume and paste a job description.'); return } try { setBusy(true); const saved = JSON.parse(localStorage.getItem('deepMatchAnalysis') || 'null'); const deepMatchAnalysisId = saved && Number(saved.resumeId) === Number(resumeId) && saved.jobDescription === jobDescription ? saved.id : null; setAnalysis((await analyzeTailoring({ resumeId: Number(resumeId), jobDescription, deepMatchAnalysisId })).data) } catch (e) { setError('Could not analyze this resume.'); console.error(e) } finally { setBusy(false) } }
  async function decide(id, decision) { try { const updated = (await decideSuggestion(id, decision)).data; setAnalysis({ ...analysis, suggestions: analysis.suggestions.map(item => item.id === id ? updated : item) }) } catch (e) { console.error(e) } }
  async function createVersion() { try { setBusy(true); await createResumeVersion(analysis.sessionId); await loadVersions() } catch (e) { setError('Could not create the new resume version.'); console.error(e) } finally { setBusy(false) } }

  return <Layout title="Resume tailoring" subtitle="Review grounded suggestions before creating a new version">
    <div className="grid lg:grid-cols-3 gap-6">
      <section className="bg-surface border border-line rounded-xl2 shadow-card p-5 h-fit">
        <div className="flex items-center gap-2 mb-3"><Sparkles size={16} /><h2 className="font-display text-[15px] text-ink">Start with an original</h2></div>
        <form onSubmit={analyze} className="space-y-3">
          <label className="block text-xs font-medium text-muted">Original resume<select required className="mt-1 w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm text-ink" value={resumeId} onChange={(e) => setResumeId(e.target.value)}><option value="">Select a resume</option>{resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.fileName}</option>)}</select></label>
          <label className="block text-xs font-medium text-muted">Job description<textarea required rows={12} className="mt-1 w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm text-ink resize-none" value={jobDescription} onChange={(e) => setJobDescription(e.target.value)} placeholder="Paste the target job description" /></label>
          {error && <p className="text-sm text-status-rejected">{error}</p>}
          <button disabled={busy} className="w-full inline-flex items-center justify-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2.5 rounded-lg disabled:opacity-50"><Sparkles size={15} />{busy ? 'Analyzing...' : 'Generate suggestions'}</button>
        </form>
        <p className="mt-3 text-xs text-muted">Your original resume is never edited. A new version is created only from suggestions you accept.</p>
      </section>

      <section className="lg:col-span-2 space-y-6">
        {!analysis ? <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center text-sm text-muted">Your grounded suggestions will appear here for review.</div> : <>
          <div className="bg-surface border border-line rounded-xl2 shadow-card p-5"><div className="grid md:grid-cols-3 gap-4"><div><h2 className="text-xs font-medium text-muted mb-2">ATS keywords</h2><div className="flex flex-wrap gap-1.5">{analysis.atsKeywords.map((item) => <span key={item} className="px-2 py-1 rounded-md bg-status-interviewSoft text-status-interview text-xs font-medium">{item}</span>)}</div></div><div><h2 className="text-xs font-medium text-muted mb-2">Existing skills</h2><p className="text-sm text-ink">{analysis.highlightedSkills.join(', ') || 'None found'}</p></div><div><h2 className="text-xs font-medium text-muted mb-2">Existing projects</h2><p className="text-sm text-ink">{analysis.highlightedProjects.join(' | ') || 'None found'}</p></div></div></div>
          <div className="space-y-3">{analysis.suggestions.length === 0 ? <div className="bg-surface border border-line rounded-xl2 p-5 text-sm text-muted">No grounded edits were found. Review the ATS keywords and update the source resume manually if a missing skill is genuinely present.</div> : analysis.suggestions.map((item) => <article key={item.id} className="bg-surface border border-line rounded-xl2 shadow-card p-5"><div className="flex items-center justify-between gap-3 mb-3"><span className="text-xs font-medium text-muted">{item.category}</span><span className="text-xs font-medium text-muted">{item.decision}</span></div><div className="grid md:grid-cols-2 gap-3"><div className="border border-line rounded-lg p-3"><div className="text-xs text-muted mb-1">Before</div><p className="text-sm text-ink whitespace-pre-wrap">{item.beforeText}</p></div><div className="border border-status-offer/30 bg-status-offerSoft/30 rounded-lg p-3"><div className="text-xs text-muted mb-1">After</div><p className="text-sm text-ink whitespace-pre-wrap">{item.afterText}</p></div></div><p className="mt-3 text-xs text-muted">{item.rationale} Evidence: “{item.evidenceText}”</p><div className="mt-3 flex gap-2"><button onClick={() => decide(item.id, 'ACCEPTED')} disabled={item.decision === 'ACCEPTED'} className="inline-flex items-center gap-1 px-3 py-2 rounded-lg bg-status-offerSoft text-status-offer text-xs font-medium disabled:opacity-50"><Check size={14} />Accept</button><button onClick={() => decide(item.id, 'REJECTED')} disabled={item.decision === 'REJECTED'} className="inline-flex items-center gap-1 px-3 py-2 rounded-lg bg-status-rejectedSoft text-status-rejected text-xs font-medium disabled:opacity-50"><X size={14} />Reject</button></div></article>)}</div>
          <button onClick={createVersion} disabled={busy || !analysis.suggestions.some(item => item.decision === 'ACCEPTED')} className="inline-flex items-center gap-1.5 bg-accent text-accent-ink text-sm font-semibold px-4 py-2.5 rounded-lg disabled:opacity-50"><FileText size={15} />Create new resume version</button>
        </>}
        <div className="bg-surface border border-line rounded-xl2 shadow-card p-5"><div className="flex items-center gap-2 mb-3"><History size={16} /><h2 className="font-display text-[15px] text-ink">Version history</h2></div>{versions.length === 0 ? <p className="text-sm text-muted">No tailored versions yet.</p> : <div className="space-y-2">{versions.map((version) => <div key={version.id} className="border-t border-line pt-2 text-sm"><div className="flex justify-between gap-3"><span className="text-ink">Version #{version.id}</span><span className="text-xs text-muted">{new Date(version.createdAt).toLocaleString()}</span></div><p className="text-xs text-muted mt-1">Based on original resume #{version.sourceResumeId}; accepted edits: {version.acceptedSuggestionIds.length}</p><button type="button" onClick={() => { const blob = new Blob([version.latexContent], { type: 'text/plain;charset=utf-8' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `resume-version-${version.id}.tex`; link.click(); URL.revokeObjectURL(url) }} className="mt-2 text-xs font-medium text-ink underline">Download Overleaf .tex</button></div>)}</div>}</div>
      </section>
    </div>
  </Layout>
}