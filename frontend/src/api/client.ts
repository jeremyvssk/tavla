// Axios instance: attaches the in-memory access token and refreshes it once when the server rejects it.
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

// The access token lives only in this module variable. Not localStorage or sessionStorage, where any
// injected script can read it and it outlives the tab, and not Redux, where devtools display it.
// A reload wipes it on purpose; restoreSession() gets a new one from the httpOnly refresh cookie.
let accessToken: string | null = null;
let onSessionExpired: () => void = () => {};

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

// Same origin as the API (nginx and the Vite proxy), so the refresh cookie is sent without CORS.
export const api = axios.create();

// Refresh goes through a separate instance so a failing refresh can't re-enter the 401 handler below.
export const refreshClient = axios.create();

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// One refresh at a time. The backend treats a refresh token presented twice as stolen and revokes
// every session for that user, so two requests failing together must share one refresh call.
// React StrictMode mounting the app twice in dev would otherwise log the user out on every reload.
let refreshing: Promise<string> | null = null;

export function refreshAccessToken(): Promise<string> {
  refreshing ??= refreshClient
    .post<{ accessToken: string }>('/auth/refresh')
    .then((res) => {
      setAccessToken(res.data.accessToken);
      return res.data.accessToken;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

type RetriableConfig = InternalAxiosRequestConfig & { retried?: boolean };

api.interceptors.response.use(undefined, async (error: AxiosError<{ error?: string }>) => {
  const config = error.config as RetriableConfig | undefined;
  // Only the security layer's "unauthorized" means the access token is missing or expired.
  // "invalid_credentials" is a wrong password on a form, and refreshing would not change that.
  const tokenRejected = error.response?.status === 401 && error.response.data?.error === 'unauthorized';
  if (!tokenRejected || !config || config.retried || accessToken === null) {
    throw error;
  }
  try {
    await refreshAccessToken();
  } catch {
    setAccessToken(null);
    onSessionExpired();
    throw error;
  }
  config.retried = true;
  return api(config);
});
