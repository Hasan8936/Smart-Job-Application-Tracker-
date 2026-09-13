import React, { useEffect, useState } from 'react'
import {
  ChevronLeft, ChevronRight, Filter, Loader2, RefreshCw,
  Search, Sparkles, X
} from 'lucide-react'
import Layout from '../components/Layout'
import JobCard from '../components/JobCard'
import JobDetails from '../components/JobDetails'
import {
  discoverJobs, generateJobDocument, getJob, getLastJobsVisit,
  getSyncProgress, listJobDocuments, listJobs, listNewJobs, markJobApplied,
  markJobsVisitedNow, readJobActions, setJobState, updateJobDocument
} from '../api/jobs'

const BLANK = { q: '', location: '', employmentType: '', provider: '', postedAfter: '', postedBefore: '' }

const EMPLOYMENT_TYPES = ['Full-time', 'Part-time', 'Contract', 'Internship']
const SORT_OPTIONS = [
  { value: 'postedAt,desc', label: 'Newest first' },
  { value: 'postedAt,asc', label: 'Oldest first' },
  { value: 'title,asc', label: 'Title A – Z' },
  { value: 'company,asc', label: 'Company A – Z' },
]

function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

const DATE_PRESETS = [
  { label: 'Any time', after: '', before: '' },
  { label: 'Past 24 h', after: daysAgo(1), before: '' },
  { label: 'Past week', after: daysAgo(7), before: '' },
  { label: 'Past month', after: daysAgo(30), before: '' },
]

function activeFilterCount(filters) {
  return [filters.location, filters.employmentType, filters.postedAfter, filters.postedBefore]
    .filter(Boolean).length
}

function activeFilterChips(filters, setFilters) {
  const chips = []
  if (filters.location) chips.push({ label: `📍 ${filters.location}`, clear: () => setFilters(f => ({ ...f, location: '' })) })
  if (filters.employmentType) chips.push({ label: filters.employmentType, clear: () => setFilters(f => ({ ...f, employmentType: '' })) })
  if (filters.postedAfter || filters.postedBefore) {
    const preset = DATE_PRESETS.find(p => p.after === filters.postedAfter && p.before === filters.postedBefore)
    chips.push({ label: preset ? preset.label : 'Custom date', clear: () => setFilters(f => ({ ...f, postedAfter: '', postedBefore: '' })) })
  }
  return chips
}

