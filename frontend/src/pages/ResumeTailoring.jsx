import React, { useEffect, useRef, useState } from 'react'
import { Check, Download, ExternalLink, FileText, History, Sparkles, X } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import { analyzeTailoring, decideSuggestion, createResumeVersion, getResumeVersions, getResumeVersionPdf } from '../api/tailoring'

export default function ResumeTailoring() {
  const [resumes, setResumes] = useState([])
  const [resumeId, setResumeId] = useState('')
  const [jobDescription, setJobDescription] = useState('')
  const [analysis, setAnalysis] = useState(null)
  const [versions, setVersions] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [previewUrl, setPreviewUrl] = useState(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [activeVersionId, setActiveVersionId] = useState(null)
  const previewUrlRef = useRef(null)

  useEffect(() => {
    loadResumes()
    loadVersions()
    return () => { if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current) }
  }, [])

  async function loadResumes() {
    try {
      const result = await api.get('/resume/me')
      setResumes(result.data)
      if (result.data[0]) setResumeId(result.data[0].id)
    } catch (e) { console.error(e) }
  }

  async function loadVersions() {
    try {
      const result = await getResumeVersions()
      const list = result.data
      setVersions(list)
      if (list.length > 0) await showPreview(list[0].id)
    } catch (e) { console.error(e) }
  }

  async function showPreview(versionId) {
    try {
      setPreviewLoading(true)
      setActiveVersionId(versionId)
      const res = await getResumeVersionPdf(versionId)
      const blob = new Blob([res.data], { type: 'application/pdf' })
      const url = URL.createObjectURL(blob)
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
      previewUrlRef.current = url
      setPreviewUrl(url)
    } catch (e) { console.error(e) } finally { setPreviewLoading(false) }
  }

  async function analyze(e) {
    e.preventDefault()
    setError('')
    if (!resumeId || !jobDescription.trim()) { setError('Select an original resume and paste a job description.'); return }
    try {
      setBusy(true)
      const saved = JSON.parse(localStorage.getItem('deepMatchAnalysis') || 'null')
      const deepMatchAnalysisId = saved && Number(saved.resumeId) === Number(resumeId) && saved.jobDescription === jobDescription ? saved.id : null
      setAnalysis((await analyzeTailoring({ resumeId: Number(resumeId), jobDescription, deepMatchAnalysisId })).data)
    } catch (e) { setError('Could not analyze this resume.'); console.error(e) } finally { setBusy(false) }
  }

  async function decide(id, decision) {
    try {
      const updated = (await decideSuggestion(id, decision)).data
      setAnalysis({ ...analysis, suggestions: analysis.suggestions.map(item => item.id === id ? updated : item) })
    } catch (e) { console.error(e) }
  }

  async function createVersion() {
    try {
      setBusy(true)
      await createResumeVersion(analysis.sessionId)
      await loadVersions()
    } catch (e) { setError('Could not create the new resume version.'); console.error(e) } finally { setBusy(false) }
  }

  async function downloadPdf(versionId) {
    try {
      const res = await getResumeVersionPdf(versionId)
      const blob = new Blob([res.data], { type: 'application/pdf' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url; link.download = `resume-version-${versionId}.pdf`; link.click()
      URL.revokeObjectURL(url)
    } catch (e) { setError('Could not generate the PDF.'); console.error(e) }
  }

  function openInOverleaf(latexContent) {
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = 'https://www.overleaf.com/docs'
    form.target = '_blank'
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = 'snip'
    input.value = latexContent
    form.appendChild(input)
    document.body.appendChild(form)
    form.submit()
    document.body.removeChild(form)
  }

  const activeVersion = versions.find(v => v.id === activeVersionId)

  return (
    <Layout title="Resume tailoring" subtitle="Review grounded suggestions before creating a new version">
      <div className="grid lg:grid-cols-3 gap-6">

        {/* ── Left: form ── */}
        <section className="bg-surface border border-line rounded-xl2 shadow-card p-5 h-fit">
          <div className="flex items-center gap-2 mb-3">
            <Sparkles size={16} />
            <h2 className="font-display text-[15px] text-ink">Start with an original</h2>
          </div>
          <form onSubmit={analyze} className="space-y-3">
            <label className="block text-xs font-medium text-muted">
              Original resume
              <select required className="mt-1 w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm text-ink" value={resumeId} onChange={(e) => setResumeId(e.target.value)}>
                <option value="">Select a resume</option>
                {resumes.map((r) => <option key={r.id} value={r.id}>{r.fileName}</option>)}
              </select>
            </label>
            <label className="block text-xs font-medium text-muted">
              Job description
              <textarea required rows={12} className="mt-1 w-full px-3 py-2.5 rounded-lg border border-line bg-paper text-sm text-ink resize-none" value={jobDescription} onChange={(e) => setJobDescription(e.target.value)} placeholder="Paste the target job description" />
            </label>
            {error && <p className="text-sm text-status-rejected">{error}</p>}
            <button disabled={busy} className="w-full inline-flex items-center justify-center gap-1.5 btn-gradient text-sm font-medium px-4 py-2.5 rounded-full disabled:opacity-50">
              <Sparkles size={15} />{busy ? 'Analyzing...' : 'Generate suggestions'}
            </button>
          </form>
          <p className="mt-3 text-xs text-muted">Your original resume is never edited. A new version is created only from suggestions you accept.</p>
        </section>

        {/* ── Right: PDF preview + suggestions + version history ── */}
        <section className="lg:col-span-2 space-y-6">

          {/* PDF preview */}
          <div className="bg-surface border border-line rounded-xl2 shadow-card p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <FileText size={16} />
                <h2 className="font-display text-[15px] text-ink">Resume preview</h2>
              </div>
              {activeVersion && (
                <div className="flex items-center gap-2">
                  <button onClick={() => downloadPdf(activeVersionId)} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-line text-xs font-medium text-ink hover:bg-paper transition-colors">
                    <Download size={13} /> Download PDF
                  </button>
                  <button onClick={() => openInOverleaf(activeVersion.latexContent)} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full btn-gradient text-xs font-medium">
                    <ExternalLink size={13} /> Open in Overleaf
                  </button>
                </div>
              )}
            </div>
            {previewLoading ? (
              <div className="h-[620px] rounded-lg bg-paper border border-line flex items-center justify-center text-sm text-muted animate-pulse">Loading preview…</div>
            ) : previewUrl ? (
              <iframe src={previewUrl} className="w-full h-[620px] rounded-lg border border-line" title="Resume preview" />
            ) : (
              <div className="h-[620px] rounded-lg bg-paper border border-dashed border-line flex flex-col items-center justify-center gap-2 text-sm text-muted">
                <FileText size={32} className="opacity-25" />
                Create a tailored version to see the PDF preview here.
              </div>
            )}
          </div>

          {/* Suggestions */}
          {analysis && (
            <>
              <div className="space-y-3">
                {analysis.suggestions.length === 0
                  ? <div className="bg-surface border border-line rounded-xl2 p-5 text-sm text-muted">No grounded edits were found. Review the ATS keywords and update the source resume manually if a missing skill is genuinely present.</div>
                  : analysis.suggestions.map((item) => (
                    <article key={item.id} className="bg-surface border border-line rounded-xl2 shadow-card p-5">
                      <div className="flex items-center justify-between gap-3 mb-3">
                        <span className="text-xs font-medium text-muted">{item.category}</span>
                        <span className="text-xs font-medium text-muted">{item.decision}</span>
                      </div>
                      <div className="grid md:grid-cols-2 gap-3">
                        <div className="border border-line rounded-lg p-3">
                          <div className="text-xs text-muted mb-1">Before</div>
                          <p className="text-sm text-ink whitespace-pre-wrap">{item.beforeText}</p>
                        </div>
                        <div className="border border-status-offer/30 bg-status-offerSoft/30 rounded-lg p-3">
                          <div className="text-xs text-muted mb-1">After</div>
                          <p className="text-sm text-ink whitespace-pre-wrap">{item.afterText}</p>
                        </div>
                      </div>
                      <p className="mt-3 text-xs text-muted">{item.rationale} Evidence: &ldquo;{item.evidenceText}&rdquo;</p>
                      <div className="mt-3 flex gap-2">
                        <button onClick={() => decide(item.id, 'ACCEPTED')} disabled={item.decision === 'ACCEPTED'} className="inline-flex items-center gap-1 px-3 py-2 rounded-full bg-status-offerSoft text-status-offer text-xs font-medium disabled:opacity-50">
                          <Check size={14} />Accept
                        </button>
                        <button onClick={() => decide(item.id, 'REJECTED')} disabled={item.decision === 'REJECTED'} className="inline-flex items-center gap-1 px-3 py-2 rounded-full bg-status-rejectedSoft text-status-rejected text-xs font-medium disabled:opacity-50">
                          <X size={14} />Reject
                        </button>
                      </div>
                    </article>
                  ))
                }
              </div>
              <button onClick={createVersion} disabled={busy || !analysis.suggestions.some(item => item.decision === 'ACCEPTED')} className="inline-flex items-center gap-1.5 btn-gradient text-sm font-semibold px-4 py-2.5 rounded-full disabled:opacity-50">
                <FileText size={15} />Create new resume version
              </button>
            </>
          )}

          {/* Version history */}
          <div className="bg-surface border border-line rounded-xl2 shadow-card p-5">
            <div className="flex items-center gap-2 mb-3">
              <History size={16} />
              <h2 className="font-display text-[15px] text-ink">Version history</h2>
            </div>
            {versions.length === 0
              ? <p className="text-sm text-muted">No tailored versions yet.</p>
              : (
                <div className="space-y-1">
                  {versions.map((version) => (
                    <div
                      key={version.id}
                      onClick={() => showPreview(version.id)}
                      className={`border-t border-line pt-3 pb-2 px-2 -mx-2 rounded cursor-pointer transition-colors ${version.id === activeVersionId ? 'bg-paper' : 'hover:bg-paper/60'}`}
                    >
                      <div className="flex justify-between gap-3">
                        <span className="text-sm font-medium text-ink">Version #{version.id}</span>
                        <span className="text-xs text-muted">{new Date(version.createdAt).toLocaleString()}</span>
                      </div>
                      <p className="text-xs text-muted mt-0.5">Based on original resume #{version.sourceResumeId}; accepted edits: {version.acceptedSuggestionIds.length}</p>
                      <div className="mt-2 flex items-center gap-3">
                        <button type="button" onClick={(e) => { e.stopPropagation(); downloadPdf(version.id) }} className="text-xs font-medium text-ink underline hover:no-underline">
                          Download PDF
                        </button>
                        <button type="button" onClick={(e) => { e.stopPropagation(); openInOverleaf(version.latexContent) }} className="inline-flex items-center gap-1 text-xs font-medium text-muted underline hover:no-underline">
                          <ExternalLink size={11} />Open in Overleaf
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )
            }
          </div>

        </section>
      </div>
    </Layout>
  )
}
