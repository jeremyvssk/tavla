# i-love-shopping

A B2C e-commerce platform. The demo catalog is **chess, Go and backgammon**: about 1,770 real
products (sets, boards, pieces, clocks, chess books, Go equipment, backgammon sets) imported from
three suppliers that sell to resellers; see [tools/catalog-import](tools/catalog-import/README.md).
Nothing in the code depends on the niche except the seed data.

Built in three projects. **This repository is Project 1 (Foundation):** secure user accounts, a
relational database designed for growth, and the product catalog that Projects 2 (Commerce) and
3 (Experience) build on. The assignment brief is kept verbatim at
[docs/ASSIGNMENT.md](docs/ASSIGNMENT.md).

> **Status — read this first.** Authentication, the catalog backend (browsing, faceted search,
> suggestions, reviews, image upload), rate limiting and the customer-facing UI for all of it are
> built and tested. The UI uses one visual theme, Tavla. A cart exists in the browser only; checkout
> and payment are Project 2. See [Project status](#project-status) for the honest line-by-line breakdown before reviewing.

---

## Quick start

Docker is the only prerequisite.

```sh
git clone <this-repo> && cd i-love-shopping1
./start.sh
```

That builds and starts all five containers. First run takes a few minutes (Maven and Bun both
resolve dependencies); subsequent runs are cached.

| | URL |
|---|---|
| App | <http://localhost:5173> |
| API | <http://localhost:8080> (this machine only; other devices use the app URL) |
| MailHog (catches all outbound dev email) | <http://localhost:8025> |
| Postgres | `localhost:5432` |
| Redis | `localhost:6379` |

```sh
./start.sh down     # stop
./start.sh reset    # stop and delete volumes (wipes the database)
docker compose logs -f backend
```

`docker-compose.yml` carries a working development default for every variable, so a bare
`docker compose up --build` works too. `./start.sh` additionally writes a `.env` from
`.env.example` to give you one place to edit. `.env` is gitignored and holds no real secrets in
development.

---

## Usage guide

A walkthrough of everything currently working. Everything below works in the app at
<http://localhost:5173>:

| Page | What to try |
|---|---|
| `/` | Hero, category tiles and featured products. |
| `/catalog` | Facets (category, brand, price band, rating), sort, paging. The category list starts at the four games and opens one level at a time. Filters live in the URL. |
| `/catalog?q=stanton` | A misspelling: no whole-word match, so it falls back to similar names and says so. |
| Header search | Type `wal`: suggestions appear after a short pause. Arrow keys and Enter work. |
| `/catalog/{id}` | Specs in metric and imperial, attributes, reviews; signed in, post a review. |
| `/register`, `/login` | Client-side validation mirroring the server rules; 2FA step when enabled. |
| `/account` | Profile from `/auth/me`; set up 2FA with a QR code, backup codes shown once. |
| `/forgot` → MailHog → `/reset?token=…` | Password reset from the emailed link. |

To see the access-token rule: sign in, open devtools, reload `/account`. Application → Local
Storage holds only the cart (`tavla.cart`), no token, and the Network tab shows one `/auth/refresh` restoring the
session from the `httpOnly` cookie.

All of it can also be driven from `curl`; the API is reachable through the app origin too (see
[Request flow](#request-flow)).

### Register and log in

```sh
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"correct horse battery staple","fullName":"Your Name"}'

curl -i -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"correct horse battery staple"}'
```

The login response body carries the **access token**. The **refresh token** comes back as an
`httpOnly; Secure; SameSite=Strict; Path=/auth` cookie — it is never readable by JavaScript.

### Refresh rotation (single-use)

```sh
curl -i -X POST http://localhost:8080/auth/refresh -b 'refresh_token=<the cookie value>'
```

Every refresh issues a **new** refresh token and destroys the old one. Replay the same cookie a
second time and it is rejected — the Redis key is already gone. This is the behaviour to test at
review; see [Security design](#security-design) for why it is done with a Lua script.

### Password reset via email

`POST /auth/forgot-password` with an email address, then open **MailHog at
<http://localhost:8025>** to read the message and follow the reset link. Completing a reset
revokes every refresh token that user holds.

### Two-factor authentication

`POST /auth/2fa/setup` (authenticated) returns an `otpauth://` URI — render it as a QR code or
paste it into Google Authenticator / Authy. `POST /auth/2fa/enable` with a code from the app turns
it on and returns eight single-use backup codes. From then on `POST /auth/login` returns a
challenge instead of tokens, and real tokens are only issued by `POST /auth/2fa/login`.

### CAPTCHA

Off by default so review does not depend on Google keys. Set `RECAPTCHA_ENABLED=true` plus
`RECAPTCHA_SITE_KEY` / `RECAPTCHA_SECRET_KEY` in `.env` to require a verified reCAPTCHA token on
registration.

### Browse and search the catalog

The database starts with about 1,770 seeded products, with photos and reviews, so there is
something to browse on a fresh clone. Browsing a category and searching are the same endpoint:

```sh
curl 'http://localhost:8080/categories'                                   # the category tree
curl 'http://localhost:8080/products?category=chess'                      # Chess and every subcategory
curl 'http://localhost:8080/products?q=walnut&sort=relevance'             # full-text search, ranked
curl 'http://localhost:8080/products?q=walnut&brand=sunrise-chess-games&minPrice=80&maxPrice=200&minRating=4'
curl 'http://localhost:8080/search/suggestions?q=stau'                    # search as you type
```

A query with no whole-word match (`wal`, `stanton`, `clok`) is retried against product names by
trigram similarity, and the response carries `"approximate": true` so the UI can say so.

Every listing returns one page of products plus **facet counts** for the whole result set:
categories, brands, price bands and "N stars and up". `sort` is one of `featured`, `relevance`,
`price_asc`, `price_desc`, `rating`, `newest`. Browsing defaults to `featured` (in stock first, then
products with a photo, then a Bayesian average rating) and a search to `relevance`. `minPrice` is inclusive and `maxPrice` exclusive, the same rule the
price bands use, so a count next to a filter always matches what the filter returns.

### Admin: manage products and images

There is deliberately no API for becoming an admin. Register normally, then promote the account:

```sh
docker compose exec postgres psql -U app -d iloveshopping \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com'"
```

Log in again (the role is read into the new access token), then `POST /products`,
`PUT /products/{id}`, `DELETE /products/{id}`, `POST /categories`, `POST /brands`, and upload images
with `curl -F file=@board.jpg http://localhost:8080/products/{id}/images`. Uploaded images appear at
`http://localhost:5173/images/products/<uuid>.jpg`.

### Google OAuth

Set `GOOGLE_CLIENT_ID` in `.env`, then `POST /auth/oauth/google` with a Google ID token. An email
already registered with a password cannot be silently taken over by the OAuth flow — it returns a
409 rather than merging the accounts.

---

## API reference

All request bodies are JSON and validated at the boundary; failures return `400` with a per-field
error map. Unknown fields are rejected outright (mass-assignment guard). A rate-limited request is
`429` with a `Retry-After` header (see [Security design](#security-design)).

### Public

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create an account. Optional CAPTCHA token. |
| `POST` | `/auth/login` | Email + password. Returns tokens, or a 2FA challenge. |
| `POST` | `/auth/2fa/login` | Exchange a 2FA challenge + TOTP code for tokens. |
| `POST` | `/auth/oauth/google` | Exchange a Google ID token for tokens. |
| `POST` | `/auth/refresh` | Rotate the refresh cookie, get a new access token. |
| `POST` | `/auth/forgot-password` | Send a reset email. Always `200`, so it cannot be used to enumerate accounts. |
| `POST` | `/auth/reset-password` | Consume a reset token, set a new password, revoke all sessions. |
| `GET` | `/categories` | Active category tree, nested. |
| `GET` | `/brands` | All brands. |
| `GET` | `/products` | Browse and search: `q`, `category`, `brand` (repeatable), `minPrice`, `maxPrice`, `minRating`, `sort`, `page`, `size` (max 48). Returns items, totals, facet counts and `approximate`. |
| `GET` | `/products/{id}` | Product detail: breadcrumb, brand, images, attributes, metric and imperial measurements, and `variants`: the other colours or sizes of the same product. Inactive products are `404`. |
| `GET` | `/products/{id}/reviews` | Reviews, newest first, paged. |
| `GET` | `/search/suggestions` | Up to 8 product names matching a fragment of 2+ characters. |

### Authenticated (`Authorization: Bearer <access token>`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/auth/me` | The caller's id, email, name, role, sign-in method and 2FA state. |
| `POST` | `/auth/logout` | Blocklist the access token's JTI, destroy the refresh token. |
| `POST` | `/auth/2fa/setup` | Re-confirm the password, get an `otpauth://` provisioning URI. `409` if 2FA is already on. |
| `POST` | `/auth/2fa/enable` | Verify a TOTP code, enable 2FA, return backup codes. |
| `POST` | `/auth/2fa/disable` | Re-confirm the password, disable 2FA. |
| `POST` | `/products/{id}/reviews` | Post a review (rating 1–5). One per user per product; a second is `409`. |
| `DELETE` | `/products/{id}/reviews/{reviewId}` | Delete a review. Only its author or an admin; anyone else gets `403`. |

### Admin only (`ADMIN` role)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/products` | Create a product. Metric measurements only; imperial is computed. |
| `PUT` | `/products/{id}` | Replace a product's writable fields. |
| `DELETE` | `/products/{id}` | Delete a product, its images (rows and files) and reviews. |
| `POST` | `/products/{id}/images` | Multipart upload (`file`, optional `altText`, `primary`). JPEG or PNG, max 5 MB. |
| `DELETE` | `/products/{id}/images/{imageId}` | Remove an image and its file. |
| `POST` | `/categories` | Create a category, optionally under a `parentId`. |
| `POST` | `/brands` | Create a brand. |

Errors are shaped consistently by a single `@RestControllerAdvice`:

```json
{ "error": "validation_failed", "fields": { "email": "must be a well-formed email address" } }
```

Stack traces, SQL and internal paths never reach a response body.

---

## Database

PostgreSQL 16. The full diagram:

![Entity relationship diagram](docs/i-love-shopping-erd.png)

Schema is owned by Flyway migrations in `backend/src/main/resources/db/migration/`, applied
automatically on startup. Migrations are append-only — a committed `V*.sql` is never edited.

### Entities

| Table | Key | Notes |
|---|---|---|
| `users` | `UUID` | Unique email, BCrypt hash, `LOCAL`/`GOOGLE`/`FACEBOOK` provider, role, 2FA secret + backup codes |
| `categories` | `INTEGER` | Self-referencing `parent_id` for an arbitrary-depth tree |
| `brands` | `INTEGER` | Name, unique slug, logo |
| `products` | `UUID` | Price `DECIMAL(10,2)`, stock, category, brand, `JSONB` attributes, both metric and imperial weight/dimensions, generated `tsvector`, denormalised rating |
| `product_images` | `INTEGER` | Ordered, one flagged `is_primary` |
| `product_reviews` | `UUID` | Rating `CHECK BETWEEN 1 AND 5`, one review per user per product |

### Design decisions worth asking about

- **Three different FK delete rules, deliberately.** `products.category_id` is `RESTRICT` — a
  category with products in it must not vanish. `products.brand_id` is `SET NULL` — losing a brand
  should orphan the product, not delete it. `product_images` and `product_reviews` are `CASCADE` —
  they have no meaning without their product.
- **`search_vector` is `GENERATED ALWAYS ... STORED`,** so Postgres maintains it on write rather
  than recomputing `to_tsvector` on every query. Costs disk, saves per-query CPU.
- **`average_rating` and `review_count` are denormalised onto `products`** so that sorting the
  catalog by rating is an index scan, not an aggregate over `product_reviews`. A **trigger** on
  `product_reviews` (V6) keeps them true for every writer, including the seed data and psql. It
  locks the product row in a separate statement before recomputing, because under `READ COMMITTED`
  that is what lets the second of two simultaneous reviews see the first. `ReviewIT` runs two
  overlapping transactions to prove it, and fails if the lock step is removed.
- **Search is weighted and indexed (V5).** The generated `search_vector` gives the name weight A,
  the description B and attribute values (wood, style) C, so `ts_rank` ranks a name match first. A
  GIN index serves full-text search, a `pg_trgm` GIN index serves suggestions, and btree indexes
  cover every foreign key, which Postgres never indexes on its own. `EXPLAIN ANALYZE` over 50,000
  products shows bitmap index scans on both GIN indexes.
- **Metric is the source of truth for measurements.** Clients send kilograms and centimetres; the
  imperial columns are computed on every write, so a product can't weigh 2 kg and 9 lbs at once.
- **Seed data is a repeatable migration** (`db/seed/R__seed_catalog.sql`), kept apart from the
  versioned schema. Flyway re-applies it when the file changes, and every insert is an upsert, so
  the demo catalog can change without a new migration. Production would leave `db/seed` out of
  `FLYWAY_LOCATIONS`. The file is generated by `tools/catalog-import` from supplier data; product
  UUIDs derive from the supplier's own id, so re-importing updates rows in place. Seed photos are
  hotlinked from the suppliers rather than stored, since a reseller's image licence only lasts as
  long as the partnership.
- **Colour and size variants live in `attributes`**, not in their own table. The importer groups
  supplier listings that are one product in several colours ("King's Chess Set – Small" in five)
  and writes `variant_group` and `variant` (`{"Colour": "Blue"}`) into each row's JSONB; the
  detail endpoint returns the active siblings, and the product page shows them as options. Each
  variant stays a product with its own stock and price, which is how the suppliers sell them. A
  `product_variants` table would be the next step once admins need to edit groups.
- **A partial unique index** (`uq_categories_root_name WHERE parent_id IS NULL`) enforces unique
  names among root categories while still allowing the same subcategory name under two parents.
- **Constraints live in the database, not only in DTOs.** Every `@NotBlank` has a `NOT NULL`, every
  `@Size(max=N)` a `VARCHAR(N)`, every range a `CHECK`. Application checks are hopes; database
  constraints are facts.

### ACID

Postgres gives all four out of the box, and the design leans on them: **atomicity** so a
multi-statement operation such as "reset password and revoke every session" cannot half-apply;
**consistency** through the FK, `CHECK` and `UNIQUE` constraints above; **isolation** so two
concurrent registrations of the same email cannot both succeed — the `UNIQUE` index makes one of
them fail, and the service catches `DataIntegrityViolationException` to turn it into a clean `409`
instead of a race; **durability** through the WAL, so a committed order survives a crash.

### Scaling path

Read replicas for catalog reads, connection pooling (HikariCP, already in use), partitioning
`product_reviews` by product once it is large, and `JSONB` for per-category attributes so new
product types do not need a migration. Redis already absorbs the hot, short-lived, high-churn
data that would otherwise hammer Postgres. `SearchService` is an interface with a Postgres
implementation so search can move to Elasticsearch in Project 3 without touching its callers.

---

## Architecture

**Modular monolith.** One Spring Boot application, split by *domain* rather than by layer:

```
com.iloveshopping/
├── user/       User, UserRepository, UserService
├── auth/       JWT, refresh rotation, 2FA, CAPTCHA, OAuth, password reset
├── catalog/    products, categories, brands, images, reviews
│   └── search/ SearchService + PostgresSearchService, suggestions
├── storage/    StorageService + LocalStorageService (the images volume)
├── config/     SecurityConfig, PasswordConfig, RestClientConfig, GoogleOAuthConfig
└── exception/  GlobalExceptionHandler, ErrorResponse
```

Each domain owns its own controller, service, repository, entity and exceptions. Cross-domain
access goes through service interfaces — never repository to repository.

**Why not microservices:** at this size they would add network calls, distributed transactions and
deployment overhead to buy independent scaling nobody needs yet. The domain boundaries here are
real, so if one domain ever does need to be extracted, the seam is already cut. **Why not a plain
layered monolith:** a `controllers/ services/ repositories/` split makes every feature a change in
three packages and lets domains reach into each other's data unnoticed.

**Stateless processes.** No `HttpSession`, no in-memory user state, no static maps. Every
session-like thing lives in Redis, so any instance can serve any request and the app scales
horizontally.

### Request flow

```
browser :5173  ──►  nginx (frontend container)
                      │
                      ├─ /                    SPA bundle, with a try_files fallback
                      ├─ /images/…            product images off the shared volume
                      └─ /auth/… /products/…  proxied to backend:8080
                                                   │
                                    ┌──────────────┼──────────────┐
                                 postgres        redis         mailhog
```

nginx serves the app *and* proxies the API, so the browser only ever talks to one origin. That
means **no CORS anywhere in the stack** and no cross-origin credential sharing to get wrong. The
proxy passes paths through verbatim rather than rewriting them under an `/api` prefix, because the
refresh cookie is scoped `Path=/auth` and a rewritten prefix would stop the browser from ever
sending it back. Port `8080` stays published so the API can also be driven directly with `curl`.

### Storage map

| Store | Holds |
|---|---|
| Postgres | Durable user, product, category, brand, image and review data |
| Redis | Refresh tokens, the access-token JTI blocklist, pending 2FA challenges, password-reset tokens — all TTL'd |
| Docker volume `product_images_data` | Image bytes, written by the backend, served by nginx. An S3 `StorageService` implementation replaces this for production. |

---

## Security design

- **Access token: in memory only.** A JavaScript module variable, never `localStorage` or
  `sessionStorage`, so an XSS payload cannot read it out of storage. On page reload the app calls
  `/auth/refresh` to rehydrate. Short-lived (15 minutes by default, `JWT_ACCESS_TTL`).
- **Refresh token: `httpOnly` cookie,** scoped `Path=/auth`, `SameSite=Strict`, `Secure`. Not
  reachable from JavaScript at all. Seven-day TTL (`JWT_REFRESH_TTL`).
- **Rotation is atomic.** `TokenStoreService` swaps the old token for a new one inside a **Redis
  Lua script**, so read-old / delete-old / write-new is one indivisible operation. Done as three
  separate Redis calls, two requests arriving a few milliseconds apart could both validate the same
  token and both get fresh ones. Under the Lua script, the second one finds the key gone and is
  rejected.
- **Revocation for both token types.** Logout writes the access token's `jti` to a Redis blocklist
  with a TTL equal to the token's remaining lifetime, and `JwtAuthenticationFilter` checks it on
  every request. The refresh token is deleted outright.
- **Password reset revokes everything.** Every refresh token for that user is destroyed, via the
  `refresh_tokens_user:{userId}` set — so a reset actually locks an attacker out.
- **No PII in the JWT.** Payload is `sub`, `role`, `iat`, `exp`, `jti`. No email, no name.
- **Identity is not authorisation.** A valid JWT proves who you are; the service and database still
  check that the resource being touched belongs to that `sub`.
- **Passwords are NFC-normalised before BCrypt**, on both register and login, so accented
  characters are never silently rejected or mismatched.
- **CSRF is disabled deliberately, not carelessly.** State-changing requests carry a `Bearer`
  header, which browsers do not attach automatically, and the one cookie in play is
  `SameSite=Strict` and scoped to `/auth`.
- **Rate limits, in two layers, in Redis.** Per client IP on login, 2FA login, Google login,
  register, forgot- and reset-password, and on search and suggestions (a search is six queries).
  Login is also counted per account, following the OWASP guidance: a strict counter per account
  *and* IP stops one address guessing one account without locking the real user out anywhere
  else, and a looser counter per account catches guessing spread over many addresses. A blocked
  address stops feeding the account counter, a correct password clears both, and a password reset
  lifts the lockout, so an attacker can never lock someone out for good. Reset emails are capped
  per address. Each counter is one atomic Lua call (`INCR` plus its expiry).
- **The client IP is nginx's `X-Real-IP`,** which nginx overwrites, so a client cannot choose its
  own address. The backend port is published on `127.0.0.1` only, because a backend reachable from
  the LAN would accept that header from anyone.
- **Every secret is an environment variable.** No secret is committed; `.env.example` holds names
  and placeholders only.

---

## Testing

```sh
cd backend && mvn verify     # unit + integration; Testcontainers needs Docker running
cd frontend && bun run test   # Vitest; plain `bun test` runs Bun's own runner instead
```

All integration tests share one Postgres and one Redis container through
`AbstractIntegrationTest`. They run against **real Postgres and Redis in Testcontainers**, never H2 — H2 lies
about the Postgres-specific features this schema depends on (`JSONB`, `tsvector`, generated
columns).

| Kind | Covers |
|---|---|
| Unit | `JwtServiceTest` (generation, validation, expiry), `TokenStoreServiceTest` (rotation, single-use), `TwoFactorServiceTest`, `PasswordResetServiceTest`, `CaptchaServiceTest`, `GoogleTokenVerifierTest`, `UserServiceTest` |
| Unit (catalog) | `ProductRequestValidationTest` (the product data model's rules), `UnitConversionTest`, `ImageProcessorTest` (magic bytes, decompression bomb, stripped payloads), `ReviewServiceTest` (ownership), `PostgresSearchServiceTest` |
| Integration | `AuthControllerIT`, `PasswordResetControllerIT`, `TwoFactorControllerIT`, `CatalogBrowseIT`, `ProductAdminIT`, `SearchIT` (relevance, facet counts, price bounds, paging, suggestions), `ReviewIT` (including concurrent reviews), `ProductImageIT` — full Spring context over MockMvc, covering `400`/`401`/`403`/`404`/`409` paths, not just happy paths |
| Rate limits | `RateLimitIT` — per-IP limits and the `429` shape, one address locked out of one account while the owner elsewhere still signs in, distributed guessing hitting the account limit, a blocked address not feeding the account counter, a correct password resetting both |
| Frontend | Vitest + Testing Library: the validation rules at their boundaries, the login and register forms (messages, disabled while pending, server errors, 429 wait time, 2FA step), and the refresh interceptor (two failing requests share one refresh; a wrong password never triggers one) |
| Security | `InputValidationIT` — SQL injection into search, sort and filter parameters, LIKE wildcards, oversized strings, deep JSON, mass assignment, script URLs, and hostile uploads (renamed scripts, SVG, truncated files, decompression bombs, path-traversal filenames, polyglot payloads). Every probe must be a 4xx with no SQL, class names or paths in the body |
| Manual | CAPTCHA, Google OAuth and the 2FA enrolment flow, per the brief |

Gaps are listed honestly under [Project status](#project-status).

---

## Project status

What a reviewer can and cannot exercise today.

**Working and tested**

- Email + password registration and login, with server-side validation
- Google OAuth login
- reCAPTCHA on registration (opt-in via env)
- JWT access tokens in memory, refresh tokens as `httpOnly` cookies
- Single-use refresh rotation, atomic in Redis
- Revocation of both token types
- Password recovery and reset by email, through MailHog
- Optional user-enabled 2FA with TOTP and backup codes
- Full schema and ERD for every Project 1 entity
- Catalog backend: category tree, browsing a category with all its subcategories, product detail
  with metric and imperial measurements, admin create/update/delete for products, categories and
  brands
- Search: weighted full-text relevance, faceted filtering by category, brand, price and rating,
  sorting by featured, relevance, price, rating and newest, and search-as-you-type suggestions
- Reviews with a database-maintained average rating
- Image upload: type checked from the bytes, re-encoded, stored under a generated name, served by nginx
- About 1,770 seeded products from real suppliers, with every supplier photo and generated demo
  reviews, so everything above is demonstrable on a fresh clone
- Product page photo gallery, and colour/size variants shown as options that switch between
  sibling products
- Typo and prefix fallback for searches with no whole-word match
- Rate limiting per IP and per account, with a lockout the real user can always get past
- `GET /auth/me`
- Frontend: routing, Redux auth state, access token in a module variable with a single-flight
  refresh interceptor, session restore on reload, register / login / 2FA / forgot / reset / account
  pages with client-side validation, catalog with facets and sort, product page with reviews,
  debounced search suggestions
- Cart: add from the product page, a side panel with quantity steppers capped at stock, a subtotal
  and a free-shipping progress bar. Kept in the browser (`localStorage`), not on the server.
- Unit, API integration, security and frontend test suites for all of the above
- One-command Docker startup

**Not built yet**

- **Checkout and payment** — the cart's Checkout button is disabled until Project 2. Cart prices are
  the ones shown when each item was added; checkout will re-price them on the server.
- **Deleting your own review from the UI** — the API supports it; the page does not offer it yet.
- **Admin UI** — admin operations are API-only; the dashboard is Project 3.

Planned order of work is in [claude-docs/BUILD_ORDER.md](claude-docs/BUILD_ORDER.md).

---

## Configuration

Every value is an environment variable, per [12-factor](https://12factor.net). Defaults in
`docker-compose.yml` are development-only.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `iloveshopping` / `app` / `changeme` | Database credentials |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/iloveshopping` | JDBC URL; host is the compose service name |
| `REDIS_URL` | `redis://redis:6379` | Token store |
| `JWT_SECRET` | dev placeholder | Signing key. **Must be replaced outside development.** |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT15M` / `P7D` | Token lifetimes (ISO-8601 durations) |
| `SMTP_HOST` / `SMTP_PORT` / `MAIL_FROM` | `mailhog` / `1025` / `noreply@iloveshopping.local` | Outbound mail |
| `FRONTEND_RESET_URL` | `http://localhost:5173/reset` | Base of the link in reset emails |
| `GOOGLE_CLIENT_ID` | empty | Google OAuth; the flow is inert until set |
| `RECAPTCHA_ENABLED` / `RECAPTCHA_SITE_KEY` / `RECAPTCHA_SECRET_KEY` | `false` / empty / empty | CAPTCHA on registration |
| `TWO_FACTOR_ISSUER` | `i-love-shopping` | Label shown in authenticator apps |
| `IMAGES_DIR` | `/var/app/images` | Where uploaded images are written; the `product_images_data` volume |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/seed` | Drop `db/seed` to start without the demo catalog |
| `RATE_LIMIT_ENABLED` | `true` | Per-IP and per-account rate limits |
| `FRONTEND_PORT` / `BACKEND_PORT` | `5173` / `8080` | Published host ports (the backend on `127.0.0.1` only) |

A missing required variable fails the application at boot rather than at first use. That is
intentional.

---

## Troubleshooting

**Ports already in use.** Set `FRONTEND_PORT`, `BACKEND_PORT`, `POSTGRES_PORT` or `REDIS_PORT` in
`.env` and restart.

**Backend exits on startup.** Almost always Flyway hitting a database left over from an older
schema. `./start.sh reset` drops the volumes and starts clean.

**No reset email arrives.** Nothing leaves the machine in development — every message is captured
by MailHog at <http://localhost:8025>.

**Reach the app at `localhost`, not a LAN IP.** The refresh cookie is marked `Secure`, and browsers
only allow that over plain HTTP for `localhost`.

**`mvn verify` fails to start containers.** Testcontainers needs a running Docker daemon and the
current user in the `docker` group.

---

## Repository layout

```
├── start.sh                one-command startup
├── docker-compose.yml      postgres, redis, mailhog, backend, frontend
├── .env.example            every variable, with placeholders
├── backend/                Spring Boot 4 · Java 21
│   ├── src/main/resources/db/migration/   Flyway schema migrations (append-only)
│   └── src/main/resources/db/seed/        repeatable demo-catalog seed
├── frontend/               React 18 · TypeScript · Vite · Bun
│   └── nginx.conf          SPA fallback, API proxy, image serving
├── tools/catalog-import/   supplier scrapers + the generator for the catalog seed
├── docs/                   ERD, assignment brief
└── claude-docs/            design and build notes
```
