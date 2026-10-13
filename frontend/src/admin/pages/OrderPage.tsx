// Admin order: move it along (pack, ship with tracking, deliver), its items and history, who and where, and cancel or refund.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { addOrderNote, AdminOrderDetail, fetchAdminOrder, moveOrder, refundOrder } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { formatPlaced, formatWhen, orderDays, ReadOnlyLines, StatusPill, Totals, Tracker } from '../../components/OrderParts';
import { formatPrice } from '../../lib/format';
import { arrivalWindow } from '../../lib/shipping';
import { COUNTRIES } from '../../validation/checkoutRules';
import { AdminHead, LoadState, useConfirm } from '../parts';

const COURIERS = ['Omniva', 'DPD'];
const CANCEL_REASONS = ['Customer asked by phone or email', "We can't fulfil it", 'Looks like fraud', 'Something else'];
const REFUND_REASONS = ['Returned by the customer', 'Arrived damaged', 'Missing from the parcel', 'Something else'];

export default function OrderPage() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const q = useQuery({ queryKey: ['staff', 'order', id], queryFn: () => fetchAdminOrder(id), retry: false });
  // every action answers with the order as it is now
  const saved = (o: AdminOrderDetail) => {
    client.setQueryData(['staff', 'order', id], o);
    client.invalidateQueries({ queryKey: ['staff', 'orders'] });
    client.invalidateQueries({ queryKey: ['staff', 'overview'] });
  };
  const o = q.data;

  return (
    <main className="adm-body">
      <Link className="backl" to="/admin/orders"><Icon name="left" width={2} />Orders</Link>
      {!o ? (
        <>
          <AdminHead title="Order" />
          <LoadState query={q} what="the order" />
        </>
      ) : (
        <>
          <AdminHead
            title={o.number}
            sub={`Placed ${formatPlaced(o.placedAt)} · ${o.guest ? 'guest checkout' : 'customer account'} · ${o.itemCount} ${o.itemCount === 1 ? 'item' : 'items'}`}
            pill={<StatusPill status={o.status} />}
          >
            <Link className="btn btn--ghost" to={`/admin/orders/slips?ids=${o.id}`}>Print packing slip</Link>
          </AdminHead>
          <div className="adm-2col">
            <div className="stack">
              <MoveAlong order={o} onSaved={saved} />
              <section className="apanel" aria-labelledby="it-h">
                <div className="panel-h"><h2 id="it-h">Items</h2><span className="mute">Prices as paid</span></div>
                <div className="panel-b">
                  <ReadOnlyLines lines={o.lines} />
                  <Totals subtotal={o.subtotal} shipping={o.shippingCost} methodName={o.shippingName} vat={o.vat} total={o.total}
                    grandLabel={o.payment ? 'Paid' : 'Total'} />
                </div>
              </section>
              <History order={o} onSaved={saved} />
            </div>
            <div className="stack">
              <section className="apanel" aria-labelledby="cu-h">
                <div className="panel-h"><h2 id="cu-h">Customer</h2><span className="role">{o.guest ? 'Guest' : 'Account'}</span></div>
                <div className="panel-b">
                  <dl className="kv">
                    <div><dt>Name</dt><dd>{o.customerName}</dd></div>
                    <div><dt>Email</dt><dd>{o.email}</dd></div>
                    <div><dt>Phone</dt><dd>{o.phone}</dd></div>
                  </dl>
                </div>
              </section>
              <section className="apanel" aria-labelledby="de-h">
                <div className="panel-h"><h2 id="de-h">Delivery</h2></div>
                <div className="panel-b">
                  <dl className="kv">
                    <div>
                      <dt>Ship to</dt>
                      <dd>{o.shipTo.firstName} {o.shipTo.lastName}<br />{o.shipTo.street}{o.shipTo.flat ? `-${o.shipTo.flat}` : ''}, {o.shipTo.postcode} {o.shipTo.city}, {country(o.shipTo.country)}</dd>
                    </div>
                    <div><dt>Method</dt><dd>{deliveryLine(o)}</dd></div>
                    {o.tracking && <div><dt>Tracking</dt><dd>{o.tracking.carrier} · <span className="mono">{o.tracking.number}</span></dd></div>}
                    {o.billTo && (
                      <div>
                        <dt>Bill to</dt>
                        <dd>{o.billTo.name}<br />{o.billTo.street}, {o.billTo.postcode} {o.billTo.city}, {country(o.billTo.country)}</dd>
                      </div>
                    )}
                  </dl>
                </div>
              </section>
              <Payment order={o} />
              {o.next.includes('CANCELLED')
                ? <CancelPanel order={o} onSaved={saved} />
                : o.payment && o.refunded < o.total && ['PROCESSING', 'SHIPPED', 'DELIVERED'].includes(o.status) && <RefundPanel order={o} onSaved={saved} />}
            </div>
          </div>
        </>
      )}
    </main>
  );
}

