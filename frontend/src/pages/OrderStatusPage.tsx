// After Pay: waits for the payment result, then says thank you or what went wrong. Guests land here too.
import { useQuery } from '@tanstack/react-query';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { register } from '../api/auth';
import { toApiError } from '../api/errors';
import { fetchOrder, OrderDetail, startPayment } from '../api/orders';
import CardPayment, { CardPaymentHandle } from '../components/CardPayment';
import Icon from '../components/Icon';
import { formatWhen, orderDays, ReadOnlyLines, ShipTo, StatusPill, Totals, Tracker } from '../components/OrderParts';
import { formatPrice } from '../lib/format';
import { arrivalWindow } from '../lib/shipping';
import { useAppSelector } from '../store';
import { validateNewPassword } from '../validation/authRules';
import { CancelSheet } from './OrderPage';
import { usePageMeta } from '../hooks/usePageMeta';
import { PAGE_META } from '../content/pageMeta';

// The webhook usually lands within seconds. Past this the page stops asking and says it is slow.
const POLL_MS = 2000;
const POLL_FOR_MS = 120_000;

export default function OrderStatusPage() {
  usePageMeta(...PAGE_META.orderStatus);
  const { orderId = '' } = useParams();
  // what Stripe told the browser at Pay, shown while the webhook catches up
  const stripeSaid = (useLocation().state as { stripeSaid?: string } | null)?.stripeSaid ?? null;
  // restarts when the shopper pays again, so the page waits for the new attempt's answer
  const [startedAt, setStartedAt] = useState(() => Date.now());
  const query = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => fetchOrder(orderId),
    // the order's status is set by Stripe's webhook through the queue, never by this page: it only asks
    refetchInterval: (q) => (q.state.data?.status === 'PENDING_PAYMENT' && Date.now() - startedAt < POLL_FOR_MS ? POLL_MS : false),
    retry: (n, err) => n < 3 && !(isAxiosError(err) && err.response?.status === 404),
  });

  // one more render when the polling window closes, so a still-pending page says it is slow
  const [, setTimedOut] = useState(0);
  useEffect(() => {
    const t = setTimeout(() => setTimedOut(startedAt), POLL_FOR_MS);
    return () => clearTimeout(t);
  }, [startedAt]);

  if (query.isPending) {
    return <main className="cf"><p className="status-line cf-wait">Loading your order…</p></main>;
  }
  if (query.isError) {
    return (
      <main className="cf">
        <div className="cf-hero" role="alert">
          <span className="mark bad"><Icon name="close" width={2.4} /></span>
          <h1 className="co-h1">We can't find this order</h1>
          <p className="lede">The link may be mistyped, or the order belongs to another account. Your email receipt links to the right page.</p>
          <div className="cta"><Link className="btn btn--ghost" to="/">Back to the shop</Link></div>
        </div>
      </main>
    );
  }

  const order = query.data;
  const retried = () => {
    setStartedAt(Date.now());
    query.refetch();
  };
  if (order.status === 'PENDING_PAYMENT') return <Pending order={order} slow={Date.now() - startedAt >= POLL_FOR_MS} stripeSaid={stripeSaid} />;
  if (order.status === 'PAYMENT_FAILED') return <Failed order={order} onRetried={retried} />;
  if (order.status === 'CANCELLED' || order.status === 'REFUNDED') return <Cancelled order={order} />;
  return <Confirmed order={order} />;
}

function Hero({ order, mark, title, lede, alert }: { order: OrderDetail; mark: 'ok' | 'bad' | 'wait'; title: string; lede: string; alert?: boolean }) {
  return (
    <div className="cf-hero" role={alert ? 'alert' : 'status'} aria-live="polite">
      <span className={`mark ${mark}`}>{mark === 'ok' && <Icon name="check" width={2.4} />}{mark === 'bad' && <Icon name="close" width={2.4} />}</span>
      <p className="t-eyebrow">Order {order.number}</p>
      <h1 className="co-h1">{title}</h1>
      <p className="lede">{lede}</p>
      <StatusPill status={order.status} />
    </div>
  );
}

