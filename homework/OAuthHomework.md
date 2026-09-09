# OAuth — Study Material

Goal: explain what OAuth/OIDC is, why we chose a **frontend-driven ID-token** flow, and how login actually works in our stack. The rubric in [README.md](../README.md) lists "OAuth Integration" as a manual test and "implements both email-password and OAuth" as mandatory.

## Table of Contents

1. [OAuth vs OIDC in one minute](#1-oauth-vs-oidc-in-one-minute)
2. [Our flow (frontend-driven)](#2-our-flow-frontend-driven)
3. [Why not the Spring redirect flow](#3-why-not-the-spring-redirect-flow)
4. [What verification actually checks](#4-what-verification-actually-checks)
5. [Account model decisions](#5-account-model-decisions)
6. [Questions for the oral](#6-questions-for-the-oral)

---

## 1. OAuth vs OIDC in one minute

- **OAuth 2.0** = *authorization* — "this app may access X on your behalf." It hands out access tokens.
- **OpenID Connect (OIDC)** = a thin layer on top of OAuth for *authentication* — "who is this user." It adds the **ID token**: a signed JWT with the user's identity (`sub`, `email`, `name`).
- We only need to **know who the user is**, so we use the OIDC **ID token**. We do *not* call any Google API on the user's behalf, so we never store Google access/refresh tokens.

## 2. Our flow (frontend-driven)

```
SPA  --(Google sign-in popup)-->  Google
SPA  <--(signed ID token, a JWT)--  Google
SPA  --POST /auth/oauth/google {idToken}-->  Backend
Backend: verify token -> find or create user -> issue OUR JWT + refresh cookie
SPA  <--(our access token + httpOnly refresh cookie)--  Backend
```

The browser does the Google handshake; the backend only **verifies the resulting token** and then issues *our own* tokens — the exact same access-token + refresh-cookie pair as email/password login.

Where it lives in code:
- `GoogleOAuthConfig` — builds the `JwtDecoder` (Google's public keys + validators).
- `GoogleTokenVerifier` — decodes the ID token, maps claims → `OAuthUserInfo`.
- `AuthService.oauthLoginGoogle()` — verify → `findOrCreateOAuthUser` → `issueTokens`.
- `AuthController` `POST /auth/oauth/google` — the public endpoint.

## 3. Why not the Spring redirect flow

Spring's `oauth2Login` does the whole redirect dance for you, but it stores the OAuth `state`/PKCE in an **HTTP session** by default. Our architecture is **stateless** (JWT + Redis, no `HttpSession`) — see `backend/CLAUDE.md` §1. Reasons statelessness matters:

- **Horizontal scaling** — any backend instance serves any request; no sticky sessions.
- **Zero-downtime deploys** — restarting a container doesn't log users out.
- **Same mechanism for SPA / future mobile** — everyone uses `Authorization: Bearer`.

Frontend-driven keeps us stateless and reuses the token-issuing code we already built. The cost we pay for statelessness (can't instantly kill an access token) was already paid via short TTL + refresh rotation + JTI blocklist.

## 4. What verification actually checks

`GoogleOAuthConfig` configures the decoder to reject a token unless **all** hold:

| Check | Why | Attack it stops |
|---|---|---|
| **Signature** (against Google's JWKS) | Proves Google issued it | Forged tokens |
| **Issuer** = `accounts.google.com` | Proves *Google*, not some other IdP | Token from a different issuer |
| **Audience** = our `GOOGLE_CLIENT_ID` | Proves it was minted **for our app** | Token stolen from another Google app |
| **Expiry** (`exp`) | Limits replay window | Old captured tokens |

The audience check is the one the **automated tests can't verify** (they mock the decoder) — only a real login proves the client id is wired correctly. `GOOGLE_CLIENT_ID` is an env var (in `.env`, passed via `docker-compose.yml`), never hardcoded.

## 5. Account model decisions

- **`password_hash` is nullable** (migration `V2`) because OAuth users have no password. A DB **CHECK** still forces LOCAL accounts to have one: `auth_provider <> 'LOCAL' OR password_hash IS NOT NULL`.
- **Login guard**: `AuthService.login` rejects password login for any non-LOCAL account *before* touching the (null) hash — defense in depth alongside the CHECK and BCrypt's fail-closed behaviour.
- **No silent account linking**: if a Google email matches an existing account, we return **409**, not "link and log in." Auto-linking on email is an account-takeover vector.
- A returning user is matched by `(auth_provider, oauth_provider_id)` — the Google `sub`, which is stable, not the email (emails can change).

## 6. Questions for the oral

1. **OAuth vs OIDC — which do we use and why?** OIDC's ID token; we only need identity, not API access on the user's behalf.
2. **Walk the login flow.** SPA gets ID token from Google → POSTs to `/auth/oauth/google` → backend verifies → find-or-create user → issues our JWT + refresh cookie.
3. **Why frontend-driven instead of Spring's redirect login?** Spring's default keeps server-side session state; we're stateless (scaling, deploys, SPA/mobile). Frontend-driven stays stateless and reuses our token machinery.
4. **What does token verification check?** Signature (Google JWKS), issuer, audience (= our client id), expiry.
5. **Why does the audience check matter?** Without it, a token Google minted for *another* app would be accepted here — we'd trust identities not meant for us.
6. **A Google user signs in with an email that's already a password account — what happens?** 409, no linking. Why: silent linking enables account takeover.
7. **Do we store Google's tokens?** No. We verify the ID token once and issue our own; nothing from Google is persisted.
8. **How can a password account have a NULL password but not break the rule?** Column is nullable, but a DB CHECK requires `password_hash` for LOCAL accounts only; OAuth accounts are exempt.
9. **How do we recognise a returning OAuth user?** By `(auth_provider, oauth_provider_id)` (the stable Google `sub`), not by email.
10. **What does the automated test *not* prove?** The real Google config (JWKS/issuer/audience) — tests mock the decoder, so a manual sign-in is still required.

## Resources

- [OpenID Connect core](https://openid.net/specs/openid-connect-core-1_0.html)
- [Google Identity — ID tokens](https://developers.google.com/identity/openid-connect/openid-connect)
- [Spring Security — JwtDecoder / NimbusJwtDecoder](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
