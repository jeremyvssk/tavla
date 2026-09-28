// Redux cart state: the lines in the bag and whether the cart panel is open. No server side until checkout exists.
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface CartLine {
  productId: string;
  name: string;
  brandName: string | null;
  /** e.g. "Colour: Green", so two variants of one product can be told apart in the panel */
  options: string | null;
  imageUrl: string | null;
  /** the price when it was added; checkout will re-price against the server */
  price: number;
  quantity: number;
  /** stock when it was added; the stepper stops here */
  maxQuantity: number;
}

export interface CartState {
  lines: CartLine[];
  open: boolean;
}

// One line never goes above this, whatever the stock says: a shop, not a wholesaler.
export const LINE_LIMIT = 10;

const initialState: CartState = { lines: [], open: false };

const clamp = (n: number, max: number) => Math.max(1, Math.min(n, max, LINE_LIMIT));

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    /** Adds to an existing line for the same product instead of making a second one. */
    added(state, action: PayloadAction<Omit<CartLine, 'quantity'> & { quantity?: number }>) {
      const { quantity = 1, ...item } = action.payload;
      if (item.maxQuantity < 1) return;
      const line = state.lines.find((l) => l.productId === item.productId);
      if (line) {
        line.maxQuantity = item.maxQuantity;
        line.price = item.price;
        line.quantity = clamp(line.quantity + quantity, item.maxQuantity);
      } else {
        state.lines.push({ ...item, quantity: clamp(quantity, item.maxQuantity) });
      }
    },
    quantitySet(state, action: PayloadAction<{ productId: string; quantity: number }>) {
      const line = state.lines.find((l) => l.productId === action.payload.productId);
      if (line) line.quantity = clamp(action.payload.quantity, line.maxQuantity);
    },
    removed(state, action: PayloadAction<string>) {
      state.lines = state.lines.filter((l) => l.productId !== action.payload);
    },
    opened(state) {
      state.open = true;
    },
    closed(state) {
      state.open = false;
    },
  },
});

export const { added, quantitySet, removed, opened, closed } = cartSlice.actions;
export default cartSlice.reducer;

export const itemCount = (s: CartState) => s.lines.reduce((n, l) => n + l.quantity, 0);
// in cents, so 0.1 + 0.2 style float drift never shows up in a total
export const subtotal = (s: CartState) => s.lines.reduce((n, l) => n + Math.round(l.price * 100) * l.quantity, 0) / 100;

const KEY = 'tavla.cart';

/** The saved lines, or none. Storage can be missing, full or blocked, and a bad value is discarded. */
export function loadCart(): CartState {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    const lines = Array.isArray(raw) ? raw.filter((l): l is CartLine =>
      l && typeof l.productId === 'string' && typeof l.name === 'string' && typeof l.price === 'number'
      && Number.isInteger(l.quantity) && Number.isInteger(l.maxQuantity) && l.quantity >= 1) : [];
    return { lines: lines.map((l) => ({ ...l, quantity: clamp(l.quantity, l.maxQuantity) })), open: false };
  } catch {
    return initialState;
  }
}

export function saveCart(state: CartState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state.lines));
  } catch {
    // private mode or a full quota: the cart still works for this visit
  }
}
