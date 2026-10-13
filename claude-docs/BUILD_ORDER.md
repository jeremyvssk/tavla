# Build Order

Project 3 is first; the Project 1 order follows it, kept as it was.

## Project 3 (status 2026-10-12)

Planned and decided with the author in the "Tavla Experience Plan" artifact (steps 0–12). Steps 0–11 are built;
the calls made without the author for 7–11 are listed in that artifact for review:

- **0. Docs reconciled.** Scope, schema, Redis keys and the JWT `mfa` claim in the CLAUDE files.
- **1. Roles, staff 2FA, audit log.** SUPPORT/SALES/ADMIN with a least-privilege matrix (`auth/StaffArea`), every
  staff area needs a second factor (`mfa` claim), Admin only from the command line, role changes need the
  changer's TOTP and revoke the person's tokens at once (`tokens_revoked_before`). Append-only `audit_log` (V11).
  *Verified:* `StaffAccessIT`, `StaffUserIT`, `AuditLogIT`.
- **2. Catalog management.** SKU, archive-or-delete, the staff product list with state counts, bulk changes,
  Sales' ±50% price limit (`catalog/PriceGuard`), CSV export, category edit/move/delete, and the all-or-nothing
  bulk upload (`catalog/imports`, V12). *Verified:* `StaffCatalogIT`, `CategoryAdminIT`, `ImportIT`, `CsvReaderTest`.
- **3. Orders and delivery.** Delivery options in the database (`delivery/`, V13), the staff order queue,
  Processing/Shipped/Delivered with emails, staff cancel while packing, part refunds (Support up to €100 an
  order), team notes, `price_changed` at checkout. *Verified:* `StaffOrderIT`, `DeliveryIT`.
- **4. Reviews.** Only a delivered purchase may review; helpful votes (atomic counter), reports, moderation
  (keep/hide/unhide), hidden reviews out of the average (V14). *Verified:* `ReviewModerationIT`, `ReviewIT`.
- **5. Staff overview and frontend wiring.** `GET /staff/overview` narrowed per role; in-page confirm dialogs;
  the 2FA upgrade token. *Verified:* `OverviewIT`, `ImportPage.test.tsx`, `confirm.test.tsx`.
- **6. Account.** Profile, data export, deletion that keeps orders unlinked, saved addresses, billing address
  on orders (V15), and the contact form emailed to the shop's inbox through the queue. *Verified:* `AccountIT`.

- **7. Images in every size.** Uploads saved at 160/600/1200 px beside the original (`ImageProcessor.WIDTHS`,
  no upscaling); `srcset`/`sizes` per place (`lib/images.ts`, `Photo`); nginx falls back to the original for a
  missing size and caches images and assets for a year. *Verified:* `ImageProcessorTest`, `ProductImageIT`,
  `images.test.ts`; live headers and fallback through nginx.
- **8. Token bucket.** `RateLimiter` rewritten as a Lua token bucket on Redis TIME (`rate_bucket:`), a general
  per-IP bucket for unlisted paths, the webhook exempt. *Verified:* `TokenBucketIT`, `RateLimitIT`.
- **9. Encrypt the rest.** `users` email/full_name/avatar_url/two_factor_secret (V16), `email_hash` blind index
  (`crypto/BlindIndex`), `UserEncryptionBackfill` after every migrate. *Verified:* `UserEncryptionIT` and the
  suites that look users up; live backfill of the seed's reviewers.
- **10. Findable and usable.** Per-page title/description (`content/pageMeta.ts`), `/catalog/{id}/{slug}` with
  redirect to the current slug, focus trap for the cart and menu, product alt text, `scripts/a11y-check.mjs`
  (axe WCAG 2.1 A + contrast, headings, 200% zoom). *Verified:* `pageMeta.test.ts`, `paths.test.ts`,
  `useFocusTrap.test.tsx`; a11y-check clean on every shop and account page.
- **11. Load test.** k6 in Docker against a separate stack (`loadtest/`), four journeys, 10–800 users; report in
  the README. Fix found by it: two-letter suggestions cached for 30 s. *Verified:* `SearchIT`; before/after runs
  in `loadtest/results/`.

Remaining: 12, the author's review of the 7–11 calls, a full rehearsal, and a commit.

## Project 1

Status as of 2026-09-29. Sequence matters: each step's verification is the next step's foundation.

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
- **10. Seed data.** `db/seed/R__seed_catalog.sql`, repeatable, generated by
  `tools/catalog-import` (Python, dev-only) from three real suppliers: ~1,770 products, 12 brands,
  a three-level tree, hotlinked supplier photos, generated reviews. Upserts keyed on the supplier id.
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
  *Verified:* the single-flight test fails when the guard is removed (37 Vitest tests after 15b). Playwright
  (Firefox) walk of every page in both themes, reload keeps the session, storage holds no token.

- **15b. Storefront redesign + cart.** One theme (Tavla), `ThemeToggle` removed; colour/size
  variants via `attributes.variant_group`; Featured sort as the browse default; category filter
  following the tree; browser-only cart (`cartSlice`, localStorage, checkout disabled until P2).
  *Verified:* `mvn verify` 85 unit + 80 integration, 37 Vitest.
- **16. Docs for reviewers.** Plain-language README, `REVIEW_ANSWERS.md`, and human guides in
  `backend/README.md` and `frontend/README.md`.

## Remaining

- **17. Rehearsal (P1 endgame phase 5).** Manual test script, live reCAPTCHA key, ERD modality
  check, spoken answers, timed clean-clone run.

Architecture, request flow and the Redis key table are documented in
[../backend/README.md](../backend/README.md) — that is the single source, kept accurate because
reviewers read it.
