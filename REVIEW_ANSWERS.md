# Review Answers — Project 1

Start the app: `./start.sh` → app at <http://localhost:5173>, emails at <http://localhost:8025> (MailHog).

Run the tests:
- Backend: `cd backend && mvn verify` (Docker must be running)
- Frontend: `cd frontend && bunx vitest run`

**Latest run (2026-09-28): 202 tests, all passing** — 85 backend unit, 80 backend integration + security, 37 frontend.

Backend paths below are short for `backend/src/main/java/com/iloveshopping/…`.

---

### 1. The README file contains a clear project overview, entity relationship diagram, setup instructions, and usage guide

Yes. [README.md](README.md) has:
- the overview and what works / what isn't built yet
- the ERD
- Quick start (`./start.sh`)
- how the main features and search work
- a usage guide: what to try in the website, step-by-step 2FA setup, how to get the Google keys for CAPTCHA and Google login, and the API

[backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md) show where each feature lives in the code and how the tests are organised.

### 2. The platform implements a Business-to-Consumer (B2C) e-commerce model.

Yes. One shop sells directly to customers. Anyone can register as a `CUSTOMER`. `ADMIN` manages the catalog and can only be granted directly in the database, never through the API.

### 3. The system implements both email-password and OAuth authentication methods.

Yes.
- **Email + password:** `/auth/register`, `/auth/login`. Passwords are hashed with BCrypt.
- **OAuth:** Google sign-in. The backend verifies Google's ID token itself (`auth/GoogleTokenVerifier.java`).
- If an email is already registered with a password, Google login returns `409` instead of merging the accounts, so nobody can take over an account that way.

Google login needs a Google client ID, so it's hidden until one is set. Step-by-step setup: [README → Needs your own Google keys](README.md#needs-your-own-google-keys). The button then appears on `/login`.

Tests: `AuthControllerIT`, `GoogleTokenVerifierTest`, `UserServiceTest`.

### 4. CAPTCHA is integrated into the registration process.

Yes, Google reCAPTCHA on `/register`. The backend checks the token with Google and also checks the hostname, so a token solved on another site doesn't work (`auth/CaptchaService.java`).

It's off by default so the app runs without Google keys. To turn it on, set `RECAPTCHA_ENABLED=true` and both keys in `.env`, then run `./start.sh` again. Step-by-step: [README → Needs your own Google keys](README.md#needs-your-own-google-keys).

Tests: `CaptchaServiceTest` (6 tests).

### 5. Student can explain the concept of JWT and its components (header, payload, signature).

A JWT is a signed token in three parts: `header.payload.signature`, each base64url-encoded.
- **Header:** which algorithm signed it (HMAC-SHA here) and the type (`JWT`).
- **Payload:** the claims. Ours has only `sub` (user id), `role`, `iat` (issued at), `exp` (expiry, 15 min) and `jti` (unique id, used for logout). No email or name, because anyone holding the token can read the payload. It's encoded, not encrypted.
- **Signature:** a hash of header + payload made with the server's secret key. Change one character and the check fails. It proves the token is genuine and unchanged. It does not hide anything.

Why use one: the server can verify it without a database lookup, so any backend instance can handle any request. The downside is that you can't cancel a JWT once it's issued. That's why it only lives 15 minutes and there's a blocklist (question 9).

Code: `auth/JwtService.java`. Tests: `JwtServiceTest` (claims, tampered token, wrong key, expired token).

### 6. Access tokens are stored in memory.

Yes. The token is a plain variable in `frontend/src/api/client.ts` (`let accessToken`). It's never in localStorage, sessionStorage or a cookie JavaScript can read, so an XSS attack can't steal it from storage.

On page reload the variable is empty, so the app calls `/auth/refresh`, which uses the httpOnly cookie, and gets a new one.

Demo: log in → DevTools → Application → storage has no token → reload → the Network tab shows one `/auth/refresh`.

### 7. Refresh token rotation is implemented with single-use validation.

Yes. Every call to `/auth/refresh` gives you a new refresh token and deletes the old one.

The swap runs as **one Redis Lua script** (`auth/TokenStoreService.java`), so it can't be split: check the old token, delete it, save the new one. Without that, two requests at the same moment could both use the same token.

Also:
- The refresh token is an `httpOnly; Secure; SameSite=Strict` cookie that JavaScript can't read.
- Redis stores only its SHA-256 hash, so a Redis dump contains no usable tokens.

### 8. Verify that each refresh token can only be used once and new refresh token is issued with each refresh. Old refresh tokens must be rejected.

Yes, and there's a test for it: `AuthControllerIT.refresh_rotatesToken_andReplayedCookieIsRejected`.

Extra: if an old token ever comes back, it must have been stolen, so the server **logs that user out of all sessions**.

