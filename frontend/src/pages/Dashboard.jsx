import React, { useContext, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Plus, Briefcase, MessagesSquare, PartyPopper, XCircle,
  ArrowRight, Bookmark, Sparkles, Search, CheckCircle2,
  Mail, CalendarDays, Loader2, Link2, Unlink
} from 'lucide-react'
import api from '../api/axios'
import Layout from '../components/Layout'
import StatCard from '../components/StatCard'
import PipelineBar from '../components/PipelineBar'
import ApplicationCard from '../components/ApplicationCard'
import ApplicationDrawer from '../components/ApplicationDrawer'
import ScoreRing from '../components/ScoreRing'
import TopCompanies from '../components/TopCompanies'
import { AuthContext } from '../context/AuthContext'
import { listJobs, readJobActions, markJobApplied, setJobState } from '../api/jobs'
import AppliedNotice from '../components/AppliedNotice'
import { getMatchingResume } from '../api/resumeBuilder'
import JobCard from '../components/JobCard'
import JobDetails from '../components/JobDetails'
import { getGmailStatus, beginGmailConnect, disconnectGmail } from '../api/gmail'
import { getCalendarStatus, getCalendarConnectUrl, disconnectCalendar } from '../api/calendar'

function ConnectedServicesCard() {
  const [gmail, setGmail] = useState(null)
  const [calendar, setCalendar] = useState(null)
  const [gmailBusy, setGmailBusy] = useState(false)
  const [calBusy, setCalBusy] = useState(false)

  useEffect(() => {
    getGmailStatus().then(setGmail).catch(() => setGmail({ connected: false }))
    getCalendarStatus().then(r => setCalendar(r.data)).catch(() => setCalendar({ connected: false, configured: false }))
  }, [])

  async function connectGmail() {
    setGmailBusy(true)
    try {
      const { authorizationUrl } = await beginGmailConnect()
      window.location.href = authorizationUrl
    } catch { setGmailBusy(false) }
  }

  async function handleDisconnectGmail() {
    setGmailBusy(true)
    try { await disconnectGmail(); setGmail({ connected: false }) }
    finally { setGmailBusy(false) }
  }

  async function connectCalendar() {
    setCalBusy(true)
    try {
      const { data } = await getCalendarConnectUrl()
      window.location.href = data.url
    } catch { setCalBusy(false) }
  }

  async function handleDisconnectCalendar() {
    setCalBusy(true)
    try { await disconnectCalendar(); setCalendar(prev => ({ ...prev, connected: false })) }
    finally { setCalBusy(false) }
  }

  const services = [
    {
      key: 'gmail',
      label: 'Gmail',
      desc: 'Auto-detect job emails and update your applications',
      icon: Mail,
      connected: gmail?.connected,
      configured: true,
      busy: gmailBusy,
      onConnect: connectGmail,
      onDisconnect: handleDisconnectGmail,
    },
    {
      key: 'calendar',
      label: 'Google Calendar',
      desc: 'Sync interview events and get smart reminders',
      icon: CalendarDays,
      connected: calendar?.connected,
      configured: calendar?.configured !== false,
      busy: calBusy,
      onConnect: connectCalendar,
      onDisconnect: handleDisconnectCalendar,
    },
  ]

  return (
    <div className="bg-surface border border-line rounded-xl2 shadow-card p-5 mb-6">
      <h2 className="font-display text-[15px] text-ink mb-4">Connected services</h2>
      <div className="grid sm:grid-cols-2 gap-3">
        {services.map(({ key, label, desc, icon: Icon, connected, configured, busy, onConnect, onDisconnect }) => (
          <div key={key} className="flex items-start gap-3 p-3 rounded-xl bg-paper border border-line">
            <div className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${connected ? 'bg-accent/15' : 'bg-paper'} border border-line`}>
              <Icon size={16} className={connected ? 'text-accent' : 'text-muted'} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-1.5 mb-0.5">
                <span className="text-sm font-medium text-ink">{label}</span>
                {connected && (
                  <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-green-400 bg-green-400/10 px-1.5 py-0.5 rounded-full">
                    <CheckCircle2 size={9} /> Connected
                  </span>
                )}
              </div>
              <p className="text-xs text-muted leading-snug mb-2">{desc}</p>
              {!configured ? (
                <span className="text-xs text-muted italic">Not configured on server</span>
              ) : connected ? (
                <button
                  onClick={onDisconnect}
                  disabled={busy}
                  className="inline-flex items-center gap-1 text-xs text-status-rejected hover:opacity-80 disabled:opacity-50"
                >
                  {busy ? <Loader2 size={11} className="animate-spin" /> : <Unlink size={11} />}
                  Disconnect
                </button>
              ) : (
                <button
                  onClick={onConnect}
                  disabled={busy}
                  className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:opacity-80 disabled:opacity-50"
                >
                  {busy ? <Loader2 size={11} className="animate-spin" /> : <Link2 size={11} />}
                  Connect
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function timeGreeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

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
  const [appliedNotice, setAppliedNotice] = useState(null)
  const [jobActionError, setJobActionError] = useState('')

  useEffect(() => {
    // Fetch applications and jobs in parallel — neither blocks the other
    fetchApps()
    fetchJobs()
  }, [])

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
      // Fetch job list and resume in parallel — avoids two sequential round-trips
      const [res, matchingResume] = await Promise.all([
        listJobs({ page: 0, size: 6, sort: 'postedAt,desc' }),
        getMatchingResume().catch(() => null)
      ])
      const content = res.content || []
      // Show jobs immediately without match scores so the page renders fast
      setJobs(content)
      setJobLoading(false)

      // Compute match scores in the background — updates the list once ready
      // Universal resume if the user has one, else their newest upload.
      const resumeId = matchingResume?.resumeId
      if (resumeId && content.length > 0) {
        const scored = await Promise.all(
          content.map(async (job) => {
            try {
              const match = await api.post('/match/hybrid-score', { resumeId, jobId: job.id })
              return { ...job, matchScore: match.data.overallMatch }
            } catch { return job }
          })
        )
        setJobs(scored.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0)))
      }
    } catch (e) {
      console.error(e)
      setJobError('Job recommendations are unavailable right now.')
      setJobLoading(false)
    }
  }

  // Persists the action on the server (Mark applied creates the application) before updating the card.
  async function updateJobAction(id, action) {
    try {
      if (action === 'APPLIED') {
        await markJobApplied(id)
        setAppliedNotice({ company: jobs.find((job) => job.id === id)?.company })
        fetchApps()
      } else if (action === 'SAVED' || action === 'BOOKMARKED') {
        await setJobState(id, action)
      }
      setJobActionError('')
    } catch {
      setJobActionError('Could not update this job. Try again.')
      return
    }
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

  // Compare with UPPERCASE values — that's how they're stored
  const savedJobs = Object.values(jobActions).filter((v) => v === 'SAVED' || v === 'BOOKMARKED').length
  const appliedJobs = Object.values(jobActions).filter((v) => v === 'APPLIED').length

  const scoredJobs = jobs.filter((job) => job.matchScore != null)
  const averageMatch = scoredJobs.length
    ? Math.round(scoredJobs.reduce((sum, job) => sum + job.matchScore, 0) / scoredJobs.length)
    : null

  return (
    <Layout
      title={firstName ? `${timeGreeting()}, ${firstName}! 👋` : 'Dashboard'}
      subtitle="Here's where your search stands today"
      actions={
        <button
          onClick={() => { setForm(emptyForm); setDrawerOpen(true) }}
          className="btn-gradient inline-flex items-center gap-1.5 text-sm font-medium px-3.5 py-2 rounded-full shadow-glow"
        >
          <Plus size={15} /> Add application
        </button>
      }
    >
      {/* ── Application stats ── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="Total applications" value={total} icon={Briefcase} tone="violet" />
        <StatCard label="Interviews" value={interviews} icon={MessagesSquare} tone="sky" />
        <StatCard label="Offers" value={offers} icon={PartyPopper} tone="mint" />
        <StatCard label="Rejections" value={rejections} icon={XCircle} tone="ember" />
      </div>

      <div className="grid lg:grid-cols-3 gap-4 mb-6">
        <PipelineBar applications={applications} />

        <div className="bg-surface border border-line rounded-xl2 shadow-card p-5 flex flex-col items-center text-center">
          <h2 className="font-display text-[15px] text-ink self-start mb-2">Resume match</h2>
          {averageMatch == null ? (
            <div className="flex-1 flex flex-col items-center justify-center py-4">
              <p className="text-sm text-muted max-w-[16rem]">
                Upload a resume to see how well it matches your recommended jobs.
              </p>
            </div>
          ) : (
            <ScoreRing value={averageMatch} size={112} />
          )}
          <Link to="/resume-match" className="btn-gradient mt-3 w-full text-center text-xs font-medium py-2 rounded-full">
            View full insights
          </Link>
        </div>

        <TopCompanies applications={applications} />
      </div>

      {/* ── Job stats ── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard label="Total jobs" value={jobs.length || '—'} icon={Search} tone="sky" />
        <StatCard label="Strong matches" value={jobs.filter((j) => (j.matchScore || 0) >= 70).length || '—'} icon={Sparkles} tone="violet" />
        <StatCard label="Saved jobs" value={savedJobs || '—'} icon={Bookmark} tone="amber" />
        <StatCard label="Applied jobs" value={appliedJobs || '—'} icon={CheckCircle2} tone="mint" />
      </div>

      {/* ── Recent applications (appears before jobs in DOM to match heading order) ── */}
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-lg text-ink">Recent applications</h2>
        <Link to="/applications" className="text-sm font-medium text-ink inline-flex items-center gap-1 hover:text-accent">
          View all <ArrowRight size={14} />
        </Link>
      </div>

      {loading ? (
        <div className="space-y-3 mb-8 animate-pulse">
          {[0, 1, 2].map((i) => (
            <div key={i} className="bg-surface border border-line rounded-xl2 shadow-card p-4">
              <div className="flex items-center justify-between gap-3 mb-2">
                <div className="h-4 w-40 bg-paper rounded" />
                <div className="h-5 w-20 bg-paper rounded-full" />
              </div>
              <div className="h-3 w-56 bg-paper rounded mb-1.5" />
              <div className="h-3 w-32 bg-paper rounded" />
            </div>
          ))}
        </div>
      ) : applications.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center mb-8">
          <p className="font-display text-ink text-lg mb-1">No applications yet</p>
          <p className="text-sm text-muted mb-4">Add the first role you've applied to — it takes a few seconds.</p>
          <button
            onClick={() => setDrawerOpen(true)}
            className="btn-gradient inline-flex items-center gap-1.5 text-sm font-medium px-4 py-2 rounded-full shadow-glow"
          >
            <Plus size={15} /> Add application
          </button>
        </div>
      ) : (
        <div className="space-y-3 mb-8">
          {applications.slice(0, 5).map((a) => (
            <ApplicationCard key={a.id} app={a} />
          ))}
        </div>
      )}

      {/* ── Connected services ── */}
      <ConnectedServicesCard />

      {/* ── Recommended jobs ── */}
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-lg text-ink">Recommended jobs</h2>
        <Link to="/discovery" className="text-sm font-medium text-ink inline-flex items-center gap-1 hover:text-accent">
          Explore all <ArrowRight size={14} />
        </Link>
      </div>

      {jobLoading ? (
        <div className="space-y-3 animate-pulse">
          {[0, 1].map((i) => (
            <div key={i} className="bg-surface border border-line rounded-xl2 shadow-card p-4">
              <div className="flex items-start gap-3">
                <div className="h-10 w-10 rounded-lg bg-paper shrink-0" />
                <div className="flex-1 space-y-2 pt-0.5">
                  <div className="h-4 w-48 bg-paper rounded" />
                  <div className="h-3 w-32 bg-paper rounded" />
                  <div className="h-3 w-24 bg-paper rounded" />
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : jobError ? (
        <div className="text-sm text-status-rejected">{jobError}</div>
      ) : jobs.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-8 text-center">
          <p className="font-display text-ink">No jobs discovered yet</p>
          <Link to="/discovery" className="text-sm text-muted hover:text-ink">Open job discovery</Link>
        </div>
      ) : (
        <div className="space-y-3">
          {jobActionError && <p className="text-sm text-status-rejected">{jobActionError}</p>}
          {jobs[0]?.matchScore != null && (
            <p className="text-xs text-muted mb-3">
              Top match: {jobs[0].title} · {Math.round(jobs[0].matchScore)}%
            </p>
          )}
          {jobs.slice(0, 3).map((job) => (
            <JobCard
              key={job.id}
              job={job}
              action={jobActions[job.id]}
              onAction={updateJobAction}
              onOpen={(id) => setSelectedJob(jobs.find((item) => item.id === id))}
            />
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
      <AppliedNotice notice={appliedNotice} onClose={() => setAppliedNotice(null)} />
    </Layout>
  )
}
