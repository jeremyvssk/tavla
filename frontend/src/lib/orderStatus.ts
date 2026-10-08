// How each order status reads on the page: its words, its colour, and where it sits on the tracker.
import type { OrderStatus, StatusGroup } from '../api/orders';

/** The pill's class in index.css (.st.pend, .st.paid...). Words always go with the colour. */
type Tone = 'pend' | 'paid' | 'proc' | 'ship' | 'done' | 'fail' | 'cxl';

const STATUS: Record<OrderStatus, { label: string; tone: Tone }> = {
  PENDING_PAYMENT: { label: 'Pending payment', tone: 'pend' },
  PAYMENT_SUCCESSFUL: { label: 'Payment successful', tone: 'paid' },
  PAYMENT_FAILED: { label: 'Payment failed', tone: 'fail' },
  PROCESSING: { label: 'Processing', tone: 'proc' },
  SHIPPED: { label: 'Shipped', tone: 'ship' },
  DELIVERED: { label: 'Delivered', tone: 'done' },
  CANCELLED: { label: 'Cancelled', tone: 'cxl' },
  REFUNDED: { label: 'Cancelled · refunded', tone: 'cxl' },
};

/** A status the server sends that this file doesn't know yet still shows, in neutral, by its own name. */
export function statusLook(status: string) {
  return STATUS[status as OrderStatus] ?? { label: status.toLowerCase().replace(/_/g, ' '), tone: 'cxl' as Tone };
}

export const STATUS_GROUPS: { value: StatusGroup; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'in_progress', label: 'In progress' },
  { value: 'delivered', label: 'Delivered' },
  { value: 'cancelled', label: 'Cancelled' },
  { value: 'failed', label: 'Payment failed' },
];

export const TRACKER_STEPS = ['Ordered', 'Paid', 'Packing', 'Shipped', 'Delivered'] as const;

/**
 * How far along the tracker an order is: the index of the step happening now, or -1 when the order
 * left the happy path (failed, cancelled), where the page shows the history instead of a tracker.
 */
export function trackerStep(status: OrderStatus) {
  switch (status) {
    case 'PENDING_PAYMENT': return 1;
    case 'PAYMENT_SUCCESSFUL': return 2;
    case 'PROCESSING': return 2;
    case 'SHIPPED': return 4;
    case 'DELIVERED': return 5;
    default: return -1;
  }
}