export default function Discovery() {
  const [filters, setFilters] = useState(BLANK)
  const [draft, setDraft] = useState(BLANK)       // held while user types; applied on submit
  const [showFilters, setShowFilters] = useState(false)
  const [jobs, setJobs] = useState({ content: [], number: 0, totalPages: 0, totalElements: 0 })
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState('postedAt,desc')
  const [actions, setActions] = useState(readJobActions())
  const [details, setDetails] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [documents, setDocuments] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncMessage, setSyncMessage] = useState('')
  const [newJobsCount, setNewJobsCount] = useState(0)
  const [showingOnlyNew, setShowingOnlyNew] = useState(false)
  const [sessionSince, setSessionSince] = useState(null)

  useEffect(() => { loadJobs() }, [page, sort, filters, showingOnlyNew])
  useEffect(() => { checkForNewJobs() }, [])

  async function checkForNewJobs() {
    const since = getLastJobsVisit()
    setSessionSince(since)
    try {
      const result = await listNewJobs(since ? { since, size: 1 } : { size: 1 })
      setNewJobsCount(result.totalElements || 0)
    } catch { /* badge is non-critical */ }
    finally { markJobsVisitedNow() }
  }

  const FRESHER_ROLES = [
    'fresher software engineer', 'junior software engineer', 'entry level software developer',
    'software trainee', 'graduate software engineer', 'junior frontend developer',
    'junior backend developer', 'junior full stack developer', 'junior data analyst',
    'software engineer intern', 'junior devops engineer', 'machine learning engineer fresher',
  ]

  const INDIA_CITIES = [
    'Bangalore', 'Hyderabad', 'Delhi', 'Noida', 'Gurgaon', 'Chennai', 'Pune', 'Mumbai',
    'Chandigarh', 'Lucknow',
  ]

  async function syncSources() {
    let pollTimer = null
    try {
      setSyncing(true); setError('')
      setSyncMessage('Connecting to job boards…')
      const body = {}
      if (filters.q) body.keywords = filters.q
      else body.roles = FRESHER_ROLES
      body.locations = filters.location ? [filters.location] : ['India']

      const { syncId } = await discoverJobs(body)

      await new Promise((resolve, reject) => {
        const TIMEOUT_MS = 120_000
        const start = Date.now()
        pollTimer = setInterval(async () => {
          try {
            if (Date.now() - start > TIMEOUT_MS) {
              clearInterval(pollTimer)
              reject(new Error('Sync timed out — try again'))
              return
            }
            const progress = await getSyncProgress(syncId)
            if (progress.currentProvider) {
              const label = progress.currentProvider.replace(/-/g, ' ')
              setSyncMessage(`Scanning ${label} — ${progress.totalSaved} job${progress.totalSaved === 1 ? '' : 's'} found so far…`)
            }
            if (progress.done) {
              clearInterval(pollTimer)
              const errs = progress.errors && Object.keys(progress.errors).length > 0
                ? ` (${Object.entries(progress.errors).map(([p, m]) => `${p}: ${m}`).join('; ')})`
                : ''
              setSyncMessage(`Synced ${progress.totalSaved} job${progress.totalSaved === 1 ? '' : 's'}.${errs}`)
              resolve()
            }
          } catch (e) { clearInterval(pollTimer); reject(e) }
        }, 1500)
      })

      setPage(0)
      await loadJobs()
      try {
        const r = await listNewJobs(sessionSince ? { since: sessionSince, size: 1 } : { size: 1 })
        setNewJobsCount(r.totalElements || 0)
      } catch { /* non-critical */ }
    } catch (e) {
      if (pollTimer) clearInterval(pollTimer)
      setError(e.response?.data?.error || e.message || 'Could not sync job sources. The service may be starting up — try again in 30 seconds.')
    } finally { setSyncing(false) }
  }

  async function loadJobs() {
    try {
      setLoading(true); setError('')
      if (showingOnlyNew) {
        setJobs(await listNewJobs({ ...filters, since: sessionSince || undefined, page, size: 10 }))
      } else {
        setJobs(await listJobs({ ...filters, page, size: 10, sort }))
      }
    } catch { setError('Could not load discovered jobs. Try again.') }
    finally { setLoading(false) }
  }

  function applySearch(e) {
    e.preventDefault()
    setFilters({ ...draft })
    setPage(0)
  }

  function clearAllFilters() {
    setFilters(BLANK)
    setDraft(BLANK)
    setPage(0)
  }

  async function openDetails(id) {
    try { setDetails(await getJob(id)); setDocuments(await listJobDocuments(id)) }
    catch { setError('Could not load job details.') }
  }

  async function action(id, value) {
    try {
      if (value === 'APPLIED') await markJobApplied(id); else await setJobState(id, value)
      setActions(current => ({ ...current, [id]: value }))
    } catch { setError('Could not update this job action.') }
  }

  async function generate(type) {
    if (!details) return
    try {
      setDocumentLoading(true)
      const doc = await generateJobDocument(details.id, type)
      setDocuments(current => [doc, ...current])
    } catch (e) { setError(e.response?.data?.error || 'Could not generate this draft.') }
    finally { setDocumentLoading(false) }
  }

  async function saveDocument(document) {
    try {
      const saved = await updateJobDocument(document.id, document.content)
      setDocuments(current => current.map(item => item.id === saved.id ? saved : item))
    } catch { setError('Could not save this draft.') }
  }

  function toggleNew() { setShowingOnlyNew(v => !v); setPage(0) }

  const chips = activeFilterChips(filters, (fn) => { setFilters(fn); setDraft(fn); setPage(0) })
  const filterCount = activeFilterCount(filters)
  const activePreset = DATE_PRESETS.find(p => p.after === filters.postedAfter && p.before === filters.postedBefore) || null

  return (
    <Layout title="Discover jobs" subtitle="Find roles from configured official job sources">

      {/* ── Search bar ── */}
      <form onSubmit={applySearch} className="bg-surface border border-line rounded-xl2 shadow-card overflow-hidden mb-4">
        <div className="flex items-center gap-2 p-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted pointer-events-none" />
            <input
              value={draft.q}
              onChange={e => setDraft({ ...draft, q: e.target.value })}
              placeholder="Search title or company…"
              className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-line bg-paper text-sm focus:outline-none focus:ring-2 focus:ring-accent/30"
            />
          </div>
          <button
            type="button"
            onClick={() => setShowFilters(v => !v)}
            className={`relative inline-flex items-center gap-1.5 px-3 py-2.5 rounded-lg border text-sm font-medium transition-colors ${showFilters ? 'border-accent text-accent bg-accent/5' : 'border-line text-ink-soft'}`}
          >
            <Filter size={15} />
            Filters
            {filterCount > 0 && (
              <span className="absolute -top-1.5 -right-1.5 h-4 w-4 rounded-full bg-accent text-white text-[10px] flex items-center justify-center font-mono">
                {filterCount}
              </span>
            )}
          </button>
          <button type="submit" className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg btn-gradient text-sm font-medium">
            Search
          </button>
        </div>

        {/* ── Expanded filter panel ── */}
        {showFilters && (
          <div className="border-t border-line px-3 pb-3 pt-3 space-y-3">
            {/* Date presets */}
            <div>
              <p className="text-xs font-medium text-muted mb-1.5 uppercase tracking-wide">Posted</p>
              <div className="flex flex-wrap gap-1.5">
                {DATE_PRESETS.map(preset => (
                  <button
                    key={preset.label}
                    type="button"
                    onClick={() => { setDraft(d => ({ ...d, postedAfter: preset.after, postedBefore: preset.before })) }}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${draft.postedAfter === preset.after && draft.postedBefore === preset.before ? 'bg-accent text-white border-accent' : 'border-line text-ink-soft hover:border-ink/30'}`}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Employment type */}
            <div>
              <p className="text-xs font-medium text-muted mb-1.5 uppercase tracking-wide">Employment type</p>
              <div className="flex flex-wrap gap-1.5">
                {EMPLOYMENT_TYPES.map(t => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setDraft(d => ({ ...d, employmentType: d.employmentType === t ? '' : t }))}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${draft.employmentType === t ? 'bg-accent text-white border-accent' : 'border-line text-ink-soft hover:border-ink/30'}`}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>

            {/* Location */}
            <div>
              <p className="text-xs font-medium text-muted mb-1 uppercase tracking-wide">Location</p>
              <input
                placeholder="e.g. Bangalore, Hyderabad (default: India)"
                value={draft.location}
                onChange={e => setDraft({ ...draft, location: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-line bg-paper text-sm focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
              <div className="flex flex-wrap gap-1.5 mt-2">
                {INDIA_CITIES.map(city => (
                  <button
                    key={city}
                    type="button"
                    onClick={() => setDraft({ ...draft, location: city })}
                    className={`px-2 py-0.5 rounded-full text-xs border transition-colors ${draft.location === city ? 'bg-accent text-white border-accent' : 'border-line text-muted hover:border-accent hover:text-accent'}`}
                  >
                    {city}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => { setDraft(BLANK) }}
                className="text-xs text-muted hover:text-ink underline"
              >
                Clear filters
              </button>
              <button type="submit" className="inline-flex items-center gap-1.5 px-4 py-2 rounded-full btn-gradient text-xs font-medium">
                Apply filters
              </button>
            </div>
          </div>
        )}
      </form>

      {/* ── Active filter chips ── */}
      {chips.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5 mb-4">
          <span className="text-xs text-muted">Active filters:</span>
          {chips.map((chip, i) => (
            <span key={i} className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-accent/10 border border-accent/20 text-xs text-accent font-medium">
              {chip.label}
              <button onClick={chip.clear} className="hover:text-accent-dark ml-0.5" aria-label="Remove filter">
                <X size={11} />
              </button>
            </span>
          ))}
          <button onClick={clearAllFilters} className="text-xs text-muted hover:text-ink underline ml-1">
            Clear all
          </button>
        </div>
      )}

      {/* ── Results header ── */}
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-3">
          <p className="text-sm text-ink font-medium">
            {loading ? 'Loading…' : `${jobs.totalElements ?? 0} role${jobs.totalElements === 1 ? '' : 's'}`}
          </p>
          {newJobsCount > 0 && (
            <button
              onClick={toggleNew}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${showingOnlyNew ? 'btn-gradient' : 'border border-line text-ink-soft'}`}
            >
              <Sparkles size={13} />
              {showingOnlyNew ? 'Showing new' : `${newJobsCount} new`}
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <select
            value={sort}
            onChange={e => { setSort(e.target.value); setPage(0) }}
            className="px-3 py-1.5 rounded-lg border border-line bg-surface text-xs focus:outline-none focus:ring-2 focus:ring-accent/30"
          >
            {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <button
            onClick={syncSources}
            disabled={syncing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-line text-ink-soft text-xs font-medium disabled:opacity-50 hover:border-ink/30 transition-colors"
          >
            {syncing ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />}
            {syncing ? 'Syncing…' : 'Sync'}
          </button>
        </div>
      </div>

      {syncMessage && <p className="text-xs text-muted mb-3">{syncMessage}</p>}
      {error && (
        <div className="border border-status-rejected/30 bg-status-rejectedSoft text-status-rejected rounded-lg p-3 text-sm mb-4">
          {error}
        </div>
      )}

      {/* ── Job list ── */}
      {loading ? (
        <div className="space-y-3 animate-pulse">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="bg-surface border border-line rounded-xl2 shadow-card p-4">
              <div className="flex items-start gap-3">
                <div className="h-10 w-10 rounded-lg bg-paper shrink-0" />
                <div className="flex-1 space-y-2 pt-0.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="h-4 w-48 bg-paper rounded" />
                    <div className="h-5 w-16 bg-paper rounded-full" />
                  </div>
                  <div className="h-3 w-36 bg-paper rounded" />
                  <div className="flex gap-2">
                    <div className="h-5 w-20 bg-paper rounded-full" />
                    <div className="h-5 w-24 bg-paper rounded-full" />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : jobs.content?.length === 0 ? (
        <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center">
          <Search size={32} className="mx-auto text-muted mb-3" />
          <h2 className="font-display text-lg">No jobs found</h2>
          <p className="text-sm text-muted mt-1">
            {filterCount > 0 ? 'Try removing some filters, or ' : 'Try '}
            run a discovery sync to pull fresh listings.
          </p>
          {filterCount > 0 && (
            <button onClick={clearAllFilters} className="mt-3 text-sm text-accent underline">Clear filters</button>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {jobs.content.map(job => (
            <JobCard key={job.id} job={job} action={actions[job.id]} onAction={action} onOpen={openDetails} />
          ))}
        </div>
      )}

      {/* ── Pagination ── */}
      {jobs.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-6">
          <button
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
            className="h-9 w-9 rounded-full border border-line flex items-center justify-center disabled:opacity-40 hover:border-ink/30 transition-colors"
            aria-label="Previous page"
          >
            <ChevronLeft size={16} />
          </button>
          <div className="flex items-center gap-1">
            {Array.from({ length: Math.min(jobs.totalPages, 7) }, (_, i) => {
              // Show first 3, last 3, and current ± 1 with ellipsis gaps
              const tp = jobs.totalPages
              const show = i === 0 || i === tp - 1 || Math.abs(i - page) <= 1
              const prevShow = i === 0 ? true : (
                i - 1 === 0 || i - 1 === tp - 1 || Math.abs(i - 1 - page) <= 1
              )
              if (!show) return (!prevShow ? null : <span key={`e${i}`} className="text-muted text-sm px-1">…</span>)
              return (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`h-9 w-9 rounded-full text-sm font-medium transition-colors ${i === page ? 'btn-gradient' : 'border border-line text-ink-soft hover:border-ink/30'}`}
                >
                  {i + 1}
                </button>
              )
            })}
          </div>
          <button
            disabled={page + 1 >= jobs.totalPages}
            onClick={() => setPage(page + 1)}
            className="h-9 w-9 rounded-full border border-line flex items-center justify-center disabled:opacity-40 hover:border-ink/30 transition-colors"
            aria-label="Next page"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      )}

      <JobDetails
        job={details}
        onClose={() => setDetails(null)}
        onGenerate={generate}
        documents={documents}
        onSaveDocument={saveDocument}
      />
    </Layout>
  )
}
