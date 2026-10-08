/// <reference types="vite/client" />

// Build-time settings, all optional: unset hides Google sign-in, the CAPTCHA widget, and Stripe's card form.
interface ImportMetaEnv {
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  readonly VITE_RECAPTCHA_SITE_KEY?: string;
  readonly VITE_STRIPE_PUBLISHABLE_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