const country = (code: string) => COUNTRIES.find((c) => c.code === code)?.name ?? code;

function deliveryLine(o: AdminOrderDetail) {
  const price = o.shippingCost === 0 ? 'free' : formatPrice(o.shippingCost);
  const due = o.status === 'DELIVERED' || !o.payment ? null : `due ${arrivalWindow(orderDays(o), new Date(o.placedAt))}`;
  return [o.shippingName, price, due].filter(Boolean).join(' · ');
}

/** The tracker with staff notes, and the next move the state machine allows. */
function MoveAlong({ order: o, onSaved }: { order: AdminOrderDetail; onSaved: (o: AdminOrderDetail) => void }) {
  const [courier, setCourier] = useState(COURIERS[0]);
  const [tracking, setTracking] = useState('');
  const [missing, setMissing] = useState(false);
  const move = useMutation({
    mutationFn: (to: string) => moveOrder(o.id, to, to === 'SHIPPED' ? { carrier: courier, trackingNumber: tracking.trim() } : {}),
    onSuccess: onSaved,
  });
  const at = (s: string) => o.timeline.find((t) => t.to === s)?.at;
  const notes = [
    formatWhen(o.placedAt),
    at('PAYMENT_SUCCESSFUL') ? formatWhen(at('PAYMENT_SUCCESSFUL')!) : 'Waiting for Stripe',
    at('PROCESSING') ? formatWhen(at('PROCESSING')!) : o.next.includes('PROCESSING') ? 'Next' : '',
    at('SHIPPED') ? formatWhen(at('SHIPPED')!) : 'Needs tracking',
    at('DELIVERED') ? formatWhen(at('DELIVERED')!) : `Due ${arrivalWindow(orderDays(o), new Date(o.placedAt))}`,
  ];
  const canShip = o.next.includes('SHIPPED');
  const ship = (e: FormEvent) => {
    e.preventDefault();
    if (!tracking.trim()) { setMissing(true); return; }
    move.mutate('SHIPPED');
  };
  const steps = o.next.filter((n) => n !== 'CANCELLED');

  return (
    <section className="apanel noprint" aria-labelledby="mv-h">
      <div className="panel-h"><div><h2 id="mv-h">Move it along</h2><p>{steps.length ? 'Shipping and delivery each email the customer.' : 'Nothing left to do by hand.'}</p></div></div>
      <div className="panel-b mvb">
        <Tracker order={o} notes={notes} />
        {o.next.includes('PROCESSING') && (
          <div className="msg msg--warn">
            <Icon name="alert" />
            <p><b>Once packing starts, the customer can no longer cancel.</b> Until then they can, from their order page. You can still cancel it here until it ships.</p>
          </div>
        )}
        {move.error && <p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(move.error).message}</p>}
        {(o.next.includes('PROCESSING') || o.next.includes('DELIVERED')) && (
          <div className="row">
            {o.next.includes('PROCESSING') && <button className="btn" type="button" disabled={move.isPending} onClick={() => move.mutate('PROCESSING')}>Start packing</button>}
            {o.next.includes('DELIVERED') && <button className="btn" type="button" disabled={move.isPending} onClick={() => move.mutate('DELIVERED')}>Mark delivered</button>}
          </div>
        )}
        {(canShip || o.next.includes('PROCESSING')) && (
          <form className="shipset" noValidate onSubmit={ship}>
            {/* shown greyed out before packing, so the next step is no surprise */}
            <fieldset disabled={!canShip}>
              <legend>When you mark it shipped</legend>
              <div className="grid2">
                <div className="f">
                  <label htmlFor="cr">Courier</label>
                  <div className="fw">
                    <select className="in" id="cr" value={courier} onChange={(e) => setCourier(e.target.value)}>
                      {COURIERS.map((c) => <option key={c}>{c}</option>)}
                    </select>
                    <Icon name="down" width={2.4} />
                  </div>
                </div>
                <div className="f">
                  <label htmlFor="tn">Tracking number</label>
                  <input className="in" id="tn" placeholder="CE123456789EE" value={tracking} aria-invalid={missing || undefined}
                    aria-describedby={missing ? 'tn-e' : undefined}
                    onChange={(e) => { setTracking(e.target.value); setMissing(false); }} />
                  {missing && <p className="err" id="tn-e"><Icon name="alert" width={2} />Type the number from the courier's label.</p>}
                </div>
              </div>
              {canShip && <button className="btn" type="submit" disabled={move.isPending}>Mark shipped</button>}
            </fieldset>
          </form>
        )}
      </div>
    </section>
  );
}

