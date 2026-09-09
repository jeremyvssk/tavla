# Input Validation — Study Material

Targeted at the gaps from the last homework session. Read each section, then re-attempt the question at the end of it without looking.

## Table of Contents

1. [Topic 1 — "Sinks" (Q20)](#topic-1--sinks-q20)
2. [Topic 2 — Where rules belong, and TOCTOU (Q21)](#topic-2--where-rules-belong-and-toctou-q21)
3. [Topic 3 — File upload validation (Q22)](#topic-3--file-upload-validation-q22)
4. [Topic 4 — Where in the request lifecycle damage occurs (Q23)](#topic-4--where-in-the-request-lifecycle-damage-occurs-q23)
5. [Topic 5 — Unicode normalization (Q24)](#topic-5--unicode-normalization-q24)
6. [Topic 6 — The "two layers" principle (Q26)](#topic-6--the-two-layers-principle-q26)
7. [Final exercise](#final-exercise)

---

## Topic 1 — "Sinks" (Q20)

A **sink** is a place where data leaves your code and gets *interpreted* by something else. Each sink has its own rules.

| Sink | Interpreter | What can go wrong |
|---|---|---|
| SQL query | Database engine | SQL injection |
| HTML page | Browser | XSS |
| File path | Operating system | Path traversal (`../../etc/passwd`) |
| Shell command | `/bin/sh` | Command injection |
| Regex | Regex engine | ReDoS (catastrophic backtracking) |
| HTTP header | Browser / proxy | CRLF injection, header smuggling |
| Log file | Log reader | Log forging (fake log lines) |

**Parameterized queries** are a fix for *one* sink: SQL. They send the query and the data on separate channels so the data is never parsed as SQL.

But that string is still going to other places. Example: a product `name` field.

```java
// Safe for SQL — JPA parameterizes it
productRepo.save(new Product(userInput));

// Unsafe for HTML — needs encoding when rendered
<h1>{product.name}</h1>   // React encodes this automatically
<h1 dangerouslySetInnerHTML={...}>...</h1>   // BYPASSES React's encoding — danger
```

**Rule**: validate at the *boundary* (input), encode at the *sink* (output). Different sinks, different encoders.

**Self-check**: A user sets `full_name = <script>alert(1)</script>`. Your DB query is parameterized. Is the system safe?
*(Answer: depends on where `full_name` is rendered. The DB write is safe. Rendering it as HTML without encoding is XSS.)*

---

## Topic 2 — Where rules belong, and TOCTOU (Q21)

**TOCTOU** = Time Of Check To Time Of Use. The classic uniqueness race:

```
T=0ms   Request A: SELECT * FROM users WHERE email='x@y' → empty, OK
T=1ms   Request B: SELECT * FROM users WHERE email='x@y' → empty, OK
T=2ms   Request A: INSERT ... → success
T=3ms   Request B: INSERT ... → success    ← duplicate, no error caught
```

The check passed for both because nothing held a lock between check and insert. Only the **DB's UNIQUE constraint** is atomic with the insert. The service-layer check exists only to give a friendly message *before* hitting the constraint — it's UX, not security.

Pattern:

```java
if (userRepo.existsByEmail(email)) {
    throw new EmailAlreadyTakenException();   // friendly path
}
try {
    userRepo.save(new User(email, ...));
} catch (DataIntegrityViolationException e) {
    throw new EmailAlreadyTakenException();   // race-condition catch-net
}
```

**Why DB constraints matter beyond races**: code can be bypassed. A migration script, a `psql` session, a sibling microservice, an admin UI — all write directly to the table. If `NOT NULL` is only in your `@NotBlank` annotation, those writers happily insert NULL. Then your `getName().toLowerCase()` throws NPE, far from the cause.

**Self-check**: regex format, uniqueness, NOT NULL — name the layer for each *and the failure mode if you skip it*.

---

## Topic 3 — File upload validation (Q22)

The big idea: **never trust anything the client sends about the file.** Not the name, not the extension, not the Content-Type header.

### Magic bytes
Every real image starts with a known byte signature:
- JPEG: `FF D8 FF`
- PNG: `89 50 4E 47 0D 0A 1A 0A`
- WebP: bytes 8–11 are `WEBP`

Read the first ~12 bytes server-side and compare. Java has libraries for this (`Apache Tika`, `java.nio.file.Files.probeContentType`).

### Image bombs
A 100KB PNG can decompress to 50,000 × 50,000 pixels = 7.5 GB in memory once decoded. Your image library OOMs.

Defense: use a library that lets you read *dimensions only* without decoding the full pixel buffer (e.g. `ImageIO.getImageReaders()` + `ImageReader.getWidth()`), reject if too big, *then* fully decode.

### Polyglot files
A file can be valid JPEG **and** valid HTML/JS at the same time. Browser sees `.jpg`, ignores it. But if you serve it from your domain and someone navigates directly to it with a different Content-Type, the browser may execute the JS. Defenses:

- **Re-encode the image** through a library — output is a fresh, clean image. Any embedded script payload is gone.
- Serve uploads from a separate domain (e.g. `usercontent.example.com`) so it can't access cookies on `example.com`.
- Set `Content-Disposition: attachment` to force download instead of execute.

### Filename
Always replace user-supplied names with a UUID. `../../etc/passwd` as a filename is the simplest path traversal in the world.

**Self-check**: list 5 distinct backend checks for an avatar upload and the *attack each prevents*.

---

## Topic 4 — Where in the request lifecycle damage occurs (Q23)

Order of execution for a JSON POST:

1. **Reverse proxy** (nginx) reads the request body off the socket.
2. **Servlet container** (Tomcat) buffers the body.
3. **Spring** hands the body to **Jackson**.
4. **Jackson** parses bytes → object tree → maps to your DTO.
5. **`@Valid`** runs annotations on the populated DTO.
6. Your **controller method** runs.

A 200MB body is consumed at step 1–2. Your `@Size(max = 100)` at step 5 never runs because the JVM is OOM at step 2.

A 50,000-deep `[[[[[...]]]]]` causes a stack overflow at step 4.

**Fix per layer**:

| Layer | Setting |
|---|---|
| Nginx | `client_max_body_size 1m;` |
| Spring | `spring.servlet.multipart.max-request-size=1MB`, `server.tomcat.max-http-form-post-size=1048576` |
| Jackson | `StreamReadConstraints` — `maxNestingDepth`, `maxStringLength`, `maxNumberLength` |
| `@Valid` | semantic rules (length, format, range) |

**Mental model**: `@Valid` is for *application logic*. Resource exhaustion is a *transport-layer* concern that has to be solved before logic runs.

**Self-check**: where in the lifecycle does a 50k-deep JSON cause damage, and what stops it?

---

## Topic 5 — Unicode normalization (Q24)

### The problem

Visually identical strings can have different byte representations.

`é` is two things in Unicode:
- **NFC** (composed): one code point `U+00E9`. UTF-8 bytes: `C3 A9`.
- **NFD** (decomposed): two code points `e` (`U+0065`) + combining acute (`U+0301`). UTF-8 bytes: `65 CC 81`.

Your eyes can't tell the difference. The bytes are different. BCrypt hashes bytes.

```
Registration on Windows browser → password "café" sent as NFC → BCrypt(C3 A9) → hash A
Login on macOS browser          → password "café" sent as NFD → BCrypt(65 CC 81) → hash B
```

`hash A != hash B`. Login fails. User screams.

### Why platforms differ

- macOS HFS+/APFS historically stored filenames as NFD. Some macOS input methods produce NFD output.
- Windows and most Linux systems use NFC.
- Modern browsers vary — some normalize, some don't, depending on input method (typed vs pasted vs IME).

You can't predict what arrives. Normalize on the server.

### The fix

```java
import java.text.Normalizer;

String normalized = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
// Hash this. Always.
```

Apply on both `register` and `login`. Both sides must agree.

Don't ban accents. `José`, `François`, `Müller`, `Łukasz` are real names with real passwords.

**Self-check**: what is BCrypt receiving differently across platforms, and what's the one-line fix?

---

## Topic 6 — The "two layers" principle (Q26)

Each defensive pair has the same structure:

> **Layer 1 makes the common case good. Layer 2 makes the adversarial case safe.**

If you skip layer 1, life is annoying but secure.
If you skip layer 2, life is smooth but insecure.

| Pair | Layer 1 (good UX) | Layer 2 (real defense) | What breaks if you skip layer 2 |
|---|---|---|---|
| Zod + Bean Validation | Form errors appear instantly without a round-trip | Server enforces the rule | curl bypasses Zod, attacker sends arbitrary JSON |
| Redis ops + Lua | Individual GET/SET/DEL each work | Lua makes the *sequence* atomic | Two `/auth/refresh` calls 5ms apart both succeed → token theft undetectable |
| BCrypt + rate-limit | BCrypt makes one guess slow (~250ms) | Rate-limit caps total guesses | Attacker with a botnet runs 1000 parallel BCrypt-slow guesses per second |
| JWT + service auth + DB ownership | JWT proves *identity* | Service/DB check proves *ownership* | Valid JWT for user A reads user B's order by changing `id` in URL |

### When two layers is over-engineering

- **Single-user CLI tool** running locally — DB constraint alone is enough.
- **Internal admin script** behind VPN, no network exposure — service-layer auth is enough.
- **Throwaway prototype** for a class demo, no real users — Bean Validation alone, skip Zod.

The principle: **layer when there's a real adversary or a real concurrency boundary.** Don't layer for the sake of looking thorough.

**Self-check**: pick one of the four pairs above. Describe a scenario where layer 1 alone is fine. Describe one where layer 2 alone (no UX layer) is fine.

---

## Final exercise

Re-attempt the original questions, closed-book, after reading:

20. JPA params already prevent SQLi. Where does string validation still earn its keep?
21. Email — regex, uniqueness, NOT NULL — name the layer for each. What fails at runtime if NOT NULL is only in code?
22. Avatar upload — 5 checks, 5 attacks.
23. 200MB body and 50k nesting — why is `@Valid` insufficient and what stops them?
24. NFC vs NFD — what is BCrypt receiving and what's the fix?
26. Pick two of the "two layers" pairs and articulate what each layer is for.

When you can answer these without looking, you're done with this topic.
