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

export default function Terms() {
  return (
    <Layout>
      <div className="max-w-3xl mx-auto px-4 py-10">
        <header className="mb-10">
          <h1 className="text-3xl font-bold text-ink mb-2">Terms &amp; Conditions</h1>
          <p className="text-sm text-muted">Last updated: {LAST_UPDATED}</p>
          <p className="mt-4 text-ink-soft leading-relaxed">
            By using Smart Job Tracker ("we", "us", "our"), you agree to these terms. Please read
            them carefully. If you disagree with any part, you may not use the service.
          </p>
        </header>

        <nav aria-label="Page sections" className="mb-10 p-4 rounded-xl bg-paper border border-line">
          <p className="text-xs font-semibold text-muted uppercase tracking-wide mb-2">Contents</p>
          <ol className="list-decimal list-inside space-y-1 text-sm text-accent">
            {[
              ['#account',      'Account Responsibilities'],
              ['#acceptable',   'Acceptable Use'],
              ['#ip',           'Intellectual Property'],
              ['#ai',           'AI-Generated Content'],
              ['#disclaimer',   'Disclaimers'],
              ['#liability',    'Limitation of Liability'],
              ['#termination',  'Termination'],
              ['#changes',      'Changes to Terms'],
              ['#contact',      'Contact'],
            ].map(([href, label]) => (
              <li key={href}>
                <a href={href} className="hover:underline">{label}</a>
              </li>
            ))}
          </ol>
        </nav>

        <Section id="account" title="1. Account Responsibilities">
          <p>
            You must provide accurate information when creating an account. You are responsible for
            maintaining the confidentiality of your credentials and for all activity under your account.
            Notify us immediately at <a href="mailto:afzalmohd44099@gmail.com" className="text-accent hover:underline">afzalmohd44099@gmail.com</a> if
            you suspect unauthorised access.
          </p>
          <p>
            You must be at least 16 years old to use Smart Job Tracker. By registering, you confirm
            you meet this requirement.
          </p>
        </Section>

        <Section id="acceptable" title="2. Acceptable Use">
          <p>You agree not to:</p>
          <ul className="list-disc list-inside space-y-1 ml-2">
            <li>Use the service for any unlawful purpose or in violation of any regulations.</li>
            <li>Attempt to scrape, reverse-engineer, or circumvent any security measure.</li>
            <li>Upload malicious files, spam, or content that infringes third-party rights.</li>
            <li>Use automated scripts to create accounts or bulk-submit job applications without oversight.</li>
            <li>Resell or commercialise the service without our written consent.</li>
          </ul>
          <p>
            We reserve the right to suspend or terminate accounts that violate these rules without notice.
          </p>
        </Section>

        <Section id="ip" title="3. Intellectual Property">
          <p>
            All content, branding, and code comprising Smart Job Tracker is owned by or licensed to us.
            You retain full ownership of the data you upload (resumes, job notes, etc.).
          </p>
          <p>
            By uploading content you grant us a limited, non-exclusive licence to process it solely to
            provide the service to you. We do not sell or share your content with third parties for
            marketing purposes.
          </p>
        </Section>

        <Section id="ai" title="4. AI-Generated Content">
          <p>
            Smart Job Tracker uses AI to assist with resume analysis, job matching, and application
            suggestions. AI-generated output is provided for informational purposes only.
          </p>
          <p>
            We do not guarantee the accuracy, completeness, or fitness of AI-generated content for any
            particular purpose. You are responsible for reviewing any AI output before relying on it or
            submitting it to a third party (e.g., a prospective employer).
          </p>
          <p>
            The Auto Apply feature submits applications on your behalf. By using it you confirm you have
            reviewed the target role and authorise the submission. We are not liable for incorrectly
            submitted applications.
          </p>
        </Section>

        <Section id="disclaimer" title="5. Disclaimers">
          <p>
            Smart Job Tracker is provided "as is" without warranties of any kind, either express or
            implied, including but not limited to warranties of merchantability, fitness for a particular
            purpose, or non-infringement.
          </p>
          <p>
            We do not guarantee that the service will be uninterrupted, error-free, or that job listings
            will be accurate or up to date. Job data is sourced from third-party providers and may change
            without notice.
          </p>
        </Section>

        <Section id="liability" title="6. Limitation of Liability">
          <p>
            To the maximum extent permitted by law, we shall not be liable for any indirect, incidental,
            special, consequential, or punitive damages arising from your use of or inability to use the
            service, even if advised of the possibility of such damages.
          </p>
          <p>
            Our total liability to you for any claim arising out of or relating to these terms or the
            service shall not exceed the amount you paid us in the twelve months preceding the claim, or
            £50 (whichever is greater).
          </p>
        </Section>

        <Section id="termination" title="7. Termination">
          <p>
            You may delete your account at any time from your profile settings. We may suspend or
            terminate your access if you breach these terms. Upon termination, your data will be deleted
            in accordance with our <Link to="/privacy" className="text-accent hover:underline">Privacy Policy</Link>.
          </p>
        </Section>

        <Section id="changes" title="8. Changes to Terms">
          <p>
            We may update these terms from time to time. We will notify you of material changes by
            displaying a notice in the app or by email. Continued use after changes take effect
            constitutes acceptance of the revised terms.
          </p>
        </Section>

        <Section id="contact" title="9. Contact">
          <p>
            Questions about these terms? Contact us at{' '}
            <a href="mailto:afzalmohd44099@gmail.com" className="text-accent hover:underline">
              afzalmohd44099@gmail.com
            </a>.
          </p>
        </Section>

        <footer className="mt-12 pt-6 border-t border-line text-sm text-muted flex flex-wrap gap-4">
          <Link to="/" className="hover:text-ink">Home</Link>
          <Link to="/privacy" className="hover:text-ink">Privacy Policy</Link>
          <Link to="/login" className="hover:text-ink">Sign in</Link>
        </footer>
      </div>
    </Layout>
  )
}
