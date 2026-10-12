// One-page checkout: contact, address, shipping, payment, and the order summary where quantities can still change.
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { CartLine, stockConflictOf, stockMessage } from '../api/cart';
import { toApiError } from '../api/errors';
import { fetchLastDelivery, placeOrder } from '../api/orders';
import { endSession } from '../auth/session';
import CardPayment, { CardPaymentHandle } from '../components/CardPayment';
import Field from '../components/Field';
import Icon from '../components/Icon';
import { Totals } from '../components/OrderParts';
import Photo from '../components/Photo';
import { useCart, useCartActions } from '../hooks/useCart';
import { formatPrice } from '../lib/format';
import { arrivalWindow, shippingCost, SHIPPING, ShippingMethod, summary } from '../lib/shipping';
import { useAppDispatch, useAppSelector } from '../store';
import { Errors, hasErrors } from '../validation/authRules';
import {
  BillingField, BillingForm, CheckoutField, CheckoutForm, COUNTRIES, CountryCode, FIELD_LABELS, FIELD_MAX, validateBilling, validateCheckout,
} from '../validation/checkoutRules';

const BLANK: CheckoutForm = { email: '', phone: '', firstName: '', lastName: '', street: '', flat: '', postcode: '', city: '', country: 'EE' };

// Past this many lines the summary's list scrolls inside it, so the totals and Pay stay on screen.
const CAP_LINES = 3;