function Pending({ order, slow, stripeSaid }: { order: OrderDetail; slow: boolean; stripeSaid: string | null }) {
  const lede = slow ? "This is taking longer than usual. We'll email you the result, so it's fine to close this page. Please don't pay again."
    : stripeSaid ? `${stripeSaid} This page shows what to do next in a moment.`
      : "Your bank is answering Stripe. It usually takes a few seconds. Keep this page open, and please don't pay again.";
  return (
    <main className="cf">
      <Hero order={order} mark="wait" title="Confirming your payment" lede={lede} />
      <div className="card">
        <h2>What happens next</h2>
        <dl className="kv">
          <div><dt>In a few seconds</dt><dd>This page changes to "Thank you" or tells you what went wrong.</dd></div>
          <div><dt>Either way</dt><dd>We email you the result, so it is fine to close this page.</dd></div>
          <div><dt>Your items</dt><dd>Reserved for you while we wait.</dd></div>
        </dl>
      </div>
      <div className="cta"><Link className="btn btn--ghost" to="/">Back to the shop</Link></div>
    </main>
  );
}

function Confirmed({ order }: { order: OrderDetail }) {
  const signedIn = useAppSelector((s) => s.auth.status === 'authenticated');
  const [cancelling, setCancelling] = useState(false);
  const ref = order.payment?.reference;
  return (
    <main className="wrap cf2">
      <div className="cf2-main">
        <header className="cf2-head" role="status">
          <p className="eyebrow"><span className="okdot"><Icon name="check" width={2.6} /></span>Order confirmed · {formatWhen(order.placedAt)}</p>
          <h1>Thank you, {order.shipTo.firstName}. Your order is in.</h1>
          <p className="lede">The receipt and a link to follow this order are on their way to {order.email}.</p>
        </header>
        <dl className="refs">
          <div><dt>Order number</dt><dd>{order.number} <CopyButton text={order.number} label={`Copy order number ${order.number}`} /><small>Quote it when you write to us.</small></dd></div>
          {ref && (
            <div><dt>Payment reference</dt><dd className="mono">{ref.length > 14 ? `${ref.slice(0, 7)}…${ref.slice(-4)}` : ref} <CopyButton text={ref} label="Copy payment reference" /><small>Stripe's id for the payment, if your bank asks.</small></dd></div>
          )}
          <div><dt>Estimated delivery</dt><dd>{arrivalWindow(orderDays(order), new Date(order.placedAt))}<small>{order.shippingName}. Tracking link by email when it ships.</small></dd></div>
        </dl>
        <div className="card"><Tracker order={order} /></div>
        <div className="card cf2-fill">
          <h2>Delivery and payment</h2>
          <dl className="kv cf2-kv">
            <div><dt>Ship to</dt><dd><ShipTo order={order} /></dd></div>
            {order.billTo && <div><dt>Bill to</dt><dd>{order.billTo.name}<br />{order.billTo.street}<br />{order.billTo.postcode} {order.billTo.city}, {order.billTo.country}</dd></div>}
            {order.payment && <div><dt>Paid with</dt><dd>{order.payment.brand} ending {order.payment.last4}<br />{formatPrice(order.total)} on {formatWhen(order.payment.paidAt)}</dd></div>}
          </dl>
          {order.cancellable && (
            <p className="cf-note foot-end">
              <Icon name="info" />
              <span>Changed your mind? You can cancel until we start packing, {signedIn
                ? <>from <Link className="lnk" to={`/account/orders/${encodeURIComponent(order.id)}`}>the order page</Link>.</>
                : <>right here. <button type="button" className="link" onClick={() => setCancelling(true)}>Cancel this order</button></>}</span>
            </p>
          )}
        </div>
      </div>
      <aside className="cf2-side" aria-label="Order summary">
        <div className="card">
          <div className="card-h"><h2>Your order</h2><span className="mute">{order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}</span></div>
          <ReadOnlyLines lines={order.lines} />
          <Totals subtotal={order.subtotal} shipping={order.shippingCost} methodName={order.shippingName} vat={order.vat} total={order.total} grandLabel="Paid" />
        </div>
        {signedIn ? (
          <div className="card cf2-fill">
            <h2>Follow it from your account</h2>
            <p className="mute">The order page shows each step, the tracking link once it ships, and lets you cancel before we pack.</p>
            <div className="cf2-join foot-end"><Link className="btn" to={`/account/orders/${encodeURIComponent(order.id)}`}>See this order</Link></div>
          </div>
        ) : (
          <JoinCard email={order.email} name={`${order.shipTo.firstName} ${order.shipTo.lastName}`} />
        )}
        <Link className="btn btn--ghost" to="/catalog">Continue shopping</Link>
      </aside>
      {cancelling && <CancelSheet order={order} onClose={() => setCancelling(false)} />}
    </main>
  );
}

