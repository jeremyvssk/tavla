// Pieces the order pages share: the status pill, the progress tracker, read-only lines, totals, the address.
import { NavLink } from 'react-router-dom';
import type { OrderDetail, OrderLine, OrderStatus } from '../api/orders';
import { formatPrice } from '../lib/format';
import { statusLook, TRACKER_STEPS, trackerStep } from '../lib/orderStatus';
import { arrivalWindow, SHIPPING, ShippingMethod } from '../lib/shipping';
import { COUNTRIES } from '../validation/checkoutRules';
import Icon from './Icon';
import Photo from './Photo';

const when = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const placed = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const dayOnly = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

/** "7 Oct, 14:31" */
export const formatWhen = (iso: string) => when.format(new Date(iso));
/** "7 Oct 2026, 14:31" */
export const formatPlaced = (iso: string) => placed.format(new Date(iso));
/** "7 Oct 2026" */
export const formatDay = (iso: string) => dayOnly.format(new Date(iso));

export function StatusPill({ status }: { status: OrderStatus }) {
  const look = statusLook(status);
  return <span className={`st ${look.tone}`}>{look.label}</span>;
}

/** Ordered, Paid, Packing, Shipped, Delivered: done steps carry their time, the next ones what to expect (or the `notes` given). */
export function Tracker({ order, notes: given }: { order: OrderDetail; notes?: string[] }) {
  const now = trackerStep(order.status);
  if (now < 0) return null;
  const at = (s: OrderStatus) => order.history.find((h) => h.status === s)?.at;
  const option = SHIPPING.find((o) => o.method === order.shippingMethod) ?? SHIPPING[0];
  const notes = given ?? [
    formatWhen(order.placedAt),
    at('PAYMENT_SUCCESSFUL') ? formatWhen(at('PAYMENT_SUCCESSFUL')!) : 'Confirming',
    at('PROCESSING') ? formatWhen(at('PROCESSING')!) : 'Usually the same day',
    at('SHIPPED') ? formatWhen(at('SHIPPED')!) : 'Tracking link by email',
    at('DELIVERED') ? formatWhen(at('DELIVERED')!) : arrivalWindow(option, new Date(order.placedAt)),
  ];
  return (
    <ol className="trk" aria-label="Order progress">
      {TRACKER_STEPS.map((step, i) => {
        const state = i < now ? 'done' : i === now ? 'now' : 'todo';
        return (
          <li key={step} className={state} aria-current={state === 'now' ? 'step' : undefined}>
            <span className="dot">{state === 'done' && <Icon name="check" width={2.4} />}</span>
            <b>{step}</b>
            <small>{notes[i]}</small>
          </li>
        );
      })}
    </ol>
  );
}

export function ReadOnlyLines({ lines }: { lines: OrderLine[] }) {
  return (
    <ul className="lines">
      {lines.map((l) => (
        <li key={l.productId} className="ln">
          <span className="th">{l.imageUrl && <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.08} loading="lazy" />}</span>
          <div>
            <p className="nm">{l.name}</p>
            <p className="meta">{[l.options, `Qty ${l.quantity}`, l.quantity > 1 && `${formatPrice(l.unitPrice)} each`].filter(Boolean).join(' · ')}</p>
          </div>
          <b className="pz">{formatPrice(l.lineTotal)}</b>
        </li>
      ))}
    </ul>
  );
}

export function shippingName(method: ShippingMethod) {
  return (SHIPPING.find((o) => o.method === method) ?? SHIPPING[0]).name;
}

export function Totals({ subtotal, shipping, method, vat, total, grandLabel }: {
  subtotal: number; shipping: number; method: ShippingMethod; vat: number; total: number; grandLabel: string;
}) {
  return (
    <dl className="tot">
      <div><dt>Subtotal</dt><dd>{formatPrice(subtotal)}</dd></div>
      <div><dt>Shipping · {shippingName(method)}</dt>{shipping === 0 ? <dd className="free">Free</dd> : <dd>{formatPrice(shipping)}</dd>}</div>
      <div className="vat"><dt>Includes VAT 24%</dt><dd>{formatPrice(vat)}</dd></div>
      <div className="grand"><dt>{grandLabel}</dt><dd>{formatPrice(total)}</dd></div>
    </dl>
  );
}

export function ShipTo({ order }: { order: OrderDetail }) {
  const a = order.shipTo;
  const country = COUNTRIES.find((c) => c.code === a.country)?.name ?? a.country;
  return (
    <>
      {a.firstName} {a.lastName}<br />
      {a.street}{a.flat ? `-${a.flat}` : ''}, {a.postcode} {a.city}, {country}<br />
      {order.phone}
    </>
  );
}

/** Profile and Orders, the account's two pages. */
export function AccountTabs() {
  return (
    <nav className="acct-tabs" aria-label="Account">
      <NavLink end to="/account">Profile</NavLink>
      <NavLink to="/account/orders">Orders</NavLink>
      <NavLink to="/account/addresses">Addresses</NavLink>
      <NavLink to="/account/security">Security</NavLink>
    </nav>
  );
}
