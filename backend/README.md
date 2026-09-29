# Backend

The server for i-love-shopping: a Spring Boot 4 application in Java 21. It handles accounts, login
and the product catalog, and stores data in PostgreSQL and Redis.

You don't need to run anything in this folder to use the app, because `./start.sh` in the project
root builds and runs it in Docker. This page explains where things live in the code.

Java code is under `src/main/java/com/iloveshopping/`. Every file starts with a one-line comment
saying what it does.

---

## Folders

The code is grouped by **feature**, not by layer. Each feature folder holds its own controllers,
services, database access and error types.

| Folder | What's in it |
|---|---|
| `user/` | The user account: the `User` entity, its repository, and `UserService` |
| `auth/` | Everything about logging in: register, login, tokens, 2FA, password reset, CAPTCHA, Google login |
| `catalog/` | Products, categories, brands, images and reviews |
| `catalog/search/` | Search, filters and suggestions |
| `ratelimit/` | Request limits per IP address, counted in Redis |
| `storage/` | Where uploaded image files are saved |
| `config/` | Spring setup: security rules, password hashing, the HTTP client, Google key checking |
| `exception/` | One place that turns every error into a clean JSON response |

Inside a feature folder:
- `dto/` holds the request and response shapes. These carry the validation rules.
- `exception/` holds that feature's error types.

One feature never reads another feature's database tables directly. It calls the other feature's
service instead.

---

## Where to find each feature

| Feature | Start here | Also involved |
|---|---|---|
| Register, login, logout, `/auth/me` | `auth/AuthController` | `AuthService`, `user/UserService` |
| Access tokens (JWT) | `auth/JwtService` | `JwtAuthenticationFilter` checks the token on every request |
| Refresh tokens, single use | `auth/TokenStoreService` | The Lua script at the top does the swap in one step |
| Logout blocklist | `auth/TokenStoreService` | `JwtAuthenticationFilter` |
| Two-factor login | `auth/TwoFactorController` | `TwoFactorService` |
| Password reset | `auth/PasswordResetController` | `PasswordResetService`, `EmailService` |
| CAPTCHA | `auth/CaptchaService` | Called from `AuthController` during registration |
| Google login | `auth/GoogleTokenVerifier` | `config/GoogleOAuthConfig` |
| Login attempt limits | `auth/AccountThrottle` | `ratelimit/RateLimiter` |
| Per-IP limits | `ratelimit/RateLimitFilter` | The list of limited endpoints is at the top of the file |
| Which endpoints need login or admin | `config/SecurityConfig` | |
| Error responses | `exception/GlobalExceptionHandler` | |
| Product browse and detail, admin edits | `catalog/ProductController` | `ProductService` |
| Search, filters, sorting, suggestions | `catalog/search/PostgresSearchService` | `SearchService` is the interface callers use |
| Categories and brands | `catalog/CategoryController`, `BrandController` | |
| Reviews | `catalog/ReviewController` | `ReviewService`. The average rating is kept up to date by a database trigger (migration V6) |
| Image upload | `catalog/ProductImageController` | `ImageProcessor` checks the file, `storage/LocalStorageService` saves it |
| Metric to imperial | `catalog/UnitConversion` | |

---

## How a request travels

```
browser :5173  -->  nginx (frontend container)
                      |-- /                    the website
                      |-- /images/...          product images from the shared volume
                      '-- /auth/..., /products/...  passed on to the backend :8080
                                                      |
                                        postgres    redis    mailhog
```

The browser only ever talks to nginx. nginx serves the website and forwards API calls to the
backend from the same address, so there is **no CORS setup anywhere**, and none is needed. The
rules are in `frontend/nginx.conf`. The backend port 8080 is open on this machine only, for `curl`.

---

## Where data is stored

| Store | Holds |
|---|---|
| PostgreSQL | Users, products, categories, brands, image records, reviews |
| Redis | Short-lived login data, all of which expires on its own (see below) |
| Docker volume `product_images_data` | Uploaded image files. The backend writes them, nginx serves them |

Redis keys:

| Key | Holds | Expires |
|---|---|---|
| `refresh_token:{hash}` | The user a refresh token belongs to | 7 days |
| `refresh_tokens_user:{userId}` | All of a user's refresh tokens, so a password reset can end every session | with the tokens |
| `used_refresh_token:{hash}` | Tokens already used. Reusing one logs the user out everywhere | 7 days |
| `token_blocklist:{jti}` | Access tokens that were logged out | when the token would expire |
| `2fa_pending:{hash}`, `2fa_attempts:{hash}` | A login waiting for its 2FA code, and how many codes were tried | 5 minutes |
| `pwreset:{hash}` | A password reset link | 15 minutes |
| `rate_limit:...` | Request counters for rate limiting | per limit |

Tokens are stored as SHA-256 hashes, never as the token itself (`auth/OpaqueTokens`).

---

## Database

The schema is built from SQL files in `src/main/resources/db/migration/`, which **Flyway** runs on
startup.

| File | Does |
|---|---|
| `V1__initial_schema.sql` | All tables, keys and constraints |
| `V2`, `V3`, `V4` | Small fixes: Google users without a password, room for backup codes, emails that ignore upper/lower case |
| `V5__catalog_search_indexes.sql` | The search index and the other indexes that keep queries fast |
| `V6__product_rating_trigger.sql` | Keeps each product's average rating correct when reviews change |

A migration file is never edited once it's committed. Changes go in a new file (`V7__...`).

`src/main/resources/db/seed/R__seed_catalog.sql` fills the shop with the demo products. It's
generated by the Python scripts in `tools/catalog-import`, so don't edit it by hand.

Settings are in `src/main/resources/application.yml`. Every value comes from an environment
variable, and those are listed with placeholders in `.env.example` in the project root.

---

## Tests

```sh
mvn verify     # all tests: unit + integration (Docker must be running)
mvn test       # unit tests only, fast, no Docker needed
```

Tests are in `src/test/java/com/iloveshopping/`, in the same folders as the code they test.

| Kind | File names | What they do |
|---|---|---|
| Unit | `*Test.java` | Test one class on its own, with its helpers faked (Mockito). Fast. |
| Integration | `*IT.java` | Start the whole app against a real PostgreSQL and Redis, which Testcontainers runs in Docker, and call the API like a client would |
| Security | `security/*IT.java` | Attack attempts: SQL injection, oversized input, malicious uploads, rate limit bypass |

Shared test helpers are in `support/`:
- `AbstractIntegrationTest` is the base class for every `*IT`. It starts one PostgreSQL and one
  Redis for the whole run.
- `CatalogTestSupport` creates users, admins and products for catalog tests.
- `TestImages` builds real and deliberately broken image files.

The tests use a real PostgreSQL rather than an in-memory database like H2, because the app relies on
PostgreSQL-only features (JSONB, full-text search, triggers) that H2 doesn't have.

**Finding the tests for a feature:** the test is named after the class, e.g. `TwoFactorService` →
`TwoFactorServiceTest`, and `TwoFactorController` → `TwoFactorControllerIT`. For which tests cover
which review checklist item, see [../REVIEW_ANSWERS.md](../REVIEW_ANSWERS.md).
