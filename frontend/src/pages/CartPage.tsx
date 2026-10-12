// The cart page (/bag; /cart is the API): lines with photo, price, quantity and total, the summary before shipping, and checkout.
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CartLine, stockConflictOf, stockMessage } from '../api/cart';
import { searchProducts } from '../api/catalog';
import { toApiError } from '../api/errors';
import Icon from '../components/Icon';
import PayMarks from '../components/PayMarks';
import Photo from '../components/Photo';
import ProductCard from '../components/ProductCard';
import { useCart, useCartActions, useRecommendations } from '../hooks/useCart';
import { usePageMeta } from '../hooks/usePageMeta';
import { formatPrice } from '../lib/format';
import { FREE_SHIPPING_FROM, VAT_RATE } from '../lib/shipping';
import { useAppSelector } from '../store';

export default function CartPage() {
  usePageMeta('Your cart', 'The chess, Go and backgammon equipment in your Tavla cart: quantities, prices and the total before shipping.');
  const signedIn = useAppSelector((s) => s.auth.status === 'authenticated');
  const { cart, isPending, isError, refetch } = useCart();
  const { setQuantity, remove } = useCartActions();
  const recs = useRecommendations(cart.lines.length > 0);
  const [problem, setProblem] = useState<string | null>(null);
  const handlers = {
    onError: (err: unknown) => {
      const conflict = stockConflictOf(err);
      setProblem(conflict ? stockMessage(conflict) : toApiError(err).message);
    },
    onSuccess: () => setProblem(null),
  };

  if (isPending) return <div className="wrap page"><p className="status-line">Loading your cart…</p></div>;
  if (isError) {
    return (
      <div className="wrap page">
        <div className="empty2">
          <h1>We couldn't load your cart</h1>
          <p>Check your connection and try again.</p>
          <button type="button" className="btn btn--ghost" onClick={() => refetch()}>Try again</button>
        </div>
      </div>
    );
  }
  if (cart.lines.length === 0) return <EmptyCart signedIn={signedIn} />;

  const toFree = Math.max(0, FREE_SHIPPING_FROM - cart.subtotal);
  const counted = cart.lines.reduce((n, l) => n + l.countedQuantity, 0);
  // prices include VAT: this is the share of the items' total that is VAT, in cents so it rounds once
  const vat = Math.round((cart.subtotal * 100 * VAT_RATE) / (1 + VAT_RATE)) / 100;
  const buyable = cart.lines.some((l) => l.countedQuantity > 0);

  return (
    <div className="wrap cartp">
      <div className="cartp-head">
        <h1 className="dt">Your cart <span className="mute num">{cart.itemCount} {cart.itemCount === 1 ? 'item' : 'items'}</span></h1>
        <Link className="t-go" to="/catalog"><Icon name="left" width={2.2} />Continue shopping</Link>
      </div>
      <div className="shipline" role="status">
        <p>
          <Icon name="truck" />
          {toFree > 0
            ? <span>Add <b>{formatPrice(toFree)}</b> more and your order ships free with Standard courier.</span>
            : <span>Your order ships <b>free</b> with Standard courier.</span>}
        </p>
        <span className="bar2" aria-hidden="true"><i style={{ width: `${Math.min(100, (cart.subtotal / FREE_SHIPPING_FROM) * 100)}%` }} /></span>
      </div>
      <section aria-labelledby="items-h">
        <h2 id="items-h" className="vh">Items</h2>
        <div aria-live="polite">{problem && <p className="msg msg--bad cart-problem"><Icon name="info" />{problem}</p>}</div>
        <div className="chead" aria-hidden="true"><span>Product</span><span>Price</span><span>Quantity</span><span>Total</span></div>
        <ul className="clist">
          {cart.lines.map((l) => (
            <Line key={l.productId} line={l}
              onQuantity={(quantity) => setQuantity.mutate({ productId: l.productId, quantity }, handlers)}
              onRemove={() => remove.mutate(l.productId, handlers)} />
          ))}
        </ul>
      </section>
      <aside className="csum" aria-labelledby="sum-h">
        <div className="card">
          <div className="card-h"><h2 id="sum-h">Order summary</h2></div>
          <dl className="tot">
            <div><dt>Items ({counted})</dt><dd>{formatPrice(cart.subtotal)}</dd></div>
            <div>
              <dt>Shipping</dt>
              {toFree > 0 ? <dd className="ship-later">You pick it at checkout</dd> : <dd className="free">Free · you pick at checkout</dd>}
            </div>
            <div className="grand"><dt>Total <small>before shipping</small></dt><dd>{formatPrice(cart.subtotal)}</dd></div>
            <div className="vat"><dt>Includes VAT 24%</dt><dd>{formatPrice(vat)}</dd></div>
          </dl>
          {buyable ? (
            <Link className="btn btn--big" to="/checkout" state={{ from: '/bag' }}><Icon name="lock" width={2} />Go to checkout</Link>
          ) : (
            <button type="button" className="btn btn--big" disabled><Icon name="lock" width={2} />Go to checkout</button>
          )}
          <PayMarks />
          <div className="perks">
            <span><Icon name="back" />30-day returns</span>
            <span><Icon name="lock" />Card payments by Stripe</span>
          </div>
        </div>
      </aside>
      {(recs.data ?? []).length > 0 && (
        <section className="goes" aria-labelledby="goes-h">
          <div className="t-head"><h2 id="goes-h">Goes with your order</h2></div>
          <div className="rg">{recs.data!.slice(0, 4).map((p) => <ProductCard key={p.id} product={p} />)}</div>
        </section>
      )}
    </div>
  );
}

