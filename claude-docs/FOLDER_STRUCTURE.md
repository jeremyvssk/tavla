# Folder Structure

Actual layout as of 2026-09-09. Entries marked *(planned)* do not exist yet — everything else is
real. Keep this file honest; a structure doc that lists imaginary files is worse than none.

```
i-love-shopping1/
├── CLAUDE.md
├── README.md                    # graded deliverable: overview, ERD, setup, usage, status
├── start.sh                     # one-command startup
├── docker-compose.yml           # postgres, redis, mailhog, backend, frontend
├── .env.example                 # every variable, placeholders only
│
├── docs/
│   ├── ASSIGNMENT.md            # the brief, verbatim
│   └── i-love-shopping-erd.png
├── claude-docs/                 # design + build notes (this file, BUILD_ORDER, validation)
├── homework/                    # study material and learning plan
├── .claude/skills/              # see .claude/skills/README.md
│
├── backend/
│   ├── CLAUDE.md                # always-on backend invariants
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
│       │                        # EmailService, GoogleTokenVerifier
│       └── catalog/             # (planned) BUILD_ORDER #8–9
│           ├── SearchService.java / PostgresSearchService.java   (planned)
│           ├── dto/ exception/                                   (planned)
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/        # V1__initial_schema, V2__oauth_nullable_password,
│                                # V3__widen_two_factor_backup_codes
│   └── src/test/java/com/iloveshopping/
│       ├── auth/                # JwtServiceTest, TokenStoreServiceTest, TwoFactorServiceTest,
│       │                        # PasswordResetServiceTest, CaptchaServiceTest,
│       │                        # GoogleTokenVerifierTest, AuthControllerIT,
│       │                        # PasswordResetControllerIT, TwoFactorControllerIT
│       ├── user/                # UserServiceTest
│       ├── catalog/             # (planned)
│       └── security/            # (planned) InputValidationTest — BUILD_ORDER #11
│
└── frontend/
    ├── Dockerfile               # multi-stage: bun build → nginx serve
    ├── nginx.conf               # SPA fallback, API proxy, /images serving
    ├── package.json, bun.lock
    ├── vite.config.ts           # Vite + Vitest + dev proxy mirroring nginx
    └── src/
        ├── main.tsx, App.tsx, index.css
        ├── pages/               # HomePage; the rest planned
        ├── features/auth/       # (planned) LoginForm, RegisterForm, authSlice, authService
        ├── features/catalog/    # (planned) ProductCard, SearchBar, FacetPanel
        └── shared/              # (planned) lib/axios.ts, lib/queryClient.ts, components/ui/
```

**Conventions**

- Each backend domain keeps its exceptions in a `<domain>/exception/` subfolder, so the domain
  root stays focused on entity/repo/service.
- There is **no `CorsConfig`, and there should not be one.** nginx serves the SPA and proxies the
  API on a single origin, so no request is ever cross-origin. See
  [../README.md](../README.md#request-flow).
- Adding a new top-level backend path prefix means editing the proxy alternation in *both*
  `frontend/nginx.conf` and `frontend/vite.config.ts`.
