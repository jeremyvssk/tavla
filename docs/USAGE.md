# Using Tavla

How to try every part of the shop once it is running. Setup is in the [README](../README.md#run-it).

## Contents

1. [The admin area](#the-admin-area)
2. [In the shop](#in-the-shop)
3. [Cart, checkout and payments](#cart-checkout-and-payments)
4. [Accounts and login](#accounts-and-login)
5. [The API](#the-api)
6. [Troubleshooting](#troubleshooting)

---

## The admin area

### Becoming an admin

Admin can't be given from the website, only from the server, so a stolen admin session can never make more
admins.

1. In the shop, **create an account** at `/register` (with a password, not Google).
2. Straight after, make the newest account an admin:
   ```sh
   docker compose exec postgres psql -U app -d iloveshopping \
     -c "UPDATE users SET role = 'ADMIN' WHERE id = (SELECT id FROM users ORDER BY created_at DESC LIMIT 1) RETURNING role"
   ```
   It prints `ADMIN`. (The email column is encrypted, so the command picks the newest account instead of
   searching by email.)
3. **Sign out and sign in again**, then open `/admin`. It asks you to **turn on two-factor sign-in** first:
   scan the QR code with your authenticator app and type the 6-digit code
   ([step by step](#setting-up-two-factor-login)). Then the admin area opens.

### Giving someone the Support or Sales role

1. Create a second account in another browser (or a private window).
2. As the admin: **Users and roles** → search for that account's **whole email** → choose the role → type
   your current 2FA code. The person's open sessions get the new role at once.
3. Signed in as that account, `/admin` asks it to turn on two-factor too, then shows only that role's pages.

### What each page does

| Page | Who sees it | What to try |
|---|---|---|
| **Overview** | Everyone on staff | What's waiting (orders to pack, reviews to check), today's sales, recent changes |
| **Orders** | Support, Admin | Pay for an order in the shop first ([test cards](#test-cards)). Then open it: **Start packing** → **Shipped** (courier and parcel number) → **Delivered**. The shopper gets an email at each step ([MailHog](http://localhost:8025)). Also: cancel while packing, a refund for one item, a team note, printable packing slips |
| **Delivery options** | Sales, Admin | Change a price or the free-shipping limit, then open the checkout: it shows the new price at once |
| **Products** | Sales, Admin | Add a product (every field, photos), edit one, hide or delete it. Tick several rows to show, hide, move or reprice them together. Export to CSV |
| **Categories** | Sales, Admin | Add, rename, hide, drag to reorder, move under another. Only an empty one can be deleted |
| **Bulk upload** | Sales, Admin | Upload a file from [docs/examples/](examples/): `products-good.csv` and `products.json` pass the check; `products-bad.csv` has one mistake per line and the page names each one. Nothing changes until you confirm, and then all rows go in or none |
| **Reviews** | Support, Admin | New and reported reviews wait here: **Keep**, or **Hide** with a reason. A hidden review leaves the product page and its average |
| **Users and roles** | Admin | Find an account, change its role (needs your 2FA code) |
| **Audit log** | Admin | Every staff change and sign-in: who, what, before and after. Nobody can edit or delete it |

**Try the role limits:** as Sales, change a price by more than half: refused. As Support, refund more than
€100 on one order: refused. As Support, open `/admin/products`: not in the menu, and the server refuses it
too. Without two-factor, every admin page says to turn it on first.

**To write a review to moderate:** a review needs a delivered order. Buy a product as a shopper, mark the
order **Delivered** as staff, then write a review on that product's page. It shows up under **Reviews**.

---

## In the shop

| Try this | Where |
|---|---|
| Featured products and categories | `/` |
| Filters, sorting, grid or list, paging | `/catalog`, or search from the header |
| Search suggestions while you type | The header's search box: type `wal` |
| Cart preview | Add something, then click the bag in the header |
| Reviews: sort by most helpful, vote, report | Any product page with reviews, signed in |
| Check out as a guest or signed in | The cart → **Checkout**, then a [test card](#test-cards) |
| Your profile, orders, addresses, data download | `/account` while signed in |
| Contact form | `/contact`: the message arrives in [MailHog](http://localhost:8025) |
| About, FAQ, Terms, 404 | `/about`, `/faq`, `/legal/terms`, any made-up address |

### Test cards

Any future expiry date, any CVC, any postcode:

| Card number | What happens |
|---|---|
| `4242 4242 4242 4242` | Paid |
| `4000 0025 0000 3155` | The bank asks for a security check (3-D Secure) first, then paid |
| `4000 0000 0000 9995` | Declined: not enough money |
| `4000 0000 0000 0069` | Declined: expired card |
| `4000 0000 0000 0127` | Declined: wrong CVC |
| `4242 4242 4242 4241` | Stripe's form says the number is wrong, before anything is sent |

### Checking security, SEO, accessibility and load

| Check | How |
|---|---|
| Data is encrypted in the database | `docker compose exec postgres psql -U app -d iloveshopping -c "SELECT email, full_name FROM users LIMIT 3"`: scrambled `v1:` text, while signing in works. Same for orders: `... -c "SELECT number, first_name, street, total FROM orders LIMIT 3"` |
| HTTPS | Click the padlock at <https://localhost:5443>, or `curl -I http://localhost:5173` to see the redirect |
| Rate limiting | `for i in $(seq 12); do curl -ks -o /dev/null -w "%{http_code} " -X POST https://localhost:5443/auth/login -H "Content-Type: application/json" -d "{\"email\":\"try$i@example.com\",\"password\":\"wrong-password\"}"; done`: `401` ten times, then `429` |
| Photo sizes | Upload a photo to a product in the admin area, then open its page with the browser's network tab: a phone-sized window loads the 160 or 600 px file |
| Titles, headings, contrast, alt text, 200% zoom | `cd frontend && bun run a11y-check / /catalog /bag /contact` while the shop runs |
| 320, 768, 1024 and 1440 px screens | `cd frontend && bun run layout-check --widths=320,768,1024,1440 / /catalog /bag`, or resize the browser |
| The load test | `loadtest/run.sh 50 400` (about 4 minutes), then `loadtest/run.sh down` |

Any database tool works too (DBeaver, pgAdmin, VS Code's SQLTools): host `localhost`, port `5432`,
database `iloveshopping`, user `app`, password `changeme`.


---

## Cart, checkout and payments

### Trying the cart and checkout

| Try this | Where |
|---|---|
| Add to cart, change quantities, see suggestions | Any product page, then the cart in the header |
| Ask for more than is in stock | A product page that says *Only N left*: raise its quantity in the cart past N, and the cart says how many are left |
| Keep a cart as a guest, then log in | Add items logged out, then log in: they move into the account's cart |
| Check out | The cart → **Checkout** (as a guest or logged in) |
| See prefilled details | Log in, place one order, then start another: phone and address are filled in |
| See the address check | Checkout with city `Tallinn` and postcode `50090` (a Tartu postcode) |
| Pay, and see a declined card | Checkout with a [test card](#test-cards) |
| The emails for paid, declined, cancelled, refunded | [MailHog](http://localhost:8025) |
| Your orders, filters, sorting, order details | `/account/orders`, while logged in |
| Cancel an order and get a refund | Open a paid order → **Cancel order**. Watch the product's stock go back up |

After a declined card the order page offers **Try another card** for the same order. Payments and
refunds show under **Payments** in the Stripe dashboard (test mode). Stripe's events as they arrive:
`docker compose logs -f stripe-cli`.

### Seeing the queues

Open RabbitMQ at <http://localhost:15672> → **Queues**. `orders.payment-events` takes Stripe's results,
`orders.emails` the emails, and each has a `.dlq` (dead letter queue) beside it.

- **A bad message set aside:** open `orders.payment-events` → **Publish message**, type `not json` as
  the payload, publish, and after a few seconds it appears in `orders.payment-events.dlq`.
- **An email waiting out a mail server failure:** run `docker compose stop mailhog`, pay an order, and
  the email lands in `orders.emails.dlq`. Run `docker compose start mailhog`, open that queue →
  **Move messages** to `orders.emails`, and it arrives.

---

## Accounts and login

### Trying accounts and search

| Try this | Where |
|---|---|
| Browse, filter and sort | `/catalog`: pick a category, brand, price or rating, change the sort |
| Search with suggestions | Search box in the header: type `wal` |
| Create an account | `/register` |
| Reset your password | `/login` → *Forgot your password?*, then open the email in [MailHog](http://localhost:8025) |
| Turn on two-factor login | `/account` (see below) |
| Write a review | Any product page, while logged in |

### Setting up two-factor login

First, on the website:
1. Log in and open `/account`.
2. Type your password and click **Set up two-factor**. A QR code and a long **setup key** appear.

Then add it to an app on your phone. Pick **one** of these:

*Option A: Google Authenticator or Authy (easiest)*
1. Install the app from the App Store or Google Play.
2. Open the app and tap **+** → **Scan a QR code**.
3. Scan the QR code on the screen. An entry called **i-love-shopping** appears with a 6-digit code.

*Option B: the Passwords app on an iPhone (no install)*

Scanning with the camera alone won't work: the Passwords app needs a saved entry for the site
first, and it has none for this one.
1. Open **Passwords** and tap **+** to add a new entry.
2. Set **Website** to `localhost`, **User name** to your email, and **Password** to your password.
   Save.
3. Open that entry, tap **Edit** → **Set Up Verification Code**.
4. Choose **Scan QR Code** and scan the screen, or choose **Enter Setup Key** and type the long key.
5. The entry now shows a 6-digit code.

Finish on the website:
1. Type the **6-digit code** from your phone into the box. Don't type the long setup key there.
   The code changes every 30 seconds, so use the current one.
2. Click **Turn on two-factor**.
3. Write down the 8 backup codes. They are shown only once. Each one works once if you lose your
   phone.

From then on, logging in asks for the current 6-digit code after your password.

### Needs your own Google keys

CAPTCHA and Google login are built, but they're switched off by default so the project runs without
any Google accounts or keys. Both need a (free) Google account. The keys go into `.env`.

**CAPTCHA on sign-up**
1. Go to <https://www.google.com/recaptcha/admin/create> and sign in with Google.
2. Fill in the form:
   - **Label:** anything, e.g. `i-love-shopping`
   - **reCAPTCHA type:** **Challenge (v2)** → **"I'm not a robot" Checkbox**
   - **Domains:** `localhost`
3. Click **Submit**. The next page shows a **Site key** and a **Secret key**.
4. Open `.env` and set:
   ```sh
   RECAPTCHA_ENABLED=true
   RECAPTCHA_SITE_KEY=<the site key>
   RECAPTCHA_SECRET_KEY=<the secret key>
   ```

**Sign in with Google**
1. Go to <https://console.cloud.google.com> and sign in. At the top, open the project picker →
   **New project**, name it anything, and create it.
2. In the search bar, open **Google Auth Platform** and click **Get started**. Enter an app name and
   your email, choose **External**, and finish the steps.
3. Under **Audience** → **Test users**, add the Google account you'll log in with. While the app is in
   testing, only these accounts can sign in.
4. Under **Clients** → **Create client**:
   - **Application type:** **Web application**
   - **Authorised JavaScript origins:** add `https://localhost:5443` (and `http://localhost:5173` if
     you also run the website with `bun run dev`)
   - Leave **Authorised redirect URIs** empty.
5. Click **Create** and copy the **Client ID** (it ends in `.apps.googleusercontent.com`). You don't
   need the client secret.
6. Open `.env` and set:
   ```sh
   GOOGLE_CLIENT_ID=<the client ID>
   ```
   A new client can take a few minutes to start working.

**Then restart the project**

Run `./start.sh`. It rebuilds the website with the new keys, and a restart without the rebuild isn't
enough. Open the site at `https://localhost:5443`, not `127.0.0.1`, because Google only accepts the
addresses you registered. The CAPTCHA box then appears on `/register`, and the **Sign in with
Google** button on `/login`.

If you'd rather skip this, the automated tests still cover both features (`CaptchaServiceTest`,
`GoogleTokenVerifierTest`).

---

## The API

The website covers everything; these are the endpoints behind it, with a few `curl` examples.

### Accounts and the catalog

Checking by hand that an old refresh token is rejected:

```sh
# log in: the response body has the access token, the refresh token comes back as a cookie
curl -i -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your password"}'

# get a new access token; run it twice with the same cookie and the second call is rejected
curl -i -X POST http://localhost:8080/auth/refresh -b 'refresh_token=<cookie value>'

# search
curl 'http://localhost:8080/products?q=walnut&minPrice=25&maxPrice=100&sort=price_asc'
```

| Endpoint | Does |
|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/logout`, `/auth/refresh` | Sign up, log in, log out, renew the session |
| `POST /auth/forgot-password`, `/auth/reset-password` | Password reset |
| `POST /auth/2fa/setup`, `/auth/2fa/enable`, `/auth/2fa/login` | Two-factor login |
| `POST /auth/oauth/google` | Log in with Google (needs `GOOGLE_CLIENT_ID` in `.env`) |
| `GET /products`, `/products/{id}`, `/categories`, `/brands` | Browse and search the catalog |
| `GET /search/suggestions?q=` | Suggestions while typing |
| `POST /products/{id}/reviews` | Write a review (logged in) |
| `POST/PUT/DELETE /products`, `POST /products/{id}/images` | Manage products and images (admin) |


### Cart, orders and payments

| Endpoint | Does |
|---|---|
| `GET /cart`, `POST /cart/items`, `PUT/DELETE /cart/items/{productId}` | The cart (guests too, by cookie) |
| `POST /cart/merge` | Move the guest cart into the account's (logged in) |
| `POST /orders`, `GET /orders/{id}` | Place an order from the cart, read it (guests too) |
| `POST /orders/{id}/payment` | Pay a declined order again with another card |
| `POST /orders/{id}/cancel` | Cancel a paid order before packing, with a refund (guests too) |
| `GET /orders?status=&from=&to=&sort=&page=` | Your orders, filtered and sorted (logged in) |
| `GET /orders/last-delivery` | The phone and address of your last order, for the checkout (logged in) |
| `POST /payments/webhook` | Where Stripe reports payments (checked by signature) |


---

## Troubleshooting

- **Pay says payments are unavailable, or the card form says it isn't set up:** the Stripe keys are
  missing from `.env`. Add them and run `./start.sh` again (the website has to be rebuilt).
- **`stripe-cli` says *Exited*, or the order page keeps saying "Confirming your payment":** the
  Stripe CLI isn't forwarding. Run `docker compose logs stripe-cli`. A `403` with
  `more_permissions_required` means `STRIPE_SECRET_KEY` is a restricted key (`rk_test_...`): put the
  standard secret key (`sk_test_...`) in its place and run `./start.sh` again.
- **A database tool says `password authentication failed for user "app"`:** the password is
  `changeme`. The database keeps the password it was created with, so if you changed
  `POSTGRES_PASSWORD` after the first start, either use the old one or run `./start.sh reset`.
- **The browser warns the site isn't secure:** expected once, see [Setup](../README.md#run-it). The certificate is
  made on your machine rather than by a certificate authority; the connection is still encrypted.
- **`docker compose` says `APP_ENCRYPTION_KEY` is missing:** run `./start.sh` once. It makes the key.
- **Orders fail to open after changing `.env`:** the encryption key changed. Put the old one back, or
  run `./start.sh reset` to start over with an empty database.
- **Port already in use:** change `FRONTEND_PORT`, `FRONTEND_HTTPS_PORT` or `BACKEND_PORT` in `.env`
  and start again.
- **Server won't start after an update:** run `./start.sh reset` to start with a fresh database. This includes a database made before the latest migrations.
- **The admin pages only say "turn on two-factor sign-in":** expected for every staff account until it has
  2FA. Turn it on there, and the pages open.
- **`/admin` sends you back to the shop after making yourself admin:** sign out and in again. The role is read
  at sign-in.
- **`bun run a11y-check` can't find a browser:** it uses Playwright's Chromium. Run
  `bunx playwright-core install chromium-headless-shell` in `frontend/`, or point `CHROME_PATH` at a Chrome or Chromium.
- **The load test's copy of the shop is still running:** `loadtest/run.sh down` stops it and deletes its data.
- **No email arrives:** emails never leave your machine; they all appear in MailHog. If MailHog was
  down, the email waits in the `orders.emails.dlq` queue in RabbitMQ (see
  [Seeing the queues](#seeing-the-queues)).
- **Can't stay logged in:** open the site at `localhost`, not your computer's network IP.
