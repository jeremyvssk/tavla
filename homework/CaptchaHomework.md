# CAPTCHA — Study Material

Goal: explain what reCAPTCHA is, why it guards **registration**, and how our verification works — including why it ships **disabled by default**. The rubric in [README.md](../README.md) lists CAPTCHA on registration as a requirement.

## Table of Contents

1. [What CAPTCHA solves](#1-what-captcha-solves)
2. [Our flow](#2-our-flow)
3. [Site key vs secret key](#3-site-key-vs-secret-key)
4. [The feature flag — why it ships off](#4-the-feature-flag--why-it-ships-off)
5. [What verification checks](#5-what-verification-checks)
6. [Where it lives in code](#6-where-it-lives-in-code)
7. [Questions for the oral](#7-questions-for-the-oral)

---

## 1. What CAPTCHA solves

CAPTCHA = "tell humans and bots apart." On **registration** it stops automated mass account creation (credential stuffing prep, spam accounts, abuse). It is **not** a rate limiter and not a login defense — it's one specific bot speed-bump on the signup endpoint. (Real rate limiting is a separate, still-open concern — see [CLAUDE.md](../CLAUDE.md) Open Questions.)

## 2. Our flow

```
Browser renders reCAPTCHA widget (uses SITE key) -> user solves it
Browser gets a one-time token -> sends it in POST /auth/register { ..., captchaToken }
Backend: CaptchaService.verify(token)
   -> POST {secret, response:token} to google.com/recaptcha/api/siteverify
   -> Google replies { success: true|false }
   -> success=false or token missing -> 400 InvalidCaptchaException (registration aborts)
   -> success=true -> registration proceeds
```

The browser does the human-check; the backend only **verifies the resulting token with Google** before doing any real work. `verify()` runs *before* `authService.register()`, so a failed CAPTCHA never creates a user.

## 3. Site key vs secret key

| Key | Lives where | Purpose |
|---|---|---|
| **Site key** | Frontend (public) | Renders the widget, identifies the site to Google |
| **Secret key** | Backend only (`RECAPTCHA_SECRET_KEY` env var) | Proves to Google's `siteverify` it's really us asking |

The secret key is an **env var, never hardcoded or committed** (per [backend/CLAUDE.md §2](../backend/CLAUDE.md)). The token itself is **single-use and short-lived** — it can't be replayed for a second signup.

## 4. The feature flag — why it ships off

`app.recaptcha.enabled` defaults to `false` (`RECAPTCHA_ENABLED`). When off, `verify()` is a **no-op** and returns immediately.

Why: dev / CI / Testcontainers have no real reCAPTCHA keys, and a browser-solved token can't be produced in an automated test. Shipping the **real integration code** behind a flag means:
- registration works locally and tests stay deterministic (no external call to Google),
- flipping `RECAPTCHA_ENABLED=true` + supplying `RECAPTCHA_SECRET_KEY` activates it in prod with **zero code change** (12-factor config, not an `if (prod)` branch).

## 5. What verification checks

`CaptchaService.verify(token)`:

| Step | Why |
|---|---|
| Flag off → return | Lets dev/test run without keys |
| Token blank → `InvalidCaptchaException` | Missing proof = treat as failed |
| POST `secret` + `response` to Google `siteverify` | Google decides if the solve was valid |
| `success != true` → `InvalidCaptchaException` | Reject bots / forged / expired tokens |

Note: `SiteVerifyResponse` is annotated `@JsonIgnoreProperties(ignoreUnknown = true)` because Google returns extra fields (`challenge_ts`, `hostname`, `error-codes`) and our Jackson is globally set to **fail on unknown properties** — without the annotation, parsing the reply would throw.

## 6. Where it lives in code

- `CaptchaService` — the flag check + Google `siteverify` call (uses `RestClient`, configured in `RestClientConfig`).
- `AuthController.register` — calls `captchaService.verify(request.captchaToken())` first.
- `RegisterRequest.captchaToken()` — the field carrying the token.
- `InvalidCaptchaException` → mapped to **400** by the `@RestControllerAdvice`.
- `application.yml` → `app.recaptcha.{enabled,secret-key,verify-url}`.

## 7. Questions for the oral

1. **What does CAPTCHA actually protect against here?** Automated bulk account creation on `/auth/register`. Not rate limiting, not login.
2. **Walk the flow.** Browser solves widget → token → `POST /auth/register` → backend verifies token+secret with Google's `siteverify` → proceed only if `success`.
3. **Site key vs secret key?** Site key is public (frontend widget); secret key is server-only (env var) and authenticates our `siteverify` call.
4. **Why verify *before* creating the user?** A failed/forged CAPTCHA must not have side effects — no user row.
5. **Why does it ship disabled?** No real keys in dev/CI and no browser to solve it in tests; the flag keeps signup + tests working while the real integration still ships. Prod flips one env var.
6. **Why is the secret key not in code?** It's a secret — env var only, never committed (12-factor / [backend/CLAUDE.md §2](../backend/CLAUDE.md)).
7. **Why the `@JsonIgnoreProperties` on the response record?** Google sends extra fields and our Jackson fails on unknown properties by default.
8. **Is CAPTCHA enough to stop abuse?** No — it raises the cost of bots but you still want rate limiting; a solved-token-for-hire service can bypass it.

## Resources

- [Google reCAPTCHA — verifying the response](https://developers.google.com/recaptcha/docs/verify)
- [OWASP — Automated Threats (account creation abuse)](https://owasp.org/www-project-automated-threats-to-web-applications/)
- [Spring `RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
