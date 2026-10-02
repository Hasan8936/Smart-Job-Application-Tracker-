import React, { useEffect, useRef, useState } from 'react'
import { Briefcase, Check, Loader2, MapPin, Plus, X } from 'lucide-react'
import { saveJobPreferences, skipJobPreferences } from '../api/jobPreferences'

const ROLE_SUGGESTIONS = [
  'Software Engineer', 'Data Analyst', 'QA Engineer', 'Business Analyst', 'Customer Care Executive',
  'Cyber Security Analyst', 'Mechanical Engineer', 'Civil Engineer', 'Electrical Engineer', 'HR Recruiter',
  'Sales Executive', 'Digital Marketing', 'Accountant', 'DevOps Engineer', 'Graphic Designer',
]
const EXPERIENCE = [
  { value: 'FRESHER', label: 'Fresher' }, { value: 'JUNIOR', label: '1–3 yrs' },
  { value: 'MID', label: '3–5 yrs' }, { value: 'SENIOR', label: '5+ yrs' },
]
const CITIES = ['Anywhere in India', 'Bangalore', 'Hyderabad', 'Pune', 'Chennai', 'Mumbai', 'Delhi', 'Noida', 'Gurgaon', 'Kolkata', 'Ahmedabad']
const WORK_MODES = [{ value: 'ONSITE', label: 'On-site' }, { value: 'HYBRID', label: 'Hybrid' }, { value: 'REMOTE', label: 'Remote' }]
const JOB_TYPES = [
  { value: 'FULL_TIME', label: 'Full-time' }, { value: 'INTERNSHIP', label: 'Internship' },
  { value: 'CONTRACT', label: 'Contract' }, { value: 'PART_TIME', label: 'Part-time' },
]
const MAX_ROLES = 5
const MAX_LOCATIONS = 6

function Chip({ active, onClick, children, disabled }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled && !active}
      aria-pressed={active}
      className={`inline-flex items-center gap-1 rounded-full border px-3 py-1.5 text-sm transition-colors disabled:opacity-40 ${
        active ? 'border-accent bg-accent-soft text-accent font-medium' : 'border-line bg-surface text-ink-soft hover:border-accent/50 hover:text-ink'
      }`}
    >
      {active && <Check size={13} />}{children}
    </button>
  )
}

const toggle = (list, value) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value])
const sameText = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase()

/**
 * Asks what jobs to recommend: roles + experience (step 1), then locations, work mode, job type and salary (step 2).
 * mode "onboarding" records a skip when dismissed so it isn't shown again; mode "edit" just closes.
 */