function History({ order: o, onSaved }: { order: AdminOrderDetail; onSaved: (o: AdminOrderDetail) => void }) {
  const [note, setNote] = useState('');
  const add = useMutation({
    mutationFn: () => addOrderNote(o.id, note.trim()),
    onSuccess: (next) => { setNote(''); onSaved(next); },
  });
  return (
    <section className="apanel noprint" aria-labelledby="hi-h">
      <div className="panel-h"><h2 id="hi-h">History</h2></div>
      <div className="panel-b">
        <ol className="tl">
          {o.timeline.map((t, i) => (
            <li key={i} className="done">
              <time dateTime={t.at}>{formatWhen(t.at)}</time><span className="d2" />
              <div>
                <p>{t.to ? <StatusPill status={t.to} /> : <b>Team note</b>}<span className="actor">{t.actor}</span></p>
                {t.note && <p>{t.note}</p>}
              </div>
            </li>
          ))}
        </ol>
        <form className="f" noValidate onSubmit={(e) => { e.preventDefault(); if (note.trim()) add.mutate(); }}>
          <label htmlFor="nt">Note for the team <small>(the customer never sees it)</small></label>
          <div className="row nowrap">
            <input className="in" id="nt" placeholder="Gift wrap asked by phone" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
            <button className="btn btn--ghost" type="submit" disabled={add.isPending || !note.trim()}>Add</button>
          </div>
          {add.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(add.error).message}</p>}
        </form>
      </div>
    </section>
  );
}

function Payment({ order: o }: { order: AdminOrderDetail }) {
  const p = o.payment;
  const tone = o.refunded >= o.total ? ['cxl', 'Refunded'] : o.refunded > 0 ? ['pend', 'Partly refunded'] : p ? ['paid', 'Succeeded'] : ['pend', 'Not paid'];
  return (
    <section className="apanel noprint" aria-labelledby="pa-h">
      <div className="panel-h"><h2 id="pa-h">Payment</h2><span className={`st ${tone[0]}`}>{tone[1]}</span></div>
      <div className="panel-b">
        {p ? (
          <dl className="kv">
            <div><dt>Card</dt><dd>{p.brand} ending {p.last4}</dd></div>
            <div><dt>Amount</dt><dd>{formatPrice(o.total)}, {formatWhen(p.paidAt)}</dd></div>
            {o.refunded > 0 && <div><dt>Refunded</dt><dd>{formatPrice(o.refunded)}</dd></div>}
            {p.reference && (
              <div>
                <dt>Stripe payment</dt>
                <dd className="mono">
                  {p.reference.slice(0, 7)}…{p.reference.slice(-4)} · <a className="lnk" href={`https://dashboard.stripe.com/payments/${p.reference}`} target="_blank" rel="noreferrer">Open in Stripe</a>
                </dd>
              </div>
            )}
          </dl>
        ) : (
          <p className="mute">{o.paymentFailure ?? 'Nothing was charged.'}</p>
        )}
      </div>
    </section>
  );
}

