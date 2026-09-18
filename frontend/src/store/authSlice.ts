// Redux auth state: who is signed in. The access token itself is deliberately not stored here.
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { Me } from '../api/auth';

export interface AuthState {
  // "restoring" until the first refresh attempt on page load settles, so guarded pages don't
  // bounce a signed-in user to /login for the few milliseconds before the session is back.
  status: 'restoring' | 'authenticated' | 'anonymous';
  user: Me | null;
}

const initialState: AuthState = { status: 'restoring', user: null };

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    signedIn(state, action: PayloadAction<Me>) {
      state.status = 'authenticated';
      state.user = action.payload;
    },
    signedOut(state) {
      state.status = 'anonymous';
      state.user = null;
    },
  },
});

export const { signedIn, signedOut } = authSlice.actions;
export default authSlice.reducer;
