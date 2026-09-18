// Session lifecycle: restore on page load, start after any login flow, end on logout.
import * as authApi from '../api/auth';
import { refreshAccessToken, setAccessToken } from '../api/client';
import type { AppDispatch } from '../store';
import { signedIn, signedOut } from '../store/authSlice';

/** Page load: the token in memory is gone, so trade the httpOnly refresh cookie for a new one. */
export async function restoreSession(dispatch: AppDispatch) {
  try {
    await refreshAccessToken();
    dispatch(signedIn(await authApi.fetchMe()));
  } catch {
    setAccessToken(null);
    dispatch(signedOut());
  }
}

/** Every successful login path (password, 2FA, Google) ends here with an access token. */
export async function startSession(dispatch: AppDispatch, accessToken: string) {
  setAccessToken(accessToken);
  dispatch(signedIn(await authApi.fetchMe()));
}

/** Re-reads the profile after something on it changed, such as 2FA being switched on. */
export async function reloadUser(dispatch: AppDispatch) {
  dispatch(signedIn(await authApi.fetchMe()));
}

export async function endSession(dispatch: AppDispatch) {
  try {
    // Blocklists the access token and revokes the refresh cookie server-side.
    await authApi.logout();
  } finally {
    setAccessToken(null);
    dispatch(signedOut());
  }
}
