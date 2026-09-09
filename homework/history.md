# Auth Study — Progress Log

## Study path (8 topics, ordered)

1. ✅ JWT internals
2. ✅ Password hashing (BCrypt)
3. ✅ Input validation
4. ✅ Refresh token rotation
5. ✅ Password reset via email
6. ✅ 2FA (TOTP)
7. ✅ OAuth (Google / Facebook)
8. ✅ CAPTCHA (reCAPTCHA v3)

**All auth study topics completed (2026-05-28).** Database study (`dbHomework.md`),
input validation study (`inputValidationHomework.md`), and search-flow study
(`searchHomework.md`) also fully covered. Ready to start implementation per
[../docs/BUILD_ORDER.md](../docs/BUILD_ORDER.md).

Original baseline (already known from `searchHomework.md`): access token in JS memory, `/auth/refresh` flow, `JwtAuthenticationFilter` checks (signature/exp/JTI blocklist), JTI semantics, logout via blocklist with TTL, CORS Vite↔Spring.

---

## Covered so far

### 1. JWT internals

- JWT = three base64url chunks joined by dots: `header.payload.signature`
- Header: `{ alg: HS256, typ: JWT }` — metadata about how it's signed
- Payload: JSON with claims — `sub` (user id), `iat`, `exp`, `jti`, `role`. Readable by anyone — base64 isn't encryption. **Never put secrets here.**
- Signature: `HMAC(header + "." + payload, SECRET)`. Server re-computes and compares on every request. Tampering → mismatch → reject.
- Why JWT vs session cookie: no DB lookup per request; just math. Scales horizontally.
- Where secrets live:
  - User passwords → BCrypt hash in `users` table
  - JWT signing secret → env var (`JWT_SECRET`), loaded by Spring Boot
- "Logout needs Redis" because once issued a JWT can't be un-expired early. Blocklist `jti` until original `exp`.

### 2. Refresh token rotation

- Redis is **not** a generic token DB — it's:
  - The authoritative store for **refresh tokens** (looked up on `/auth/refresh`)
  - A **blocklist** of cancelled access token JTIs (peek on every request)
- Access tokens are validated by **math only** — not stored server-side, not looked up in Redis (except the blocklist peek)
- Tokens are issued at **login**, not on first request
- Rotation flow on `/auth/refresh`: read old refresh → delete it → generate new access + refresh → store new refresh with fresh 7-day TTL → return both
- Single-use refresh: replay of an old refresh = key gone = reject. Detects theft.
- 7 days = max **inactivity**, not max session length. Active users never get logged out automatically.
- Atomicity: must be one atomic Redis op or two near-simultaneous refreshes can both succeed → use **Lua script** (CLAUDE.md updated to specify Lua, not "MULTI/EXEC or Lua")

### 3. Password hashing (BCrypt)

- BCrypt is for **things humans choose**: passwords + 2FA backup codes
- SHA-256 is for **server-generated secrets**: refresh tokens, password reset tokens (already high entropy, just need a fingerprint)
- BCrypt properties: deliberately slow (~250ms at cost 12), per-password salt baked in, format: `$2a$12$<salt><hash>`
- Same plaintext → different hash for each user (because of the salt)
- Spring Security: `new BCryptPasswordEncoder(12)`, then `.encode()` and `.matches()` — never roll your own

### 6. 2FA (TOTP)

- TOTP = HMAC(shared secret, current_unix_time / 30) → 6 digits. RFC 6238.
- Secret reaches the phone via QR code containing `otpauth://` URI
- Setup flow: `/auth/2fa/setup` → secret stored, URI returned → QR rendered → user scans → `/auth/2fa/verify` with first code → enable + generate 8 BCrypt'd backup codes (shown once)
- Login flow with 2FA on: password OK → Redis `2fa_pending:{userId}` with 5min TTL → return "challenge required" → user submits TOTP → verify with ±1 time-step tolerance for clock drift → delete pending key → issue real JWTs
- Stack: backend `dev.samstevens.totp:totp:1.7.1`, frontend `qrcode.react`
- Complexity: medium — TOTP itself is trivial (library), the real work is orchestrating the two-step login + Redis bridge state. ~1 day fresh, ~half day once the pattern clicks.

---

## Still to cover

3. **Input validation** — next up. Zod on client (React Hook Form), Bean Validation on server (`@Email`, `@Size`, `@Pattern`). Field-level error shape consistent.
5. **Password reset via email** — `/auth/forgot-password` → one-time token (SHA-256 fingerprint, 15 min TTL) → email link via MailHog (dev) → submit new password → revoke ALL refresh tokens for that user (iterate `refresh_tokens_user:{userId}` s
et).
7. **OAuth (Google, Facebook)** — Authorization Code flow. User → provider consent → code → backend exchanges for provider tokens → look up/create user with `auth_provider=GOOGLE` and `oauth_provider_id=<sub>` → issue **our own** JWTs. Provider tokens not stored long-term.
8. **CAPTCHA (reCAPTCHA v3)** — frontend gets token from Google → sends with form → backend POSTs to siteverify → reject if score too low. Bolt-on, last topic.

---

## Homework — questions to discuss next session

Do these unaided, then we'll go over them together.

