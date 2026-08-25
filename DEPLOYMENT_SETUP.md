# Deployment setup — Google login & password reset

Both features were failing in production because of **missing configuration**, not broken code:

| Symptom | Real cause | Fix |
| --- | --- | --- |
| `GET /oauth2/authorization/google` → **HTTP 403** | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` were not set on Render, so the OAuth login filter was never wired up. The endpoint didn't exist → 404 → forwarded to `/error` (which required auth) → **403**. | Set the Google env vars on Render + register the redirect URI in Google Cloud Console. Code now also permits `/error` and trusts proxy headers. |
| Forgot password → **"Unable to send the reset email"** | SMTP defaulted to `localhost:25` with auth/TLS **hard-coded off**, so nothing could send from Render. | Set the `MAIL_*` env vars on Render. Code now enables SMTP auth + STARTTLS by default and defaults to Gmail. |

## Code changes already made (in your local files — commit & push them)

- `application.yml` — added `server.forward-headers-strategy: framework` (so the OAuth `redirect_uri` is built as `https://…onrender.com/…`, not an internal `http` URL that Google rejects), and made SMTP `auth` / `starttls` env-overridable and default to `true` with Gmail as the default host.
- `SecurityConfig.java` — added `/error` to the public endpoints so a missing route returns a clean error instead of a misleading 403.
- `EmailService.java` — logs the real SMTP error to the Render logs, and supports a `MAIL_FROM` override.

**Everything below is configuration you set in the Render / Google / Vercel dashboards — no code left to change.**

---

## 1. Render — backend environment variables

Render dashboard → your backend service (`smart-job-tracker-api-iflm`) → **Environment** → add these, then **Save** (it redeploys automatically):

| Key | Value | Notes |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` | *(from step 2)* | Ends in `.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | *(from step 2)* | Starts with `GOCSPX-` |
| `FRONTEND_URL` | `https://<your-production-vercel-domain>` | No trailing slash. Where the user lands after Google login. Use your **stable** Vercel domain, not a per-deploy preview URL. |
| `MAIL_USERNAME` | `youraddress@gmail.com` | The Gmail account that sends reset emails |
| `MAIL_PASSWORD` | *(16-char App Password from step 3)* | **Not** your normal Gmail password |
| `JWT_SECRET` | a long random string (32+ chars) | Currently falls back to a weak default — set this for security |
| `AI_MATCHING_PROVIDER` | `fallback` | Set to `gemini` to enable validated Gemini embedding similarity |
| `AI_MATCHING_API_KEY` | *(empty)* | Gemini API key; required only when the provider is `gemini` |
| `AI_MATCHING_MODEL` | `gemini-embedding-001` | Gemini embedding model |
| `GREENHOUSE_ENABLED` | `false` | Enable configured official Greenhouse boards |
| `GREENHOUSE_BOARDS` | *(empty)* | Comma-separated public Greenhouse board slugs |
| `LEVER_ENABLED` | `false` | Enable configured official Lever sites |
| `LEVER_SITES` | *(empty)* | Comma-separated public Lever site slugs |
| `ASHBY_ENABLED` | `false` | Enable configured official Ashby boards |
| `ASHBY_BOARDS` | *(empty)* | Comma-separated public Ashby board slugs |

Optional (only if you change defaults):

| Key | Default | When to set |
| --- | --- | --- |
| `MAIL_HOST` | `smtp.gmail.com` | Different email provider |
| `MAIL_PORT` | `587` | Different provider/port |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `https://*.vercel.app,http://localhost:5173,http://localhost:3000` | Custom frontend domain |
| `JOB_PROVIDER_MIN_INTERVAL_MS` | `500` | Minimum delay between official-provider requests |
| `JOB_PROVIDER_MAX_RETRIES` | `3` | Retries for provider `429` and `5xx` responses |
| `APIFY_ENABLED` | `false` | Optional provider seam; enable only for an approved compliant actor |
| `APIFY_TOKEN` / `APIFY_ACTOR` | *(empty)* | Required only for an explicitly configured Apify integration |

Your database vars (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) should already be set — leave them as they are.

---

## 2. Google Cloud Console — OAuth credentials

1. Go to <https://console.cloud.google.com/> → create or select a project.
2. **APIs & Services → OAuth consent screen**: choose **External**, fill in app name + your email, and under **Test users** add the Google account(s) you'll sign in with (needed while the app is in "Testing" mode).
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
4. Application type: **Web application**.
5. Under **Authorized redirect URIs**, add this **exact** URL (this is your backend, not the frontend):

   ```
   https://smart-job-tracker-api-iflm.onrender.com/login/oauth2/code/google
   ```

   It must match character-for-character (`https`, no trailing slash). A mismatch here is the #1 cause of `redirect_uri_mismatch` errors.
6. Click **Create**, copy the **Client ID** and **Client secret**, and paste them into the Render vars in step 1.

---

## 3. Gmail — App Password (for password-reset email)

Gmail blocks normal-password SMTP, so you need a 16-character App Password:

1. The sending Gmail account must have **2-Step Verification ON** (<https://myaccount.google.com/security>).
2. Go to <https://myaccount.google.com/apppasswords>.
3. Create a password (name it e.g. "Smart Job Tracker"). Google shows a 16-character code like `abcd efgh ijkl mnop`.
4. Put that code (spaces optional) into Render's `MAIL_PASSWORD`, and the Gmail address into `MAIL_USERNAME`.

> Prefer a dedicated mail service? Brevo / SendGrid / Mailgun all work — set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` to their SMTP values. Gmail's free tier is fine for a portfolio project (limit ~500 emails/day).

---

## 4. Vercel — frontend environment variable

1. Vercel → your project → **Settings → Environment Variables**.
2. Ensure this exists for **Production**:

   ```
   VITE_API_BASE = https://smart-job-tracker-api-iflm.onrender.com/api
   ```

   (Include `/api`, no trailing slash.)
3. Vite bakes this in at **build time**, so after adding/changing it you must **redeploy** the frontend (Deployments → ⋯ → Redeploy).

---

## 5. Verify

After Render finishes redeploying with the new vars:

1. **Backend health** — open `https://smart-job-tracker-api-iflm.onrender.com/actuator/health` → should show `{"status":"UP"}`.
2. **Google login** — from your site, click **Continue with Google**. It should now redirect to the Google consent screen (a 302), *not* show a 403. After consent it returns to `/oauth2/callback` and logs you in.
3. **Password reset** — use **Forgot password?** with a real address. You should receive the email within a minute. If it still fails, open the Render service **Logs** and look for the line `Failed to send password reset email …` — it prints the exact SMTP cause (bad credentials, TLS, etc.).

## Common gotchas

- **Still 403 on Google** → the env vars didn't take effect; confirm the Render deploy finished *after* you added them, and that the names are exactly `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
- **`redirect_uri_mismatch`** → the Google Console redirect URI doesn't exactly match `https://smart-job-tracker-api-iflm.onrender.com/login/oauth2/code/google`.
- **Google login works but lands on the wrong site / localhost** → `FRONTEND_URL` on Render is unset or wrong.
- **Reset email "sent" locally but nothing arrives** → App Password wrong, or 2-Step Verification is off on that Gmail account.
