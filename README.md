# i-love-shopping

An online shop (B2C e-commerce) for chess, Go and backgammon products. This repository is
**Project 1 of 3**: user accounts and login, the database, and the product catalog. Cart
checkout and payments come in Project 2, the admin dashboard in Project 3.

The shop starts with about 1,770 real products (with photos and reviews) so there is something to
browse right away. The assignment brief is in [docs/ASSIGNMENT.md](docs/ASSIGNMENT.md).

> **Reviewers:** every checklist item is answered in **[REVIEW_ANSWERS.md](REVIEW_ANSWERS.md)**,
> with where to find it in the code and which tests cover it.

---

## Quick start

The only thing you need installed is **Docker**. From the project folder, run:

```sh
./start.sh
```

The first start takes a few minutes while everything is built. Then open:

| What | Where |
|---|---|
| The shop | <http://localhost:5173> |
| The API | <http://localhost:8080> |
| MailHog (inbox for every email the app sends) | <http://localhost:8025> |
| Database (PostgreSQL) | `localhost:5432`, user `app`, password `changeme`, database `iloveshopping` |

```sh
./start.sh down     # stop
./start.sh reset    # stop and wipe the database
```

Settings live in `.env` (created on first start from `.env.example`). The defaults work as-is.

---

## What works

**Accounts and security**
- Sign up and log in with email and password, or with Google
- CAPTCHA on sign-up (off by default; turn on with `RECAPTCHA_ENABLED=true` and your keys in `.env`)
- Password reset by email
- Optional two-factor login (authenticator app, plus 8 one-time backup codes)
- Short-lived access tokens kept only in memory, refresh tokens in a cookie JavaScript cannot read
- Each refresh token works exactly once; reusing an old one is rejected
- Logout invalidates both tokens; a password reset logs out every device
- Rate limiting on login, sign-up and search

**Catalog**
- Categories you can browse level by level, starting from the four games
- Search with filters for category, brand, price and rating, and suggestions while you type
- Tolerates typos (`stanton` still finds Staunton sets)
- Sorting by relevance, price, rating and newest
- Product pages with photos, specs in metric and imperial, colour/size options and reviews
- Admins can add, edit and delete products and upload images

**Not built yet**
- Checkout and payment (Project 2). The cart works, but is saved in the browser only.
- Admin screens (Project 3). Admin actions are available through the API only.
- Deleting your own review from the website (the API supports it).

---

## Database

PostgreSQL. The schema is created automatically on startup.

![Entity relationship diagram](docs/i-love-shopping-erd.png)

| Table | Holds |
|---|---|
| `users` | Accounts: email, password hash, name, role, login provider, 2FA settings |
| `categories` | The category tree; each category can have a parent |
| `brands` | Product brands |
| `products` | Name, description, price, stock, category, brand, weight and size in both unit systems, extra attributes, average rating |
| `product_images` | Photos of a product, in display order |
| `product_reviews` | A 1–5 star rating and text; one review per user per product |

Login sessions (refresh tokens, logged-out tokens, pending 2FA) are kept in **Redis**, not in the
database, and expire on their own.

### Where the product data comes from

The products are real listings from three chess, Go and backgammon suppliers. A few **Python
scripts** in [tools/catalog-import](tools/catalog-import/README.md) download the suppliers'
product pages and turn them into one SQL file, which the database loads on startup.

Why Python, when the shop itself is Java and TypeScript? The scripts are a one-off tool the
developer runs by hand, not part of the shop. Python is quick to write for this kind of
download-and-reshape job and needs no extra libraries. The finished SQL file is committed to the
repository, so **you don't need Python to run the project**. Docker is still the only thing to
install.

---

## How it is built

| Part | Technology |
|---|---|
| Website | React 18, TypeScript, Vite. Redux Toolkit for login state, React Query for server data, Axios for requests |
| Server | Java 21, Spring Boot 4, Spring Security |
| Database | PostgreSQL 16, with the schema managed by Flyway |
| Sessions | Redis 7 |
| Email (development) | MailHog |
| Everything runs in | Docker Compose: 5 containers (website, server, database, Redis, MailHog) |

The server is one application split into three areas: **users**, **login** and **catalog**. Each
area has its own code and talks to the others only through a small interface, so an area could be
split out into its own service later if it ever needs to scale on its own.

### How the main features work

**Login tokens (JWT).** Logging in returns two tokens. The *access token* is a signed JWT (library:
jjwt) that lasts 15 minutes and holds only the user id and role, no personal data. The website keeps
it in memory only, never in browser storage. The *refresh token* lasts 7 days and sits in an
`httpOnly` cookie that JavaScript can't read. After a page reload, the website uses the refresh
token to get a new access token.

**Single-use refresh tokens.** Every refresh swaps the old refresh token for a new one in Redis in
one atomic step (a small Lua script), so the same token can never be used twice, even by two
requests at the same moment.

