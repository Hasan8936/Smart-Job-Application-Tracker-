# CLAUDE.md — Smart Job Application Tracker

Operating guide for any AI coding assistant working in this repo (Claude Code / VS Code).
Read this first, then follow it. For deep detail, see the reference docs listed at the bottom.

---

## 1. Project overview

Full-stack "Smart Job Application Tracker" — a monorepo.

- **Backend:** Spring Boot **3.2.0**, **Java 17**, Maven (`pom.xml` at repo root). Package `com.smartjobtracker`. Spring Data JPA + Hibernate, **Flyway** migrations, **PostgreSQL** in prod / **H2** in tests. Stateless **JWT** auth (Bearer tokens, not cookies), Spring Security 6.2. Apache **PDFBox/POI** for resume text extraction. `@EnableScheduling` is on `Application`.
- **Frontend:** React 18 + **Vite 8** (Rolldown) + Tailwind 3, in `frontend/`. `react-router-dom` v6, axios (`frontend/src/api/axios.js`, baseUrl `/api`, JWT read from localStorage), `lucide-react` icons.
- **Deployment:** Frontend → **Vercel** (Root Directory = `frontend`; `VITE_API_BASE` baked in at build time). Backend → **Render** via Docker. CI in `.github/workflows/ci.yml`; deploy in `.github/workflows/deploy.yml`.

There is **no `/api` context-path** — each `@RequestMapping` hardcodes `/api/...`.

---

## 2. How I want you to work (MUST follow)

### 2.1 The loop — run this for every feature

```
PLAN → LIST FILES → IMPLEMENT → TEST → BUILD → FIX ERRORS → REPORT CHANGES
```

- **Before coding:** list the files you will change and explain the implementation. Wait if the plan is risky (see rule 22).
- **After coding:** run backend tests, run the backend build, run the frontend build/tests, and fix any errors your change introduced.
- **Implement only the feature explicitly requested.** Do not scope-creep into other phases or "nice to haves."

### 2.2 Standing rules (all 22 apply to every change)

1. Inspect existing code before changing it.
2. Reuse existing patterns.
3. Make minimal changes.
4. Do not rewrite unrelated functionality.
5. Do not break existing APIs.
6. List files to be modified before implementation.
7. Use DTOs.
8. Validate input.
9. Use global (or appropriately scoped) exception handling.
10. Never hardcode secrets.
11. Use environment variables.
12. Create Flyway migrations for database changes.
13. Add unit tests.
14. Add integration tests for critical flows.
15. Run backend tests after changes.
16. Run frontend build/tests after changes.
17. Fix errors introduced by your changes.
18. Do not silently change existing behavior.
19. Use transactions where appropriate.
20. Make background jobs idempotent.
21. Add proper logging without logging sensitive data.
22. Stop and explain if an architectural decision is risky.

### 2.3 Anti-fabrication contract (product-critical)

Never invent skills, experience, projects, education, or achievements. AI/extraction may only
**rewrite or re-emphasize verified information** that literally appears in the source. When a value
is unknown (salary, logo, posted date, etc.), show "unavailable" — never guess. Label any AI-derived
figures as estimates. The user's original resume is never modified.

---

## 3. Build, test, run

Requires **JDK 17** and **Node 22** locally.

```bash
# Backend (from repo root)
mvn -B test         # run tests
mvn -B package      # build jar (runs tests)
mvn spring-boot:run # run locally

# Frontend
cd frontend
npm ci
npm run build       # production build (Vite/Rolldown)
npm run dev         # local dev server
```

CI runs exactly `mvn -B test package` (JDK 17) and `npm ci && npm run build` (Node 22), plus Cypress
(`continue-on-error`). **CI + Vercel/Render are the source of truth for build/test status.**

---

## 4. Architecture & conventions

**Layering:** Controller → Service → Repository → Entity (no mapper layer). Keep this shape.

**House style (match the surrounding files):**
- Entities and DTOs use **manual getters/setters** — the existing entities do *not* use Lombok even though it's on the classpath. Follow the file you're editing.
- **DTOs** for all request/response bodies (never return entities directly — they leak internals). `/users/me` is the existing DTO-wrapped example.
- **Validation** via `jakarta.validation` annotations on DTOs + `@Valid` on controller params (see `PasswordResetController`, `CandidateProfileDto`).
- **Exception handling:** there is no global `@ControllerAdvice`. New controllers get a **scoped** `@RestControllerAdvice(assignableTypes = XController.class)` so 400/404 bodies are clean without changing how existing controllers report errors. (See `ProfileExceptionHandler`.)
- **`currentUserId()`** is resolved from the JWT `Authentication` → email → `UserRepository.findByEmail` → id. This helper is duplicated across controllers today; reuse the pattern.
- **Logging:** log ids and counts, never resume text, tokens, or profile content.
- **Transactions:** annotate service methods (`@Transactional`, `readOnly=true` for reads).

