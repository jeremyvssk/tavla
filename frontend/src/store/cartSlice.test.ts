// Cart reducer rules: merging lines, the stock and per-line caps, removal, totals and the saved-cart guard.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import cart, { added, CartState, itemCount, LINE_LIMIT, loadCart, quantitySet, removed, subtotal } from './cartSlice';

const item = { productId: 'a', name: 'Walnut set', brandName: null, options: null, imageUrl: null, price: 19.99, maxQuantity: 3 };
const empty: CartState = { lines: [], open: false };

describe('cart', () => {
  // a fresh in-memory storage per test; Node's own experimental localStorage global shadows jsdom's here
  beforeEach(() => {
    const data = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => data.get(k) ?? null,
      setItem: (k: string, v: string) => void data.set(k, v),
      clear: () => data.clear(),
    });
  });

  it('adds a line, and adding the same product again raises its quantity', () => {
    let s = cart(empty, added(item));
    s = cart(s, added(item));
    expect(s.lines).toHaveLength(1);
    expect(s.lines[0].quantity).toBe(2);
  });

  it('never goes above the stock or the per-line limit, or below one', () => {
    let s = cart(empty, added({ ...item, quantity: 5 }));
    expect(s.lines[0].quantity).toBe(3);
    s = cart(cart(empty, added({ ...item, maxQuantity: 500 })), quantitySet({ productId: 'a', quantity: 99 }));
    expect(s.lines[0].quantity).toBe(LINE_LIMIT);
    s = cart(s, quantitySet({ productId: 'a', quantity: 0 }));
    expect(s.lines[0].quantity).toBe(1);
  });

  it('ignores a sold-out product', () => {
    expect(cart(empty, added({ ...item, maxQuantity: 0 })).lines).toHaveLength(0);
  });

  it('removes a line and totals the rest without float drift', () => {
    let s = cart(empty, added({ ...item, quantity: 3 }));
    s = cart(s, added({ ...item, productId: 'b', price: 0.1 }));
    expect(itemCount(s)).toBe(4);
    expect(subtotal(s)).toBe(60.07);
    s = cart(s, removed('a'));
    expect(s.lines.map((l) => l.productId)).toEqual(['b']);
  });

  it('restores a saved cart and drops anything malformed', () => {
    localStorage.setItem('tavla.cart', JSON.stringify([{ ...item, quantity: 2 }, { productId: 'x', quantity: 'lots' }, null]));
    expect(loadCart().lines).toEqual([{ ...item, quantity: 2 }]);
    localStorage.setItem('tavla.cart', '{not json');
    expect(loadCart().lines).toEqual([]);
  });
});
