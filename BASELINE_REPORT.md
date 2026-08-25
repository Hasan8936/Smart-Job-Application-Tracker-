# Pre-Implementation Baseline Report

**Prepared:** 2026-08-26 · **Repo:** `Smart-Job-Application-Tracker-` · **Branch:** `main`
**Baseline commit:** `01b212995799fca9c57fc9717cab061c33b389dc` — *"Fix mobile sidebar drawer, touch targets, and CI/deploy workflows"*
**Purpose:** Establish a known-good reference point **before** any AI-agent feature work. No feature code has been written. This report answers the nine baseline requirements and returns build status, test status, existing issues, files that must not be modified, and the recommended implementation order.

> **Key framing — existing vs. new errors.** This snapshot is taken *before* any change, so **every issue listed here is a pre-existing (existing) issue. There are zero "new" errors**, by definition. After each future feature I will re-run the same checks; anything that breaks relative to this baseline is a *new* error attributable to that feature and I will fix it before reporting done.

---

## 1. Safe baseline created

- **Git tag:** `baseline-pre-ai-agent` → points at `01b2129` (verified). This is a restore/compare point.
- The tag currently exists **only in the local repo** — the sandbox has no network egress to GitHub, so it cannot be pushed from here. **Action for you (from your own terminal):**
  ```bash
  git push origin baseline-pre-ai-agent
  # optional extra safety net — a full branch snapshot you can always return to:
  git branch backup/pre-ai-agent 01b2129
  git push origin backup/pre-ai-agent
  ```
- Working tree is the frozen contract; all new work will be additive (see §6).

---

## 2. Build status

| Target | Result in this sandbox | Why | Authoritative source |
|---|---|---|---|
| **Backend** (Spring Boot 3.2, Java 17) | **Not runnable here** | Sandbox has **JDK 11 only** (no `javac`), **no Maven**, no `mvnw` wrapper, no `~/.m2` cache. The project requires **Java 17**. | GitHub Actions `ci.yml` → `mvn -B test package` on **temurin-17**; Render Docker build. |
| **Frontend** (React 18 + Vite 8) | **Build failed here — environmental only** | Installed `node_modules` is your **Windows** install (`@rolldown/binding-win32-x64-msvc`); this sandbox is **Linux x64** and needs `@rolldown/binding-linux-x64-gnu` (or the wasm fallback), which isn't present, and the **npm registry is blocked** so it can't be fetched. **Not a code defect.** | GitHub Actions `ci.yml` → `npm ci && npm run build` on Node 22; Vercel build. |

**Static verification I *could* do here (all pass):**

- `pom.xml` is well-formed: Spring Boot parent `3.2.0`, `<java.version>17</java.version>`, standard `spring-boot-maven-plugin`, test deps present (`spring-boot-starter-test`, `h2`).
- `frontend/package.json` valid: `build: vite build`, `engines.node = 22.x`, `package-lock.json` present, deps consistent with the app.
- CI workflow is intact and is the real build/test gate for both apps.

**To confirm build is green (run on your machine or check CI):**
```bash
# backend
mvn -B -DskipTests=false test package
# frontend
cd frontend && npm ci && npm run build
```

---

## 3. Test status

| Test | Type | Runnable here? |
|---|---|---|
| `AuthIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` — full-context integration | No (needs JDK17 + Maven) |
| `ApplicationIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` — full-context integration | No (needs JDK17 + Maven) |
| `service/KeywordMatchServiceTest` | `@SpringBootTest` — context + service | No (needs JDK17 + Maven) |

- **3 backend test classes**, all boot the Spring context against **H2** (Flyway runs the migrations during the test boot, so these also exercise the migration set).
- **Frontend:** no unit tests; CI runs a **Cypress** smoke check that is explicitly `continue-on-error` (not a shippability gate).
- **Not executed in this sandbox** for the tooling reasons above. Authoritative signal is CI `mvn test`. I cannot read CI results from here (no GitHub egress) — please confirm the latest run on `main` is green, or run the command in §2.

**Baseline test expectation:** these 3 tests are the regression guard. My plan (§7 of the upgrade plan) adds **backward-compat contract tests** on top so future features can't silently change existing JSON shapes.

---

## 4. Existing issues (pre-existing — none introduced by me)

