// Single source of truth for how application statuses look and behave.
// The backend enum is APPLIED | OA | INTERVIEW | OFFER | REJECTED | WITHDRAWN.
// "OA" (online assessment) is shown to the user as "Screening".

export const STATUS_ORDER = ['APPLIED', 'OA', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']

export const STATUS_META = {
  APPLIED: { label: 'Applied', text: 'text-status-applied', bg: 'bg-status-appliedSoft', dot: 'bg-status-applied' },
  OA: { label: 'Screening', text: 'text-status-screening', bg: 'bg-status-screeningSoft', dot: 'bg-status-screening' },
  INTERVIEW: { label: 'Interview', text: 'text-status-interview', bg: 'bg-status-interviewSoft', dot: 'bg-status-interview' },
  OFFER: { label: 'Offer', text: 'text-status-offer', bg: 'bg-status-offerSoft', dot: 'bg-status-offer' },
  REJECTED: { label: 'Rejected', text: 'text-status-rejected', bg: 'bg-status-rejectedSoft', dot: 'bg-status-rejected' },
  WITHDRAWN: { label: 'Withdrawn', text: 'text-status-withdrawn', bg: 'bg-status-withdrawnSoft', dot: 'bg-status-withdrawn' },
}

export function statusLabel(status) {
  return STATUS_META[status]?.label || status
}

// Stages that count toward the "pipeline" — withdrawn applications are excluded
// since they left the funnel rather than progressing through it.
export const PIPELINE_STAGES = ['APPLIED', 'OA', 'INTERVIEW', 'OFFER', 'REJECTED']
