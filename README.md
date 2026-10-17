# Tavla

An online shop for chess, Go and backgammon, with about 1,770 real products from three suppliers, and an
**admin area** where staff run the products, orders, delivery, reviews and users.

I'm Jeremy Vaask, a full-stack developer in Tallinn. I built Tavla end to end: the **React and TypeScript**
website and admin area, the **Java and Spring Boot** server, the **PostgreSQL** database, card payments
through **Stripe** and **RabbitMQ**, and the Python scripts that import the catalog. The whole thing starts
with one command in **Docker**.

**Status: finished.** I built it at kood/Jõhvi in three parts: accounts and the catalog, then the cart,
checkout and payments, then the admin area and the work a real shop needs (security, accessibility, load
testing). A distributor is lined up to supply the products, and the plan is to launch Tavla as a real shop.

## Contents

- [What works](#what-works)
- [Worth a look](#worth-a-look)
- [How I work with Claude Code](#how-i-work-with-claude-code)
- [Run it](#run-it)
- [How it is built](#how-it-is-built)
- [Tests](#tests)
- [Where things are](#where-things-are)

---

## What works

**Shopping**
- Catalog and search with filters, sorting, suggestions while you type, and typo tolerance: `stanton`
  still finds Staunton sets
- Product pages with photos, colour and size options, reviews, and sizes in metric and imperial
- A cart saved on the server, for guests and accounts. Logging in moves the guest cart into the account
- One-page checkout, as a guest or signed in, with the address checked against the city
- Card payments through Stripe's test mode, including the bank's security check (3-D Secure)
- Your orders, with filters and status history. Cancel before packing and the card is refunded
- An email for each step: paid, declined, shipped, delivered, cancelled, refunded

**Your account**
- Sign up with email and password or with Google, with CAPTCHA on sign-up
- Two-factor sign-in with an authenticator app, plus 8 one-time backup codes
- Password reset by email, which also logs the account out on every device
- Profile, up to 10 saved addresses, a download of everything the shop keeps about you, and deleting the
  account

**The admin area**
- Three staff roles, each with only what it needs: **Support** (orders, refunds, reviews), **Sales**
  (products, categories, bulk upload, delivery), **Admin** (everything, plus users and the audit log)
- Every staff member must use two-factor sign-in, and can't turn it off
- Orders from packing to delivered, refunds, delivery prices, review moderation, bulk upload from CSV or
  JSON, users and roles, and an **audit log** of every staff change that nobody can edit

**Reviews**
- Star ratings and text reviews, only from shoppers whose order with the product was delivered
- "Helpful" votes, sorting by most helpful, and reporting a review to staff

**Security, accessibility and speed**
- Personal and payment details are **encrypted in the database** (AES-256-GCM)
- HTTPS, and rate limits with token buckets on sign-in, sign-up and search
- An automated **WCAG 2.1 A** check (axe-core, colour contrast, 200% zoom) finds nothing on any shop or
  account page, and everything works with the keyboard
- Uploaded photos are saved in three extra sizes, so a phone loads the small ones
- Load tested from 10 to 1,600 shoppers at once: 90% of requests finish within 2 seconds up to 800
  ([report](docs/PERFORMANCE.md))

**Not built yet**
- Parcel tracking with a real courier: the parcel number is shown, but nothing asks the courier
- Contact messages are only emailed; there is no inbox for them in the admin area
- The demo catalog's photos are linked from the suppliers' sites, so only uploaded photos get the three
  sizes

---

## Worth a look

**Roles are checked by the server, not the menu.** One table, `auth/StaffArea.java`, says which role may
use which part of the admin area, and every admin request is checked against it. Changing someone's role
ends their open sessions, so the new role applies at once. The audit log is protected by a database
trigger that refuses any edit or delete for 12 months, so not even the server can rewrite it.

**Two people can't buy the last item.** Placing an order takes the stock with one "only if there's enough"
update (`catalog/ProductRepository.java`) inside the same transaction that creates the order. When two
shoppers buy the last one at the same moment, exactly one succeeds and the other is told it sold out.

**An order is paid only when Stripe says so.** The browser never decides. Stripe calls a webhook, the
server checks its signature and puts the result on a RabbitMQ queue, and a listener updates the order.
Each message is applied once even if Stripe sends it twice, and one that keeps failing waits in a dead
letter queue instead of being lost. Emails go through a queue of their own, so a broken mail server never
holds up a payment.

**Encrypted, but you can still sign in.** An encrypted email can't be looked up, so the database also keeps
a keyed hash of it, and that is what sign-in searches. A changed byte in an encrypted column is detected
instead of decrypted to nonsense. See `crypto/FieldCipher.java`.

**A refresh token works exactly once.** Every refresh swaps the old token for a new one in Redis in one
atomic step, a short Lua script in `auth/TokenStoreService.java`, so two requests sent at the same moment
can't both use it.

**The load test found a real bottleneck.** At 400 shoppers, most of the database's time went to search
suggestions after only two letters, which are too short for the typo index. Keeping those answers for 30
seconds cut the slowest 5% of suggestions at 600 shoppers from 1.6 s to 0.5 s. The full report, with what
would come next, is in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

**The catalog is real.** Python scripts in [tools/catalog-import](tools/catalog-import/README.md) read three
suppliers' public product data and turn it into one SQL file that the database loads on startup. They also
download every photo, drop the broken ones, and measure how each photo should sit on its square tile.

Paths above are under `backend/src/main/java/com/iloveshopping/`.

---

## How I work with Claude Code

I build with Claude Code, and the setup is in this repo for anyone to read.

- **The decisions are mine.** For each new feature I state the goal and sketch the design first: how data
  flows, where state lives, what can fail, and why this approach over the alternative. Claude pokes holes in
  it before any code is written. I direct the core logic, review every diff, and list the test cases before
  the tests are written. The point is that I can explain and defend every part of the code.
- **It coaches as well as builds.** Before building each feature I studied the topic behind it: JWT, OAuth,
  two-factor login, CAPTCHA, password reset, input validation, database design and search. My notes are in
  `homework/`. Claude is set up to ask me to predict an answer before it explains, and to hint before it
  solves.
- **Context on demand.** `CLAUDE.md` loads every session, so it holds only working rules and a routing
  table. Backend rules (security invariants, configuration, errors) are in `backend/CLAUDE.md`. The detailed
  checklists are three project skills, `new-endpoint`, `verify` and `flyway-migration`, which load only when
  the task matches.
- **Working rules.** Touch only what the task needs, so every changed line traces back to a request.
  Nothing is done until it has been checked. When a change moves or renames something a doc names, the doc
  is updated in the same change.
- **Passing tests aren't the finish line.** After the tests pass, the next question is what a passing test
  suite would miss: two requests racing each other, queries that slow down once the data grows, security
  holes. Those get checked separately, like the load test above.

---

## Run it

The only thing you need installed is **Docker** with Docker Compose. Java, Node, the database, RabbitMQ and
the Stripe CLI all run inside containers.

To pay with a card you need two test keys from a free **Stripe account** (an email address is enough, and
test mode moves no money). Without them the shop still runs, but placing an order says payments are
unavailable.

**1. Get your Stripe test keys.** Sign up at <https://dashboard.stripe.com/register> and skip the business
questions. In **test mode**, open **Developers → API keys** and copy the **Publishable key** (`pk_test_...`)
and the **Secret key** (`sk_test_...`). Use the standard secret key: a restricted one (`rk_test_...`) doesn't
work.

**2. Put them in `.env`.**

```sh
cp .env.example .env
```

```sh
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_SECRET_KEY=sk_test_...
```

Leave `STRIPE_WEBHOOK_SECRET` and `APP_ENCRYPTION_KEY` empty: the start script fills them in.

**3. Start.**

```sh
./start.sh
```

The first start takes a few minutes while everything is built. Then open:

| What | Where |
|---|---|
| The shop | <https://localhost:5443> |
| The admin area | <https://localhost:5443/admin> (needs a staff account: [becoming an admin](docs/USAGE.md#becoming-an-admin)) |
| MailHog (every email the shop sends) | <http://localhost:8025> |
| RabbitMQ (the message queues) | <http://localhost:15672>, user `app`, password `changeme` |
| The API | <http://localhost:8080> |

The certificate is made on your machine, so the browser warns the first time. Choose **Advanced → Continue
to localhost** once; the connection is still encrypted.

```sh
./start.sh down     # stop
./start.sh reset    # stop and wipe the database
```

How to try each feature, including the admin area, test cards, two-factor sign-in and Google sign-in, is
in the [usage guide](docs/USAGE.md). If something doesn't start, see
[troubleshooting](docs/USAGE.md#troubleshooting).

---

## How it is built

| Part | Technology |
|---|---|
| Website | React 18, TypeScript, Vite. Redux Toolkit and React Query for state, Axios for requests, Stripe.js for the card form |
| Server | Java 21, Spring Boot 4, Spring Security, stripe-java |
| Database | PostgreSQL 16, with the schema managed by Flyway |
| Sessions and rate limits | Redis 7 |
| Message queue | RabbitMQ 4 |
| Email (development) | MailHog |
| Payments | Stripe (test mode), with the Stripe CLI forwarding its webhooks |
| Everything runs in | Docker Compose: 7 containers |

The server is one application split into areas: users, login, catalog, cart, orders, payments, delivery,
audit, overview and support. Each area talks to the others only through a small interface.

How login, search, payments, the staff area and encryption work, with the database diagrams:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Tests

The tests need Docker running (they start their own PostgreSQL, Redis and RabbitMQ), plus Java 21 and
Maven 3.9+ for the server and [Bun](https://bun.sh) for the website. The shop doesn't have to be running.

```sh
cd backend && mvn verify       # server: 444 tests, a few minutes
cd frontend && bunx vitest run # website: 132 tests, seconds
```

**Latest run (2026-10-12): 576 tests, all passing.** Server tests call the API the way the website does,
against a real database. Stripe and the mail server are replaced by stand-ins, so no keys or internet are
needed. Some tests try attacks: SQL injection, oversized input, malicious uploads, getting around rate
limits, reusing an old refresh token, and staff reaching pages their role doesn't allow.

How the tests are organised: [backend/README.md](backend/README.md#tests) and
[frontend/README.md](frontend/README.md#tests).

---

## Where things are

| Folder or file | What it is |
|---|---|
| [backend/](backend/README.md) | The server. Its README maps every feature to the file that handles it, and explains the tests |
| [frontend/](frontend/README.md) | The website. Its README lists the pages, folders and tests; the admin area is in `frontend/src/admin/` |
| [docs/](docs/) | The [usage guide](docs/USAGE.md), [how it works](docs/ARCHITECTURE.md), the [load test report](docs/PERFORMANCE.md), and example bulk upload files in `docs/examples/` |
| [tools/catalog-import/](tools/catalog-import/README.md) | The Python scripts that build the demo catalog |
| [loadtest/](loadtest/) | The load test: k6 script, the separate stack it runs against, and each run's results |
| `CLAUDE.md`, `claude-docs/`, `.claude/` | Instructions and notes for Claude Code |
| `homework/` | My study notes for the topics in this project |
| `start.sh`, `docker-compose.yml`, `.env.example` | Startup script, container setup, and the list of settings |