Live demo:
```sh
curl -i -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"…"}'                     # copy the refresh_token cookie
curl -i -X POST localhost:8080/auth/refresh -b 'refresh_token=OLD'     # 200, new cookie
curl -i -X POST localhost:8080/auth/refresh -b 'refresh_token=OLD'     # 401, old one rejected
curl -i -X POST localhost:8080/auth/refresh -b 'refresh_token=NEW'     # 401, reuse logged out everything
```

### 9. Token revocation mechanism is in place for both access and refresh tokens.

Yes.
- **Access token:** on logout its `jti` goes into a Redis blocklist, which expires when the token would have. Every request checks the blocklist (`auth/JwtAuthenticationFilter.java`).
- **Refresh token:** deleted from Redis. A password reset or a detected reuse deletes **all** of that user's refresh tokens.

Test: `AuthControllerIT.logout_blocklistsAccessToken_soItStopsWorking`.
Demo: `/auth/me` returns 200, then logout, then the same token gets 401.

### 10. Password recovery and reset functionality via email is implemented.

Yes.
1. `/forgot` sends an email with a one-time link. It always answers 200, so nobody can use it to check which emails exist.
2. The token is random, stored hashed in Redis, and expires after 15 minutes.
3. A successful reset changes the password and **logs out every session**.

Demo: `/forgot` → MailHog at `localhost:8025` → click the link → set a new password.
Tests: `PasswordResetServiceTest`, `PasswordResetControllerIT`.

### 11. Two-factor authentication (2FA) is available as an optional, user-enabled feature.