/** Before packing: the whole order is cancelled, the money goes back and the items return to stock. */
function CancelPanel({ order: o, onSaved }: { order: AdminOrderDetail; onSaved: (o: AdminOrderDetail) => void }) {
  const [ask, confirmDialog] = useConfirm();
  const [reason, setReason] = useState(CANCEL_REASONS[0]);
  const cancel = useMutation({ mutationFn: () => moveOrder(o.id, 'CANCELLED', { note: reason }), onSuccess: onSaved });
  const items = `${o.itemCount} ${o.itemCount === 1 ? 'item' : 'items'}`;
  const label = o.payment ? `Cancel and refund ${formatPrice(o.total)}` : 'Cancel the order';
  return (
    <section className="apanel danger noprint" aria-labelledby="rf-h">
      <div className="panel-h"><h2 id="rf-h">{o.payment ? 'Cancel and refund' : 'Cancel'}</h2></div>
      <div className="sec-in danger-b">
        <p className="hint">
          {o.payment
            ? `Refunds ${formatPrice(o.total)} to ${o.payment.brand} ending ${o.payment.last4} through Stripe, puts the ${items} back on sale and emails the customer. Possible until packing starts.`
            : `Nothing was charged. Puts the ${items} back on sale and emails the customer.`}
        </p>
        <Reason id="rr" value={reason} options={CANCEL_REASONS} onChange={setReason} />
        {cancel.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(cancel.error).message}</p>}
        <button className="btn btn--line-danger foot-end" type="button" disabled={cancel.isPending}
          onClick={async () => (await ask({ title: `${label}?`, body: <p>This can't be undone.</p>, action: label, danger: true })) && cancel.mutate()}>
          {cancel.isPending ? 'Working…' : label}
        </button>
      </div>
      {confirmDialog}
    </section>
  );
}

/** After packing: refund by line, so each line's stock goes back, plus shipping when it should. */
function RefundPanel({ order: o, onSaved }: { order: AdminOrderDetail; onSaved: (o: AdminOrderDetail) => void }) {
  const [ask, confirmDialog] = useConfirm();
  const left = (productId: string, quantity: number) => quantity - (o.refundedLines[productId] ?? 0);
  const [qty, setQty] = useState<Record<string, number>>({});
  const [shipping, setShipping] = useState(false);
  const [reason, setReason] = useState(REFUND_REASONS[0]);
  const refund = useMutation({
    mutationFn: () => refundOrder(o.id, Object.entries(qty).filter(([, n]) => n > 0).map(([productId, quantity]) => ({ productId, quantity })), shipping, reason),
    onSuccess: (next) => { setQty({}); setShipping(false); onSaved(next); },
  });
  const amount = o.lines.reduce((sum, l) => sum + (qty[l.productId] ?? 0) * l.unitPrice, 0) + (shipping ? o.shippingCost : 0);
  return (
    <section className="apanel danger noprint" aria-labelledby="rf-h">
      <div className="panel-h"><h2 id="rf-h">Refund items</h2></div>
      <div className="sec-in danger-b">
        <p className="hint">Back to {o.payment!.brand} ending {o.payment!.last4} through Stripe. The items go back in stock and the customer gets an email.</p>
        <ul className="refl">
          {o.lines.map((l) => {
            const max = left(l.productId, l.quantity);
            return (
              <li key={l.productId}>
                <span className="nm">{l.name}</span>
                <span className="fw">
                  <select className="in" aria-label={`How many of ${l.name} to refund`} disabled={max === 0} value={qty[l.productId] ?? 0}
                    onChange={(e) => setQty((s) => ({ ...s, [l.productId]: Number(e.target.value) }))}>
                    {Array.from({ length: max + 1 }, (_, n) => <option key={n} value={n}>{n}</option>)}
                  </select>
                  <Icon name="down" width={2.4} />
                </span>
                <b>{max === 0 ? 'Refunded' : formatPrice((qty[l.productId] ?? 0) * l.unitPrice)}</b>
              </li>
            );
          })}
        </ul>
        {o.shippingCost > 0 && !o.shippingRefunded && (
          <div className="ck">
            <label>
              <input type="checkbox" checked={shipping} onChange={(e) => setShipping(e.target.checked)} />
              <i><Icon name="check" width={3} /></i>Refund shipping too<small>{formatPrice(o.shippingCost)}</small>
            </label>
          </div>
        )}
        <Reason id="rfr" value={reason} options={REFUND_REASONS} onChange={setReason} />
        {refund.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(refund.error).message}</p>}
        <button className="btn btn--line-danger foot-end" type="button" disabled={refund.isPending || amount === 0}
          onClick={async () => (await ask({ title: `Refund ${formatPrice(amount)}?`, body: <p>The money goes back to the customer's card through Stripe. This can't be undone.</p>, action: `Refund ${formatPrice(amount)}`, danger: true })) && refund.mutate()}>
          {refund.isPending ? 'Working…' : amount ? `Refund ${formatPrice(amount)}` : 'Pick what to refund'}
        </button>
      </div>
      {confirmDialog}
    </section>
  );
}

function Reason({ id, value, options, onChange }: { id: string; value: string; options: string[]; onChange: (v: string) => void }) {
  return (
    <div className="f">
      <label htmlFor={id}>Reason</label>
      <div className="fw">
        <select className="in" id={id} value={value} onChange={(e) => onChange(e.target.value)}>
          {options.map((r) => <option key={r}>{r}</option>)}
        </select>
        <Icon name="down" width={2.4} />
      </div>
    </div>
  );
}
