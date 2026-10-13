// The delivery options and free-shipping limit, shared by the cart, the checkout and the help pages.
import { useQuery } from '@tanstack/react-query';
import { fetchDeliveryOptions } from '../api/delivery';

/** Staff changes show up within five minutes; the delivery admin page refreshes this copy when it saves. */
export function useDelivery() {
  return useQuery({ queryKey: ['delivery-options'], queryFn: fetchDeliveryOptions, staleTime: 5 * 60_000 });
}
