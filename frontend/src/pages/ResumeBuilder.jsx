import React, { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Target, FileText, Eye, Download, Plus, Trash2, ChevronRight,
  ChevronLeft, CheckCircle, AlertCircle, Sparkles, X, Loader2,
  Briefcase, GraduationCap, Wrench, User, ArrowRight, Upload,
  Link2, Github, Code2, Globe, BookOpen, Wand2, FolderGit2
} from 'lucide-react'
import Layout from '../components/Layout'
import {
  getPrefill, exportResume, importResume, aiEnhanceResume,
  exportLatex, listResumes, uploadResume
} from '../api/resumeBuilder'

// ─── Role suggestions ─────────────────────────────────────────────────────────
const ROLE_DATA = {
  'Software Engineer': {
    bullets: [
      'Designed and shipped REST APIs serving millions of daily requests',
      'Reduced page load time by X% through frontend optimization',
      'Led migration from monolithic architecture to microservices',
      'Implemented CI/CD pipelines reducing deployment time by X%',
      'Mentored junior engineers and conducted code reviews',
    ],
    skills: { languages: ['Java', 'Python', 'JavaScript', 'TypeScript'], frameworks: ['Spring Boot', 'React', 'Node.js'], tools: ['Docker', 'Git', 'AWS', 'PostgreSQL'] },
  },
  'Frontend Developer': {
    bullets: [
      'Built responsive, accessible UI components using React and Tailwind CSS',
      'Improved Core Web Vitals scores, achieving a Lighthouse score of 95+',
      'Implemented state management with Redux Toolkit / Zustand',
      'Collaborated with designers to translate Figma mockups into pixel-perfect UIs',
    ],
    skills: { languages: ['JavaScript', 'TypeScript', 'HTML', 'CSS'], frameworks: ['React', 'Next.js', 'Tailwind CSS', 'Vite'], tools: ['Git', 'Figma', 'Webpack', 'Storybook'] },
  },
  'Backend Developer': {
    bullets: [
      'Designed scalable database schemas and optimized slow queries by X%',
      'Built and maintained RESTful and GraphQL APIs consumed by mobile and web clients',
      'Implemented authentication and authorization using JWT and OAuth 2.0',
      'Reduced API response time by X% through caching with Redis',
    ],
    skills: { languages: ['Java', 'Python', 'Go', 'SQL'], frameworks: ['Spring Boot', 'FastAPI', 'Express'], tools: ['PostgreSQL', 'Redis', 'Docker', 'Kubernetes', 'Git'] },
  },
  'Data Scientist': {
    bullets: [
      'Built and deployed ML models achieving X% accuracy on production data',
      'Developed ETL pipelines processing X GB of data daily',
      'Presented data-driven insights to stakeholders, influencing product decisions',
      'Reduced model training time by X% through feature engineering',
    ],
    skills: { languages: ['Python', 'R', 'SQL'], frameworks: ['TensorFlow', 'PyTorch', 'scikit-learn', 'Pandas'], tools: ['Jupyter', 'Spark', 'Airflow', 'AWS SageMaker'] },
  },
  'DevOps Engineer': {
    bullets: [
      'Automated infrastructure provisioning using Terraform, reducing setup time by X%',
      'Designed and maintained CI/CD pipelines for X+ microservices',
      'Reduced cloud infrastructure costs by X% through rightsizing',
      'Led incident response, maintaining 99.9% uptime SLA',
    ],
    skills: { languages: ['Python', 'Bash', 'Go'], frameworks: ['Kubernetes', 'Terraform', 'Ansible'], tools: ['Docker', 'Jenkins', 'GitHub Actions', 'AWS', 'GCP'] },
  },
  'Full Stack Developer': {
    bullets: [
      'Built end-to-end features spanning React frontend and Spring Boot backend',
      'Designed and implemented RESTful APIs consumed by web and mobile clients',
      'Integrated third-party APIs and payment gateways',
      'Wrote comprehensive unit and integration tests maintaining 80%+ code coverage',
    ],
    skills: { languages: ['JavaScript', 'TypeScript', 'Java', 'SQL'], frameworks: ['React', 'Node.js', 'Spring Boot', 'Next.js'], tools: ['PostgreSQL', 'Docker', 'Git', 'AWS'] },
  },
  'Machine Learning Engineer': {
    bullets: [
      'Productionized ML models serving X+ predictions per day with <50ms latency',
      'Built feature engineering pipelines reducing model training time by X%',
      'Implemented A/B testing framework to evaluate model improvements',
      'Reduced model inference costs by X% through quantization',
    ],
    skills: { languages: ['Python', 'C++', 'CUDA'], frameworks: ['PyTorch', 'TensorFlow', 'FastAPI', 'MLflow'], tools: ['Kubernetes', 'Airflow', 'AWS SageMaker', 'Docker'] },
  },
  'Product Manager': {
    bullets: [
      'Defined product roadmap and prioritized features based on user research and data',
      'Launched X features used by X+ monthly active users',
      'Collaborated with engineering, design, and marketing to ship on time',
      'Reduced customer churn by X% through targeted product improvements',
    ],
    skills: { languages: [], frameworks: [], tools: ['Jira', 'Figma', 'Mixpanel', 'SQL', 'Confluence', 'Notion'] },
  },
}

const ALL_ROLES = Object.keys(ROLE_DATA)
const GOALS = ['Get a new job', 'Level up my career', 'Make a career change', 'Land my first job']

// ─── Helpers ──────────────────────────────────────────────────────────────────
function emptyExp() {
  return { company: '', role: '', startDate: '', endDate: '', current: false, bullets: [''] }
}
function emptyEdu() {
  return { institution: '', degree: '', field: '', startYear: '', endYear: '', gpa: '' }
}
function emptyProject() {
  return { name: '', description: [''], techStack: [], githubUrl: '', liveUrl: '', date: '' }
}
function emptySkills() {
  return { languages: [], frameworks: [], tools: [], other: [] }
}