export default function JobPreferencesModal({ open, mode = 'onboarding', initial, onClose, onSaved }) {
  const [step, setStep] = useState(1)
  const [roles, setRoles] = useState([])
  const [roleInput, setRoleInput] = useState('')
  const [experience, setExperience] = useState('')
  const [locations, setLocations] = useState([])
  const [cityInput, setCityInput] = useState('')
  const [workModes, setWorkModes] = useState([])
  const [jobTypes, setJobTypes] = useState([])
  const [salary, setSalary] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const dialogRef = useRef(null)
  const dismissRef = useRef(null)

  useEffect(() => {
    if (!open) return
    const saved = initial?.status === 'SAVED' ? initial : null
    setStep(1); setError(''); setRoleInput(''); setCityInput('')
    setRoles(saved?.roles || [])
    setExperience(saved?.experienceLevel || '')
    setLocations(saved?.locations || [])
    setWorkModes(saved?.workModes || [])
    setJobTypes(saved?.jobTypes || [])
    setSalary(saved?.minSalaryLpa ? String(saved.minSalaryLpa) : '')
  }, [open, initial])

  useEffect(() => {
    if (!open) return undefined
    dialogRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') dismissRef.current?.() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  if (!open) return null

  function addRole(value) {
    const role = value.trim().replace(/\s+/g, ' ')
    if (!role || role.length > 80 || roles.length >= MAX_ROLES || roles.some((r) => sameText(r, role))) return
    setRoles([...roles, role])
    setRoleInput('')
  }

  function toggleRole(role) {
    if (roles.some((r) => sameText(r, role))) setRoles(roles.filter((r) => !sameText(r, role)))
    else addRole(role)
  }

  function addCity(value) {
    const city = value.trim().replace(/\s+/g, ' ')
    if (!city || city.length > 60 || locations.length >= MAX_LOCATIONS || locations.some((l) => sameText(l, city))) return
    setLocations([...locations, city])
    setCityInput('')
  }

  async function dismiss() {
    if (saving) return
    if (mode === 'onboarding') { try { await skipJobPreferences() } catch { /* asked again next visit */ } }
    onClose()
  }
  dismissRef.current = dismiss

  function next() {
    const pending = roleInput.trim()
    const all = pending && !roles.some((r) => sameText(r, pending)) && roles.length < MAX_ROLES ? [...roles, pending] : roles
    if (all.length === 0) { setError('Add at least one role you want to see jobs for.'); return }
    setRoles(all); setRoleInput(''); setError(''); setStep(2)
  }

  async function save() {
    const pendingCity = cityInput.trim()
    const allLocations = pendingCity && locations.length < MAX_LOCATIONS && !locations.some((l) => sameText(l, pendingCity))
      ? [...locations, pendingCity] : locations
    const lpa = salary === '' ? null : Number(salary)
    if (lpa != null && (!Number.isInteger(lpa) || lpa < 0 || lpa > 500)) { setError('Enter the salary as whole lakhs per year (0–500).'); return }
    try {
      setSaving(true); setError('')
      const saved = await saveJobPreferences({
        roles, experienceLevel: experience || null, locations: allLocations, workModes, jobTypes, minSalaryLpa: lpa,
      })
      onSaved(saved)
    } catch (e) {
      const fields = e.response?.data?.fields
      setError(fields ? Object.values(fields)[0] : (e.response?.data?.error || 'Could not save your preferences. Try again.'))
    } finally { setSaving(false) }
  }

  const title = step === 1 ? 'What jobs should we recommend?' : 'Where and how do you want to work?'

  return (
    <div className="fixed inset-0 z-[90] flex items-end sm:items-center justify-center sm:p-4" role="dialog" aria-modal="true" aria-labelledby="job-prefs-title">
      <button aria-label="Close" tabIndex={-1} className="absolute inset-0 bg-ink/50 backdrop-blur-[1px]" onClick={dismiss} />
      <div
        ref={dialogRef}
        tabIndex={-1}
        className="relative w-full sm:max-w-lg max-h-[92svh] flex flex-col bg-surface rounded-t-3xl sm:rounded-xl2 shadow-card outline-none focus-visible:outline-none"
      >
        <div className="px-5 sm:px-6 pt-5 pb-3 border-b border-line">
          <div className="flex items-start gap-3">
            <div className="h-10 w-10 shrink-0 rounded-full bg-accent-soft text-accent flex items-center justify-center">
              {step === 1 ? <Briefcase size={18} /> : <MapPin size={18} />}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-accent">Step {step} of 2</p>
              <h2 id="job-prefs-title" className="font-display text-lg text-ink leading-snug">{title}</h2>
            </div>
            <button onClick={dismiss} className="h-9 w-9 shrink-0 -mr-1 rounded-full flex items-center justify-center text-muted hover:text-ink hover:bg-paper" aria-label="Close">
              <X size={18} />
            </button>
          </div>
          <div className="flex gap-1.5 mt-3" aria-hidden="true">
            <span className="h-1 flex-1 rounded-full bg-accent" />
            <span className={`h-1 flex-1 rounded-full ${step === 2 ? 'bg-accent' : 'bg-line'}`} />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-5 sm:px-6 py-5 space-y-6">
          {step === 1 ? (
            <>
              <section>
                <label htmlFor="pref-role" className="block text-sm font-medium text-ink">Roles you want <span className="text-muted font-normal">(up to {MAX_ROLES})</span></label>
                <p className="text-xs text-muted mt-0.5 mb-2">Recommended jobs show only these roles — tech or non-tech.</p>
                {roles.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-2">
                    {roles.map((r) => (
                      <span key={r} className="inline-flex items-center gap-1 rounded-full bg-accent-soft text-accent text-sm font-medium pl-3 pr-1.5 py-1">
                        {r}
                        <button type="button" onClick={() => setRoles(roles.filter((x) => x !== r))} className="h-5 w-5 rounded-full flex items-center justify-center hover:bg-accent/15" aria-label={`Remove ${r}`}><X size={12} /></button>
                      </span>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <input
                    id="pref-role"
                    value={roleInput}
                    onChange={(e) => setRoleInput(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addRole(roleInput) } }}
                    disabled={roles.length >= MAX_ROLES}
                    placeholder={roles.length >= MAX_ROLES ? 'Maximum roles added' : 'Type a role, e.g. Data Analyst'}
                    maxLength={80}
                    className="flex-1 min-w-0 rounded-xl border border-line bg-paper px-3.5 py-2.5 text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
                  />
                  <button type="button" onClick={() => addRole(roleInput)} disabled={!roleInput.trim() || roles.length >= MAX_ROLES}
                    className="shrink-0 h-11 w-11 rounded-xl border border-line flex items-center justify-center text-ink-soft hover:text-accent disabled:opacity-40" aria-label="Add role">
                    <Plus size={18} />
                  </button>
                </div>
                <div className="flex flex-wrap gap-2 mt-3">
                  {ROLE_SUGGESTIONS.map((r) => (
                    <Chip key={r} active={roles.some((x) => sameText(x, r))} onClick={() => toggleRole(r)} disabled={roles.length >= MAX_ROLES}>{r}</Chip>
                  ))}
                </div>
              </section>

              <section>
                <p className="text-sm font-medium text-ink mb-2">Experience</p>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {EXPERIENCE.map((x) => (
                    <Chip key={x.value} active={experience === x.value} onClick={() => setExperience(experience === x.value ? '' : x.value)}>{x.label}</Chip>
                  ))}
                </div>
              </section>
            </>
          ) : (
            <>
              <section>
                <label htmlFor="pref-city" className="block text-sm font-medium text-ink">Preferred locations</label>
                <p className="text-xs text-muted mt-0.5 mb-2">Leave empty for any location.</p>
                <div className="flex flex-wrap gap-2">
                  {[...CITIES, ...locations.filter((l) => !CITIES.some((c) => sameText(c, l)))].map((c) => (
                    <Chip key={c} active={locations.some((l) => sameText(l, c))}
                      onClick={() => setLocations(locations.some((l) => sameText(l, c)) ? locations.filter((l) => !sameText(l, c)) : (locations.length < MAX_LOCATIONS ? [...locations, c] : locations))}
                      disabled={locations.length >= MAX_LOCATIONS}>{c}</Chip>
                  ))}
                </div>
                <div className="flex gap-2 mt-2">
                  <input
                    id="pref-city"
                    value={cityInput}
                    onChange={(e) => setCityInput(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addCity(cityInput) } }}
                    placeholder="Other city"
                    maxLength={60}
                    className="flex-1 min-w-0 rounded-xl border border-line bg-paper px-3.5 py-2.5 text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
                  />
                  <button type="button" onClick={() => addCity(cityInput)} disabled={!cityInput.trim()}
                    className="shrink-0 h-11 w-11 rounded-xl border border-line flex items-center justify-center text-ink-soft hover:text-accent disabled:opacity-40" aria-label="Add city">
                    <Plus size={18} />
                  </button>
                </div>
              </section>

              <section>
                <p className="text-sm font-medium text-ink mb-2">Work mode</p>
                <div className="flex flex-wrap gap-2">
                  {WORK_MODES.map((m) => <Chip key={m.value} active={workModes.includes(m.value)} onClick={() => setWorkModes(toggle(workModes, m.value))}>{m.label}</Chip>)}
                </div>
              </section>

              <section>
                <p className="text-sm font-medium text-ink mb-2">Job type</p>
                <div className="flex flex-wrap gap-2">
                  {JOB_TYPES.map((t) => <Chip key={t.value} active={jobTypes.includes(t.value)} onClick={() => setJobTypes(toggle(jobTypes, t.value))}>{t.label}</Chip>)}
                </div>
              </section>

              <section>
                <label htmlFor="pref-salary" className="block text-sm font-medium text-ink">Minimum expected salary <span className="text-muted font-normal">(optional)</span></label>
                <div className="mt-2 flex items-center gap-2">
                  <input
                    id="pref-salary"
                    type="number"
                    inputMode="numeric"
                    min="0"
                    max="500"
                    value={salary}
                    onChange={(e) => setSalary(e.target.value)}
                    placeholder="e.g. 4"
                    className="w-24 rounded-xl border border-line bg-paper px-3.5 py-2.5 text-sm text-ink placeholder:text-muted focus:outline-none focus:border-accent"
                  />
                  <span className="text-sm text-muted">LPA <span className="text-xs">(₹ lakh per year)</span></span>
                </div>
                <p className="text-xs text-muted mt-1.5">Only hides jobs that state a lower salary. Jobs that don't list a salary are always shown.</p>
              </section>
            </>
          )}
          {error && <p className="text-sm text-status-rejected" role="alert">{error}</p>}
        </div>

        <div className="px-5 sm:px-6 py-4 border-t border-line flex items-center gap-3 pb-[max(1rem,env(safe-area-inset-bottom))]">
          {step === 1 ? (
            <button type="button" onClick={dismiss} className="text-sm text-muted hover:text-ink px-1 py-2">
              {mode === 'onboarding' ? 'Skip for now' : 'Cancel'}
            </button>
          ) : (
            <button type="button" onClick={() => { setError(''); setStep(1) }} disabled={saving} className="text-sm text-muted hover:text-ink px-1 py-2">Back</button>
          )}
          <button
            type="button"
            onClick={step === 1 ? next : save}
            disabled={saving}
            className="ml-auto btn-gradient inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-medium shadow-glow disabled:opacity-60"
          >
            {saving && <Loader2 size={15} className="animate-spin" />}
            {step === 1 ? 'Next' : saving ? 'Saving…' : 'Save & find jobs'}
          </button>
        </div>
      </div>
    </div>
  )
}
