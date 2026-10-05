# Frontend

The website for i-love-shopping: React 18 and TypeScript, built with Vite and managed with Bun.

You don't need to run anything in this folder to use the site. `./start.sh` in the project root
builds it, and nginx serves it at <http://localhost:5173>. This page explains where things live.
Every file starts with a one-line comment saying what it does.

---

## Folders (`src/`)

| Folder | What's in it |
|---|---|
| `main.tsx` | Starting point: wraps the app in Redux, React Query and the router |
| `App.tsx` | The list of pages (routes), and restoring the login after a page reload |
| `pages/` | One file per page (see below) |
| `components/` | Pieces used on several pages: header and footer (`Layout`), search box, product cards, cart panel, form fields, the CAPTCHA and Google buttons |
| `api/` | Every call to the backend. `client.ts` is the shared Axios setup |
| `auth/session.ts` | Starting, restoring and ending a login session |
| `store/` | Redux state: who is logged in (`authSlice`) and the cart (`cartSlice`) |
| `validation/` | Form rules (email format, password length...), copied from the backend's rules |
| `hooks/`, `lib/` | Small helpers: delaying search input, loading Google's scripts, formatting prices, picking colour/size variants, placing a photo on its tile (`framing.ts`, used by `components/Photo.tsx`) |
| `test/` | Test setup and a helper that renders a page with everything it needs |
| `index.css` | All styles and colours |

## Pages

| Address | File | Shows |
|---|---|---|
| `/` | `HomePage` | The landing page with featured products |
| `/catalog` | `CatalogPage` | Browsing and search results, with filters and sorting |
| `/catalog/:id` | `ProductPage` | One product: photos, variants, specs, reviews, add to cart |
| `/login`, `/register` | `LoginPage`, `RegisterPage` | Log in (plus the 2FA step) and sign up |
| `/forgot`, `/reset` | `ForgotPasswordPage`, `ResetPasswordPage` | Password reset |
| `/account` | `AccountPage` | Profile and two-factor setup. Logged-in users only |

Page addresses never start with an API path like `/products` or `/auth`. Those paths go to the
backend, not to the website.

---

## How login works here

- **The access token is kept only in a variable** in `api/client.ts`, never in `localStorage`, so
  a malicious script can't read it from storage. Redux stores *who* is logged in, but never the
  token.
- **On page reload** the variable is empty, so `auth/session.ts` asks `/auth/refresh` for a new
  token. The refresh token is in a cookie the browser sends automatically.
- **When a token expires**, `client.ts` gets a new one and retries the request. If several requests
  fail at once, they share a single refresh.

**Data from the server** (products, reviews, categories) is loaded and cached with **React Query**.
**The cart** lives in Redux and is saved to `localStorage`. It holds no secrets and has no server
side until checkout exists (Project 2).

---

## Running it on its own (for development)

```sh
docker compose stop frontend   # free port 5173; the backend keeps running
bun install
bun run dev                    # http://localhost:5173, reloads on every save
```

The dev server forwards API calls to the backend on port 8080, the same way nginx does in Docker
(`vite.config.ts`). If you add a new API path prefix, add it in both `vite.config.ts` and
`nginx.conf`.

Google login and CAPTCHA appear only when `VITE_GOOGLE_CLIENT_ID` and `VITE_RECAPTCHA_SITE_KEY`
are set. With Docker, these come from `GOOGLE_CLIENT_ID` and `RECAPTCHA_SITE_KEY` in the root
`.env`. See the main [README](../README.md#needs-your-own-google-keys).

## Tests

```sh
bun run test         # Vitest, re-runs on save
bunx vitest run      # run once
```

Use `bun run test`, not `bun test`, which starts Bun's own test runner instead of Vitest.

Test files sit next to the file they test (`*.test.ts`, `*.test.tsx`):

| Test | Checks |
|---|---|
| `validation/authRules.test.ts` | The form rules at their exact limits |
| `pages/LoginPage.test.tsx`, `RegisterPage.test.tsx` | Error messages, the button locking while waiting, server errors, the 2FA step |
| `api/client.test.ts` | Expired tokens are refreshed once, even when several requests fail together |
| `store/cartSlice.test.ts` | Cart rules: merging items, stock limits, totals |
| `lib/variants.test.ts` | Picking the right product when you switch colour or size |
| `lib/framing.test.ts` | Placing a photo: uncut products centred, cut edges on the tile edge, book covers whole |
