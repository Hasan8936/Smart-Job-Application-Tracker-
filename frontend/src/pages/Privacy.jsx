import React from 'react'
import { Link } from 'react-router-dom'
import Layout from '../components/Layout'

const LAST_UPDATED = 'September 2026'

function Section({ id, title, children }) {
  return (
    <section id={id} className="mb-10">
      <h2 className="text-xl font-semibold text-ink mb-3">{title}</h2>
      <div className="text-ink-soft leading-relaxed space-y-3">{children}</div>
    </section>
  )
}

export default function Privacy() {
  return (
    <Layout>
      <div className="max-w-3xl mx-auto px-4 py-10">
        <header className="mb-10">
          <h1 className="text-3xl font-bold text-ink mb-2">Privacy &amp; Data Policy</h1>
          <p className="text-sm text-muted">Last updated: {LAST_UPDATED}</p>
          <p className="mt-4 text-ink-soft leading-relaxed">
            Smart Job Tracker ("we", "us", "our") is committed to protecting your personal data.
            This policy explains what we collect, why, and how we keep it safe—in plain language.
          </p>
        </header>

        {/* Table of contents */}
        <nav aria-label="Page sections" className="mb-10 p-4 rounded-xl bg-paper border border-line">
          <p className="text-xs font-semibold text-muted uppercase tracking-wide mb-2">Contents</p>
          <ol className="list-decimal list-inside space-y-1 text-sm text-accent">
            {[
              ['#collect', 'Data We Collect'],
              ['#use',     'How We Use It'],
              ['#retention','Data Retention'],
              ['#soc2',    'SOC 2 Alignment'],
              ['#cookies', 'Cookie Policy'],
              ['#rights',  'Your Rights'],
              ['#contact', 'Contact & Data Requests'],
            ].map(([href, label]) => (
              <li key={href}>
                <a href={href} className="hover:underline">{label}</a>
              </li>
            ))}
          </ol>
        </nav>

        <Section id="collect" title="1. Data We Collect">
          <p>
            <strong className="text-ink">Account data:</strong> Email address and hashed password (we never store your plain-text password).
            If you sign in with Google, we receive only your email and display name—no Google Calendar or Gmail data is stored unless you explicitly connect those integrations.
          </p>
          <p>
            <strong className="text-ink">Job application data:</strong> Company names, role titles, job descriptions, application statuses, and notes you enter manually.
            This data lives in your account and is never shared with employers or third parties.
          </p>
          <p>
            <strong className="text-ink">Resume content:</strong> Text extracted from resumes you upload, used solely for skill matching and profile pre-fill.
            Original file bytes are not stored after extraction.
          </p>
          <p>
            <strong className="text-ink">Profile data:</strong> Skills, experience, education, and preferred roles you build in your Candidate Profile.
          </p>
          <p>
            <strong className="text-ink">Usage data:</strong> Standard server logs (IP address, request path, timestamp) retained for up to 30 days for security and debugging.
          </p>
        </Section>

        <Section id="use" title="2. How We Use It">
          <p>We use your data only to provide the service you signed up for:</p>
          <ul className="list-disc list-inside space-y-1 ml-2">
            <li>Authenticate your account and keep it secure</li>
            <li>Match your resume and skills against job postings</li>
            <li>Send reminders you explicitly create (email, WhatsApp)</li>
            <li>Pre-fill the Resume Builder with your saved profile</li>
            <li>Show you job discovery results relevant to your preferences</li>
          </ul>
          <p>
            We do <strong className="text-ink">not</strong> sell, rent, or share your personal data with advertisers, data brokers, or any third party for marketing purposes.
            AI-powered features (resume analysis, interview prep) send minimal required data to Google Gemini and do not store AI responses against your identity.
          </p>
        </Section>

        <Section id="retention" title="3. Data Retention">
          <p>
            Your account data is retained for as long as your account is active.
            You may delete your account at any time; upon deletion all personal data is removed within 30 days, except where retention is required by law.
          </p>
          <p>
            Server logs are purged after 30 days.
            Aggregated, anonymised usage statistics (no PII) may be retained indefinitely for product improvement.
          </p>
        </Section>

        <Section id="soc2" title="4. SOC 2 Alignment">
          <p>
            Smart Job Tracker is designed to align with SOC 2 Trust Service Criteria. We are not currently SOC 2 certified,
            but the architecture and controls are built to meet those standards:
          </p>
          <div className="space-y-4 mt-2">
            {[
              ['Security',               'Secrets are stored as environment variables, never in code. Passwords are hashed with BCrypt. JWT tokens have short expiry. All API communication uses HTTPS. CORS is restricted to known origins.'],
              ['Availability',           'The service is hosted on Render with health-check monitoring. Background jobs are idempotent and retry-safe. Database is backed up daily.'],
              ['Confidentiality',        'Gmail integration tokens are encrypted at rest using AES-256. Resume content is processed in memory and not persisted in plain text. Database access is restricted by role.'],
              ['Processing Integrity',   'AI-derived data is labelled as an estimate. Resume content is never fabricated—only verified information from your uploaded file is used. Status history is append-only.'],
              ['Privacy',                'Data collection is minimal and purpose-limited. No third-party advertising trackers. Google OAuth scopes are limited to openid, profile, and email unless you explicitly enable Gmail or Calendar integrations.'],
            ].map(([criterion, detail]) => (
              <div key={criterion} className="p-3 rounded-lg bg-accent/5 border border-accent/10">
                <p className="font-medium text-ink text-sm">{criterion}</p>
                <p className="text-sm mt-0.5">{detail}</p>
              </div>
            ))}
          </div>
        </Section>

        <Section id="cookies" title="5. Cookie Policy">
          <p>
            Smart Job Tracker uses a small number of browser storage mechanisms to make the app work:
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse mt-2">
              <thead>
                <tr className="border-b border-line">
                  <th className="text-left py-2 pr-4 font-semibold text-ink">Name / Key</th>
                  <th className="text-left py-2 pr-4 font-semibold text-ink">Type</th>
                  <th className="text-left py-2 font-semibold text-ink">Purpose</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {[
                  ['token',                'localStorage', 'Stores your JWT authentication token. Required for the app to work.'],
                  ['cookie_consent',       'localStorage', 'Records whether you accepted or declined cookies. Expires never (your choice is permanent until you clear site data).'],
                  ['smart-job-tracker-jobs-last-visit', 'localStorage', 'Tracks when you last visited the Discovery page so we can highlight new jobs.'],
                  ['smart-job-tracker-job-actions',     'localStorage', 'Saves your local saved/bookmarked job states across sessions.'],
                  ['sidebar_collapsed',    'localStorage', 'Remembers whether you prefer the sidebar collapsed.'],
                ].map(([name, type, purpose]) => (
                  <tr key={name}>
                    <td className="py-2 pr-4 font-mono text-xs text-accent">{name}</td>
                    <td className="py-2 pr-4 text-muted">{type}</td>
                    <td className="py-2">{purpose}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-3">
            We do <strong className="text-ink">not</strong> use advertising cookies, third-party tracking cookies, or analytics cookies.
            All storage is first-party (your browser, for your use only).
          </p>
          <p>
            You can clear all stored data at any time through your browser's site-data settings.
            Clearing the <code className="text-xs bg-mist px-1 py-0.5 rounded">token</code> key will log you out.
          </p>
        </Section>

        <Section id="rights" title="6. Your Rights">
          <p>Under GDPR and similar frameworks you have the right to:</p>
          <ul className="list-disc list-inside space-y-1 ml-2">
            <li><strong className="text-ink">Access</strong> — request a copy of all data we hold about you</li>
            <li><strong className="text-ink">Rectification</strong> — correct inaccurate data</li>
            <li><strong className="text-ink">Erasure</strong> — request deletion of your account and all associated data</li>
            <li><strong className="text-ink">Portability</strong> — receive your data in a machine-readable format</li>
            <li><strong className="text-ink">Objection</strong> — object to specific processing activities</li>
          </ul>
          <p>To exercise any of these rights, contact us using the details below.</p>
        </Section>

        <Section id="contact" title="7. Contact &amp; Data Requests">
          <p>
            For data requests, privacy questions, or to report a concern, email us at{' '}
            <a href="mailto:hasanryan052@gmail.com" className="text-accent hover:underline">hasanryan052@gmail.com</a>.
            We aim to respond within 5 business days.
          </p>
          <p>
            If you are based in the EU or EEA and feel your request was not handled properly, you have the right to lodge a complaint with your local Data Protection Authority.
          </p>
        </Section>

        <div className="mt-8 pt-6 border-t border-line">
          <Link to="/" className="text-sm text-accent hover:underline">← Back to dashboard</Link>
        </div>
      </div>
    </Layout>
  )
}
