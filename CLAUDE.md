# i-love-shopping — Project Context

B2C e-commerce platform (Japanese products). School project in 3 parts.
**Current scope: Project 1** — auth, database, product catalog.

All project requirements and test specifications are in [README.md](README.md).

## Stack

| Layer | Choice |
|---|---|
| Frontend | React 18 + TypeScript + Vite, managed with Bun |
| Frontend state | Redux Toolkit (auth) + React Query (server data) |
| Routing | React Router v6 |
| HTTP | Axios with interceptors (access token stored in memory var) |
| Backend | Spring Boot 3.2, Java 21 |
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

See [docs/FOLDER_STRUCTURE.md](docs/FOLDER_STRUCTURE.md)

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

- Access token: JS module variable only. Never localStorage/sessionStorage.
  On page reload → call `/auth/refresh` on mount to rehydrate.
- Refresh rotation: Redis MULTI/EXEC or Lua to atomically swap tokens.
  Old token replayed = key gone = rejected.
- Password reset: revokes ALL refresh tokens for that user (iterate `refresh_tokens_user` set).
- 2FA: partial session via Redis challenge key after password OK, full JWT only after TOTP.
  TwoFactorService returns `otpauth://` URI for QR code generation.
  Generate 8 backup codes on 2FA setup, store hashed.
- Token revocation: logout writes JTI to blocklist, filter checks on every request.
- GDPR: UserService must support hard delete (cascade) and data export endpoint.

## Build Order (P1)

See [docs/BUILD_ORDER.md](docs/BUILD_ORDER.md)

## Open Questions / Known Gaps

- Image CDN strategy for production (currently: Docker volume + nginx)
- GDPR data export format (JSON dump of user record + orders)
- Rate limiting (not in P1 rubric but relevant for CAPTCHA bypass)
- Elasticsearch for P3 — SearchService interface is already the abstraction point