### JWT
1. Why is the JWT payload base64-encoded if base64 isn't encryption? What's the purpose of base64 here?
2. An attacker steals a valid access token. The token expires in 10 minutes. What's the *worst* damage they can do, and what (if anything) stops them sooner?
3. Could you put the user's email in the JWT payload? Why or why not?

### Refresh rotation
4. Walk through what goes wrong, step by step, if `/auth/refresh` does (read old → delete → write new) as three separate Redis calls instead of a Lua script — assume two requests with the same refresh token arrive 5ms apart.
5. A user logs in on their laptop and their phone. Each gets its own refresh token. They click "log out everywhere." How does this actually work given the data we store in Redis?
6. Why does password reset specifically need to revoke ALL refresh tokens, vs. a normal logout which only revokes one access token's JTI?

### BCrypt
7. Two users register with the same password `hunter2`. What's stored in `users.password_hash` for each? Same value or different? Why?
8. Why doesn't the `users` table need a separate `salt` column?
9. We BCrypt 2FA backup codes but use plain SHA-256 for refresh tokens in Redis. Justify the asymmetry.

### 2FA
10. Why does the login flow need the `2fa_pending:{userId}` Redis key at all? What attack does it prevent?
11. The TOTP code is only 6 digits — that's 1 in a million. Why is brute-forcing it not a real concern? (Hint: think about what changes every 30 seconds, and what the server should track.)
12. The user enables 2FA, then loses their phone *and* loses their backup codes. What's the recovery path? (Open-ended — design something reasonable.)
13. Why ±1 time-step tolerance and not ±5? What's the tradeoff?

### Cross-topic
14. Trace a single API request (say, `GET /api/orders`) end-to-end with 2FA-enabled user, valid access token, no logout: which of {Postgres, Redis, BCrypt, JWT signing secret} are touched, and which are not?

---

## Homework — Input validation (current session)

Probe questions to assess starting knowledge before issuing study material.

15. We validate on both client (Zod + React Hook Form) and server (Bean Validation). If both run the same checks, why keep both?
16. Difference between *validating* and *sanitizing* an input. Concrete example of each, plus a field where you'd do both.
17. User registers with `full_name = <script>alert(1)</script>`. Should the validator reject this? If yes, what rule? If no, where does the defense belong?
18. Server rejects a registration form (email malformed + password too short). What should the HTTP response look like (status + body shape), and why does the shape matter for the frontend?
19. Admin POSTs to `/api/products` with `{ "name": "Matcha", "price": 12.50, "is_admin": true, "stock_quantity": 50 }`. `is_admin` isn't on the Product model. What should the backend do?

### Round 2 — harder validation
20. Parameterized queries vs validation: if all queries use JPA params, is string validation redundant? Where does it still earn its keep, and where does it not?
21. For email: regex-format check, uniqueness check, NOT NULL — name the layer for each and why. Bonus: what fails at runtime if only the code-level non-null check exists?
22. Avatar upload — list every backend validation check + the attack each prevents (≥4).
23. JSON DoS: 200MB body or 50k-deep nesting hits a validated endpoint. Why is `@Valid` insufficient, where in the lifecycle does damage occur, what stops it earlier?
24. Unicode password trap: same `café` password works on Mac, fails on Windows. What's BCrypt receiving, and what's the fix?

### Tying it together
25. End-to-end attack chain on `admin@shop.com` (2FA on): name the defense for each step (email leak, brute force, lucky guess, stale refresh token from old laptop, phishing-induced mass-assignment POST).
26. The "two layers" meta-principle: what connects Zod+Bean / Redis+Lua / BCrypt+rate-limit / JWT+service+DB-ownership? Give an example where a second layer would be over-engineering.


 1. Go deep on a few load-bearing fundamentals. Not breadth — you have breadth. Pick black boxes you use every day and pry them open, one at a time, until you could reconstruct the
  mechanism: how a database actually runs your query and what an index physically does; what really happens across one HTTP request; how concurrency creates races (you already own one —
  the Lua thing — generalize it); how memory and the network impose the constraints everything else is designed around. Use the exact method you already use in your homework: explain it
  from a blank page, get it wrong, fix it. This is the bedrock the rest stands on.

  2. Climb into system design — this is your biggest lever. You said you mostly take the recommended option. That's the muscle to build, because making the design — not accepting it — is
  the judgment layer that sits above the code, the part AI is weakest at, and the line between a steerer and a rubber stamp. The habit: for any feature, before you touch AI, sketch it
  yourself — where does state live, what's the data model, what are the failure modes, what breaks at 100× load, why this approach over the alternative — then have AI red-team your design
  instead of handing you its own. (North-star book for later: Designing Data-Intensive Applications. You don't need it yet; the "design it on paper first" habit matters more right now.)

  3. Train verification — "how do I know this is correct?" In a world where you didn't write the code, the person who can tell whether the output is right is worth more than the one who
  generated it. Practice it deliberately: review AI's diffs like a junior's PR before you accept them; write the test that would catch the bug before you trust the code; reproduce a bug
  by hand instead of letting AI auto-fix it — bugs are the densest learning there is, so stop donating them. Critical reading of code is a real, trainable skill, and it's quietly becoming
  the central one.

  4. Keep building real things, and hand-build the load-bearing 20%. Don't learn in the abstract — ship projects. Let AI do the boilerplate, but build the part that teaches the mechanism
  yourself. Breadth is free now; depth still only comes from doing.