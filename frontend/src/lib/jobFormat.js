// Display helpers for job postings. Only shows what the API provides; unknown values stay "unavailable".

const PERIOD_SUFFIX = { YEAR: '/yr', MONTH: '/mo', WEEK: '/wk', DAY: '/day', HOUR: '/hr' }

function money(value, currency) {
  if (value == null) return null
  if (currency) {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency', currency, notation: value >= 10000 ? 'compact' : 'standard', maximumFractionDigits: value >= 10000 ? 1 : 0,
      }).format(value)
    } catch { /* unknown currency code: fall through */ }
  }
  return `${currency ? currency + ' ' : ''}${value.toLocaleString()}`
}

/**
 * { text, note, estimated } for a job's salary, or null when there is none.
 * note explains where the figure came from so estimates are never mistaken for listed pay.
 */
export function formatSalary(job) {
  if (job?.salaryMin == null && job?.salaryMax == null) return null
  const min = money(job.salaryMin, job.salaryCurrency)
  const max = money(job.salaryMax, job.salaryCurrency)
  const range = min && max && min !== max ? `${min} – ${max}` : (min || max)
  const text = `${range}${PERIOD_SUFFIX[job.salaryPeriod] ?? ''}`

  const estimated = !!job.salaryEstimated || job.salarySource === 'ESTIMATE'
  let note = null
  if (job.salarySource === 'ESTIMATE') {
    note = job.salarySampleSize
      ? `Estimate from ${job.salarySampleSize} similar listed salaries`
      : 'Estimate from similar listed salaries'
  } else if (estimated) note = 'Estimate'
  else if (job.salarySource === 'DESCRIPTION') note = 'Stated in the job description'
  else if (job.salarySource === 'PROVIDER') note = 'Listed by the employer'
  return { text, note, estimated }
}

/** "Today", "3 days ago", "2 weeks ago", or a date once it's older than ~2 months. */
export function formatPostedAt(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const days = Math.floor((Date.now() - date.getTime()) / 86_400_000)
  if (days <= 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 14) return `${days} days ago`
  if (days < 60) return `${Math.floor(days / 7)} weeks ago`
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

export function formatFullDate(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' })
}

/** "FullTime" / "full_time" / "full-time" → "Full time". */
export function formatEmploymentType(value) {
  if (!value) return null
  const words = value.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}
