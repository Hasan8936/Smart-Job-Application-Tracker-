# Smart Job Application Tracker → AI Job Application Agent
## Architecture Analysis & Implementation Plan

**Status:** Proposal for review — **rev 2** (Job Discovery & Recommendation subsystem added 2026-08-26). **No code has been changed.** Nothing will be built until you approve.
**Prepared:** 2026-08-26 · **Scope:** Upgrade the existing tracker into an AI-powered agent — now including a verified candidate profile, provider-abstracted live job search, hybrid explainable job matching, and in-app discovery UI — without breaking any current feature or API.

---

## 0. How to read this document

Sections 1–3 are the **analysis you asked for first** (existing architecture, current database, risks). Sections 4–9 are the **proposal** (target architecture, data model, feature designs, safe implementation order, testing). Section 10 lists the **confirmed decisions** (LLM provider, WhatsApp, vector store, rollout). Section 11 states the **backward-compatibility guarantees** I will hold myself to. **Part B** — added in rev 2 — specifies the Job Discovery & Recommendation subsystem, supersedes the old F10, and revises §8's roadmap to integrate it.

The guiding rule throughout: **existing endpoints, JSON shapes, and DB tables are treated as a frozen contract.** Every new capability is *additive*.

---

## 1. Existing architecture analysis

### 1.1 Stack & topology
Monorepo. Backend is **Spring Boot 3.2.0 / Java 17 / Maven** (`pom.xml` at root); frontend is **React 18 + Vite 8** in `frontend/`. PostgreSQL with **Flyway** migrations, **JWT** (Auth0 `java-jwt`, HMAC-SHA256, Bearer header — not cookies), Spring Security 6.2, optional **Google OAuth2 login**. Deploy: frontend → Vercel, backend → Render (Docker), both auto-deploy on push to `main`. CI (`.github/workflows/ci.yml`) runs `mvn test package` and builds both Docker images.

### 1.2 Backend layering
Clean, conventional separation under `com.smartjobtracker`: `controller/` → `service/` → `repository/` (Spring Data JPA) → `model/` (entities) + `dto/`. `security/` holds JWT + OAuth wiring. `@EnableScheduling` is on the main class (only `ReminderService` uses it today). There is **no `/api` context-path**; every controller hardcodes its `/api/...` path.

### 1.3 API surface (the contract to preserve)

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | RegisterRequest | 201 / 400 |
| POST | `/api/auth/login` | public | AuthRequest | `{token}` / 401 |
| POST | `/api/auth/forgot-password` | public | ForgotPasswordRequest | `{message}` |
| POST | `/api/auth/reset-password` | public | ResetPasswordRequest | `{message}` |
| GET | `/api/users/me` | JWT | – | UserProfile `{id,name,email}` |
| GET | `/api/applications` | JWT | – | `JobApplication[]` |
| POST | `/api/applications` | JWT | ApplicationRequest | JobApplication |
| GET | `/api/applications/{id}` | JWT | – | JobApplication / 404 |
| PUT | `/api/applications/{id}` | JWT | ApplicationRequest | JobApplication |
| DELETE | `/api/applications/{id}` | JWT | – | 204 |
| PATCH | `/api/applications/{id}/status` | JWT | StatusPatchRequest | ApplicationStatusHistory |
| GET | `/api/applications/{id}/history` | JWT | – | `ApplicationStatusHistory[]` |
| POST | `/api/resume/upload` | JWT | multipart `file` | Resume |
| GET | `/api/resume/me` | JWT | – | `Resume[]` |
| POST | `/api/match/score` | JWT | MatchRequest | MatchResponse |
| GET | `/api/reminders/upcoming` | JWT | – | `Reminder[]` |
| POST | `/api/reminders` | JWT | **raw Reminder entity** | Reminder |
| DELETE | `/api/reminders/{id}` | JWT | – | 204 / 404 |
| GET | `/api/health` | public | – | `{status:OK}` |

**Frontend depends on these exact JSON field names** (backward-compat critical): application `{id, companyName, roleTitle, jobDescription, status, appliedDate}`; login `{token}`; match `{matchScore, matchedKeywords[], missingKeywords[]}`; reminder `{id, type, message, remindAt}`; resume `{id, fileName, uploadedAt}`.

### 1.4 Services
- **JobApplicationService** – CRUD + status history; ownership enforced via `findOwnedOrThrow(id, userId)`; `changeStatus()` appends an `ApplicationStatusHistory` row. *(This is the natural hook for AI auto-status updates.)*
- **ResumeService** – upload (≤10 MB) + text extraction (PDFBox for PDF, POI for `.docx`/`.doc`). Scanned PDFs (<50 chars) silently store empty text (no OCR). *(Reusable text pipeline for AI.)*
- **KeywordMatchService** – loads 54 hard-coded skills from `skills.txt` on startup and scores by **naive substring `contains()`** overlap between resume text and JD. Two baked-in weaknesses: substring false positives ("git" ∈ "di**git**al") and a tiny, backend-heavy dictionary missing data/analytics terms. `tokenizeText()` is dead code. *(This is the seam to replace with semantic/LLM matching behind the same DTO.)*
- **ReminderService** – `@Scheduled` every 30 min; sends due reminders. **Bug:** recipient is hard-coded `noreply@example.com` (reminders never reach the user) and the send failure `catch` block is empty (silent). Bypasses `EmailService`.
- **EmailService** – clean, well-logged SMTP primitive. *(Should become the single notification send-path.)*
- **PasswordResetService / UserService** – SHA-256-hashed reset tokens; `UserService` is the `UserDetailsService` (no roles/authorities anywhere).

### 1.5 Security model
Stateless. `JwtFilter` validates the Bearer token, loads `UserDetails`, sets the `SecurityContext`. `SecurityConfig` permits `/api/auth/**`, `/oauth2/**`, `/login/**`, `/error`, health/prometheus; everything else requires auth. CORS allows `https://*.vercel.app` + localhost **with credentials**. Google OAuth login is wired **only if** `GOOGLE_CLIENT_ID/SECRET` are set (conditional bean). On OAuth success, a user is auto-created and redirected to `…/oauth2/callback?token=<JWT>`.

### 1.6 Frontend
`react-router-dom` v6, hooks-only state (no Redux/React Query), Tailwind design system (single source of truth in `src/lib/status.js`). Auth in `AuthContext` stores the JWT in **localStorage**; a single axios **request** interceptor attaches it. Pages: Dashboard, Applications (CRUD + search/filter), ResumeMatch (upload + JD + ScoreRing), Reminders, Profile, and 5 auth pages. Natural AI plug-in points already exist: `ResumeMatch` (optimization suggestions), `ApplicationDrawer` (AI form-fill), Dashboard/Sidebar (recommendations), Reminders (auto-generated reminders — `CHANGES.md` notes this was deliberately left out).