export default function CheckoutPage() {
  const { status, user } = useAppSelector((s) => s.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  const { cart, isPending: loading, refetch } = useCart();

  const [form, setForm] = useState<CheckoutForm>(BLANK);
  const [sameBilling, setSameBilling] = useState(true);
  const [billing, setBilling] = useState<BillingForm>({ name: '', street: '', postcode: '', city: '', country: 'EE' });
  const [method, setMethod] = useState<ShippingMethod>('standard');
  // a field shows its error once it has been left, or once Pay was pressed
  const [touched, setTouched] = useState<Partial<Record<CheckoutField, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const [serverErrors, setServerErrors] = useState<Errors<CheckoutField>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const summaryBox = useRef<HTMLDivElement>(null);
  const card = useRef<CardPaymentHandle>(null);
  const queryClient = useQueryClient();

  // Known details are filled in once the session is back, without overwriting anything typed already.
  useEffect(() => {
    if (!user) return;
    const [first, ...rest] = user.fullName.trim().split(/\s+/);
    setForm((f) => ({
      ...f,
      email: f.email || user.email,
      firstName: f.firstName || first || '',
      lastName: f.lastName || rest.join(' '),
    }));
  }, [user]);

  // The phone and address of the account's last order. The address goes in as a whole, and only while
  // none of it is typed yet, so an old flat number never lands in a half-typed new address.
  // fetched each time the page opens (an order placed a minute ago counts), not on refocus, which could refill a cleared field
  const { data: last } = useQuery({ queryKey: ['last-delivery', user?.id], queryFn: fetchLastDelivery, enabled: !!user, refetchOnWindowFocus: false });
  useEffect(() => {
    if (!last) return;
    const country = COUNTRIES.find((c) => c.code === last.country)?.code;
    setForm((f) => ({
      ...f,
      phone: f.phone || last.phone,
      ...(!f.street && !f.postcode && !f.city
        ? { street: last.street, flat: last.flat ?? '', postcode: last.postcode, city: last.city, country: country ?? f.country }
        : {}),
    }));
  }, [last]);

  const errors = useMemo(() => validateCheckout(form), [form]);
  const errorOf = (name: CheckoutField) => serverErrors[name] ?? (submitted || touched[name] ? errors[name] : undefined);
  const listed = (Object.keys(FIELD_LABELS) as CheckoutField[]).filter((name) => errorOf(name));
  const billingErrors = useMemo(() => (sameBilling ? {} : validateBilling(billing)), [sameBilling, billing]);
  const billingWrong = submitted && hasErrors(billingErrors);
  const billingField = (name: BillingField, label: string, autoComplete: string) => (
    <Field label={label} name={`billing-${name}`} autoComplete={autoComplete} maxLength={FIELD_MAX} value={billing[name]}
      error={submitted ? billingErrors[name] : undefined} onChange={(e) => setBilling((b) => ({ ...b, [name]: e.target.value }))} />
  );

  const field = (name: CheckoutField) => ({
    name,
    value: form[name],
    error: errorOf(name),
    maxLength: FIELD_MAX,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
      setForm((f) => ({ ...f, [name]: e.target.value }));
      setServerErrors((s) => ({ ...s, [name]: undefined }));
    },
    onBlur: () => setTouched((t) => ({ ...t, [name]: true })),
  });

  const buyable = cart.lines.filter((l) => l.countedQuantity > 0);
  const { shipping, total, vat } = summary(cart.subtotal, method);

  async function pay(event: FormEvent) {
    event.preventDefault();
    setSubmitted(true);
    setFailure(null);
    if (hasErrors(errors) || hasErrors(billingErrors)) {
      // the summary at the top lists every field to fix; focus goes there so a screen reader reads it
      requestAnimationFrame(() => summaryBox.current?.focus());
      return;
    }
    setPaying(true);
    try {
      // Stripe checks the card in its own frame first, so a typo never places an order
      const cardProblem = await card.current?.check();
      if (cardProblem) {
        setFailure(cardProblem);
        return;
      }
      const trimmed = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v.trim()])) as CheckoutForm;
      // sent only when it differs: the server refuses fields it doesn't know
      const billingAddress = sameBilling ? {}
        : { billingAddress: Object.fromEntries(Object.entries(billing).map(([k, v]) => [k, v.trim()])) as BillingForm };
      const order = await placeOrder({ ...trimmed, flat: trimmed.flat || null, shippingMethod: method, ...billingAddress });
      // Stripe's answer in the browser is only a hint: the order page waits for the webhook to say paid or failed
      const stripeSaid = await card.current?.confirm(order.clientSecret, order.id);
      // the order took these lines out of the cart on the server; refreshed only now, so the form stays mounted while Stripe confirms
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      navigate(`/checkout/${encodeURIComponent(order.id)}`, { replace: true, state: stripeSaid ? { stripeSaid } : undefined });
    } catch (err) {
      const conflict = stockConflictOf(err);
      const apiError = toApiError(err);
      if (conflict) {
        refetch();
        setFailure(`Your cart changed: ${stockMessage(conflict)} Nothing was charged.`);
      } else if (apiError.code === 'network_error') {
        setFailure("We couldn't reach the server, so nothing was charged. Check your connection and press Pay again.");
      } else if (apiError.code === 'validation_failed') {
        setServerErrors(apiError.fields as Errors<CheckoutField>);
        requestAnimationFrame(() => summaryBox.current?.focus());
      } else if (apiError.code === 'payment_unavailable' || apiError.code === 'cart_empty') {
        // the server put the cart back as it was (or it changed under us): show what it holds now
        refetch();
        setFailure(apiError.message);
      } else {
        setFailure(`${apiError.message} Nothing was charged.`);
      }
    } finally {
      setPaying(false);
    }
  }

  const focusField = (name: CheckoutField) => document.querySelector<HTMLElement>(`[name="${name}"]`)?.focus();

  // not while paying: the order empties the cart on the server, and Stripe's form must stay mounted until it answers
  if (!loading && !paying && buyable.length === 0) {
    return (
      <CheckoutShell from={from}>
        <div className="co co--empty">
          <div className="empty">
            <h1>Nothing to check out</h1>
            <p>Your cart is empty, or everything in it sold out. Add something and come back.</p>
            <Link className="btn" to="/catalog">Browse products</Link>
          </div>
        </div>
      </CheckoutShell>
    );
  }

  return (
    <CheckoutShell from={from}>
      <form className="co" onSubmit={pay} noValidate>
        <div className="co-main">
          <div className="co-head">
            <h1 className="co-h1">Checkout</h1>
            {status === 'authenticated' && user ? (
              <p className="who">
                <span className="av" aria-hidden="true">{initials(user.fullName)}</span>
                <span>Signed in as <b>{user.fullName}</b> · <button type="button" className="link" onClick={() => endSession(dispatch)}>Not you?</button></span>
              </p>
            ) : (
              <p className="who">Have an account? <Link className="link" to="/login" state={{ from: '/checkout' }}>Sign in</Link> and we fill this in.</p>
            )}
          </div>

          <div aria-live="assertive">
            {((listed.length > 0 || billingWrong) && submitted) && (
              <div className="msg msg--bad msg--block" tabIndex={-1} ref={summaryBox}>
                <Icon name="alert" />
                <div>
                  <p><b>{listed.length + (billingWrong ? 1 : 0) === 1 ? '1 thing' : `${listed.length + (billingWrong ? 1 : 0)} things`} to fix before you pay</b></p>
                  <p className="fix">
                    {listed.map((name, i) => (
                      <span key={name}>{i > 0 && ' · '}<button type="button" className="link" onClick={() => focusField(name)}>{FIELD_LABELS[name]}</button></span>
                    ))}
                    {billingWrong && (
                      <span>{listed.length > 0 && ' · '}<button type="button" className="link"
                        onClick={() => document.querySelector<HTMLElement>('.bill [aria-invalid=true]')?.focus()}>Billing address</button></span>
                    )}
                  </p>
                </div>
              </div>
            )}
            {failure && <div className="msg msg--bad msg--block" role="alert"><Icon name="alert" /><p>{failure}</p></div>}
          </div>

          <Section n={1} title="Contact" aside="Your receipt goes here">
            <div className="grid2 grid2--stack">
              <Field label="Email" type="email" autoComplete="email" {...field('email')} />
              <Field label="Phone" type="tel" autoComplete="tel" inputMode="tel" placeholder="+372 5123 4567"
                hint="Only for the courier, about this delivery." {...field('phone')} />
            </div>
          </Section>

          <Section n={2} title="Delivery address" aside={user ? 'Filled in from your account' : undefined}>
            <div className="grid2">
              <Field label="First name" autoComplete="given-name" {...field('firstName')} />
              <Field label="Last name" autoComplete="family-name" {...field('lastName')} />
            </div>
            <div className="grid2">
              <Field label="Street and house number" autoComplete="address-line1" {...field('street')} />
              <Field label="Flat, floor (optional)" autoComplete="address-line2" {...field('flat')} />
            </div>
            <div className="grid3">
              <Field label="Postcode" autoComplete="postal-code" {...field('postcode')} />
              <Field label="City" autoComplete="address-level2" {...field('city')} />
              <div className="field">
                <label className="field__label" htmlFor="co-country">Country</label>
                <div className="sel-wrap">
                  <select id="co-country" name="country" className="field__input" autoComplete="country" value={form.country}
                    onChange={(e) => setForm((f) => ({ ...f, country: e.target.value as CountryCode }))}>
                    {COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                  </select>
                  <Icon name="down" width={2} />
                </div>
              </div>
            </div>
          </Section>

          <Section n={3} title="Shipping" aside="Ships from Tallinn today if you order by 15:00">
            <div className="opts" role="radiogroup" aria-label="Shipping">
              {SHIPPING.map((o) => {
                const cost = shippingCost(o.method, cart.subtotal);
                return (
                  <label key={o.method} className={`opt${method === o.method ? ' on' : ''}`}>
                    <input type="radio" name="shipping" value={o.method} checked={method === o.method} onChange={() => setMethod(o.method)} />
                    <b>{o.name}</b>
                    <span className="d">{o.blurb} · {o.method === 'locker' ? 'you pick the locker next' : `arrives ${arrivalWindow(o)}`}</span>
                    <span className={`p${cost === 0 ? ' free' : ''}`}>
                      {cost === 0 ? <>Free<s>{formatPrice(o.price)}</s></> : formatPrice(cost)}
                    </span>
                  </label>
                );
              })}
            </div>
          </Section>

          <Section n={4} title="Payment" aside="Charged only when you press Pay">
            <p className="test-note"><Icon name="info" /><span>Test mode. Pay with <code>4242 4242 4242 4242</code>, any future date, any CVC.</span></p>
            {/* card number, expiry and CVC are typed into Stripe's own frame and never reach our server */}
            <CardPayment ref={card} amount={total} />
            <div className="ck billck">
              <label>
                <input type="checkbox" checked={sameBilling} onChange={(e) => setSameBilling(e.target.checked)} />
                <i><Icon name="check" width={3} /></i>Billing address is the same as delivery
              </label>
            </div>
            {!sameBilling && (
              <div className="bill" role="group" aria-labelledby="bill-h">
                <p id="bill-h" className="bill-h">Billing address <span className="mute">· as it appears on the invoice</span></p>
                {billingField('name', 'Name or company', 'billing organization')}
                {billingField('street', 'Street and house number', 'billing address-line1')}
                <div className="grid3">
                  {billingField('postcode', 'Postcode', 'billing postal-code')}
                  {billingField('city', 'City', 'billing address-level2')}
                  <div className="field">
                    <label className="field__label" htmlFor="billing-country">Country</label>
                    <div className="sel-wrap">
                      <select id="billing-country" className="field__input" value={billing.country}
                        onChange={(e) => setBilling((b) => ({ ...b, country: e.target.value as CountryCode }))}>
                        {COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                      </select>
                      <Icon name="down" width={2} />
                    </div>
                  </div>
                </div>
              </div>
            )}
          </Section>

          <div className="pay">
            <button type="submit" className="btn btn--big" disabled={paying || loading}>
              <Icon name="lock" width={2} />{paying ? 'Placing your order…' : `Pay ${formatPrice(total)}`}
            </button>
            <p className="legal">
              {submitted && (listed.length > 0 || billingWrong) ? 'Nothing was charged. Fix the fields in red and press Pay again.'
                : 'You can cancel until we start packing.'}
            </p>
          </div>
        </div>

        <aside className="co-side" aria-label="Order summary">
          <Summary lines={cart.lines} subtotal={cart.subtotal} method={method} shipping={shipping} vat={vat} total={total} />
        </aside>
      </form>
    </CheckoutShell>
  );
}

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('');
}

