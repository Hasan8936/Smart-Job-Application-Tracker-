# Smart Job Tracker (backend)

Minimal Spring Boot backend scaffold for the Smart Job Application Tracker.

Run locally:

```bash
mvn spring-boot:run
```

Edit database settings in `src/main/resources/application.yml`.

## Running backend tests locally without Maven installed

If you don't have Maven on your PATH, you can run the backend tests using Docker.

Windows:

```
run-backend-tests.bat
```

Unix / WSL / macOS:

```
./run-backend-tests.sh
```

Both scripts use the official `maven` Docker image and mount the project directory into the container.
Ensure Docker is installed and running before invoking them.

## Start backend + database with Docker Compose

If you have Docker installed, you can start Postgres and the backend together with:

```
docker compose up --build
```

This will build the backend image (using the included `Dockerfile`) and start a Postgres container. The backend will be available at `http://localhost:8080`.

To stop and remove containers:

```
docker compose down
```

## Password reset and Google sign-in

Password reset uses SMTP and sends links to `${FRONTEND_URL}/reset-password`. Set
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `FRONTEND_URL` in
the backend environment. The reset-token table is created by Flyway migration V4.

Google sign-in is enabled when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are
set. In Google Cloud Console, add this redirect URI:

```
https://<your-backend-host>/login/oauth2/code/google
```

For local development use `http://localhost:8080/login/oauth2/code/google` and
set `VITE_API_BASE=http://localhost:8080/api` for the frontend.

## Job sources

Job discovery pulls from whichever providers are enabled via env vars. At
least one of `GREENHOUSE_ENABLED`, `LEVER_ENABLED`, `ASHBY_ENABLED`,
`JOBSPY_ENABLED`, or `TELEGRAM_ENABLED` must be `true` (with the matching
boards/sites/URL/channels) or discovery will refuse to run.

### JobSpy (replaces Apify)

[`jobspy-service/`](https://github.com/Hasan8936/Smart-Job-Application-Tracker-/tree/main/jobspy-service)
is a standalone Python FastAPI microservice wrapping
[python-jobspy](https://github.com/speedyapply/JobSpy). It exposes
`POST /search` and `GET /health`.

To deploy on Render:

1. Create a new Render Web Service, point it at the `jobspy-service/` directory.
2. Runtime: Docker (uses the `Dockerfile` in that folder).
3. Set env vars on the main backend service:
   * `JOBSPY_ENABLED=true`
   * `JOBSPY_SERVICE_URL=https://your-jobspy-service.onrender.com`
   * Optional: `JOBSPY_RESULTS_WANTED=20`, `JOBSPY_HOURS_OLD=168`

## Google Calendar reminders

When a user schedules a reminder, one Google Calendar event is created at the
actual event time (e.g., the interview), with popup + email notifications at
every configured offset (e.g., 24h and 2h before). The reminder email/WhatsApp
is still sent by the existing scheduler as a fallback.

Endpoints:

* `GET /api/google-calendar/connect-url` → returns OAuth URL for the browser
* `GET /api/google-calendar/callback` → receives the Google redirect, stores tokens
* `GET /api/google-calendar/status` → `{connected, configured}`
* `DELETE /api/google-calendar/disconnect`

To activate:

1. In Google Cloud Console → OAuth consent screen → add scope
   `https://www.googleapis.com/auth/calendar.events`.
2. Add redirect URI: `https://your-backend.onrender.com/api/google-calendar/callback`.
3. Set on the Render backend:
   * `GOOGLE_CALENDAR_REDIRECT_URI=https://your-backend.onrender.com/api/google-calendar/callback`
   * (reuses the existing `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`)
4. The V23 Flyway migration runs automatically on next deploy.

UI: the Reminders page shows a "Google Calendar" section at the bottom —
users click "Connect Google Calendar", authorize in Google's popup, and land
back at `/reminders?calendar=connected`.