Yes. It's off until the user turns it on in `/account`.
1. **Setup:** confirm your password, then a QR code appears. Scan it from inside Google Authenticator or Authy. On an iPhone, the Passwords app also works, but only after you save an entry with website `localhost` (full steps in the [README](README.md#in-the-website)).
2. **Enable:** enter the 6-digit code from the app, not the long setup key. You get 8 backup codes, shown once only.
3. **Login:** the password step now returns a 5-minute challenge instead of tokens. Tokens are issued only after a valid code. Backup codes work once each.
4. **Disable:** needs your password.

Tests: `TwoFactorServiceTest` (14), `TwoFactorControllerIT` (4), `LoginPage.test.tsx`.

### 12. User input validation is implemented on both client and server sides for authentication forms.

Yes, with the same rules on both sides.
- **Client:** `frontend/src/validation/authRules.ts`. Email format, password 8–72 characters, name required, passwords must match. Errors show next to each field and the request isn't sent.
- **Server:** validation annotations (`@NotBlank`, `@Email`, `@Size`) on every request DTO. Errors come back as `400` with a message per field. Unknown JSON fields are rejected.

The client check is for user experience. The server check is the real one, because anyone can skip the frontend with curl.

Why 72: BCrypt ignores everything after 72 bytes.

Tests: `authRules.test.ts`, `LoginPage.test.tsx`, `RegisterPage.test.tsx`, `AuthControllerIT`, `InputValidationIT`.

### 13. Student can explain the chosen database's scalability features and how they support potential growth of the e-commerce platform.

PostgreSQL 16.
- **Read replicas:** shoppers mostly browse, so catalog reads can go to copies of the database.
- **Connection pooling:** HikariCP is already in use. PgBouncer can go in front when there are many app instances.
- **Indexes for the real queries:** GIN for full-text search, trigram GIN for suggestions, btree on foreign keys and sort columns. Tested on 50,000 products with `EXPLAIN ANALYZE`, which showed the queries using the indexes.
- **Precomputed rating:** the average rating is stored on the product and kept up to date by a trigger, so "sort by rating" doesn't recalculate over all reviews.
- **JSONB attributes:** new product types don't need a schema change.
- **Partitioning:** big tables like reviews, and orders later, can be split when they grow.
- The app itself keeps no state (sessions live in Redis), so you can run as many backend instances as you need.

### 14. Student can explain ACID properties and their importance in e-commerce database design.

- **Atomicity:** all or nothing. An order must not take the money without reducing stock. Here: a new review and its rating update commit together.
- **Consistency:** rules always hold, enforced by the database itself: rating 1–5, price ≥ 0, stock ≥ 0, unique email, one review per user per product, valid foreign keys.
- **Isolation:** simultaneous transactions don't corrupt each other. Two signups with the same email: one wins, the other gets `409`. Two reviews at the same time: the trigger locks the product row so the average comes out right (tested in `ReviewIT`).
- **Durability:** once committed, it survives a crash (write-ahead log). A paid order can't disappear.

Prices are `NUMERIC(10,2)`, not float, so there are no rounding errors on money.

### 15. An Entity Relationship Diagram (ERD) is provided, clearly showing entities, attributes, relationships, primary keys, foreign keys, cardinality, and modality.

Yes: [docs/i-love-shopping-erd.png](docs/i-love-shopping-erd.png), also shown in the README.

| Relationship | Cardinality | Required? |
|---|---|---|
| category → products | 1 : many | a product **must** have a category; a category can be empty |
| brand → products | 1 : many | a product **may** have a brand |
| category → subcategories | 1 : many (self-reference) | top-level categories have no parent |
| product → images | 1 : many | an image **must** belong to a product; a product may have none |
| product → reviews | 1 : many | a review **must** belong to a product |
| user → reviews | 1 : many | a review **must** belong to a user; max one review per user per product |

Deletes are handled on purpose: deleting a category that still has products is **blocked**, deleting a brand **leaves its products without a brand**, deleting a product **deletes its images and reviews**.

### 16. Student can demonstrate and explain the search implementation including database design and basic text search functionality.

- `products.search_vector` is a column Postgres fills in automatically from the name (weight A, most important), the description (B) and the attributes (C).
- A GIN index on that column makes search fast.
- Results are ranked with `ts_rank`, so a match in the name beats a match in the description.
- **Typo fallback:** if nothing matches (`stanton`, `wal`), it searches for similar names with trigrams and the page says "showing similar results".
- **Suggestions while typing:** `/search/suggestions?q=…` powers the search box in the header.
- All SQL uses parameters, and sort values come from a fixed list, so there's no SQL injection.

Code: `catalog/search/PostgresSearchService.java`, migration `V5`. Tests: `SearchIT`, `InputValidationIT`.
Demo: type `wal` in the header, then try `/catalog?q=walnut` and `/catalog?q=stanton`.

### 17. The product data model includes all required fields: id, name, description, price, stock quantity, category, brand, images, and weight/dimensions (in both metric and imperial units).

Yes, all of them:

| Field | Column |
|---|---|
| id | `id` UUID |
| name, description | `name`, `description` |
| price | `price` NUMERIC(10,2) |
| stock | `stock_quantity` |
| category, brand | `category_id`, `brand_id` (foreign keys) |
| images | `product_images` table |
| weight | `weight_kg` + `weight_lbs` |
| dimensions | `width/height/depth_cm` + `width/height/depth_in` |

Metric is the source of truth: you send kg/cm and the backend calculates the imperial values, so the two can never disagree (`catalog/UnitConversion.java`).

### 18. Products are organized into categories with an intuitive browsing structure.

Yes. Categories form a three-level tree (each category points to its parent). Opening a category shows the products in all of its subcategories too. The home page has category tiles, the catalog's category filter shows the path back up the tree, and a product page's Related row offers its whole top-level category.

Demo: `/` → click a category → narrow it down → open a product. Test: `CatalogBrowseIT`.

### 19. Faceted search is implemented, allowing users to refine results by product attributes (e.g., price range, brand, category).

Yes. You can filter by **category, brand (several at once), price range and minimum rating**, and combine them with a search. Every filter option shows how many products it would return.

The filters are kept in the URL, so reloading or sharing the link keeps them.

Demo: `/catalog` → pick a brand, then a price band. Test: `SearchIT`.

### 20. Product listing includes sorting options for relevance, price and rating.

Yes: featured (the browse default: in stock, then with a photo, then rating), relevance (the search default), price low→high, price high→low, rating, newest. Any other value is rejected.

### 21. Product images are stored with proper file handling and basic serving functionality.

Yes. Admins upload with `POST /products/{id}/images`, up to 5 MB.
- The file type is checked from the file's **actual bytes**, not its name. Only JPEG and PNG are accepted.
- Huge images are rejected before they're opened, so a small file can't expand into gigabytes in memory.
- The image is **re-saved**, which strips metadata and anything hidden inside it.
- It's saved under a **random filename**, so a filename like `../../etc` can't reach anywhere.
- Files live in a Docker volume, and nginx serves them at `/images/...`.

Code: `catalog/ImageProcessor.java`, `storage/LocalStorageService.java`. Tests: `ImageProcessorTest`, `ProductImageIT`, `InputValidationIT`.

### 22. Student can explain their approach to testing, integration of automated and usage of manual tests throughout the development process.

- **Unit tests** for logic: tokens, 2FA, validation, image checks, unit conversion.
- **Integration tests** call the real API against **real Postgres and Redis** in Testcontainers. Not H2, because H2 doesn't support the Postgres features we use (JSONB, full-text search, Lua scripts).
- **Security tests** as their own suite.
- **Frontend tests** for forms, validation and the token refresh logic.
- Each feature was built together with its tests before moving on to the next.
- We test error cases (400/401/403/404/409/429), not just the happy path.
- **Manual testing** for things that need real outside services: Google login, live CAPTCHA, scanning the 2FA QR with a phone, reading emails in MailHog, and clicking through every page.

### 23. Automated tests exist for Unit, API integration, and Security tests covering authentication and product catalog functionality.

Yes, 202 tests, all passing.

| | Authentication | Catalog |
|---|---|---|
| Unit | `JwtServiceTest`, `TokenStoreServiceTest`, `AuthServiceTest`, `TwoFactorServiceTest`, `PasswordResetServiceTest`, `CaptchaServiceTest`, `GoogleTokenVerifierTest`, `UserServiceTest` | `ProductRequestValidationTest`, `UnitConversionTest`, `ImageProcessorTest`, `ReviewServiceTest`, `PostgresSearchServiceTest` |
| API integration | `AuthControllerIT`, `PasswordResetControllerIT`, `TwoFactorControllerIT` | `CatalogBrowseIT`, `ProductAdminIT`, `SearchIT`, `ReviewIT`, `ProductImageIT` |
| Security | `RateLimitIT`, `InputValidationIT` | `InputValidationIT` (27 attack attempts: SQL injection, bad uploads, extra fields) |
| Frontend | `authRules.test.ts`, `LoginPage.test.tsx`, `RegisterPage.test.tsx`, `client.test.ts` | `variants.test.ts` |

### 24. Ask the student to explain and demonstrate the functionality of the tests.

Run `cd backend && mvn verify` and `cd frontend && bunx vitest run`. Good ones to walk through:
1. `AuthControllerIT.refresh_rotatesToken_andReplayedCookieIsRejected`: an old refresh token is rejected.
2. `AuthControllerIT.logout_blocklistsAccessToken_soItStopsWorking`: after logout the token stops working.
3. `ReviewIT`: two reviews posted at the same moment still give the correct average rating.
4. `InputValidationIT`: 27 attacks, and each one gets a clean 4xx that leaks nothing.
5. `RateLimitIT`: an attacker gets locked out while the real user can still log in.
6. `client.test.ts`: two failed requests trigger only one token refresh.

### 25. Student can explain their chosen architectural approach and justify how it aligns with their platform's scalability requirements.

**Modular monolith:** one Spring Boot app, with code grouped by feature (`user`, `auth`, `catalog`) instead of by layer. A feature can only reach another feature through its service, never its database code.
- **Not microservices:** too much overhead (network calls, separate deployments, transactions across services) for a project this size. The feature boundaries are already clean, so one can be split out later if it needs to be.
- **Scales horizontally:** the app keeps no state in memory, and tokens and sessions live in Redis, so you can run many copies behind a load balancer.
- **Easy to swap later:** search sits behind `SearchService` (can become Elasticsearch) and storage behind `StorageService` (can become S3).
- nginx serves the frontend and forwards API calls from the same address, so no CORS setup is needed.

---

## Extra

### 26. Authentication system implementation quality, security measures, and user experience.

On top of the requirements:
- a reused refresh token logs out every session
- refresh and reset tokens are stored hashed
- login takes the same time whether the email exists or not
- emails aren't case-sensitive
- Google login can't take over a password account
- CAPTCHA hostname check
- limited attempts on 2FA codes
- rate limits per IP and per account, designed so the real user can't be locked out for good

UX: errors shown next to each field, buttons disabled while waiting, "try again in N seconds" on rate limits, and you stay logged in after a reload.

### 27. Database design quality, ERD completeness, and adherence to ACID properties.

- 6 Flyway migrations, never edited once committed.
- The database enforces its own rules (NOT NULL, CHECK, UNIQUE, foreign keys), not only the Java code.
- Delete rules chosen per relationship (see question 15).
- The rating trigger is safe when two reviews arrive at once, and a test proves it.
- ACID: see question 14.

### 28. Product catalog organization, search functionality, and filtering system implementation.

- About 1,770 real products imported from 3 suppliers (`tools/catalog-import`).
- Three-level category tree.
- Ranked search with typo fallback and suggestions while typing.
- Filters with live counts.
- Colour and size variants on product pages.

### 29. Project application is containerized using Docker.

Yes. `docker-compose.yml` runs 5 containers: postgres, redis, mailhog, backend and frontend. They have health checks, and the database and images are kept in volumes.

### 30. The project uses Docker to containerize the application and its dependencies. Docker is the only host prerequisite - all other dependencies are managed within containers.

Yes. Java, Maven, Bun and Node only exist inside the Docker build images. The Python scripts in `tools/catalog-import` aren't needed either, because the SQL they produced is committed. With nothing but Docker installed: `./start.sh`. The only exception is running the test suites directly on your machine, which needs Java 21, Maven and Bun.
