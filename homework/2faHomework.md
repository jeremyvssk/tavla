# Two-Factor Auth (TOTP) — Study Material

Goal: explain TOTP 2FA end-to-end — **enrollment** (setup → enable), **backup codes**, and the **two-step login challenge** — and how each piece wires into the stateless auth already in [CLAUDE.md](../CLAUDE.md). The rubric in [README.md](../README.md) lists 2FA as a requirement.

## Table of Contents

1. [What TOTP is in one minute](#1-what-totp-is-in-one-minute)
2. [Enrollment: setup then enable](#2-enrollment-setup-then-enable)
3. [Backup codes](#3-backup-codes)
4. [Login: the challenge flow](#4-login-the-challenge-flow)
5. [Disable](#5-disable)
6. [What's stored where](#6-whats-stored-where)
7. [Questions for the oral](#7-questions-for-the-oral)

---

## 1. What TOTP is in one minute

**TOTP** (Time-based One-Time Password, RFC 6238) = a shared secret + the current 30-second time window, hashed into a 6-digit code. The authenticator app (Google Authenticator, Authy) and the server both hold the secret and both compute the same code independently — a matching code proves the user has the secret. Nothing is sent over the network at enrollment; the secret is shared once via a QR code.

We use the `dev.samstevens.totp` library. The QR code is encoded as an `otpauth://` URI (`SHA1`, 6 digits, 30s period, plus our issuer label).

## 2. Enrollment: setup then enable

Two authenticated steps — and the order matters:

```
POST /auth/2fa/setup    -> generate secret, store it, return { secret, otpauthUri }
                           (2FA is still OFF) ; user scans QR into their app
POST /auth/2fa/enable {code}
                        -> verify the FIRST code against the secret
                        -> flip two_factor_enabled = true
                        -> generate 8 backup codes, return them ONCE
```

**Why two steps?** Setup alone doesn't turn 2FA on. We only enable it after the user submits a working code — proof their authenticator is correctly configured. Enabling blindly would risk **locking the user out** of their own account.

## 3. Backup codes

On `enable`, we generate **8 one-time backup codes** and return them in plaintext **exactly once** (the user must save them). They let a user log in if they lose their phone.

Stored as **SHA-256 hashes** (comma-joined) — never plaintext. Same reasoning as reset tokens: they're server-generated and high-entropy, so a fast hash is enough; BCrypt is for *low-entropy human passwords* (see [passwordResetHomework.md](passwordResetHomework.md)). Each code is **single-use**: on a successful login with a backup code, its hash is removed from the stored set so it can't be reused.

## 4. Login: the challenge flow

A 2FA user can't get tokens from password alone — login becomes two requests with a short-lived **partial session** in Redis:

```
POST /auth/login {email, password}
   password OK + 2FA on  ->  NO tokens. Create challenge:
        random 32-byte token; store SHA-256(token) in Redis
        2fa_pending:{hash} -> userId, TTL 5 min
   return { twoFactorRequired: true, challenge }

POST /auth/2fa/login {challenge, code}
   peek challenge (does NOT consume) -> resolve userId
   verifyCode: current TOTP?  or  unused backup code (consume it)?
   wrong  -> 401, challenge still alive -> user can retry within TTL
   right  -> consume challenge (single-use), issue access token + refresh cookie
```

Key design points:
- **Peek vs consume.** A wrong code must not burn the challenge, or a typo logs you out. The challenge is consumed **only on success**; otherwise it just expires after 5 min.
- **Partial session lives in Redis, not a server session** — keeps the app stateless (any instance can complete the second step). Same hashing trick as refresh/reset tokens: only `SHA-256(token)` is stored, so a Redis dump yields no usable challenges.
- **Real JWTs only after the second factor** — the first step proves *knowledge* (password), the second proves *possession* (the phone).

## 5. Disable

`POST /auth/2fa/disable {password}` — re-confirms the **account password** before turning 2FA off. Re-auth on a security-downgrade action stops someone with a borrowed, already-logged-in session from silently removing 2FA.

## 6. What's stored where

| Data | Where | Form |
|---|---|---|
| TOTP secret | Postgres `users.two_factor_secret` | plaintext* |
| 2FA on/off | Postgres `users.two_factor_enabled` | boolean |
| Backup codes | Postgres `users.two_factor_backup_codes` | SHA-256 hashes, comma-joined |
| Login challenge | Redis `2fa_pending:{hash}` | SHA-256(token) → userId, TTL 5 min |

\*The TOTP secret is **not hashed** — the server must reproduce it to verify each code (unlike a password, which is only ever compared). The proper hardening is **encryption at rest** (e.g. column encryption); we don't do that yet — name it honestly as a known gap if asked.

## 7. Questions for the oral

1. **What is TOTP and why is it "something you have"?** Shared secret + time window → 6-digit code; both sides compute it. Holding the secret (in the phone) is the possession factor.
2. **Why is enrollment two steps?** `setup` stores the secret but leaves 2FA off; `enable` only flips it on after a valid first code proves the authenticator works — avoids lockout.
3. **Walk the 2FA login.** `/auth/login` returns a challenge (no tokens); `/auth/2fa/login` verifies a TOTP/backup code against that challenge, then issues tokens.
4. **Why peek the challenge instead of consuming it on first use?** So a wrong code can be retried within the TTL; consume only on success.
5. **Where does the partial session live and why there?** Redis with a 5-min TTL — keeps the app stateless so any instance completes the login.
6. **How are backup codes stored, and why not BCrypt?** SHA-256 hashes, single-use; they're high-entropy and server-generated, so a fast hash suffices (BCrypt is for human passwords).
7. **Why re-confirm the password to disable 2FA?** It's a security downgrade; re-auth blocks a hijacked active session from removing it.
8. **Why is the TOTP secret stored in plaintext when passwords aren't?** The server must recompute TOTP, so it must be reversible — passwords only need comparison. Encrypting the secret at rest is the next step.
9. **What stops a stolen Redis snapshot from giving working challenges?** Only `SHA-256(token)` is stored; the raw token only ever lives in the client response.

## Resources

- [RFC 6238 — TOTP](https://datatracker.ietf.org/doc/html/rfc6238)
- [`dev.samstevens.totp` library](https://github.com/samdjstevens/java-totp)
- [OWASP — Multifactor Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
