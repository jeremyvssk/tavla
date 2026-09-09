# JWT Auth — Study Material

Goal: explain JWT and its parts, the access/refresh token model, single-use refresh rotation, revocation for *both* token types, and how a request gets authenticated on every call. This covers the code written for build-order steps 4–5 (`JwtService`, `TokenStoreService`, `AuthService`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthController`). The rubric in [README.md](../README.md) tests these directly.

## Table of Contents

1. [The big picture — what lives where](#1-the-big-picture--what-lives-where)
2. [What a JWT actually is](#2-what-a-jwt-actually-is)
3. [JwtService — minting and verifying access tokens](#3-jwtservice--minting-and-verifying-access-tokens)
4. [TokenStoreService — refresh tokens + the blocklist (Redis)](#4-tokenstoreservice--refresh-tokens--the-blocklist-redis)
5. [Single-use rotation and the Lua script](#5-single-use-rotation-and-the-lua-script)
6. [The four auth flows](#6-the-four-auth-flows)
7. [Why the refresh token is an httpOnly cookie](#7-why-the-refresh-token-is-an-httponly-cookie)
8. [JwtAuthenticationFilter + SecurityConfig — authenticating a request](#8-jwtauthenticationfilter--securityconfig--authenticating-a-request)
9. [Error handling](#9-error-handling)
10. [Cheat-sheet answers for the oral](#cheat-sheet-answers-for-the-oral)
11. [Self-check questions](#self-check-questions)
12. [Resources](#resources)

---

## 1. The big picture — what lives where

Two token types, two jobs:

| Token | Lifetime | Job | Stored where? |
|---|---|---|---|
| **Access token** (JWT) | short (15 min) | proves identity on every API call | client memory (JS var); **never** persisted server-side |
| **Refresh token** (opaque random string) | long (7 days) | exchanges for a new access token when it expires | server: SHA-256 hash in Redis; client: httpOnly cookie |

The core tension the design solves: **access tokens are fast but can't be un-issued** (they're just math — no lookup), while **refresh tokens are revocable but slow** (a Redis lookup). So you make access tokens short-lived and stateless, and you make refresh tokens stateful and rotated.

Authoritative storage map:
- **Postgres** → the user (durable identity, password hash, role).
- **Redis** → refresh-token hashes (`refresh_token:{hash}` → userId), the per-user set of those hashes (`refresh_tokens_user:{userId}`), and the access-token blocklist (`token_blocklist:{jti}`).
- **Client** → access token in memory, refresh token in an httpOnly cookie.

---

## 2. What a JWT actually is

A JWT is three Base64URL parts joined by dots: `header.payload.signature`.

- **Header** — the algorithm, e.g. `{"alg":"HS256"}`.
- **Payload** — the *claims*. Ours carry **only**: `sub` (user id), `role`, `iat` (issued-at), `exp` (expiry), `jti` (unique token id). **No PII** — no email, no name. The payload is *not encrypted*, only encoded — anyone can read it. So you never put secrets in it.
- **Signature** — `HMAC-SHA256(header + "." + payload, secret)`. Only someone with `JWT_SECRET` can produce a valid signature, and any tampering with header/payload breaks it.

Key idea: the server doesn't store the token. To validate, it just **recomputes the signature** with its secret and checks `exp`. That's why access tokens scale — no database hit. The price: you can't easily revoke one (see §4, the blocklist).

We use **HS256** (symmetric — one shared secret). HMAC needs a key ≥256 bits; that's why `JWT_SECRET` must be a long random string.

---

## 3. JwtService — minting and verifying access tokens

File: `auth/JwtService.java`. Two methods.

**`generateAccessToken(userId, role)`** builds the JWT:
```java
return Jwts.builder()
        .subject(userId.toString())          // sub
        .claim("role", role.name())          // custom claim
        .id(UUID.randomUUID().toString())    // jti — unique per token, enables blocklisting
        .issuedAt(Date.from(now))            // iat
        .expiration(Date.from(now.plus(accessTokenTtl)))  // exp = now + 15 min
        .signWith(key)                       // HS256 signature
        .compact();
```

**`parse(token)`** verifies and returns the claims:
```java
return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
```
If the signature is wrong, the token is malformed, or it's expired, this **throws** a `JwtException` subtype (`SignatureException`, `ExpiredJwtException`, …). The caller (the filter) catches that and treats the request as anonymous.

The signing key is built once in the constructor from the `JWT_SECRET` env var: `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`. The TTL comes from config (`app.jwt.access-token-ttl`, default `PT15M`).

Why this is unit-testable with no mocks: it's pure crypto. The test (`JwtServiceTest`) generates a token and asserts the claims; tampers a character and expects `JwtException`; signs with a *different* secret and expects rejection; and uses a **negative TTL** to mint an already-expired token and expect `ExpiredJwtException` — no `Thread.sleep` needed.

---

## 4. TokenStoreService — refresh tokens + the blocklist (Redis)

File: `auth/TokenStoreService.java`. This is the *stateful* half of auth.

**Refresh tokens are opaque + hashed.** A refresh token is just 32 random bytes (`SecureRandom`), Base64URL-encoded. We **never store the raw value** — only its SHA-256 hash:
```
refresh_token:{sha256(raw)}      → userId      (TTL = 7 days)
refresh_tokens_user:{userId}     → Set of those hashes
```
Why hash it? Defense in depth. If Redis is dumped, the hashes are useless — they can't be replayed as tokens. (Same reasoning as hashing the password-reset token.)

Why the per-user **set**? So password reset can revoke *all* of a user's refresh tokens at once: iterate the set, `DEL` each `refresh_token:{hash}`, then drop the set (`revokeAllRefreshTokens`).

**The access-token blocklist.** Since access tokens aren't stored, the only way to kill one early is a deny-list:
```
token_blocklist:{jti}  → "1"   (TTL = the token's remaining lifetime)
```
On logout we write the JTI here; the filter checks it on every request. The TTL means the key auto-expires exactly when the token would have expired anyway — so the blocklist never grows unbounded.

This is the answer to the rubric's "**revocation for both** token types": refresh tokens are revoked by deleting their Redis key; access tokens are revoked by blocklisting their `jti`.

---

## 5. Single-use rotation and the Lua script

The rubric demands "**refresh token rotation with single-use validation**": each refresh token works exactly once; using it issues a *new* one and invalidates the old. Replaying an old token must fail.

Naively that's three Redis calls (check old → delete old → write new), which has a race: two requests with the same token could both pass the check before either deletes. The fix is **atomicity** — do it in one Redis operation with a Lua script (`rotateRefreshToken`):
```lua
local owner = redis.call('GET', KEYS[1])               -- old token's owner
if not owner or owner ~= ARGV[1] then return 0 end     -- unknown or not this user → reject
redis.call('DEL', KEYS[1])                              -- consume the old token
redis.call('SREM', KEYS[3], ARGV[2])                   -- drop old hash from user's set
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])     -- store the new token
redis.call('SADD', KEYS[3], ARGV[3])                   -- track the new hash
return 1
```
Redis runs a script with no interleaving, so the "delete old, write new" is one indivisible step. A replay of the consumed token hits `GET` → key gone → returns `0` → we throw `InvalidRefreshTokenException`.

> Note: at refresh time the client only sends the cookie (the access token is expired), so we first resolve the owner with `findUserIdByRefreshToken`, then rotate. The Lua re-checks ownership, so a replay sneaking in between still fails.

This is the one piece best proven against **real Redis** (Testcontainers), which `AuthControllerIT` does: log in, refresh once (200 + new cookie), replay the first cookie → 401.

---

## 6. The four auth flows

`AuthService` orchestrates; `AuthController` handles HTTP + the cookie.

- **Register** (`POST /auth/register`, public) → `UserService.createLocalUser` (BCrypt + NFC-normalized password). Returns **201**, no auto-login. (Email verification will gate things in a later step.)
- **Login** (`POST /auth/login`, public) → find user, `passwordMatches` (BCrypt verify on the NFC-normalized input), then issue **access token (JSON body)** + **refresh token (Set-Cookie)**.
- **Refresh** (`POST /auth/refresh`, public, cookie required) → resolve owner from the cookie, **rotate** (single-use), reload the user (so role changes / deletions take effect), mint a new access token + new cookie.
- **Logout** (`POST /auth/logout`, authenticated) → blocklist the current access token's `jti` for its remaining life, revoke the refresh token from the cookie, and clear the cookie (`Max-Age=0`).

The cookie is always `HttpOnly; Secure; SameSite=Strict; Path=/auth`.

---

## 7. Why the refresh token is an httpOnly cookie

The README requires the **access token in memory** (not local/session storage) and "rehydrate on reload via `/auth/refresh`". For rehydrate to work, *something* must survive a page reload — and it can't be the access token (memory is wiped on reload). So the refresh token has to persist on the client. Its only two homes are an **httpOnly cookie** or **`localStorage`**.

We chose the cookie:
- **XSS-safe** — JavaScript literally cannot read an httpOnly cookie, so an injected script can't steal the refresh token. `localStorage` is fully readable by any script.
- **Matches the rubric's spirit** — the access token stays in memory; the more-sensitive refresh token never touches JS-readable storage.
- **CSRF** is the cookie tradeoff (cookies auto-send), closed cheaply by `SameSite=Strict` + `Path=/auth` — no CSRF-token machinery needed.

Tradeoff we accepted: cookies are browser-centric. A future **native mobile app** would instead use a Bearer header + the OS secure store (Keychain/Keystore). That's an *additive* second transport on the same endpoints — not a rewrite. (And note: one login is per-device — logging in on web doesn't log in your phone; OAuth gives a shared *identity*, not a shared *session*.)

---

## 8. JwtAuthenticationFilter + SecurityConfig — authenticating a request

**`JwtAuthenticationFilter`** (a `OncePerRequestFilter`) runs on every request:
1. Read `Authorization: Bearer <jwt>`. No header → do nothing, continue (request stays anonymous).
2. `jwtService.parse(token)` — verifies signature + expiry.
3. Check `tokenStore.isAccessTokenBlocklisted(jti)` — the **one** stateful check we allow per request (revocation). Blocklisted → don't authenticate.
4. Build an `AuthPrincipal(userId, role, jti, expiresAt)` and set it in the `SecurityContext` with authority `ROLE_<role>`.
5. Any `JwtException` (bad/expired) → leave it anonymous.

Stashing `jti` + `expiresAt` in the principal is what lets `logout` blocklist the token without re-parsing it.

**`SecurityConfig`** wires the chain:
- `SessionCreationPolicy.STATELESS` — no HTTP session; auth state is the JWT + Redis.
- `csrf` disabled — justified: state-changing authenticated calls use a Bearer header (not auto-sent by browsers), and the one cookie is `SameSite=Strict`.
- `formLogin` / `httpBasic` disabled — this is a JSON API.
- `register`/`login`/`refresh` are `permitAll`; everything else `authenticated`.
- Custom entry point → **401 JSON** for unauthenticated, custom handler → **403 JSON** for forbidden (instead of Spring's default redirect/403 page).
- The JWT filter is added before `UsernamePasswordAuthenticationFilter`.

A request's life: filter authenticates (or not) → authorization rules decide → if unauthenticated on a protected route, the entry point writes 401.

---

## 9. Error handling

One `@RestControllerAdvice` (`GlobalExceptionHandler`) maps exceptions to HTTP, so controllers never `try/catch` into `ResponseEntity`:

| Exception | Status | Body |
|---|---|---|
| `MethodArgumentNotValidException` (bean validation) | 400 | `{"error":"validation_failed","fields":{...}}` |
| `HttpMessageNotReadableException` (bad/unknown-field JSON) | 400 | `{"error":"malformed_request"}` |
| `InvalidCredentialsException` / `InvalidRefreshTokenException` | 401 | `{"error":"invalid_credentials"}` |
| `EmailAlreadyExistsException` | 409 | `{"error":"email_already_exists"}` |
| `UserNotFoundException` | 404 | `{"error":"user_not_found"}` |

Login failure is deliberately generic ("invalid_credentials") — never reveal whether the email exists. No stack traces, SQL, or paths leak; unexpected errors bubble to a generic 500.

---

## Cheat-sheet answers for the oral

- **What are the three parts of a JWT?** → Header (algorithm), payload (claims — ours: `sub`, `role`, `iat`, `exp`, `jti`, no PII), signature (`HMAC-SHA256` over header+payload with the secret). Base64URL, dot-joined. Payload is encoded, not encrypted.
- **How do you validate a JWT?** → Recompute the signature with `JWT_SECRET` and check `exp`. No DB lookup — that's why it scales. Only extra check: a Redis blocklist peek for the `jti`.
- **Access vs refresh token?** → Access = short-lived (15 min), stateless, in memory, proves identity per request. Refresh = long-lived (7 days), stateful (hashed in Redis), in an httpOnly cookie, only used to mint new access tokens.
- **What does single-use rotation mean and how do you enforce it?** → Each refresh issues a new refresh token and kills the old one; replaying the old one fails. Enforced atomically with a Redis **Lua script** (check owner → delete old → write new in one step), so there's no check-then-act race.
- **Revocation for both tokens?** → Refresh: delete its Redis key (and on password reset, iterate the user's set and delete all). Access: write its `jti` to `token_blocklist:{jti}` with TTL = remaining lifetime; the filter checks it every request.
- **Why hash the refresh token in Redis?** → If Redis leaks, hashes can't be replayed. Raw tokens would be live credentials.
- **Why is the access token in memory, refresh in an httpOnly cookie?** → Memory = no persistent XSS target for the access token; httpOnly = JS can't read the refresh token. `SameSite=Strict` handles the cookie's CSRF risk.
- **Why is logout's access-token revocation needed if tokens expire in 15 min anyway?** → Without it, a stolen token works until `exp`. Blocklisting the `jti` kills it immediately; the TTL bounds blocklist growth.
- **Why NFC-normalize the password?** → So accented characters hash identically on register and login (the same bytes), without banning them.

---

## Self-check questions

Do these closed-book before next session.

1. A teammate says "let's store the user's email in the JWT so the frontend doesn't need another call." Why do we refuse? Give two reasons.
2. An attacker steals a valid access token (15 min left) via a logged URL. They use it for 5 minutes, then the user logs out. Walk through exactly what stops the attacker at minute 6 — name the Redis key and who writes/reads it.
3. Two browser tabs fire `/auth/refresh` with the *same* cookie at the same instant. What does each get back, and which line of the Lua script guarantees only one wins?
4. Why can't the `/auth/refresh` rotation be done with three separate Redis commands instead of a Lua script? Describe the race.
5. The access token is "stateless," yet we do a Redis lookup on every request. What is that lookup, and doesn't it defeat the point of statelessness? Defend the design.
6. `register` returns 201 with no tokens; the user must `login` separately. What did we trade away, and why is that fine for this project?
7. Login with a wrong password and login with a non-existent email both return the same 401 body. Why is that deliberate?
8. We disabled CSRF protection. Justify it in two sentences — what about the request shape makes CSRF a non-issue here, and what handles the one cookie?
9. You add a mobile app next year. What changes on the backend, and what does *not*? Why isn't this a rewrite?
10. In `JwtServiceTest`, how do we test that an expired token is rejected without making the test slow? Why does that trick work?

---

## Resources

**Spec**
- [RFC 7519 — JSON Web Token](https://datatracker.ietf.org/doc/html/rfc7519)
- [jwt.io](https://jwt.io) — paste a token to see header/payload/signature

**Libraries**
- [jjwt (Java JWT) docs](https://github.com/jwtk/jjwt)
- [Spring Security reference](https://docs.spring.io/spring-security/reference/)
- [Redis Lua scripting (EVAL)](https://redis.io/docs/latest/commands/eval/)

**Background**
- [OWASP — JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [OWASP — session / token storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#local-storage)
