# Backend Rules — Spring Boot

Operating rules for code under `backend/`. Root [CLAUDE.md](../CLAUDE.md) still applies (working
style, simplicity, surgical changes). This file adds the backend invariants that hold *all the
time*.

Task-specific detail lives in skills, so it loads only when the work calls for it:

| Doing this | Load |
|---|---|
| Adding/changing a controller, DTO, or validation rule | `new-endpoint` — full checklist, validation rules, Boot 4 API table |
| Writing or planning tests | `verify` — the test ladder, Testcontainers wiring |
| Changing the schema | `flyway-migration` — naming, append-only rule, Postgres features |

> **This is Spring Boot 4.0.6 / Spring Framework 7 / Jakarta EE 11 — not Boot 3.** Jackson **3**
> is the default (`tools.jackson.databind.ObjectMapper`), autoconfiguration is split into
> per-technology modules, and `@AutoConfigureMockMvc` is unavailable. Boot 3 tutorials will
> produce code that does not compile. Load `new-endpoint` for the version table and the full list
> of differences before writing backend code.

For the *why* behind the validation rules, see
[../claude-docs/backendInputValidation.md](../claude-docs/backendInputValidation.md). For where
each feature lives, the request flow and the Redis key table, see [README.md](README.md) — keep it
current when you add, move or rename a class it names.

---

## 1. Architecture invariants

- **Stateless processes.** No `HttpSession`, no in-memory user state, no static maps holding
  per-user data. All session-like state lives in Redis. Any instance must serve any request.
- **Modular monolith.** Code splits by domain (`user`, `auth`, `catalog`), not by layer. Each
  domain owns its controller/service/repository/entity, and its own `exception/` subfolder.
- **Cross-domain access only via service interfaces.** No repo-to-repo calls between domains.
- **Service interfaces for swappable backends.** `SearchService` gets `PostgresSearchService` now
  and `ElasticsearchSearchService` later. Don't inline Postgres-specific logic into callers.

## 2. Config and secrets

- **Every secret is an env var**, read in `application.yml` via `${VAR}`. `JWT_SECRET`, DB
  password, Redis URL, OAuth client secrets, reCAPTCHA keys, SMTP creds.
- **Never commit secrets.** Only `.env.example` (names, placeholders) is tracked; `.env` is
  gitignored. `docker-compose.yml` carries development defaults only.
- **No hardcoded URLs to backing services.** `DATABASE_URL`, `REDIS_URL`, `SMTP_HOST` come from
  the environment, so code reaches Postgres/Redis/MailHog exactly as it will reach prod equivalents.
- **No environment switches in code.** Never `if (env == "prod")`. Configure via properties.

## 3. Backing services

Postgres, Redis and MailHog are **attached resources** identified by URL. The authoritative
storage map — don't blur these:

- **Postgres** → durable user/product data
- **Redis** → refresh tokens, JTI blocklist, pending 2FA challenges, password-reset tokens
- **Filesystem** (Docker volume `product_images_data`) → image bytes, written by the backend,
  served by nginx. Path/URL stored in the DB. Keep access behind a `StorageService` interface so
  S3 can replace it.

## 4. Build, release, run

- Multi-stage Dockerfile: build the JAR in a Maven stage, copy into a JRE-only runtime. Never run
  `mvn` at container start.
- Flyway migrations run at startup and are **append-only** — never edit a committed `V*.sql`.

## 5. Logs

- **stdout only.** No `FileAppender`, no log directory in the container.
- SLF4J: `private static final Logger log = LoggerFactory.getLogger(X.class);` — not `@Slf4j`.
- **Never log secrets**: passwords (raw or hashed), JWTs, refresh tokens, 2FA secrets or backup
  codes, OAuth tokens. Prefer user IDs over emails and names.

## 6. Security invariants

- **Access tokens are validated by signature math only** — never stored or looked up — plus one
  Redis JTI blocklist check per request.
- **Refresh rotation is a single Redis Lua script** (atomic read-old → delete → write-new). Never
  three separate Redis calls; that race is invisible to tests.
- **Password reset revokes ALL refresh tokens** for the user, via `refresh_tokens_user:{userId}`.
- **2FA pending state is a Redis key** with a 5-minute TTL. Real JWTs only after TOTP verify.
- **A JWT proves identity; the service and DB prove ownership.** A valid token for user A must
  never touch user B's data.
- **No PII in the JWT payload.** `sub`, `role`, `iat`, `exp`, `jti` — nothing else.
- **Never bind a request body to a JPA entity.** Always a DTO that whitelists writable fields.
- **Parameterised queries always**, including `ORDER BY` (allowlist the sort key).

## 7. Errors

One `@RestControllerAdvice` maps exceptions to HTTP responses. No `try/catch` returning
`ResponseEntity` inside a controller. Never leak stack traces, SQL or internal paths. Don't catch
`Exception` to swallow it.
