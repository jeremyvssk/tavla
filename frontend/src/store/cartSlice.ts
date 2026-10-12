// Redux cart state: whether the cart is open, and which product was just added. The lines live on the server (hooks/useCart).
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface CartState {
  open: boolean;
  /** the product an Add to cart just put in, which the quick cart marks "Just added" */
  added: string | null;
}

const initialState: CartState = { open: false, added: null };

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    opened(state, action: PayloadAction<string | undefined>) {
      state.open = true;
      state.added = action.payload ?? null;
    },
    closed(state) {
      state.open = false;
      state.added = null;
    },
  },
});

export const { opened, closed } = cartSlice.actions;
export default cartSlice.reducer;
