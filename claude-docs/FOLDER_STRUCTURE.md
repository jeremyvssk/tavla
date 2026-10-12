# Folder Structure

Actual layout as of 2026-10-08. Entries marked *(planned)* do not exist yet — everything else is
real. Keep this file honest; a structure doc that lists imaginary files is worse than none.

```
i-love-shopping1/
├── CLAUDE.md
├── README.md                    # graded deliverable: overview, ERD, setup, usage, what works
├── REVIEW_ANSWERS.md            # one answer per review-checklist item, with code + test refs
├── start.sh                     # one-command startup
├── docker-compose.yml           # postgres, redis, rabbitmq, mailhog, backend, frontend, stripe-cli
├── rabbitmq/enabled_plugins     # management page + shovel ("Move messages" out of a dead letter queue)
├── .env.example                 # every variable, placeholders only
│
├── docs/
│   ├── ASSIGNMENT.md            # the Project 1 brief, verbatim
│   ├── ASSIGNMENT2.md           # the Project 2 brief and review checklist
│   └── i-love-shopping-erd.png
├── claude-docs/                 # design + build notes (this file, BUILD_ORDER, validation, ORDER_STATES)
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
│       │                        # GoogleOAuthConfig, SchedulingConfig, MessagingConfig (queues + DLQs)
│       ├── crypto/              # FieldCipher (AES-256-GCM), Encrypted (JPA converter)
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
│       ├── cart/                # Cart, CartItem + repos, CartOwner, CartService,
│       │   ├── dto/             # CartController (incl. the sign-in merge), GuestCartCleanup
│       │   └── exception/
│       ├── order/               # Order, OrderLine, OrderStatusChange + repo, OrderStatus
│       │   ├── dto/             # (the state machine), OrderActor, ShippingMethod, OrderTotals,
│       │   └── exception/       # CheckoutRules, OrderService, OrderController, OrderExpiry,
│       │                        # RefundRetry, PaymentEventListener, OrderNotice, OrderNotifier,
│       │                        # OrderEmailListener, OrderMail
│       ├── payment/             # PaymentGateway + StripePaymentGateway, Payment,
│       │   ├── dto/             # ProcessedPaymentEvent + repos, PaymentService, PaymentEvent,
│       │   └── exception/       # PaymentFailures, PaymentWebhookController, PaymentEventPublisher
│       ├── ratelimit/           # RateLimiter (Redis Lua), RateLimitFilter (per-IP rules)
│       └── storage/             # StorageService, LocalStorageService
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/        # V1__initial_schema … V4__case_insensitive_email_unique,
│       │                        # V5__catalog_search_indexes, V6__product_rating_trigger,
│       │                        # V7__product_image_framing, V8__carts,
│       │                        # V9__orders_and_payments, V10__encrypt_order_and_payment_columns
│       └── db/seed/             # R__seed_catalog (repeatable demo catalog, generated)
│   └── src/test/java/com/iloveshopping/
│       ├── auth/                # JwtServiceTest, TokenStoreServiceTest, AuthServiceTest,
│       │                        # TwoFactorServiceTest,
│       │                        # PasswordResetServiceTest, CaptchaServiceTest,
│       │                        # GoogleTokenVerifierTest, AuthControllerIT,
│       │                        # PasswordResetControllerIT, TwoFactorControllerIT
│       ├── cart/                # CartIT, CartServiceTest
│       ├── crypto/              # FieldCipherTest
│       ├── order/               # OrderIT, OrderCancelIT, OrderListIT, MessagingIT, EncryptionIT,
│       │                        # OrderTotalsTest, OrderStatusTest, CheckoutRulesTest, OrderMailTest
│       ├── payment/             # PaymentFailuresTest, PaymentEventPublisherTest
│       ├── user/                # UserServiceTest
│       ├── catalog/             # CatalogBrowseIT, ProductAdminIT, SearchIT, ReviewIT,
│       │   │                    # ProductImageIT + unit tests
│       │   └── search/          # PostgresSearchServiceTest
│       ├── security/            # InputValidationIT, RateLimitIT
│       └── support/             # AbstractIntegrationTest, CatalogTestSupport, OrderTestSupport,
│                                # TestImages
│
└── frontend/
    ├── README.md                # human guide: folders, pages, session handling, tests
    ├── Dockerfile               # multi-stage: bun build → nginx serve (HTTPS)
    ├── nginx.conf               # template: http → https redirect, TLS, SPA fallback, API proxy, /images
    ├── 15-self-signed-cert.sh   # makes the local TLS certificate on the container's first start
    ├── package.json, bun.lock
    ├── vite.config.ts           # Vite + Vitest + dev proxy mirroring nginx
    ├── scripts/                 # layout-check.mjs (+ layout-measure.mjs): lists misaligned layout
    │                            # on the running site, per screen width (`bun run layout-check`)
    └── src/
        ├── main.tsx             # providers: Redux, React Query, router
        ├── App.tsx              # routes + session restore
        ├── index.css            # the Tavla theme tokens + all component styles
        ├── p3.css               # Project 3 screens, ported from the design canvas
        ├── api/                 # client.ts (axios, in-memory token, refresh), auth.ts,
        │                        # catalog.ts, cart.ts, orders.ts, errors.ts; account.ts,
        │                        # support.ts, admin.ts (mostly PLANNED endpoints)
        ├── auth/session.ts      # restore / start (merges the guest cart) / end session (+ test)
        ├── store/               # Redux store, authSlice (user, never the token),
        │                        # cartSlice (whether the cart panel is open, what was just added)
        ├── validation/          # authRules.ts: client copies of the DTO constraints;
        │                        # checkoutRules.ts (+ tests), contactRules.ts, addressRules.ts
        ├── pages/               # Home, Catalog, Product, Login, Register, ForgotPassword,
        │                        # ResetPassword, Account, Addresses, Security, Checkout,
        │                        # OrderStatus, Orders, Order, Cart, Contact, Faq, About,
        │                        # Legal, NotFound (+ *.test.tsx)
        ├── admin/               # the staff area under /admin, a lazy chunk: AdminApp (routes),
        │                        # AdminLayout, TwoFactorGate, access.ts (role → section),
        │                        # parts.tsx (shared bits), pages/ (Overview, Orders, Order,
        │                        # PackingSlips, Delivery, Products, ProductEdit, Categories,
        │                        # Import, Reviews, Users, Audit)
        ├── content/             # FAQ and legal texts
        ├── components/          # Layout, SearchBox, MiniCart, Field, RequireAuth, ProductCard,
        │                        # ProductRow, ProductListRow, CartPanel (+ test), CardPayment
        │                        # (Stripe's form), PayMarks, Reviews, ReviewModal,
        │                        # TwoFactorPanel, OrderParts, Icon, Stars, GoogleButton, Recaptcha
        ├── hooks/, lib/         # useDebouncedValue, useCart, useMediaQuery, usePageMeta,
        │                        # useActiveSection; loadScript, format, variants, shipping,
        │                        # orderStatus, stripe, completions (+ tests)
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
