// The checkout's summary arithmetic and delivery-date windows, from the delivery options the server sends.
//
// A display copy. The server prices the order when it is placed, with the same rule (delivery/DeliveryOption.costFor),
// and that is the total charged; the checkout sends the total it showed, and a difference is refused (price_changed).
import type { DeliveryOption, DeliverySettings } from '../api/delivery';

export const VAT_RATE = 0.24;

/** An option's id; the server's list decides which exist. */
export type ShippingMethod = string;

/** What an option costs on this subtotal: free past the limit when it takes part in it. */
export function shippingCost(option: Pick<DeliveryOption, 'price' | 'freeOverLimit'>, subtotal: number, free: DeliverySettings['freeShipping']) {
  return option.freeOverLimit && free.on && subtotal >= free.from ? 0 : option.price;
}

/** In cents, so 0.1 + 0.2 style drift never shows. EU prices include VAT: the VAT is a part of the total, not added. */
export function summary(subtotal: number, shipping: number) {
  const totalCents = Math.round(subtotal * 100) + Math.round(shipping * 100);
  const vatCents = Math.round((totalCents * VAT_RATE) / (1 + VAT_RATE));
  return { shipping, total: totalCents / 100, vat: vatCents / 100 };
}

/** How far the order is from free delivery; null when no option is ever free. */
export function toFreeShipping(settings: DeliverySettings | undefined, subtotal: number) {
  if (!settings?.freeShipping.on || !settings.options.some((o) => o.freeOverLimit)) return null;
  return { left: Math.max(0, settings.freeShipping.from - subtotal), from: settings.freeShipping.from };
}

function addWorkingDays(from: Date, n: number) {
  const d = new Date(from);
  let left = n;
  while (left > 0) {
    d.setDate(d.getDate() + 1);
    if (d.getDay() !== 0 && d.getDay() !== 6) left--;
  }
  return d;
}

/** The day the parcel leaves: today before the cut-off ("15:00") on a weekday, else the next working day. */
export function dispatchDay(now = new Date(), orderBy = '15:00') {
  const [h, m] = orderBy.split(':').map(Number);
  const weekday = now.getDay() !== 0 && now.getDay() !== 6;
  const before = now.getHours() < h || (now.getHours() === h && now.getMinutes() < m);
  return weekday && before ? new Date(now) : addWorkingDays(now, 1);
}

const day = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
const dayNoMonth = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric' });

/** "Fri 9 – Tue 13 Oct", or "Thu 8 Oct" when the window is one day. An order's own days need no cut-off: it is placed. */
export function arrivalWindow(option: { daysMin: number; daysMax: number; orderBy?: string }, now = new Date()) {
  const leaves = dispatchDay(now, option.orderBy);
  const first = addWorkingDays(leaves, option.daysMin);
  const last = addWorkingDays(leaves, option.daysMax);
  if (option.daysMin === option.daysMax) return day.format(first);
  const sameMonth = first.getMonth() === last.getMonth();
  return `${(sameMonth ? dayNoMonth : day).format(first)} – ${day.format(last)}`;
}

/** "2–4 working days", "Next working day". */
export function daysText(option: { daysMin: number; daysMax: number }) {
  if (option.daysMax === 0) return 'Same working day';
  if (option.daysMin === option.daysMax) return option.daysMax === 1 ? 'Next working day' : `${option.daysMax} working days`;
  return `${option.daysMin}–${option.daysMax} working days`;
}
