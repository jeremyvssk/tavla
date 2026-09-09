# Password Recovery — Study Material (Quick Reference)

Goal: the short version of the "forgot password" flow for fast revision. For the full walkthrough — MailHog, Spring wiring, code, prod changes — see **[passwordResetHomework.md](passwordResetHomework.md)**. The rubric in [README.md](../README.md) lists password recovery via email as a requirement.

## Table of Contents

1. [The flow](#1-the-flow)
2. [The five decisions](#2-the-five-decisions)
3. [Where it lives in code](#3-where-it-lives-in-code)
4. [Questions for the oral](#4-questions-for-the-oral)

---

## 1. The flow

```
POST /auth/forgot-password {email}
   -> ALWAYS return 200 (don't leak who has an account)
   -> if user exists: random 32-byte token T;
      store SHA-256(T) in Redis  pwreset:{hash} -> userId, TTL 15 min;
      email a link containing raw T

POST /auth/reset-password {token: T, newPassword}
   -> SHA-256(T) -> look up Redis key; missing -> 400
   -> NFC-normalize + BCrypt newPassword -> users.password_hash
   -> DEL the Redis key (single-use)
   -> revoke ALL refresh tokens for the user
   -> 200
```

Both endpoints are **public** (the user isn't logged in).

## 2. The five decisions

| Decision | Choice | Why |
|---|---|---|
| Token store | Redis, fingerprinted with SHA-256 | High-entropy + server-generated → fast hash is enough (BCrypt is for human passwords). A Redis dump leaks fingerprints, not live tokens. |
| TTL | 15 min | Long enough for an email round-trip, short enough that a stale mailbox is useless. |
| Single-use | `DEL` key after reset | A reset token must not work twice. |
| Leak existence? | No — `/forgot-password` always 200 | Blocks account enumeration. |
| Existing sessions | Revoke **all** refresh tokens | The usual reason to reset is "someone got in" — their sessions must die. (Access tokens live until their own short `exp`.) |

The last one is the one people forget: without it the reset is theatre — the attacker stays logged in.

## 3. Where it lives in code

- `PasswordResetController` — `POST /auth/forgot-password`, `POST /auth/reset-password`.
- `PasswordResetService` — token gen (`SecureRandom`), SHA-256, Redis store, BCrypt update, revoke-all.
- `EmailService` — sends via `JavaMailSender` → MailHog in dev (`http://localhost:8025` to read it).
- Redis: `pwreset:{hash}` → userId, TTL 900s. Revoke-all iterates `refresh_tokens_user:{userId}`.
- `InvalidResetTokenException` → **400** via the `@RestControllerAdvice`.

## 4. Questions for the oral

1. **Why is `/forgot-password` always 200?** Prevents account enumeration — probing emails reveals nothing.
2. **Why SHA-256 the token in Redis but BCrypt the password?** Token is high-entropy/server-made (fast hash fine); passwords are low-entropy/human (need a slow hash). A Redis dump then yields no usable tokens.
3. **Why 15-minute TTL and single-use?** Bounds the replay window; a used or stale token does nothing.
4. **Why revoke all refresh tokens on reset?** The common trigger is account compromise — existing sessions must be killed or the reset is meaningless.
5. **Why don't access tokens die instantly too?** They're verified by signature math, not looked up; the short `exp` bounds their survival (the tradeoff for not hitting Redis per request).
6. **What does MailHog do in prod?** Nothing — swap the `SMTP_HOST` env var to SendGrid/SES; no code change.

## Resources

- **[passwordResetHomework.md](passwordResetHomework.md)** — the full version (code, Spring wiring, prod migration, self-check questions).
- [OWASP — Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
