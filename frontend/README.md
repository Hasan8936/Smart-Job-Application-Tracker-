# Smart Job Tracker — Frontend

Vite + React frontend for Smart Job Tracker.

## Local development

```bash
cd frontend
npm install
npm run dev
```

The frontend talks to the backend API. The base URL comes from the `VITE_API_BASE`
environment variable and defaults to `http://localhost:8080/api` when unset.

To point local dev at a different backend, copy `.env.example` to `.env` and edit it:

```bash
cp .env.example .env
```

## Deploying to Vercel

1. **Root Directory:** In the Vercel project settings, set **Root Directory** to `frontend`
   (this repo is a monorepo — the Java backend lives at the root).
2. **Node version:** Use Node `22.x` (pinned here via `.nvmrc` and the `engines` field in
   `package.json`). Vite 8 will fail to build on Node 18.
3. **Environment variable:** Add `VITE_API_BASE` pointing at your deployed backend, e.g.
   `https://sjt-backend.onrender.com/api`. Without it the app calls `http://localhost:8080`
   and every request fails on the live site. Redeploy after changing it — Vite bakes the
   value in at build time.
4. **SPA routing:** `vercel.json` rewrites all paths to `index.html` so client-side routes
   (`/login`, `/register`, `/profile`) work on refresh and direct navigation.

The backend must allow your Vercel origin via CORS (already configured for `*.vercel.app`
in `SecurityConfig`; add a custom domain via `APP_CORS_ALLOWED_ORIGIN_PATTERNS`).
