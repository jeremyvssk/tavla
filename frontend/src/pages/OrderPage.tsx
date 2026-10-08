// One order: progress, items and totals, delivery and payment, the status history, and cancelling.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { cancelOrder, fetchOrder, OrderDetail } from '../api/orders';
import { toApiError } from '../api/errors';
import Icon from '../components/Icon';
import { formatPlaced, formatWhen, ReadOnlyLines, shippingName, ShipTo, StatusPill, Totals, Tracker } from '../components/OrderParts';
import { formatPrice } from '../lib/format';

const REASONS = ['Changed my mind', 'Ordered by mistake', 'Found it cheaper', 'Delivery takes too long', 'Something else'];

export default function OrderPage() {
  const { orderId = '' } = useParams();
  const [cancelling, setCancelling] = useState(false);
  const { data: order, isPending, error } = useQuery({ queryKey: ['order', orderId], queryFn: () => fetchOrder(orderId) });

  if (isPending) return <main className="acct"><p className="status-line">Loading the order…</p></main>;
  if (error || !order) {
    return (
      <main className="acct">
        <Link className="backl" to="/account/orders"><Icon name="left" width={2} />All orders</Link>
        {/* someone else's order answers 404 too, so this page can't be used to find out whether it exists */}
        <div className="empty od-missing"><h1>We can't find this order</h1><p>{toApiError(error).code === 'network_error' ? toApiError(error).message : 'It may belong to another account.'}</p></div>
      </main>
    );
  }

  const offTrack = order.status === 'PAYMENT_FAILED' || order.status === 'CANCELLED' || order.status === 'REFUNDED';
  const onItsWay = order.status === 'SHIPPED' || order.status === 'DELIVERED';

  return (
    <main className="acct">
      <Link className="backl" to="/account/orders"><Icon name="left" width={2} />All orders</Link>
      <div className="od-top">
        <div><p className="t-eyebrow">Placed {formatPlaced(order.placedAt)}</p><h1 className="co-h1">Order {order.number}</h1></div>
        <StatusPill status={order.status} />
      </div>

      {!offTrack && <div className="card"><Tracker order={order} /></div>}

      <div className="od-grid">
        <div className="stack">
          {order.tracking && (
            <div className="card">
              <div className="card-h">
                <h2>Tracking</h2>
                {order.tracking.url && <a className="link" href={order.tracking.url} target="_blank" rel="noopener noreferrer">Track with {order.tracking.carrier}</a>}
              </div>
              <dl className="kv">
                <div><dt>Courier</dt><dd>{order.tracking.carrier} · {shippingName(order.shippingMethod)}</dd></div>
                <div><dt>Parcel number</dt><dd className="code">{order.tracking.number}</dd></div>
                {order.tracking.lastUpdate && <div><dt>Last update</dt><dd>{order.tracking.lastUpdate}</dd></div>}
              </dl>
            </div>
          )}
          <div className="card">
            <div className="card-h"><h2>Items</h2><span className="mute">{order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}</span></div>
            <ReadOnlyLines lines={order.lines} />
            <Totals subtotal={order.subtotal} shipping={order.shippingCost} method={order.shippingMethod} vat={order.vat} total={order.total}
              grandLabel={order.payment ? 'Paid' : 'Total'} />
          </div>
          <div className="card">
            <h2>Status history</h2>
            <ol className="hist">
              {order.history.map((h, i) => (
                <li key={i}>
                  <time dateTime={h.at}>{formatWhen(h.at)}</time>
                  <div><p><StatusPill status={h.status} /></p>{h.note && <p>{h.note}</p>}</div>
                </li>
              ))}
            </ol>
          </div>
        </div>

        <div className="stack">
          {order.cancellable ? (
            <div className="card cancel">
              <h2>Cancel this order</h2>
              <p>
                We haven't started packing.
                {order.payment ? ` Cancel now and ${formatPrice(order.total)} goes back to your ${order.payment.brand} ending ${order.payment.last4} in 5–10 working days.` : ' Cancel now and nothing is charged.'}
              </p>
              <button type="button" className="btn btn--line-danger btn--wide" onClick={() => setCancelling(true)}>Cancel order</button>
            </div>
          ) : onItsWay ? (
            <p className="msg msg--block"><Icon name="back" /><span><b>It's on its way, so it can't be cancelled.</b> You can return it within 30 days of delivery for a full refund.</span></p>
          ) : null}
          <div className="card">
            <h2>Delivery and payment</h2>
            <dl className="kv">
              <div><dt>Ship to</dt><dd><ShipTo order={order} /></dd></div>
              <div><dt>Method</dt><dd>{shippingName(order.shippingMethod)}</dd></div>
              {!order.tracking && !offTrack && <div><dt>Tracking</dt><dd className="mute">A tracking link appears here, and in your email, when it ships.</dd></div>}
              <div><dt>Paid with</dt><dd>{order.payment
                ? `${order.payment.brand} ending ${order.payment.last4} · ${formatPrice(order.total)} on ${formatWhen(order.payment.paidAt)}`
                : order.status === 'PAYMENT_FAILED' ? 'Not charged: the payment failed' : 'Not paid yet'}</dd></div>
              {order.payment && (order.status === 'CANCELLED' || order.status === 'REFUNDED') && (
                <div><dt>Refund</dt><dd>{order.status === 'REFUNDED'
                  ? `${formatPrice(order.total)} sent back to ${order.payment.brand} ending ${order.payment.last4}. Banks take 5–10 working days to show it.`
                  : `${formatPrice(order.total)} on its way back to ${order.payment.brand} ending ${order.payment.last4}. We email you when it's sent.`}</dd></div>
              )}
            </dl>
          </div>
        </div>
      </div>

      {cancelling && <CancelSheet order={order} onClose={() => setCancelling(false)} />}
    </main>
  );
}

