// The cart: a panel from the right with the lines, recommendations pinned above the subtotal, and Checkout.
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { CartLine, stockConflictOf, stockMessage } from '../api/cart';
import { toApiError } from '../api/errors';
import { useCart, useCartActions, useRecommendations } from '../hooks/useCart';
import { formatPrice } from '../lib/format';
import { FREE_SHIPPING_FROM } from '../lib/shipping';
import { useAppDispatch, useAppSelector } from '../store';
import { closed } from '../store/cartSlice';
import Icon from './Icon';
import Photo from './Photo';

export default function CartPanel() {
  const open = useAppSelector((s) => s.cart.open);
  const dispatch = useAppDispatch();
  const location = useLocation();
  const closeBtn = useRef<HTMLButtonElement>(null);
  const { cart, isPending, isError, refetch } = useCart();
  const { add, setQuantity, remove } = useCartActions();
  const recs = useRecommendations(open && cart.lines.length > 0);
  const [problem, setProblem] = useState<string | null>(null);
  const toFree = Math.max(0, FREE_SHIPPING_FROM - cart.subtotal);
  const close = () => dispatch(closed());

  // Focus moves into the panel on open and back to whatever opened it on close.
  useEffect(() => {
    if (!open) return;
    setProblem(null);
    const before = document.activeElement as HTMLElement | null;
    closeBtn.current?.focus({ preventScroll: true });
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && dispatch(closed());
    addEventListener('keydown', onKey);
    return () => {
      removeEventListener('keydown', onKey);
      document.documentElement.style.overflow = '';
      before?.focus?.({ preventScroll: true });
    };
  }, [open, dispatch]);

  // A refused write leaves the cart as it was and says why, at the top of the panel.
  const report = (err: unknown) => {
    const conflict = stockConflictOf(err);
    setProblem(conflict ? stockMessage(conflict) : toApiError(err).message);
  };
  const handlers = { onError: report, onSuccess: () => setProblem(null) };

  const buyable = cart.lines.some((l) => l.countedQuantity > 0);
  const recommended = recs.data ?? [];

  return (
    <>
      <div className="t-scrim cart-scrim" data-on={open ? '' : undefined} onClick={close} />
      <aside className="cart" role="dialog" aria-modal="true" aria-labelledby="cart-h" data-on={open ? '' : undefined}
        aria-hidden={!open}>
        <div className="cart-head">
          <h2 id="cart-h">Your cart{cart.itemCount > 0 && <small>{cart.itemCount} {cart.itemCount === 1 ? 'item' : 'items'}</small>}</h2>
          <button type="button" ref={closeBtn} aria-label="Close cart" onClick={close}>
            <Icon name="close" width={2} />
          </button>
        </div>

        <div aria-live="polite">
          {problem && <div className="cart-note"><p className="msg msg--bad"><Icon name="info" />{problem}</p></div>}
        </div>

        {isPending ? (
          <p className="cart-wait status-line">Loading your cart…</p>
        ) : isError ? (
          <div className="cart-empty">
            <p><b>We couldn't load your cart.</b><br />Check your connection and try again.</p>
            <button type="button" className="btn btn--ghost" onClick={() => refetch()}>Try again</button>
          </div>
        ) : cart.lines.length === 0 ? (
          <div className="cart-empty">
            <span className="ico"><Icon name="bag" width={1.6} /></span>
            <p><b>Your cart is empty.</b><br />Sets, boards and clocks you add will wait here.</p>
            <Link className="btn" to="/catalog" onClick={close}>Browse products</Link>
          </div>
        ) : (
          <>
            <div className="cart-ship" aria-live="polite">
              <p>{toFree > 0 ? <>Add <b>{formatPrice(toFree)}</b> more for free shipping</> : <>Your order ships <b>free</b></>}</p>
              <span className="bar"><i style={{ transform: `scaleX(${Math.min(1, cart.subtotal / FREE_SHIPPING_FROM)})` }} /></span>
            </div>
            <ul className="cart-lines">
              {cart.lines.map((l) => (
                <Line key={l.productId} line={l} onClose={close}
                  onQuantity={(quantity) => setQuantity.mutate({ productId: l.productId, quantity }, handlers)}
                  onRemove={() => remove.mutate(l.productId, handlers)} />
              ))}
            </ul>
            <div className="cart-foot">
              {recommended.length > 0 && (
                <section className="recs" aria-labelledby="recs-h">
                  <h3 id="recs-h">Goes with your order</h3>
                  <ul className="rrow">
                    {recommended.map((p) => (
                      <li key={p.id} className="rc">
                        <Link className="th" to={`/catalog/${p.id}`} onClick={close} tabIndex={-1} aria-hidden="true">
                          {p.primaryImageUrl && <Photo src={p.primaryImageUrl} framing={p.primaryImageFraming} pad={0.08} loading="lazy" />}
                        </Link>
                        <div className="rc-tx">
                          <Link className="nm" to={`/catalog/${p.id}`} onClick={close}>{p.name}</Link>
                          <b className="pz">{formatPrice(p.price)}</b>
                        </div>
                        <button type="button" className="add" aria-label={`Add ${p.name} to cart`}
                          disabled={add.isPending} onClick={() => add.mutate({ productId: p.id }, handlers)}>
                          <Icon name="plus" width={2.2} />
                        </button>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
              <div className="cart-sum">
                <p className="sumr"><span>Subtotal</span><b>{formatPrice(cart.subtotal)}</b></p>
                <p className="note">
                  {toFree > 0 ? 'Shipping is added at checkout.' : 'Standard shipping is free.'} Prices include VAT.
                </p>
                {buyable ? (
                  <Link className="btn" to="/checkout" state={{ from: location.pathname + location.search }} onClick={close}>
                    Checkout<Icon name="arrow" width={2} />
                  </Link>
                ) : (
                  <button type="button" className="btn" disabled>Checkout<Icon name="arrow" width={2} /></button>
                )}
                <button type="button" className="btn btn--ghost" onClick={close}>Continue shopping</button>
              </div>
            </div>
          </>
        )}
      </aside>
    </>
  );
}

function Line({ line: l, onClose, onQuantity, onRemove }: {
  line: CartLine;
  onClose: () => void;
  onQuantity: (quantity: number) => void;
  onRemove: () => void;
}) {
  const gone = l.state === 'sold_out' || l.state === 'unavailable';
  return (
    <li className={gone ? 'gone' : undefined}>
      <Link className="im" to={`/catalog/${l.productId}`} tabIndex={-1} aria-hidden="true" onClick={onClose}>
        {l.imageUrl ? <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.07} loading="lazy" /> : <Icon name="bag" width={1.4} />}
      </Link>
      <div className="tx">
        {gone && <span className="tag">{l.state === 'sold_out' ? 'Sold out' : 'No longer sold'}</span>}
        <Link className="nm" to={`/catalog/${l.productId}`} onClick={onClose}>{l.name}</Link>
        <span className="meta">{[l.brandName ?? 'Tavla', l.options].filter(Boolean).join(' · ')}</span>
        {gone ? (
          <p className="why">{l.state === 'sold_out' ? 'Sold out since you added it' : 'We stopped selling this'}, so it is not in your total.</p>
        ) : (
          <>
            <div className="row">
              <div className="qty" role="group" aria-label={`Quantity of ${l.name}`}>
                <button type="button" aria-label="One fewer" disabled={l.quantity <= 1} onClick={() => onQuantity(l.quantity - 1)}>
                  <Icon name="minus" width={2} />
                </button>
                <output aria-live="polite">{l.quantity}</output>
                <button type="button" aria-label="One more" disabled={l.quantity >= l.maxQuantity} onClick={() => onQuantity(l.quantity + 1)}>
                  <Icon name="plus" width={2} />
                </button>
              </div>
              <b className="pz">{formatPrice(l.lineTotal)}</b>
            </div>
            {l.state === 'short' && (
              <p className="why">Only {l.maxQuantity} left, so {l.countedQuantity} {l.countedQuantity === 1 ? 'is' : 'are'} counted. Lower the quantity to {l.maxQuantity}.</p>
            )}
          </>
        )}
        <button type="button" className="rm" onClick={onRemove}>Remove</button>
      </div>
    </li>
  );
}
