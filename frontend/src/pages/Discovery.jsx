import React, { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Search, SlidersHorizontal } from 'lucide-react'
import Layout from '../components/Layout'
import JobCard from '../components/JobCard'
import JobDetails from '../components/JobDetails'
import { generateJobDocument, getJob, listJobDocuments, listJobs, markJobApplied, readJobActions, setJobState, updateJobDocument } from '../api/jobs'

const initialFilters = { q: '', location: '', employmentType: '', provider: '', postedAfter: '', postedBefore: '' }

export default function Discovery() {
  const [filters, setFilters] = useState(initialFilters)
  const [jobs, setJobs] = useState({ content: [], number: 0, totalPages: 0, totalElements: 0 })
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState('postedAt,desc')
  const [actions, setActions] = useState(readJobActions())
  const [details, setDetails] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [documents, setDocuments] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)

  useEffect(() => { loadJobs() }, [page, sort])
  async function loadJobs() {
    try { setLoading(true); setError(''); setJobs(await listJobs({ ...filters, page, size: 10, sort })) } catch { setError('Could not load discovered jobs. Try again.') } finally { setLoading(false) }
  }
  function submit(e) { e.preventDefault(); setPage(0); loadJobs() }
  async function openDetails(id) { try { setDetails(await getJob(id)); setDocuments(await listJobDocuments(id)) } catch { setError('Could not load job details.') } }
  async function action(id, value) { try { if (value === 'APPLIED') await markJobApplied(id); else await setJobState(id, value); setActions(readJobActions()); setActions(current => ({ ...current, [id]: value })) } catch { setError('Could not update this job action.') } }
  async function generate(type) { if (!details) return; try { setDocumentLoading(true); const document = await generateJobDocument(details.id, type); setDocuments(current => [document, ...current]) } catch { setError('Could not generate this draft. Add a verified candidate profile first.') } finally { setDocumentLoading(false) } }
  async function saveDocument(document) { try { const saved = await updateJobDocument(document.id, document.content); setDocuments(current => current.map(item => item.id === saved.id ? saved : item)) } catch { setError('Could not save this draft.') } }

  return <Layout title="Discover jobs" subtitle="Find roles from configured official job sources">
    <form onSubmit={submit} className="bg-surface border border-line rounded-xl2 p-4 mb-5 shadow-card">
      <div className="flex flex-col sm:flex-row gap-2"><div className="relative flex-1"><Search size={16} className="absolute left-3 top-3 text-muted" /><input value={filters.q} onChange={e => setFilters({ ...filters, q: e.target.value })} placeholder="Search title or company" className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-line bg-paper" /></div><button className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-ink text-white text-sm font-medium"><Search size={15} /> Search</button></div>
      <div className="grid grid-cols-2 md:grid-cols-5 gap-2 mt-3"><input placeholder="Location" value={filters.location} onChange={e => setFilters({ ...filters, location: e.target.value })} className="px-3 py-2 rounded-lg border border-line bg-paper" /><select value={filters.employmentType} onChange={e => setFilters({ ...filters, employmentType: e.target.value })} className="px-3 py-2 rounded-lg border border-line bg-paper"><option value="">All employment types</option><option>Full-time</option><option>Part-time</option><option>Contract</option><option>Internship</option></select><select value={sort} onChange={e => { setSort(e.target.value); setPage(0) }} className="px-3 py-2 rounded-lg border border-line bg-paper"><option value="postedAt,desc">Newest first</option><option value="title,asc">Title A-Z</option><option value="company,asc">Company A-Z</option></select><input type="date" value={filters.postedAfter} onChange={e => setFilters({ ...filters, postedAfter: e.target.value })} className="px-3 py-2 rounded-lg border border-line bg-paper" /><input type="date" value={filters.postedBefore} onChange={e => setFilters({ ...filters, postedBefore: e.target.value })} className="px-3 py-2 rounded-lg border border-line bg-paper" /></div>
    </form>
    <div className="flex items-center justify-between mb-3"><div className="flex items-center gap-2"><SlidersHorizontal size={16} className="text-muted" /><h2 className="font-display text-lg">Available jobs</h2></div><span className="text-sm text-muted">{jobs.totalElements || 0} roles</span></div>
    {error && <div className="border border-status-rejected/30 bg-status-rejectedSoft text-status-rejected rounded-lg p-3 text-sm mb-4">{error}</div>}
    {loading ? <div className="space-y-3">{[1, 2, 3].map(item => <div key={item} className="h-36 rounded-xl2 bg-surface border border-line animate-pulse" />)}</div> : jobs.content?.length === 0 ? <div className="bg-surface border border-dashed border-line rounded-xl2 p-10 text-center"><h2 className="font-display text-lg">No jobs found</h2><p className="text-sm text-muted mt-1">Try a broader search or run a discovery sync.</p></div> : <div className="space-y-3">{jobs.content.map(job => <JobCard key={job.id} job={job} action={actions[job.id]} onAction={action} onOpen={openDetails} />)}</div>}
    {jobs.totalPages > 1 && <div className="flex items-center justify-center gap-3 mt-5"><button disabled={page === 0} onClick={() => setPage(page - 1)} className="h-10 w-10 rounded-lg border border-line flex items-center justify-center disabled:opacity-40" aria-label="Previous page"><ChevronLeft size={16} /></button><span className="text-sm text-muted">Page {page + 1} of {jobs.totalPages}</span><button disabled={page + 1 >= jobs.totalPages} onClick={() => setPage(page + 1)} className="h-10 w-10 rounded-lg border border-line flex items-center justify-center disabled:opacity-40" aria-label="Next page"><ChevronRight size={16} /></button></div>}
    <JobDetails job={details} onClose={() => setDetails(null)} onGenerate={generate} documents={documents} onSaveDocument={saveDocument} />
  </Layout>
}