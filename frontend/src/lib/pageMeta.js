import { useEffect } from 'react'

const SITE_URL = 'https://smartjobtracker.indevs.in'
const DEFAULT_TITLE = 'Smart Job Tracker'
const DEFAULT_DESCRIPTION = 'Track applications, match your resume to job descriptions, and never miss a follow-up.'

function upsertMeta(name, content) {
  let el = document.head.querySelector(`meta[name="${name}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('name', name)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
  return el
}

// Per-route <title>, description, canonical and optional noindex for the SPA.
// Crawlers that render JS pick these up; index.html holds the site-wide defaults.
export function usePageMeta({ title, description, path, noindex = false }) {
  useEffect(() => {
    document.title = title
    upsertMeta('description', description || DEFAULT_DESCRIPTION)

    let canonical = document.head.querySelector('link[rel="canonical"]')
    if (path) {
      if (!canonical) {
        canonical = document.createElement('link')
        canonical.setAttribute('rel', 'canonical')
        document.head.appendChild(canonical)
      }
      canonical.setAttribute('href', SITE_URL + path)
    } else if (canonical) {
      canonical.remove()
    }

    const robots = noindex ? upsertMeta('robots', 'noindex') : null

    return () => {
      document.title = DEFAULT_TITLE
      upsertMeta('description', DEFAULT_DESCRIPTION)
      robots?.remove()
    }
  }, [title, description, path, noindex])
}

// Injects a JSON-LD block for the lifetime of the calling component.
export function useJsonLd(id, data) {
  useEffect(() => {
    const el = document.createElement('script')
    el.type = 'application/ld+json'
    el.id = id
    el.textContent = JSON.stringify(data)
    document.head.appendChild(el)
    return () => el.remove()
  }, [id, data])
}
