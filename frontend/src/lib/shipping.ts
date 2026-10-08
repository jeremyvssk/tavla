// Shipping options and the summary arithmetic the checkout shows, plus delivery-date windows.
//
// A display copy. The server prices the order when it is placed (step 3) and that is the total
// charged; if the two ever disagree, the server wins and the page shows its numbers.

export const FREE_SHIPPING_FROM = 100;
export const VAT_RATE = 0.24;

export type ShippingMethod = 'standard' | 'express' | 'locker';

export interface ShippingOption {
  method: ShippingMethod;
  name: string;
  price: number;
  /** working days from dispatch: [earliest, latest] */
  days: [number, number];
  blurb: string;
}

export const SHIPPING: ShippingOption[] = [
  { method: 'standard', name: 'Standard courier', price: 4.9, days: [2, 4], blurb: '2–4 working days' },
  { method: 'express', name: 'Express courier', price: 12.9, days: [1, 1], blurb: 'Next working day' },
  { method: 'locker', name: 'Parcel locker', price: 3.9, days: [1, 3], blurb: '1–3 working days' },
];

/** Standard is free from €100; the faster and the locker options always cost what they say. */
export function shippingCost(method: ShippingMethod, subtotal: number) {
  const option = SHIPPING.find((o) => o.method === method) ?? SHIPPING[0];
  return method === 'standard' && subtotal >= FREE_SHIPPING_FROM ? 0 : option.price;
}

/** In cents, so 0.1 + 0.2 style drift never shows. EU prices include VAT: the VAT is a part of the total, not added. */
export function summary(subtotal: number, method: ShippingMethod) {
  const shipping = shippingCost(method, subtotal);
  const totalCents = Math.round(subtotal * 100) + Math.round(shipping * 100);
  const vatCents = Math.round((totalCents * VAT_RATE) / (1 + VAT_RATE));
  return { shipping, total: totalCents / 100, vat: vatCents / 100 };
}

// Parcels leave the warehouse the same working day when ordered by 15:00.
const CUTOFF_HOUR = 15;

function addWorkingDays(from: Date, n: number) {
  const d = new Date(from);
  let left = n;
  while (left > 0) {
    d.setDate(d.getDate() + 1);
    if (d.getDay() !== 0 && d.getDay() !== 6) left--;
  }
  return d;
}

/** The day the parcel leaves: today before the cut-off on a weekday, else the next working day. */
export function dispatchDay(now = new Date()) {
  const weekday = now.getDay() !== 0 && now.getDay() !== 6;
  return weekday && now.getHours() < CUTOFF_HOUR ? new Date(now) : addWorkingDays(now, 1);
}

const day = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
const dayNoMonth = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric' });

/** "Fri 9 – Tue 13 Oct", or "Thu 8 Oct" when the window is one day. */
export function arrivalWindow(option: ShippingOption, now = new Date()) {
  const leaves = dispatchDay(now);
  const first = addWorkingDays(leaves, option.days[0]);
  const last = addWorkingDays(leaves, option.days[1]);
  if (option.days[0] === option.days[1]) return day.format(first);
  const sameMonth = first.getMonth() === last.getMonth();
  return `${(sameMonth ? dayNoMonth : day).format(first)} – ${day.format(last)}`;
}