### 4a. Environmental (this sandbox only — not code problems)
- Backend cannot be built/tested here (JDK 11, no Maven).
- Frontend cannot be built here (platform-mismatched Rolldown binding + blocked npm registry).
- Git cannot push from here; commits/tags must be pushed from your terminal.

### 4b. Security — should be fixed in Phase 0 (backward-compatible)
| Issue | Location | Risk |
|---|---|---|
| **Weak JWT secret default** `secret123` | `security/JwtUtil.java` `@Value("${JWT_SECRET:secret123}")` | If `JWT_SECRET` is unset in prod, tokens are signed with a guessable key. Fix: **fail-fast** when unset in a non-dev profile. |
| **Dev datasource password default** `password` | `application.yml` `${SPRING_DATASOURCE_PASSWORD:password}` | Fine for local, must never be the prod value; prod already sets the env var. Keep, but document. |
| **Mass-assignment / IDOR** | `ReminderController.create` binds the raw `Reminder` entity | Caller can set `id`/`userId` and overwrite another user's reminder. Fix: accept a validated DTO, set `userId` from the token. |
| **Missing ownership check** | `MatchController` / `KeywordMatchService` | Match runs without verifying the resume belongs to the caller. Fix: enforce ownership. |

### 4c. Correctness bugs (pre-existing)
| Bug | Location | Effect |
|---|---|---|
| Reminders never actually email the user | `ReminderService` sends from hard-coded `noreply@example.com` and **swallows errors in an empty catch** | Silent failure; reminders don't reach users. |
| Unmapped exceptions → HTTP 500 | No `@ControllerAdvice` global handler | `IllegalArgumentException` etc. surface as 500 instead of 400; inconsistent error bodies. |
| Input validation is spotty | Only password-reset DTOs are validated | Other endpoints accept unvalidated input. |

### 4d. Tech debt / smells (non-blocking)
- `currentUserId()` duplicated across ~3 controllers (should be a `@CurrentUser` resolver).
- Controllers return entities directly in places (leak risk; plan mandates DTasO-only for new code and DTO-izes responses without renaming fields).
- Frontend: JWT in `localStorage` (and printed on the Profile page); no axios **response** interceptor (no 401/expiry handling); session-restore race can bounce an authed user to `/login` on refresh.

> These are **reported, not changed.** Phase 0 addresses the security-critical ones (4b) and the global-exception/validation gaps (4c) in a strictly backward-compatible way. Nothing here is touched until we explicitly start Phase 0.

---

## 5. Flyway migrations — verified

- Present and additive: `V1__init.sql` (users, resumes, applications), `V2__reminders.sql`, `V3__application_status_history.sql`, `V4__password_reset_tokens.sql`.
- Config is correct: `spring.flyway.enabled: true`, `baseline-on-migrate: true`, and JPA `ddl-auto: validate` (Flyway owns the schema; Hibernate only validates). **Confirmed under `spring:` namespace** (a top-level `flyway:` block would be ignored).
- **Rule going forward:** applied migration files are immutable (Flyway checksums them). All schema changes ship as **new `V5+` files only** — never edit `V1`–`V4`. New tables get audit fields, FKs, unique constraints, and indexes (per the plan).

---

## 6. Files that must NOT be modified (frozen contract)

Treat these as read-only / additive-only. Changing them risks breaking the frontend or the migration history.

**Never edit (immutable):**
- `src/main/resources/db/migration/V1__init.sql`, `V2__reminders.sql`, `V3__application_status_history.sql`, `V4__password_reset_tokens.sql` — already-applied migrations. New changes = new `V5+` files.

**Do not rename fields / change response shapes (frozen JSON contract the React app depends on):**
- `dto/AuthResponse` → `{ token }`
- `dto/MatchResponse` → `{ matchScore, matchedKeywords[], missingKeywords[] }`
- `model/JobApplication` serialized fields → `{ id, companyName, roleTitle, jobDescription, status, appliedDate }`
- Reminder response → `{ id, type, message, remindAt }`
- Resume response → `{ id, fileName, uploadedAt }`

