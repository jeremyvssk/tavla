# Frontend

The website for i-love-shopping: React 18 and TypeScript, built with Vite and managed with Bun.

You don't need to run anything in this folder to use the site. `./start.sh` in the project root
builds it, and nginx serves it over HTTPS at <https://localhost:5443> (<http://localhost:5173> redirects
there). This page explains where things live.
Every file starts with a one-line comment saying what it does.

---

## Folders (`src/`)

| Folder | What's in it |
|---|---|
| `main.tsx` | Starting point: wraps the app in Redux, React Query and the router |
| `App.tsx` | The list of pages (routes), and restoring the login after a page reload |
| `pages/` | One file per page (see below) |
| `admin/` | The staff area under `/admin`, loaded only when a staff member opens it (see [Admin](#admin)) |
| `components/` | Pieces used on several pages: header and footer (`Layout`), search box with word and product suggestions (`SearchBox`), the cart dropdown on desktop (`MiniCart`) and panel on smaller screens (`CartPanel`), product cards and list rows, reviews and the write-a-review dialog, form fields, the CAPTCHA and Google buttons, Stripe's card form (`CardPayment`), card brand marks (`PayMarks`), two-factor setup (`TwoFactorPanel`), and the order pages' shared parts (`OrderParts`: status pill, progress tracker, totals) |
| `content/` | The text of the help, FAQ and legal pages |
| `api/` | Every call to the backend. `client.ts` is the shared Axios setup. `orders.ts` covers placing, reading, paying again, the account's list, cancelling, and the last delivery details. `account.ts` (profile, addresses, data export, delete), `support.ts` (contact form) and most of `admin.ts` call endpoints the backend doesn't have yet; each is marked `PLANNED` |
| `auth/session.ts` | Starting, restoring and ending a login session |
| `store/` | Redux state: who is logged in (`authSlice`) and whether the cart panel is open (`cartSlice`) |
| `validation/` | Form rules (email format, password length...), copied from the backend's rules, and the checkout's (`checkoutRules.ts`, with the billing address), contact form's and address book's rules |
| `hooks/`, `lib/` | Small helpers: the cart from the server (`useCart.ts`), delaying search input, loading Google's scripts, formatting prices, picking colour/size variants, placing a photo on its tile (`framing.ts`, used by `components/Photo.tsx`), shipping options and delivery dates (`shipping.ts`), how each order status looks (`orderStatus.ts`), loading Stripe.js (`stripe.ts`), finishing a typed word for the search (`completions.ts`), the page title and description (`usePageMeta.ts`) |
| `test/` | Test setup and a helper that renders a page with everything it needs |
| `index.css`, `p3.css` | All styles and colours. `p3.css` holds the Project 3 screens, ported from the design canvas |

## Pages

| Address | File | Shows |
|---|---|---|
| `/` | `HomePage` | The landing page with featured products |
| `/catalog` | `CatalogPage` | Browsing and search results, with filters and sorting |
| `/catalog/:id` | `ProductPage` | One product: photos, variants, specs, reviews, add to cart |
| `/login`, `/register` | `LoginPage`, `RegisterPage` | Log in (plus the 2FA step) and sign up |
| `/forgot`, `/reset` | `ForgotPasswordPage`, `ResetPasswordPage` | Password reset |
| `/account` | `AccountPage` | Profile, your data as a file, deleting the account. Logged-in users only |
| `/account/addresses`, `/account/security` | `AddressesPage`, `SecurityPage` | Saved addresses; two-factor setup. Logged-in users only |
| `/bag` | `CartPage` | The full cart (not `/cart`, which is the API) |
| `/contact`, `/faq`, `/about` | `ContactPage`, `FaqPage`, `AboutPage` | Help and the company |
| `/legal/:doc` | `LegalPage` | Terms, privacy and cookies, as tabs |
| anything else | `NotFoundPage` | A 404 with the search filled from the broken address |
| `/checkout` | `CheckoutPage` | One-page checkout: contact, address, shipping, Stripe's card form, and the order summary. Has its own slim header |
| `/checkout/:orderId` | `OrderStatusPage` | After Pay: waiting for the payment result, then confirmed, or failed with another card to try. A guest cancels a paid order here (the link in their email leads here) |
| `/account/orders` | `OrdersPage` | Your orders, filtered by status and date, sorted. Logged-in users only |
| `/account/orders/:orderId` | `OrderPage` | One order: progress, items, delivery, history, and cancelling. Logged-in users only |
| `/admin/...` | `admin/` | The staff area, below |

The checkout fills in a signed-in shopper's name and email from the account, and the phone and
address from their last order (`GET /orders/last-delivery`), only into fields still empty.
`validation/checkoutRules.ts` checks the postcode's format per country, and for the biggest cities
that it belongs to the city; the backend's `CheckoutRules` checks the same.

**Paying.** The card form is Stripe's Payment Element, in an iframe served by Stripe, so card details
never pass through this app's code or server. Pressing Pay: Stripe checks the card fields
(`elements.submit()`), the order is placed (`POST /orders`, which answers with a client secret), then
Stripe.js confirms the payment with it. The order page then asks the server for the status every 2
seconds until Stripe's webhook has set it. The browser never decides that an order is paid.

Page addresses never start with an API path like `/products`, `/auth`, `/cart`, `/orders`, `/staff`
or `/support`. Those paths go to the backend, not to the website.

## Admin

`/admin` is for staff (the Admin, Support and Sales roles). It is a separate chunk of code that
shoppers never download. A shopper who opens it gets the 404 page; a staff account without two-factor
gets only the screen that turns it on (`admin/TwoFactorGate.tsx`). `admin/access.ts` says which role
opens which section, and the side menu shows only those. The server checks the role on every call
anyway; hiding a link is not the security.

| Address | File | Shows |
|---|---|---|
| `/admin` | `OverviewPage` | What is waiting on someone, the day's numbers, recent changes, latest orders |
| `/admin/orders`, `/admin/orders/:id` | `OrdersPage`, `OrderPage` | The order queue, oldest first (J and K move, X ticks, P starts packing), and one order: pack, ship with tracking, deliver, cancel, refund by line |
| `/admin/orders/slips?ids=` | `PackingSlipsPage` | Packing slips for the ticked orders, one printed page each, without prices |
| `/admin/delivery` | `DeliveryPage` | The free-shipping limit and the delivery options |
| `/admin/products`, `/admin/products/:id` | `ProductsPage`, `ProductEditPage` | The catalog as a table, and one product's every field and photo |
| `/admin/categories`, `/admin/import` | `CategoriesPage`, `ImportPage` | The shelf tree; a CSV or JSON upload that is checked before it imports |
| `/admin/reviews` | `ReviewsPage` | New and reported reviews to keep or hide |
| `/admin/users`, `/admin/audit` | `UsersPage`, `AuditPage` | Roles (a change asks for your two-factor code), and the log of every staff action |

**What works today.** Product create, edit, photos and archive, and adding a category, use endpoints
the backend has. Everything else in the admin calls `/staff/...` endpoints that don't exist yet; they
are typed in `api/admin.ts` and marked `PLANNED`, so the page shows "The server can't do this yet"
until they are built. The product list and the category tree fall back to the shop's own search and
tree until then (active products only, no SKU or exact stock).

---

## How login works here

- **The access token is kept only in a variable** in `api/client.ts`, never in `localStorage`, so
  a malicious script can't read it from storage. Redux stores *who* is logged in, but never the
  token.
- **On page reload** the variable is empty, so `auth/session.ts` asks `/auth/refresh` for a new
  token. The refresh token is in a cookie the browser sends automatically.
- **When a token expires**, `client.ts` gets a new one and retries the request. If several requests
  fail at once, they share a single refresh.

**Data from the server** (products, reviews, categories, the cart) is loaded and cached with
**React Query**. **The cart** lives on the server (`hooks/useCart.ts`). A guest's cart is found by a
cookie JavaScript can't read, and a logged-in user's by their account. Each change sends one
request, and the server answers with the whole cart. Right after any login, `auth/session.ts` asks
the server to merge the guest cart into the account's (`POST /cart/merge`).

---

## Running it on its own (for development)

```sh
docker compose stop frontend   # free port 5173; the backend keeps running
bun install
bun run dev                    # http://localhost:5173, reloads on every save
```

The dev server forwards API calls to the backend on port 8080, the same way nginx does in Docker
(`vite.config.ts`). If you add a new API path prefix, add it in both `vite.config.ts` and
`nginx.conf`.

Google login and CAPTCHA appear only when `VITE_GOOGLE_CLIENT_ID` and `VITE_RECAPTCHA_SITE_KEY`
are set. With Docker, these come from `GOOGLE_CLIENT_ID` and `RECAPTCHA_SITE_KEY` in the root
`.env`. See the main [README](../README.md#needs-your-own-google-keys). The card form needs
`VITE_STRIPE_PUBLISHABLE_KEY` the same way (from `STRIPE_PUBLISHABLE_KEY`, see
[Setup](../README.md#setup)).

## Tests

```sh
bun run test         # Vitest, re-runs on save
bunx vitest run      # run once
```

Use `bun run test`, not `bun test`, which starts Bun's own test runner instead of Vitest.

Test files sit next to the file they test (`*.test.ts`, `*.test.tsx`). Stripe's form can't run in
jsdom, so the checkout and order tests swap `components/CardPayment` for a stand-in.

| Test | Checks |
|---|---|
| `validation/authRules.test.ts` | The form rules at their exact limits |
| `pages/LoginPage.test.tsx`, `RegisterPage.test.tsx` | Error messages, the button locking while waiting, server errors, the 2FA step |
| `api/client.test.ts` | Expired tokens are refreshed once, even when several requests fail together |
| `components/CartPanel.test.tsx` | The cart panel: lines and prices from the server, a sold-out line left out of the total, recommendations, a refused quantity explained |
| `pages/CheckoutPage.test.tsx` | Details filled in for logged-in users (the account, and the last order's phone and address), every field to fix listed before paying, a card Stripe finds wrong stopping before any order, Pay placing the order and confirming it with Stripe, a lost connection or a down payment provider keeping what was typed |
| `pages/OrderPages.test.tsx` | Order filters sent to the server, cancelling only after asking, where the refund stands, a guest cancelling from the confirmation page, the waiting page turning into thank-you, another card on a failed order |
| `auth/session.test.ts` | The guest cart is merged before the shopper counts as signed in; a failed merge doesn't block sign-in |
| `validation/checkoutRules.test.ts` | The checkout's messages for missing fields, a bad email, phone, street or postcode, and a postcode from another city; the same for a separate billing address |
| `validation/contactRules.test.ts`, `addressRules.test.ts` | The contact form (an optional order number that must look like TV-24851, the message length) and a saved address (its name, then the checkout's rules) |
| `lib/completions.test.ts` | Search suggestions: the typed word finished plus the next one, little words skipped, no repeats |
| `admin/parts.test.ts` | How long an order has waited, as the admin queue shows it |
| `lib/shipping.test.ts` | Free shipping from €100, VAT inside the total, delivery dates that skip weekends |
| `lib/variants.test.ts` | Picking the right product when you switch colour or size |
| `lib/framing.test.ts` | Placing a photo: uncut products centred, cut edges on the tile edge, book covers whole |

**Layout check.** `scripts/layout-check.mjs` opens pages of the running site (`bun dev`) at several
screen widths and lists what looks misaligned: content wider than the screen, edges a few pixels
apart, text off its row's line, side-by-side cards that end at different heights, text cut off.
The comment at the top of the script lists its options and known false alarms.

```sh
bun run layout-check /catalog /bag
bun run layout-check --widths=390,1440 --login=you@example.com:password /account
```