function Section({ n, title, aside, children }: { n: number; title: string; aside?: string; children: ReactNode }) {
  const id = `co-s${n}`;
  return (
    <section className="sec" aria-labelledby={id}>
      <div className="sec-h"><span className="n" aria-hidden="true">{n}</span><h2 id={id}>{title}</h2>{aside && <span className="aside">{aside}</span>}</div>
      {children}
    </section>
  );
}

/** Sticky beside the form on a desktop; on a tablet or phone a bar above it that folds open. */
function Summary({ lines, subtotal, method, shipping, vat, total }: {
  lines: CartLine[]; subtotal: number; method: ShippingMethod; shipping: number; vat: number; total: number;
}) {
  const [open, setOpen] = useState(false);
  const { setQuantity, remove } = useCartActions();
  const [problem, setProblem] = useState<string | null>(null);
  const handlers = {
    onSuccess: () => setProblem(null),
    onError: (err: unknown) => {
      const c = stockConflictOf(err);
      setProblem(c ? stockMessage(c) : toApiError(err).message);
    },
  };
  const count = lines.reduce((n, l) => n + l.countedQuantity, 0);
  return (
    <div className={`sum${open ? '' : ' closed'}`}>
      <button type="button" className="sum-bar" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        Order summary · {count} {count === 1 ? 'item' : 'items'}<Icon name="down" width={2} /><span className="tt">{formatPrice(total)}</span>
      </button>
      <div className="sum-h"><h2>Order summary</h2><span>{count} {count === 1 ? 'item' : 'items'}</span></div>
      <div className="sum-body">
        {problem && <p className="msg msg--bad" role="alert"><Icon name="alert" />{problem}</p>}
        <ul className={`lines${lines.length > CAP_LINES ? ' capped' : ''}`}>
          {lines.map((l) => {
            const gone = l.countedQuantity === 0;
            return (
              <li key={l.productId} className={`ln${gone ? ' gone' : ''}`}>
                <span className="th">{l.imageUrl && <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.08} loading="lazy" />}</span>
                <div>
                  <p className="nm">{l.name}</p>
                  <p className="meta">
                    {gone ? (l.state === 'sold_out' ? 'Sold out, not in your total' : 'No longer sold, not in your total')
                      : [l.brandName, l.options, l.quantity > 1 && `${formatPrice(l.unitPrice)} each`].filter(Boolean).join(' · ')}
                  </p>
                  {l.state === 'short' && <p className="meta short">Only {l.maxQuantity} left, so {l.countedQuantity} counted.</p>}
                  <div className="ctl">
                    {!gone && (
                      <div className="qty" role="group" aria-label={`Quantity of ${l.name}`}>
                        <button type="button" aria-label="One fewer" disabled={l.quantity <= 1}
                          onClick={() => setQuantity.mutate({ productId: l.productId, quantity: l.quantity - 1 }, handlers)}>
                          <Icon name="minus" width={2} />
                        </button>
                        <output aria-live="polite">{l.quantity}</output>
                        <button type="button" aria-label="One more" disabled={l.quantity >= l.maxQuantity}
                          onClick={() => setQuantity.mutate({ productId: l.productId, quantity: l.quantity + 1 }, handlers)}>
                          <Icon name="plus" width={2} />
                        </button>
                      </div>
                    )}
                    <button type="button" className="rm" onClick={() => remove.mutate(l.productId, handlers)}>Remove</button>
                  </div>
                </div>
                <b className="pz">{formatPrice(l.lineTotal)}</b>
              </li>
            );
          })}
        </ul>
        <Totals subtotal={subtotal} shipping={shipping} method={method} vat={vat} total={total} grandLabel="Total" />
        <div className="perks">
          <span><Icon name="truck" />Free standard shipping over €100</span>
          <span><Icon name="back" />30-day returns</span>
          <span><Icon name="lock" />Card payments by Stripe</span>
        </div>
      </div>
    </div>
  );
}

/** The checkout keeps only a way back and the logo: no menu or search to wander off to. */
function CheckoutShell({ from, children }: { from: string; children: ReactNode }) {
  return (
    <div className="t">
      <header className="cbar">
        <div className="wrap">
          {/* back to the cart page when that is where Checkout was pressed; otherwise to that page, with the cart open again */}
          <Link className="back" to={from} state={from === '/bag' ? undefined : { openCart: true }}><Icon name="left" width={2} /><span>Cart</span></Link>
          <Link className="t-logo" to="/" aria-label="Tavla, home">Tavla</Link>
          <span className="secure"><Icon name="lock" width={2} /><span>Secure<span className="long"> checkout</span></span></span>
        </div>
      </header>
      <main id="view">{children}</main>
      <footer className="cfoot">
        <div className="wrap"><span>© 2026 Tavla</span><span>Prices include VAT</span></div>
      </footer>
    </div>
  );
}
