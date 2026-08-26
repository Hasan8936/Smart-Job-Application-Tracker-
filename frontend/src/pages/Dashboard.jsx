import React, { useContext, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, Briefcase, MessagesSquare, PartyPopper, XCircle, ArrowRight, Bookmark, Sparkles, Search, CheckCircle2 } from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import StatCard from '../components/StatCard'
import PipelineBar from '../components/PipelineBar'
import ApplicationCard from '../components/ApplicationCard'
import ApplicationDrawer from '../components/ApplicationDrawer'
import { AuthContext } from '../context/AuthContext'
import { getJob, listJobs, readJobActions } from '../api/jobs'
import JobCard from '../components/JobCard'
import JobDetails from '../components/JobDetails'

const emptyForm = { companyName: '', roleTitle: '', jobDescription: '', status: 'APPLIED', appliedDate: '' }

export default function Dashboard() {
  const { user } = useContext(AuthContext)
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [jobs, setJobs] = useState([])
  const [jobLoading, setJobLoading] = useState(true)
  const [jobError, setJobError] = useState('')
  const [jobActions, setJobActions] = useState(readJobActions())
  const [selectedJob, setSelectedJob] = useState(null)

  useEffect(() => { fetchApps(); fetchJobs() }, [])

  async function fetchApps() {
    try {
      setLoading(true)
      const res = await api.get('/applications')
      setApplications(res.data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  async function fetchJobs() {
    try {
      setJobLoading(true)
      const res = await listJobs({ page: 0, size: 6, sort: 'postedAt,desc' })
      const resumes = await api.get('/resume/me')
      const resumeId = resumes.data?.[0]?.id
      const scored = resumeId ? await Promise.all((res.content || []).map(async (job) => {
        try {
          const detail = await getJob(job.id)
          const match = await api.post('/match/hybrid-score', { resumeId, jobId: job.id })
          return { ...job, ...detail, matchScore: match.data.overallMatch }
        } catch { return job }
      })) : (res.content || [])
      setJobs(scored.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0)))
    } catch (e) {
      console.error(e)
      setJobError('Job recommendations are unavailable right now.')
    } finally { setJobLoading(false) }
  }

  function updateJobAction(id, action) {
    setJobActions((current) => {
      const next = { ...current }
      if (action) next[id] = action
      else delete next[id]
      localStorage.setItem('smart-job-tracker-job-actions', JSON.stringify(next))
      return next
    })
  }

  async function submitApp(e) {
    e.preventDefault()
    try {
      await api.post('/applications', form)
      setForm(emptyForm)
      setDrawerOpen(false)
      fetchApps()
    } catch (e) {
      console.error(e)
    }
  }

  const total = applications.length
  const interviews = applications.filter((a) => a.status === 'INTERVIEW').length
  const offers = applications.filter((a) => a.status === 'OFFER').length
  const rejections = applications.filter((a) => a.status === 'REJECTED').length
  const firstName = (user?.profile?.name || '').split(' ')[0]
  const savedJobs = Object.values(jobActions).filter((value) => value === 'saved' || value === 'bookmarked').length
  const appliedJobs = Object.values(jobActions).filter((value) => value === 'applied').length
  const scoredJobs = jobs.filter((job) => job.matchScore != null)
  const averageMatch = scoredJobs.length ? Math.round(scoredJobs.reduce((sum, job) => sum + job.matchScore, 0) / scoredJobs.length) : null

  return (
    <Layout
      title={firstName ? `Welcome back, ${firstName}` : 'Dashboard'}
      subtitle="Here's where your search stands today"
      actions={
        <button
          onClick={() => { setForm(emptyForm); setDrawerOpen(true) }}
          className="inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-3.5 py-2 rounded-lg hover:bg-ink-soft"
        >
          <Plus size={15} /> Add application
        </button>
      }
    >
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="Total applications" value={total} icon={Briefcase} accent />
        <StatCard label="Interviews" value={interviews} icon={MessagesSquare} />
        <StatCard label="Offers" value={offers} icon={PartyPopper} />
        <StatCard label="Rejections" value={rejections} icon={XCircle} />
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
        <StatCard label="Total jobs" value={jobs.length || '—'} icon={Search} />
        <StatCard label="Recommended jobs" value={jobs.filter((job) => (job.matchScore || 0) >= 70).length || '—'} icon={Sparkles} />
        <StatCard label="Average match" value={averageMatch == null ? '—' : `${averageMatch}%`} icon={Sparkles} />
        <StatCard label="Saved jobs" value={savedJobs || '—'} icon={Bookmark} />
        <StatCard label="Applied jobs" value={appliedJobs || '—'} icon={CheckCircle2} />
      </div>

      <div className="mb-6">
        <PipelineBar applications={applications} />
      </div>

      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-lg text-ink">Recent applications</h2>
        <Link to="/applications" className="text-sm font-medium text-ink inline-flex items-center gap-1 hover:text-accent-dark">
          View all <ArrowRight size={14} />
        </Link>
      </div>

      <div className="flex items-center justify-between mb-3 mt-8">
        <h2 className="font-display text-lg text-ink">Recommended jobs</h2>
        <Link to="/discovery" className="text-sm font-medium text-ink inline-flex items-center gap-1 hover:text-accent-dark">Explore all <ArrowRight size={14} /></Link>
      </div>
      {jobLoading ? <div className="h-32 rounded-xl2 bg-surface border border-line animate-pulse" /> : jobError ? <div className="text-sm text-status-rejected">{jobError}</div> : jobs.length === 0 ? <div className="bg-surface border border-dashed border-line rounded-xl2 p-8 text-center"><p className="font-display text-ink">No jobs discovered yet</p><Link to="/discovery" className="text-sm text-muted hover:text-ink">Open job discovery</Link></div> : <div><p className="text-xs text-muted mb-3">Top match: {jobs[0]?.title || 'Unavailable'} {jobs[0]?.matchScore != null ? `· ${Math.round(jobs[0].matchScore)}%` : ''}</p><div className="space-y-3">{jobs.slice(0, 3).map((job) => <JobCard key={job.id} job={job} action={jobActions[job.id]} onAction={updateJobAction} onOpen={(id) => setSelectedJob(jobs.find((item) => item.id === id))} />)}</div></div>}

      {loading ? (
        <div className="text-sm text-muted">Loading your applications…</div>
      ) : applications.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center">
          <p className="font-display text-ink text-lg mb-1">No applications yet</p>
          <p className="text-sm text-muted mb-4">Add the first role you've applied to — it takes a few seconds.</p>
          <button
            onClick={() => setDrawerOpen(true)}
            className="inline-flex items-center gap-1.5 bg-ink text-white text-sm font-medium px-4 py-2 rounded-lg"
          >
            <Plus size={15} /> Add application
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {applications.slice(0, 5).map((a) => (
            <ApplicationCard key={a.id} app={a} />
          ))}
        </div>
      )}

      <ApplicationDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        form={form}
        setForm={setForm}
        onSubmit={submitApp}
        isEditing={false}
      />
      <JobDetails job={selectedJob} onClose={() => setSelectedJob(null)} />
    </Layout>
  )
}
