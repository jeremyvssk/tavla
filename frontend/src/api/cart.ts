// Typed calls to the /cart endpoints. Guests are known by an HttpOnly cookie the browser sends by itself.
import { isAxiosError } from 'axios';
import type { ImageFraming } from '../lib/framing';
import type { ProductSummary } from './catalog';
import { api } from './client';

// One line never goes above this, whatever the stock says (CartService.LINE_LIMIT).
export const LINE_LIMIT = 10;

export interface CartLine {
  productId: string;
  name: string;
  brandName: string | null;
  /** e.g. "Colour: Green", so two variants of one product can be told apart */
  options: string | null;
  imageUrl: string | null;
  imageFraming: ImageFraming | null;
  unitPrice: number;
  quantity: number;
  /** what the subtotal counts: all of it when ok, what is left when short, nothing when sold out */
  countedQuantity: number;
  lineTotal: number;
  /** where the stepper stops: the stock or the line limit */
  maxQuantity: number;
  state: 'ok' | 'short' | 'sold_out' | 'unavailable';
}

export interface Cart {
  lines: CartLine[];
  itemCount: number;
  subtotal: number;
}

/** The 409 body when a line would go past the stock or the line limit. */
export interface StockConflict {
  error: 'out_of_stock' | 'not_enough_stock' | 'line_limit';
  productId: string;
  maxQuantity: number;
  inCart: number;
}

export async function fetchCart(): Promise<Cart> {
  return (await api.get<Cart>('/cart')).data;
}

export async function addToCart(productId: string, quantity = 1): Promise<Cart> {
  return (await api.post<Cart>('/cart/items', { productId, quantity })).data;
}

export async function setCartQuantity(productId: string, quantity: number): Promise<Cart> {
  return (await api.put<Cart>(`/cart/items/${encodeURIComponent(productId)}`, { quantity })).data;
}

export async function removeFromCart(productId: string): Promise<Cart> {
  return (await api.delete<Cart>(`/cart/items/${encodeURIComponent(productId)}`)).data;
}

/** Right after sign-in: the browser's guest cart joins the account's, and the guest cookie is cleared. */
export async function mergeGuestCart(): Promise<Cart> {
  return (await api.post<Cart>('/cart/merge')).data;
}

export async function fetchRecommendations(): Promise<ProductSummary[]> {
  return (await api.get<ProductSummary[]>('/cart/recommendations')).data;
}

export function stockConflictOf(err: unknown): StockConflict | null {
  if (!isAxiosError(err) || err.response?.status !== 409) return null;
  const body = err.response.data as Partial<StockConflict> | undefined;
  return body && typeof body.maxQuantity === 'number' && typeof body.inCart === 'number' ? (body as StockConflict) : null;
}

/** What to tell the shopper when the server refused an add or a quantity change. */
export function stockMessage(c: StockConflict) {
  if (c.error === 'out_of_stock' || c.maxQuantity === 0) return 'Sold out. It sold out while you were looking.';
  if (c.error === 'line_limit') return `${LINE_LIMIT} is the most one order can take of this.`;
  if (c.inCart >= c.maxQuantity) return `Only ${c.maxQuantity} in stock, and all of them are in your cart.`;
  return `Only ${c.maxQuantity} in stock. You can add ${c.maxQuantity - c.inCart} more.`;
}
