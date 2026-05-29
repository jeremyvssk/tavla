# Backend Rules — Spring Boot

Operating rules for code under `backend/`. Root [CLAUDE.md](../CLAUDE.md) still applies (working style, simplicity, surgical changes). This file adds backend-specific constraints.

For the *why* behind validation rules, see [../claude-docs/backendInputValidation.md](../claude-docs/backendInputValidation.md). This file is the *what to do*.

---

## 1. Architecture invariants

- **Stateless processes.** No `HttpSession`, no in-memory user state, no static maps holding per-user data. All session-like state lives in Redis. Any instance must be able to serve any request.
- **Modular monolith.** Code is split by domain (`user`, `auth`, `catalog`), not by layer. Each domain owns its controller/service/repository/entity.
- **Cross-domain access only via service interfaces.** No repo-to-repo calls between domains.
- **Service interfaces for swappable backends.** `SearchService` has `PostgresSearchService` now and `ElasticsearchSearchService` later. Don't inline Postgres-specific logic in callers.

## 2. Config and secrets

- **Every secret is an env var.** `JWT_SECRET`, DB password, Redis URL, OAuth client secrets, reCAPTCHA keys, SMTP creds. Read in `application.yml` via `${VAR}` substitution.
- **Never commit secrets.** Only `.env.example` (names, placeholder values) goes in Git. `.env` is gitignored.
- **No hardcoded URLs to backing services.** `DATABASE_URL`, `REDIS_URL`, `SMTP_HOST` come from env. Code reaches Postgres/Redis/MailHog the same way it'll reach the prod equivalents.
- **No environment switches in code.** Don't write `if (env == "prod")`. Configure via properties; code stays the same.

## 3. Backing services

- Treat Postgres, Redis, MailHog as **attached resources** identified by URL.
- **Authoritative storage map** (don't blur these):
  - Postgres → durable user/product data, refresh-token-set membership only by reference (the value lives in Redis)
  - Redis → refresh tokens, JTI blocklist, 2FA pending challenges, password-reset tokens
  - Local filesystem (Docker volume `product_images_data`) → image bytes, served via nginx. Path/URL stored in DB. Will be S3 later — keep the access behind a `StorageService` interface.

## 4. Build / release / run

- Multi-stage Dockerfile: build the JAR in a Maven stage, copy into a JRE-only runtime stage. Don't run `mvn` at container start.
- Flyway migrations run on app startup. Migrations are **append-only** — never edit a committed `V*.sql`; add a new one.

## 5. Logs

- Log to stdout only. No `FileAppender`, no log directory inside the container.
- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(X.class);`).
- **Never log secrets**: passwords (raw or hashed), JWTs, refresh tokens, 2FA secrets/codes, OAuth tokens. Log user IDs, not emails or names, where avoidable.

## 6. Persistence rules

- **DB constraints mirror DTO constraints.** Every `@NotBlank` has a `NOT NULL`. Every `@Size(max=N)` has a `VARCHAR(N)`. Every `@Min`/`@DecimalMin` has a `CHECK`. Code-level checks are hopes; DB constraints are facts.
- **Uniqueness is a DB `UNIQUE` constraint.** A service-level pre-check exists only to produce a friendly 409. Always wrap the insert in a `try/catch (DataIntegrityViolationException)` to handle the TOCTOU race.
- **Never bind a request body to a JPA entity.** Always go through a DTO that whitelists writable fields.
- **Use parameterized queries always** (JPA does this). Never concatenate user input into JPQL/SQL strings, even for `ORDER BY`.

## 7. Input validation rules

- **DTOs are records with `jakarta.validation` annotations.** Controllers take `@Valid @RequestBody DtoName`.
- **Resource limits are global, set once**, not per endpoint:
  - nginx `client_max_body_size`
  - `spring.servlet.multipart.max-request-size`, `server.tomcat.max-http-form-post-size`
  - Jackson `StreamReadConstraints` (max nesting depth, max string length, max number length)
- **Jackson rejects unknown fields:** `spring.jackson.deserialization.fail-on-unknown-properties: true`. Mass-assignment guard.
- **Passwords are NFC-normalized before BCrypt**, on both register and login. Never ban accents.
- **File uploads run the full checklist** (size cap, content-type whitelist, magic-byte sniff, dimension limits, re-encode through library, UUID filename, served with `Content-Disposition: attachment` from a separate path). See [../claude-docs/backendInputValidation.md §6](../claude-docs/backendInputValidation.md).
- **Validate at the boundary, encode at the sink.** Parameterized queries protect the SQL sink only; HTML/log/header/path sinks each need their own encoder.

## 8. Auth and security rules

- **Access tokens validated by signature math only.** Not stored, not looked up — except a Redis JTI blocklist peek on every request.
- **Refresh rotation is a Redis Lua script** (atomic read-old → delete → write-new). Never three separate Redis calls.
- **Password reset revokes ALL refresh tokens** for the user (iterate `refresh_tokens_user:{userId}` set).
- **2FA pending state is a Redis key** with 5-minute TTL. Real JWTs only issued after TOTP verify.
- **JWT proves identity; service+DB checks prove ownership.** Always re-check that the resource being acted on belongs to the JWT's `sub`. A valid JWT for user A must not read user B's order.
- **No PII in JWT payload.** `sub` (user id), `role`, `iat`, `exp`, `jti` only. No email, no name.

## 9. Error handling

- **One `@RestControllerAdvice`** maps exceptions to HTTP responses. No try/catch returning `ResponseEntity` in controllers.
- **Validation failures → 400** with structured body:
  ```json
  { "error": "validation_failed", "fields": { "email": "...", "password": "..." } }
  ```
- **Never leak stack traces, SQL, or internal paths** in responses.
- **Don't catch `Exception` to swallow it.** Let unexpected errors bubble to the advice → 500 with a generic message.

## 10. Tests

- **Integration tests use Testcontainers** (real Postgres, real Redis). Never H2 — it lies about Postgres-specific syntax (JSONB, tsvector, generated columns).
- **Unit tests for pure logic** (`JwtService`, `TokenStoreService`, password hashing wrappers). Mock collaborators with Mockito.
- **Controller integration tests** hit the full Spring context with `@SpringBootTest` + `MockMvc` + Testcontainers. Cover 400/401/403/409 paths, not just the happy path.
- **One `InputValidationTest`** runs known injection probes (oversized strings, deep JSON, `<script>`, path traversal in filenames) against actual endpoints.

## 11. New-endpoint checklist

Before marking an endpoint done:

- [ ] DTO with `jakarta.validation` annotations
- [ ] `@Valid @RequestBody` on the controller method
- [ ] DTO whitelists only client-writable fields
- [ ] DB constraints mirror DTO constraints
- [ ] Uniqueness enforced at DB level if relevant; race caught in service
- [ ] Auth required (or explicitly noted as public)
- [ ] Ownership check if acting on a user-owned resource
- [ ] Errors flow through `@RestControllerAdvice`
- [ ] Integration test covers happy path + at least one validation failure + auth failure
