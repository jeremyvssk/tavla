# Performance analysis report

**How it was tested.** [k6](https://k6.io) runs in Docker against a separate copy of the whole shop (nginx,
server, PostgreSQL, Redis, RabbitMQ), started by `loadtest/run.sh` with its own data, so the numbers include
TLS, the proxy and the real database. Each virtual user is one shopper who pauses 1 to 8 seconds between
pages, like someone reading, and repeats one of four journeys ([loadtest/shop.js](../loadtest/shop.js)):

| Journey | Share | Steps |
|---|---:|---|
| Browse | 45% | Categories, a shelf's listing, a product and its reviews |
| Search | 30% | Suggestions while typing, the results, a product |
| Buy (as a guest) | 15% | A product, add to cart, the cart, delivery options, place the order |
| Sign in | 10% | Sign in, the profile, my orders |

Each step count runs for a minute, and all its users start in the same second (the worst case: a sale
email sent to everyone at once). A *transaction* is one finished journey; requests are counted too.
Photos aren't requested: nginx serves them from disk without touching the server.

**Machine.** One laptop: AMD Ryzen 7 4800H (8 cores, 16 threads), Docker with 3.7 GB of memory. The server
container is limited to **2 CPU cores and 1 GB**, like a small cloud machine; the database and k6 itself
are not limited, and k6 shares the laptop's CPU with the shop. Payments go to a stand-in for Stripe (Stripe's
test mode allows 25 requests a second), and an unpaid order releases its stock after 2 minutes instead of 30.

**Results** (after the fix below; [loadtest/results/2026-10-12-2228](../loadtest/results/2026-10-12-2228/summary.md)
and [2026-10-12-2244](../loadtest/results/2026-10-12-2244/summary.md)). Server CPU is the average once every
user has arrived:

| Users | Requests/s | Journeys/s | Median | 90% within | 95% within | Failed requests | Journeys OK | Server CPU | Server memory | Database CPU |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 4.1 | 0.7 | 26 ms | 100 ms | 108 ms | 0% | 100% | 12% | 41% | 0.0 cores |
| 25 | 9.4 | 1.8 | 13 ms | 47 ms | 77 ms | 0% | 100% | 12% | 42% | 0.1 |
| 50 | 18.0 | 3.7 | 9 ms | 39 ms | 68 ms | 0% | 100% | 15% | 43% | 0.1 |
| 100 | 34.9 | 7.3 | 8 ms | 37 ms | 82 ms | 0% | 100% | 19% | 45% | 0.2 |
| 200 | 69.6 | 14.5 | 7 ms | 38 ms | 99 ms | 0% | 100% | 29% | 47% | 0.3 |
| 300 | 102.3 | 21.4 | 7 ms | 91 ms | 360 ms | 0.19% | 99.6% | 37% | 48% | 0.5 |
| 400 | 135.7 | 28.5 | 6 ms | 96 ms | 425 ms | 0.07% | 99.8% | 38% | 49% | 0.6 |
| 600 | 197.6 | 41.9 | 6 ms | 98 ms | 1.2 s | 0.10% | 99.7% | 55% | 52% | 0.8 |
| 800 | 234.3 | 49.6 | 28 ms | 1.6 s | 2.6 s | 0.78% | 98.0% | 74% | 56% | 0.9 |
| 1200 | 289.2 | 58.3 | 691 ms | 2.9 s | 4.6 s | 1.69% | 95.6% | 86% | 62% | 1.1 |
| 1600 | 303.2 | 58.1 | 1.5 s | 5.4 s | 8.9 s | 2.18% | 94.5% | 88% | 63% | 1.3 |

Every failed request was a **409**: the shop refusing to put in a cart, or sell, stock that other test buyers'
unpaid orders were holding. There was no server error and no timeout at any step. The buy journeys pick from
under 200 products and every unpaid test order holds its items for 2 minutes, so the more buyers, the more of
those products are held: a limit of the test data, not of the server.

**Against the targets:**

| Target | Result |
|---|---|
| 90% of requests within 2 seconds | Met up to 800 users (1.6 s); 2.9 s at 1,200 |
| At least 50 users without slowing down | 50 users: 95% within 68 ms, the same as at 10 |
| At least 10 transactions a second at peak | 14.5 journeys (70 requests) a second at 200 users; at most 58 journeys (300 requests) |
| 98% of transactions succeed | Met up to 800 users (98.0%) |
| Error rate under 5% | Under 2.2% at every step |

**What the test identified:**
- **Most users before responses take over 5 seconds:** about **1,200**. There, 95% of requests finish within
  4.6 s; at 1,600 it is 8.9 s. Throughput also stops growing there (289 → 303 requests a second): the server is
  full.
- **Throughput:** about **50 journeys (230 requests) a second** with good response times (800 users), at most
  58 journeys (300 requests) a second.
- **Normal and peak load:** a specialist shop like this one expects tens of shoppers at once on a normal day;
  we take **25 to 50** as normal and **200** as a sale peak. Both are far inside the limits: at 200 users 95% of
  requests take under 0.1 s and the server uses under a third of its CPU.
- **CPU or memory over 90%:** the server's 2 cores reach **100% for the first seconds of every step from 300
  users** (everyone arriving at once), and average **86 to 88% from 1,200 users** on. Memory never passes 63% of
  its 1 GB: memory is not the limit.

**Bottlenecks, with the evidence:**
1. **Two-letter search suggestions (fixed).** Sampling the database's running queries at 400 users showed the
   suggestion query in 17 of 23 samples. Two letters are too short for the trigram index (a trigram is three
   letters), so each one read all 1,772 products: about 10 ms of database CPU, against 0.6 ms from three letters
   (`EXPLAIN ANALYZE`). The search box asks at two letters, so every search did this. **Fix:** two-letter answers
   are kept in memory for 30 seconds (`PostgresSearchService.suggest`, tested in `SearchIT`). After it the query
   was in 5 of 27 samples, suggestions' 95th percentile at 600 users fell from 1.6 s to 0.5 s, and the database's
   CPU at 600 users fell from 1.0 to 0.8 cores ([before](../loadtest/results/2026-10-12-1830/summary.md)).
2. **Everyone arriving at once.** At 600 users, nearly all slow requests fall in the first 10 seconds; after the
   first 15 seconds 95% of requests finish within 85 ms. The burst queues work on the server's 2 cores and its
   10 database connections. **Proposed:** run two or more server containers behind nginx (they keep no state of
   their own, so any of them can answer any request), and cache what every first page asks for, the category
   tree, for a few seconds.
3. **Sign-in.** The slowest request once the burst is over (95% within 1.1 s at 600 users, against 19 ms for
   suggestions): checking a password with bcrypt is slow on purpose, and costs CPU on the 2 cores. Making it
   cheaper would make stolen hashes easier to crack, so the fix is **more CPU for the server**, not a weaker hash.
4. **Listing counts.** The next most frequent database work: each listing counts brands, prices and ratings for
   its filters, four queries per page. **Proposed:** keep the counts of an unfiltered shelf for a minute, since
   they change only when products do.

**Images and static files (CDN).** Not part of the run: nginx serves them from disk, cached for a year. In
production a CDN in front of nginx would serve them from near the shopper; see
[How it works → Images](ARCHITECTURE.md#images-and-search-engines).

To run it yourself: `loadtest/run.sh` (the steps 10 to 400), or `loadtest/run.sh 50 800` for chosen steps; then
`loadtest/run.sh down`. Each run writes its own folder in `loadtest/results/`.
