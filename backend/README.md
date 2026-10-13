# Backend

The server for i-love-shopping: a Spring Boot 4 application in Java 21. It handles accounts, login,
the product catalog, the cart, orders and Stripe payments, and stores data in PostgreSQL and Redis.
Stripe's payment events and the order emails go through RabbitMQ queues.

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
| `user/` | The user account: the `User` entity, `UserService`, the shopper's own account (`/users/me`, saved addresses) and the staff Users page |
| `auth/` | Everything about logging in: register, login, tokens, 2FA, password reset, CAPTCHA, Google login |
| `catalog/` | Products, categories, brands, images and reviews |
| `catalog/search/` | Search, filters, suggestions, and the products that go with a cart |
| `catalog/imports/` | The bulk upload: reading a CSV or JSON file, checking every row, importing all or nothing |
| `audit/` | The audit log of staff changes and sign-ins, and its 12-month clean-up |
| `delivery/` | Delivery options and the free-shipping limit: staff edit them, the checkout prices orders with them |
| `overview/` | The staff overview page, gathered from the other features' services |
| `support/` | The contact form: emailed to the shop's inbox through a queue |
| `csv/` | Writing CSV files safely (formula cells defused) and reading uploaded ones |
| `cart/` | The cart on the server, for guests and for accounts, and the merge at sign-in |
| `order/` | Orders: placing, listing, cancelling, the statuses and the moves between them, the payment hold, the queue consumers and the emails |
| `payment/` | Stripe: starting a payment, refunds, the webhook and publishing its events, and the `payments` table |
| `crypto/` | Encryption at rest: `FieldCipher` (AES-256-GCM), the `Encrypted` JPA converter, and `BlindIndex` (the keyed email hash) |
| `ratelimit/` | Request limits per IP address, counted in Redis |
| `storage/` | Where uploaded image files are saved |
| `config/` | Spring setup: security rules, password hashing, the HTTP client, Google key checking, scheduled jobs, the RabbitMQ queues |
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
| Per-IP limits | `ratelimit/RateLimitFilter` | The list of limited endpoints is at the top of the file; every other path shares one general bucket (300 per 15 s). The Stripe webhook is never limited |
| Token buckets | `ratelimit/RateLimiter` | One Lua script reads, refills, takes and writes; Redis's own clock. `app.rate-limit.multiplier` raises every limit, for the load test only |
| Which endpoints need login or admin | `config/SecurityConfig` | `auth/StaffArea`: which staff role may use which `/staff` area, and the second factor every area needs |
| Staff roles and the users page | `user/StaffUserController` | `StaffUserService`: a role change needs the admin's own 2FA code; `TokenStoreService.revokeAccessTokens` ends the person's sessions |
| Audit log | `audit/AuditLog` | `AuditEvent` is what a change writes; `AuditController` reads and exports; the `audit_log_append_only` trigger (V11) refuses edits |
| Staff product list, bulk changes, export | `catalog/StaffCatalogController` | `StaffProductService`; `PriceGuard` holds Sales to ±50% a change |
| Archive or delete a product | `catalog/ProductService.delete` | `ProductUsage`, answered by `order/OrderedProducts`, says whether it was ever ordered |
| Category edit, move, delete | `catalog/CategoryController` | `CategoryService.update`, `move`, `delete` |
| Bulk upload | `catalog/imports/ImportController` | `ImportFileReader` (file), `ImportChecker` (rows), `ImportRunner` (the one transaction), `csv/CsvReader` |
| Delivery options | `delivery/DeliveryController` | `DeliveryService.quote` prices both the checkout's list and the order |
| Staff orders: queue, moves, notes, refunds | `order/StaffOrderController` | `StaffOrderService`; part refunds in `Refund`, retried by `RefundRetry` |
| Review votes, reports, the summary | `catalog/ReviewController` | `ReviewService`; `PurchaseHistory` (answered by `order/OrderedProducts`) says whether the product was delivered |
| Review moderation | `catalog/StaffReviewController` | `StaffReviewService` |
| Staff overview | `overview/OverviewController` | `OverviewService` |
| The shopper's account and addresses | `user/AccountController` | `AccountService` (profile, export, delete), `AddressService` |
| Contact form | `support/ContactController` | `ContactService` queues it; `ContactMailListener` emails the inbox |
| Error responses | `exception/GlobalExceptionHandler` | |
| Product browse and detail, admin edits | `catalog/ProductController` | `ProductService` |
| Search, filters, sorting, suggestions | `catalog/search/PostgresSearchService` | `SearchService` is the interface callers use. Two-letter suggestions are kept in memory for 30 s (they can't use the trigram index; the load test found them) |
| Categories and brands | `catalog/CategoryController`, `BrandController` | |
| Reviews | `catalog/ReviewController` | `ReviewService`. The average rating is kept up to date by a database trigger (migration V6) |
| Image upload | `catalog/ProductImageController` | `ImageProcessor` checks the file and makes the 160, 600 and 1200 px copies (`WIDTHS`); `storage/LocalStorageService` saves them as `{name}-{width}.{ext}`; nginx falls back to the original for a size that doesn't exist |
| Metric to imperial | `catalog/UnitConversion` | |
| Cart: read, add, change a quantity, remove | `cart/CartController` | `CartService`. Prices and stock come from `catalog/ProductService.forCart` on every read |
| Guest carts | `cart/CartController` | A random token in the `cart_token` cookie (HttpOnly, SameSite=Strict); the database keeps only its hash |
| Stock checks when adding | `cart/CartItemRepository.addUpTo` | One SQL statement adds only while the line stays within the stock and the limit of 10 |
| "Goes with your order" | `cart/CartService.recommendations` | `catalog/search/PostgresSearchService.complements` |
| Old guest carts | `cart/GuestCartCleanup` | Runs hourly, deletes guest carts untouched for 30 days (`CART_GUEST_TTL`) |
| Merging the guest cart at sign-in | `cart/CartController.merge` | `CartService.mergeGuestCart`, `CartItemRepository.mergeInto` (the larger quantity wins). The website calls it from `auth/session.ts` |
| Placing an order | `order/OrderController` | `OrderService.place`: reserve stock, copy the cart, start the Stripe payment. `CheckoutRules` checks the postcode per country, and against the city for the biggest cities |
| Order statuses and who may change them | `order/OrderStatus` | `Order.moveTo` is the only method that changes a status; the table is in [claude-docs/ORDER_STATES.md](../claude-docs/ORDER_STATES.md) |
| Not selling the last item twice | `catalog/ProductRepository.reserveStock` | One conditional `UPDATE`; `CHECK (stock_quantity >= 0)` underneath |
| Order totals, shipping and VAT | `order/OrderTotals` | `delivery/DeliveryService.quote` gives the shipping cost; VAT is the 24% inside the total |
| Starting a Stripe payment | `payment/PaymentService.start` | `StripePaymentGateway` calls Stripe; tests replace the `PaymentGateway` interface |
| Stripe's webhook | `payment/PaymentWebhookController` | `PaymentService.parseWebhook` checks the signature; `PaymentEventPublisher` puts it on the queue and waits for RabbitMQ's confirm |
| The queues and dead letter queues | `config/MessagingConfig` | Retries in `application.yml` (`spring.rabbitmq.listener.simple.retry`); after the last one a message moves to its `.dlq` |
| Applying a payment event | `order/PaymentEventListener` | `OrderService.applyPaymentEvent`: once per Stripe event id, in one transaction with the order change |
| Order emails | `order/OrderNotifier` | Queues the email after the commit; `OrderEmailListener` consumes it; `OrderMail` writes and sends it |
| The account's order list | `order/OrderController.list` | `OrderRepository.findPageForUser`: filters and sort in SQL, the sort as a bound parameter |
| Cancelling, and the refund | `order/OrderController.cancel` | `OrderService.cancel` then `requestRefund`; `StripePaymentGateway.refund`; `RefundRetry` asks again when Stripe was down |
| Checkout prefill | `order/OrderController.lastDelivery` | The phone and address of the account's latest order |
| Encryption at rest | `crypto/FieldCipher` | `@Convert(converter = Encrypted.class)` on the fields of `Order`, `OrderLine`, `Payment`, `User`, `UserAddress`; the key is `APP_ENCRYPTION_KEY` |
| Finding a user by an encrypted email | `crypto/BlindIndex` | `users.email_hash` = HMAC-SHA256 of the lower-cased email, key derived from `APP_ENCRYPTION_KEY`; `UserService.findByEmail` and the unique index use it |
| Encrypting rows saved in plain text | `user/UserEncryptionBackfill` | A Flyway callback after every migrate: old users and the seed's reviewers |
| A payment gateway for the load test | `payment/FakePaymentGateway` | Only when `PAYMENTS_FAKE=true` (the load test's stack); logs a warning at start. Otherwise `StripePaymentGateway` |
| Paying again after a decline | `order/OrderController.startPayment` | Same PaymentIntent, so one order is never two payments |
| Unpaid orders running out of time | `order/OrderExpiry` | Every minute: asks Stripe first, then cancels the payment and puts the stock and cart lines back |

---

## How a request travels

```
browser :5443 (https) -->  nginx (frontend container)       :5173 (http) only redirects to https
                      |-- /                    the website
                      |-- /images/...          product images from the shared volume
                      '-- /auth/..., /products/..., /cart/..., /orders/...  passed on to the backend :8080
                                                      |
                                  postgres    redis    rabbitmq    mailhog    Stripe API
                                                                                  |
stripe-cli container  -- forwards Stripe's test events -->  backend :8080/payments/webhook

webhook --> [payments] --> orders.payment-events --> PaymentEventListener --> order, stock, history (one transaction)
                                  '-- after 3 failed tries --> orders.payment-events.dlq
after commit --> [orders] --> orders.emails --> OrderEmailListener --> MailHog
                                  '-- after 3 failed tries --> orders.emails.dlq
```

The browser only ever talks to nginx. nginx serves the website and forwards API calls to the
backend from the same address, so there is **no CORS setup anywhere**, and none is needed. The
rules are in `frontend/nginx.conf`. The backend port 8080 is open on this machine only, for `curl`.
nginx serves HTTPS with a self-signed certificate it makes on first start
(`frontend/15-self-signed-cert.sh`), kept in the `tls_data` volume.

---

## Where data is stored

| Store | Holds |
|---|---|
| PostgreSQL | Users, products, categories, brands, image records, reviews, carts, orders, payments. The personal and payment columns of orders, order items and payments are encrypted (see below) |
| Stripe | The card details themselves, and the payment. We keep only Stripe's payment id, the card brand and the last 4 digits, encrypted |
| RabbitMQ | Messages on their way: Stripe events for the order side, and "send this email". Durable, so a restart loses none. Ids and outcomes only, no personal data |
| Redis | Short-lived login data, all of which expires on its own (see below) |
| Docker volume `product_images_data` | Uploaded image files. The backend writes them, nginx serves them |

Redis keys:

| Key | Holds | Expires |
|---|---|---|
| `refresh_token:{hash}` | The user a refresh token belongs to | 7 days |
| `refresh_tokens_user:{userId}` | All of a user's refresh tokens, so a password reset can end every session | with the tokens |
| `used_refresh_token:{hash}` | Tokens already used. Reusing one logs the user out everywhere | 7 days |
| `token_blocklist:{jti}` | Access tokens that were logged out | when the token would expire |
| `tokens_revoked_before:{userId}` | A time: every access token of that user issued before it is refused (role changed, account deleted) | after an access token's lifetime |
| `rate_bucket:step_up:{userId}` | 2FA codes typed to approve a role change, or passwords typed to delete the account | 15 minutes |
| `2fa_pending:{hash}`, `2fa_attempts:{hash}` | A login waiting for its 2FA code, and how many codes were tried | 5 minutes |
| `pwreset:{hash}` | A password reset link | 15 minutes |
| `rate_bucket:{ip:…, login:…, reset_email:…}` | A token bucket: tokens left and when they were counted | once it would be full again |

Tokens are stored as SHA-256 hashes, never as the token itself (`auth/OpaqueTokens`). The guest
cart token is too, in the `carts` table.

Encrypted columns: `orders` email, phone, first_name, last_name, street, flat, postcode, city;
`order_items` product_name, options, image_url; `payments` provider_payment_id, card_brand, card_last4,
failure_code; and since Project 3 `orders` billing_* and tracking_number, `users` phone, every personal
column of `user_addresses`, and (V16) `users` email, full_name, avatar_url, two_factor_secret. Stored as `v1:` + base64(IV, ciphertext, GCM tag). Ids, statuses, dates, money and the
country stay plain: queries filter and sort on them, and ciphertext can't be searched.

RabbitMQ queues (`config/MessagingConfig`):

| Queue | Message | Consumer | Dead letters go to |
|---|---|---|---|
| `orders.payment-events` | `PaymentEvent`: Stripe event id, kind, order id, time, decline code | `order/PaymentEventListener` | `orders.payment-events.dlq` |
| `orders.emails` | `OrderNotice`: order id and which email | `order/OrderEmailListener` | `orders.emails.dlq` |
| `support.contact-messages` | `ContactMessage`: the contact form itself (the shop keeps no other copy) | `support/ContactMailListener` | `support.contact-messages.dlq` |

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
| `V7__product_image_framing.sql` | Stores how each photo sits on its tile (see `tools/catalog-import/frame_photos.py`) |
| `V8__carts.sql` | `carts` (one per user, or per guest token) and `cart_items` (product and quantity, at most 10) |
| `V9__orders_and_payments.sql` | `orders`, `order_items`, `order_status_history`, `payments`, `processed_payment_events`, and a check that stock never goes below zero |
| `V10__encrypt_order_and_payment_columns.sql` | Makes the encrypted columns `TEXT`, and adds the refund statuses to `payments` |
| `V11__staff_roles_and_audit_log.sql` | The Support and Sales roles, `last_sign_in_at`, and the append-only `audit_log` |
| `V12__product_sku_archive_and_imports.sql` | Product SKU and archiving, category order, and `import_jobs` |
| `V13__delivery_options_refunds_and_order_notes.sql` | `delivery_options`, `shop_settings`, the delivery copy and tracking on orders, `order_notes`, `refunds` |
| `V14__review_moderation_votes_and_reports.sql` | Review status, `review_votes`, `review_reports`; the rating trigger leaves hidden reviews out |
| `V15__account_profile_addresses_and_billing.sql` | `users.phone`, `user_addresses`, billing on orders; orders outlive a deleted account |
| `V16__encrypt_user_personal_data.sql` | `users` email, name, avatar and 2FA secret become `TEXT` for ciphertext; `email_hash` with its unique index replaces the email's. `UserEncryptionBackfill` then encrypts the rows |

A migration file is never edited once it's committed. Changes go in a new file (`V17__...`).

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
| Integration | `*IT.java` | Start the whole app against a real PostgreSQL, Redis and RabbitMQ, which Testcontainers runs in Docker, and call the API like a client would |
| Security | `security/*IT.java` | Attack attempts: SQL injection, oversized input, malicious uploads, rate limit bypass, token bucket bursts and refills (`TokenBucketIT`) |

Shared test helpers are in `support/`:
- `AbstractIntegrationTest` is the base class for every `*IT`. It starts one PostgreSQL, one Redis
  and one RabbitMQ for the whole run, and turns the queue consumers off.
- `CatalogTestSupport` creates users, admins and products for catalog and cart tests.
- `OrderTestSupport` is the base for every order test. It replaces Stripe (`PaymentGateway`) and the
  mail server with mocks, but nothing between them: each test event is signed the way Stripe signs it,
  goes through the real signature check, onto the real queue, to the real consumer. Only this context
  runs the consumers, so another test class's cached context can't take its messages. `deliver()`
  waits until the consumer has applied an event.
- `TestImages` builds real and deliberately broken image files.

The order tests: `OrderIT` (placing, ownership, the race for the last item, a double-clicked Pay,
webhooks, the hold), `OrderCancelIT` (the cancel rule, the refund, Stripe down, a double-clicked
Cancel), `OrderListIT` (filters, sorts, paging, prefill), `MessagingIT` (the emails, a poison message
and a dead mail server ending in their dead letter queues, no personal data on the queue),
`EncryptionIT` (ciphertext in the tables, plain text in the API, tampering detected). Unit tests:
`CartServiceTest`, `OrderTotalsTest`, `OrderStatusTest`, `CheckoutRulesTest`, `OrderMailTest`,
`FieldCipherTest`, `PaymentEventPublisherTest`, `PaymentFailuresTest`.

The tests use a real PostgreSQL rather than an in-memory database like H2, because the app relies on
PostgreSQL-only features (JSONB, full-text search, triggers) that H2 doesn't have.

Project 3's tests: `StaffAccessIT` (every role against every area), `StaffUserIT`, `AuditLogIT`,
`StaffCatalogIT`, `CategoryAdminIT`, `ImportIT`, `DeliveryIT`, `StaffOrderIT`, `ReviewModerationIT`,
`OverviewIT`, `AccountIT`, `UserEncryptionIT` (ciphertext in `users`, sign-in by the blind index, the backfill), `TokenBucketIT`, `RateLimitIT`, `ProductImageIT` (the sizes); unit tests `ImageProcessorTest`, `CsvTest`, `CsvReaderTest`, `PriceGuardTest`, `DeliveryOptionTest`.

**Finding the tests for a feature:** the test is named after the class, e.g. `TwoFactorService` →
`TwoFactorServiceTest`, and `TwoFactorController` → `TwoFactorControllerIT`. For which tests cover
which review checklist item, see [../REVIEW_ANSWERS.md](../REVIEW_ANSWERS.md).