/** Copies a reference to the clipboard and says so for a moment. */
function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button className="copy" type="button" aria-label={copied ? 'Copied' : label}
      onClick={async () => { await navigator.clipboard?.writeText(text); setCopied(true); setTimeout(() => setCopied(false), 1600); }}>
      <Icon name={copied ? 'check' : 'log'} width={2} />{copied ? 'Copied' : 'Copy'}
    </button>
  );
}

/** A guest can keep their next orders in one place: an account with the email they just used. */
function JoinCard({ email, name }: { email: string; name: string }) {
  const [password, setPassword] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);
  async function join(e: FormEvent) {
    e.preventDefault();
    const bad = validateNewPassword(password);
    if (bad) { setProblem(bad); return; }
    setPending(true);
    try {
      await register({ email, password, fullName: name });
      setDone(true);
    } catch (err) {
      setProblem(toApiError(err).message);
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="card cf2-fill">
      <h2>Keep your orders in an account</h2>
      {done ? (
        <p className="mute" role="status">Your account is made. <Link className="lnk" to="/login">Sign in</Link> with {email} to see your orders.</p>
      ) : (
        <>
          <p className="mute">Choose a password and your next orders show up under Your orders. Your email is already filled in.</p>
          <form className="cf2-join foot-end" noValidate onSubmit={join}>
            <div className="f">
              <label className="vh" htmlFor="join-pw">Password</label>
              <input className="in" id="join-pw" type="password" placeholder="At least 8 characters" autoComplete="new-password"
                value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={problem ? true : undefined}
                aria-describedby={problem ? 'join-e' : undefined} />
              {problem && <p className="err" id="join-e"><Icon name="alert" width={2} />{problem}</p>}
            </div>
            <button className="btn" type="submit" disabled={pending}>{pending ? 'Creating…' : 'Create account'}</button>
          </form>
        </>
      )}
    </div>
  );
}

const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

function Failed({ order, onRetried }: { order: OrderDetail; onRetried: () => void }) {
  const hold = order.holdUntil ? new Date(order.holdUntil) : null;
  const canRetry = hold !== null && hold.getTime() > Date.now();
  return (
    <main className="cf">
      <Hero order={order} mark="bad" alert title="Your payment didn't go through"
        lede={`${order.paymentFailure ?? 'The payment was declined.'} Nothing was charged.`} />
      <div className="card">
        <div className="card-h"><h2>Your order is saved</h2><b className="num">{formatPrice(order.total)}</b></div>
        <ReadOnlyLines lines={order.lines} />
        {hold && hold.getTime() > Date.now() && (
          <p className="msg msg--warn"><Icon name="clock" />We hold these items for you until {time.format(hold)}. After that they go back on sale.</p>
        )}
      </div>
      {canRetry && <PayAgain order={order} onRetried={onRetried} />}
      <div className="cta">
        <Link className="btn btn--ghost" to="/">Back to the shop</Link>
      </div>
      <p className="legal">We emailed you this too, in case you close the page.</p>
    </main>
  );
}

/** Another card for the same order: the same PaymentIntent, so it can never become two payments. */
function PayAgain({ order, onRetried }: { order: OrderDetail; onRetried: () => void }) {
  const card = useRef<CardPaymentHandle>(null);
  const [paying, setPaying] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  async function pay() {
    setProblem(null);
    setPaying(true);
    try {
      const cardProblem = await card.current?.check();
      if (cardProblem) {
        setProblem(cardProblem);
        return;
      }
      const { clientSecret } = await startPayment(order.id);
      const stripeSaid = await card.current?.confirm(clientSecret, order.id);
      if (stripeSaid) setProblem(stripeSaid);
      onRetried();
    } catch (err) {
      setProblem(toApiError(err).message);
    } finally {
      setPaying(false);
    }
  }

  return (
    <section className="card" aria-labelledby="pay-again">
      <h2 id="pay-again">Try another card</h2>
      {problem && <p className="msg msg--bad" role="alert"><Icon name="alert" />{problem}</p>}
      <CardPayment ref={card} amount={order.total} />
      <button type="button" className="btn btn--big" disabled={paying} onClick={pay}>
        <Icon name="lock" width={2} />{paying ? 'Paying…' : `Pay ${formatPrice(order.total)}`}
      </button>
    </section>
  );
}

function Cancelled({ order }: { order: OrderDetail }) {
  return (
    <main className="cf">
      <Hero order={order} mark="bad" title="This order was cancelled"
        lede={order.status === 'REFUNDED' ? 'The refund is on its way back to your card.' : 'Nothing more will be charged for it.'} />
      <div className="cta"><Link className="btn btn--ghost" to="/catalog">Continue shopping</Link></div>
    </main>
  );
}
