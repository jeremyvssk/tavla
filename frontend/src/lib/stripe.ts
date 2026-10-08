// Stripe.js, loaded once and only when a payment form is on screen, with the public test key from the build.
import { loadStripe, type Stripe } from '@stripe/stripe-js';

// pk_test_...: public by design. The secret key and the webhook secret never reach the browser.
const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY;

/** False on a copy of the shop built without Stripe keys: the card form explains instead of loading. */
export const stripeConfigured = Boolean(publishableKey);

let stripe: Promise<Stripe | null> | null = null;

/** loadStripe fetches Stripe.js from js.stripe.com itself: card fields must come from Stripe, never from our bundle. */
export function getStripe(): Promise<Stripe | null> {
  if (!publishableKey) return Promise.resolve(null);
  stripe ??= loadStripe(publishableKey);
  return stripe;
}