/**
 * Asks once more before cancelling, with what happens to the money and the items. The server decides
 * whether the order can still be cancelled; a refusal (packing started meanwhile) is shown here.
 * The guest's order page (OrderStatusPage) uses it too.
 */
export function CancelSheet({ order, onClose }: { order: OrderDetail; onClose: () => void }) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const keep = useRef<HTMLButtonElement>(null);
  const cancel = useMutation({
    mutationFn: () => cancelOrder(order.id, reason || null),
    onSuccess: (updated) => {
      client.setQueryData(['order', order.id], updated);
      client.invalidateQueries({ queryKey: ['orders'] });
      onClose();
    },
    // refused because packing started meanwhile: the page behind the sheet catches up with the new status
    onError: () => client.invalidateQueries({ queryKey: ['order', order.id] }),
  });

  // focus starts on the safe choice; Escape and the backdrop keep the order
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    keep.current?.focus();
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    addEventListener('keydown', onKey);
    return () => {
      removeEventListener('keydown', onKey);
      document.documentElement.style.overflow = '';
      before?.focus?.();
    };
  }, [onClose]);

  const refused = cancel.error ? toApiError(cancel.error) : null;
  return (
    <>
      <div className="t-scrim" data-on="" onClick={onClose} />
      <div className="sheet" role="dialog" aria-modal="true" aria-labelledby="cx-h">
        <span className="grab" aria-hidden="true" />
        <div>
          <h2 id="cx-h">Cancel order {order.number}?</h2>
          <p className="mute">We haven't started packing, so you still can.</p>
        </div>
        <dl className="kv">
          <div><dt>Refund</dt><dd>{order.payment ? <><b className="num">{formatPrice(order.total)}</b> back to {order.payment.brand} ending {order.payment.last4}</> : 'Nothing was charged'}</dd></div>
          {order.payment && <div><dt>When</dt><dd>5–10 working days, depending on your bank</dd></div>}
          <div><dt>Your items</dt><dd>Go back on sale straight away</dd></div>
        </dl>
        <div className="field">
          <label className="field__label" htmlFor="cx-why">Why are you cancelling? <small>(optional)</small></label>
          <div className="sel-wrap">
            <select id="cx-why" className="field__input" value={reason} onChange={(e) => setReason(e.target.value)}>
              <option value="">Choose a reason</option>
              {REASONS.map((r) => <option key={r}>{r}</option>)}
            </select>
            <Icon name="down" width={2} />
          </div>
        </div>
        {refused && <p className="msg msg--bad" role="alert"><Icon name="alert" />{refused.message}</p>}
        <div className="btns">
          <button type="button" className="btn btn--danger" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            {cancel.isPending ? 'Cancelling…' : order.payment ? 'Yes, cancel and refund' : 'Yes, cancel'}
          </button>
          <button type="button" className="btn btn--ghost" ref={keep} onClick={onClose}>Keep my order</button>
        </div>
      </div>
    </>
  );
}