function Line({ line: l, onQuantity, onRemove }: { line: CartLine; onQuantity: (q: number) => void; onRemove: () => void }) {
  const gone = l.state === 'sold_out' || l.state === 'unavailable';
  const to = `/catalog/${l.productId}`;
  return (
    <li className={gone ? 'cl gone' : 'cl'}>
      <div className="prod">
        <Link className="th" to={to} tabIndex={-1} aria-hidden="true">
          {l.imageUrl ? <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.07} loading="lazy" /> : <Icon name="bag" width={1.4} />}
        </Link>
        <div>
          {gone && <span className="tag">{l.state === 'sold_out' ? 'Sold out' : 'No longer sold'}</span>}
          <Link className="nm" to={to}>{l.name}</Link>
          <p className="meta">{[l.brandName ?? 'Tavla', l.options].filter(Boolean).join(' · ')}</p>
          {gone ? (
            <p className="gone-why">{l.state === 'sold_out' ? 'Sold out since you added it' : 'We stopped selling this'}, so it is not in your total.</p>
          ) : l.state === 'short' ? (
            <span className="stock low">Only {l.maxQuantity} left, so {l.countedQuantity} {l.countedQuantity === 1 ? 'is' : 'are'} counted</span>
          ) : l.maxQuantity < 10 && l.maxQuantity <= l.quantity + 2 ? (
            <span className="stock low">Only {l.maxQuantity} left</span>
          ) : (
            <span className="stock">In stock</span>
          )}
          <div className="acts3"><button className="rm" type="button" onClick={onRemove}>Remove</button></div>
        </div>
      </div>
      <span className="each"><span className="lbl-sm">Price </span>{gone ? '–' : formatPrice(l.unitPrice)}</span>
      <div className="qcell">
        {gone ? <span className="mute na">Not available</span> : (
          <div className="qty" role="group" aria-label={`Quantity of ${l.name}`}>
            <button type="button" aria-label="One fewer" disabled={l.quantity <= 1} onClick={() => onQuantity(l.quantity - 1)}>
              <Icon name="minus" width={2} />
            </button>
            <output aria-live="polite">{l.quantity}</output>
            <button type="button" aria-label="One more" disabled={l.quantity >= l.maxQuantity} onClick={() => onQuantity(l.quantity + 1)}>
              <Icon name="plus" width={2} />
            </button>
          </div>
        )}
      </div>
      <b className={gone ? 'lt mute' : 'lt'}>{gone ? '–' : formatPrice(l.lineTotal)}</b>
    </li>
  );
}

/** Nothing in the cart: where it went, the way to products, and a few of the best-rated ones. */
function EmptyCart({ signedIn }: { signedIn: boolean }) {
  const top = useQuery({
    queryKey: ['products', 'category=&sort=rating&size=8'],
    queryFn: () => searchProducts({ sort: 'rating', size: '8' }),
    staleTime: 5 * 60_000,
  });
  const picks = (top.data?.items ?? []).filter((p) => p.inStock).slice(0, 4);
  return (
    <div className="wrap cartp-empty">
      <div className="empty2">
        <p className="eyebrow">Cart</p>
        <h1>Your cart is empty</h1>
        <p>{signedIn
          ? 'Your cart follows you to any device you sign in on.'
          : 'Signed in, your cart follows you to any device. As a guest it stays in this browser for 30 days.'}</p>
        <div className="empty2-acts">
          <Link className="btn" to="/catalog">Browse products</Link>
          {!signedIn && <Link className="btn btn--ghost" to="/login">Sign in to see a saved cart</Link>}
        </div>
      </div>
      {picks.length > 0 && (
        <section aria-labelledby="pop-h">
          <h2 id="pop-h" className="pop-h">Top rated right now</h2>
          <div className="rg">{picks.map((p) => <ProductCard key={p.id} product={p} />)}</div>
        </section>
      )}
    </div>
  );
}
