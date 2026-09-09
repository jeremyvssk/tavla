# Backend Input Validation — Reference

Practical rules for validating input in the Spring Boot backend. Use this when wiring up controllers, DTOs, and persistence.

---

## The mental model

Validation isn't one thing — it's a **pipeline** with different jobs at each stage:

```
Request → Reverse proxy → Servlet container → Jackson parser → @Valid (DTO) → Service rules → DB constraints
         (size limits)   (size limits)       (depth/length)   (shape)        (business)     (truth)
```

Each layer has a different job. You can't replace one with another.

---

## 1. Validate vs sanitize vs encode

- **Validate**: reject bad input. Happens on the way *in*.
- **Sanitize**: strip/transform bad input into safe input. Happens on the way *in*, sparingly.
- **Encode**: make input safe for a specific output context (HTML, SQL, shell). Happens on the way *out*.

Rule of thumb: prefer **validate + encode** over **sanitize**. Sanitization tends to mangle legitimate data (e.g. stripping `'` breaks `O'Brien`).

---

## 2. Where each rule lives

| Rule type | Layer | Why |
|---|---|---|
| Format (regex, email shape) | Controller / DTO (`@Email`, `@Pattern`) | Reject malformed before service runs |
| Length / size bounds | DTO (`@Size`) **and** DB (`VARCHAR(n)`) | DTO gives 400; DB makes it a fact |
| NOT NULL | DTO (`@NotBlank`) **and** DB (`NOT NULL`) | Same — defense in depth |
| Numeric bounds (price ≥ 0) | DTO (`@Min`, `@DecimalMin`) **and** DB CHECK | Bean Validation alone won't survive a direct SQL insert |
| Uniqueness | **DB** (`UNIQUE`), with service pre-check for friendly error | Only the DB wins under concurrency (TOCTOU race) |
| Business rules (stock vs cart) | Service | Cross-entity logic doesn't fit annotations |
| Resource limits (body size, JSON depth) | Reverse proxy + Spring properties + Jackson `StreamReadConstraints` | Must run *before* `@Valid` |

**Key insight**: code-level checks alone are *hopes*. DB constraints are *facts*. A future migration, admin tool, or sibling service can bypass code; it can't bypass `NOT NULL`.

---

## 3. Parameterized queries do NOT make validation redundant

Parameterized queries (JPA/Hibernate) make string content **safe for the SQL sink**. They do nothing for:

- **Other sinks**: HTML (XSS), filesystem paths (traversal), shell (command injection), regex (ReDoS), logs (log forging), HTTP headers (CRLF injection).
- **Business rules**: JPA happily inserts `price = -50`.
- **Storage shape**: oversized strings reach the DB and become 500s instead of clean 400s.

What you should **not** do: try to "sanitize SQL" by stripping quotes or `;`. Pointless and harmful.

---

## 4. Bean Validation (jakarta.validation) cheat sheet

```java
public record RegisterRequest(
    @Email @NotBlank @Size(max = 254) String email,
    @ValidPassword String password,
    @NotBlank @Size(min = 1, max = 100) String fullName
) {}
```

```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) { ... }
```

`@Valid` triggers validation. Failures throw `MethodArgumentNotValidException` — handle it in a `@RestControllerAdvice` and return a 400 with a structured body:

```json
{
  "error": "validation_failed",
  "fields": {
    "email": "must be a well-formed email",
    "password": "must be at least 12 characters"
  }
}
```

Frontend keys errors by field name. Never leak stack traces.

---

## 5. Mass assignment defense

Never bind directly to a JPA entity from a request. Always go through a DTO that contains **only** the fields a client may set.

```java
// BAD — client can set is_admin, created_at, anything on the entity
@PostMapping("/products")
public Product create(@RequestBody Product p) { ... }

// GOOD — DTO whitelists the fields
@PostMapping("/products")
public ProductResponse create(@Valid @RequestBody ProductCreateDTO dto) { ... }
```