**Entities (anemic; foreign keys are raw `Long`, no `@ManyToOne`):**
`User`(email unique, passwordHash), `JobApplication`(userId, companyName, roleTitle, jobDescription, status enum, appliedDate, createdAt), `Resume`(userId, fileName, extractedText, uploadedAt), `Reminder`(userId, applicationId, remindAt, type, status, message), `ApplicationStatusHistory`(applicationId, status, changedAt, remark), `PasswordResetToken`(tokenHash, userEmail, expiresAt, used), `CandidateProfile` (see §6). Enums: `ApplicationStatus{APPLIED,OA,INTERVIEW,OFFER,REJECTED,WITHDRAWN}`, `ReminderType{INTERVIEW,FOLLOW_UP,CUSTOM}`, `ReminderStatus{PENDING,SENT}`.

**API surface (all `/api/**`, JWT Bearer except `/auth/**`):** `/auth/register|login|forgot-password|reset-password`, `/users/me`, applications CRUD + `/{id}/status` (PATCH) + `/{id}/history`, `/resume/upload` + `/resume/me`, `/match/score`, `/reminders/upcoming|POST|DELETE`, `/profile` + `/profile/extract` + `/profile` (PUT), `/api/health`. New `/api/**` paths are auto-protected by `anyRequest().authenticated()` — no `SecurityConfig` change needed for authenticated endpoints.

**⚠ Backward-compatible JSON field names the React app depends on — DO NOT rename:**
- application `{id, companyName, roleTitle, jobDescription, status, appliedDate}`
- login `{token}`
- match `{matchScore, matchedKeywords[], missingKeywords[]}`
- reminder `{id, type, message, remindAt}`
- resume `{id, fileName, uploadedAt}`

---

## 5. Database & migrations discipline

- **Flyway owns the prod schema.** Migrations `V1..V6` live in `src/main/resources/db/migration/`. They are **immutable** once written — never edit an existing migration; add a new `V{n+1}__*.sql`. All migrations are **additive**.
- **Prod (PostgreSQL):** `ddl-auto: validate` — entity mappings must exactly match the migration columns.
- **Tests (H2, `application-test.yml`):** `ddl-auto: update` + `flyway.enabled: false` — Hibernate auto-creates the test schema from entities; **migrations do NOT run in tests.** This means an entity/migration mismatch won't fail tests but WILL fail prod startup. Keep them in sync by hand.
- **Portability rule:** store list/JSON data as **JSON in `TEXT` columns** (not JSONB) so the mapping is identical on H2 and PostgreSQL. The service layer (Jackson) is the only place that (de)serializes it.
- Backup/restore helpers: `scripts/db_backup.sh` / `scripts/db_restore.sh` (env-driven `pg_dump`/`pg_restore`).

---

## 6. Progress so far

**Safe baseline:** git tag `baseline-pre-ai-agent` → commit `01b2129` marks the pre-AI-agent state. See `BASELINE_REPORT.md`.

**✅ Phase 1 — "Candidate Profile & Resume Analysis" (complete, additive).**
Users extract structured data from their most recent (or a chosen) resume, review/edit it, and save it.
Extraction is **deterministic and offline** (curated dictionary + word-boundary regex + section parsing over `resume.extractedText`) — deliberately **not** an LLM yet, so it's offline-testable and non-fabricating. It sits behind one replaceable seam (`ResumeProfileExtractor` → `ExtractedProfile`) so a future AI-backed version drops in with no change to the service, DTO, controller, or schema.

Files added:
- `model/CandidateProfile.java` — one row/user; the 7 groups (skills, programmingLanguages, frameworks, projects, education, experience, preferredRoles) stored as JSON in `TEXT` columns; references `sourceResumeId` (resume never mutated).
- `repository/CandidateProfileRepository.java` (`findByUserId`)
- `service/ResumeProfileExtractor.java`, `service/CandidateProfileService.java`, `service/ProfileNotFoundException.java`
- `dto/CandidateProfileDto.java` (bean-validation `@Size` on lists + elements)
- `controller/ProfileController.java` (`GET /api/profile`, `POST /api/profile/extract?resumeId=`, `PUT /api/profile`), `controller/ProfileExceptionHandler.java` (scoped)
- `resources/db/migration/V5__candidate_profiles.sql` (additive; FKs to `users`/`resumes`)
- `test/.../service/ResumeProfileExtractorTest.java`, `test/.../CandidateProfileIntegrationTest.java`
- `frontend/src/api/profile.js`, `frontend/src/pages/CandidateProfile.jsx`

Files modified (only these two): `frontend/src/App.jsx` (route `/candidate-profile`), `frontend/src/components/Sidebar.jsx` (nav link). No existing backend file was changed.

Verification note: the extractor's word-boundary regex was fixed so a term ending a sentence (`"…on Linux."`) matches, while `node` still won't match inside `node.js` and `asp.net` doesn't yield a spurious `.NET`. Logic validated by unit + integration tests; confirm green in CI.

