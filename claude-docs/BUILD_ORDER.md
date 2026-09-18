# Build Order (P1)

Status as of 2026-09-15. Sequence matters: each step's verification is the next step's foundation.

## Done

- **0. Repo is reviewable.** One-command startup (`./start.sh`) with compose defaults so a fresh
  clone runs with no setup; nginx serving the SPA with a `try_files` fallback and proxying the API
  same-origin; deliverable README with overview, ERD, setup and usage; docs reconciled with the
  code.
  *Verified:* clean clone → `./start.sh` → register/login/refresh/replay-rejected over `:5173`.
- **1. Docker skeleton.** Five containers, healthchecks, volumes.
- **2. Flyway V1–V3.** All P1 tables, constraints and FK delete rules.
  *Verified:* `\dt` in the postgres container.
- **3. `user` domain.** Entity, repository, service, unit tests.
- **4. `auth` core.** `JwtService` + `TokenStoreService`, unit tested first.
- **5. Auth endpoints + `JwtAuthenticationFilter`.** `AuthControllerIT` on Testcontainers.
- **6. Google OAuth.** Manual test.
- **7. 2FA, CAPTCHA, password reset, email via MailHog.**

- **8. `catalog` domain.** Entities, repositories, services, controllers, DTOs for products,
  categories, brands, images and reviews. Public read, admin write (path rules in
  `SecurityConfig`), review delete owner-or-admin. Offset pagination, Summary + Detail DTOs.
  *Verified:* `CatalogBrowseIT`, `ProductAdminIT`, `ReviewIT`, `ProductRequestValidationTest`.
- **9. Search.** `SearchService` + `PostgresSearchService` on `JdbcClient`: weighted `ts_rank`
  relevance, facets that omit their own filter, recursive category subtree, `pg_trgm` suggestions.
  V5 adds the GIN, trigram, FK and sort indexes; V6 the rating trigger.
  *Verified:* `SearchIT`; `EXPLAIN ANALYZE` over 50k rows shows bitmap scans on both GIN indexes.
- **10. Seed data.** `db/seed/R__seed_catalog.sql`, repeatable: 61 chess and strategy-game
  products, 8 brands, a three-level tree, generated reviews.
- **11. Security tests.** `security/InputValidationIT` — injection probes, oversized strings,
  deep JSON, mass assignment, hostile uploads. *Verified:* 27 probes, none leak internals.
- **12. Product images.** `ImageProcessor` (magic bytes, header dimension cap, re-encode) behind
  `StorageService`. *Verified:* `ProductImageIT`, and a live upload served by nginx with `nosniff`.

- **12b. Rate limiting + search fallback.** `ratelimit/` (Redis fixed window, per-IP filter) and
  `auth/AccountThrottle` (per account+IP and per account on login, per address on reset emails).
  Client IP from nginx's `X-Real-IP` via Tomcat's RemoteIpValve; backend port on loopback only.
  Search retries name trigrams when full-text finds nothing (`approximate` in the response).
  *Verified:* `RateLimitIT`, `SearchIT`; live through nginx, spoofed `X-Real-IP` still limited.
- **13. Frontend auth.** Redux auth state (never the token), Axios instance with the token in a
  module variable and a single-flight refresh on `unauthorized` 401s, restore on load via
  `/auth/refresh` + `GET /auth/me`, login/2FA/register/forgot/reset/account pages with client-side
  validation, reCAPTCHA and Google button when their keys are set.
- **14. Frontend catalog.** Catalog with URL-held facets, sort and paging; product page with specs
  and reviews; debounced search-as-you-type combobox. SPA routes avoid proxied API prefixes
  (`/catalog`, not `/products`).
- **15. Frontend tests.** Vitest: validation rules, login and register forms, refresh interceptor.
  *Verified:* 27 tests; the single-flight test fails when the guard is removed. Playwright
  (Firefox) walk of every page in both themes, reload keeps the session, storage holds no token.

## Remaining

- **16. Choose a theme.** Delete the other token block, its accents and fonts, and `ThemeToggle`.
- **17. Rehearsal (P1 endgame phase 5).** Manual test script, live reCAPTCHA key, ERD modality
  check, spoken answers, timed clean-clone run.

Architecture and request flow are documented in [../README.md](../README.md#architecture) — that is
the single source, kept accurate because reviewers read it.
