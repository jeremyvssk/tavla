/// <reference types="vite/client" />

// Build-time settings. Both are optional: unset hides Google sign-in and the CAPTCHA widget.
interface ImportMetaEnv {
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  readonly VITE_RECAPTCHA_SITE_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
