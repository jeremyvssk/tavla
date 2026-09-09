# Password Reset via Email — Study Material

Goal: understand the full "forgot password" flow end-to-end, why each piece exists, and exactly how it wires into the stack already in [CLAUDE.md](../CLAUDE.md). After reading this you should be able to walk a reviewer through the flow from "user clicks Forgot Password" to "user logs in with new password and all old sessions are dead."

## Table of Contents

1. [The flow at a glance](#1-the-flow-at-a-glance)
2. [The five design decisions](#2-the-five-design-decisions)
3. [MailHog — the dev email sink](#3-mailhog--the-dev-email-sink)
4. [Spring wiring](#4-spring-wiring)
5. [Redis key shape](#5-redis-key-shape)
6. [Token generation and storage](#6-token-generation-and-storage)
7. [The two endpoints](#7-the-two-endpoints)
8. [Revoking all refresh tokens](#8-revoking-all-refresh-tokens)
9. [What changes in prod](#9-what-changes-in-prod)
10. [Cheat-sheet answers for the oral](#cheat-sheet-answers-for-the-oral)
11. [Self-check questions](#self-check-questions)
12. [Resources](#resources)

---

## 1. The flow at a glance

```
User clicks "Forgot password"
      |
      v
POST /auth/forgot-password { email }
      |
      |--> Always return 200 (don't leak existence)
      |--> If user exists:
      |       generate random token T
      |       store SHA-256(T) in Redis: pwreset:{hash} -> user_id, TTL 15 min
      |       send email containing https://app/reset?token=T
      v
(... user opens email, clicks link, lands on /reset page ...)
      |
      v
POST /auth/reset-password { token: T, newPassword }
      |
      |--> SHA-256(T) -> look up Redis key
      |--> Found?  No -> 400 invalid/expired
      |--> Found?  Yes:
      |       NFC-normalize newPassword, BCrypt it, write to users.password_hash
      |       DEL Redis key (single-use)
      |       Iterate refresh_tokens_user:{userId} set, DEL every refresh token
      |       Return 200
      v
User logs in fresh with new password. Every previously-logged-in device is now signed out.
```

## 2. The five design decisions

| Decision | Choice | Why |
|---|---|---|
| Where is the token stored? | Redis, fingerprinted with SHA-256 | Server-generated, high-entropy — no need for BCrypt (see [dbHomework.md §BCrypt asymmetry](../homework/dbHomework.md) and history). Same pattern as refresh tokens. |
| Token lifetime (TTL)? | 15 minutes | Long enough for the user to find the email and click. Short enough that a leaked email archive months later is useless. |
| Single-use? | Yes — `DEL` the Redis key after successful reset | A used reset token must not work twice. If the email leaks after the reset, replay does nothing. |
| Leak whether the email exists? | No — `/forgot-password` always returns 200 | Prevents account enumeration. An attacker probing 1M emails learns nothing about which are registered. |
| What about already-logged-in sessions? | Revoke ALL refresh tokens for that user | If someone reset because their laptop was stolen, the thief's existing session must die. Access tokens still live until their own `exp` (max 60 min) — that's why access tokens are short-lived. |

The fifth point is the one most people miss. Without it, the reset is theatre: the thief is still logged in.

## 3. MailHog — the dev email sink

MailHog is a fake SMTP server with a web UI. It accepts any email and shows it in a browser. **It never delivers anywhere.** Perfect for development — you can't accidentally email a real user.

In `docker-compose.yml`:

```yaml
mailhog:
  image: mailhog/mailhog
  ports:
    - "1025:1025"   # SMTP — Spring sends here
    - "8025:8025"   # Web UI — you open this in a browser
```

Spring connects to `mailhog:1025` (service name resolves inside the Docker network). You open `http://localhost:8025` to see every email the app has sent. That's the entire dev workflow.

## 4. Spring wiring

### Dependencies (`backend/pom.xml`)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

This pulls in JavaMailSender, the standard Spring abstraction over SMTP.

### Config (`application.yml`)

```yaml
spring:
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT}
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
```

In `.env` (dev):

```
SMTP_HOST=mailhog
SMTP_PORT=1025
```

In prod, `.env` points to SendGrid/SES/whatever — code stays identical. This is the 12-factor "attached resources" rule from [backend/CLAUDE.md §3](../backend/CLAUDE.md).

### The mail-sending service

```java
@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordReset(String to, String resetUrl) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Reset your password");
        msg.setText("Click to reset: " + resetUrl + "\n\nThis link expires in 15 minutes.");
        mailSender.send(msg);
    }
}
```

That's it for the mail side. `JavaMailSender` is auto-wired by Spring Boot because `spring-boot-starter-mail` is on the classpath and `spring.mail.host` is set.

## 5. Redis key shape

Add one new key family to the set already in [CLAUDE.md](../CLAUDE.md):

```
pwreset:{sha256_of_token}  ->  user_id   (TTL 900 seconds)
```

Mirrors the existing pattern:
- `refresh_token:{hash}` — server-generated, SHA-256, TTL
- `token_blocklist:{jti}` — JTI string, TTL
- `2fa_pending:{userId}` — challenge marker, TTL
- **`pwreset:{hash}` — new** — SHA-256, TTL 15 min

Same mental model: ephemeral auth state that should auto-expire.

## 6. Token generation and storage

```java
@Service
public class PasswordResetService {

    private static final Duration TTL = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final RefreshTokenStore refreshStore;   // already exists from auth work
    private final PasswordEncoder bcrypt;

    public void requestReset(String email) {
        var user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return;  // silent — don't leak existence

        String rawToken = generateToken();          // 32 random bytes, URL-safe base64
        String hash = sha256(rawToken);
        redis.opsForValue().set("pwreset:" + hash, user.getId().toString(), TTL);

        String url = "https://app.local/reset?token=" + rawToken;
        emailService.sendPasswordReset(email, url);
    }

    public void confirmReset(String rawToken, String newPassword) {
        String hash = sha256(rawToken);
        String key = "pwreset:" + hash;
        String userId = redis.opsForValue().get(key);
        if (userId == null) throw new InvalidResetTokenException();

        String normalized = Normalizer.normalize(newPassword, Normalizer.Form.NFC);
        String passwordHash = bcrypt.encode(normalized);
        userRepo.updatePasswordHash(UUID.fromString(userId), passwordHash);

        redis.delete(key);                          // single-use
        refreshStore.revokeAllForUser(UUID.fromString(userId));   // kill every session
    }

    private String generateToken() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
```

Two things worth pausing on:

- **Why send the raw token in the email but store only the hash in Redis?** Same logic as storing BCrypt of a password instead of the password: if Redis is dumped, attacker sees fingerprints, not working tokens. The raw token only ever exists in the email and in transit.
- **`SecureRandom`, not `Math.random()`** — predictable RNG = predictable reset tokens = account takeover by guessing.

## 7. The two endpoints

```java
@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private final PasswordResetService service;

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        service.requestReset(req.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        service.confirmReset(req.token(), req.newPassword());
        return ResponseEntity.ok().build();
    }
}

public record ForgotPasswordRequest(@Email @NotBlank String email) {}

public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 100) String newPassword
) {}
```

Both endpoints are **public** (no JWT required) — the user can't be logged in to use them. Note them as such per the new-endpoint checklist in [backend/CLAUDE.md §11](../backend/CLAUDE.md).

## 8. Revoking all refresh tokens

Already designed in [CLAUDE.md](../CLAUDE.md) — this is what `refresh_tokens_user:{userId}` is for. It's a Redis **Set** containing the hashes of every refresh token that user currently has. To revoke them all:

```java
public void revokeAllForUser(UUID userId) {
    String setKey = "refresh_tokens_user:" + userId;
    Set<String> hashes = redis.opsForSet().members(setKey);
    if (hashes == null) return;
    for (String h : hashes) {
        redis.delete("refresh_token:" + h);
    }
    redis.delete(setKey);
}
```

After this runs, every old device's next `/auth/refresh` call returns 401 because the key is gone. Their currently-held access token still works until its `exp` (15–60 min) — that's the tradeoff for not looking access tokens up in Redis on every request. If you need sharper revocation, also add every active JTI to the blocklist; for password reset that's usually overkill.

## 9. What changes in prod

Almost nothing. Three things:

1. `.env` points `SMTP_HOST` at SendGrid/SES instead of `mailhog`.
2. `SMTP_PORT` becomes 587 (STARTTLS) or 465 (SSL).
3. You add `mail.smtp.auth: true` and provide `spring.mail.username` / `spring.mail.password` env vars.

`EmailService.java`, `PasswordResetService.java`, the controller, and the Redis schema all stay byte-for-byte identical. That's the payoff of treating SMTP as an attached resource.

---

## Cheat-sheet answers for the oral

- **Walk me through password reset.** → `/forgot-password` always returns 200 to avoid leaking who has an account. If the email exists, we generate a 32-byte random token, store its SHA-256 in Redis with 15-min TTL keyed `pwreset:{hash}`, and email the raw token in a link. User clicks, posts the raw token + new password to `/reset-password`. We hash it, look up the Redis key, BCrypt the new password (after NFC normalize), `DEL` the Redis key, and revoke every refresh token for that user.
- **Why hash the reset token in Redis if Redis is internal?** → Defense in depth. If Redis is dumped or snapshotted, raw tokens would be live credentials. Hashes are useless.
- **Why is `/forgot-password` always 200?** → Prevents account enumeration. An attacker can't probe a list of emails to learn which are registered.
- **Why 15 minutes?** → Long enough for an email round-trip + user clicking. Short enough that a stolen mailbox months later is useless.
- **Why single-use?** → A token that worked twice is worth twice as much to an attacker. Once consumed, replay does nothing.
- **Why revoke all refresh tokens on reset?** → Because the most common reason to reset is "I think someone got into my account." If we don't kill their existing sessions, the reset is meaningless.
- **Why doesn't it kill access tokens immediately?** → Access tokens aren't stored — they're math. Killing them all instantly would require Redis lookups on every request, which we explicitly avoided for performance. The 15-min `exp` is the bound on how long a stolen access token can survive past a reset. Could also blocklist active JTIs if you want sharper guarantees.
- **What does MailHog do in prod?** → Nothing. Swap `SMTP_HOST` env var to point at SendGrid/SES. No code change.
- **Why `SecureRandom` not `Math.random`?** → Predictable RNG = predictable tokens = trivial account takeover.

---

## Self-check questions

Do these closed-book before moving on.

1. The user requests a reset for `nobody@nowhere.invalid` (not a registered email). What HTTP status do they see, and what side effects happened on the server? Why?
2. An attacker intercepts the reset email (e.g. via a compromised email provider). What can they do, and what's the time window? What stops them once the user resets?
3. Why is the reset token SHA-256'd in Redis but the user's password is BCrypt'd in Postgres? Why not the same algorithm for both?
4. The user clicks the reset link, gets to the new-password form, sets a new password successfully — and then a thief tries the same link 30 seconds later. What happens, and which line of code is responsible?
5. The user has the app open on their stolen laptop (active access token, 8 min until expiry). They reset their password from a safe machine. Walk through what the thief can and cannot do for the next 15 minutes.
6. Why does this flow not need a Lua script (unlike `/auth/refresh`)?
7. In prod, you switch `SMTP_HOST` from `mailhog` to `smtp.sendgrid.net`. Name every other code or config change required. (Hint: think about what changes about the SMTP connection itself.)

---

## Resources

**Spring**
- [Spring Boot Mail reference](https://docs.spring.io/spring-boot/reference/io/email.html)
- [JavaMailSender API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/mail/javamail/JavaMailSender.html)

**Tools**
- [MailHog GitHub](https://github.com/mailhog/MailHog) — `docker pull mailhog/mailhog`, web UI on :8025

**Background**
- [OWASP — Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) — the canonical list of dos and don'ts; mirrors most of the decisions above