**✅ Phase 2a — Job Discovery Providers & Sync (complete, additive).** Provider-neutral discovery supports official Greenhouse, Lever, and Ashby public APIs behind `JobProvider`, with an optional config-gated Apify seam and disabled future-provider seam. Results are rate-limited, retried on transient failures, normalized, cross-provider deduplicated, and idempotently persisted. `POST /api/jobs/discover`, `GET /api/jobs`, and `GET /api/jobs/{id}` are authenticated and DTO-only. No matching, recommendations, AI, scraping, CAPTCHA bypass, or authentication bypass was added.

**✅ Phase 4 — Hybrid Resume vs Job Description Matching (complete, additive).** The new `POST /api/match/hybrid-score` endpoint combines exact verified skill matching, required/preferred weighting, configurable Gemini embedding similarity with deterministic fallback, experience relevance, and role relevance. It returns an explainable breakdown, missing required/preferred skills, strong/partial matches, and recommendations, and persists analyses in `match_analyses`. The existing `/api/match/score` contract is unchanged. Gmail remains deferred.

**⛔ Deferred:** Recommendations ranking, job actions, discovery UI, Gmail, and later phases.

---

## 7. The AI-agent upgrade — direction & confirmed decisions

Full phased plan: **`AI_AGENT_UPGRADE_PLAN.md`** (rev 2). Confirmed tech choices:
- **LLM = Google Gemini** via a thin REST `AiClient` (no Spring Boot upgrade).
- **Semantic matching = pgvector** on the existing Postgres (Gemini embeddings).
- **WhatsApp = Twilio.**
- Rollout order (per the plan): Phase 1 candidate profile/analysis (done) → **Phase 2 Job Discovery & Recommendation** (JobProvider SPI over official career-page APIs only — no scraping/CAPTCHA bypass; normalize/dedup/idempotent sync; explainable matching; discovery UI) → Phase 3 Gmail pipeline → Phase 4 Twilio WhatsApp.
- Planned new tables include: `candidate_profiles` (done), `candidate_skills`, `job_search_preferences`, `job_postings`, `job_skills`, `job_provider_syncs`, `match_analyses`, `job_recommendations`, `saved_jobs`, `resume_analyses`, `resume_versions`, `generated_documents` (+ pgvector columns).

**Reusable seams for the next phases:** `KeywordMatchService.score` (swap substring → embeddings/LLM behind the stable `MatchResponse`), `ResumeService.extractText` (PDFBox/POI), `JobApplicationService.changeStatus` + `ApplicationStatusHistory` (hook for auto-status), `EmailService` (single notifier send-path), the `@Scheduled` poller pattern (template for a Gmail/job poller).

---

## 8. Known tech-debt / security gaps (fix backward-compatibly, only when in scope)

- **JWT secret defaults to `secret123`** (`JwtUtil @Value("${JWT_SECRET:secret123}")`) — should fail-fast when unset in prod.
- **`ReminderController.create` binds the raw `Reminder` entity** → mass-assignment / IDOR (a caller can overwrite any reminder by id). Needs a DTO + ownership check.
- **`MatchController`/`KeywordMatchService` never verify the resume belongs to the caller.**
- **Validation exists only on password-reset + profile DTOs** so far.
- **`ReminderService`** sends from a hard-coded address and swallows errors in an empty catch (reminders may never reach users).
- `currentUserId()` is duplicated across controllers; entities are sometimes returned directly.
- **Frontend:** JWT stored in localStorage (and printed on the Profile page); no axios 401/expiry interceptor; a session-restore race can bounce authenticated users to `/login` on refresh.
- **OAuth login discards Google's tokens** (`OAuth2LoginSuccessHandler` reads only email/name; scopes `openid/profile/email` only). Gmail ingestion (Phase 3) needs a separate incremental-auth flow (`gmail.readonly`, `access_type=offline`) + an encrypted refresh-token store — the single biggest new-subsystem gap.

---

## 9. Environment & secrets

- All secrets via **environment variables** — never commit them. Key ones: `JWT_SECRET`, `DATABASE_URL`/JDBC creds, `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `MAIL_*` (SMTP), `APP_CORS_ALLOWED_ORIGIN_PATTERNS`, and (frontend, build-time) `VITE_API_BASE`. Full setup: **`DEPLOYMENT_SETUP.md`**.
- Do not commit `node_modules`. Stage explicit paths rather than `git add .`.

---

## 10. Reference docs in this repo

- `AI_AGENT_UPGRADE_PLAN.md` — the full phased plan (rev 2), the authority for what to build next.
- `BASELINE_REPORT.md` — build/test/security baseline + backup strategy.
- `DEPLOYMENT_SETUP.md` — Vercel/Render env vars, OAuth + SMTP config.
- `API_CLIENTS.md`, `README.md` — API usage and project setup.
