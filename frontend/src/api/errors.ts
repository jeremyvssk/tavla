// Turns the backend's { error, fields } shape and transport failures into text a form can show.
import { isAxiosError } from 'axios';

export interface ApiError {
  code: string;
  message: string;
  fields: Record<string, string>;
}

const MESSAGES: Record<string, string> = {
  invalid_credentials: 'Email or password is incorrect.',
  email_already_exists: 'An account with this email already exists.',
  email_registered_with_password: 'This email already has a password account. Sign in with your password.',
  invalid_oauth_token: 'Google sign-in failed. Try again.',
  captcha_failed: 'The CAPTCHA check failed. Tick the box again.',
  invalid_2fa_code: 'That code is not valid.',
  invalid_reset_token: 'This reset link has expired or was already used. Request a new one.',
  two_factor_already_enabled: 'Two-factor authentication is already on.',
  review_already_exists: 'You have already reviewed this product.',
  product_not_found: 'This product does not exist or is no longer sold.',
  cart_item_not_found: 'That item is no longer in your cart.',
  order_not_found: "We can't find this order.",
  order_not_cancellable: "We started packing it a moment ago, so it can't be cancelled now.",
  cart_empty: 'Your cart is empty, or everything in it sold out.',
  payment_unavailable: "We couldn't reach the payment provider, so nothing was charged. Try again in a minute.",
  hold_expired: 'The time we held your items for ran out, so they went back on sale. Nothing was charged.',
  order_not_payable: 'This order is already paid or closed.',
  validation_failed: 'Check the highlighted fields.',
  unauthorized: 'Your session has ended. Sign in again.',
  forbidden: 'You are not allowed to do that.',
  // no such endpoint: the PLANNED calls answer this until the backend builds them
  not_found: "The server can't do this yet.",
};

export function toApiError(err: unknown): ApiError {
  if (!isAxiosError(err) || !err.response) {
    return { code: 'network_error', message: 'Could not reach the server. Check your connection.', fields: {} };
  }
  const { status, data, headers } = err.response;
  const code: string = data?.error ?? 'request_failed';
  const fields: Record<string, string> = data?.fields ?? {};
  if (status === 429) {
    return { code, message: tooManyRequests(Number(headers['retry-after'])), fields };
  }
  return { code, message: MESSAGES[code] ?? 'Something went wrong. Try again.', fields };
}

function tooManyRequests(retryAfterSeconds: number) {
  if (!Number.isFinite(retryAfterSeconds) || retryAfterSeconds <= 0) {
    return 'Too many attempts. Wait a moment and try again.';
  }
  if (retryAfterSeconds < 90) {
    return `Too many attempts. Try again in ${retryAfterSeconds} seconds.`;
  }
  return `Too many attempts. Try again in ${Math.ceil(retryAfterSeconds / 60)} minutes.`;
}
