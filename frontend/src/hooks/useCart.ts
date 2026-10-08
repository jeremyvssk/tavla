// The server cart through React Query: one cached copy per shopper, and the writes that replace it.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as cartApi from '../api/cart';
import type { Cart } from '../api/cart';
import { useAppSelector } from '../store';

const EMPTY: Cart = { lines: [], itemCount: 0, subtotal: 0 };

/** Signing in or out switches carts, so the cache key names whose cart it is. */
function useCartKey() {
  const { status, user } = useAppSelector((s) => s.auth);
  return { key: ['cart', user?.id ?? 'guest'] as const, ready: status !== 'restoring' };
}

export function useCart() {
  const { key, ready } = useCartKey();
  // wait for the session restore, or the first read would fetch the guest cart and then refetch
  const query = useQuery({ queryKey: key, queryFn: cartApi.fetchCart, enabled: ready, staleTime: 10_000 });
  return { ...query, cart: query.data ?? EMPTY };
}

export function useRecommendations(enabled: boolean) {
  const { key, ready } = useCartKey();
  return useQuery({
    queryKey: [...key, 'recommendations'],
    queryFn: cartApi.fetchRecommendations,
    enabled: ready && enabled,
    staleTime: 60_000,
  });
}

/**
 * Every write answers with the whole cart, which replaces the cached one. The writes share a
 * scope, so React Query runs them one after another: two quick taps on + can't land out of order
 * and leave the older cart on screen.
 */
export function useCartActions() {
  const client = useQueryClient();
  const { key } = useCartKey();
  const options = {
    scope: { id: 'cart' },
    onSuccess: (cart: Cart) => {
      client.setQueryData(key, cart);
      client.invalidateQueries({ queryKey: [...key, 'recommendations'] });
    },
  };
  const add = useMutation({ ...options, mutationFn: (v: { productId: string; quantity?: number }) => cartApi.addToCart(v.productId, v.quantity) });
  const setQuantity = useMutation({ ...options, mutationFn: (v: { productId: string; quantity: number }) => cartApi.setCartQuantity(v.productId, v.quantity) });
  const remove = useMutation({ ...options, mutationFn: (productId: string) => cartApi.removeFromCart(productId) });
  return { add, setQuantity, remove };
}
