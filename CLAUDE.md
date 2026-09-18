# i-love-shopping — Project Context

B2C e-commerce platform (demo catalog: chess & strategy games; the final niche is still open). School project in 3 parts.
**Current scope: Project 1** — auth, database, product catalog.

The assignment brief and its review checklist are in [docs/ASSIGNMENT.md](docs/ASSIGNMENT.md).
[README.md](README.md) is the graded deliverable — overview, ERD, setup, usage. Keep its
**Project status** section honest; a README claiming unbuilt features is worse than no README.

## Learning Mode

This project doubles as the author's deliberate-practice gym. On **learning-relevant** tasks (a new
domain, endpoint, migration, or non-trivial feature), act as a **coach, not just an implementer** —
the goal is that they *understand and could defend* what we build, not merely that it works. Full
plan and the per-pillar deep-dives: [homework/learningStrategy.md](homework/learningStrategy.md).

Per non-trivial task, run this loop — **do not jump straight to code:**

1. **Design first — they drive.** Have them state the goal and sketch the design (data flow,
   entities/endpoints, where state lives, failure modes, the key tradeoff + *why this over the
   alternative*) before any code. Don't hand them the design.
2. **Red-team it.** Poke holes, name missed tradeoffs, ask "why not X." They revise.
3. **Predict before reveal.** Before explaining or generating, have them predict the answer/shape,
   so the output grades their retrieval instead of replacing it.
4. **Build — they own the load-bearing 20%.** They specify/direct the core logic (the part that
   teaches); you generate boilerplate/wiring. They review every diff and must be able to explain it.
5. **Verify — they propose tests first.** Before you write tests, have them list cases (happy,
   validation, auth/ownership, conflict/race, edge, security); refine together; then write. Then
   ask: *what could be wrong that a passing test wouldn't catch?* (concurrency / perf-at-scale /
   security — the Lua-rotation race class).
6. **Retrospective.** One line: what was right, what they'd change, what surprised them.

Be Socratic; hint before solving; let them struggle first; never rubber-stamp a decision on their
behalf. They can say **"just build it"** to skip coaching for throwaway/boilerplate work.

## Working Style

The general rules — simplicity, finish what was asked, don't widen scope, ask when two readings
lead to materially different work — are assumed. These are the ones specific to this repo, or
easy to get wrong.

**Surgical changes.** Touch only what the request requires. Don't refactor working code, reformat,
or "improve" adjacent comments. If you notice unrelated dead code, *mention* it — don't delete it.
Remove imports and variables that **your** change orphaned; leave pre-existing dead code alone.
The test: every changed line traces to the request.

**No emojis in code.**

**File headers.** Every `.tsx`/`.jsx` and `.java` file opens with a one-line comment saying what
that file does.

**Comments.** Worth writing for non-obvious logic, domain rules, and "X instead of Y because Z".
Not worth writing for what the code already says through naming.

**Verifiable goals.** State a multi-step task as steps with a check each — "add validation →
write tests for invalid input, watch them fail, then pass". Strong checks let you loop without
coming back to ask whether it worked.

## Stack

| Layer | Choice |
|---|---|
| Frontend | React 18 + TypeScript + Vite, managed with Bun |
| Frontend state | Redux Toolkit (auth) + React Query (server data) |
| Routing | React Router v6 |
| HTTP | Axios with interceptors (access token stored in memory var) |
| Backend | Spring Boot 4.0.6, Java 21 (**Boot 4, not 3** — see [backend/CLAUDE.md](backend/CLAUDE.md)) |
| ORM + migrations | Spring Data JPA + Flyway (plain SQL files, run on startup) |
| Database | PostgreSQL 16 (ACID, JSONB, built-in full-text search) |
| Token store | Redis 7 (refresh tokens, access token blocklist) |
| Email (dev) | MailHog in docker-compose |
| Containers | Docker + docker-compose (one-command startup) |
| Backend tests | JUnit 5 + Mockito + Testcontainers |
| Frontend tests | Vitest + Testing Library |

## Architecture

Modular monolith — single Spring Boot app, code split by domain not layer.
Each domain owns its own controller/service/repository/entity.
Cross-domain calls through service interfaces only (no repo-to-repo).

Domains (P1): `user`, `auth`, `catalog`

## Folder Structure

See [claude-docs/FOLDER_STRUCTURE.md](claude-docs/FOLDER_STRUCTURE.md)

