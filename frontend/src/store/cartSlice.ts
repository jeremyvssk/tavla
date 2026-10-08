// Redux cart state: only whether the cart panel is open. The lines live on the server (hooks/useCart).
import { createSlice } from '@reduxjs/toolkit';

export interface CartState {
  open: boolean;
}

const initialState: CartState = { open: false };

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    opened(state) {
      state.open = true;
    },
    closed(state) {
      state.open = false;
    },
  },
});

export const { opened, closed } = cartSlice.actions;
export default cartSlice.reducer;
