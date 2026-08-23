# What changed — Smart Job Application Tracker frontend redesign

This replaces the plain, unstyled frontend with a real design system and adds
the one feature that had a working backend but no UI: **Reminders**.

## Drop-in instructions
1. In your repo, back up or delete the existing `frontend/` folder's contents
   (keep `Dockerfile`, `vercel.json` if you haven't changed them — they're
   included here unchanged too).
2. Copy everything in this zip into `frontend/`.
3. `cd frontend && npm install && npm run dev` (or `npm run build` to ship).
4. No backend changes needed — this only touches the React app. It calls the
   same `/api/...` routes your Spring Boot backend already exposes, including
   `/api/reminders/*`, which existed on the backend but was never called from
   the old UI.

## Design system
- **Colors**: ink-navy sidebar (`#14161C`), paper background (`#F5F6F8`), an
  amber accent (`#E7A335`) for primary actions, and a dedicated color per
  application status (applied/screening/interview/offer/rejected/withdrawn)
  used consistently everywhere a status appears.
- **Type**: Fraunces (serif, headings) + Inter (UI text) + IBM Plex Mono
  (numbers, dates, stats) — defined in `tailwind.config.cjs` and loaded via
  Google Fonts in `index.html`.
- **Signature element**: the "Pipeline" bar on the dashboard — a proportional,
  ordered read of how many applications sit at each real stage, reused as the
  page's visual identity.

## New/changed files
- `src/lib/status.js` — single source of truth for status labels/colors (was
  duplicated ad hoc before).
- `src/components/Layout.jsx`, `Sidebar.jsx` — replaces the old top `Navbar`
  with a persistent sidebar (desktop) / drawer (mobile).
- `src/components/PipelineBar.jsx`, `StatCard.jsx`, `StatusBadge.jsx`,
  `ScoreRing.jsx`, `ApplicationDrawer.jsx`, `AuthLayout.jsx` — new UI pieces.
- `src/components/ApplicationCard.jsx` — redesigned, adds a delete action
  (the backend already supported `DELETE /api/applications/{id}`; the old UI
  didn't expose it).
- `src/pages/Dashboard.jsx` — real stat cards + pipeline + recent applications
  (no fabricated "+12% from last month" style numbers — only what your data
  actually shows).
- `src/pages/Applications.jsx` — **new**: full list with search, status
  filter, add/edit, delete.
- `src/pages/ResumeMatch.jsx` — **new**: dedicated page for resume upload +
  JD paste + score ring + matched/missing keyword chips (previously buried
  inline on the dashboard).
- `src/pages/Reminders.jsx` — **new**: wired to `/api/reminders`, which had
  zero frontend before this.
- `src/pages/Login.jsx`, `Register.jsx`, `Profile.jsx` — redesigned to match
  the new system; added inline error states instead of `alert()`.
- `src/App.jsx` — added routes for `/applications`, `/resume-match`,
  `/reminders`.
- Removed `src/components/Navbar.jsx` (superseded by `Sidebar.jsx`).

## Notes / things you may want to decide on
- The backend's `OA` status is displayed as **"Screening"** in the UI (label
  only — the API value is unchanged). Rename in `src/lib/status.js` if you'd
  rather show `OA` literally.
- I did not add auto-generated reminders (e.g. auto-creating a follow-up
  reminder when an application status changes) — the backend doesn't do this
  currently either. Happy to add it if you want that behavior.
- Verified with `npm run build` — compiles clean. I couldn't render live
  screenshots in this sandbox (browser download blocked by network policy),
  so give the dev server a quick look before you ship.
