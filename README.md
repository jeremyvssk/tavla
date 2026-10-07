# Tavla

An online shop for chess, Go and backgammon, with about 1,770 real products from three suppliers.

I'm Jeremy Vaask, a fullstack developer in Tallinn. I built Tavla end to end: the **Java and
Spring Boot** server, the **React and TypeScript** website, the **PostgreSQL** database and the
Python scripts that import the catalog. The whole thing starts with one command in **Docker**.

**Status: in development.** Accounts, login and the product catalog work today. A distributor is
already lined up to supply the products, so once checkout and the admin panels are done, the plan
is to launch Tavla as a real shop.

| When | What |
|---|---|
| Done | Accounts and secure login, the product catalog with search and filters, a cart saved in the browser |
| Planned for 8 October 2026 | Cart and checkout, with payments through **Stripe** |
| Planned for 14 October 2026 | Admin panels for running the shop |
| After that | Launch |

## Contents

- [What works](#what-works)
- [Worth a look](#worth-a-look)
- [How I work with Claude Code](#how-i-work-with-claude-code)
- [Run it](#run-it)
- [How it is built](#how-it-is-built)
- [Database](#database)
- [Tests](#tests)
- [Where things are](#where-things-are)

---

## What works

**Accounts and security**
- Sign up and log in with email and password, or with Google
- Two-factor login with an authenticator app, plus 8 one-time backup codes
- Password reset by email, which also logs the account out on every device
- CAPTCHA on sign-up, and rate limits on login, sign-up and search

**Catalog**
- Categories you can browse level by level, starting from the four games
- Search with filters for category, brand, price and rating, and suggestions while you type
- Typo-tolerant search: `stanton` still finds Staunton sets
- Sorting by featured, relevance, price, rating and newest
- Product pages with photos, colour and size options, reviews, and sizes in metric and imperial
- Admins can add, edit and delete products and upload images

**Not built yet**
- Checkout and payment. The cart works, but is saved in the browser only.
- Admin screens. Admin actions work through the API only.
- Deleting your own review from the website (the API supports it).

---

## Worth a look

**The catalog is real.** Python scripts in [tools/catalog-import](tools/catalog-import/README.md)
read three suppliers' public product data and turn it into one SQL file that the database loads on
startup. They also download every photo, drop the dead and blank ones, and measure how each photo
should sit on its square tile, so a photo that is cut off at one edge lines up with the tile's edge.
The generated SQL is committed, so you don't need Python to run the shop.

**A refresh token works exactly once.** Every refresh swaps the old token for a new one in Redis in
one atomic step, a short Lua script in `auth/TokenStoreService.java`, so two requests sent at the
same moment can't both use it. Replaying a used token logs the account out on every device. Tokens
are stored only as SHA-256 hashes, and the website keeps the access token in memory, never in
browser storage.

**Search without a separate search engine.** PostgreSQL full-text search ranks a match in the name
above a match in the description, and the `pg_trgm` extension catches typos. Each filter shows how
many results it would leave. `EXPLAIN ANALYZE` on 50,000 products confirmed the queries use the
indexes. Search sits behind one interface, so Elasticsearch could replace it later without changing
the code that calls it. See `catalog/search/PostgresSearchService.java`.

**Uploads are checked by content, not by name.** `catalog/ImageProcessor.java` reads the file's
real format from its first bytes and checks the image size before decoding it, because a tiny PNG
can claim to be 50,000 pixels wide and fill the server's memory. It then saves a freshly encoded
copy under a random name, so nothing hidden in the original, such as GPS location data, survives.

**Tests run against the real thing.** Integration tests start a real PostgreSQL and Redis in Docker
(Testcontainers) and call the API the way the website does. Some of them try attacks: SQL
injection, oversized input, malicious uploads, getting around rate limits and reusing an old
refresh token.

Paths above are under `backend/src/main/java/com/iloveshopping/`.

---

## How I work with Claude Code

I build with Claude Code, and the setup is in this repo for anyone to read.

- **The decisions are mine.** For each new feature I state the goal and sketch the design first:
  how data flows, where state lives, what can fail, and why this approach over the alternative.
  Claude pokes holes in it before any code is written. I direct the core logic, review every diff,
  and list the test cases before the tests are written. The point is that I can explain and defend
  every part of the code.
- **It coaches as well as builds.** Before building each feature I studied the topic behind it: JWT,
  OAuth, two-factor login, CAPTCHA, password reset, input validation, database design and search. My
  notes are in `homework/`. Claude is set up to ask me to predict an answer before it explains, and
  to hint before it solves.
- **Context on demand.** `CLAUDE.md` loads every session, so it holds only working rules and a
  routing table. Backend rules (security invariants, configuration, errors) are in
  `backend/CLAUDE.md`. The detailed checklists are three project skills, `new-endpoint`, `verify`
  and `flyway-migration`, which load only when the task matches. They started inside
  `backend/CLAUDE.md`, which loads on every turn, and were moved out to keep the context small.
- **Working rules.** Touch only what the task needs, so every changed line traces back to a request.
  Nothing is done until it has been checked. When a change moves or renames something a doc names,
  the doc is updated in the same change.
- **Passing tests aren't the finish line.** After the tests pass, the next question is what a
  passing test suite would miss: two requests racing each other, queries that slow down once the
  data grows, security holes. Those get checked separately, like the search check on 50,000
  products.

---

## Run it

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

How to try each feature, including two-factor login, the Google keys, becoming an admin and calling
the API directly, is in the [usage guide](docs/USAGE.md).

---

## How it is built

| Part | Technology |
|---|---|
| Website | React 18, TypeScript, Vite. Redux Toolkit for login state and the cart, React Query for server data, Axios for requests |
| Server | Java 21, Spring Boot 4, Spring Security |
| Database | PostgreSQL 16, with the schema managed by Flyway |
| Sessions | Redis 7 |
| Email (development) | MailHog |
| Everything runs in | Docker Compose: 5 containers (website, server, database, Redis, MailHog) |

The server is one application split into three areas: **users**, **login** and **catalog**. Each
area has its own code and talks to the others only through a small interface, so an area could be
split out into its own service later.

### Database

![Entity relationship diagram](docs/i-love-shopping-erd.png)

| Table | Holds |
|---|---|
| `users` | Accounts: email, password hash, name, role, login provider, 2FA settings |
| `categories` | The category tree; each category can have a parent |
| `brands` | Product brands |
| `products` | Name, description, price, stock, category, brand, weight and size in both unit systems, extra attributes, average rating |
| `product_images` | Photos of a product, in display order |
| `product_reviews` | A 1 to 5 star rating and text; one review per user per product |

Login sessions (refresh tokens, logged-out tokens, pending 2FA) are kept in **Redis**, not in the
database, and expire on their own.

---

## Tests

```sh
cd backend && mvn verify      # server: unit, API and security tests (needs Docker running)
cd frontend && bun run test   # website: form validation, login flow and cart
```

How the tests are organised is explained in [backend/README.md](backend/README.md#tests) and
[frontend/README.md](frontend/README.md#tests).

---

## Where things are

| Folder or file | What it is |
|---|---|
| [backend/](backend/README.md) | The server. Its README maps every feature to the file that handles it, and explains the tests |
| [frontend/](frontend/README.md) | The website. Its README lists the pages, folders and tests |
| [tools/catalog-import/](tools/catalog-import/README.md) | The Python scripts that build the catalog |
| [docs/](docs/) | The [usage guide](docs/USAGE.md), the original brief and the ERD image |
| `CLAUDE.md`, `claude-docs/`, `.claude/` | Instructions and notes for Claude Code |
| `homework/` | My study notes for the topics in this project |
| `start.sh`, `docker-compose.yml`, `.env.example` | Startup script, container setup, and the list of settings |