function buildPreviewText(form) {
  const { personalInfo: pi, summary, experience, projects, education, skills } = form
  const lines = []
  lines.push(pi.name || 'Your Name')
  const contact = [
    pi.email, pi.phone, pi.location,
    pi.linkedin?.replace(/^https?:\/\//, ''),
    pi.github?.replace(/^https?:\/\//, ''),
    pi.leetcode?.replace(/^https?:\/\//, ''),
    pi.website?.replace(/^https?:\/\//, ''),
  ].filter(Boolean)
  lines.push(contact.join(' | '))
  if (summary?.trim()) { lines.push(''); lines.push('SUMMARY'); lines.push(summary.trim()) }
  if (experience?.length) {
    lines.push(''); lines.push('EXPERIENCE')
    experience.forEach(e => {
      const end = e.current ? 'Present' : (e.endDate || 'Present')
      const date = e.startDate ? `${e.startDate} – ${end}` : end
      lines.push([e.company, e.role, date].filter(Boolean).join(' | '))
      e.bullets?.forEach(b => { if (b?.trim()) lines.push('• ' + b.trim()) })
      lines.push('')
    })
  }
  if (projects?.length) {
    lines.push('PROJECTS')
    projects.forEach(p => {
      lines.push([p.name, p.date].filter(Boolean).join(' | '))
      if (p.techStack?.length) lines.push('Tech Stack: ' + p.techStack.join(', '))
      p.description?.forEach(b => { if (b?.trim()) lines.push('• ' + b.trim()) })
      const lks = [p.githubUrl && 'GitHub: ' + p.githubUrl.replace(/^https?:\/\//, ''), p.liveUrl && 'Live: ' + p.liveUrl.replace(/^https?:\/\//, '')].filter(Boolean)
      if (lks.length) lines.push(lks.join(' | '))
      lines.push('')
    })
  }
  if (education?.length) {
    lines.push('EDUCATION')
    education.forEach(e => {
      const deg = [e.degree, e.field ? 'in ' + e.field : ''].filter(Boolean).join(' ')
      const yr = [e.startYear, e.endYear ? '– ' + e.endYear : ''].filter(Boolean).join(' ')
      lines.push([e.institution, deg, yr].filter(Boolean).join(' | '))
      if (e.gpa) lines.push('GPA: ' + e.gpa)
      lines.push('')
    })
  }
  if (skills) {
    const hasSkills = [...(skills.languages || []), ...(skills.frameworks || []), ...(skills.tools || []), ...(skills.other || [])].length > 0
    if (hasSkills) {
      lines.push('SKILLS')
      if (skills.languages?.length) lines.push('Languages: ' + skills.languages.join(', '))
      if (skills.frameworks?.length) lines.push('Frameworks: ' + skills.frameworks.join(', '))
      if (skills.tools?.length) lines.push('Tools: ' + skills.tools.join(', '))
      if (skills.other?.length) lines.push('Other: ' + skills.other.join(', '))
    }
  }
  return lines
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function StepBar({ current }) {
  const steps = [
    { n: 1, label: 'Goal & Role', icon: Target },
    { n: 2, label: 'Build Resume', icon: FileText },
    { n: 3, label: 'Review & Export', icon: Eye },
  ]
  return (
    <nav aria-label="Progress steps" className="flex items-center justify-center gap-0 mb-8">
      {steps.map((s, i) => {
        const done = current > s.n
        const active = current === s.n
        const Icon = s.icon
        return (
          <React.Fragment key={s.n}>
            <div className={`flex flex-col items-center gap-1.5 px-4 ${active ? 'opacity-100' : done ? 'opacity-70' : 'opacity-40'}`}>
              <div className={`w-9 h-9 rounded-full flex items-center justify-center text-sm font-semibold transition-colors
                ${active ? 'bg-accent text-white shadow-sm' : done ? 'bg-accent/20 text-accent' : 'bg-mist text-ink-soft'}`}>
                {done ? <CheckCircle size={18} /> : <Icon size={16} />}
              </div>
              <span className={`text-xs font-medium hidden sm:block ${active ? 'text-accent' : 'text-ink-soft'}`}>{s.label}</span>
            </div>
            {i < steps.length - 1 && (
              <div className={`h-px w-12 sm:w-20 ${done ? 'bg-accent/40' : 'bg-line'}`} aria-hidden="true" />
            )}
          </React.Fragment>
        )
      })}
    </nav>
  )
}

function TagInput({ label, id, tags, onChange, suggestions = [] }) {
  const [input, setInput] = useState('')
  const add = (val) => {
    const v = val.trim()
    if (v && !tags.includes(v)) onChange([...tags, v])
    setInput('')
  }
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink mb-1.5">{label}</label>
      <div className="min-h-[42px] flex flex-wrap gap-1.5 p-2 rounded-xl border border-line bg-surface focus-within:ring-2 focus-within:ring-accent focus-within:border-accent">
        {tags.map(t => (
          <span key={t} className="inline-flex items-center gap-1 bg-accent/10 text-accent text-xs font-medium px-2 py-0.5 rounded-full">
            {t}
            <button type="button" onClick={() => onChange(tags.filter(x => x !== t))} aria-label={`Remove ${t}`} className="hover:text-red-500">
              <X size={11} />
            </button>
          </span>
        ))}
        <input
          id={id}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); add(input) } if (e.key === 'Backspace' && !input && tags.length) onChange(tags.slice(0, -1)) }}
          onBlur={() => add(input)}
          placeholder={tags.length === 0 ? 'Type and press Enter' : ''}
          className="flex-1 min-w-[100px] text-sm bg-transparent outline-none text-ink placeholder:text-ink-soft"
        />
      </div>
      {suggestions.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {suggestions.filter(s => !tags.includes(s)).slice(0, 8).map(s => (
            <button key={s} type="button" onClick={() => onChange([...tags, s])}
              className="text-xs px-2 py-0.5 rounded-full border border-line text-ink-soft hover:border-accent hover:text-accent transition-colors">
              + {s}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function Field({ label, id, required, error, children }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink mb-1.5">
        {label}{required && <span className="text-red-500 ml-0.5" aria-hidden="true">*</span>}
      </label>
      {children}
      {error && <p role="alert" className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  )
}

function Input({ id, value, onChange, placeholder, type = 'text', required, autoFocus, disabled }) {
  return (
    <input id={id} type={type} value={value} onChange={e => onChange(e.target.value)}
      placeholder={placeholder} required={required} autoFocus={autoFocus} disabled={disabled}
      className="w-full px-3 py-2 rounded-xl border border-line bg-surface text-sm text-ink placeholder:text-ink-soft
        focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent transition-colors disabled:opacity-50" />
  )
}

function Textarea({ id, value, onChange, placeholder, rows = 3 }) {
  return (
    <textarea id={id} value={value} onChange={e => onChange(e.target.value)} rows={rows}
      placeholder={placeholder}
      className="w-full px-3 py-2 rounded-xl border border-line bg-surface text-sm text-ink placeholder:text-ink-soft
        focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent transition-colors resize-none" />
  )
}

// ─── Import Resume Modal ──────────────────────────────────────────────────────

function ImportModal({ onImport, onClose, importing }) {
  const [tab, setTab] = useState('upload')
  const [savedResumes, setSavedResumes] = useState([])
  const [loadingSaved, setLoadingSaved] = useState(false)
  const [dragging, setDragging] = useState(false)
  const fileRef = useRef(null)

  useEffect(() => {
    if (tab === 'saved') {
      setLoadingSaved(true)
      listResumes().then(setSavedResumes).catch(() => setSavedResumes([])).finally(() => setLoadingSaved(false))
    }
  }, [tab])

  const handleFile = (file) => {
    if (!file) return
    if (!file.name.match(/\.(pdf|docx|doc)$/i)) { alert('Please upload a PDF, DOCX, or DOC file.'); return }
    onImport(file)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={onClose}>
      <div className="bg-surface rounded-2xl border border-line shadow-xl w-full max-w-md" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between p-5 border-b border-line">
          <div>
            <h2 className="text-base font-semibold text-ink">Import from Resume</h2>
            <p className="text-xs text-ink-soft mt-0.5">Extract your skills, experience & education automatically</p>
          </div>
          <button onClick={onClose} className="text-ink-soft hover:text-ink transition-colors"><X size={18} /></button>
        </div>

        <div className="flex border-b border-line">
          {[['upload', 'Upload New'], ['saved', 'From Saved']].map(([key, label]) => (
            <button key={key} onClick={() => setTab(key)}
              className={`flex-1 py-2.5 text-sm font-medium transition-colors ${tab === key ? 'text-accent border-b-2 border-accent' : 'text-ink-soft hover:text-ink'}`}>
              {label}
            </button>
          ))}
        </div>

        <div className="p-5">
          {tab === 'upload' ? (
            <div
              onDragOver={e => { e.preventDefault(); setDragging(true) }}
              onDragLeave={() => setDragging(false)}
              onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }}
              onClick={() => fileRef.current?.click()}
              className={`flex flex-col items-center justify-center gap-3 p-8 rounded-xl border-2 border-dashed cursor-pointer transition-colors
                ${dragging ? 'border-accent bg-accent/5' : 'border-line hover:border-accent/40 hover:bg-mist/30'}`}>
              <input ref={fileRef} type="file" accept=".pdf,.docx,.doc" className="hidden"
                onChange={e => handleFile(e.target.files[0])} />
              {importing ? (
                <Loader2 size={24} className="animate-spin text-accent" />
              ) : (
                <Upload size={24} className="text-accent" />
              )}
              <div className="text-center">
                <p className="text-sm font-medium text-ink">{importing ? 'Importing…' : 'Drop your resume here'}</p>
                <p className="text-xs text-ink-soft mt-0.5">PDF, DOCX, or DOC • click to browse</p>
              </div>
            </div>
          ) : (
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {loadingSaved ? (
                <div className="flex items-center justify-center py-8"><Loader2 size={20} className="animate-spin text-accent" /></div>
              ) : savedResumes.length === 0 ? (
                <p className="text-sm text-ink-soft text-center py-8">No saved resumes found.<br />Upload one first.</p>
              ) : (
                savedResumes.map(r => (
                  <button key={r.id} onClick={() => onImport(null, r.id)} disabled={importing}
                    className="w-full flex items-center gap-3 p-3 rounded-xl border border-line hover:border-accent/40 hover:bg-mist/30 transition-colors text-left">
                    <FileText size={16} className="text-accent shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-ink truncate">{r.fileName}</p>
                      <p className="text-xs text-ink-soft">{r.uploadedAt ? new Date(r.uploadedAt).toLocaleDateString() : ''}</p>
                    </div>
                    {importing && <Loader2 size={14} className="animate-spin text-accent ml-auto" />}
                  </button>
                ))
              )}
            </div>
          )}
        </div>

        <div className="p-4 bg-amber-50 dark:bg-amber-950/20 rounded-b-2xl border-t border-amber-200/50">
          <p className="text-xs text-amber-700 dark:text-amber-400">
            <strong>Note:</strong> Links (GitHub, LinkedIn, portfolio) cannot be extracted from PDFs. A popup will let you add them manually after import.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Links Modal ──────────────────────────────────────────────────────────────

function LinksModal({ personalInfo, onSave, onClose }) {
  const [links, setLinks] = useState({
    linkedin: personalInfo.linkedin || '',
    github: personalInfo.github || '',
    leetcode: personalInfo.leetcode || '',
    website: personalInfo.website || '',
  })
  const set = (key, val) => setLinks(l => ({ ...l, [key]: val }))

  const linkFields = [
    { key: 'linkedin', label: 'LinkedIn', icon: Link2, placeholder: 'linkedin.com/in/yourname' },
    { key: 'github', label: 'GitHub', icon: Github, placeholder: 'github.com/yourname' },
    { key: 'leetcode', label: 'LeetCode / Codeforces', icon: Code2, placeholder: 'leetcode.com/yourname' },
    { key: 'website', label: 'Portfolio / Website', icon: Globe, placeholder: 'yourportfolio.com' },
  ]

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={onClose}>
      <div className="bg-surface rounded-2xl border border-line shadow-xl w-full max-w-sm" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between p-5 border-b border-line">
          <div>
            <h2 className="text-base font-semibold text-ink">Add Your Links</h2>
            <p className="text-xs text-ink-soft mt-0.5">These couldn't be extracted from the PDF</p>
          </div>
          <button onClick={onClose} className="text-ink-soft hover:text-ink transition-colors"><X size={18} /></button>
        </div>
        <div className="p-5 space-y-3">
          {linkFields.map(({ key, label, icon: Icon, placeholder }) => (
            <div key={key}>
              <label className="flex items-center gap-1.5 text-sm font-medium text-ink mb-1.5">
                <Icon size={14} className="text-accent" /> {label}
              </label>
              <input value={links[key]} onChange={e => set(key, e.target.value)} placeholder={placeholder}
                className="w-full px-3 py-2 rounded-xl border border-line bg-surface text-sm text-ink placeholder:text-ink-soft
                  focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent transition-colors" />
            </div>
          ))}
        </div>
        <div className="flex gap-2 p-5 pt-0">
          <button onClick={onClose}
            className="flex-1 py-2.5 rounded-xl border border-line text-sm text-ink-soft hover:text-ink hover:border-accent/40 transition-colors">
            Skip
          </button>
          <button onClick={() => onSave(links)}
            className="flex-1 py-2.5 rounded-xl bg-accent text-white text-sm font-medium hover:bg-accent/90 transition-colors">
            Save Links
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── AI Enhance Panel ─────────────────────────────────────────────────────────

function AiEnhancePanel({ result, form, onApplyBullets, onApplyProjectDesc, onAddKeywords, onClose }) {
  const { improvedExperience = [], improvedProjects = [], missingKeywords = [], sectionFeedback, overallScore } = result
  const [tab, setTab] = useState('bullets')

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={onClose}>
      <div className="bg-surface rounded-2xl border border-line shadow-xl w-full max-w-lg max-h-[80vh] flex flex-col" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-line">
          <div className="flex items-center gap-2">
            <Wand2 size={18} className="text-accent" />
            <h2 className="text-base font-semibold text-ink">AI Suggestions</h2>
          </div>
          {overallScore != null && (
            <div className="flex items-center gap-2">
              <span className="text-xs text-ink-soft">ATS Score</span>
              <span className={`text-sm font-bold ${overallScore >= 80 ? 'text-green-600' : overallScore >= 60 ? 'text-yellow-600' : 'text-red-500'}`}>
                {overallScore}/100
              </span>
            </div>
          )}
          <button onClick={onClose} className="text-ink-soft hover:text-ink ml-2 transition-colors"><X size={18} /></button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-line">
          {[['bullets', 'Bullets'], ['keywords', 'Keywords'], ['feedback', 'Feedback']].map(([key, label]) => (
            <button key={key} onClick={() => setTab(key)}
              className={`flex-1 py-2.5 text-sm font-medium transition-colors ${tab === key ? 'text-accent border-b-2 border-accent' : 'text-ink-soft hover:text-ink'}`}>
              {label}
              {key === 'keywords' && missingKeywords.length > 0 && (
                <span className="ml-1 text-xs bg-accent/10 text-accent px-1.5 py-0.5 rounded-full">{missingKeywords.length}</span>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {tab === 'bullets' && (
            <>
              {improvedExperience.length === 0 && improvedProjects.length === 0 ? (
                <p className="text-sm text-ink-soft text-center py-4">No bullet improvements suggested. Your bullets look good!</p>
              ) : null}
              {improvedExperience.map((item) => {
                const exp = form.experience[item.index]
                if (!exp) return null
                return (
                  <div key={item.index} className="p-3 rounded-xl border border-line bg-mist/20 space-y-2">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold text-ink">{exp.company || `Experience ${item.index + 1}`} — {exp.role}</p>
                      <button onClick={() => onApplyBullets(item.index, item.bullets)}
                        className="text-xs px-2.5 py-1 rounded-full bg-accent text-white font-medium hover:bg-accent/90 transition-colors">
                        Apply All
                      </button>
                    </div>
                    {item.bullets.map((b, bi) => (
                      <div key={bi} className="flex items-start gap-2">
                        <span className="text-accent text-xs mt-1">•</span>
                        <p className="text-xs text-ink leading-relaxed flex-1">{b}</p>
                      </div>
                    ))}
                  </div>
                )
              })}
              {improvedProjects.map((item) => {
                const proj = form.projects[item.index]
                if (!proj) return null
                return (
                  <div key={`proj-${item.index}`} className="p-3 rounded-xl border border-line bg-mist/20 space-y-2">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold text-ink">{proj.name || `Project ${item.index + 1}`}</p>
                      <button onClick={() => onApplyProjectDesc(item.index, item.description)}
                        className="text-xs px-2.5 py-1 rounded-full bg-accent text-white font-medium hover:bg-accent/90 transition-colors">
                        Apply All
                      </button>
                    </div>
                    {item.description.map((b, bi) => (
                      <div key={bi} className="flex items-start gap-2">
                        <span className="text-accent text-xs mt-1">•</span>
                        <p className="text-xs text-ink leading-relaxed flex-1">{b}</p>
                      </div>
                    ))}
                  </div>
                )
              })}
            </>
          )}

          {tab === 'keywords' && (
            <div className="space-y-3">
              <p className="text-sm text-ink-soft">These keywords are relevant to <strong className="text-ink">{form.targetRole}</strong> and appear to be missing from your resume:</p>
              <div className="flex flex-wrap gap-2">
                {missingKeywords.map(k => (
                  <span key={k} className="inline-flex items-center gap-1 bg-amber-50 dark:bg-amber-950/30 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800 text-xs font-medium px-2.5 py-1 rounded-full">
                    {k}
                  </span>
                ))}
              </div>
              {missingKeywords.length > 0 && (
                <button onClick={() => onAddKeywords(missingKeywords)}
                  className="w-full py-2 rounded-xl bg-accent text-white text-sm font-medium hover:bg-accent/90 transition-colors">
                  Add All to Skills (Other)
                </button>
              )}
            </div>
          )}

          {tab === 'feedback' && (
            <div className="space-y-3">
              {sectionFeedback && (
                <div className="p-3 rounded-xl bg-blue-50 dark:bg-blue-950/20 border border-blue-200/50">
                  <p className="text-sm text-blue-700 dark:text-blue-400">{sectionFeedback}</p>
                </div>
              )}
              {overallScore != null && (
                <div className="p-3 rounded-xl border border-line">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm font-medium text-ink">ATS Readiness Score</p>
                    <p className={`text-sm font-bold ${overallScore >= 80 ? 'text-green-600' : overallScore >= 60 ? 'text-yellow-600' : 'text-red-500'}`}>
                      {overallScore}/100
                    </p>
                  </div>
                  <div className="h-2 rounded-full bg-mist overflow-hidden">
                    <div className={`h-full rounded-full transition-all ${overallScore >= 80 ? 'bg-green-500' : overallScore >= 60 ? 'bg-yellow-500' : 'bg-red-500'}`}
                      style={{ width: overallScore + '%' }} />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Step 1: Goal & Role ──────────────────────────────────────────────────────

function Step1({ form, setForm, onNext }) {
  const [query, setQuery] = useState(form.targetRole || '')
  const matches = query.length > 0
    ? ALL_ROLES.filter(r => r.toLowerCase().includes(query.toLowerCase()))
    : ALL_ROLES

  return (
    <div className="max-w-xl mx-auto space-y-8">
      <div>
        <h2 className="text-lg font-semibold text-ink mb-1">What's your goal?</h2>
        <p className="text-sm text-ink-soft mb-4">This helps us tailor the suggestions for you.</p>
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Career goal">
          {GOALS.map(g => (
            <button key={g} type="button" role="radio" aria-checked={form.goal === g}
              onClick={() => setForm(f => ({ ...f, goal: g }))}
              className={`p-3 rounded-xl border text-sm text-left transition-all ${form.goal === g
                ? 'border-accent bg-accent/8 text-accent font-medium ring-2 ring-accent/30'
                : 'border-line hover:border-accent/40 text-ink'}`}>
              {g}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label htmlFor="role-search" className="block text-sm font-semibold text-ink mb-1">
          Target role <span className="text-red-500" aria-hidden="true">*</span>
        </label>
        <p className="text-xs text-ink-soft mb-3">Pick a role to get smart suggestions as you build.</p>
        <Input id="role-search" value={query} onChange={v => { setQuery(v); setForm(f => ({ ...f, targetRole: v })) }}
          placeholder="e.g. Software Engineer, Data Scientist…" autoFocus />
        {matches.length > 0 && (
          <div className="mt-2 grid grid-cols-2 gap-1.5">
            {matches.map(r => (
              <button key={r} type="button" onClick={() => { setQuery(r); setForm(f => ({ ...f, targetRole: r })) }}
                className={`px-3 py-2 rounded-lg border text-sm text-left transition-all ${form.targetRole === r
                  ? 'border-accent bg-accent/8 text-accent font-medium'
                  : 'border-line hover:border-accent/40 text-ink'}`}>
                {r}
              </button>
            ))}
          </div>
        )}
      </div>

      <button onClick={onNext} disabled={!form.targetRole}
        className="w-full flex items-center justify-center gap-2 py-3 rounded-xl bg-accent text-white font-medium text-sm
          disabled:opacity-40 disabled:cursor-not-allowed hover:bg-accent/90 transition-colors focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2">
        Continue <ArrowRight size={16} />
      </button>
    </div>
  )
}

// ─── Step 2: Build Resume ─────────────────────────────────────────────────────

function Step2({ form, setForm, suggestions, onNext, onBack }) {
  const pi = form.personalInfo
  const setPI = (key, val) => setForm(f => ({ ...f, personalInfo: { ...f.personalInfo, [key]: val } }))
  const setSkills = (key, val) => setForm(f => ({ ...f, skills: { ...f.skills, [key]: val } }))

  // Experience
  const addExp = () => setForm(f => ({ ...f, experience: [...f.experience, emptyExp()] }))
  const removeExp = i => setForm(f => ({ ...f, experience: f.experience.filter((_, idx) => idx !== i) }))
  const setExp = (i, key, val) => setForm(f => ({
    ...f, experience: f.experience.map((e, idx) => idx === i ? { ...e, [key]: val } : e)
  }))
  const setBullets = (i, bullets) => setExp(i, 'bullets', bullets)

  // Projects
  const addProj = () => setForm(f => ({ ...f, projects: [...f.projects, emptyProject()] }))
  const removeProj = i => setForm(f => ({ ...f, projects: f.projects.filter((_, idx) => idx !== i) }))
  const setProj = (i, key, val) => setForm(f => ({
    ...f, projects: f.projects.map((p, idx) => idx === i ? { ...p, [key]: val } : p)
  }))
  const setProjBullets = (i, desc) => setProj(i, 'description', desc)

  // Education
  const addEdu = () => setForm(f => ({ ...f, education: [...f.education, emptyEdu()] }))
  const removeEdu = i => setForm(f => ({ ...f, education: f.education.filter((_, idx) => idx !== i) }))
  const setEdu = (i, key, val) => setForm(f => ({
    ...f, education: f.education.map((e, idx) => idx === i ? { ...e, [key]: val } : e)
  }))

  return (
    <div className="space-y-8">
      {/* Personal Info */}
      <section aria-labelledby="sec-personal">
        <h2 id="sec-personal" className="flex items-center gap-2 text-base font-semibold text-ink mb-4">
          <User size={16} className="text-accent" /> Personal Info
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="Full name" id="pi-name" required>
            <Input id="pi-name" value={pi.name} onChange={v => setPI('name', v)} placeholder="Jane Doe" required autoFocus />
          </Field>
          <Field label="Email" id="pi-email" required>
            <Input id="pi-email" type="email" value={pi.email} onChange={v => setPI('email', v)} placeholder="jane@example.com" required />
          </Field>
          <Field label="Phone" id="pi-phone">
            <Input id="pi-phone" value={pi.phone} onChange={v => setPI('phone', v)} placeholder="+1 (555) 123-4567" />
          </Field>
          <Field label="Location" id="pi-location">
            <Input id="pi-location" value={pi.location} onChange={v => setPI('location', v)} placeholder="New York, NY" />
          </Field>
          <Field label="LinkedIn URL" id="pi-linkedin">
            <Input id="pi-linkedin" value={pi.linkedin} onChange={v => setPI('linkedin', v)} placeholder="linkedin.com/in/janedoe" />
          </Field>
          <Field label="GitHub URL" id="pi-github">
            <Input id="pi-github" value={pi.github} onChange={v => setPI('github', v)} placeholder="github.com/janedoe" />
          </Field>
          <Field label="LeetCode / Codeforces" id="pi-leetcode">
            <Input id="pi-leetcode" value={pi.leetcode} onChange={v => setPI('leetcode', v)} placeholder="leetcode.com/janedoe" />
          </Field>
          <Field label="Portfolio / Website" id="pi-website">
            <Input id="pi-website" value={pi.website} onChange={v => setPI('website', v)} placeholder="janedoe.dev" />
          </Field>
        </div>
      </section>

      {/* Summary */}
      <section aria-labelledby="sec-summary">
        <h2 id="sec-summary" className="flex items-center gap-2 text-base font-semibold text-ink mb-1">
          <Sparkles size={16} className="text-accent" /> Summary <span className="text-xs font-normal text-ink-soft">(optional)</span>
        </h2>
        <p className="text-xs text-ink-soft mb-3">2–3 sentences about who you are and what you bring.</p>
        <Textarea id="summary" value={form.summary} onChange={v => setForm(f => ({ ...f, summary: v }))}
          placeholder={`Passionate ${form.targetRole || 'engineer'} with X years of experience building…`} rows={3} />
      </section>

      {/* Experience */}
      <section aria-labelledby="sec-exp">
        <div className="flex items-center justify-between mb-3">
          <h2 id="sec-exp" className="flex items-center gap-2 text-base font-semibold text-ink">
            <Briefcase size={16} className="text-accent" /> Experience
          </h2>
          <button type="button" onClick={addExp}
            className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent/80 transition-colors focus:outline-none focus:underline">
            <Plus size={14} /> Add entry
          </button>
        </div>
        {form.experience.length === 0 && (
          <p className="text-sm text-ink-soft text-center py-6 border border-dashed border-line rounded-xl">
            No experience entries yet. <button type="button" onClick={addExp} className="text-accent underline">Add your first one.</button>
          </p>
        )}
        <div className="space-y-4">
          {form.experience.map((e, i) => (
            <div key={i} className="p-4 rounded-xl border border-line bg-surface/60 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-ink-soft uppercase tracking-wide">Entry {i + 1}</span>
                <button type="button" onClick={() => removeExp(i)} aria-label={`Remove experience entry ${i + 1}`}
                  className="text-ink-soft hover:text-red-500 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Field label="Company" id={`exp-co-${i}`}>
                  <Input id={`exp-co-${i}`} value={e.company} onChange={v => setExp(i, 'company', v)} placeholder="Google" />
                </Field>
                <Field label="Role / Title" id={`exp-role-${i}`}>
                  <Input id={`exp-role-${i}`} value={e.role} onChange={v => setExp(i, 'role', v)} placeholder="Software Engineer" />
                </Field>
                <Field label="Start date" id={`exp-start-${i}`}>
                  <Input id={`exp-start-${i}`} value={e.startDate} onChange={v => setExp(i, 'startDate', v)} placeholder="Jan 2022" />
                </Field>
                <div>
                  <Field label="End date" id={`exp-end-${i}`}>
                    <Input id={`exp-end-${i}`} value={e.endDate} onChange={v => setExp(i, 'endDate', v)}
                      placeholder="Dec 2024" disabled={e.current} />
                  </Field>
                  <label className="mt-1.5 flex items-center gap-1.5 text-xs text-ink-soft cursor-pointer">
                    <input type="checkbox" checked={e.current} onChange={ev => setExp(i, 'current', ev.target.checked)}
                      className="rounded border-line accent-accent" />
                    Currently working here
                  </label>
                </div>
              </div>
              <div>
                <p className="text-xs font-medium text-ink mb-1.5">Bullet points (achievements & impact)</p>
                {e.bullets.map((b, bi) => (
                  <div key={bi} className="flex items-start gap-2 mb-1.5">
                    <span className="mt-2.5 text-accent text-xs">•</span>
                    <input value={b} onChange={ev => {
                      const next = [...e.bullets]; next[bi] = ev.target.value; setBullets(i, next)
                    }} onKeyDown={ev => {
                      if (ev.key === 'Enter') { ev.preventDefault(); setBullets(i, [...e.bullets.slice(0, bi + 1), '', ...e.bullets.slice(bi + 1)]) }
                      if (ev.key === 'Backspace' && !b && e.bullets.length > 1) { ev.preventDefault(); setBullets(i, e.bullets.filter((_, x) => x !== bi)) }
                    }}
                    placeholder="Quantify impact: e.g. Shipped X feature used by Y users"
                    className="flex-1 px-2.5 py-1.5 text-sm rounded-lg border border-line bg-surface text-ink placeholder:text-ink-soft
                      focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
                    {e.bullets.length > 1 && (
                      <button type="button" onClick={() => setBullets(i, e.bullets.filter((_, x) => x !== bi))}
                        aria-label="Remove bullet" className="mt-1.5 text-ink-soft hover:text-red-500 shrink-0">
                        <X size={13} />
                      </button>
                    )}
                  </div>
                ))}
                <button type="button" onClick={() => setBullets(i, [...e.bullets, ''])}
                  className="text-xs text-accent hover:underline mt-0.5 focus:outline-none focus:underline">
                  + Add bullet
                </button>
              </div>
              {suggestions?.bullets?.length > 0 && (
                <div>
                  <p className="text-xs text-ink-soft mb-1.5 flex items-center gap-1"><Sparkles size={11} /> Suggestions for {form.targetRole}</p>
                  <div className="flex flex-wrap gap-1.5">
                    {suggestions.bullets.map((s, si) => (
                      <button key={si} type="button"
                        onClick={() => setBullets(i, [...e.bullets.filter(b => b), s])}
                        className="text-xs px-2.5 py-1 rounded-full border border-line text-ink-soft hover:border-accent hover:text-accent transition-colors text-left">
                        {s.length > 60 ? s.slice(0, 60) + '…' : s}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Projects */}
      <section aria-labelledby="sec-proj">
        <div className="flex items-center justify-between mb-3">
          <h2 id="sec-proj" className="flex items-center gap-2 text-base font-semibold text-ink">
            <FolderGit2 size={16} className="text-accent" /> Projects
          </h2>
          <button type="button" onClick={addProj}
            className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent/80 transition-colors focus:outline-none focus:underline">
            <Plus size={14} /> Add project
          </button>
        </div>
        {form.projects.length === 0 && (
          <p className="text-sm text-ink-soft text-center py-6 border border-dashed border-line rounded-xl">
            No projects yet. <button type="button" onClick={addProj} className="text-accent underline">Add your first project.</button>
          </p>
        )}
        <div className="space-y-4">
          {form.projects.map((p, i) => (
            <div key={i} className="p-4 rounded-xl border border-line bg-surface/60 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-ink-soft uppercase tracking-wide">Project {i + 1}</span>
                <button type="button" onClick={() => removeProj(i)} aria-label={`Remove project ${i + 1}`}
                  className="text-ink-soft hover:text-red-500 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Field label="Project name" id={`proj-name-${i}`}>
                  <Input id={`proj-name-${i}`} value={p.name} onChange={v => setProj(i, 'name', v)} placeholder="Smart Job Tracker" />
                </Field>
                <Field label="Date / Duration" id={`proj-date-${i}`}>
                  <Input id={`proj-date-${i}`} value={p.date} onChange={v => setProj(i, 'date', v)} placeholder="Jan 2024 – Mar 2024" />
                </Field>
                <Field label="GitHub URL" id={`proj-gh-${i}`}>
                  <Input id={`proj-gh-${i}`} value={p.githubUrl} onChange={v => setProj(i, 'githubUrl', v)} placeholder="github.com/you/project" />
                </Field>
                <Field label="Live Demo URL" id={`proj-live-${i}`}>
                  <Input id={`proj-live-${i}`} value={p.liveUrl} onChange={v => setProj(i, 'liveUrl', v)} placeholder="myproject.vercel.app" />
                </Field>
              </div>
              <TagInput label="Tech Stack" id={`proj-tech-${i}`} tags={p.techStack}
                onChange={v => setProj(i, 'techStack', v)} />
              <div>
                <p className="text-xs font-medium text-ink mb-1.5">Description (bullet points)</p>
                {p.description.map((b, bi) => (
                  <div key={bi} className="flex items-start gap-2 mb-1.5">
                    <span className="mt-2.5 text-accent text-xs">•</span>
                    <input value={b} onChange={ev => {
                      const next = [...p.description]; next[bi] = ev.target.value; setProjBullets(i, next)
                    }} onKeyDown={ev => {
                      if (ev.key === 'Enter') { ev.preventDefault(); setProjBullets(i, [...p.description.slice(0, bi + 1), '', ...p.description.slice(bi + 1)]) }
                      if (ev.key === 'Backspace' && !b && p.description.length > 1) { ev.preventDefault(); setProjBullets(i, p.description.filter((_, x) => x !== bi)) }
                    }}
                    placeholder="Built a full-stack web app with X feature used by Y users"
                    className="flex-1 px-2.5 py-1.5 text-sm rounded-lg border border-line bg-surface text-ink placeholder:text-ink-soft
                      focus:outline-none focus:ring-2 focus:ring-accent focus:border-accent" />
                    {p.description.length > 1 && (
                      <button type="button" onClick={() => setProjBullets(i, p.description.filter((_, x) => x !== bi))}
                        aria-label="Remove bullet" className="mt-1.5 text-ink-soft hover:text-red-500 shrink-0">
                        <X size={13} />
                      </button>
                    )}
                  </div>
                ))}
                <button type="button" onClick={() => setProjBullets(i, [...p.description, ''])}
                  className="text-xs text-accent hover:underline mt-0.5">
                  + Add bullet
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Education */}
      <section aria-labelledby="sec-edu">
        <div className="flex items-center justify-between mb-3">
          <h2 id="sec-edu" className="flex items-center gap-2 text-base font-semibold text-ink">
            <GraduationCap size={16} className="text-accent" /> Education
          </h2>
          <button type="button" onClick={addEdu}
            className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent/80 transition-colors focus:outline-none focus:underline">
            <Plus size={14} /> Add entry
          </button>
        </div>
        {form.education.length === 0 && (
          <p className="text-sm text-ink-soft text-center py-6 border border-dashed border-line rounded-xl">
            No education entries. <button type="button" onClick={addEdu} className="text-accent underline">Add one.</button>
          </p>
        )}
        <div className="space-y-3">
          {form.education.map((e, i) => (
            <div key={i} className="p-4 rounded-xl border border-line bg-surface/60">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-medium text-ink-soft uppercase tracking-wide">Entry {i + 1}</span>
                <button type="button" onClick={() => removeEdu(i)} aria-label={`Remove education entry ${i + 1}`}
                  className="text-ink-soft hover:text-red-500 transition-colors"><Trash2 size={14} /></button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Field label="Institution" id={`edu-inst-${i}`}>
                  <Input id={`edu-inst-${i}`} value={e.institution} onChange={v => setEdu(i, 'institution', v)} placeholder="MIT" />
                </Field>
                <Field label="Degree" id={`edu-deg-${i}`}>
                  <Input id={`edu-deg-${i}`} value={e.degree} onChange={v => setEdu(i, 'degree', v)} placeholder="Bachelor of Science" />
                </Field>
                <Field label="Field of study" id={`edu-field-${i}`}>
                  <Input id={`edu-field-${i}`} value={e.field} onChange={v => setEdu(i, 'field', v)} placeholder="Computer Science" />
                </Field>
                <Field label="GPA" id={`edu-gpa-${i}`}>
                  <Input id={`edu-gpa-${i}`} value={e.gpa} onChange={v => setEdu(i, 'gpa', v)} placeholder="3.8/4.0" />
                </Field>
                <Field label="Start year" id={`edu-sy-${i}`}>
                  <Input id={`edu-sy-${i}`} value={e.startYear} onChange={v => setEdu(i, 'startYear', v)} placeholder="2019" />
                </Field>
                <Field label="End year" id={`edu-ey-${i}`}>
                  <Input id={`edu-ey-${i}`} value={e.endYear} onChange={v => setEdu(i, 'endYear', v)} placeholder="2023" />
                </Field>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Skills */}
      <section aria-labelledby="sec-skills">
        <h2 id="sec-skills" className="flex items-center gap-2 text-base font-semibold text-ink mb-4">
          <Wrench size={16} className="text-accent" /> Skills
        </h2>
        <div className="space-y-3">
          <TagInput label="Programming Languages" id="sk-lang" tags={form.skills.languages}
            onChange={v => setSkills('languages', v)} suggestions={suggestions?.skills?.languages || []} />
          <TagInput label="Frameworks & Libraries" id="sk-fw" tags={form.skills.frameworks}
            onChange={v => setSkills('frameworks', v)} suggestions={suggestions?.skills?.frameworks || []} />
          <TagInput label="Tools & Platforms" id="sk-tools" tags={form.skills.tools}
            onChange={v => setSkills('tools', v)} suggestions={suggestions?.skills?.tools || []} />
          <TagInput label="Other" id="sk-other" tags={form.skills.other}
            onChange={v => setSkills('other', v)} suggestions={[]} />
        </div>
      </section>

      <div className="flex gap-3 pt-2">
        <button type="button" onClick={onBack}
          className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-line text-sm text-ink-soft hover:text-ink hover:border-accent/40 transition-colors">
          <ChevronLeft size={15} /> Back
        </button>
        <button type="button" onClick={onNext}
          className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-accent text-white font-medium text-sm hover:bg-accent/90 transition-colors">
          Review & Export <ChevronRight size={15} />
        </button>
      </div>
    </div>
  )
}

// ─── Step 3: Review & Export ──────────────────────────────────────────────────

function Step3({ form, onBack, onExport, onExportLatex, onEnhance, exporting, exportingLatex, enhancing, exportDone }) {
  const pi = form.personalInfo
  const checks = [
    { ok: !!pi.name?.trim(), label: 'Full name', req: true },
    { ok: !!pi.email?.trim(), label: 'Email address', req: true },
    { ok: !!pi.phone?.trim(), label: 'Phone number', req: false },
    { ok: !!pi.location?.trim(), label: 'Location', req: false },
    { ok: !!pi.linkedin?.trim() || !!pi.github?.trim(), label: 'LinkedIn or GitHub URL', req: false },
    { ok: form.experience.length > 0 && form.experience.some(e => e.company || e.role), label: 'At least one experience entry', req: true },
    { ok: form.education.length > 0 && form.education.some(e => e.institution), label: 'At least one education entry', req: true },
    { ok: (form.skills.languages.length + form.skills.frameworks.length + form.skills.tools.length) >= 3, label: '3 or more skills listed', req: false },
    { ok: form.experience.some(e => e.bullets.some(b => b?.trim())), label: 'Bullet points for experience', req: false },
    { ok: form.projects.length > 0, label: 'At least one project', req: false },
  ]
  const missing = checks.filter(c => !c.ok && c.req)
  const ready = missing.length === 0
  const score = Math.round((checks.filter(c => c.ok).length / checks.length) * 100)
  const previewLines = buildPreviewText(form)

  return (
    <div className="space-y-6">
      {/* Readiness score */}
      <div className="p-4 rounded-xl border border-line bg-surface/60">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-ink">Resume Readiness</h2>
          <span className={`text-sm font-bold ${score >= 80 ? 'text-green-600' : score >= 50 ? 'text-yellow-600' : 'text-red-500'}`}>
            {score}%
          </span>
        </div>
        <div className="h-2 rounded-full bg-mist overflow-hidden">
          <div className="h-full rounded-full bg-accent transition-all duration-500" style={{ width: score + '%' }} />
        </div>
      </div>

      {/* Checklist */}
      <div>
        <h3 className="text-sm font-semibold text-ink mb-3">Checklist</h3>
        <ul className="space-y-2" role="list">
          {checks.map((c, i) => (
            <li key={i} className={`flex items-center gap-2 text-sm ${c.ok ? 'text-ink' : c.req ? 'text-red-500' : 'text-ink-soft'}`}>
              {c.ok
                ? <CheckCircle size={15} className="text-green-500 shrink-0" />
                : <AlertCircle size={15} className={`shrink-0 ${c.req ? 'text-red-400' : 'text-ink-soft'}`} />}
              {c.label}{c.req && !c.ok ? ' (required)' : ''}
            </li>
          ))}
        </ul>
      </div>

      {/* AI Enhance button */}
      <button type="button" onClick={onEnhance} disabled={enhancing || !ready}
        className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl border border-accent/40 text-accent text-sm font-medium
          hover:bg-accent/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
        {enhancing ? <Loader2 size={15} className="animate-spin" /> : <Wand2 size={15} />}
        {enhancing ? 'Analyzing with AI…' : 'Enhance with AI (Gemini)'}
      </button>

      {/* Live text preview */}
      <div>
        <h3 className="text-sm font-semibold text-ink mb-2">Preview</h3>
        <div className="border border-line rounded-xl bg-surface p-4 font-mono text-xs text-ink-soft leading-relaxed max-h-72 overflow-y-auto whitespace-pre-wrap">
          {previewLines.join('\n') || 'Fill in some details to see your resume preview…'}
        </div>
      </div>

      {exportDone && (
        <div role="alert" className="flex items-center gap-2 p-3 rounded-xl bg-green-50 border border-green-200 text-green-700 text-sm">
          <CheckCircle size={16} /> Resume saved to your account and downloaded!
        </div>
      )}

      <div className="flex gap-3">
        <button type="button" onClick={onBack}
          className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-line text-sm text-ink-soft hover:text-ink hover:border-accent/40 transition-colors">
          <ChevronLeft size={15} /> Back
        </button>
        <button type="button" onClick={onExport} disabled={!ready || exporting}
          className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-accent text-white font-medium text-sm
            disabled:opacity-50 disabled:cursor-not-allowed hover:bg-accent/90 transition-colors">
          {exporting ? <><Loader2 size={15} className="animate-spin" /> Generating…</> : <><Download size={15} /> Export PDF & Save</>}
        </button>
        <button type="button" onClick={onExportLatex} disabled={!ready || exportingLatex}
          title="Download .tex file for Overleaf"
          className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl border border-line text-sm text-ink-soft hover:text-ink hover:border-accent/40 transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
          {exportingLatex ? <Loader2 size={15} className="animate-spin" /> : <BookOpen size={15} />}
          <span className="hidden sm:inline">.tex</span>
        </button>
      </div>
      {!ready && (
        <p role="alert" className="text-xs text-red-500 text-center">
          Please fill in the required fields ({missing.map(c => c.label).join(', ')}) before exporting.
        </p>
      )}
      <p className="text-xs text-ink-soft text-center">
        The <strong>.tex</strong> button downloads a LaTeX source file you can upload directly to{' '}
        <span className="text-accent">Overleaf</span> for professional typesetting.
      </p>
    </div>
  )
}

// ─── Main page ─────────────────────────────────────────────────────────────────

export default function ResumeBuilder() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [exporting, setExporting] = useState(false)
  const [exportingLatex, setExportingLatex] = useState(false)
  const [enhancing, setEnhancing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [exportDone, setExportDone] = useState(false)
  const [showImportModal, setShowImportModal] = useState(false)
  const [showLinksModal, setShowLinksModal] = useState(false)
  const [enhancements, setEnhancements] = useState(null)

  const [form, setForm] = useState({
    goal: '',
    targetRole: '',
    personalInfo: { name: '', email: '', phone: '', location: '', linkedin: '', github: '', website: '', leetcode: '' },
    summary: '',
    experience: [],
    projects: [],
    education: [],
    skills: emptySkills(),
  })
  const topRef = useRef(null)

  useEffect(() => {
    getPrefill()
      .then(data => {
        setForm(f => ({
          ...f,
          personalInfo: { ...f.personalInfo, ...(data.personalInfo || {}) },
          skills: {
            languages: data.skills?.languages || [],
            frameworks: data.skills?.frameworks || [],
            tools: data.skills?.tools || [],
            other: [],
          },
        }))
      })
      .catch(() => {})
  }, [])

  const goTo = (n) => {
    setStep(n)
    topRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const suggestions = ROLE_DATA[form.targetRole] || null

  const applyImportedData = (data) => {
    setForm(f => ({
      ...f,
      skills: {
        languages: data.skills?.languages?.length ? data.skills.languages : f.skills.languages,
        frameworks: data.skills?.frameworks?.length ? data.skills.frameworks : f.skills.frameworks,
        tools: data.skills?.tools?.length ? data.skills.tools : f.skills.tools,
        other: f.skills.other,
      },
      experience: data.experience?.length ? data.experience.map(e => ({
        company: e.company || '',
        role: e.role || '',
        startDate: e.startDate || '',
        endDate: e.endDate || '',
        current: !!e.current,
        bullets: Array.isArray(e.bullets) && e.bullets.length > 0 ? e.bullets : [''],
      })) : f.experience,
      education: data.education?.length ? data.education.map(e => ({
        institution: e.institution || '',
        degree: e.degree || '',
        field: e.field || '',
        startYear: e.startYear || '',
        endYear: e.endYear || '',
        gpa: e.gpa || '',
      })) : f.education,
    }))
  }

  const handleImport = async (file, resumeId) => {
    setImporting(true)
    try {
      let rId = resumeId
      if (file) {
        const uploaded = await uploadResume(file)
        rId = uploaded.id
      }
      const data = await importResume(rId)
      applyImportedData(data)
      setShowImportModal(false)
      setShowLinksModal(true)
    } catch (err) {
      alert('Import failed: ' + (err.response?.data?.error || err.message))
    } finally {
      setImporting(false)
    }
  }

  const handleSaveLinks = (links) => {
    setForm(f => ({ ...f, personalInfo: { ...f.personalInfo, ...links } }))
    setShowLinksModal(false)
  }

  const handleExport = async () => {
    setExporting(true)
    setExportDone(false)
    try {
      const { blob } = await exportResume({
        goal: form.goal,
        targetRole: form.targetRole,
        personalInfo: form.personalInfo,
        summary: form.summary,
        experience: form.experience,
        projects: form.projects,
        education: form.education,
        skills: form.skills,
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${form.targetRole?.toLowerCase().replace(/\s+/g, '-') || 'resume'}-resume.pdf`
      a.click()
      URL.revokeObjectURL(url)
      setExportDone(true)
    } catch (err) {
      alert('Export failed. Please try again.')
    } finally {
      setExporting(false)
    }
  }

  const handleExportLatex = async () => {
    setExportingLatex(true)
    try {
      const blob = await exportLatex({
        goal: form.goal,
        targetRole: form.targetRole,
        personalInfo: form.personalInfo,
        summary: form.summary,
        experience: form.experience,
        projects: form.projects,
        education: form.education,
        skills: form.skills,
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${form.targetRole?.toLowerCase().replace(/\s+/g, '-') || 'resume'}-resume.tex`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      alert('LaTeX export failed. Please try again.')
    } finally {
      setExportingLatex(false)
    }
  }

  const handleEnhance = async () => {
    setEnhancing(true)
    try {
      const result = await aiEnhanceResume({
        goal: form.goal,
        targetRole: form.targetRole,
        personalInfo: form.personalInfo,
        summary: form.summary,
        experience: form.experience,
        projects: form.projects,
        education: form.education,
        skills: form.skills,
      })
      setEnhancements(result)
    } catch (err) {
      alert('AI enhancement unavailable: ' + (err.response?.data?.error || 'AI is not configured on this server.'))
    } finally {
      setEnhancing(false)
    }
  }

  const applyBullets = (expIdx, bullets) => {
    setForm(f => ({
      ...f,
      experience: f.experience.map((e, i) => i === expIdx ? { ...e, bullets } : e),
    }))
  }

  const applyProjectDesc = (projIdx, description) => {
    setForm(f => ({
      ...f,
      projects: f.projects.map((p, i) => i === projIdx ? { ...p, description } : p),
    }))
  }

  const addKeywordsToSkills = (keywords) => {
    setForm(f => ({
      ...f,
      skills: { ...f.skills, other: [...new Set([...(f.skills.other || []), ...keywords])] },
    }))
  }

  const buildDto = () => ({
    goal: form.goal, targetRole: form.targetRole,
    personalInfo: form.personalInfo, summary: form.summary,
    experience: form.experience, projects: form.projects,
    education: form.education, skills: form.skills,
  })

  return (
    <Layout title="Resume Builder" subtitle="ATS-ready resume in minutes — with AI enhance and Overleaf export.">
      <div className="max-w-2xl mx-auto" ref={topRef}>

        {/* Import banner (only on steps 1–2) */}
        {step <= 2 && (
          <div className="mb-5 flex items-center justify-between gap-3 p-3 rounded-xl border border-line bg-surface/60">
            <div>
              <p className="text-sm font-medium text-ink">Already have a resume?</p>
              <p className="text-xs text-ink-soft">Import it to auto-fill skills & experience.</p>
            </div>
            <button onClick={() => setShowImportModal(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-accent/10 text-accent text-sm font-medium hover:bg-accent/20 transition-colors shrink-0">
              <Upload size={14} /> Import
            </button>
          </div>
        )}

        <StepBar current={step} />

        <div className="bg-surface border border-line rounded-2xl p-6 shadow-sm">
          {step === 1 && <Step1 form={form} setForm={setForm} onNext={() => goTo(2)} />}
          {step === 2 && (
            <Step2
              form={form}
              setForm={setForm}
              suggestions={suggestions}
              onNext={() => goTo(3)}
              onBack={() => goTo(1)}
            />
          )}
          {step === 3 && (
            <Step3
              form={form}
              onBack={() => goTo(2)}
              onExport={handleExport}
              onExportLatex={handleExportLatex}
              onEnhance={handleEnhance}
              exporting={exporting}
              exportingLatex={exportingLatex}
              enhancing={enhancing}
              exportDone={exportDone}
            />
          )}
        </div>
      </div>

      {/* Modals */}
      {showImportModal && (
        <ImportModal
          onImport={handleImport}
          onClose={() => setShowImportModal(false)}
          importing={importing}
        />
      )}
      {showLinksModal && (
        <LinksModal
          personalInfo={form.personalInfo}
          onSave={handleSaveLinks}
          onClose={() => setShowLinksModal(false)}
        />
      )}
      {enhancements && (
        <AiEnhancePanel
          result={enhancements}
          form={form}
          onApplyBullets={applyBullets}
          onApplyProjectDesc={applyProjectDesc}
          onAddKeywords={addKeywordsToSkills}
          onClose={() => setEnhancements(null)}
        />
      )}
    </Layout>
  )
}
