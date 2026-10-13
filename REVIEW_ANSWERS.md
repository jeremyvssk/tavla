# Review Answers

Project 3's answers come first; Project 2's follow, unchanged.

Start the app: `./start.sh` → shop at <https://localhost:5443> (accept the self-signed certificate once;
<http://localhost:5173> redirects there), emails at <http://localhost:8025> (MailHog), queues at
<http://localhost:15672> (RabbitMQ, `app` / `changeme`). Card payments need free Stripe test keys in
`.env`: [README → Setup](README.md#setup).

Run the tests:
- Backend: `cd backend && mvn verify` (Docker must be running: the tests start PostgreSQL, Redis and RabbitMQ)
- Frontend: `cd frontend && bunx vitest run`

**Latest run (2026-10-12): 576 tests, all passing** — 203 backend unit, 241 backend integration and security, 132 frontend.

Backend paths below are short for `backend/src/main/java/com/iloveshopping/…`.

---

## Project 3

To try the staff area: make an account an admin ([README → Becoming an admin](README.md#becoming-an-admin)),
sign in, turn on two-factor when asked, and open `/admin`. The Users page gives other accounts Support or Sales.

### 1. The README has the overview, ERD, performance report, setup and usage guide

Yes. [README.md](README.md): *What works*, *Setup*, *The admin area* (how to get in and what to try on each
page), *Usage guide*, *Database* (the whole schema as three ERDs), and *Performance analysis report*.

### 2. Star ratings, with the average from all reviews

Each review has 1–5 stars. The product's average and review count are kept up to date by the database (a
trigger), so they're always right, even when two reviews arrive at once. Hidden reviews don't count.
Tests: `ReviewIT`, `ReviewModerationIT`.

### 3. Text reviews, only for products the user bought

Only an account whose order with that product was **delivered** can review it (`catalog/PurchaseHistory`).
Anyone else gets `403`. Tests: `ReviewIT`, `ReviewServiceTest`.

### 4. Reviews sorted by helpful votes

"Most helpful" is the default sort. One vote per shopper (`review_votes`), and the count goes up inside the
database, so votes at the same moment all count. Test: `ReviewModerationIT.manyVotesAtOnceAllCount`.

### 5. 2FA is enforced for every admin account

A staff account signed in with only a password can reach nothing but the 2FA setup page (`403
two_factor_required`). Staff can't turn 2FA off. Code: `auth/StaffArea.java`. Tests: `StaffAccessIT`
(`staffSignedInWithAPasswordOnly_canReachOnlyTheTwoFactorSetup`, `aStaffAccountCantTurnTwoFactorOff`).

### 6. Product CRUD with all required fields

`/admin/products`: add, edit, hide, delete. Every field: name, description, price, stock, SKU, category, brand,
photos, weight and size (metric, imperial worked out from it), specs and variants. A product that was ever
ordered is archived instead of deleted, so old orders stay whole. Tests: `ProductAdminIT`, `StaffCatalogIT`,
`ProductRequestValidationTest`.

### 7. Admins manage products, categories, orders and refunds

- Products: the list above, plus bulk show/hide/move/price change and CSV export.
- Categories: add, edit, move, drag to reorder, delete only when empty. Test: `CategoryAdminIT`.
- Orders: a work queue, then Processing → Shipped (with tracking number) → Delivered, cancel, notes. Test: `StaffOrderIT`.
- Refunds: whole or part (chosen by line), sent to Stripe. Test: `StaffOrderIT`.

### 8. Admins manage delivery options and order status updates

`/admin/delivery`: add, edit, reorder and switch off delivery options, and set the free-shipping limit. Checkout
reads them from the database. Status updates are on each order's page, and the shopper gets an email for each.
An order can only move along allowed steps ([claude-docs/ORDER_STATES.md](claude-docs/ORDER_STATES.md)).
Tests: `DeliveryIT`, `DeliveryOptionTest`, `StaffOrderIT`.

### 9. Admins view all users and assign roles

`/admin/users`: everyone with an account, searchable by email. A role change (Customer, Support, Sales) needs the
admin's own 2FA code and logs the person out at once so the new role applies. Admin itself is only given from the
server's command line, so a stolen admin session can't create more admins. Test: `StaffUserIT`.

**Least privilege:** Support handles orders and reviews, Sales the catalog and delivery, Admin everything. Sales
can change a price by at most ±50% at a time; Support can refund at most €100 per order. One table holds the
rules: `auth/StaffArea.java`. **Audit log:** every staff change and sign-in is recorded (who, what, before and
after) and the database refuses any edit or delete for 12 months (`/admin/audit`). Tests: `StaffAccessIT`, `AuditLogIT`.

### 10. Bulk upload products from JSON or CSV

`/admin/import`: upload a file, see every problem row by row, then import all rows or none. Nothing changes
until you confirm. Example files: `docs/examples/products-good.csv`, `products-bad.csv`, `products.json`.
Code: `catalog/imports/`. Tests: `ImportIT`, `CsvReaderTest`, `ImportPage.test.tsx`.

### 11–21. The pages

| # | Page | Where | What's on it |
|---|---|---|---|
| 11 | Home | `/` | Category tiles, featured products in tabs, shop by budget |
| 12 | Product listing | `/catalog` | Photo, name, rating, price; filters (category, brand, price, rating); sort; grid/list toggle; search; paging |
| 13 | Product detail | `/catalog/{id}/{name}` | Photo gallery, full details, specs, reviews with stars and votes, Add to cart, related products |
| 14 | Cart | `/bag` | Thumbnails, prices, quantities, remove, total without shipping, Checkout button |
| 15 | Checkout | `/checkout` | Guest or signed in, delivery and billing address, delivery method, card payment, order summary |
| 16 | Order confirmation | `/checkout/{orderId}` | Order number, payment reference, estimated delivery, summary |
| 17 | Search results | `/catalog?q=…` | Same page as the listing: filters, sorting, result count, paging |
| 18 | Admin | `/admin` | Products, categories, orders, delivery, users and roles, reviews, bulk upload, audit log |
| 19 | Contact | `/contact` | A working form, emailed to the shop's inbox (see it in MailHog) |
| 20 | About | `/about` | Company, mission, team, social media links |
| 21 | 404 | any unknown address | A friendly message with a search box |

Also: account (`/account`: profile, order history, addresses, 2FA), FAQ (`/faq`), Terms and privacy (`/legal/terms`).

### 22. Quick cart preview

The bag icon in every page's header opens a drop-down of the cart (a side panel on phones).
`components/MiniCart.tsx`, `CartPanel.tsx`. Test: `CartPanel.test.tsx`.

### 23. Quick search with suggestions

The header's search box suggests words and products from the second letter typed (`components/SearchBox.tsx`).
Test: `SearchIT`.

### 24. Product images in several sizes

Every upload is saved at 160, 600 and 1200 px wide plus the original (`catalog/ImageProcessor.java`). Each page
asks for the smallest size that looks sharp (`lib/images.ts`). nginx caches images for a year; since a file's name
changes when its content does, a CDN could be put in front with no extra work. Tests: `ImageProcessorTest`,
`ProductImageIT`, `images.test.ts`.

### 25. Works at 320, 768, 1024 and 1440 px

One responsive layout: on small screens the header becomes a menu button, filters a drawer, the cart a full-height
panel. Check it: `cd frontend && bun run layout-check --widths=320,768,1024,1440 / /catalog /bag`.

### 26. Self-signed TLS certificate

nginx serves https at <https://localhost:5443> with a certificate made on first start
(`frontend/15-self-signed-cert.sh`); http redirects to https.

### 27–28. Sensitive data encrypted at rest

AES-256-GCM, key kept in `.env`, never in the database (`crypto/FieldCipher.java`).

| What | How it's stored |
|---|---|
| User credentials | Passwords as bcrypt hashes; 2FA secret encrypted; backup codes hashed |
| Personal data | Email, name, phone, avatar encrypted; email also as a keyed hash so sign-in can find it |
| Shipping addresses | Order addresses and saved addresses encrypted |
| Order details | Items bought, tracking number, payment id, card brand and last 4 encrypted |
| Session tokens | Refresh tokens, reset links, guest cart tokens stored only as hashes; access tokens never stored |

Prices, dates and statuses stay plain, since the shop sorts and filters on them. Tests: `EncryptionIT`,
`UserEncryptionIT`, `FieldCipherTest`. See it:
`docker compose exec postgres psql -U app -d iloveshopping -c "SELECT email, full_name FROM users LIMIT 3"`.

### 29. Token bucket rate limiting

Each user gets a "bucket" of requests per action (e.g. 10 sign-ins), refilled at a steady rate. Empty bucket →
`429` with "try again in N seconds". It runs in Redis, so all servers share the same count
(`ratelimit/RateLimiter.java`). Tests: `TokenBucketIT`, `RateLimitIT`.

### 30. CIA triad

- **Confidentiality** (only the right people see data): https, encryption at rest, staff roles with 2FA.
- **Integrity** (data stays correct): encrypted values detect tampering, database rules (stock never below zero),
  Stripe messages checked by signature, an audit log nobody can edit.
- **Availability** (the shop stays up): rate limits, queues that retry, unpaid orders release stock by themselves,
  and the load test shows how far it goes.

### 31. SEO basics

- Every page has its own title (under 60 characters) and description (`content/pageMeta.ts`, `pageMeta.test.ts`).
- One `h1` per page, then `h2`–`h6` in order.
- Readable URLs: `/catalog/{id}/walnut-chessboard-45-cm`, filters as `?brand=…`.
- Alt text on every product photo. All checked by `bun run a11y-check`.

### 32. Descriptive alt text on meaningful images

Product photos use the product's name (or the alt text typed at upload); decorative images have `alt=""` so
screen readers skip them. Checked by `bun run a11y-check`.

### 33. Readable at 200% zoom

Font sizes in `rem`, and the layout reflows instead of scrolling sideways. `bun run a11y-check` renders each page
at 200% and reports anything that overflows.

### 34. Why semantic HTML matters

A screen reader reads the page's structure, not its looks. Real `<nav>`, `<main>`, `<button>`, headings and lists
let a blind user jump between sections and use every control with the keyboard. A `<div>` with a click handler
can't be found or pressed that way.

### 35. Testing approach

Each rule is tested at the cheapest level that proves it: pure logic as unit tests, anything touching the
database, Redis or the queue against real ones in Docker (Testcontainers), pages and forms with Testing Library.
Tricky cases get their own tests (two buyers for the last item, the same payment message twice). Manual checks
for the rest: the a11y and layout scripts, Stripe's real card form, the load test.

### 36. Automated tests: unit, API integration, user flow, security

| Kind | Tests |
|---|---|
| Unit: product model | `ProductRequestValidationTest` |
| Unit: cart | `CartServiceTest`, `CartPanel.test.tsx` |
| Unit: order totals and shipping | `OrderTotalsTest`, `shipping.test.ts` |
| Unit: JWT | `JwtServiceTest` |
| Unit: input validation | `authRules.test.ts`, `checkoutRules.test.ts`, `CheckoutRulesTest` |
| API integration and database | every `*IT` class, e.g. `ProductAdminIT`, `OrderIT`, `StaffOrderIT` |
| User flow: product search | `SearchIT` |
| Security: rate limiting | `TokenBucketIT`, `RateLimitIT`; also `InputValidationIT`, `StaffAccessIT` |

### 37. Explain and demonstrate the tests

`cd backend && mvn verify` and `cd frontend && bunx vitest run`. One class: `mvn verify -Dit.test=TokenBucketIT`.
Good ones to show: `TokenBucketIT` (a burst is refused, then allowed again after the refill),
`StaffAccessIT` (every role against every admin area), `ReviewModerationIT.manyVotesAtOnceAllCount`.

### 38. Most users before responses pass 5 seconds

About **1,200 users** at once (95% of requests within 4.6 s; 8.9 s at 1,600). Server limited to 2 CPU cores and
1 GB. Full report: [README → Performance analysis report](README.md#performance-analysis-report).

### 39. Throughput

About **50 shopper journeys (230 requests) a second** with good response times at 800 users; at most 58 journeys
(300 requests). Every target in the brief holds up to 800 users.

### 40. Bottlenecks and fixes

- Search suggestions for two letters read every product → **fixed**: cached for 30 s, now 3× faster.
- bcrypt password checks use a lot of CPU → more CPU (not a weaker hash).
- Everyone arriving in the same second → run more server containers behind nginx.
- Filter counts on big shelves → cache them.

Run it: `loadtest/run.sh 50 400` (about 4 minutes), then `loadtest/run.sh down`.

## Extra

### UI/UX quality and responsiveness
One design across the shop and the admin area, tested at 320–1440 px, with clear buttons on every page.

### Admin interface
An overview page with a to-do list (what's waiting, oldest first), lists with filters and counts, bulk actions,
keyboard shortcuts in the order queue, printable packing slips, and a confirm step before anything destructive.

### Error handling and user guidance
Every form error says how to fix it, next to the field and in a list at the top. Typed input is kept when
something fails. Sold-out items, rate limits and payment failures each get a plain message.

### Extra features
Product variants (colour, size), metric and imperial sizes, saved addresses, data export and account deletion,
part refunds, the bad rows of an import downloadable for fixing, FAQ and Terms pages.

### Security, performance and accessibility
See 5, 9 and 26–30 (security), 24 and 38–40 (performance), 31–34 (accessibility: no findings from
`bun run a11y-check` on any shop or account page).

### Docker, with only Docker and the payment CLI needed
`./start.sh` builds and starts 7 containers (website, server, PostgreSQL, Redis, RabbitMQ, MailHog, Stripe CLI).
The Stripe CLI runs in a container too, so Docker is the only thing to install.

---

## Project 2

### 1. The README contains an updated overview, ERD, setup instructions and usage guide for the commerce functionality

Yes. [README.md](README.md): *What works* (cart, checkout, orders, payments, the queue, emails,
cancelling, encryption, HTTPS, and what isn't built), the new tables with a Mermaid ERD and the list of
encrypted columns, setup with the Stripe keys, and a usage guide: test cards, the emails in MailHog,
seeing the encryption with `psql`, seeing HTTPS, and making a message land in a dead letter queue.

### 2. The database schema supports the cart, for guests and for logged-in users

`V8__carts.sql`: `carts` has exactly one owner, either `user_id` (unique: one cart per account) or
`guest_token_hash` (the SHA-256 of the guest's `cart_token` cookie), enforced by
`CHECK ((user_id IS NULL) <> (guest_token_hash IS NULL))`. `cart_items` is one row per product per
cart, quantity 1–10 (`CHECK`). No price column: prices are read from `products` on every read, so the
cart can never show a stale price.

### 3. The cart shows each item's name, price and thumbnail

`frontend/src/components/CartPanel.tsx`, from `GET /cart` (`cart/CartService.view`). Test: `CartPanel.test.tsx`.

### 4. Add, remove and change quantities, with the totals updating as you go

`POST /cart/items`, `PUT` and `DELETE /cart/items/{productId}`; every answer is the whole cart with its
new subtotal, worked out on the server in `BigDecimal`. Tests: `CartServiceTest` (unit: totals, short
and sold-out lines, caps), `CartIT` (integration).

### 5. A guest cart is saved for a while

A random token in an `HttpOnly`, `SameSite=Strict` cookie finds the cart; the database keeps only its
hash. `cart/GuestCartCleanup` deletes guest carts untouched for 30 days (`CART_GUEST_TTL`). Test: `CartIT`.

### 6. A logged-in cart persists across sessions

The cart belongs to `carts.user_id`, so it is the same on any browser. At sign-in the guest cart is
merged in (`POST /cart/merge`; where both have a product, the larger quantity wins). Tests: `CartIT`
(merge cases), `session.test.ts`.

### 7. Out-of-stock is handled when adding

One conditional upsert (`cart/CartItemRepository.addUpTo`) adds only while the line stays within the
stock and the limit of 10, so two quick adds can't overshoot. Past it: `409` with how many are left,
and the page says so. A line that sells out later is flagged `sold_out` and left out of the total.
Tests: `CartServiceTest`, `CartIT`.

### 8. Single-page checkout

`/checkout` (`frontend/src/pages/CheckoutPage.tsx`): contact, address, shipping and Stripe's card form
on one page, with the summary beside it.

### 9. It collects basic information, an address and a payment choice

Email and phone; name, street, flat, postcode, city, country; three shipping options priced by the
server (`order/ShippingMethod`); and Stripe's Payment Element for the card.

### 10. Known details are prefilled for logged-in users

Name and email from the account; phone and address from the account's last order
(`GET /orders/last-delivery`, `OrderService.lastDelivery`), filled only into fields still empty.
Tests: `CheckoutPage.test.tsx` ("fills in what the account knows", "fills in the phone and address of
the last order"), `OrderListIT.theLastDelivery_prefillsTheNextCheckout`.

### 11. The shipping address is validated for accuracy

In the browser (`validation/checkoutRules.ts`) and again on the server (`order/CheckoutRules.java`,
plus Bean Validation on `PlaceOrderRequest`):
- every field present, within its length, a house number in the street
- the postcode's format for the country (Estonia 5 digits, Latvia `LV-1234`, …)
- for the biggest cities, the postcode belongs to the city: `Tallinn` + `50090` is refused, because
  every Tallinn postcode starts with 10–15 (`50090` is Tartu). Only cities whose postcodes all share
  their first digits are listed, so a real address is never refused.

What it can't do: confirm a street exists at that postcode. That needs a postal lookup service (Omniva,
Google Address Validation), which this project doesn't use. Tests: `CheckoutRulesTest`,
`checkoutRules.test.ts`, `OrderIT.badFields_are400_withTheFieldAndHowToFixIt_andNothingIsReserved`.

### 12. An order summary with every item, quantity and cost

The checkout's summary lists each line, its quantity (still changeable) and price, subtotal, shipping,
VAT and total. The server prices the order itself (`order/OrderTotals`); the page's numbers are only a preview.

### 13. An email confirmation after a successful order

When Stripe confirms the payment, the order consumer queues a confirmation email; `order/OrderMail`
sends it with the items, totals, VAT, card, delivery address and a link to the order. Test:
`MessagingIT.aPayment_isConfirmedByEmail_toTheAddressOnTheOrder`, `OrderMailTest`. See it in MailHog.

### 14–15. Clear errors for invalid input and failed payments: missing fields, bad email, phone or address, payment validation, network errors

Every message says how to fix it ("Enter the full number with the country code, like +372 5123 4567").
They show next to the field and in a summary at the top that moves the focus. A bad card stops in
Stripe's form before any order exists; a declined card shows the bank's reason; a lost connection
keeps everything typed and says nothing was charged. Tests: `CheckoutPage.test.tsx`,
`checkoutRules.test.ts`, `OrderIT.badFields_…`, `PaymentFailuresTest`.

### 16. Stripe sandbox

Stripe test mode: `payment/StripePaymentGateway` (stripe-java), one PaymentIntent per order, the Stripe
CLI in a container forwarding webhooks. Test cards: [README](README.md#test-cards).

### 17. The payment form uses the provider's secure elements

Stripe's Payment Element (`frontend/src/components/CardPayment.tsx`) runs in Stripe's own iframe; card
details go from the browser straight to Stripe. The server only sees the PaymentIntent id, and later
the card brand and last 4 digits.

### 18. Card number, expiry and CVC are checked before submitting

`elements.submit()` validates the card fields inside Stripe's frame before the order is placed.
Test: `CheckoutPage.test.tsx` ("stops at a card Stripe finds wrong, before any order exists").

### 19. Explain PCI DSS, and why card data shouldn't be stored on the server

PCI DSS is the card industry's security standard for anyone who stores, processes or transmits card
data. Every system that touches a card number is "in scope": it must be locked down, logged, scanned
and audited, and a breach can cost fines and the right to take cards at all (Heartland). Using
Stripe's hosted fields, the number never touches our servers, so we qualify for the simplest
self-assessment (SAQ A). We store no card number, expiry or CVC — there are no such columns — only
Stripe's payment id and the brand and last 4 for the receipt, and those are encrypted too. A breach of
our database leaks no usable card. This is **tokenization**: the PaymentIntent and its client secret
stand in for the card.

### 20. The order status follows the provider's callbacks, for success and failure

Only Stripe's signed webhook moves an order to *Payment successful* or *Payment failed*; the browser
never decides. A bad signature is `400` and changes nothing. Each event is applied once
(`processed_payment_events`). Tests: `OrderIT.aSignedInCheckout_goesFromCartToPaid_onlyWhenStripeSaysSo`,
`aBadOrMissingSignature_is400_andChangesNothing`, `theSameEventTwice_isAppliedOnce`.

### 21. Payment status updates are published to a message queue

`payment/PaymentWebhookController` checks the signature, then `PaymentEventPublisher` publishes a
`PaymentEvent` to RabbitMQ (exchange `payments` → queue `orders.payment-events`) and waits for the
broker's publisher confirm before answering Stripe 200. If RabbitMQ is down the webhook answers 503 and
Stripe sends it again. `order/PaymentEventListener` (the order service) consumes it and updates the
order, stock and history in one transaction — the brief's diagram. Tests: `MessagingIT`,
`PaymentEventPublisherTest`, and every webhook test in `OrderIT` goes through the real queue.

### 22. Emails for both successful and failed payments

Paid → confirmation; declined → "the payment didn't go through", with the reason and until when the
items are held; also cancelled and refunded. Queued only after the order's transaction commits
(`order/OrderNotifier`), on their own queue `orders.emails`. Tests: `MessagingIT`, `OrderMailTest`,
`OrderCancelIT`.

### 23–24. Responds to payment failures: insufficient funds, invalid card number, expired card, gateway timeout

- insufficient funds, expired card, wrong CVC: Stripe's decline code → a plain sentence
  (`payment/PaymentFailures`), shown on the order page and in the email; the shopper can try another
  card on the same order while the items are held
- invalid card number: Stripe's form refuses it before anything is sent
- gateway timeout: Stripe unreachable when placing → the order is withdrawn, stock and cart restored,
  `503`, nothing charged. A timeout later leaves the order pending; the expiry job asks Stripe for the
  real status before releasing anything
Tests: `OrderIT.theFourFailuresTheBriefNames_eachReachTheOrderPage`,
`whenStripeCantBeReached_theOrderIsWithdrawn_andEverythingGoesBack`, `anExpiredHold_staysPut_whileStripeCantBeAsked`.

### 25–26. No overselling, even with simultaneous payments

Stock is taken when the order is placed, with one statement:
`UPDATE products SET stock_quantity = stock_quantity - n WHERE id = ? AND stock_quantity >= n`. Zero rows
changed means sold out, and the whole order rolls back. Lines are locked in product-id order so two
orders can't deadlock, and `CHECK (stock_quantity >= 0)` is the safety net. Test:
`OrderIT.twoBuyersForTheLastOne_exactlyOneGetsIt_andStockNeverGoesBelowZero` (two threads, five rounds).

### 27. Filter and sort orders by date and status

`/account/orders`; `GET /orders?status=&from=&to=&sort=&page=&size=`. Filters and sorting happen in SQL,
a page at a time (`OrderRepository.findPageForUser`); the sort is a bound parameter checked against a
fixed list, never text in `ORDER BY`. The filter pills show counts per status. Tests: `OrderListIT`
(filters, date range, sorts, paging, only your own orders, bad parameters → 400), `OrderPages.test.tsx`.

### 28. The order page shows everything, with status updates

`/account/orders/:id`: progress tracker, items, totals, delivery, payment, refund, and the status
history from `order_status_history` (every move, who made it, and why). Someone else's order is `404`.

### 29. Cancelling unprocessed orders, with a refund workflow

Allowed only in *Payment successful*; once packing starts (*Processing*) the server answers `409`
whatever the page shows. One transaction: *Cancelled*, stock back on sale, payment marked
`REFUND_DUE`. Then Stripe is asked for the refund, with `refund-{orderId}` as the idempotency key, so a
double click or a retry can't refund twice. Stripe's answer, or its `charge.refunded` webhook, moves
the order to *Refunded*. If Stripe is down, the order stays cancelled and `order/RefundRetry` asks
again every minute. Tests: `OrderCancelIT` (refund, pending refund finished by webhook, Stripe down,
refused when processing or unpaid, double click refunds once, owner only).

### 30. Stock goes down when orders are placed and back up when they are cancelled

Down: the reserving `UPDATE` above. Up: `ProductService.releaseStock`, in the same transaction as the
cancellation, and also when an unpaid order's 30-minute hold runs out. Tests:
`OrderCancelIT.cancellingAPaidOrder_putsTheStockBack_refundsTheCard_andSaysSoInTheHistory`,
`OrderIT.anUnpaidOrderPastItsHold_isCancelled_atStripeToo_andTheStockAndCartComeBack`.

### 31–32. Sensitive data is encrypted at rest: order details, shipping addresses, payment records

AES-256-GCM, through a JPA converter (`crypto/FieldCipher`, `crypto/Encrypted`):
- `orders`: email, phone, first and last name, street, flat, postcode, city
- `order_items`: product name, options, photo (what was bought)
- `payments`: Stripe payment id, card brand, last 4, decline code

Each value gets a fresh random 12-byte IV, so equal values look different, and a 16-byte tag, so a
changed byte fails to decrypt instead of decrypting to something else. The key (`APP_ENCRYPTION_KEY`)
is generated by `start.sh` into `.env`; it is never in git or the database. Ids, statuses, dates,
totals and the country stay plain because queries filter and sort on them.

**Manual check (at rest):** place an order, then
`docker compose exec postgres psql -U app -d iloveshopping -c "SELECT number, first_name, street, city, total FROM orders"`:
names and addresses are `v1:…` ciphertext, the website shows them normally.
**Manual check (in transit):** <https://localhost:5443> is TLS 1.2/1.3 (the padlock, or
`curl -kIv https://localhost:5443`); `curl -I http://localhost:5173` answers `301` to https; the refresh
cookie is `Secure`. Tests: `EncryptionIT` (ciphertext in all three tables, plain text in the API,
tampering detected), `FieldCipherTest`.

### 33. Explain the approach to testing the cart, checkout and payments

Three layers. **Unit tests** pin the arithmetic and rules, fast and with no database: cart totals and
caps (`CartServiceTest`), order totals, the €100 free-shipping line and VAT rounding
(`OrderTotalsTest`), the status table (`OrderStatusTest`), address rules, email wording, the cipher.
**Flow tests** run the real app against real PostgreSQL, Redis and RabbitMQ (Testcontainers), with
only Stripe and the mail server replaced: checkout from cart to paid as a guest and signed in, every
webhook signed the way Stripe signs it and going through the real queue. **Pressure tests** cover what
only breaks under stress: two buyers for the last item, the same webhook twice (also at once), a
double-clicked Pay and Cancel, a poison message, a dead mail server.

What a green suite still doesn't prove: real Stripe timing and 3-D Secure (only trying the test cards
by hand shows that), many buyers at once rather than two, and the moment between a commit and RabbitMQ
going down (that one email would be lost; an outbox table would close it).

### 34. Automated tests exist: unit (cart, order calculations) and critical flows (registration, checkout)

- Unit: `CartServiceTest` (cart), `OrderTotalsTest` (order calculations)
- Registration flow: `AuthControllerIT.register_succeeds_thenDuplicateEmailConflicts` (the second sign-up
  with the same email is refused, so the first was stored), `register_withInvalidBody_returns400WithFieldErrors`,
  `RegisterPage.test.tsx`
- Checkout flow: `OrderIT.aGuestOrder_reservesTheStock_copiesTheCart_andEmptiesIt` and `aSignedInCheckout_goesFromCartToPaid_onlyWhenStripeSaysSo`,
  plus `CheckoutPage.test.tsx` in the browser

### 35. Explain and demonstrate the tests

`cd backend && mvn verify` runs all 316 backend tests in a few minutes; a single class:
`mvn verify -Dit.test=OrderCancelIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`.
`cd frontend && bunx vitest run` runs the 75 frontend tests in seconds. A good one to show:
`OrderIT.twoBuyersForTheLastOne_…` — start two threads at the same instant, exactly one gets `201`,
the other `409`, stock ends at 0, never −1.

## Extra

### Cart quality, UX, and persistence across guest and logged-in

Server cart for both; the merge at sign-in never doubles a line; sold-out and short lines are shown,
not silently dropped; recommendations from the same game.

### Checkout flow, error handling and guidance

One page, prefilled, every error with a fix, the focus moved to the summary of errors, input kept on
any failure, and the waiting page that never claims "paid" before Stripe says so.

### Payment security, transaction handling, response to payment scenarios

No card data on our side; signed webhooks only; one PaymentIntent per order (idempotency key), so a
retry never charges twice; two short transactions around every Stripe call, so no row lock waits on
the network; a lost webhook settled by asking Stripe.

### Order management, status tracking, message queue

The state machine with a single gate (`Order.moveTo`) and a full history; two durable queues, each
with retries and a dead letter queue; idempotent consumer; emails only after commit; no personal data
in messages. Try: RabbitMQ → `orders.payment-events` → *Publish message* `not json` → it lands in
`orders.payment-events.dlq`.

### Docker, and only Docker and the payment CLI as prerequisites

`./start.sh` builds and runs 7 containers (website, server, PostgreSQL, Redis, RabbitMQ, MailHog,
Stripe CLI). The Stripe CLI itself runs in a container, so Docker really is the only thing to install.

---

# Review Answers — Project 1

As answered at the Project 1 review (2026-09-28); counts and container numbers are from then.

<details>
<summary><b>Show the Project 1 answers</b></summary>

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
1. **Setup:** confirm your password, then a QR code appears. Scan it from inside Google Authenticator or Authy. On an iPhone, the Passwords app also works, but only after you save an entry with website `localhost` (full steps in the [README](README.md#setting-up-two-factor-login)).
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

</details>
