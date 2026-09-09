# Build Order (P1)

Status as of 2026-09-09. Sequence matters: each step's verification is the next step's foundation.

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

## Remaining

- **8. `catalog` domain.** Entities, repositories, services, controllers, DTOs for products,
  categories and brands. Public read vs. admin write. Pagination.
  *Design session first* — entities, DTO boundaries, which fields are client-writable, how admin
  CRUD differs from public read. Load `new-endpoint`.
  *Verify:* unit tests on the product model; `ProductControllerIT` covering 400/401/403/404.
- **9. Search.** `SearchService` interface, then `PostgresSearchService`: `ts_rank` relevance,
  `pg_trgm` autocomplete, SQL-computed facets, sorting by relevance/price/rating.
  Needs a migration for the GIN index on `search_vector` and the trigram index — load
  `flyway-migration`. *Verify:* `EXPLAIN ANALYZE` shows the indexes are used, not just present.
- **10. Seed data.** Enough products, categories, brands and reviews that facets and sorting have
  something to bite on. Without this steps 8–9 cannot be demonstrated at review.
- **11. Security tests.** `security/InputValidationTest` — injection probes, oversized strings,
  deep JSON, path traversal, mass assignment. *The author proposes the probe cases first.*
  Load `verify`.
- **12. Product images.** Upload with the full validation checklist, written to the
  `product_images_data` volume; nginx already serves `/images/`.
- **13. Frontend auth.** Redux store, Axios interceptor, access token in a module variable,
  refresh-on-mount, login/register/reset/2FA screens with client-side validation.
  *Design session first* — where the token lives, the refresh flow, the Redux vs React Query split.
- **14. Frontend catalog.** Product list, detail, search with debounced autocomplete, facet panel,
  sort controls.
- **15. Frontend tests.** Vitest + Testing Library; client-side validation is a graded requirement.

Architecture and request flow are documented in [../README.md](../README.md#architecture) — that is
the single source, kept accurate because reviewers read it.