## Skills

Skills hold detail that shouldn't sit in always-loaded context. Load one *before* starting the
work it covers, not after getting stuck. Inventory and provenance: [.claude/skills/README.md](.claude/skills/README.md).

**Project skills** — load these by default for the matching work:

| Work | Skill |
|---|---|
| Any controller, DTO, or request-validation change under `backend/` | `new-endpoint` |
| Writing, planning, or judging the adequacy of any test | `verify` |
| Any change under `db/migration/` | `flyway-migration` |

**Frontend design skills** (vendored from [emilkowalski/skills](https://github.com/emilkowalski/skills), MIT):

| Work | Skill |
|---|---|
| Choosing a frontend library (toasts, dropdowns, charts, drag & drop…) | `/pick-ui-library` — explicit invoke only |
| Exploring several UI directions before committing | `/prototype` — explicit invoke only |
| Building an animation or transition | `/animate` — explicit invoke only |
| General UI polish and component-design judgment | `/emil-design-eng` — explicit invoke only |

All four are explicit-invoke only — none can trigger themselves — and all four are gitignored,
so they live on this machine and not in the repo. Project 3 is where they earn their keep; the P1
frontend steps (BUILD_ORDER #13-15) are forms, routing and validation, so build those plainly.

**Built-ins — don't build project skills that duplicate these:** `/code-review` (correctness
bugs), `/simplify` (reuse and over-complication), `/security-review` (vulnerabilities in the
current diff).

Adding a skill has a real cost: its description line sits in context permanently and can
mis-trigger. Add one only when a checklist is being repeated across sessions, and prefer moving
existing always-loaded text into it over writing something new.

When working under `backend/`, also follow [backend/CLAUDE.md](backend/CLAUDE.md) — backend-specific operating rules (validation, auth, persistence, 12-factor).

## Database Schema (Flyway migrations)

**users** — id UUID, email unique, password_hash, full_name, avatar_url,
  auth_provider (LOCAL/GOOGLE/FACEBOOK), oauth_provider_id, role (CUSTOMER/ADMIN),
  two_factor_enabled, two_factor_secret, two_factor_backup_codes,
  email_verified, created_at, updated_at

**categories** — id, name, slug unique, parent_id (self-FK for hierarchy), active

**brands** — id, name, slug, logo_url

**products** — id UUID, name, description, price NUMERIC(10,2), stock_quantity,
  category_id FK, brand_id FK,
  weight_kg, weight_lbs, width/height/depth_cm, width/height/depth_in,
  attributes JSONB, search_vector tsvector (generated),
  average_rating NUMERIC(3,2), review_count INT, active, created_at, updated_at

**product_images** — id, product_id FK, url, alt_text, display_order, is_primary

**product_reviews** — id UUID, product_id FK, user_id FK, rating INT (1-5),
  title, body, verified_purchase, created_at
  *(needed in P1: faceted search + sorting by rating requires real data)*

**Redis keys:**
- `refresh_token:{hash}` → user_id, TTL 7 days
- `refresh_tokens_user:{userId}` → Set of hashes (for "revoke all on password reset")
- `token_blocklist:{jti}` → "1", TTL = remaining access token lifetime
- `2fa_pending:{userId}` → challenge marker, TTL 5 min

**Image storage:** local filesystem, mounted as Docker volume `product_images_data`.
Served via nginx at `/images/**`. Not suitable for production scale — use S3 later.

## Security Rules

The server-side invariants are in [backend/CLAUDE.md](backend/CLAUDE.md) §6 — rotation atomicity,
revoke-all on reset, the 2FA challenge key, no PII in the payload — and they load with any backend
work. These are the ones that live outside that file:

- Access token: JS module variable only. Never localStorage/sessionStorage.
  On page reload → call `/auth/refresh` on mount to rehydrate.
- 2FA setup returns an `otpauth://` URI for the client to render as a QR code. The 8 backup codes
  are shown once and never again.
- GDPR: UserService must support hard delete (cascade) and a data export endpoint.

## Build Order (P1)

See [claude-docs/BUILD_ORDER.md](claude-docs/BUILD_ORDER.md)

## Open Questions / Known Gaps

- Image CDN strategy for production (currently: Docker volume + nginx)
- GDPR data export format (JSON dump of user record + orders)
- Elasticsearch for P3 — SearchService interface is already the abstraction point