---

## 2. Current database analysis

Schema is owned by Flyway (`ddl-auto: validate`), migrations `V1__init` → `V4__password_reset_tokens`, all additive.

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, name, email (unique), password_hash, created_at | No phone, no roles, no Google-linked flag |
| `resumes` | id, user_id→users, file_name, extracted_text, uploaded_at | Full text stored inline |
| `applications` | id, user_id→users, company_name, role_title, job_description, status, applied_date, created_at | status is a string enum |
| `reminders` | id, user_id→users, application_id→applications, remind_at, type, status, message, created_at | |
| `application_status_history` | id, application_id→applications (ON DELETE CASCADE), status, changed_at, remark | Indexed on application_id |
| `password_reset_tokens` | id, token_hash (unique), user_email, expires_at, used | |

**Observations relevant to the upgrade:**
- Foreign keys exist in SQL, but JPA entities model them as **raw `Long` ids** (no `@ManyToOne`) — ownership is enforced in service code, not by navigation. New tables should follow the same convention for consistency.
- Mixed temporal types (`OffsetDateTime`, `LocalDateTime`, `LocalDate`) against `TIMESTAMP`/`DATE` columns — offsets are effectively dropped. New tables should standardize (recommend `timestamptz`).
- No `pgvector` extension yet (needed if we choose embeddings-based matching).
- Migration convention is simple `V{n}__desc.sql`; new work continues at **V5+**.

---

## 3. Risks, bugs & technical debt

Prioritized. These inform *why* a foundation phase comes before AI features.

### 3.1 Security — high
1. **Weak default JWT secret.** `JwtUtil` falls back to `secret123` if `JWT_SECRET` is unset → forgeable tokens / auth bypass. Same weak-default pattern for the DB password.
2. **Mass-assignment / IDOR in `POST /api/reminders`.** It binds the raw `Reminder` entity; a client can pass `"id": <someone else's reminder>` and JPA will *update* it (and reassign ownership). Also no check that `applicationId` belongs to the caller.
3. **No ownership check in `/api/match/score`.** Any authenticated user can score against another user's `resumeId`; a missing resume silently scores 0.

### 3.2 Security — medium/low
4. **No validation on register/login** — empty/weak passwords and malformed emails are accepted (strength is only enforced on password *reset*).
5. **No rate limiting** on auth/forgot-password (brute force, email bombing).
6. **JWT in the URL** on OAuth callback (leaks via history/referrer/logs) and **JWT in localStorage** on the frontend (XSS-exposed; also printed on the Profile page).
7. **CORS `allowCredentials(true)` with a wildcard Vercel pattern** — low impact today (auth is header-based, not cookies) but worth tightening.

### 3.3 Correctness bugs
8. **Reminders never reach users** — hard-coded `noreply@example.com` recipient; the whole feature is effectively a stub.
9. **Silent failures** — empty `catch` in the reminder scheduler.
10. **Wrong status codes** — `IllegalArgumentException` (e.g. "file too large") and DB constraint violations surface as **500** instead of 400, because there is **no global exception handler**.
11. **Frontend session-restore race** — a valid, logged-in user is bounced to `/login` on hard refresh (no auth-bootstrap loading state).

### 3.4 Tech debt / smells
12. **No global exception handling** (`@ControllerAdvice`) — inconsistent error responses.
13. **Entities returned directly** from most endpoints (schema coupling; full `extractedText` shipped in resume lists). Only `/users/me` uses a DTO.
14. **`currentUserId()` duplicated** across three controllers; the numeric user id is re-looked-up on every request even though the filter already loaded the user.
15. **Weak matching algorithm** + tiny dictionary; `tokenizeText()` dead code.
16. **No pagination** on any list endpoint.
17. **Duplicate health endpoints** (`/api/health` + Actuator).
18. **Spring Boot 3.2.0 is past OSS support** — a minor bump (3.3.x/3.4.x) is advisable for security patches and is also what Spring AI 1.0 expects (see §4.4).

---

## 4. Proposed target architecture

### 4.1 Principles
Keep the existing Controller→Service→Repository→Entity layering and **add**, per new capability, a self-contained vertical slice. Introduce a small set of cross-cutting abstractions (AI client, notifications, async) that features depend on, so no feature is coupled to a specific vendor. Uphold SOLID: features depend on **interfaces** (`AiClient`, `Notifier`, `EmailIngestionClient`), not concrete SDKs.

### 4.2 New package layout (additive)
```
com.smartjobtracker
├─ ai/                 # AiClient interface + provider impl(s), prompt templates, structured-output parsing
│   ├─ AiClient.java   (interface + Gemini REST impl)
│   ├─ classification/ EmailClassificationService
│   ├─ matching/       SemanticMatchService, EmbeddingService
│   └─ extraction/     JdAnalysisService, DateExtractionService, FormFillService
├─ profile/            CandidateProfileService, ResumeProfileExtractor           # Part B
├─ jobs/                                                                          # Part B
│   ├─ provider/       JobProvider (interface) ← OfficialCareerPageProvider, ApifyJobProvider, FutureJobProvider
│   ├─ discovery/      JobDiscoveryService, JobNormalizer, JobDeduplicator, JobSyncService
│   ├─ match/          HybridMatchService, ExperienceRelevanceScorer, MatchExplanationBuilder
│   ├─ recommend/      RecommendationRankingService
│   └─ actions/        JobActionService (save / apply / generate cover-letter, cold-email, …)
├─ integration/gmail/  GmailConnectionService, GmailPoller, GmailApiClient
├─ notification/       Notifier (interface), EmailNotifier, WhatsAppNotifier, NotificationService
├─ config/             AsyncConfig, AiConfig, GmailOAuthConfig, EncryptionConfig, JobProviderConfig
└─ common/             GlobalExceptionHandler, @CurrentUser resolver, ApiError DTO
```
Existing packages (`controller`, `service`, `model`, `dto`, `security`, `repository`) stay; new controllers/DTOs live alongside the current ones.

