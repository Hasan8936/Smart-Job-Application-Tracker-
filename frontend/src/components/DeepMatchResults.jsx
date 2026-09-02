export default function DeepMatchResults({ result }) {
  if (!result) return null

  const { recruiterTest } = result

  return (
    <div className="rounded-xl2 border border-line bg-surface">
      <div className="p-5">
        <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="font-mono text-4xl text-ink">{recruiterTest.compatibilityScore}<span className="text-lg text-muted">/100</span></div>
              <span className="text-sm text-muted">Compatibility score</span>
            </div>
            <List title="Missing critical keywords" items={recruiterTest.missingKeywords} tone="red" />
            <List title="Red flags" items={recruiterTest.redFlags} />
        </div>
      </div>
    </div>
  )
}
function List({ title, items = [], tone }) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-semibold text-ink">{title}</h4>
      {tone === 'red' ? (
        <div className="flex flex-wrap gap-2">{items.map((item) => <span key={item} className="rounded-full bg-status-rejectedSoft px-2.5 py-1 text-sm text-status-rejected">{item}</span>)}</div>
      ) : (
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink">{items.map((item) => <li key={item}>{item}</li>)}</ul>
      )}
    </div>
  )
}