**Do not change existing paths/methods (extend alongside, don't alter):**
- All existing `@RequestMapping` paths in `AuthController`, `ApplicationController`, `MatchController`, `ReminderController`, `ResumeController`, `UserController`, `HealthController`.
- `security/SecurityConfig` public-path rules and CORS — **add** rules, don't remove existing ones.
- `frontend/src/api/axios.js` baseURL + request-interceptor behavior — extend, don't break.

> New features add **new** DTOs/controllers/endpoints next to these. Where an existing controller must return richer data, I will add a **new** endpoint (e.g. `/api/match/ai-score`) rather than change the existing one.

---

## 7. Database backup strategy

Because Flyway Community has no automatic "undo" and `ddl-auto: validate` means the schema is authoritative, the safety model is **backup-then-migrate, restore-on-failure**:

1. **Before every migration deploy (prod = Render Postgres):** take a logical dump and keep the artifact.
   ```bash
   pg_dump "$SPRING_DATASOURCE_URL" -Fc -f backup_pre_V<next>_$(date +%Y%m%d_%H%M).dump
   ```
   Also enable/verify **Render's managed daily backups** (and point-in-time restore if on a plan that offers it) as a second layer.
2. **Per phase:** snapshot immediately before applying that phase's new migrations. Since all planned migrations are **additive-only**, a failed deploy is recovered by restoring the pre-migration dump.
3. **Test migrations on a scratch DB first** (local Postgres or a disposable Render branch DB) before running against prod. The existing `@SpringBootTest` suite already runs the full migration set against H2 on every CI build, catching ordering/checksum errors early.
4. **Restore path:**
   ```bash
   pg_restore --clean --if-exists -d "$TARGET_DATASOURCE_URL" backup_pre_V<n>.dump
   ```
5. **Verify** `flyway_schema_history` before and after each deploy; never mutate an applied migration file.
6. **Destructive changes** (none are currently planned — additive only) would require an explicit backup + review checkpoint before proceeding.

---

## 8. Secrets / environment-variable compliance — verified

- **No hardcoded secrets and no committed `.env`** with real values (only `frontend/.env.example`, which is a template).
- All secrets resolve from environment variables: `SPRING_DATASOURCE_*`, `MAIL_USERNAME`/`MAIL_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGIN_PATTERNS`. CI/deploy secrets (`DOCKERHUB_*`, `RENDER_*`) come from GitHub Actions secrets.
- **Only real concern:** the `JWT_SECRET:secret123` *fallback default* (see §4b). It's not a committed production secret, but it must **fail-fast** in prod rather than silently signing with `secret123`. This is the first thing Phase 0 fixes.
- Going forward, new integrations (Gemini `AI_API_KEY`, Twilio `TWILIO_*`, provider keys like `APIFY_TOKEN`) will follow the same env-var-only pattern — never hardcoded.

---

## 9. Recommended implementation order

Grounded by this baseline and the approved rev-2 plan. Each step is independently testable and deployable; I check in before each one.

1. **Phase 0 — Foundation hardening (do this first).** Backward-compatible fixes to the pre-existing issues above (JWT fail-fast, global `@ControllerAdvice`, input validation, `ReminderController` DTO + ownership, `MatchController` ownership, `EmailService` as the single send path) + scaffolding used by everything later (`AiClient` interface, `Notifier` interface, async config). Adds contract tests that lock the current JSON shapes. **No behavior change to existing flows.**
2. **Phase 1 — Candidate Profile + AI resume/JD tools.** Profile extraction/editor, semantic matching (pgvector), resume optimization, JD analysis, form-fill.
3. **Phase 2 — Job Discovery & Recommendation**, delivered as **2a** (provider SPI + Greenhouse/Lever/Ashby + normalize/dedup/idempotent sync) → **2b** (hybrid explainable matching + recommendations + gap analysis) → **2c** (job actions + tracker integration + discovery UI + dashboard).
4. **Phase 3 — Gmail automation pipeline** (detect → classify → auto-status → interview/deadline extraction).
5. **Phase 4 — Notifications** (Twilio WhatsApp) wired into reminders + status-change events.

**Recommended first action:** start **Phase 0**. It's a prerequisite for the AI scaffolding and it closes the security-critical pre-existing gaps in a backward-compatible way — the safest possible first change.

---

*No features implemented. Awaiting your go-ahead on a specific starting point (per your rule: "implement only the feature I explicitly request").*
