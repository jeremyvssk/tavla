# Folder Structure

Actual layout as of 2026-09-29. Entries marked *(planned)* do not exist yet — everything else is
real. Keep this file honest; a structure doc that lists imaginary files is worse than none.

```
i-love-shopping1/
├── CLAUDE.md
├── README.md                    # for hiring managers: status, roadmap, what works, setup, ERD
├── start.sh                     # one-command startup
├── docker-compose.yml           # postgres, redis, mailhog, backend, frontend
├── .env.example                 # every variable, placeholders only
│
├── docs/
│   ├── ASSIGNMENT.md            # the brief, verbatim
│   ├── USAGE.md                 # how to try each feature (moved out of README)
│   └── i-love-shopping-erd.png
├── claude-docs/                 # design + build notes (this file, BUILD_ORDER, validation)
├── homework/                    # study material and learning plan
├── tools/catalog-import/        # supplier scrapers + build_seed.py, which generates
│                                # db/seed/R__seed_catalog.sql (raw scrapes in data/, gitignored)
├── .claude/skills/              # see .claude/skills/README.md
│
├── backend/
│   ├── CLAUDE.md                # always-on backend invariants
│   ├── README.md                # human guide: feature → file map, request flow, Redis keys, tests
│   ├── Dockerfile               # multi-stage: maven build → jre-alpine runtime
│   ├── pom.xml                  # Spring Boot 4.0.6, Java 21
│   └── src/main/java/com/iloveshopping/
│       ├── IloveshoppingApplication.java
│       ├── config/              # SecurityConfig, PasswordConfig, RestClientConfig,
│       │                        # GoogleOAuthConfig
│       ├── exception/           # GlobalExceptionHandler, ErrorResponse
│       ├── user/                # User, UserRepository, UserService, Role, AuthProvider,
│       │   └── exception/       # OAuthUserInfo
│       ├── auth/                # AuthController, AuthService, JwtService, TokenStoreService,
│       │   ├── dto/             # JwtAuthenticationFilter, TwoFactorService/Controller,
│       │   └── exception/       # PasswordResetService/Controller, CaptchaService,
│       │                        # EmailService, GoogleTokenVerifier, AccountThrottle,
│       │                        # OpaqueTokens
│       ├── catalog/             # Product, Category, Brand, ProductImage, ProductReview + repos,
│       │   ├── dto/             # services and controllers; ImageProcessor, UnitConversion
│       │   ├── exception/
│       │   └── search/          # SearchService, PostgresSearchService, SuggestionController
│       ├── ratelimit/           # RateLimiter (Redis Lua), RateLimitFilter (per-IP rules)
│       └── storage/             # StorageService, LocalStorageService
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/        # V1__initial_schema … V4__case_insensitive_email_unique,
│       │                        # V5__catalog_search_indexes, V6__product_rating_trigger
│       └── db/seed/             # R__seed_catalog (repeatable demo catalog, generated)
│   └── src/test/java/com/iloveshopping/
│       ├── auth/                # JwtServiceTest, TokenStoreServiceTest, AuthServiceTest,
│       │                        # TwoFactorServiceTest,
│       │                        # PasswordResetServiceTest, CaptchaServiceTest,
│       │                        # GoogleTokenVerifierTest, AuthControllerIT,
│       │                        # PasswordResetControllerIT, TwoFactorControllerIT
│       ├── user/                # UserServiceTest
│       ├── catalog/             # CatalogBrowseIT, ProductAdminIT, SearchIT, ReviewIT,
│       │   │                    # ProductImageIT + unit tests
│       │   └── search/          # PostgresSearchServiceTest
│       ├── security/            # InputValidationIT, RateLimitIT
│       └── support/             # AbstractIntegrationTest, CatalogTestSupport, TestImages
│
└── frontend/
    ├── README.md                # human guide: folders, pages, session handling, tests
    ├── Dockerfile               # multi-stage: bun build → nginx serve
    ├── nginx.conf               # SPA fallback, API proxy, /images serving
    ├── package.json, bun.lock
    ├── vite.config.ts           # Vite + Vitest + dev proxy mirroring nginx
    └── src/
        ├── main.tsx             # providers: Redux, React Query, router
        ├── App.tsx              # routes + session restore
        ├── index.css            # the Tavla theme tokens + all component styles
        ├── api/                 # client.ts (axios, in-memory token, refresh), auth.ts,
        │                        # catalog.ts, errors.ts
        ├── auth/session.ts      # restore / start / end session
        ├── store/               # Redux store, authSlice (user, never the token),
        │                        # cartSlice (saved to localStorage) + test
        ├── validation/          # authRules.ts: client copies of the DTO constraints
        ├── pages/               # Home, Catalog, Product, Login, Register, ForgotPassword,
        │                        # ResetPassword, Account (+ *.test.tsx)
        ├── components/          # Layout, SearchBox, Field, RequireAuth, ProductCard,
        │                        # ProductRow, CartPanel, Icon, Stars, GoogleButton, Recaptcha
        ├── hooks/, lib/         # useDebouncedValue; loadScript, format, variants (+ test)
        └── test/                # Vitest setup, renderWithProviders
```

**Conventions**

- Each backend domain keeps its exceptions in a `<domain>/exception/` subfolder, so the domain
  root stays focused on entity/repo/service.
- There is **no `CorsConfig`, and there should not be one.** nginx serves the SPA and proxies the
  API on a single origin, so no request is ever cross-origin. See
  [../backend/README.md](../backend/README.md#how-a-request-travels).
- Adding a new top-level backend path prefix means editing the proxy alternation in *both*
  `frontend/nginx.conf` and `frontend/vite.config.ts`.
