---
name: verify
description: Decide what to test and write the tests for this repo — the six-rung test ladder, the Testcontainers and Spring Boot 4 wiring that integration tests need here, and the bug classes a green suite cannot catch (concurrency, performance at scale, security). Use when writing, reviewing, or planning any backend or frontend test, or when asked whether something is adequately tested.
---

# Verifying Work Here

"Tests pass" means "the bugs the tests cover are absent". Nothing more. This skill covers both
halves: building the ladder, and knowing what sits above it.

## Order of operations — do not skip step 1

**The author proposes the cases before any test is written.** Per
[CLAUDE.md → Learning Mode](../../../CLAUDE.md#learning-mode), listing the cases is the
verification rep and it belongs to them, not to me. Ask for their list, refine it together, then
write. Afterwards they read the tests and confirm the tests bite the way they expected.

Skip this only on an explicit "just build it".

## The ladder

Six rungs. Name each one explicitly when proposing cases — the gaps show up in the naming.

| Rung | Covers | Typical status |
|---|---|---|
| 1. Happy path | The thing works at all | 200/201 |
| 2. Validation | Malformed, missing, oversized, wrong-type input | 400 |
| 3. Auth & ownership | No token, expired token, blocklisted token, valid token for *someone else's* resource | 401 / 403 |
| 4. Conflict & race | Duplicate insert, replayed single-use token, concurrent update | 409 |
| 5. Edge | Empty collections, boundary values, unicode, pagination past the end | varies |
| 6. Security probe | Injection, deep JSON, path traversal, mass assignment | 400, never 200 |

Rung 3 is the one most often skipped, and rung 4 is the one that matters most in this codebase.

## What a passing suite will not catch

Ask this out loud for every feature, and name the specific risk rather than the category:

- **Concurrency.** The canonical example here is refresh rotation: read-old / delete-old /
  write-new as three Redis calls passes every test and still lets two requests 5 ms apart both
  succeed. Only the Lua script makes it atomic. Same shape appears at email-uniqueness
  registration and 2FA enable. A test suite runs these sequentially and sees nothing.
- **Performance at scale.** A query that is fine over 10 seeded rows and dies over 100k. Faceted
  search and `ts_rank` ordering are the live risks — check for an index, not just a green test.
- **Security.** An injection that returns `200` with the wrong rows is a passing test and a breach.
- **Correctness under real data.** Seeded fixtures are tidy; production data is not.

## Backend wiring

```sh
cd backend && mvn verify     # unit (surefire) + *IT integration (failsafe)
```

- **Integration tests use Testcontainers — real Postgres and Redis. Never H2.** H2 lies about
  `JSONB`, `tsvector`, generated columns and partial indexes, all of which this schema depends on.
- **Name integration tests `*IT`** so failsafe picks them up in the `verify` phase. `*Test` runs in
  surefire during `test`.
- **`@AutoConfigureMockMvc` does not exist here** (Spring Boot 4; `spring-boot-webmvc-test` is not
  on the classpath). Build MockMvc by hand:

  ```java
  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
  ```

- Unit tests cover pure logic (`JwtService`, `TokenStoreService`, hashing wrappers) with Mockito
  for collaborators. Existing tests in `backend/src/test/java/com/iloveshopping/` are the pattern
  to match.
- The failsafe config already pins Docker API `1.43` and disables the Ryuk reaper — that is a fix
  for this machine's Docker Desktop, not a thing to re-debug.

## Frontend wiring

```sh
cd frontend && bun test      # Vitest + Testing Library, jsdom
```

Config lives in `vite.config.ts` under `test`. `globals: true`, so no per-file imports of
`describe`/`it`. Client-side input validation on the auth forms is a graded requirement — it needs
real tests, not just working code.

## Coverage this repo still owes

Check these off against reality before claiming a suite is complete:

- `security/InputValidationTest` — specified in BUILD_ORDER #11, still not written
- Catalog: product model unit tests, controller integration tests
- Search: relevance ordering, facet counts, autocomplete
- Frontend: any test at all
