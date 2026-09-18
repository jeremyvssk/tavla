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
  validation_failed: 'Check the highlighted fields.',
  unauthorized: 'Your session has ended. Sign in again.',
  forbidden: 'You are not allowed to do that.',
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
