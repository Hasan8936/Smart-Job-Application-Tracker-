import React from 'react'
import { ExternalLink, FileText, Mail, MessageSquare, Pencil, X } from 'lucide-react'

export default function JobDetails({ job, onClose, onGenerate, documents = [], onSaveDocument }) {
  if (!job) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-5">
      <button aria-label="Close job details" onClick={onClose} className="absolute inset-0 bg-ink/50" />
      <section className="relative w-full sm:max-w-2xl max-h-[90vh] overflow-y-auto bg-surface sm:rounded-xl2 shadow-card p-5 sm:p-7">
        <button onClick={onClose} className="absolute top-4 right-4 h-10 w-10 rounded-full border border-line flex items-center justify-center" aria-label="Close"><X size={17} /></button>
        <div className="pr-12 mb-6">
          <p className="text-sm text-muted mb-1">{job.company}</p>
          <h2 className="font-display text-2xl text-ink">{job.title}</h2>
          <p className="text-sm text-muted mt-2">{job.location || 'Location unavailable'} · {job.employmentType || 'Employment type unavailable'}</p>
        </div>
        <div className="grid grid-cols-2 gap-3 mb-6">
          <div className="bg-paper rounded-lg p-3"><div className="text-xs text-muted">Salary</div><div className="text-sm text-ink mt-1">{job.salaryMin || job.salaryMax ? `${job.salaryMin || '—'} – ${job.salaryMax || '—'} ${job.salaryCurrency || ''}` : 'Unavailable'}</div></div>
          <div className="bg-paper rounded-lg p-3"><div className="text-xs text-muted">Posted</div><div className="text-sm text-ink mt-1">{job.postedAt ? new Date(job.postedAt).toLocaleDateString() : 'Unavailable'}</div></div>
        </div>
        <h3 className="font-display text-base mb-2">Job description</h3>
        <p className="text-sm text-muted whitespace-pre-line leading-6">{job.description || 'Description unavailable.'}</p>
        {job.requiredSkills?.length > 0 && <SkillGroup label="Required skills" skills={job.requiredSkills} />}
        {job.preferredSkills?.length > 0 && <SkillGroup label="Preferred skills" skills={job.preferredSkills} />}
        <div className="mt-6 flex flex-wrap gap-2">
          <a href={job.applyUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 bg-accent text-white hover:bg-accent-dark rounded-full px-4 py-2.5 text-sm font-medium"><ExternalLink size={15} /> Open official application</a>
          {onGenerate && <button onClick={() => onGenerate('COVER_LETTER')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><FileText size={15} /> Cover letter</button>}
          {onGenerate && <><button onClick={() => onGenerate('COLD_EMAIL')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><Mail size={15} /> Cold email</button>
          <button onClick={() => onGenerate('INTERVIEW_QUESTIONS')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><MessageSquare size={15} /> Interview questions</button>
          <button onClick={() => onGenerate('IMPROVE_RESUME')} className="inline-flex items-center gap-2 border border-line rounded-full px-3 py-2.5 text-sm text-ink-soft"><Pencil size={15} /> Improve resume</button></>}
        </div>
        {documents.length > 0 && <div className="mt-7"><h3 className="font-display text-base mb-2">Generated drafts</h3>{documents.map(document => <div key={document.id} className="border border-line rounded-lg p-3 mb-2"><div className="text-xs text-muted mb-2">{document.type.replaceAll('_', ' ')}</div><textarea value={document.content} onChange={event => onSaveDocument({ ...document, content: event.target.value })} rows={6} className="w-full bg-paper rounded-lg border border-line p-3 text-sm leading-6" /></div>)}</div>}
      </section>
    </div>
  )
}

function SkillGroup({ label, skills }) {
  return <div className="mt-5"><h3 className="font-display text-base mb-2">{label}</h3><div className="flex flex-wrap gap-1.5">{skills.map(skill => <span key={skill} className="px-2.5 py-1 rounded-full bg-accent-soft text-accent text-xs font-medium">{skill}</span>)}</div></div>
}