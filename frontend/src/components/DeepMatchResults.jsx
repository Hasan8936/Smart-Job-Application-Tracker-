import { useState } from 'react'

export default function DeepMatchResults({ result }) {
  const [tab, setTab] = useState('recruiter')
  if (!result) return null

  const { recruiterTest, xyzRewrite, atsFilter } = result
  const tabs = [
    ['recruiter', 'Recruiter verdict'],
    ['rewrite', 'XYZ rewrite'],
    ['ats', 'ATS filter'],
  ]

  return (
    <div className="rounded-xl2 border border-line bg-surface">
      <div className="flex overflow-x-auto border-b border-line">
        {tabs.map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setTab(id)}
            className={`shrink-0 px-4 py-3 text-sm font-medium ${tab === id ? 'border-b-2 border-accent text-ink' : 'text-muted hover:text-ink'}`}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="p-5">
        {tab === 'recruiter' && (
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="font-mono text-4xl text-ink">{recruiterTest.compatibilityScore}<span className="text-lg text-muted">/100</span></div>
              <span className="text-sm text-muted">Compatibility score</span>
            </div>
            <List title="Missing critical keywords" items={recruiterTest.missingKeywords} tone="red" />
            <List title="Red flags" items={recruiterTest.redFlags} />
          </div>
        )}
        {tab === 'rewrite' && (
          <div className="space-y-4">
            <Section title="Experience" text={xyzRewrite.rewrittenExperience} />
            <Section title="Projects" text={xyzRewrite.rewrittenProjects} />
            <Section title="Skills" text={xyzRewrite.rewrittenSkills} />
          </div>
        )}
        {tab === 'ats' && (
          <div className="space-y-3">
            {atsFilter.flaggedSections.map((flagged, index) => (
              <div key={`${flagged}-${index}`} className="rounded-lg border border-line p-3">
                <p className="mb-1 text-xs font-semibold uppercase text-status-rejected">Would get skipped</p>
                <p className="mb-2 text-sm text-muted line-through">{flagged}</p>
                <p className="text-xs font-semibold uppercase text-status-offer">Fixed</p>
                <p className="text-sm text-ink">{atsFilter.fixedSections[index] || 'No replacement returned'}</p>
              </div>
            ))}
            {!atsFilter.flaggedSections.length && <p className="text-sm text-muted">No weak sections identified.</p>}
          </div>
        )}
      </div>
    </div>
  )
}

function List({ title, items = [], tone }) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-semibold text-ink">{title}</h4>
      {tone === 'red' ? (
        <div className="flex flex-wrap gap-2">{items.map((item) => <span key={item} className="rounded-md bg-status-rejectedSoft px-2 py-1 text-sm text-status-rejected">{item}</span>)}</div>
      ) : (
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink">{items.map((item) => <li key={item}>{item}</li>)}</ul>
      )}
    </div>
  )
}

function Section({ title, text }) {
  return <div><h4 className="mb-1 text-sm font-semibold text-ink">{title}</h4><p className="whitespace-pre-line rounded-lg bg-paper p-3 text-sm text-ink">{text}</p></div>
}