**Logout.** The access token's id goes on a blocklist in Redis until the token would have expired
anyway, and the refresh token is deleted.

**Two-factor login (TOTP).** The standard authenticator-app method (library: `dev.samstevens.totp`).
The server creates a secret, the website shows it as a QR code, and the app then shows a new 6-digit
code every 30 seconds. After 2FA is on, logging in takes the password *and* a current code.

**CAPTCHA.** Google reCAPTCHA on sign-up. The server checks the CAPTCHA token with Google before
creating the account.

**Google login.** The website gets an ID token from Google, and the server checks that token's
signature against Google's public keys before logging the user in.

**Password reset.** The server emails a one-time link that expires. Using it sets the new password
and logs the account out everywhere.

**Validation.** Forms are checked in the browser for quick feedback, and every request is checked
again on the server, because the browser checks can be bypassed.

**Image upload.** The server checks the file's actual contents, not its name, to confirm it's a
JPEG or PNG. It then re-saves the image, which strips anything hidden inside it, and stores it under
a random name.

### Search

Search uses PostgreSQL's built-in full-text search, so no separate search engine is needed.

- **Matching.** Every product has a pre-built search index made from its name, description and
  attributes (such as wood type). A match in the name ranks above a match in the description.
- **Typos.** If nothing matches exactly, the search falls back to "similar-looking" names
  (the `pg_trgm` extension), so `stanton` still finds Staunton sets. The page says when it did this.
- **Filters.** Results can be narrowed by category, brand, price range and rating. Each filter shows
  how many results it would leave.
- **Sorting.** By relevance, price, rating or newest.
- **Suggestions.** Product names appear while you type, from 2 characters on.
- **Speed.** Indexes keep searches fast. They were tested on 50,000 products.

Search is behind one interface in the code, so it can be swapped for a dedicated search engine
(such as Elasticsearch) later without changing anything else.

---

## Tests

```sh
cd backend && mvn verify      # server: unit, API and security tests (needs Docker running)
cd frontend && bun run test   # website: form validation and login flow
```

Server tests run against a real PostgreSQL and Redis started by the test itself. They cover the
login and catalog features above, including error cases and attack attempts (SQL injection,
malicious file uploads, reusing an old refresh token).

---

## Usage guide

Most features can be tried in the website at <http://localhost:5173>. Some need your own Google
keys, and a few only exist in the API. What each checklist item should show is in
[REVIEW_ANSWERS.md](REVIEW_ANSWERS.md).

### In the website

| Try this | Where |
|---|---|
| Browse, filter and sort | `/catalog`: pick a category, brand, price or rating, change the sort |
| Search with suggestions | Search box in the header: type `wal` |
| Create an account | `/register` |
| Reset your password | `/login` → *Forgot your password?*, then open the email in [MailHog](http://localhost:8025) |
| Turn on two-factor login | `/account` (see below) |
| Write a review | Any product page, while logged in |
| Add to cart | Any product page |

To see the data behind it, connect to the database with the credentials from the Quick start table.

**Setting up two-factor login**

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
any accounts or keys. Both need a (free) Google account. The keys go into the `.env` file in the
project folder, which `./start.sh` creates on its first run.

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
   - **Authorised JavaScript origins:** add both `http://localhost` and `http://localhost:5173`
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
enough. Open the site at `http://localhost:5173`, not `127.0.0.1`, because Google only accepts the
addresses you registered. The CAPTCHA box then appears on `/register`, and the **Sign in with
Google** button on `/login`.

If you'd rather skip this, the automated tests still cover both features (`CaptchaServiceTest`,
`GoogleTokenVerifierTest`).

### Becoming an admin

There is no admin sign-up on purpose. Create a normal account, then promote it:

```sh
docker compose exec postgres psql -U app -d iloveshopping \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com'"
```

Log in again to get admin rights.

### Using the API directly

The website covers everyday use. Some things can only be done through the API, like admin actions
and checking by hand that an old refresh token is rejected.

```sh
# log in: the response body has the access token, the refresh token comes back as a cookie
curl -i -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your password"}'

# get a new access token; run it twice with the same cookie and the second call is rejected
curl -i -X POST http://localhost:8080/auth/refresh -b 'refresh_token=<cookie value>'

# search
curl 'http://localhost:8080/products?q=walnut&minPrice=80&maxPrice=200&sort=price_asc'
```

Main endpoints:

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

---

## Troubleshooting

- **Port already in use:** change `FRONTEND_PORT` or `BACKEND_PORT` in `.env` and start again.
- **Server won't start after an update:** run `./start.sh reset` to start with a fresh database.
- **No email arrives:** emails never leave your machine; they all appear in MailHog.
- **Can't stay logged in:** open the site at `localhost`, not your computer's network IP.
