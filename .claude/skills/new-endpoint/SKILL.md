---
name: new-endpoint
description: Build or review a Spring Boot REST endpoint in this repo — the DTO/validation/persistence/auth/error-handling checklist every endpoint must satisfy before it counts as done, plus the Spring Boot 4 API differences that break code written against Boot 3 tutorials. Use when adding, changing, or reviewing any controller method, DTO, or request-validation rule under backend/.
---

# Adding an Endpoint

The rules an endpoint in this codebase must satisfy. The *why* behind the validation rules is in
[claude-docs/backendInputValidation.md](../../../claude-docs/backendInputValidation.md); this is
the *what to do*.

## Spring Boot 4 — read before writing any backend code

The parent BOM is **Spring Boot 4.0.6 / Spring Framework 7 / Jakarta EE 11**, not Boot 3. Most
tutorials you or I have seen are Boot 3 and will produce code that does not compile.

| Area | Version | Area | Version |
|---|---|---|---|
| Java | 21 | Hibernate ORM | 7.2.12 |
| Spring Boot | 4.0.6 | Flyway | 11.14.1 |
| Spring Framework | 7.0.7 | PostgreSQL JDBC | 42.7.10 |
| Spring Security | 7.0.5 | **Jackson** | **3.1.2** |
| Spring Data JPA / Redis | 4.0.5 | jjwt | 0.12.5 |
| Tomcat | 11.0.21 | TOTP (`dev.samstevens.totp`) | 1.7.1 |
| Lettuce | 6.8.2 | Lombok | 1.18.46 |

Gotchas already paid for — do not relearn them:

- **Jackson 3 is the default.** The managed `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper`,
  *not* `com.fasterxml.jackson.databind.ObjectMapper` (which exists transitively but has no bean).
  Some accessors were renamed — `JsonNode.asText()` → `asString()`. Annotations stay under
  `com.fasterxml.jackson.annotation` (`@JsonInclude` etc.), shared between both.
- **Autoconfiguration is split into per-technology modules.** Flyway needs the `spring-boot-flyway`
  dependency; `flyway-core` alone will not run migrations. Same pattern elsewhere.
- **`@AutoConfigureMockMvc` is unavailable** — it lives in `spring-boot-webmvc-test`, which is not
  on the classpath. Build MockMvc by hand:
  `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()`.

**Lombok** is for boilerplate on JPA entities (`@Getter @Setter`, see `user/User.java`).
**DTOs are Java records, never Lombok classes.** Loggers are plain SLF4J
(`LoggerFactory.getLogger`), not `@Slf4j`.

## The checklist

Every box before the endpoint is done:

- [ ] DTO is a **record** with `jakarta.validation` annotations
- [ ] `@Valid @RequestBody` on the controller method
- [ ] DTO whitelists **only client-writable fields** — never bind a request body to a JPA entity
- [ ] DB constraints mirror the DTO constraints (see below)
- [ ] Uniqueness enforced by a DB `UNIQUE` constraint; the TOCTOU race caught in the service
- [ ] Auth required, or explicitly documented as public in `SecurityConfig`
- [ ] Ownership re-checked if the resource belongs to a user
- [ ] Errors flow through the single `@RestControllerAdvice` — no `try/catch` returning
      `ResponseEntity` in a controller
- [ ] Integration test covers happy path + at least one validation failure + one auth failure
      (use the `verify` skill for the full ladder)

## Validation

- **DB constraints mirror DTO constraints, always.** Every `@NotBlank` has a `NOT NULL`; every
  `@Size(max=N)` a `VARCHAR(N)`; every `@Min`/`@DecimalMin` a `CHECK`. Code-level checks are hopes;
  database constraints are facts.
- **Uniqueness is a database constraint.** A service pre-check exists only to produce a friendly
  `409`. Always also wrap the insert in `try/catch (DataIntegrityViolationException)` — two
  requests milliseconds apart both pass the pre-check.
- **Parameterised queries always.** Never concatenate user input into JPQL or SQL, *including*
  `ORDER BY` — sort keys come from an allowlist, not from the request string.
- **Passwords are NFC-normalised before BCrypt**, on register and login both. Never ban accents.
- **Resource limits are global, set once**, not per endpoint: nginx `client_max_body_size`,
  `spring.servlet.multipart.max-request-size`, `server.tomcat.max-http-form-post-size`, Jackson
  `StreamReadConstraints`.
- **Jackson rejects unknown fields** (`fail-on-unknown-properties: true`). That is the
  mass-assignment guard — don't disable it for convenience.
- **Validate at the boundary, encode at the sink.** Parameterised queries protect the SQL sink
  only. HTML, log, header and path sinks each need their own encoder.
- **File uploads run the full checklist**: size cap, content-type allowlist, magic-byte sniff,
  dimension limits, re-encode through a library, UUID filename, served with
  `Content-Disposition: attachment` from a separate path. Details in
  [backendInputValidation.md §6](../../../claude-docs/backendInputValidation.md).

## Auth

- **Access tokens are validated by signature math only** — not stored, not looked up — plus one
  Redis JTI blocklist check per request.
- **A JWT proves identity; the service and DB prove ownership.** A valid token for user A must
  never read user B's data. Re-check against the token's `sub`.
- **No PII in the payload.** `sub`, `role`, `iat`, `exp`, `jti`. No email, no name.

## Errors

One `@RestControllerAdvice` maps exceptions to responses. Validation failures return `400`:

```json
{ "error": "validation_failed", "fields": { "email": "...", "password": "..." } }
```

Never leak stack traces, SQL or internal paths. Don't catch `Exception` to swallow it — let
unexpected errors reach the advice and become a generic `500`.

## Wiring a new path through the proxy

`frontend/nginx.conf` proxies a fixed alternation of backend prefixes
(`auth|users|products|categories|brands|search`). A new top-level domain prefix must be added
there **and** to the matching `server.proxy` pattern in `frontend/vite.config.ts`, or the frontend
will 404 against its own origin. Paths are proxied verbatim — never introduce a rewrite, because
the refresh cookie is scoped `Path=/auth`.