### 4.3 Cross-cutting foundations (built once, reused by all features)
- **Global exception handling** — `@RestControllerAdvice` returning a consistent `ApiError {timestamp, status, error, message, path}`; maps `IllegalArgumentException`→400, `ResponseStatusException` passthrough, validation errors→400 with field details. *Additive: success responses are unchanged.*
- **`AiClient` abstraction** — one interface with `complete(prompt)`, `completeStructured(prompt, schema)`, and `embed(text)`. Concrete impl is **Gemini** (§10 #1). This is the single seam every AI feature calls, so the provider can change without touching features.
- **`Notifier` abstraction** — `EmailNotifier` (wraps the existing `EmailService`) + `WhatsAppNotifier`; `NotificationService` fans out by user preference. Reminder + status-change events publish through this.
- **Async execution** — `@Async` + a bounded `ThreadPoolTaskExecutor` so LLM/Gmail calls never block request threads or the single scheduler thread. (Multi-instance scaling would later need a real queue; out of scope now.)
- **Secrets** — every key via env var (`AI_API_KEY`, `GMAIL_*`, `WHATSAPP_*`, `APP_ENCRYPTION_KEY`); **fail-fast if `JWT_SECRET` is still the default in a non-dev profile.** No hardcoded credentials. `DEPLOYMENT_SETUP.md` updated.

### 4.4 AI integration approach
Two viable paths; I recommend **A** to avoid touching the running stack, with **B** as an optional later step:
- **A (chosen): thin REST-based `AiClient`** using Spring's `RestClient`/`WebClient` against the **Google Gemini** HTTP API (both `generateContent` and the embeddings endpoint). Works on the current Boot 3.2.0, zero framework upgrade, full control over retries/timeouts/cost.
- **B (optional): adopt Spring AI.** Cleaner abstractions and built-in vector-store support, but Spring AI 1.0 targets Boot 3.3+/3.4, so it requires a minor Spring Boot upgrade first — do it as its own tested step, not bundled with a feature.

### 4.5 Semantic matching & vector storage
For AI resume/JD matching (feature 7) we'll use **`pgvector` on the existing Postgres** (confirmed; Render's managed Postgres supports the extension) — no new infrastructure, embeddings (from Gemini's embedding endpoint) stored next to the data.

### 4.6 Backward-compatibility strategy
- Existing endpoints keep their paths **and JSON field names**. When I introduce response DTOs to stop leaking entities, the DTO fields mirror the current names exactly, guarded by tests.
- New capabilities are **new endpoints** (e.g. `POST /api/match/ai-score`) or **additive fields**, never breaking changes to `POST /api/match/score`.
- All migrations are **additive** (new tables; new columns nullable/defaulted). No destructive changes to `users`/`applications`/etc.
- The frontend keeps working untouched at each phase; new UI is added, old UI is not rewritten.

---

## 5. New data model & migrations (V5+)

All additive. Exact columns finalized per phase.

| Migration | Table / change | Purpose |
|---|---|---|
| V5 | `gmail_connections` (user_id, email, encrypted_refresh_token, scopes, connected_at, last_history_id, status) | Store per-user Gmail authorization + poll cursor (refresh token **encrypted at rest**). |
| V6 | `ingested_emails` (user_id, gmail_message_id UNIQUE, thread_id, from_addr, subject, snippet, received_at, raw_hash, processed_at) | Dedup + audit of pulled messages (store minimal PII). |
| V7 | `email_classifications` (ingested_email_id, category, confidence, extracted_json, model, created_at) | AI classification results + structured extraction. |
| V8 | `application_email_links` (application_id, ingested_email_id, match_confidence) | Link an email to the application it updated. |
| V9 | `job_postings` (user_id, source_url, company, title, description, parsed_json, created_at) | JD analysis / recommendations source. |
| V10 | `pgvector` extension + embedding columns (e.g. `resume_embeddings`, `job_posting_embeddings`) | Semantic matching (pgvector confirmed — §10 #3). |
| V11 | `notification_preferences` (user_id, channel, phone_e164, whatsapp_opt_in, verified) | WhatsApp/email routing + opt-in. |
| V12 | `ai_suggestions` (user_id, type, target_id, payload_json, status, created_at) | Persist resume-optimization / form-fill / low-confidence status suggestions for user review. |

> **Job Discovery (Part B) adds further tables** — `candidate_profiles`, `candidate_skills`, `job_search_preferences`, `job_postings` (richer), `job_skills`, `job_provider_syncs`, `match_analyses`, `job_recommendations`, `saved_jobs`, `resume_analyses`, `resume_versions`, `generated_documents`, plus pgvector embedding columns — detailed in **§B3**. The simple `V9 job_postings` row above is **superseded** by the richer `job_postings` defined there. Final Flyway numbers are assigned sequentially **at build time in phase order** (discovery tables land in Phase 1–2, i.e. before the Gmail/notification tables), so treat the V-numbers here as logical, not literal.

---

## 6. Feature-by-feature design (all 10)

For each: what it reuses, what's new, and the files touched. "New" files are additive; "modify" edits preserve existing behavior.

**F1 — Gmail OAuth + automatic job-email detection.** New *incremental-auth* flow (`gmail.readonly`, `access_type=offline`, `prompt=consent`) separate from the existing login OAuth, so login is untouched. New `GmailConnectionService` persists the encrypted refresh token (V5); `GmailPoller` (`@Scheduled`) lists messages since `last_history_id` and writes `ingested_emails` (V6). New: `integration/gmail/*`, `GmailOAuthConfig`, controller `POST /api/gmail/connect` + `DELETE /api/gmail/disconnect` + `GET /api/gmail/status`; deps `google-api-services-gmail`, `google-auth-library-oauth2-http`. Modify: `SecurityConfig` (permit the new callback), Sidebar/Profile UI ("Connect Gmail").

**F2 — AI email classification.** `EmailClassificationService` calls `AiClient.completeStructured` → category enum {APPLICATION_ACK, ASSESSMENT_INVITE, INTERVIEW_INVITE, OFFER, REJECTION, RECRUITER_OUTREACH, OTHER} + confidence, stored in `email_classifications` (V7). Idempotent per `gmail_message_id`. New: `ai/classification/*`. Reuses `AiClient`.

**F3 — Automatic application status updates.** Match a classified email to an application (company/domain/subject/thread heuristics → `application_email_links`, V8). High confidence → call the existing `JobApplicationService.changeStatus()` with a system remark ("Auto-updated from email: …"); low confidence → write an `ai_suggestions` row for user confirmation (no silent wrong changes). Reuses status-history mechanism entirely. Modify: `JobApplicationService` (add a system-initiated path), Dashboard UI (a "suggested updates" area).

**F4 — Interview & deadline extraction.** From INTERVIEW/ASSESSMENT emails (and JDs), `DateExtractionService` extracts datetime(s) via structured LLM output → create `Reminder`s. **Depends on the Phase-0 reminder-recipient fix** so reminders actually send. New: `ai/extraction/DateExtractionService`. Reuses `Reminder` + `NotificationService`.

**F5 — WhatsApp notifications.** `WhatsAppNotifier` implementing `Notifier`; `NotificationService` routes by `notification_preferences` (V11) with opt-in + phone verification. Provider: **Twilio WhatsApp** (confirmed; sandbox for dev, creds via `TWILIO_*`). New: `notification/*`, `POST /api/notifications/preferences`. Modify: reminder scheduler + status-change events to publish through `NotificationService` (also fixes F-era email path).

**F6 — Job link & JD analysis.** `JdAnalysisService`: accept pasted JD text (primary) or a URL (best-effort server-side fetch; many boards block scraping / need JS — expectations set) → structured JD (title, company, must-have skills, responsibilities) in `job_postings` (V9). New: `ai/extraction/JdAnalysisService`, `POST /api/jd/analyze`. Feeds F7–F9.

**F7 — AI resume/job matching.** `SemanticMatchService` = embeddings similarity (pgvector, §10 #3) + LLM gap analysis. Exposed as a **new** `POST /api/match/ai-score` returning the existing `{matchScore, matchedKeywords, missingKeywords}` **plus** new fields (semanticScore, sectionFeedback, suggestions). The old `/api/match/score` stays. **Also fixes the missing ownership check.** New: `ai/matching/*`. Modify: `ResumeMatch.jsx` (richer result panel).

**F8 — Resume optimization suggestions.** `POST /api/resume/{id}/optimize` (with target JD) → LLM rewrite suggestions, missing keywords, ATS notes, persisted to `ai_suggestions` (V12). Reuses resume text + `AiClient`. Modify: `ResumeMatch.jsx`.

**F9 — AI-assisted application form filling.** `FormFillService`: given a JD/URL + the user's resume/profile, generate suggested field values and answers to common questions (`POST /api/applications/ai-fill`). New: `ai/extraction/FormFillService`. Modify: `ApplicationDrawer.jsx` ("Autofill from JD" button).

**F10 — AI job recommendation/search. → SUPERSEDED & EXPANDED by Part B.** The original single-endpoint recommender is replaced by the full **Job Discovery & Recommendation** subsystem: verified candidate profile, provider-abstracted live search, hybrid explainable matching, filtering, gap analysis, job actions, and discovery UI. See Part B.

---

## 7. Foundation phase (Phase 0) — hardening, fully backward-compatible

Built and tested before any AI feature, because every later feature leans on it and it de-risks the codebase:
1. Global exception handler + consistent `ApiError` (fixes the 500-instead-of-400 issues).
2. `@CurrentUser` argument resolver (removes the duplicated `currentUserId()` and the redundant per-request user lookup).
3. Response **DTOs** for endpoints currently leaking entities — **mirroring exact field names**, with tests asserting the shape is unchanged.
4. Security fixes: fail-fast on default `JWT_SECRET` in prod; replace the raw-entity `POST /reminders` with a `ReminderRequest` DTO (kills mass-assignment); add the resume-ownership check to matching.
5. Reminder fix: resolve the real recipient, route through `EmailService`/`NotificationService`, log failures.
6. `AiClient` + `Notifier` + async config scaffolding (no external calls yet).
7. Frontend: axios **response interceptor** (global 401 → logout) + an auth-bootstrap loading state (fixes the refresh-bounce).

---

## 8. Safe implementation order (integrated roadmap — rev 2)

Revised to fold in the Job Discovery & Recommendation subsystem (Part B). Ordered by dependency and blast radius, and aligned with the end-to-end product workflow (profile → discovery → match → recommend → save/apply → tracker → Gmail → notify).

| Phase | Delivers | Why here |
|---|---|---|
| **0. Foundation** | §7 hardening + `AiClient`/`Notifier`/async scaffolding | Prereq for everything; backward-compatible; de-risks. |
| **1. Candidate Profile + AI resume/JD tools** | Candidate Profile extraction & editor (Part B) + F7 semantic matching (pgvector) + F8 resume optimization + F6 JD analysis + F9 form-fill | Builds the **verified profile + embeddings** every downstream feature needs; smallest blast radius; validates the AI client on real value. |
| **2. Job Discovery & Recommendation** | `JobProvider` abstraction + official-API providers (Greenhouse/Lever/Ashby) → normalization/dedup/**idempotent sync** → hybrid **explainable** matching → recommendations + transparent ranking → filters/preferences → job analysis + gap analysis → job actions (tracker-integrated) → discovery pages + dashboard. Apify optional, config-gated. | The big new subsystem. Delivered in increments **2a** (providers + sync), **2b** (matching + ranking + recommendations), **2c** (UI + actions + dashboard). Absorbs the old F10. |
| **3. Gmail automation pipeline** | F1 → F2 → F3 → F4 | Detects status updates on applications created via discovery/tracker (matches the workflow). External API + PII; sequential. |
| **4. Notifications** | Twilio WhatsApp (F5) wired into reminders + status-change events | Cross-cutting channel; tail of the workflow. |

Each sub-feature ships behind tests and is independently deployable (Render/Vercel auto-deploy on `main`), so we can pause between increments. **We deliberately do not build everything at once** — Phase 2 lands as 2a → 2b → 2c.

---

## 9. Testing strategy

- **Unit tests** for every new service, with `AiClient`, Gmail, and WhatsApp **mocked** — no real external calls in CI (cost + flakiness).
- **Integration tests** continue the existing H2 pattern; for pgvector-dependent tests use **Testcontainers (Postgres + pgvector)** since H2 lacks vector ops (or keep vector logic behind an interface that's stubbed in H2 runs).
- **Backward-compat contract tests** asserting the existing endpoints' JSON field names/shapes are unchanged — this is the guardrail for "don't break existing features."
- Extend the existing `AuthIntegrationTest`/`ApplicationIntegrationTest` style; keep `KeywordMatchServiceTest` and add tests for the new matcher.
- CI stays green: mocked externals, deterministic prompts/fixtures.

---

## 10. Decisions — CONFIRMED (2026-08-26)

| # | Decision | Choice | Implication |
|---|---|---|---|
| 1 | LLM provider | **Google Gemini** | `AiClient` first impl targets the Gemini REST API; key via `AI_API_KEY`/`GEMINI_API_KEY`. Swappable behind the interface. |
| 2 | WhatsApp | **Twilio WhatsApp** | `WhatsAppNotifier` uses Twilio (sandbox for dev); creds via `TWILIO_*` env vars. |
| 3 | Semantic matching | **pgvector on existing Postgres** | V10 enables the `pgvector` extension; embeddings stored in Postgres. Gemini embeddings via `AiClient.embed`. |
| 4 | Rollout order | **Phased (rev 2, see §8)** | Phase 0 → Phase 1 (Candidate Profile + matching/resume) → Phase 2 (Job Discovery & Recommendation, 2a→2b→2c) → Phase 3 (Gmail pipeline) → Phase 4 (WhatsApp). Old F10 absorbed into Phase 2. |

---

## 11. Backward-compatibility guarantees (what will NOT change)

- No existing endpoint path, HTTP method, request field, or response field name is removed or renamed.
- No existing DB table/column is dropped or retyped; migrations are additive only.
- The existing login, Google-login, password-reset, applications CRUD, reminders, resume upload, and `/match/score` flows keep working exactly as today at every phase.
- The current frontend keeps functioning; AI UI is added, not swapped in.
- Existing tests keep passing; new contract tests lock the current behavior in place.

**Nothing above is built yet. On your approval of this updated plan, I'll start with Phase 0 (backward-compatible hardening), then Phase 1 (Candidate Profile + AI resume/JD tools), and check in before each phase and each Phase-2 increment.**

---

# Part B — AI Job Discovery & Recommendation subsystem

> Added in **rev 2** (2026-08-26) in response to the expanded requirements. This part is self-contained and answers the nine items you asked for, in order. It **supersedes the old F10** and overlaps F6–F9 (which stay in Phase 1 as the profile/resume foundations this subsystem consumes). Everything here is **additive** and obeys the same non-negotiables: DTOs only (never expose entities), env-var secrets, Flyway additive migrations, global validation/exception handling, backward compatibility, and — new for this part — **verified-only anti-fabrication guardrails** and **provider/ToS compliance**.

## B1. Updated architecture

The subsystem sits on top of the Phase-0 foundation and the Phase-1 **Candidate Profile**, and reuses the existing tracker (`JobApplication`, `ApplicationStatusHistory`, `Reminder`) as the system of record for anything the user acts on. The end-to-end flow you described maps onto these layers:

```
Resume / Profile
   └─(F8/Phase1) ResumeProfileExtractor ──► CandidateProfile (+ verified CandidateSkills, embedding)
                                                   │
User preferences (JobSearchPreferences) ───────────┤
                                                   ▼
Job Discovery  ──►  JobProvider SPI  ──►  [OfficialCareerPageProvider | ApifyJobProvider(optional) | FutureJobProvider]
   (JobDiscoveryService)                     │  official public APIs, rate-limited, ToS-respecting
                                             ▼
                       JobNormalizer ──► JobDeduplicator ──► JobSyncService (idempotent upsert)
                                             │
                                             ▼   persisted JobPostings (+ JobSkills, embedding)
                                             ▼
Matching  ──►  HybridMatchService  = deterministic skill match  (required + preferred weighting)
   (per user × job)                 + semantic similarity (pgvector cosine)
                                     + ExperienceRelevanceScorer
                                     ──► MatchExplanationBuilder ──► MatchAnalysis (sub-scores + breakdown stored)
                                             │
                                             ▼
Recommendation ──► RecommendationRankingService (transparent factors) ──► JobRecommendation (rank, priority, factors)
                                             │
                                             ▼
Frontend (React) ── Discovery / Recommended / Saved / Job Details (Overview · Match · Gap · Resume Recs) + Dashboard stats
                                             │  user acts
                                             ▼
Job Actions ──► JobActionService ──► SavedJob state machine  &  tracker integration:
                                        Mark-as-Applied ─► create/patch JobApplication (dedup, no duplicate rows)
                                        Apply with AI  ─► F9 form-fill workflow (draft, user confirms)
                                        Generate*      ─► GeneratedDocument (cover letter / cold email / interview Qs)
                                             │
                                             ▼
Existing Tracker ─► (Phase 3) Gmail detects updates ─► classify ─► auto status ─► (Phase 4) WhatsApp reminders
```

Two architectural rules govern the whole subsystem. First, **the Candidate Profile is the single source of truth for the user's skills/experience**, and every AI output is constrained to it — the matcher and the resume-gap analyzer may *emphasize, reorder, or flag missing* items, but may never assert the user has a skill/experience/project that isn't verified in the profile. Second, **providers are fully decoupled** behind an SPI: adding/removing a source (including Apify) is a config change, never a code change to the discovery/matching layers.

## B2. New components / modules required

**Backend (new packages under `com.smartjobtracker`):**

| Package | Component | Responsibility |
|---|---|---|
| `profile/` | `CandidateProfileService` | CRUD + versioned edits of the profile; owns verified/unverified skill state. Reuses `ResumeService.extractText`. |
| `profile/` | `ResumeProfileExtractor` | One-time structured extraction from resume text via `AiClient` → profile fields + skills (each tagged `source=RESUME, verified=true`). "Never re-ask for data already in resume/profile." |
| `jobs/provider/` | `JobProvider` (interface) + `OfficialCareerPageProvider`, `ApifyJobProvider`, `FutureJobProvider` | Pluggable job sources (see **B6**). |
| `jobs/provider/client/` | `GreenhouseClient`, `LeverClient`, `AshbyClient` | Thin REST clients for the official public board APIs used by `OfficialCareerPageProvider`. |
| `jobs/discovery/` | `JobDiscoveryService`, `JobNormalizer`, `JobDeduplicator`, `JobSyncService` | Orchestrate providers → normalize to `JobPosting` → dedupe → **idempotent** persist via sync cursors. |
| `jobs/match/` | `HybridMatchService`, `ExperienceRelevanceScorer`, `MatchExplanationBuilder` | Compute the explainable sub-scores and persist the breakdown. Wraps/extends existing `KeywordMatchService`. |
| `jobs/recommend/` | `RecommendationRankingService` | Rank matched jobs by transparent factors; produce priority + factor breakdown. |
| `jobs/actions/` | `JobActionService`, `GeneratedDocumentService` | The 11 job actions; tracker integration; AI document generation (verified-only). |
| `jobs/embedding/` | `JobEmbeddingService` | Embeds JD text via `AiClient.embed`; stores pgvector column. (Shares infra with Phase-1 profile embedding.) |

**Reused (not rebuilt):** `AiClient` (Gemini, from Phase 0), pgvector infra (Phase 0/1), `ResumeService`, `KeywordMatchService` (deterministic core), `JobApplicationService.changeStatus` + `ApplicationStatusHistory` (for Mark-as-Applied), `GlobalExceptionHandler`/`@CurrentUser` (Phase 0), `Notifier` (later phases).

**Frontend (new, under `frontend/src/`):** service modules `api/profile.js`, `api/jobs.js`; pages `Discovery`, `Recommended`, `SavedJobs`, `JobDetails`, `ProfileEditor`; components `JobCard`, `JobFilters`, `JobList`, `MatchBreakdown`, `GapAnalysisPanel`, `ResumeRecommendations`, `JobActionsMenu`, `DiscoveryStats`. Reuses existing `ScoreRing`, `StatusBadge`, `StatCard`, `Layout`, `Sidebar`, `ProtectedRoute`, `axios` client.

## B3. Database changes

All **new tables**, additive only, Flyway-managed, `ddl-auto: validate` unchanged. Every table gets **audit fields** (`created_at`, `updated_at` as `timestamptz`), **FKs** to `users(id)` (and between the new tables) with sensible `ON DELETE`, **unique constraints** for idempotency/dedup, and **indexes** on every FK + common filter/sort column. Final Flyway version numbers are assigned sequentially at build time in phase order (these land in Phase 1–2). The rev-1 `V9 job_postings` sketch is **superseded** by the richer definition below.

| Table | Key columns | Constraints / indexes | Notes |
|---|---|---|---|
| `candidate_profiles` | `id`, `user_id`, `full_name`, `years_experience`, `seniority_level`, `github_url`, `linkedin_url`, `portfolio_url`, `preferred_roles` (jsonb), `preferred_locations` (jsonb), `work_modes` (jsonb), `industries` (jsonb), `projects` (jsonb), `education` (jsonb), `experience` (jsonb), `tech_stack` (jsonb), `source_resume_id`, `profile_embedding` (vector), audit | **unique(user_id)**; FK→users, FK→resumes(source_resume_id); idx(user_id) | One editable profile per user. Extracted once from resume, then user-editable. JSONB for the flexible list fields. |
| `candidate_skills` | `id`, `profile_id`, `name`, `normalized_name`, `category` (enum: LANGUAGE/FRAMEWORK/TOOL/DOMAIN/SOFT), `source` (enum: RESUME/USER_EDITED), `verified` (bool) | **unique(profile_id, normalized_name)**; FK→candidate_profiles ON DELETE CASCADE; idx(profile_id) | `verified=true` only when it came from the resume or the user affirmed it. **Matcher reads only verified skills.** |
| `job_search_preferences` | `id`, `user_id`, `min_match_pct`, `roles` (jsonb), `locations` (jsonb), `work_modes` (jsonb), `experience_level`, `salary_min`, `salary_max`, `date_posted_days`, audit | **unique(user_id)**; FK→users | User-tunable filter defaults (threshold changeable). |
| `job_postings` | `id`, `provider`, `external_id`, `dedupe_hash`, `company`, `company_logo_url` (nullable), `title`, `location`, `employment_type`, `work_mode`, `salary_min`/`salary_max`/`salary_currency` (all nullable), `apply_url`, `posted_at` (nullable), `description`, `raw_json` (jsonb), `job_embedding` (vector), audit | **unique(provider, external_id)**; idx(dedupe_hash), idx(posted_at), idx(company), idx(provider) | Nullable salary/logo/date columns are the schema-level enforcement of "mark unavailable, never fabricate." `dedupe_hash` powers cross-provider dedup. |
| `job_skills` | `id`, `job_posting_id`, `name`, `normalized_name`, `requirement` (enum: REQUIRED/PREFERRED) | **unique(job_posting_id, normalized_name)**; FK→job_postings ON DELETE CASCADE; idx(job_posting_id) | Parsed from the JD; drives required-vs-preferred weighting. |
| `job_provider_syncs` | `id`, `provider`, `query_key`, `cursor`, `last_synced_at`, `status`, audit | **unique(provider, query_key)**; idx(provider) | Makes discovery **idempotent** — re-running a query resumes from cursor instead of duplicating. |
| `match_analyses` | `id`, `user_id`, `job_posting_id`, `overall_score`, `skill_score`, `experience_score`, `role_score`, `location_score`, `industry_score`, `salary_fit` (nullable), `career_growth_score`, `application_priority`, `missing_required` (jsonb), `missing_preferred` (jsonb), `breakdown_json` (jsonb), `computed_at` | **unique(user_id, job_posting_id)**; FK→users, FK→job_postings; idx both | **The stored, explainable score breakdown.** Recomputed when profile or posting changes. |
| `job_recommendations` | `id`, `user_id`, `job_posting_id`, `rank`, `priority`, `factors_json` (jsonb), `generated_at` | **unique(user_id, job_posting_id)**; FK→users, FK→job_postings; idx(user_id, rank) | Ranking output with the transparent per-factor contributions. |
| `saved_jobs` | `id`, `user_id`, `job_posting_id`, `state` (enum: SAVED/BOOKMARKED/NOT_INTERESTED/REJECTED/APPLIED), `application_id` (nullable FK→applications), audit | **unique(user_id, job_posting_id)**; FK→users, FK→job_postings, FK→applications; idx(user_id, state) | One row per user×job; state machine. `application_id` links to the tracker when applied. Discovery **excludes** jobs whose state ∈ {APPLIED, SAVED, REJECTED, NOT_INTERESTED}. |
| `resume_analyses` | `id`, `user_id`, `job_posting_id` (nullable), `payload_json` (jsonb), `created_at` | FK→users, FK→job_postings; idx(user_id) | Per-job gap analysis (missing keywords/tech, demonstrated skills, projects to highlight, bullets to improve, ATS recs). Verified-only. |
| `resume_versions` | `id`, `user_id`, `job_posting_id` (nullable), `label`, `content_json` (jsonb), `created_at` | FK→users, FK→job_postings; idx(user_id) | Tailored resume variants (emphasis/reorder of **verified** content only — never invented). |
| `generated_documents` | `id`, `user_id`, `job_posting_id`, `type` (enum: COVER_LETTER/COLD_EMAIL/INTERVIEW_QUESTIONS), `content`, `created_at` | FK→users, FK→job_postings; idx(user_id, job_posting_id) | Output of the AI generation actions; auditable, regenerable. |

Plus a migration to add **`vector` columns** (`profile_embedding`, `job_embedding`) — gated on the pgvector extension enabled in the Phase-0/1 `CREATE EXTENSION` migration. No changes to existing tables beyond these additions.

## B4. API changes

All new endpoints are **additive**, under `/api`, JWT-protected, DTO-in/DTO-out (**no entity ever serialized**), validated, and routed through the global exception handler. **No existing endpoint changes** — `/applications`, `/match/score`, `/resume/*`, `/reminders/*`, `/users/me`, `/auth/*` keep identical paths/shapes.

**Profile & preferences**

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/profile` | Current user's candidate profile (+ skills). |
| `POST` | `/api/profile/extract` | Extract/refresh profile from the stored resume (one-time; idempotent). |
| `PUT` | `/api/profile` | Edit profile fields/skills (sets `source=USER_EDITED`). |
| `GET`/`PUT` | `/api/profile/preferences` | Read/update job-search preferences (match threshold, roles, locations, work mode, salary, recency). |

**Discovery, matching & recommendations**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/jobs/discover` | Trigger a discovery sync across enabled providers for the user's preferences (idempotent; async). |
| `GET` | `/api/jobs` | List/search discovered jobs — query params for `q`, min match %, role, location, work mode, experience, salary range, date-posted range, `sort`, `page`, `size`. Excludes already-actioned jobs. |
| `GET` | `/api/jobs/{id}` | Full job analysis (company, logo-if-available, title, location, type, salary-if-available, posted-if-available, official apply URL, match summary). |
| `GET` | `/api/jobs/{id}/match` | Stored explainable `MatchAnalysis` (all sub-scores + breakdown + missing required/preferred). |
| `GET` | `/api/jobs/{id}/gap-analysis` | Resume gap analysis vs this job (verified-only). |
| `GET` | `/api/recommendations` | Ranked recommendations with transparent factor breakdown + priority. |
| `GET` | `/api/dashboard/discovery-stats` | Aggregate stats for the dashboard cards (below). |

**Job actions (the 11)** — `save`, `bookmark`, `not-interested`, `reject` and `applied` are `POST /api/jobs/{id}/{action}`; **Mark-as-Applied** creates/patches a `JobApplication` (dedup by user+company+role → **no duplicate records**) and links `saved_jobs.application_id`; **Apply with AI Assistant** reuses the Phase-1 F9 endpoint `POST /api/applications/ai-fill` (draft → user confirms). Generation actions: `POST /api/jobs/{id}/cover-letter | cold-email | interview-questions | improve-resume`. "Open Official Job Page", "Copy Job Link", "Share Job" are **client-side** (use the stored `apply_url`; no new endpoint).

**Idempotency & rate limiting:** `/api/jobs/discover` is safe to call repeatedly (sync cursors); provider calls are internally rate-limited/back-off per provider; expensive LLM calls (gap analysis, generation) are per-user request-scoped and cache their last result in the tables above.

## B5. Frontend changes

Extends the **existing React app** — no standalone HTML dashboard. New routes are added to `App.jsx` behind `ProtectedRoute`, new links to `Sidebar`, and the existing `Layout`, `ScoreRing`, `StatusBadge`, `StatCard`, and `axios` client are reused.

| Route | Page | Contents |
|---|---|---|
| `/discovery` | `Discovery` | `JobFilters` (min match %, role, location, work mode, experience, salary, date posted — all tunable) + `JobList` of `JobCard`s; search, sort, pagination; a "Discover" button that calls `/api/jobs/discover`. |
| `/recommended` | `Recommended` | Ranked `JobRecommendation`s with visible factor contributions and priority badges. |
| `/saved` | `SavedJobs` | Saved/bookmarked/applied jobs grouped by state; links into the tracker. |
| `/jobs/:id` | `JobDetails` | Tabs: **Overview** (job analysis; logo/salary/date shown only when present, else "Not available"), **Match Analysis** (`MatchBreakdown` — all sub-scores + explanation), **Skill Gap** (`GapAnalysisPanel`), **Resume Recommendations** (`ResumeRecommendations`). `JobActionsMenu` with the 11 actions. |
| `/profile` (extend) | `ProfileEditor` | View/edit the extracted candidate profile + verified skills + search preferences. Pre-filled from extraction — **never asks for data already present**. |

**Dashboard cards** (extend existing `Dashboard`, fed by `/api/dashboard/discovery-stats`): total jobs discovered, jobs matching profile (≥ threshold), average match score, top match, recently posted, applications in progress, saved jobs, jobs requiring urgent action (deadline/high-priority). Every new view implements **loading / error / empty** states and is responsive (Tailwind), consistent with the current UI. AI-estimated figures (e.g. priority, any growth estimate) are clearly **labeled as AI estimates** in the UI.

## B6. Job provider abstraction design

The SPI is a single interface with pluggable implementations discovered by Spring as an injected `List<JobProvider>` — adding or removing a source is a **config/deployment change, not a code change** to discovery/matching.

```java
public interface JobProvider {
    String id();                              // "greenhouse", "lever", "ashby", "apify", ...
    boolean isEnabled();                      // from config; disabled providers are skipped
    Set<Capability> capabilities();           // e.g. SALARY, LOGO, POSTED_DATE — tells the UI what may be null
    JobBatch fetch(JobQuery query, String cursor);  // returns normalized-ish results + next cursor
}
```

```
JobProvider
├── OfficialCareerPageProvider   (Greenhouse / Lever / Ashby public posting APIs) — DEFAULT, enabled first
├── ApifyJobProvider             (OPTIONAL, config-gated, disabled by default)
└── FutureJobProvider            (extension point: additional compliant sources)
```

- **`OfficialCareerPageProvider`** wraps the official public board APIs — Greenhouse (`boards-api.greenhouse.io`), Lever (`api.lever.co/v0/postings/{site}`), Ashby posting API. These are documented, public, no-scraping endpoints and they return the **official application URL**, which we always prefer. Per-provider **rate limiting + exponential backoff**; robots/ToS respected; no CAPTCHA or anti-bot bypass.
- **`ApifyJobProvider`** is **optional and disabled by default**, fully decoupled, enabled only via config with a key supplied through env vars (**never hardcoded**). Operating it within a target site's ToS/robots is the operator's responsibility; the abstraction never assumes Apify is present.
- **`FutureJobProvider`** is the seam for adding sources later (e.g. a compliant Google Jobs SERP data provider) without touching existing code. Wellfound/Google Jobs have **no open API** and are deferred to such a provider.

**Config shape (env-driven, no secrets in code):**
```yaml
app:
  job-providers:
    greenhouse: { enabled: true,  boards: [...] }
    lever:      { enabled: true,  sites:  [...] }
    ashby:      { enabled: true,  boards: [...] }
    apify:      { enabled: false, token: ${APIFY_TOKEN:} , actor: ${APIFY_ACTOR:} }
```

**Normalization & idempotency:** every provider result flows through `JobNormalizer` → canonical `JobPosting` (unknown salary/logo/date → left null, surfaced as "unavailable"), then `JobDeduplicator` (cross-provider `dedupe_hash` on company+title+location) and `JobSyncService` (**upsert by `(provider, external_id)`**, cursor tracked in `job_provider_syncs`) so re-syncs never create duplicates. Jobs already in `saved_jobs` as APPLIED/SAVED/REJECTED/NOT_INTERESTED are excluded from recommendations.

## B7. Recommended implementation phases

This is **Phase 2** of the integrated roadmap (§8), delivered in three independently shippable increments after the Phase-1 Candidate Profile exists. **We do not build it all at once.**

| Increment | Delivers | Depends on |
|---|---|---|
| **2a — Providers & sync** | `JobProvider` SPI + `OfficialCareerPageProvider` (Greenhouse/Lever/Ashby) + normalization + dedup + idempotent `JobSyncService`; `job_postings`/`job_skills`/`job_provider_syncs` tables; `POST /api/jobs/discover`, `GET /api/jobs`, `GET /api/jobs/{id}`; basic Discovery page. Apify left disabled. | Phase 0 + 1 |
| **2b — Matching, gap analysis & recommendations** | `HybridMatchService` (deterministic + required/preferred weighting + semantic + experience relevance) + `MatchExplanationBuilder` + stored `match_analyses`; `RecommendationRankingService` + `job_recommendations`; gap analysis (`resume_analyses`); `GET /api/jobs/{id}/match|gap-analysis`, `GET /api/recommendations`; Match/Gap tabs + Recommended page. | 2a |
| **2c — Actions, tracker integration & dashboard** | `JobActionService` (11 actions), `saved_jobs` state machine, Mark-as-Applied → tracker (dedup), Apply-with-AI → F9, `generated_documents`; `resume_versions`; `JobActionsMenu`, Saved page, dashboard discovery-stats. | 2b |

Each increment ships behind unit + integration + backward-compat contract tests and auto-deploys; we can pause between any of them.

## B8. Risks and limitations

- **Provider coverage is partial.** Greenhouse/Lever/Ashby cover many tech employers but not all; some companies use unsupported ATSs. Mitigation: the SPI makes new compliant providers additive; we never scrape to fill gaps.
- **ToS / legal / anti-bot.** Scraping and CAPTCHA-bypass are out of scope by design. Apify stays optional/off and its compliant use is the operator's responsibility. We prefer official APIs and official apply URLs.
- **Rate limits & quotas.** Provider APIs and the Gemini API both have limits; discovery is async, rate-limited, cursor-based, and backs off. Bulk matching uses cheap embeddings (pgvector) and reserves LLM calls for top-N / on-demand gap analysis to control cost + latency.
- **Hallucination / fabrication.** Hard guardrail: the matcher and generators read **only verified `candidate_skills`**; salary/logo/posted-date are nullable and shown as "unavailable" when absent; AI figures (priority, any growth estimate) are **labeled estimates**; **no "interview probability" is presented as fact** — only as a clearly-labeled estimate, and only when backed by real data. Resume tailoring may re-emphasize but **never invent** skills/experience/projects/education/achievements.
- **Semantic-match quality** depends on embedding quality and JD parsing; the deterministic skill layer + stored explainable breakdown keep results auditable and let the user see *why*.
- **Company logos** only from sources that permit it; otherwise omitted.
- **pgvector on Render** must be verified available/enabled on the managed Postgres; if unavailable we fall back to storing embeddings and computing similarity in-service (interface-hidden), so the feature still ships.
- **Duplicate applications** avoided via `saved_jobs` uniqueness + Mark-as-Applied dedup against existing `JobApplication`s.
- **Scope/effort.** This is the largest subsystem; the 2a→2b→2c split keeps each release small and reversible.

## B9. Exact files likely to be added / modified

**Backend — added**
```
src/main/java/com/smartjobtracker/
  profile/CandidateProfileService.java, ResumeProfileExtractor.java
  jobs/provider/JobProvider.java, OfficialCareerPageProvider.java, ApifyJobProvider.java, FutureJobProvider.java
  jobs/provider/client/GreenhouseClient.java, LeverClient.java, AshbyClient.java
  jobs/discovery/JobDiscoveryService.java, JobNormalizer.java, JobDeduplicator.java, JobSyncService.java
  jobs/match/HybridMatchService.java, ExperienceRelevanceScorer.java, MatchExplanationBuilder.java
  jobs/recommend/RecommendationRankingService.java
  jobs/actions/JobActionService.java, GeneratedDocumentService.java
  jobs/embedding/JobEmbeddingService.java
  controller/ProfileController.java, JobDiscoveryController.java, JobController.java,
             RecommendationController.java, JobActionController.java, DiscoveryStatsController.java
  model/CandidateProfile.java, CandidateSkill.java, JobSearchPreferences.java, JobPosting.java, JobSkill.java,
        JobProviderSync.java, MatchAnalysis.java, JobRecommendation.java, SavedJob.java,
        ResumeAnalysis.java, ResumeVersion.java, GeneratedDocument.java  (+ enums)
  repository/  (one Spring Data repo per new entity)
  dto/  ProfileDtos, PreferencesDtos, JobSummaryDto, JobDetailDto, MatchAnalysisDto, GapAnalysisDto,
        RecommendationDto, SavedJobDto, JobActionDtos, GeneratedDocumentDto, DiscoveryStatsDto
  config/JobProviderConfig.java
src/main/resources/db/migration/  V13__candidate_profile.sql ... V1x__generated_documents.sql
                                   (candidate_profiles, candidate_skills, job_search_preferences, job_postings,
                                    job_skills, job_provider_syncs, match_analyses, job_recommendations,
                                    saved_jobs, resume_analyses, resume_versions, generated_documents, + vector cols)
src/test/java/...  unit tests per service (AiClient/providers mocked) + integration tests + backward-compat contract tests
```

**Backend — modified (minimal, additive)**
```
KeywordMatchService.java     (extract a stable scoring seam reused by HybridMatchService — behavior preserved)
SecurityConfig.java          (permit/authorize the new /api/profile, /api/jobs, /api/recommendations routes — still JWT-protected)
application.yml              (app.job-providers.* config block; no secrets — values via env vars)
pom.xml                      (only if a provider/http client dep is needed; prefer existing Spring WebClient/RestClient)
```

**Frontend — added**
```
frontend/src/api/profile.js, jobs.js
frontend/src/pages/Discovery.jsx, Recommended.jsx, SavedJobs.jsx, JobDetails.jsx, ProfileEditor.jsx
frontend/src/components/JobCard.jsx, JobFilters.jsx, JobList.jsx, MatchBreakdown.jsx,
                          GapAnalysisPanel.jsx, ResumeRecommendations.jsx, JobActionsMenu.jsx, DiscoveryStats.jsx
```

**Frontend — modified**
```
frontend/src/App.jsx                 (register the new routes under ProtectedRoute)
frontend/src/components/Sidebar.jsx  (add Discovery / Recommended / Saved links)
frontend/src/pages/Dashboard.jsx     (add discovery-stat cards)
frontend/src/pages/Profile.jsx       (link/extend into ProfileEditor)
```

No existing backend endpoint, DTO field, DB column, or frontend flow is removed or renamed — every item above is an addition or a behavior-preserving extension.

---

*End of Part B. Awaiting your approval of this updated plan before any code is written. On approval I'll begin with Phase 0, then Phase 1 (Candidate Profile), and check in before each Phase-2 increment.*
