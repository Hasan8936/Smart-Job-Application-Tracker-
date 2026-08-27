import React, { useEffect, useState } from 'react'
import { UploadCloud, FileText, CheckCircle2, XCircle } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import ScoreRing from '../components/ScoreRing'
import DeepMatchResults from '../components/DeepMatchResults'

export default function ResumeMatch() {
  const [resumes, setResumes] = useState([])
  const [file, setFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [jd, setJd] = useState('')
  const [selectedResumeId, setSelectedResumeId] = useState(null)
  const [matchResult, setMatchResult] = useState(null)
  const [deepMatchResult, setDeepMatchResult] = useState(null)
  const [matching, setMatching] = useState(false)
  const [deepMatching, setDeepMatching] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { fetchResumes() }, [])

  async function fetchResumes() {
    try {
      const res = await api.get('/resume/me')
      setResumes(res.data)
      if (!selectedResumeId && res.data[0]) setSelectedResumeId(res.data[0].id)
    } catch (e) {
      console.error(e)
    }
  }

  async function upload(e) {
    e.preventDefault()
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    try {
      setUploading(true)
      await api.post('/resume/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      setFile(null)
      await fetchResumes()
    } catch (e) {
      console.error(e)
    } finally {
      setUploading(false)
    }
  }

  async function doMatch() {
    setError('')
    const rid = selectedResumeId || resumes[0]?.id
    if (!rid) { setError('Upload or select a resume first.'); return }
    if (!jd.trim()) { setError('Paste a job description to match against.'); return }
    try {
      setMatching(true)
      const res = await api.post('/match/hybrid-score', { resumeId: rid, jobDescriptionText: jd })
      setMatchResult(res.data)
    } catch (e) {
      console.error(e)
      setError('Could not compute a match score. Try again in a moment.')
    } finally {
      setMatching(false)
    }
  }

  async function doDeepMatch() {
    setError('')
    const resume = resumes.find((item) => item.id === selectedResumeId) || resumes[0]
    if (!resume?.extractedText?.trim()) { setError('This resume has no extractable text. Upload a text-based PDF or DOCX.'); return }
    if (!jd.trim()) { setError('Paste a job description to match against.'); return }
    try {
      setDeepMatching(true)
      const res = await api.post('/resume/deep-match', { resumeId: resume.id, jobDescription: jd })
      setDeepMatchResult(res.data)
      localStorage.setItem('deepMatchAnalysis', JSON.stringify({ id: res.data.analysisId, resumeId: resume.id, jobDescription: jd }))
    } catch (e) {
      console.error(e)
      setError('Could not complete the deep analysis. Check the server configuration and try again.')
    } finally {
      setDeepMatching(false)
    }
  }

  return (
    <Layout title="Resume matcher" subtitle="See how well a resume matches a job description">
      <div className="grid lg:grid-cols-2 gap-6">
        <div className="space-y-6">
          <section className="bg-surface border border-line rounded-xl2 shadow-card p-5">
            <h2 className="font-display text-[15px] text-ink mb-3">Your resumes</h2>
            <form onSubmit={upload} className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 mb-4">
              <label className="flex-1 flex items-center gap-2 px-3 py-2.5 rounded-lg border border-dashed border-line text-sm text-muted cursor-pointer hover:border-ink/30 min-w-0">
                <UploadCloud size={16} className="shrink-0" />
                <span className="truncate">{file ? file.name : 'Choose a resume file'}</span>
                <input type="file" className="hidden" onChange={(e) => setFile(e.target.files[0])} />
              </label>
              <button
                disabled={!file || uploading}
                className="px-3.5 py-2.5 rounded-lg bg-ink text-white text-sm font-medium disabled:opacity-40 shrink-0"
              >
                {uploading ? 'Uploading…' : 'Upload'}
              </button>
            </form>

            {resumes.length === 0 ? (
              <p className="text-sm text-muted">No resumes uploaded yet.</p>
            ) : (
              <div className="space-y-2">
                {resumes.map((r) => (
                  <label
                    key={r.id}
                    className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border cursor-pointer text-sm ${
                      selectedResumeId === r.id ? 'border-ink bg-paper' : 'border-line'
                    }`}
                  >
                    <input
                      type="radio"
                      name="resume"
                      className="accent-ink"
                      checked={selectedResumeId === r.id}
                      onChange={() => setSelectedResumeId(r.id)}
                    />
                    <FileText size={15} className="text-muted" />
                    <span className="flex-1 truncate">{r.fileName}</span>
                    <span className="text-xs text-muted font-mono">{new Date(r.uploadedAt).toLocaleDateString()}</span>
                  </label>
                ))}
              </div>
            )}
          </section>

          <section className="bg-surface border border-line rounded-xl2 shadow-card p-5">
            <h2 className="font-display text-[15px] text-ink mb-3">Job description</h2>
            <textarea
              rows={8}
              value={jd}
              onChange={(e) => setJd(e.target.value)}
              placeholder="Paste the job description here"
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-paper focus:bg-surface text-sm resize-none mb-3"
            />
            {error && <p className="text-sm text-status-rejected mb-3">{error}</p>}
            <button
              onClick={doMatch}
              disabled={matching}
              className="w-full px-4 py-2.5 rounded-lg bg-accent text-accent-ink text-sm font-semibold hover:bg-accent-dark hover:text-white disabled:opacity-50"
            >
              {matching ? 'Scoring…' : 'Compute match score'}
            </button>
            <button
              onClick={doDeepMatch}
              disabled={deepMatching}
              className="mt-2 w-full rounded-lg border border-line px-4 py-2.5 text-sm font-semibold text-ink hover:bg-paper disabled:opacity-50"
            >
              {deepMatching ? 'Analyzing…' : 'Run deep Claude analysis'}
            </button>
          </section>
        </div>

        <section className="bg-surface border border-line rounded-xl2 shadow-card p-5">
          <h2 className="font-display text-[15px] text-ink mb-4">Result</h2>
          {deepMatchResult ? (
            <DeepMatchResults result={deepMatchResult} />
          ) : !matchResult ? (
            <div className="h-full flex flex-col items-center justify-center text-center py-10">
              <p className="text-sm text-muted max-w-xs">
                Select a resume and paste a job description to see your match score and keyword gaps.
              </p>
            </div>
          ) : (
            <div>
              <div className="flex justify-center mb-6">
                <ScoreRing value={matchResult.matchScore} />
              </div>

              <div className="grid sm:grid-cols-2 gap-4 mb-5">
                <div className="sm:col-span-2 grid grid-cols-2 md:grid-cols-4 gap-2">
                  {[
                    ['Skill', matchResult.skillMatch], ['Semantic', matchResult.semanticSimilarity],
                    ['Experience', matchResult.experienceMatch], ['Role', matchResult.roleMatch],
                  ].map(([label, value]) => (
                    <div key={label} className="rounded-lg border border-line bg-paper px-3 py-2">
                      <div className="text-xs text-muted">{label}</div>
                      <div className="font-mono text-lg text-ink">{Math.round(value)}%</div>
                    </div>
                  ))}
                </div>
                <div>
                  <div className="flex items-center gap-1.5 text-xs font-medium text-status-offer mb-2">
                    <CheckCircle2 size={14} /> Strong matches
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {matchResult.strongMatches?.length ? matchResult.strongMatches.map((k) => (
                      <span key={k} className="px-2 py-1 rounded-md bg-status-offerSoft text-status-offer text-xs font-medium">{k}</span>
                    )) : <span className="text-xs text-muted">None found</span>}
                  </div>
                </div>
                <div>
                  <div className="flex items-center gap-1.5 text-xs font-medium text-status-rejected mb-2">
                    <XCircle size={14} /> Missing required skills
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {matchResult.missingRequiredSkills?.length ? matchResult.missingRequiredSkills.map((k) => (
                      <span key={k} className="px-2 py-1 rounded-md bg-status-rejectedSoft text-status-rejected text-xs font-medium">{k}</span>
                    )) : <span className="text-xs text-muted">None — great coverage</span>}
                  </div>
                </div>
                <div>
                  <div className="text-xs font-medium text-muted mb-2">Missing preferred skills</div>
                  <div className="flex flex-wrap gap-1.5">
                    {matchResult.missingPreferredSkills?.length ? matchResult.missingPreferredSkills.map((k) => (
                      <span key={k} className="px-2 py-1 rounded-md bg-status-rejectedSoft text-status-rejected text-xs font-medium">{k}</span>
                    )) : <span className="text-xs text-muted">None found</span>}
                  </div>
                </div>
                <div className="sm:col-span-2">
                  <div className="text-xs font-medium text-muted mb-2">Recommendations</div>
                  <ul className="space-y-1 text-sm text-ink list-disc pl-5">
                    {matchResult.recommendations?.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                  <div className="text-xs text-muted mt-3">Semantic source: {matchResult.semanticProvider}</div>
                </div>
              </div>
            </div>
          )}
        </section>
      </div>
    </Layout>
  )
}