Configure Jackson to fail on unknown fields so clients can't quietly attach extra keys:

```yaml
spring.jackson.deserialization.fail-on-unknown-properties: true
```

---

## 6. File upload checklist (avatar / product images)

Every check, every time:

1. **Size cap** — `spring.servlet.multipart.max-file-size: 5MB`
2. **Content-Type whitelist** — `image/jpeg`, `image/png`, `image/webp`
3. **Magic-byte sniff** — read first bytes, confirm it's actually that format. Never trust the extension or the Content-Type header.
4. **Dimension limits** — reject images > 4000×4000. Prevents "image bombs" (small file, huge decompressed pixels → OOM).
5. **Re-encode** with a library (e.g. Thumbnailator) — strips embedded scripts, EXIF exploits, malformed chunks.
6. **Generated filename** — UUID. Never use the user-supplied name (path traversal, overwrite).
7. **Serve from a separate path/origin** with `Content-Disposition: attachment` if possible — even if a malicious file slips through, it can't run as same-origin JS.

Storage strategy is separate: store the URL/path in the DB, the bytes on disk/S3.

---

## 7. JSON DoS — `@Valid` is not enough

`@Valid` runs **after** Jackson parses. By then a 200MB body has already been read into memory and a 50k-deep nested JSON has already blown the stack.

Defenses, in order:

1. **Reverse proxy** — `client_max_body_size` in nginx.
2. **Spring** — `spring.servlet.multipart.max-request-size`, `server.tomcat.max-http-form-post-size`.
3. **Jackson `StreamReadConstraints`** — max nesting depth, max string length, max number length. Apply globally:
   ```java
   ObjectMapper mapper = new ObjectMapper();
   mapper.getFactory().setStreamReadConstraints(
       StreamReadConstraints.builder()
           .maxNestingDepth(100)
           .maxStringLength(20_000_000)
           .maxNumberLength(1000)
           .build());
   ```
4. **Then** `@Valid` runs on already-bounded input.

---

## 8. Unicode normalization for passwords

Same visual `café` can be two byte sequences (NFC = `U+00E9`, NFD = `e + U+0301`). BCrypt hashes raw bytes → user registers on macOS, fails to log in on Windows.

**Fix**: normalize to NFC on **both** registration and login, before hashing.

```java
import java.text.Normalizer;
String normalized = Normalizer.normalize(password, Normalizer.Form.NFC);
String hash = encoder.encode(normalized);
```

Don't ban accented characters. That breaks legitimate passwords for millions of users.

Apply the same normalization to any user identifier compared as bytes (rare for emails since they're ASCII for the local part in practice, but worth knowing).

---

## 9. Defense in depth — the "two layers" principle

Most validation pairs follow the same shape: **one layer for UX/correctness, one for security/atomicity.**

| Pair | UX layer | Security layer |
|---|---|---|
| Zod (frontend) + Bean Validation (backend) | Instant feedback | Real check — clients can bypass |
| Redis ops + Lua script | Operations work | Atomicity under concurrency |
| BCrypt + rate limiting | Slow per guess | Cap total guesses |
| JWT + service auth + DB ownership | Identity | Resource ownership |

You can't skip the security layer. The UX layer is the bonus.

---

## 10. Quick checklist when adding a new endpoint

- [ ] DTO defined with `jakarta.validation` annotations
- [ ] Controller uses `@Valid @RequestBody`
- [ ] DTO has only fields the client may set (mass-assignment guard)
- [ ] DB constraints mirror DTO constraints (NOT NULL, length, CHECK)
- [ ] Uniqueness enforced at DB level if needed
- [ ] Resource limits set (body size, JSON depth) — once globally, not per endpoint
- [ ] `@RestControllerAdvice` returns structured 400 on validation failure
- [ ] If accepting files: full upload checklist (section 6)
- [ ] If accepting passwords: NFC normalize before hashing
- [ ] If output goes to HTML: encode at render time (React handles this for `{value}` but not `dangerouslySetInnerHTML`)
