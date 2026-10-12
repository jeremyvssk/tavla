// Typed calls to the /orders endpoints: place an order, read one, pay it again, the account's list, cancel,
// and the last delivery details that prefill the checkout. The server side is backend OrderController.
import type { ImageFraming } from '../lib/framing';
import type { ShippingMethod } from '../lib/shipping';
import type { BillingForm, CheckoutForm } from '../validation/checkoutRules';
import type { Page } from './catalog';
import { api } from './client';

/** Labels, tones and the tracker step for each live in lib/orderStatus.ts. */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAYMENT_SUCCESSFUL'
  | 'PAYMENT_FAILED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

/** POST /orders. The flat field names are the ones a validation_failed answer names in `fields`. */
export type PlaceOrderRequest = Omit<CheckoutForm, 'flat'> & {
  flat: string | null;
  shippingMethod: ShippingMethod;
  /**
   * PLANNED: left out when it is the delivery address. The server doesn't accept it yet, and it refuses
   * unknown fields (400 malformed_request), so a separate billing address fails until the backend adds it.
   */
  billingAddress?: BillingForm;
};

export interface OrderLine {
  productId: string;
  /** copied into the order when it was placed, so a later price or name change doesn't alter it */
  name: string;
  options: string | null;
  imageUrl: string | null;
  imageFraming: ImageFraming | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderSummary {
  id: string;
  /** what the shopper sees, e.g. TV-24817 */
  number: string;
  placedAt: string;
  status: OrderStatus;
  itemCount: number;
  total: number;
  /** the first few lines, for the names and photos in the list row */
  preview: Pick<OrderLine, 'name' | 'imageUrl' | 'imageFraming'>[];
}

export interface OrderDetail extends Omit<OrderSummary, 'preview'> {
  email: string;
  phone: string;
  shipTo: { firstName: string; lastName: string; street: string; flat: string | null; postcode: string; city: string; country: string };
  shippingMethod: ShippingMethod;
  lines: OrderLine[];
  subtotal: number;
  shippingCost: number;
  vat: number;
  /** the server's answer to "can this still be cancelled", so the page never decides that itself */
  cancellable: boolean;
  /** card brand and last 4 once paid; Stripe never gives the server more than that */
  payment: {
    brand: string;
    last4: string;
    paidAt: string;
    /** PLANNED: Stripe's PaymentIntent id (pi_…), the reference a bank asks for; not sent yet */
    reference?: string;
  } | null;
  /** PLANNED: the invoice address when it differs from shipTo; not sent yet */
  billTo?: { name: string; street: string; postcode: string; city: string; country: string } | null;
  /** why the last payment attempt failed, in words the shopper can act on */
  paymentFailure: string | null;
  /** until when unpaid items stay reserved */
  holdUntil: string | null;
  tracking: { carrier: string; number: string; url: string | null; lastUpdate: string | null } | null;
  /** newest first */
  history: { at: string; status: OrderStatus; note: string | null }[];
}

export type StatusGroup = 'all' | 'in_progress' | 'delivered' | 'cancelled' | 'failed';
export type OrderSort = 'newest' | 'oldest' | 'status' | 'total_desc';

export interface OrderListParams {
  status?: StatusGroup;
  /** ISO dates, inclusive; either may be missing */
  from?: string;
  to?: string;
  sort?: OrderSort;
  page?: number;
  size?: number;
}

/** A page of orders, and how many the current date range holds per status group, for the filter pills. */
export interface OrderListResponse extends Page<OrderSummary> {
  counts: Record<StatusGroup, number>;
}

/** 201 from POST /orders. The client secret confirms this order's Stripe payment and nothing else. */
export interface PlacedOrder {
  id: string;
  number: string;
  total: number;
  /** the items are reserved until then */
  holdUntil: string;
  clientSecret: string;
}

export async function placeOrder(request: PlaceOrderRequest): Promise<PlacedOrder> {
  return (await api.post('/orders', request)).data;
}

/** After a decline: puts the order back to Pending payment and returns the same payment's client secret. */
export async function startPayment(orderId: string): Promise<{ clientSecret: string }> {
  return (await api.post(`/orders/${encodeURIComponent(orderId)}/payment`)).data;
}

/** The contact and address of the account's latest order, under the checkout form's own field names. */
export type DeliveryDetails = Omit<CheckoutForm, 'flat' | 'country'> & { flat: string | null; country: string };

/** Signed-in only. Null before the account's first order (the server answers 204). */
export async function fetchLastDelivery(): Promise<DeliveryDetails | null> {
  const response = await api.get('/orders/last-delivery');
  return response.status === 204 ? null : response.data;
}

export async function fetchOrders(params: OrderListParams): Promise<OrderListResponse> {
  return (await api.get('/orders', { params })).data;
}

export async function fetchOrder(id: string): Promise<OrderDetail> {
  return (await api.get(`/orders/${encodeURIComponent(id)}`)).data;
}

export async function cancelOrder(id: string, reason: string | null): Promise<OrderDetail> {
  return (await api.post(`/orders/${encodeURIComponent(id)}/cancel`, { reason })).data;
}
