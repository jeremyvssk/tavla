# Learning Strategy — Learning *Inside* iLoveShopping

Goal: turn building iLoveShopping into deliberate practice for the four things that matter —
**fundamentals depth, system-design judgment, verification, building** — without separate study
time. This is catered to *this* codebase. The generic method (retrieval, spacing, predict-before-
reveal) you already do well in your topic homework; this doc is about **what to go deep on here,
how, and what to do each session.** When we work in this repo I coach you through it — see
[../CLAUDE.md → Learning Mode](../CLAUDE.md#learning-mode).

## The setup: the project is already your curriculum

It splits cleanly into two gyms:

- **Built — `auth/`, `user/`, security, Redis, the schema** → your **verification & reconstruction
  gym.** Go deep on what's there, rebuild it from blank, add the test types it's missing.
- **Unbuilt — `catalog/` domain, search, security tests, the whole frontend (BUILD_ORDER steps
  8–12)** → your **design-first gym.** You design it before I build it.

So you don't add study hours — you do the remaining P1 work *deliberately* and mine the finished
work for depth.

## 1. The load-bearing fundamentals in THIS repo (ranked)

Go deep on these, in roughly this order. Each: what it is · where it lives · what to gain · the rep.

**1. Stateless auth & the access/refresh model.**
Where: `JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`.
Gain: why access tokens are validated by *math only* (no DB hit) and what that buys/costs; the
stateless-vs-stateful tradeoff that recurs in every system.
Rep: close the files and reconstruct `JwtService` + the filter from blank; then trace
`GET /api/...` end-to-end naming every component it touches. (You already have the questions for
this in `jwtHomework.md` — re-do #14 cold.)

**2. Concurrency & atomicity — your crown jewel.**
Where: the `rotateRefreshToken` Lua script in `TokenStoreService.java`; the TOCTOU race on email
uniqueness (`UserService` + `DataIntegrityViolationException`).
Gain: how races happen, why atomicity needs one indivisible op, and *why this whole class of bug is
invisible to passing tests* — it only shows up under real concurrency. This is the single most
valuable intuition you can build, because it's exactly what AI and green test suites miss.
Rep: reconstruct the Lua script from blank and explain the 5ms-apart race out loud; then hunt the
codebase for *other* TOCTOU spots (registration, 2FA enable) and decide if each is actually safe.

**3. Data modeling & Postgres mechanisms.**
Where: `V1__initial_schema.sql` (schema is built; the catalog *code* is not).
Gain: constraints as facts not hopes, indexes, FK delete semantics, full-text search, denormaliza-
tion tradeoffs. Your schema is a goldmine of *design decisions to interrogate*:
- Why `ON DELETE RESTRICT` for `category_id`, `SET NULL` for `brand_id`, `CASCADE` for
  `product_images`/`reviews`? (Three different choices — defend each.)
- Why a `GENERATED ALWAYS ... STORED` `search_vector` instead of computing `to_tsvector` per query?
- Why store both `weight_kg` and `weight_lbs` (and cm/in)? Is that a smell? What would you do?
- Why denormalize `average_rating` / `review_count` onto `products` instead of `COUNT/AVG` on read?
- Why the *partial* unique index `uq_categories_root_name WHERE parent_id IS NULL`?
Rep: answer those five from blank (they're genuine design judgment, no single right answer), then
bring your answers and I'll push back.

**4. Input validation & security boundaries.**
Where: DTOs + `@Valid`, `GlobalExceptionHandler.java`, `claude-docs/backendInputValidation.md`.
Gain: validate-at-boundary / encode-at-sink, mass-assignment, injection, the upload checklist.
Rep: **the `security/InputValidationTest` is specified (backend CLAUDE.md §6, BUILD_ORDER #11) but
doesn't exist yet.** Propose the probe cases yourself (oversized strings, deep JSON, `<script>`,
path traversal, mass-assignment `is_admin`), we refine, then it gets written. That's a real
verification rep *and* fills a real gap.

**5. Data-store roles (Postgres vs Redis).**
Where: `TokenStoreService.java`, the storage map in CLAUDE.md.
Gain: what data belongs where and *why*, TTL semantics, blocklist sizing.
Rep: redraw the authoritative storage map from blank and justify every Redis key's existence + TTL.

**6. API & system design (modular monolith + swappable services).**
Where: the domain split (`auth`/`user`); next: the `catalog` domain + `SearchService` interface.
Gain: domain boundaries, service interfaces for swappable backends (Postgres search now → Elastic
later), REST + faceted-search design.
Rep: this is the heart of the next build phase — see §2.

**7. Frontend architecture & auth (unbuilt).**
Where: nothing yet — `frontend/src` is just `App.tsx`/`main.tsx`.
Gain: access token in a JS memory var + refresh-on-mount (never localStorage), server-state vs
client-state (React Query vs Redux), the Axios interceptor.
Rep: design the token-handling + data-fetching layer on paper before building (see §2).

## 2. System design — how we do it together

This is your weakest muscle (you said you mostly accept the recommended option) and your biggest
lever, and the unbuilt half of P1 is the perfect canvas. The rule: **you make the design before I
build anything.** For each upcoming piece:

- **Catalog domain (BUILD_ORDER #8):** you decide the entities, the DTO ↔ entity boundaries, which
  fields are client-writable, how admin CRUD differs from public read, pagination. Then I red-team.
- **Search (#9):** you design the `SearchService` *interface* first (so Postgres↔Elastic is
  swappable), then the query: `ts_rank` ordering, `pg_trgm` autocomplete, how facets are computed,
  what's indexed. This is real architecture.
- **Frontend (#11–12):** you design where the access token lives, the refresh flow, and the split
  between Redux (auth) and React Query (catalog) before a component is written.

Each design session, produce ~5–10 bullets: data model / flow / failure modes / what breaks at
100×scale / the tradeoff you chose and why. Bring it; I tear it apart; you revise; *then* we build.

## 3. Verification — how you actually know it's correct

"Tests pass → correct" is true only for the bugs tests cover. Build the ladder and the instinct for
what's *above* it:

- **The test ladder** (propose these yourself before I write them — your stated improvement):
  happy path → validation failures (400) → auth/ownership (401/403) → conflict/races (409) → edge
  cases → security probes. You list the cases, we refine, I write them, **you read them and confirm
  they test what you think they test.**
- **The class above tests** — always ask: *what could be wrong that a green suite wouldn't catch?*
  Concurrency (the Lua race), performance at scale (the unindexed query that's fine on 10 rows and
  dies on 100k), security (the injection that returns 200), and correctness-under-real-data. These
  are where *your* judgment is the only safety net — name the risk explicitly each feature.
- You don't write Java by hand — fine. You **specify** the cases and **review** every test/diff
  critically. The reviewing *is* the verification skill.

## 4. Your per-session protocol (run this every non-trivial task)

This is the answer to "what do I do when I start a session." I'll drive it with you (it's encoded
in CLAUDE.md → Learning Mode):

1. **Frame & design first — you drive.** State the goal; sketch the design (data flow, entities/
   endpoints, where state lives, failure modes, the key tradeoff + why) *before any code*.
2. **I red-team it.** I poke holes and name missed tradeoffs; you revise. I don't hand you the design.
3. **Predict before reveal.** Before I explain/generate, you predict the shape — so my output grades
   your retrieval instead of replacing it.
4. **Build — you own the load-bearing 20%.** You specify/direct the core logic; I do boilerplate.
   You review every diff and must be able to explain each line.
5. **Verify — you propose tests first.** You list the ladder; we refine; I write; you confirm they
   bite. Then name what a passing test wouldn't catch.
6. **Retrospective (one line).** What was right, what you'd change, what surprised you.

Say **"just build it"** when a task is throwaway/boilerplate and you want speed, not coaching.

## 5. What to do next (mapped to your BUILD_ORDER)

1. **Design session: the `catalog` domain (#8).** Bring your entity/DTO/endpoint design + the five
   schema-judgment questions from §1.3. (Design-first + data-modeling reps in one.)
2. **Build catalog** with the per-session protocol; you own the service-layer logic, I do CRUD/wiring.
3. **Write the missing `InputValidationTest` (#11)** — propose the probes (verification rep + real gap).
4. **Design session: `SearchService` interface + query (#9).** The architecture rep.
5. **Frontend (#11–12):** design token handling + state split before building.

Interleave a **Friday retrieval block**: re-answer old `*Homework.md` questions cold (JWT, BCrypt,
2FA), and re-reconstruct the Lua script. Spacing is what makes the auth knowledge *stick*.

## 6. The few method-habits that actually matter (you know these — keep them honest)

Retrieve from blank (don't reread) · predict before reveal · own the load-bearing 20% by hand/voice
· name the bug tests can't catch · revisit cold on a schedule · one-line post-mortem per feature.
Everything else in learning-science is secondary to these.
