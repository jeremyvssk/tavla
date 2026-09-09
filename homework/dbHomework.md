# Database — Study Material

Goal: defend the DB choice, explain ACID, read an ERD, demo a search query, walk through migrations and locking. The rubric in [README.md](../README.md) tests these directly.

## Table of Contents

1. [Why PostgreSQL](#1-why-postgresql)
2. [ACID](#2-acid)
3. [ERD — 7 components](#3-erd--7-components)
4. [Full-text search](#4-full-text-search)
5. [Concurrency & locking](#5-concurrency--locking)
6. [Scaling: read replicas and Redis cache](#6-scaling-read-replicas-and-redis-cache)
7. [Design quality the reviewer checks](#7-design-quality-the-reviewer-checks)
8. [Migrations & Flyway](#8-migrations--flyway)
9. [Cheat-sheet answers for the oral](#cheat-sheet-answers-for-the-oral)
10. [Resources](#resources)

---

## 1. Why PostgreSQL

- E-commerce data is structured and relational → foreign keys fit naturally.
- ACID by default → payments can't half-succeed.
- Scales via indexes (B-tree, GIN), HikariCP connection pool, read replicas, partitioning, Redis cache.
- JSONB → flexible product attributes without giving up SQL.
- Built-in full-text search → no Elasticsearch needed in P1.

## 2. ACID

| Letter | Means | E-commerce example |
|---|---|---|
| **A**tomicity | All steps succeed or none do | "Place order" = deduct stock + charge card + create order. Card fails → stock not deducted. |
| **C**onsistency | DB always in a valid state | A review's `user_id` can't point to a deleted user. |
| **I**solation | Concurrent transactions don't interfere | Two customers buy the last unit → only one wins. |
| **D**urability | Committed survives crashes | After 200 OK, the order is on WAL/disk. |

Most likely oral question: *"two customers buy the last item simultaneously — what stops both succeeding?"* → **transactions + `SELECT ... FOR UPDATE`** (see §5).

## 3. ERD — 7 components

| Component | Definition | In your schema |
|---|---|---|
| Entity | A table | `users`, `products`, `categories` |
| Attribute | A column | `products.price`, `users.email` |
| Primary Key | Uniquely identifies a row | `users.id` (UUID) |
| Foreign Key | Reference to another PK | `products.category_id → categories.id` |
| Relationship | How tables connect | `categories` 1—N `products` |
| Cardinality | How many (1, N, M) | One category → many products = 1:N |
| Modality | Mandatory (1) or optional (0) | Product *must* have a category. Brand is optional. |

### Mermaid symbol cheat-sheet

Each side of a relationship is **two characters** encoding cardinality AND modality together. Rule: character closer to the entity = MAX, character closer to the line = MIN.

| Symbol | Means |
|---|---|
| `\|` | one |
| `o` | zero |
| `{`, `}` | many (crow's foot) |

| Pair | Decoded | Plain English |
|---|---|---|
| `\|\|` | max=1, min=1 | exactly one, mandatory |
| `o\|` | max=1, min=0 | zero or one (optional) |
| `o{` | max=many, min=0 | zero or many |
| `\|{` | max=many, min=1 | one or many (mandatory) |

Worked example — `categories ||--o{ products`:
- Left `||` (categories side) = a **product** MUST have exactly one category (mandatory).
- Right `o{` (products side) = a **category** can have zero or many products (optional).
- The symbol on one side describes what the *other* entity sees.

Apply to `users ||--o{ product_reviews`:
- `||` → a review MUST have exactly one author (`user_id NOT NULL` enforces it).
- `o{` → a user can have zero or many reviews (most users have zero).

## 4. Full-text search

### The problem

`WHERE name LIKE '%wireless headphones%'` fails three ways:
1. **Word order** — "Headphones, Wireless Bluetooth" doesn't match.
2. **Word variations** — "headphone" misses "headphones".
3. **Performance** — `LIKE '%...%'` can't use an index.

### The pipeline

`to_tsvector('english', 'The Running Shoes for Athletes')` runs four steps in order:

1. **Tokenize** → `['The', 'Running', 'Shoes', 'for', 'Athletes']` with positions 1–5.
2. **Lowercase** → all lowercase.
3. **Drop stop words** → removes `the` (1), `for` (4).
4. **Stem** → `running → run`, `shoes → shoe`, `athletes → athlet` (not "athlete" — the stemmer is mechanical, it strips `-es` then trailing `e`).

Result: `'athlet':5 'run':2 'shoe':3` (positions reflect the *original* input).

- **Why "athlete" matches**: query goes through the same pipeline, stems to `athlet`, matches the row's `athlet:5`.
- **Why "the" doesn't appear**: dropped as a stop word both at index time and query time. Nothing to match against.

### tsquery functions

| Function | Behavior | When to use |
|---|---|---|
| `to_tsquery` | Strict syntax (`&`, `\|`, `!`). Throws on raw input. | Programmatic queries you build yourself. |
| `plainto_tsquery` | Plain words, all ANDed. Safe for raw input. | Simple search box. |
| `websearch_to_tsquery` | Google-style: quoted phrases, `-exclude`. | **Your search bar.** |

Matching uses `@@`:
```sql
WHERE search_vector @@ websearch_to_tsquery('english', 'wireless headphones')
```

### Why `search_vector` is a generated column

```sql
search_vector tsvector GENERATED ALWAYS AS (
  to_tsvector('english',
    coalesce(name, '') || ' ' || coalesce(description, ''))
) STORED
```

`GENERATED ALWAYS AS (...) STORED` means **Postgres auto-recomputes the value every time `name` or `description` changes**. You never write to it from Java.

If it were a regular column, every `UPDATE products SET name = ...` would need to also recompute the vector. The day someone forgets, search silently goes stale: a product renamed "Headphones" → "Earbuds" still matches searches for "headphones". **No error, just lying results.** Generated columns make that bug class impossible.

### Why GIN, not B-tree

- **B-tree** sorts rows by a single comparable key. Great for `=` and ranges. But a tsvector is a *set* of lexemes — you can't sort rows by a set.
- **GIN** is an inverted index. Instead of `row → set of tokens`, it stores `token → list of rows`:

```
run     → [5, 12, 89, 204, ...]
shoe    → [5, 12, 47, 88, ...]
athlet  → [5, 78, 102, ...]
```

Same data structure as a textbook's back-of-index, or how Google indexes the web. Lookup is roughly constant in table size.

### Faceted search

Amazon-style sidebar = AND more WHERE clauses onto the FTS query:

```sql
SELECT id, name, price, average_rating FROM products
WHERE search_vector @@ websearch_to_tsquery('english', 'headphones')
  AND brand_id = $1
  AND price BETWEEN $2 AND $3
  AND average_rating >= 4
ORDER BY ts_rank(search_vector,
                 websearch_to_tsquery('english', 'headphones')) DESC;
```

- `ts_rank` returns a relevance score — matches in `name` count more than matches in long `description`.
- Indexes needed: GIN on `search_vector`, B-tree on every facetable column (`brand_id`, `category_id`, `price`, `average_rating`).

### Prefix matching (`head` → `headphones`)

Stemming doesn't help — `head` stems to `head`, `headphones` stems to `headphon`. Different lexemes, no match.

Postgres-native fix: the **`:*` operator**:

```sql
WHERE search_vector @@ to_tsquery('english', 'head:*')
```

Matches any lexeme starting with `head`. GIN supports it efficiently. `websearch_to_tsquery` does **not** add `:*` automatically — for autocomplete you split the input and append `:*` to each token manually.

Elasticsearch is only needed for typo tolerance, synonyms, multi-language analyzers, or >50M rows. Not for plain prefix matching.

### Gotchas

- **Stop words are language-dependent.** `'english'` drops `the`, `and`, `of`. A brand "The North Face" indexes as just `north face` in English mode.
- **Stemming is mechanical, not semantic.** "Better" does NOT stem to "good". For synonyms you need ES or a synonym dictionary.
- **JSONB attributes aren't searched by default.** Include them in the generated column expression to make them searchable.

## 5. Concurrency & locking

### The race condition (no locks)

Stock starts at **1**. Two users hit "Buy" simultaneously:

| Step | Tx A | Tx B | Stock |
|---|---|---|---|
| 1 | BEGIN; SELECT stock → **1** | | 1 |
| 2 | | BEGIN; SELECT stock → **1** | 1 |
| 3 | Checks `stock >= 1` ✓ | Checks `stock >= 1` ✓ | 1 |
| 4 | UPDATE stock = stock - 1 | | 0 |
| 5 | | UPDATE stock = stock - 1 | **-1** |
| 6 | INSERT order; COMMIT | INSERT order; COMMIT | -1, 2 orders |

Both read before either wrote → both passed the check → stock now negative, two orders shipped, one customer is going to be unhappy.

### `SELECT ... FOR UPDATE` (pessimistic locking)

- **Lock type**: row-level **exclusive lock**.
- **Scope**: only the rows the SELECT returns. Other rows untouched.
- **Released**: on COMMIT or ROLLBACK. No manual unlock.
- **Effect on others**:
  - Plain `SELECT` (no FOR UPDATE) — works, sees pre-lock value (MVCC).
  - Another `SELECT FOR UPDATE` on the same row — blocks.
  - `UPDATE` / `DELETE` on the same row — blocks.

Replay with the lock:
- Tx A: `SELECT stock FOR UPDATE` → locks row, reads 1.
- Tx B: `SELECT stock FOR UPDATE` → **blocks**.
- Tx A: UPDATE, INSERT, COMMIT → lock released.
- Tx B: unblocks, re-reads stock = 0, check fails → returns "out of stock".

Only one customer succeeds. Stock never goes negative.

### Optimistic vs pessimistic

| | Pessimistic | Optimistic |
|---|---|---|
| Bet | Conflicts common | Conflicts rare |
| Mechanism | Lock row up front | No lock; check version at write time |
| SQL | `SELECT ... FOR UPDATE` | `UPDATE ... WHERE version = N` (0 rows → conflict, retry) |
| Conflict handling | Other tx waits | Failed write retries |
| Best for | Hot rows, short tx (stock decrement) | Long tx, rare conflicts (profile edit) |
| JPA / Hibernate | `@Lock(PESSIMISTIC_WRITE)` | `@Version` annotation |

Hybrid threshold ideas ("use optimistic above stock=10") introduce a new race on the threshold check itself. Pessimistic locks are so cheap that the optimization isn't worth the complexity. Real high-scale e-commerce uses inventory reservation queues or sharded stock, not adaptive locking.

## 6. Scaling: read replicas and Redis cache

### Read replica

A second Postgres server kept in sync with the primary by streaming replication (~ms lag).
- Writes go only to primary.
- Reads can go to either.
- Adding replicas multiplies read capacity.
- **Catch — replication lag**: don't use replicas for reads that need the latest write ("did the order I just placed succeed?").

### Redis as cache

In-memory key-value store. Pattern:

```
1. Check Redis: GET product:42
2. Hit → return immediately.
3. Miss → query Postgres → store in Redis with TTL → return.
```

**Catch — staleness**: cached data is a copy. After UPDATE in Postgres, Redis still has the old value until expiry/invalidation. Only cache things where slight staleness is OK.

### Q10 — Catalog browse vs. order placement

| | Read replica | Redis cache |
|---|---|---|
| Catalog browse (read-heavy) | ✓ helps (spread reads) | ✓ helps more (hot reads served from RAM) |
| Order placement (write-heavy) | ✗ writes only go to primary | ✗ stock must be fresh |

Order placement scales with different tools: connection pooling, sharded stock, reservation queues — not replicas.

### Why Redis is cheaper than a replica

| | Postgres replica | Redis |
|---|---|---|
| Workload | Full SQL (joins, filters, sorts) | Hash lookup only |
| Storage | Mostly disk, some RAM | Entirely RAM |
| Latency | 1–5 ms | 0.1–0.5 ms |
| Server cost | Same hardware class as primary | Tiny instance |

### What Redis is used for in *this* project

Auth state (per CLAUDE.md), not catalog caching:
- `refresh_token:{hash}` → user_id (TTL 7 days)
- `refresh_tokens_user:{userId}` → Set of hashes (revoke-all on password reset)
- `token_blocklist:{jti}` → "1" (TTL = remaining access token lifetime)
- `2fa_pending:{userId}` → challenge marker (TTL 5 min)

Catalog caching is a P3 concern.

## 7. Design quality the reviewer checks

- Normalized to 3NF (no duplicated brand names across products).
- DB-level constraints: `NOT NULL`, `UNIQUE`, `CHECK`, `FOREIGN KEY`.
- Indexes on every FK and every filter/sort column.
- Flyway migrations are **append-only** — never edit `V1__init.sql`, add `V2__...sql`.
- UUIDs as PKs (no sequential ID leakage).

## 8. Migrations & Flyway

### Concept

A **schema** is the shape of your DB (tables, columns, types, constraints, indexes). The shape changes over time, but the data already in the shape has to survive.

You have **one codebase** but **multiple DB instances** (your laptop, teammate's laptop, staging, prod). Each instance has its own Postgres process and its own data — they share the same schema because the same migrations ran against all of them.

### What Flyway does on startup

1. Connects to Postgres.
2. Reads `flyway_schema_history`.
3. For each `V*.sql` file in `src/main/resources/db/migration/`:
   - **Already ran?** Recompute checksum, compare with stored. Mismatch → **abort**.
   - **Hasn't run?** Execute in a transaction, record success + checksum.
4. Hand control to Spring Boot.

### `flyway_schema_history` columns

| Column | What it is | Used at startup for |
|---|---|---|
| `version` | Number from filename (V1, V2…) | Compare against files on disk; anything new gets executed. |
| `checksum` | Fingerprint of the file at time of execution | Recompute current checksum, compare. Mismatch → tampered → abort. This is what enforces append-only. |
| `success` | Did this migration finish? | If `false`, DB is half-applied; abort, needs `flyway repair`. |

**Checksum** = a short fingerprint (CRC32) of the file's contents. Same content → same number. One character changed → totally different number. Lets Flyway detect tampering without comparing files byte-by-byte.

### Append-only rule — the rename scenario

You shipped `V3__add_user_role.sql`. Now you realize it should be `role_id` not `role`.

**Wrong: edit V3 in place.**
- **Your laptop**: V3 already ran. New file has different checksum → Flyway aborts with `Migration checksum mismatch for migration version 3`. App refuses to boot.
- **Production**: V3 is marked done → your edit **never runs**. Prod schema unchanged. Your Java code now references the new name → crashes on every request.

**Right: add V4.**
```sql
-- V4__rename_user_role_to_role_id.sql
ALTER TABLE users RENAME COLUMN role TO role_id;
```
Update Java code. Deploy.

**Why keep V3 around?** It's a small SQL file in git, not duplicated data. Deleting it breaks Flyway: history says V3 ran, file is missing → "Detected resolved migration not applied" error.

### Expand → migrate → contract (zero-downtime rename)

Renaming `rating` → `ranking` on a live DB without breaking requests:

1. **Expand** (migration V_n): `ALTER TABLE ADD COLUMN ranking; UPDATE SET ranking = rating;`. Both columns exist with identical data. Code unchanged. Deploy.
2. **Migrate** (code change): code writes to both, reads from `rating`. Deploy. Both stay in sync.
3. **Switch** (code change): code reads from `ranking`, still writes to both. Deploy. Rollback safe.
4. **Contract** (migration V_n+1): code stops writing to `rating`; new migration drops the column. Deploy.

At every step, the schema is compatible with both old and new code → no broken requests, every deploy is rollback-safe.

For a school project you almost never need this — wipe the dev DB and re-run. The pattern matters when real users have data you can't lose.

---

## Cheat-sheet answers for the oral

- **Why Postgres over Mongo?** → Structured relational data + ACID + JSONB for flexibility + built-in FTS.
- **What is ACID?** → Atomicity (all-or-nothing), Consistency (constraints hold), Isolation (concurrent tx don't interfere), Durability (committed survives crashes).
- **Two customers buy the last item — what prevents both succeeding?** → Transactions + row-level locking via `SELECT ... FOR UPDATE`. First Tx locks the row, second blocks, then re-reads stock=0 and fails.
- **Optimistic vs pessimistic locking?** → Pessimistic locks the row up front (`SELECT FOR UPDATE`) — use when conflicts are common and tx short. Optimistic skips the lock and checks a version column at write time — use when conflicts are rare or tx long.
- **What does `||--o{` mean?** → Left `||` = exactly one mandatory. Right `o{` = zero or many. Each pair encodes cardinality (max) and modality (min) together.
- **How does `to_tsvector` work?** → Tokenize → lowercase → drop stop words → stem to root lexemes. Output is a set of `lexeme:position` pairs.
- **Why is `search_vector` generated?** → Postgres auto-recomputes when `name`/`description` change. If it were regular, forgetting to update it silently breaks search.
- **Why GIN, not B-tree?** → B-tree sorts by a single value; tsvector is a set. GIN inverts to `token → rows`, like a book's back-of-index. Lookup is roughly constant in table size.
- **Prefix matching?** → `to_tsquery('head:*')`. `websearch_to_tsquery` doesn't add `:*` automatically.
- **When does a read replica help?** → Read-heavy endpoints (catalog browse). Not write-heavy paths (order placement) — writes only go to primary.
- **Why is Redis cheaper than a replica?** → Hash lookups from RAM, not full SQL. A tiny instance handles huge traffic.
- **Why are migrations append-only?** → Flyway tracks a checksum of each applied migration. Editing changes the checksum → Flyway aborts. On prod, the edit never runs because the migration is already marked done.
- **How do you rename a column safely on a live DB?** → Expand-migrate-contract: add new column, backfill, switch reads, drop old. Each step is its own migration + deploy, rollback-safe.

---

## Resources

**Videos**
- [Lec-88: ACID Properties (Gate Smashers)](https://www.youtube.com/watch?v=-GS0OxFJsYQ) — tight, exam-focused.
- [ERD Tutorial Part 1](https://www.youtube.com/watch?v=xsg9BDiwiJE) — full overview.
- [ERD Cardinality](https://www.youtube.com/watch?v=u2QqjofJvGo) — focuses on min/max cardinality.
- [ER Model Modality and Cardinality](https://www.youtube.com/watch?v=MkvXPqqB7PU) — modality as a separate concept.
- [Mongo vs Postgres](https://www.youtube.com/watch?v=ZZ2tx8iL3P4) — DB-choice defense.

**Reading**
- [Postgres FTS docs](https://www.postgresql.org/docs/current/textsearch-intro.html)
- [Neon FTS guide](https://neon.com/postgresql/postgresql-indexes/postgresql-full-text-search)

**Tools**
- [dbdiagram.io](https://dbdiagram.io) — code-driven ERD, export PNG/PDF.
- [Mermaid Live](https://mermaid.live) — Mermaid renders inside GitHub markdown directly.
